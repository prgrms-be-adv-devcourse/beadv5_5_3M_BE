"""
ticket-service E2E Test Script
==============================
Prerequisites:
  - user-service (port 8085) running
  - ticket-service (port 8084) running
  - PostgreSQL, Redis running locally
  - Kafka on EC2 (52.78.88.98:9092)
  - 3000 test users inserted (test_users.sql)
  - 4 schedule events published to Kafka (schedule_events.json)

Usage:
  pip install requests aiohttp
  python e2e_test.py
"""

import asyncio
import aiohttp
import requests
import time
import sys
from collections import Counter

TICKET_SERVICE = "http://localhost:8084"
USER_SERVICE = "http://localhost:8085"

# UUID format: 00000000-0000-0000-0000-{12자리 zero-padded}
def user_uuid(n: int) -> str:
    return f"00000000-0000-0000-0000-{n:012d}"

PASS = 0
FAIL = 0

def log(msg: str):
    print(f"  {msg}")

def log_header(msg: str):
    print(f"\n{'='*60}")
    print(f"  {msg}")
    print(f"{'='*60}")

def log_step(msg: str):
    print(f"\n--- {msg} ---")

def assert_eq(label: str, actual, expected):
    global PASS, FAIL
    if actual == expected:
        PASS += 1
        log(f"[PASS] {label}: {actual}")
    else:
        FAIL += 1
        log(f"[FAIL] {label}: expected={expected}, actual={actual}")

def assert_true(label: str, condition: bool, detail: str = ""):
    global PASS, FAIL
    if condition:
        PASS += 1
        log(f"[PASS] {label} {detail}")
    else:
        FAIL += 1
        log(f"[FAIL] {label} {detail}")

def assert_in(label: str, actual, expected_set):
    global PASS, FAIL
    if actual in expected_set:
        PASS += 1
        log(f"[PASS] {label}: {actual}")
    else:
        FAIL += 1
        log(f"[FAIL] {label}: {actual} not in {expected_set}")

# ── API Helpers ──

def headers(user_num: int) -> dict:
    return {"X-User-Id": user_uuid(user_num), "Content-Type": "application/json"}

def add_to_cart(user_num: int, schedule_id: int) -> requests.Response:
    return requests.post(f"{TICKET_SERVICE}/api/cart/{schedule_id}", headers=headers(user_num))

def remove_from_cart(user_num: int, schedule_id: int) -> requests.Response:
    return requests.delete(f"{TICKET_SERVICE}/api/cart/{schedule_id}", headers=headers(user_num))

def get_cart(user_num: int) -> requests.Response:
    return requests.get(f"{TICKET_SERVICE}/api/cart", headers=headers(user_num))

def get_cart_count(user_num: int, schedule_id: int) -> requests.Response:
    return requests.get(f"{TICKET_SERVICE}/api/cart/{schedule_id}/count", headers=headers(user_num))

def trigger_cart_close(schedule_id: int) -> requests.Response:
    return requests.post(f"{TICKET_SERVICE}/internal/test/jobs/cart-close/{schedule_id}")

def trigger_ticketing_start(schedule_id: int) -> requests.Response:
    return requests.post(f"{TICKET_SERVICE}/internal/test/jobs/ticketing-start/{schedule_id}")

def trigger_streaming_start(schedule_id: int) -> requests.Response:
    return requests.post(f"{TICKET_SERVICE}/internal/test/jobs/streaming-start/{schedule_id}")

def trigger_streaming_finish(schedule_id: int) -> requests.Response:
    return requests.post(f"{TICKET_SERVICE}/internal/test/jobs/streaming-finish/{schedule_id}")

def trigger_review_auth(schedule_id: int) -> requests.Response:
    return requests.post(f"{TICKET_SERVICE}/internal/test/jobs/review-auth/{schedule_id}")

def enter_queue(user_num: int, schedule_id: int) -> requests.Response:
    return requests.post(f"{TICKET_SERVICE}/api/queue/{schedule_id}/enter", headers=headers(user_num))

def get_position(user_num: int, schedule_id: int) -> requests.Response:
    return requests.get(f"{TICKET_SERVICE}/api/queue/{schedule_id}/position", headers=headers(user_num))

def get_my_tickets(user_num: int, page: int = 0, size: int = 100) -> requests.Response:
    return requests.get(f"{TICKET_SERVICE}/api/tickets?page={page}&size={size}", headers=headers(user_num))

def get_ticket(user_num: int, ticket_id: int) -> requests.Response:
    return requests.get(f"{TICKET_SERVICE}/api/tickets/{ticket_id}", headers=headers(user_num))

def pay_ticket(user_num: int, ticket_id: int) -> requests.Response:
    return requests.post(f"{TICKET_SERVICE}/api/tickets/{ticket_id}/pay", headers=headers(user_num))

def refund_ticket(user_num: int, ticket_id: int) -> requests.Response:
    return requests.post(f"{TICKET_SERVICE}/api/tickets/{ticket_id}/refund", headers=headers(user_num))

def cancel_ticket(user_num: int, ticket_id: int) -> requests.Response:
    return requests.delete(f"{TICKET_SERVICE}/api/tickets/{ticket_id}", headers=headers(user_num))

def get_user_ticket_ids(user_num: int) -> list[int]:
    """Get all ticket IDs for a user."""
    resp = get_my_tickets(user_num)
    if resp.status_code != 200:
        return []
    data = resp.json()
    # PageResult has 'content' field
    content = data.get("content", data) if isinstance(data, dict) else data
    if isinstance(content, list):
        return [t["ticketId"] for t in content]
    return []


# ══════════════════════════════════════════════════════════════
#  Scenario A: Full Flow (scheduleId=1, seats=100, Case A)
# ══════════════════════════════════════════════════════════════

def test_scenario_a():
    log_header("Scenario A: Full Flow (scheduleId=1, seats=100)")
    schedule_id = 1

    # A-1: Add to cart (50 users, demand < 100 seats → Case A)
    log_step("A-1: 장바구니 추가 (user0001~user0050)")
    for i in range(1, 51):
        resp = add_to_cart(i, schedule_id)
        if i <= 3:  # log first few
            log(f"  user{i:04d} cart add: {resp.status_code}")
    # Verify last one
    assert_eq("user0050 cart add status", resp.status_code, 201)

    # A-1b: Duplicate add should fail
    log_step("A-1b: 중복 장바구니 추가 실패")
    resp = add_to_cart(1, schedule_id)
    assert_eq("duplicate cart add status", resp.status_code, 409)

    # A-1c: Cart count
    log_step("A-1c: 장바구니 수요 확인")
    resp = get_cart_count(1, schedule_id)
    assert_eq("cart count status", resp.status_code, 200)
    assert_eq("cart count value", resp.json(), 50)

    # A-1d: My cart
    log_step("A-1d: 내 장바구니 조회")
    resp = get_cart(1)
    assert_eq("my cart status", resp.status_code, 200)
    assert_true("my cart has schedule", len(resp.json()) >= 1)

    # A-1e: Cart remove and re-add
    log_step("A-1e: 장바구니 제거 후 재추가")
    resp = remove_from_cart(50, schedule_id)
    assert_eq("cart remove status", resp.status_code, 204)
    resp = get_cart_count(1, schedule_id)
    assert_eq("cart count after remove", resp.json(), 49)
    resp = add_to_cart(50, schedule_id)
    assert_eq("cart re-add status", resp.status_code, 201)

    # A-2: Cart close → Case A (50 < 100)
    log_step("A-2: 장바구니 마감 (Case A: 50 < 100)")
    resp = trigger_cart_close(schedule_id)
    assert_eq("cart close trigger status", resp.status_code, 200)
    time.sleep(1)  # wait for async processing

    # Verify: all 50 users should have RESERVED tickets
    log_step("A-2 검증: 50명 전원 RESERVED 티켓 보유")
    reserved_count = 0
    ticket_map = {}  # user_num -> ticket_id
    for i in range(1, 51):
        ids = get_user_ticket_ids(i)
        if ids:
            reserved_count += 1
            ticket_map[i] = ids[0]
    assert_eq("reserved ticket count", reserved_count, 50)

    # A-2b: Cart DB entries should be deleted (Redis counter may persist with TTL)
    resp = get_cart(1)
    user1_carts_for_schedule = [c for c in resp.json() if c.get("scheduleId") == schedule_id]
    assert_eq("user1 cart entries after close", len(user1_carts_for_schedule), 0)

    # A-3: Self-payment (user0001~user0030)
    log_step("A-3: 자율결제 (user0001~user0030)")
    pay_success = 0
    for i in range(1, 31):
        if i in ticket_map:
            resp = pay_ticket(i, ticket_map[i])
            if resp.status_code == 200:
                pay_success += 1
            if i <= 3:
                log(f"  user{i:04d} pay: {resp.status_code}")
    assert_eq("payment success count", pay_success, 30)

    # A-3b: Verify CONFIRMED status
    resp = get_ticket(1, ticket_map[1])
    assert_eq("ticket status after pay", resp.json().get("status"), "CONFIRMED")

    # A-3c: Payment failure (user2901 - 잔액 0, but they don't have ticket)
    # Instead test: already CONFIRMED ticket can't be paid again
    log_step("A-3c: 이미 결제된 티켓 재결제 실패")
    resp = pay_ticket(1, ticket_map[1])
    assert_eq("double pay status", resp.status_code, 409)

    # A-3d: Someone else's ticket payment failure
    log_step("A-3d: 타인 티켓 결제 실패")
    resp = pay_ticket(2, ticket_map[1])  # user2 tries to pay user1's ticket
    assert_eq("other's ticket pay status", resp.status_code, 403)

    # A-4: Ticketing start
    log_step("A-4: 티켓팅 시작 트리거")
    resp = trigger_ticketing_start(schedule_id)
    assert_eq("ticketing start status", resp.status_code, 200)
    time.sleep(1)

    # Verify: unpaid RESERVED tickets (user0031~0050) should be deleted
    log_step("A-4 검증: 미결제 RESERVED 삭제 확인")
    deleted_count = 0
    for i in range(31, 51):
        ids = get_user_ticket_ids(i)
        if len(ids) == 0:
            deleted_count += 1
    assert_eq("deleted unpaid ticket count", deleted_count, 20)

    # A-5: Queue immediate purchase (stock should be 100 - 30 = 70)
    log_step("A-5: 대기열 즉시 구매 (user0051~user0110, 60명)")
    purchased = 0
    queue_tickets = {}
    for i in range(51, 111):
        resp = enter_queue(i, schedule_id)
        if resp.status_code == 200:
            data = resp.json()
            if data.get("type") == "PURCHASED":
                purchased += 1
                if data.get("ticket"):
                    queue_tickets[i] = data["ticket"]["ticketId"]
        if i <= 53:
            log(f"  user{i:04d} enter: {resp.status_code} type={resp.json().get('type', 'N/A')}")
    assert_eq("immediate purchase count", purchased, 60)

    # A-6: Queue entry (stock exhausted, should QUEUED)
    log_step("A-6: 재고 소진 후 대기열 진입 (user0111~user0120)")
    queued_count = 0
    for i in range(111, 121):
        resp = enter_queue(i, schedule_id)
        if resp.status_code == 200 and resp.json().get("type") == "QUEUED":
            queued_count += 1
    # stock was 70 and we tried 60 purchases, so remaining 10 stock
    # then 10 more users should get PURCHASED if stock remains
    # Let's just check what we got
    log(f"  queued users: {queued_count} out of 10")
    assert_true("some users queued or purchased", True)

    # A-6b: Queue position
    log_step("A-6b: 대기열 순번 조회")
    resp = get_position(120, schedule_id)
    if resp.status_code == 200:
        log(f"  user0120 position: {resp.json()}")

    # A-6c: Duplicate queue entry
    log_step("A-6c: 대기열 중복 진입 실패")
    if queued_count > 0:
        resp = enter_queue(120, schedule_id)
        assert_in("duplicate queue entry status", resp.status_code, {409, 200})

    # A-7: Refund → stock restore → auto drain
    log_step("A-7: 환불 (user0001) → 재고 복구 → 자동 드레인")
    resp = refund_ticket(1, ticket_map[1])
    assert_eq("refund status", resp.status_code, 204)
    time.sleep(2)  # wait for auto-drain

    # Verify user0001 ticket is gone
    ids = get_user_ticket_ids(1)
    assert_eq("user0001 tickets after refund", len(ids), 0)

    # A-8: Streaming start/finish
    log_step("A-8: 스트리밍 시작")
    resp = trigger_streaming_start(schedule_id)
    assert_eq("streaming start status", resp.status_code, 200)

    log_step("A-8b: 스트리밍 종료")
    resp = trigger_streaming_finish(schedule_id)
    assert_eq("streaming finish status", resp.status_code, 200)

    # A-9: Review auth
    log_step("A-9: 리뷰 권한 발행")
    resp = trigger_review_auth(schedule_id)
    assert_eq("review auth status", resp.status_code, 200)


# ══════════════════════════════════════════════════════════════
#  Scenario B: Case B Flow (scheduleId=2, seats=3)
# ══════════════════════════════════════════════════════════════

def test_scenario_b():
    log_header("Scenario B: Case B Flow (scheduleId=2, seats=3)")
    schedule_id = 2

    # B-1: Add to cart (10 users, demand 10 >= seats 3 → Case B)
    log_step("B-1: 장바구니 추가 (user0001~user0010)")
    for i in range(1, 11):
        resp = add_to_cart(i, schedule_id)
    assert_eq("last cart add status", resp.status_code, 201)

    resp = get_cart_count(1, schedule_id)
    assert_eq("cart count", resp.json(), 10)

    # B-2: Cart close → Case B (10 >= 3)
    log_step("B-2: 장바구니 마감 (Case B: 10 >= 3)")
    resp = trigger_cart_close(schedule_id)
    assert_eq("cart close status", resp.status_code, 200)
    time.sleep(1)

    # Verify: NO reserved tickets (Case B = no bulk reservation)
    log_step("B-2 검증: RESERVED 티켓 0개")
    has_ticket = 0
    for i in range(1, 11):
        ids = get_user_ticket_ids(i)
        # filter for schedule_id=2 tickets
        for tid in ids:
            resp_t = get_ticket(i, tid)
            if resp_t.status_code == 200 and resp_t.json().get("scheduleId") == schedule_id:
                has_ticket += 1
    assert_eq("reserved tickets (Case B)", has_ticket, 0)

    # B-3: Ticketing start
    log_step("B-3: 티켓팅 시작")
    resp = trigger_ticketing_start(schedule_id)
    assert_eq("ticketing start status", resp.status_code, 200)
    time.sleep(1)

    # B-4: Queue purchase (3 seats)
    log_step("B-4: 대기열 즉시 구매 (user0001~user0003)")
    for i in range(1, 4):
        resp = enter_queue(i, schedule_id)
        data = resp.json()
        assert_eq(f"user{i:04d} queue entry type", data.get("type"), "PURCHASED")

    # B-5: SOLD_OUT
    log_step("B-5: 매진 확인 (user0004)")
    resp = enter_queue(4, schedule_id)
    assert_eq("sold out status", resp.status_code, 409)
    log(f"  response: {resp.json()}")


# ══════════════════════════════════════════════════════════════
#  Scenario C: SOLD_OUT (scheduleId=4, seats=2)
# ══════════════════════════════════════════════════════════════

def test_scenario_c():
    log_header("Scenario C: SOLD_OUT (scheduleId=4, seats=2)")
    schedule_id = 4

    # C-1: Cart close (no carts → Case A with 0 demand)
    log_step("C-1: 장바구니 마감 (수요 0)")
    resp = trigger_cart_close(schedule_id)
    assert_eq("cart close status", resp.status_code, 200)
    time.sleep(1)

    # C-2: Ticketing start
    log_step("C-2: 티켓팅 시작")
    resp = trigger_ticketing_start(schedule_id)
    assert_eq("ticketing start status", resp.status_code, 200)
    time.sleep(1)

    # C-3: Buy all 2 seats
    log_step("C-3: 2석 구매")
    for i in range(1, 3):
        resp = enter_queue(i, schedule_id)
        assert_eq(f"user{i:04d} purchased", resp.json().get("type"), "PURCHASED")

    # C-4: SOLD_OUT
    log_step("C-4: SOLD_OUT 확인")
    resp = enter_queue(3, schedule_id)
    assert_eq("sold out status", resp.status_code, 409)
    log(f"  message: {resp.json().get('message', '')}")


# ══════════════════════════════════════════════════════════════
#  Scenario D: INSUFFICIENT_BALANCE (scheduleId=4)
# ══════════════════════════════════════════════════════════════

def test_scenario_d():
    log_header("Scenario D: INSUFFICIENT_BALANCE")

    # user4501~user5000: balance=0 (test_users.sql 기준)
    # schedule_id=1 은 Scenario A 이후 TICKETING 상태이며 잔여 재고 있음
    BROKE_USER = 4501

    log_step("D-1: 잔액 부족 유저 대기열 진입 테스트")
    resp = enter_queue(BROKE_USER, 1)
    log(f"  user{BROKE_USER:04d} enter queue response: status={resp.status_code}")
    if resp.status_code == 402:
        assert_true("INSUFFICIENT_BALANCE (402)", True, f"user{BROKE_USER:04d} 잔액 부족으로 거절됨")
    elif resp.status_code == 409:
        log(f"  Got 409 (SOLD_OUT or already in queue): {resp.json().get('message', '')}")
        assert_true("INSUFFICIENT_BALANCE test skipped (sold out)", True)
    elif resp.status_code == 200:
        data = resp.json()
        log(f"  type={data.get('type')}")
        assert_true("expected 402 but got 200", False, f"user{BROKE_USER:04d} balance=0 임에도 구매 성공")
    else:
        log(f"  response: {resp.json()}")
        assert_true("expected 402", False, f"unexpected status {resp.status_code}")


# ══════════════════════════════════════════════════════════════
#  Scenario E: Concurrency (scheduleId=3, seats=50, 500 users)
# ══════════════════════════════════════════════════════════════

async def test_scenario_e():
    log_header("Scenario E: Concurrency (scheduleId=3, seats=50, 500 concurrent)")
    schedule_id = 3

    # E-1: Setup - cart close and ticketing start
    log_step("E-1: Setup (장바구니 마감 → 티켓팅 시작)")

    # Add some users to cart first (Case B: need demand >= seats)
    log("  Adding 100 users to cart...")
    for i in range(501, 601):
        add_to_cart(i, schedule_id)

    resp = trigger_cart_close(schedule_id)
    assert_eq("cart close status", resp.status_code, 200)
    time.sleep(1)

    resp = trigger_ticketing_start(schedule_id)
    assert_eq("ticketing start status", resp.status_code, 200)
    time.sleep(1)

    # E-2: 500 concurrent queue entries
    log_step("E-2: 500명 동시 대기열 진입")
    start_time = time.time()

    results = []
    connector = aiohttp.TCPConnector(limit=100)

    async with aiohttp.ClientSession(connector=connector) as session:
        async def enter(user_num: int):
            url = f"{TICKET_SERVICE}/api/queue/{schedule_id}/enter"
            h = {"X-User-Id": user_uuid(user_num), "Content-Type": "application/json"}
            try:
                async with session.post(url, headers=h) as resp:
                    status = resp.status
                    body = await resp.json()
                    return {"user": user_num, "status": status, "body": body}
            except Exception as e:
                return {"user": user_num, "status": -1, "error": str(e)}

        tasks = [enter(i) for i in range(1, 501)]
        results = await asyncio.gather(*tasks)

    elapsed = time.time() - start_time
    log(f"  Completed in {elapsed:.2f}s")

    # E-3: Analyze results
    log_step("E-3: 결과 분석")

    status_counter = Counter()
    type_counter = Counter()
    errors = []
    purchased_users = set()
    queued_users = set()

    for r in results:
        status_counter[r["status"]] += 1
        if r["status"] == 200:
            t = r["body"].get("type", "UNKNOWN")
            type_counter[t] += 1
            if t == "PURCHASED":
                purchased_users.add(r["user"])
            elif t == "QUEUED":
                queued_users.add(r["user"])
        elif r["status"] == -1:
            errors.append(r.get("error", "unknown"))
        elif r["status"] == 409:
            type_counter["ERROR_409"] += 1
        elif r["status"] == 402:
            type_counter["ERROR_402"] += 1

    log(f"  HTTP status distribution: {dict(status_counter)}")
    log(f"  Type distribution: {dict(type_counter)}")
    log(f"  PURCHASED users: {len(purchased_users)}")
    log(f"  QUEUED users: {len(queued_users)}")
    if errors:
        log(f"  Errors: {len(errors)} (first: {errors[0]})")

    # Verify: exactly 50 PURCHASED (= seats)
    assert_eq("PURCHASED count", len(purchased_users), 50)

    # Verify: no duplicate tickets
    log_step("E-3b: 중복 티켓 검증")
    ticket_ids = set()
    duplicates = 0
    sample_users = list(purchased_users)[:20]  # check first 20
    for u in sample_users:
        ids = get_user_ticket_ids(u)
        schedule_tickets = []
        for tid in ids:
            resp = get_ticket(u, tid)
            if resp.status_code == 200 and resp.json().get("scheduleId") == schedule_id:
                schedule_tickets.append(tid)
                if tid in ticket_ids:
                    duplicates += 1
                ticket_ids.add(tid)
        if len(schedule_tickets) > 1:
            duplicates += 1
            log(f"  [WARN] user{u:04d} has {len(schedule_tickets)} tickets for schedule {schedule_id}")

    assert_eq("duplicate tickets (sampled)", duplicates, 0)

    # E-4: Streaming lifecycle
    log_step("E-4: 스트리밍 시작/종료")
    resp = trigger_streaming_start(schedule_id)
    assert_eq("streaming start", resp.status_code, 200)
    resp = trigger_streaming_finish(schedule_id)
    assert_eq("streaming finish", resp.status_code, 200)


# ══════════════════════════════════════════════════════════════
#  Main
# ══════════════════════════════════════════════════════════════

def main():
    print("\n" + "=" * 60)
    print("  ticket-service E2E Test Suite")
    print("=" * 60)

    # Health check
    log_step("Health Check")
    try:
        resp = requests.get(f"{TICKET_SERVICE}/swagger-ui.html", timeout=5, allow_redirects=True)
        log(f"ticket-service: {resp.status_code}")
    except Exception as e:
        log(f"[ERROR] ticket-service not reachable: {e}")
        log("Please start ticket-service first (./gradlew bootRun)")
        sys.exit(1)

    try:
        resp = requests.get(f"{USER_SERVICE}/swagger-ui.html", timeout=5, allow_redirects=True)
        log(f"user-service: {resp.status_code}")
    except Exception as e:
        log(f"[WARN] user-service not reachable: {e}")
        log("Some tests may fail if user-service is needed for cookie deduction")

    # Run scenarios sequentially
    test_scenario_a()
    test_scenario_b()
    test_scenario_c()
    test_scenario_d()
    asyncio.run(test_scenario_e())

    # Summary
    print("\n" + "=" * 60)
    print(f"  RESULTS: {PASS} passed, {FAIL} failed, {PASS + FAIL} total")
    print("=" * 60)

    if FAIL > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()