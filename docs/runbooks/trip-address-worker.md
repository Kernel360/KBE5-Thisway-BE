# 운행 주소 자동 보정 worker

2026-09-07. V10을 적용한 내부 애플리케이션에서 사용하는 절차다. 실제 운영 실행 기록은 아니다.
기존 [수동 보정](trip-address-enrichment.md)에 durable discovery/retry를 추가하며,
기존 AFTER_COMMIT 경로도 유지한다.

## 설정과 실행

스케줄은 기본 `-`로 비활성이다. 테스트에서도 자동 HTTP 호출을 하지 않는다.
API 키·quota·배포 환경을 확인한 담당자가 아래 설정으로 활성화한다.

```yaml
spring.task.scheduling.pool.size: 2 # 주소 HTTP 대기가 통계 scheduler를 점유하지 않도록 분리 여유 확보
thisway:
  trip-address:
    worker:
      cron: "0 * * * * *"          # UTC, 매분; "-"이면 비활성
      scan-batch-size: 100         # 한 번에 검색할 Trip ID 수, 1..1000
      max-lookups: 20             # 한 번 실행에서 claim할 주소 side 수, 1..100
      max-attempts: 5              # claim 시 증가, 1..20; crash도 시도를 소비
      initial-backoff-seconds: 60
      max-backoff-seconds: 3600    # 최대 86400초
      lease-seconds: 30           # 5..3600초; DB claim 유효 기간
```

기본 5회는 실패 뒤 60·120·240·480초 이상 간격을 두고 마지막 실패는 `EXHAUSTED`에 남는다.
실행 주기·앞선 처리량 때문에 실제 재시도는 더 늦을 수 있다. 백오프는 sleep이 아니라 DB의
`next_attempt_at` 조건이다. `max-lookups`는 인스턴스별 실행 상한이며 전역 API quota가 아니다.
일회성 내부 실행은 Spring proxy `TripAddressWorker.runOnce()`를 사용하고 반환 집계를 확인한다.
새 HTTP 관리 API/원격 실행 CLI는 제공하지 않는다.
위 pool 설정 없이 단일 thread scheduler를 사용하면 주소 HTTP 대기가 다른 예약 작업을 지연시킬 수 있다.

## 상태 확인

좌표·주소·HTTP 예외 본문·Kakao key를 일반 로그나 티켓에 복사하지 않는다.
업무 counter는 `thisway.trip.address.worker`의 고정 `outcome` 태그다.
Prometheus에서는 `thisway_trip_address_worker_total{outcome="exhausted"}` 등으로 확인한다.
counter는 현재 JVM의 누적 이벤트다. restart 후에도 남는 실제 backlog는 DB를 조회한다.

```sql
SELECT status, COUNT(*) AS sides, MIN(next_attempt_at) AS oldest_due
FROM trip_address_retry GROUP BY status;

SELECT trip_id, side, attempts, next_attempt_at, lease_until
FROM trip_address_retry
WHERE status = 'EXHAUSTED'
ORDER BY next_attempt_at, trip_id, side LIMIT 100;

SELECT last_trip_id, high_watermark
FROM trip_address_scan_state WHERE id = 1;
```

고정 high watermark까지 순회한 후 0으로 돌아가므로 오래된 Trip의 나중에 도착한 OFF 좌표도 찾는다.
`EXHAUSTED` 행이 있어도 다음 ID 탐색과 다른 side 처리는 계속된다.
대상이 많으면 한 바퀴에 여러 실행이 필요하다. 큰 DB의 scan 처리 비용과 복구 지연은 별도 측정 대상이다.

## 실패 원인 수정 후 재개

1. 원인(credential/quota/외부 장애/주소가 없는 좌표)을 확인한다. `EXHAUSTED`는 성공이 아니다.
2. 해당 Trip/side가 여전히 주소를 필요로 하는지 먼저 읽어 본다. 아래 prepared statement의
   `trip_id`와 `side`에 점검한 한 대상을 bind한다. 스케줄을 끌 때는 설정을 `-`로 바꾸고 현재 실행 완료를 확인한다.
3. 점검한 실패 행 하나만 다음 prepared statement로 재개한다. `RUNNING` claim은 강제로 덮어쓰지 않는다.

```sql
UPDATE trip_address_retry
SET status='PENDING', attempts=0, next_attempt_at=UTC_TIMESTAMP(6)
WHERE trip_id=? AND side=? AND status='EXHAUSTED';
```

4. 수정 행 수 1인지 확인하고 다음 실행/승인된 내부 `runOnce()` 뒤 주소와 retry 행을 재조회한다.
   성공 또는 더 이상 보정이 필요 없는 대상은 retry 행이 제거된다.
   주소가 이미 채워진 `EXHAUSTED` 행도 재개하면 외부 호출 없이 정리한다.
5. 좌표가 수정되어도 exhausted 시도 횟수를 자동 초기화하지 않는다. 원인 확인 후 위 절차로 재개한다.

실행 중 프로세스가 죽으면 DB `lease_until` 이후 다른 worker가 이어서 claim한다.
이미 주소를 쓴 뒤 죽은 경우에는 외부 재호출 없이 완료 행을 정리한다.
마지막 claim 중 죽었다면 lease 만료 후 `EXHAUSTED`로 남아 담당자의 점검을 받는다.

## 보장 범위

- claim transaction이 commit된 뒤 외부 HTTP를 호출한다. 좌표/주소 조건부 update를 유지한다.
- 유효한 lease 동안 worker 간 같은 side 중복 호출을 막는다. 오래 걸린 HTTP가 lease를 넘기거나
  기존 AFTER_COMMIT과 겹치면 외부 호출 중복은 가능하다. 외부 API의 exactly-once 호출을 보장하지 않는다.
- claim token은 이전 worker가 새 retry 상태를 완료·재시도로 덮어쓰는 것을 막는다.
  Trip 주소는 null과 조회 당시 좌표 조건이 맞는 첫 유효 결과만 기록한다.
- DB 장애 뒤 lease 복구는 가능하지만 DB 자체 복구, provider의 전역 quota, DNS/전체 HTTP deadline,
  좌표 변경 이력, retry 실패 종류별 정책은 이 worker가 해결하지 않는다.
- 전용 통합 테스트는 실제 MySQL에서 독립 worker 두 객체와 두 실행 thread를 사용한다.
  실제 JVM kill/외부 Kakao 장애/여러 운영 host의 배포 시연은 실행하지 않았다.

설계·검증·학습 기록: [CHANGE-039](../portfolio/work-logs/2026-09-07-trip-address-worker.md).
