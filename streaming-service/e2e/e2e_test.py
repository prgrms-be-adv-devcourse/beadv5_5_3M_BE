"""
streaming-service E2E Test Script
==================================
Scenarios:
  A. Session issue + HLS manifest serve + segment rewrite (schedule 42)
  B. No-entitlement user rejection (403 NO_ENTITLEMENT)
  C. Token tamper / scheduleId mismatch / path traversal rejection
  D. STOMP CONNECT + chat SUBSCRIBE/SEND + duplicate-login kick
  E. Lifecycle 6 states + ForceExit + Redis purge (schedule 43)

Prerequisites:
  - streaming-service (8088, dev profile) running
  - user-service (8085), creator-service (8080), ticket-service (8084) optional but
    HLS fetch in Scenario A requires creator-service to have uploaded movieId=1 and
    HLS assets to exist under storage.s3-path/movies/1/...
  - Redis @ localhost:6379, Kafka broker accessible
  - publish_events.py run beforehand (creates schedule 42/43 rows + entitlements)

Usage:
  pip install requests websocket-client redis
  python e2e_test.py
"""
import json
import re
import ssl
import sys
import threading
import time
import uuid
from collections import defaultdict
from typing import Optional

import redis
import requests
import websocket

STREAMING_SERVICE = "http://localhost:8088"
WS_URL = "ws://localhost:8088/ws/stream"
REDIS_HOST = "localhost"
REDIS_PORT = 6379

PASS = 0
FAIL = 0


# ───── Logging / Assertions ─────
def log(msg: str):
    print(f"  {msg}")


def log_header(msg: str):
    print("\n" + "=" * 60)
    print(f"  {msg}")
    print("=" * 60)


def log_step(msg: str):
    print(f"\n--- {msg} ---")


def assert_eq(label: str, actual, expected):
    global PASS, FAIL
    if actual == expected:
        PASS += 1
        log(f"[PASS] {label}: {actual}")
    else:
        FAIL += 1
        log(f"[FAIL] {label}: expected={expected!r}, actual={actual!r}")


def assert_true(label: str, condition: bool, detail: str = ""):
    global PASS, FAIL
    if condition:
        PASS += 1
        log(f"[PASS] {label} {detail}")
    else:
        FAIL += 1
        log(f"[FAIL] {label} {detail}")


def assert_contains(label: str, haystack: str, needle: str):
    assert_true(label, needle in haystack, f"needle={needle!r}")


# ───── HTTP Helpers ─────
def user_uuid(n: int) -> str:
    return f"00000000-0000-0000-0000-{n:012d}"


def headers(user_num: int) -> dict:
    return {"X-User-Id": user_uuid(user_num), "Content-Type": "application/json"}


def issue_session(user_num: int, schedule_id: int) -> requests.Response:
    return requests.post(
        f"{STREAMING_SERVICE}/api/streaming/sessions",
        headers=headers(user_num),
        json={"scheduleId": schedule_id},
        timeout=5,
    )


def get_manifest(schedule_id: int, file: str, token: str) -> requests.Response:
    return requests.get(
        f"{STREAMING_SERVICE}/api/streaming/{schedule_id}/{file}",
        params={"t": token},
        timeout=10,
    )


def trigger_lifecycle(kind: str, schedule_id: int) -> requests.Response:
    return requests.post(
        f"{STREAMING_SERVICE}/internal/test/jobs/{kind}/{schedule_id}",
        timeout=5,
    )


# ───── STOMP over WebSocket client ─────
class StompClient:
    """Minimal STOMP 1.2 client on top of websocket-client."""

    NUL = "\x00"

    def __init__(self, url: str):
        self.url = url
        self.ws: Optional[websocket.WebSocketApp] = None
        self.thread: Optional[threading.Thread] = None
        self.connected = threading.Event()
        self.closed = threading.Event()
        self.messages: dict[str, list] = defaultdict(list)   # destination → frames
        self.user_queue_messages: list = []                  # /user/queue/** frames
        self.error_frames: list = []
        self.lock = threading.Lock()

    def connect(self, token: str, timeout: float = 5.0):
        self.ws = websocket.WebSocketApp(
            self.url,
            subprotocols=["v12.stomp", "v11.stomp", "v10.stomp"],
            on_open=lambda w: self._on_open(w, token),
            on_message=self._on_message,
            on_error=self._on_error,
            on_close=self._on_close,
        )
        self.thread = threading.Thread(
            target=self.ws.run_forever,
            kwargs={"sslopt": {"cert_reqs": ssl.CERT_NONE}},
            daemon=True,
        )
        self.thread.start()
        if not self.connected.wait(timeout=timeout):
            raise TimeoutError("STOMP CONNECTED frame not received")

    def _on_open(self, ws, token: str):
        frame = self._build(
            "CONNECT",
            {"accept-version": "1.2", "host": "localhost", "token": token},
            body="",
        )
        ws.send(frame)

    def _on_message(self, ws, raw: str):
        for frame_text in raw.split(self.NUL):
            frame_text = frame_text.lstrip("\n")
            if not frame_text.strip():
                continue
            cmd, hdrs, body = self._parse(frame_text)
            if cmd == "CONNECTED":
                self.connected.set()
            elif cmd == "MESSAGE":
                dest = hdrs.get("destination", "")
                with self.lock:
                    self.messages[dest].append({"headers": hdrs, "body": body})
                    if dest.startswith("/user/"):
                        self.user_queue_messages.append({"headers": hdrs, "body": body})
            elif cmd == "ERROR":
                with self.lock:
                    self.error_frames.append({"headers": hdrs, "body": body})

    def _on_error(self, ws, err):
        self.error_frames.append({"headers": {}, "body": str(err)})

    def _on_close(self, ws, code, reason):
        self.closed.set()

    def _build(self, command: str, headers: dict, body: str = "") -> str:
        lines = [command]
        for k, v in headers.items():
            lines.append(f"{k}:{v}")
        lines.append("")
        lines.append(body)
        return "\n".join(lines) + self.NUL

    def _parse(self, text: str):
        lines = text.split("\n")
        cmd = lines[0]
        hdrs = {}
        i = 1
        while i < len(lines) and lines[i] != "":
            if ":" in lines[i]:
                k, v = lines[i].split(":", 1)
                hdrs[k] = v
            i += 1
        body = "\n".join(lines[i + 1:]) if i + 1 < len(lines) else ""
        return cmd, hdrs, body

    def subscribe(self, destination: str, sub_id: str):
        self.ws.send(self._build("SUBSCRIBE", {"id": sub_id, "destination": destination}))

    def send(self, destination: str, payload: dict):
        body = json.dumps(payload)
        self.ws.send(self._build(
            "SEND",
            {"destination": destination, "content-type": "application/json",
             "content-length": str(len(body.encode("utf-8")))},
            body=body,
        ))

    def wait_for_message(self, destination: str, timeout: float = 3.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self.lock:
                if self.messages.get(destination):
                    return self.messages[destination][0]
            time.sleep(0.1)
        return None

    def wait_for_user_message(self, destination_suffix: str, timeout: float = 5.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self.lock:
                for msg in self.user_queue_messages:
                    if destination_suffix in msg["headers"].get("destination", ""):
                        return msg
            time.sleep(0.1)
        return None

    def close(self):
        try:
            if self.ws:
                self.ws.send(self._build("DISCONNECT", {}))
                time.sleep(0.2)
                self.ws.close()
        except Exception:
            pass


# ───── Scenarios ─────
def scenario_a_hls(state: dict):
    log_header("Scenario A: Session Issue + HLS Manifest + Segment Rewrite")
    schedule_id = 42

    log_step("A-1: 세션 발급 (user0001, schedule 42)")
    resp = issue_session(1, schedule_id)
    assert_eq("issue_session status", resp.status_code, 200)
    if resp.status_code != 200:
        log(f"  body: {resp.text}")
        return
    body = resp.json()
    assert_true("response.sessionToken present", "sessionToken" in body)
    assert_true("response.sessionId present", "sessionId" in body)
    assert_true("response.manifestUrl present", "manifestUrl" in body)
    assert_true("response.wsEndpoint is /ws/stream",
                body.get("wsEndpoint") == "/ws/stream")
    token = body["sessionToken"]
    state["token_schedule_42"] = token

    log_step("A-2: HLS manifest 조회 (index.m3u8)")
    manifest_url = body["manifestUrl"]
    m = re.search(r"/api/streaming/\d+/([^?]+)", manifest_url)
    manifest_file = m.group(1) if m else "index.m3u8"
    log(f"  manifest_file parsed from manifestUrl: {manifest_file}")

    resp = get_manifest(schedule_id, manifest_file, token)
    if resp.status_code != 200:
        log(f"  [SKIP] manifest fetch status={resp.status_code} body={resp.text[:200]}")
        log("  (creator-service 업로드·HLS 생성 전이라면 이 단계는 정상적으로 실패합니다.)")
        return
    assert_eq("manifest content-type",
              resp.headers.get("Content-Type", "").split(";")[0].strip(),
              "application/vnd.apple.mpegurl")
    assert_eq("manifest cache-control", resp.headers.get("Cache-Control"), "no-store")
    text = resp.text
    assert_contains("manifest rewrite prefix",
                    text,
                    f"http://localhost:8088/api/streaming/{schedule_id}/")
    assert_contains("manifest rewrite token",
                    text,
                    f"?t={token}" if "%" not in token else "?t=")

    log_step("A-3: 재작성된 segment URL 첫 항목 fetch")
    segment_lines = [
        line for line in text.splitlines()
        if line and not line.startswith("#")
    ]
    if not segment_lines:
        log("  [SKIP] no segment lines in manifest (empty playlist?)")
        return
    seg_url = segment_lines[0]
    resp = requests.get(seg_url, timeout=10)
    assert_eq("segment fetch status", resp.status_code, 200)
    if resp.status_code == 200:
        assert_eq("segment content-type",
                  resp.headers.get("Content-Type", "").split(";")[0].strip(),
                  "video/mp2t")


def scenario_b_no_entitlement():
    log_header("Scenario B: Reject user without entitlement")
    resp = issue_session(99, 42)   # user0099 has no entitlement
    assert_eq("no-entitlement issue status", resp.status_code, 403)
    if resp.status_code == 403:
        body = resp.json() if resp.content else {}
        assert_eq("error code", body.get("code"), "NO_ENTITLEMENT")


def scenario_c_token_rejections(state: dict):
    log_header("Scenario C: Token / scheduleId / path rejections")
    token = state.get("token_schedule_42")
    if not token:
        # try to get a fresh token if scenario A skipped
        resp = issue_session(2, 42)
        if resp.status_code == 200:
            token = resp.json()["sessionToken"]
            state["token_schedule_42"] = token
    if not token:
        log("  [SKIP] no token available (scenario A and fallback both failed)")
        return

    log_step("C-1: 토큰 서명 변조 → 401 INVALID_TOKEN")
    tampered = token[:-2] + ("AA" if not token.endswith("AA") else "BB")
    resp = get_manifest(42, "index.m3u8", tampered)
    assert_eq("tampered token status", resp.status_code, 401)
    if resp.status_code == 401:
        body = resp.json() if resp.content else {}
        assert_eq("tampered token code", body.get("code"), "INVALID_TOKEN")

    log_step("C-2: scheduleId 불일치 → 409 SESSION_MISMATCH")
    resp = get_manifest(9999, "index.m3u8", token)
    assert_eq("mismatch status", resp.status_code, 409)
    if resp.status_code == 409:
        body = resp.json() if resp.content else {}
        assert_eq("mismatch code", body.get("code"), "SESSION_MISMATCH")

    log_step("C-3: path traversal → 400 INVALID_FILE_NAME")
    resp = get_manifest(42, "..%2Fsecret.txt", token)
    assert_true("traversal status in {400,404}", resp.status_code in (400, 404))
    if resp.status_code == 400:
        body = resp.json() if resp.content else {}
        assert_eq("traversal code", body.get("code"), "INVALID_FILE_NAME")


def scenario_d_stomp_chat_kick():
    log_header("Scenario D: STOMP CONNECT + Chat + Duplicate-login Kick")

    log_step("D-1: user0001 세션 발급 + STOMP CONNECT")
    resp = issue_session(1, 42)
    if resp.status_code != 200:
        log(f"  [SKIP] issue_session failed status={resp.status_code}")
        return
    token = resp.json()["sessionToken"]

    client_a = StompClient(WS_URL)
    try:
        client_a.connect(token, timeout=5)
        assert_true("client A STOMP CONNECTED", client_a.connected.is_set())
    except TimeoutError as e:
        assert_true("client A STOMP CONNECTED", False, str(e))
        return

    log_step("D-2: chat + state + user-queue SUBSCRIBE")
    client_a.subscribe("/topic/chat/schedule/42", "sub-chat")
    client_a.subscribe("/topic/state/schedule/42", "sub-state")
    client_a.subscribe("/user/queue/kick", "sub-kick")
    time.sleep(0.3)

    log_step("D-3: chat SEND → 본인 구독자가 수신")
    client_a.send("/app/chat/schedule/42", {"content": "hello e2e"})
    msg = client_a.wait_for_message("/topic/chat/schedule/42", timeout=3)
    assert_true("chat message received", msg is not None)
    if msg:
        body = json.loads(msg["body"])
        assert_eq("chat content", body.get("content"), "hello e2e")
        assert_true("chat nickname looks like 관람객#",
                    str(body.get("nickname", "")).startswith("관람객#"))

    log_step("D-4: user0001 재발급 → client A 가 /user/queue/kick 수신")
    resp = issue_session(1, 42)
    assert_eq("re-issue status", resp.status_code, 200)
    kick_msg = client_a.wait_for_user_message("/queue/kick", timeout=5)
    assert_true("kick frame received", kick_msg is not None)
    if kick_msg:
        try:
            body = json.loads(kick_msg["body"])
            assert_eq("kick reason", body.get("reason"), "DUPLICATE_LOGIN")
        except json.JSONDecodeError:
            log(f"  kick body (not json): {kick_msg['body']!r}")

    client_a.close()


def scenario_e_lifecycle_forceexit():
    log_header("Scenario E: Lifecycle 6 states + ForceExit + Redis purge")
    schedule_id = 43

    log_step("E-1: user0001 세션 발급 + STOMP SUBSCRIBE /topic/state/schedule/43")
    resp = issue_session(1, schedule_id)
    if resp.status_code != 200:
        log(f"  [SKIP] issue_session failed status={resp.status_code} body={resp.text[:200]}")
        return

    client = StompClient(WS_URL)
    token = resp.json()["sessionToken"]
    try:
        client.connect(token, timeout=5)
    except TimeoutError as e:
        assert_true("client STOMP CONNECTED", False, str(e))
        return
    client.subscribe(f"/topic/state/schedule/{schedule_id}", "sub-state-e")
    client.subscribe("/user/queue/kick", "sub-kick-e")
    time.sleep(0.3)

    log_step("E-2: lifecycle 6 트리거 순차 실행")
    lifecycle_order = [
        ("lobby-open", "LOBBY_OPEN"),
        ("starting-soon", "STARTING_SOON"),
        ("started", "STARTED"),
        ("ending-soon", "ENDING_SOON"),
        ("ended", "ENDED"),
        ("force-exit", "FORCE_EXIT"),
    ]
    received_states = []
    for kind, expected_state in lifecycle_order:
        resp = trigger_lifecycle(kind, schedule_id)
        assert_eq(f"trigger {kind} status", resp.status_code, 200)
        time.sleep(0.4)

    time.sleep(1.0)
    with client.lock:
        state_frames = list(client.messages.get(f"/topic/state/schedule/{schedule_id}", []))
    for frame in state_frames:
        try:
            body = json.loads(frame["body"])
            received_states.append(body.get("state"))
        except json.JSONDecodeError:
            received_states.append(frame["body"])

    expected_states = [s[1] for s in lifecycle_order]
    log(f"  expected: {expected_states}")
    log(f"  received: {received_states}")
    for exp in expected_states:
        assert_true(f"state broadcast contains {exp}", exp in received_states)

    log_step("E-3: ForceExit 이후 /user/queue/kick 수신")
    kick_msg = client.wait_for_user_message("/queue/kick", timeout=5)
    assert_true("force-exit kick frame received", kick_msg is not None)
    if kick_msg:
        try:
            body = json.loads(kick_msg["body"])
            assert_eq("kick reason", body.get("reason"), "FORCE_EXIT")
        except json.JSONDecodeError:
            log(f"  kick body (not json): {kick_msg['body']!r}")

    log_step("E-4: Redis purge 검증")
    try:
        r = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)
        r.ping()
        viewers_exists = r.exists(f"stream:viewers:schedule:{schedule_id}")
        assert_eq("viewers key purged", viewers_exists, 0)
        session_key = f"stream:session:user:{user_uuid(1)}"
        session_exists = r.exists(session_key)
        assert_eq("user session key evicted", session_exists, 0)
    except redis.exceptions.ConnectionError as e:
        log(f"  [SKIP] redis not reachable: {e}")

    client.close()


# ───── Main ─────
def health_check() -> bool:
    log_step("Health Check")
    try:
        resp = requests.get(f"{STREAMING_SERVICE}/actuator/health", timeout=3)
        log(f"streaming-service /actuator/health: {resp.status_code}")
        return resp.status_code in (200, 404)  # 404 if actuator not exposed — still OK
    except Exception as e:
        log(f"[ERROR] streaming-service not reachable: {e}")
        log("Please start streaming-service first (./gradlew bootRun)")
        return False


def main():
    print("\n" + "=" * 60)
    print("  streaming-service E2E Test Suite")
    print("=" * 60)

    if not health_check():
        sys.exit(1)

    state: dict = {}
    scenario_a_hls(state)
    scenario_b_no_entitlement()
    scenario_c_token_rejections(state)
    scenario_d_stomp_chat_kick()
    scenario_e_lifecycle_forceexit()

    print("\n" + "=" * 60)
    print(f"  RESULTS: {PASS} passed, {FAIL} failed, {PASS + FAIL} total")
    print("=" * 60)

    sys.exit(1 if FAIL > 0 else 0)


if __name__ == "__main__":
    main()
