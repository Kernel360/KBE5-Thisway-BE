# CHANGE-039: 누락 운행 주소의 durable 자동 보정

## 메타데이터

- 날짜: 2026-09-07
- 작업자: 개인 현대화, AI 구현·검증 보조
- 브랜치/기준: `codex/statistics-batch-restart@ef74fcf`, 기존 dirty 변경 보존
- 상태: Verified (전용 MySQL 검증 통과, 통합 전체 회귀는 최종 작업 기록 참조)
- 선행: [CHANGE-026](2026-09-06-trip-address-enrichment.md)

## 1. 문제와 근거

원 팀의 주소 변환 기능을 CHANGE-026에서 핵심 운행 commit 이후로 분리했다.
그 이벤트는 메모리 이벤트이므로 commit 직후 crash에는 주소가 채워지지 않으며, 외부 실패에도
좌표/null 주소만 남고 수동 호출 없이는 복구되지 않았다. 원 팀의 Trip 전체가 이번 개인 기여는 아니다.
이번 변경은 후속 개인 현대화인 자동 discovery, durable retry, 실패 운영 절차에 한정한다.

## 2. Acceptance criteria

- [x] 이벤트가 전달되지 않은 ON/OFF 주소를 DB에서 찾아 보정한다.
- [x] HTTP 동안 transaction/row lock을 유지하지 않고 기존 좌표/null 조건부 update를 유지한다.
- [x] 재시도 횟수/간격과 scan cursor가 worker 재생성 후에도 유지된다.
- [x] 영구실패·연속 신규 Trip이 있어도 고정 high watermark를 향해 순회한다.
- [x] 다중 worker의 유효 lease 중 중복 호출, lease 만료 복구, stale claim 완료를 검증한다.
- [x] 한도 초과는 EXHAUSTED에 남고 점검·재개할 수 있다. 기본 스케줄은 비활성이다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| 매번 최근 null 주소 LIMIT 조회 | 코드가 작음 | 영구실패가 같은 LIMIT을 점유하고 오래된 Trip이 밀림 | 거절 |
| in-memory cursor/backoff | schema 불필요 | restart 때 실패 횟수·간격과 순회 진도가 초기화됨 | 거절 |
| 별도 retry 상태와 durable cursor | restart 복구, 제한된 시도, 순회·운영 점검 가능 | migration과 lifecycle 필요; exactly-once 외부 호출은 아님 | 채택 |

null 주소와 좌표가 이미 durable 원천이므로 모든 Trip 변경 경로에 outbox event 쓰기를 결합하는 대신
bounded keyset scan을 선택했다. 이 방식의 복구 지연은 scan 주기·데이터량에 의존한다.

## 4. 구현과 실행 흐름

- `V10__trip_address_retry_state.sql`: side별 retry 상태/lease와 singleton scan cursor.
- `TripAddressWorker`: 발견→claim→HTTP→token 조건 완료, 최대 시도·지수 백오프, 고정 outcome counter.
- `TripAddressEnrichment`: 기존 boolean 진입점 유지, UPDATED/NO_LONGER_NEEDED/RETRY 상세 결과 추가.
- `TripAddressWorkerIntegrationTest`: 실제 MySQL 기반 동작·실패·경쟁 fixture.
- [운영 절차](../../runbooks/trip-address-worker.md): 설정, 상태 점검, EXHAUSTED 재개.

1. 짧은 transaction에서 scan cursor를 잠그고 한 batch의 Trip ID 구간만 검사한다.
   순회 시작 시 MAX(id)를 고정하고 끝까지 간 뒤 0부터 다시 시작한다.
2. 좌표가 있고 주소가 null인 side를 retry table에 넣는다. 기존 실패 횟수/EXHAUSTED는 보존한다.
3. 별도 짧은 transaction에서 due row를 `FOR UPDATE SKIP LOCKED`로 claim하고
   시도 횟수·UUID token·DB UTC lease를 기록한 뒤 commit한다.
4. transaction 밖에서 기존 enrichment를 호출한다. 새 transaction의 주소 update는
   ID/side뿐 아니라 조회 당시 좌표와 null 주소 조건을 검사한다.
5. 성공/불필요면 token이 같은 retry row를 제거한다. 실패는 backoff 뒤 PENDING 또는
   한도에 도달하면 EXHAUSTED로 남긴다. crash한 최종 claim도 lease 만료 후 EXHAUSTED가 된다.

주소/좌표/HTTP 오류 본문은 retry metadata나 로그에 복사하지 않는다. 임의 사용자 입력이나
tenant ID를 받는 관리 HTTP endpoint를 추가하지 않았다. 내부 worker는 저장된 Trip만 처리한다.

## 5. 검증 결과

실행 명령:

```bash
./gradlew test --tests 'org.thisway.vehicle.triplog.application.TripAddressWorkerIntegrationTest' --tests 'org.thisway.vehicle.triplog.application.TripAddressEnrichmentIntegrationTest' --console=plain
```

- Before: 누락/실패 주소는 수동 호출 외 자동 복구 경로 없음(CHANGE-026 문서·코드).
- After: 실제 MySQL 8.0.40/Flyway V10 + Hibernate validate에서 worker 11개, 기존 enrichment 14개로 총 25개 통과. 실패·오류·skipped 0, Gradle 19초.
- 증거: `build/test-results/test/TEST-org.thisway.vehicle.triplog.application.TripAddressWorkerIntegrationTest.xml` (11/0/0/0), `TEST-org.thisway.vehicle.triplog.application.TripAddressEnrichmentIntegrationTest.xml` (14/0/0/0).
- 별도 확인: `git diff --check` 통과. 전용 검증에서 실패 명령은 없었다. 기존 unchecked/unsafe compile note와 JVM CDS warning은 출력됐으나 검증 실패는 아니다.
- 전체 회귀는 병렬 작업을 통합한 root 실행 결과를 기준으로 따로 기록한다.

## 6. 실패 사례와 남은 위험

- 유효 lease 중 worker 간 중복 호출을 제한하지만 lease 만료보다 늦은 HTTP와 기존 AFTER_COMMIT
  경로의 경쟁은 중복 외부 호출이 가능하다. DB 조건부 쓰기와 token fencing만 보장한다.
- 최대 시도는 worker claim 기준이다. 즉시 AFTER_COMMIT 호출은 해당 횟수 밖이다.
- 좌표가 바뀐 exhausted row도 자동 reset하지 않는다. 운영자가 원인 확인 뒤 재개한다.
- scan/lookup 상한은 실행 단위이고 전역 provider quota가 아니다. 운영 규모의 scan 비용·복구 지연,
  다중 host/JVM kill, 실제 Kakao API 장애, 운영 배포는 미검증이다.
- DB 장애 중에는 실행이 실패하고 기존 claim은 만료 후 재개된다. DB 복원 자체를 수행하는 도구는 아니다.
- 기본 cron은 `-`다. 실제 활성화와 quota 설정은 운영 환경 설정으로 남는다.

## 7. 학습 기록

- `AFTER_COMMIT`은 durable event delivery가 아니다. 파생 주소는 null 상태를 재발견할 수 있다.
- keyset pagination의 고정 high watermark는 계속 생기는 신규 row에 순회 종료가 밀리지 않게 한다.
- lease는 row lock을 HTTP 동안 유지하지 않고 작업 소유권을 표현한다. lease 만료 뒤 stale worker는 존재할 수 있다.
- UUID claim token은 retry state의 stale completion을 막고, 좌표/null 조건은 주소 결과의 stale write를 막는다.
- 지수 backoff·최대 시도와 재개 runbook이 있어야 실패가 조용히 반복되거나 사라지지 않는다.

## 8. 예상 면접 질문

1. 왜 매분 주소가 null인 최신 20개만 처리하지 않나요?
   - 영구 실패한 최신 row가 매번 선택되면 다른 row가 굶는다. durable ID cursor로 전체를 순회하고
     due retry를 별도로 관리하며 시도 횟수를 제한한다.
2. transaction에서 주소 API를 호출하면 무엇이 문제인가요?
   - 느린 HTTP 동안 connection/row lock을 점유하고 핵심 쓰기까지 외부 장애에 결합한다.
     claim만 짧게 commit하고 HTTP 뒤 필요한 주소 write만 새 transaction으로 처리한다.
3. lease/token이면 Kakao API 호출이 정확히 한 번인가요?
   - 아니다. 첫 요청이 느리면 lease 만료 후 두 번째 worker가 호출할 수 있다.
     token은 retry state를, null/좌표 조건은 Trip 주소를 보호하며 외부 호출 자체의 중복은 남는다.
4. 프로세스가 마지막 시도에서 죽으면 실패 횟수는 어떻게 되나요?
   - claim 시 횟수를 먼저 기록한다. lease 만료 뒤 EXHAUSTED로 보존해 점검 후 명시적으로 재개한다.

## 9. AI 활용과 사람의 검증

- AI가 기존 실패 경로를 읽고 durable state·테스트·문서 초안과 실행을 담당했다.
- 사용자는 남은 로컬 구현을 모두 진행하도록 요청했다. 실제 세부 설계를 이해·채택했다는 주장은 하지 않는다.
- schema 없는 in-memory 대안은 restart 손실 때문에 거절하고 전용 V10 migration을 선택했다.
- 사람이 직접 확인할 흐름은 commit 후 누락→scan→claim→외부 I/O→조건부 저장→실패 재개다.
- 자동화는 합성 좌표·mock provider와 실제 MySQL을 사용한다. 실제 고객 데이터·Kakao 응답·운영 quota·사용자 이해는 검증하지 않았다.
