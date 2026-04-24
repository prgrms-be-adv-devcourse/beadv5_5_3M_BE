"""
streaming-service E2E Setup: Kafka Event Publisher
===================================================
schedule_events.json + ticket_review_events.json 두 파일을 Kafka 로 발행한다.

streaming-service 컨슈머가 StringDeserializer + 수동 Jackson 역직렬화 (KafkaMessageUtil)
이므로 Python 은 JSON 문자열을 그대로 send 해도 정상 동작한다.

Usage:
  pip install kafka-python
  python publish_events.py

Prerequisites:
  - Kafka broker 접근 가능 (KAFKA_BOOTSTRAP_SERVERS 환경변수로 지정, 기본 localhost:9092)
  - streaming-service 기동 상태 (컨슈머가 group-id=streaming-service-dev 로 대기)
"""
import json
import os
import sys
from pathlib import Path

from kafka import KafkaProducer

BROKER = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
EVENT_FILES = ["schedule_events.json", "ticket_review_events.json"]


def main():
    here = Path(__file__).parent
    producer = KafkaProducer(
        bootstrap_servers=BROKER,
        value_serializer=lambda v: json.dumps(v, ensure_ascii=False).encode("utf-8"),
        key_serializer=lambda k: k.encode("utf-8") if k is not None else None,
        acks="all",
    )

    print(f"broker: {BROKER}")
    total = 0
    for filename in EVENT_FILES:
        path = here / filename
        if not path.exists():
            print(f"[WARN] not found: {path}")
            continue
        with open(path, encoding="utf-8") as f:
            events = json.load(f)
        for event in events:
            topic = event["topic"]
            value = event["value"]
            key = str(value.get("scheduleId", ""))
            producer.send(topic, key=key, value=value)
            total += 1
            print(f"  [{topic}] key={key} value.keys={list(value.keys())}")

    producer.flush(timeout=10)
    producer.close(timeout=5)
    print(f"published {total} events.")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"[ERROR] {e}", file=sys.stderr)
        sys.exit(1)