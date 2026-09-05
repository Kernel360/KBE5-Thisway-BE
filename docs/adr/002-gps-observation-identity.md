# ADR-002: 신규 GPS 관측값의 정확 중복 저장 방지

- 날짜: 2026-09-05
- 결정: normalized observation v1 fingerprint + MySQL unique index.

## 문제와 선택

현재 packet에는 event ID와 sequence가 없다. `(mdn, occurredTime)`만 unique로 만들면 같은 초의 다른 측정값을 버리게 된다. 원본 JSON hash는 숫자 표기나 min/sec 생략의 차이에 민감하고, packet 전체 hash는 packet 재분할/내부 중복을 잡지 못한다. producer UUID 도입은 장기적으로 유리하지만 재전송 시 동일 UUID 유지와 구버전 계약 전환이 필요하다.

이번에는 vehicleId, mdn, 유효 발생 시각, GPS 상태, 위경도, 방향, 속도, 누적 거리, 배터리 값이 모두 같은 **관측값**을 중복으로 정의한다. 동일 시각에 값이 다르면 둘 다 보존한다. device event identity를 확정한 것이 아니며 same-second 동일 측정의 별개 이벤트를 구별할 수 없다.

## key와 저장

version 1을 포함한 binary serialization에 SHA-256을 적용한다. 문자열은 UTF-8 byte length를 앞에 기록해 단순 구분자 결합의 모호성을 없앤다. 숫자는 고정폭, 시간은 초 정밀도 LocalDateTime으로 정규화한다. DB에도 초 이하를 제거하여 쓰며 DOUBLE의 -0.0/+0.0은 같은 key로 취급한다. timezone 변환을 새로 수행하지 않는다. 이미 converter가 만든 시각을 사용한다.

`gps_log.event_key BINARY(32)`에 unique index를 두고 batch insert에 `ON DUPLICATE KEY UPDATE event_key=event_key`를 적용한다. 기존 측정값은 덮어쓰지 않는다. INSERT IGNORE를 쓰지 않아 FK 등 무결성 오류는 실패한다. 한 SQL과 기존 repository transaction이 batch 원자성을 유지한다. 여러 batch의 겹치는 key는 unsigned key 순서로 정렬해 잠금 순서를 맞춘다. 모든 종류의 deadlock 방지를 주장하지 않는다.

SHA-256 collision 확률은 작지만 0은 아니며 별도 collision 원문 비교는 없다. fingerprint 형식이나 의미를 바꾸면 migration/호환 전략 없이 v1 코드를 변경해서는 안 된다.

## 기존 데이터와 경계

V3는 nullable key를 추가한다. 기존 행에는 key를 추정하거나 backfill하지 않고 중복 행도 삭제하지 않는다. 따라서 도입 전 행과 새 재전송 간 중복은 여전히 발생할 수 있다. 구 writer가 NULL key로 삽입하면 dedup을 우회하므로 schema와 새 writer 배포 조합을 맞춰야 한다. 기존 이력 없는 DB는 ADR-001의 전환 절차가 먼저다.

차량 재연결로 MDN이 다른 vehicle에 매핑되면 key도 달라진다. 현재 매핑을 읽는 구조이므로 재연결 전 이벤트가 지연 도착한 경우를 완전히 해결하지 않는다. 시간 복원/장치 identity 계약은 후속 작업이다.

RabbitMQ 저장 이후 ack 유실로 재전달되더라도 같은 정규화 값은 DB unique key로 재저장되지 않는다. 다만 이번에는 실제 broker ack 장애를 주입하지 않았고 repository 동시성으로 검증했다. 별도 fan-out은 여전히 중복 방송할 수 있다. 전체 exactly-once나 무손실 수집으로 표현하지 않는다.

## 비용

행마다 32-byte key와 unique index 저장·계산 비용이 추가된다. duplicate insert도 auto-increment gap을 만들 수 있어 ID 연속성을 기대하지 않는다. affected-row 수는 driver 설정에 따라 달라지므로 입력 건수를 신규 저장 건수로 보고하지 않는다. 기존 로그 문구도 입력 건수임을 명시했다. throughput/대용량 ALTER 비용은 미측정이다. 같은 timestamp의 다른 측정값 간 insert ID 순서는 hash 정렬 영향이 있어 source order 보장으로 사용하지 않는다.
