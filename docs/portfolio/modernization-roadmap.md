# Thisway Backend 현대화 로드맵

## North Star

> 차량 텔레메트리의 중복·지연·실패를 견디는 수집 파이프라인과 재처리 가능한 통계 배치를 구축하고, 이를 멀티테넌트 보안 테스트·부하 측정·운영 지표로 검증한다.

AI는 이 목표의 필수 조건이 아니다. 기반 데이터와 evaluation set이 만들어진 뒤 운영자의 anomaly triage를 돕는 제한된 기능으로 추가한다.

## 우선순위 원칙

1. 재현할 수 없는 기능보다 green baseline을 먼저 만든다.
2. 노출·tenant 침범·데이터 오염 가능성을 기능 추가보다 먼저 차단한다.
3. “기술을 사용했다”보다 실패 시 동작과 측정 결과를 남긴다.
4. 사용자의 원 기여인 Vehicle·Statistics 현대화를 대표 성장 서사로 만든다.
5. 각 단계는 작은 PR/commit과 하나의 work log로 끝낸다.

세 저장소의 현재 연결 계약과 변경 영향은 [`cross-repository-audit.md`](cross-repository-audit.md)를 기준으로 한다. FE와 Emulator를 실제 수정하기 전까지는 정적 분석 결과이며 통합 실행 완료로 표현하지 않는다.

## Phase 0. 포트폴리오 기반

### P0-01 재현 가능한 테스트

목표:

- fresh clone에서 테스트 사전조건을 명령 하나 또는 명시적 task로 재현한다.
- Redis unit test와 실제 Redis integration test의 목적을 분리한다.
- 현재 skipped 핵심 테스트를 단순 활성화하지 않고 의미 있는 시나리오로 다시 작성한다.

Acceptance criteria:

- `./gradlew test --console=plain`이 외부 수동 상태에 의존하지 않고 green이다.
- Redis integration은 Testcontainers 또는 동등한 격리 환경에서 실제 직렬화·TTL·원자 동작을 검증한다.
- CI와 로컬 명령이 같고 README에 JDK/Docker 사전조건이 적힌다.
- 기준선 178/161/4/13과 변경 후 결과가 work log에 남는다.

진행 상태:

- [x] P0-01A: Redis service/unit test를 mock으로 격리하고 실제 adapter 계약은 Testcontainers로 검증했다. 전체 결과는 180/167/0/13이다.
- [x] P0-01B: 본문이 비어 있던 TripLog test 10건을 조회·상태 전이 특성 테스트로 교체했다. 전체 결과는 180/177/0/3이며, 중복·역순·지연 이벤트 정책 구현은 P1-02 범위다.
- [x] P0-01C: disabled Security test 3건을 실제 API 허용·거부/JWT 시나리오로 교체했다. 통계 보안 테스트 3건을 추가해 전체 결과는 183/183/0/0이다.

P0-01은 외부 수동 Redis 없이 전체 test green, 핵심 빈 test와 disabled test 0건이라는 기준으로 완료했다. 실제 MySQL·RabbitMQ·tenant·동시성 계약은 이후 단계의 별도 품질 gate다.

### P0-02 보안 containment

범위:

- query/body의 password·JWT 로깅 제거
- Actuator 최소 공개
- 통계 저장, member delete, TripLog, Emulator, SSE의 role·tenant 경계 수정
- SSE key의 정확 매칭과 token 전달 방식 재설계
- cross-tenant negative integration test

Acceptance criteria:

- 다른 회사의 vehicle/trip/emulator ID로 접근하는 모든 테스트가 403 또는 정책상 404를 반환한다.
- URL과 application log에 JWT/password가 나타나지 않는다.
- 공개 관리 endpoint 목록을 test로 고정한다.

진행 상태:

- [x] P0-02A: 실제 route 기반 role test를 만들고 회사 회원 DELETE pattern과 통계 저장 role 누락을 교정했다.
- [x] P0-02B: HTTP body/query와 telemetry 원본 logging을 최소화하고 Actuator를 health/prometheus exact allowlist로 제한했다. 전체 결과는 194/194/0/0이다.
- [x] P0-02C: Member·Vehicle·TripLog·Emulator의 repository tenant predicate와 cross-tenant negative test를 구현했다. 관련 76/76, 전체 218/218/0/0.
  - [x] Member: 상세 조회·수정·삭제를 `memberId + companyId + active` query로 제한하고 repository/service/API negative test를 추가했다. 전체 201/201/0/0.
  - [x] Vehicle: 사용자 CRUD 상세 경로를 `vehicleId + companyId + active` query로 제한하고 실제 JWT GET/PATCH/DELETE negative test를 추가했다.
  - [x] TripLog: 일반 HTTP의 차량 운행 요약·현재 GPS·운행 상세를 tenant-scoped Vehicle/TripLog 조회로 제한했다. query-token SSE는 P0-02D 범위다.
  - [x] Emulator: 목록·CRUD와 등록/재연결 Vehicle을 principal 회사로 제한했다. device MDN 조회는 P0-04 범위다.
- [ ] P0-02D: SSE token 전달, 정확한 subscription key, tenant ownership을 재설계한다.
  - [x] P0-02D-1: resource ID 구분자 일치와 이전 연결 callback의 새 연결 삭제 방지. CHANGE-010.
  - [x] P0-02D-2: FE fetch SSE 헤더 인증, 세 SSE 경로 role 검사와 resource ownership. CHANGE-011.
  - [x] P0-02D-3A: 초기/live 경계의 256건 bounded buffering, event name/FIFO 보존, flush 경쟁 조건 제거와 RabbitMQ fan-out topology 특성 테스트. CHANGE-012.
  - [ ] P0-02D-3B: 실제 RabbitMQ에서 2개 application instance 전달, 브라우저·proxy disconnect/reconnect, 여러 탭 subscription identity를 검증한다.
    - CHANGE-013: 서버 UUID로 탭별 구독 분리, 실제 broker의 독립 connection 두 개로 fan-out·종료·재구독 검증 완료. 두 JVM 및 브라우저·proxy 검증은 남아 있다.
    - CHANGE-014: 별도 JVM worker 두 개의 실제 broker 수신 및 Chromium 차량 화면 단절/수동 재연결 검증 완료. 전체 Boot 서버와 proxy를 연결한 E2E는 남아 있다.

### P0-03 versioned database

범위:

- Flyway baseline migration
- `geofence_log.occurred_time`, `trip_log` index, `statistics`, Batch metadata 정합화
- dev/prod `ddl-auto=validate`
- 기존 데이터가 있다면 baseline/migration 전략 별도 ADR

Acceptance criteria:

- 빈 MySQL container에 migration을 적용해 application context가 기동한다.
- geofence insert, 핵심 QueryDSL/JDBC query, Batch metadata 접근을 실제 MySQL에서 검증한다.
- schema drift가 있으면 CI가 실패한다.

## Phase 1. 대표 백엔드 문제 해결

### P1-01 신뢰 가능한 telemetry ingestion

설계 질문:

- event identity는 device가 제공하는가, `(mdn, occurredAt, sequence)`로 합성하는가?
- duplicate와 late/out-of-order packet을 저장·무시·보정하는 정책은 무엇인가?
- retryable 오류와 poison message를 어떻게 구분하는가?

구현 후보:

- device authentication과 request validation
- idempotency key와 DB unique constraint
- publisher confirm/return
- bounded retry, DLQ, replay command/API와 runbook
- ingest accepted/rejected/duplicate, consumer lag, DLQ metric

Acceptance criteria:

- 같은 event를 여러 번 보내도 business row와 통계가 한 번만 반영된다.
- consumer 중단·재시작과 DB 일시 장애 뒤 처리 결과가 유실되지 않는다.
- poison message는 DLQ에서 원인과 trace를 확인하고 안전하게 replay할 수 있다.

### P1-02 Trip state machine과 enrichment 분리

범위:

- `sum` protocol 의미를 Emulator와 계약 test로 확정
- `startOdometer`, `endOdometer`, `distance` 분리
- ON/OFF/duplicate/out-of-order/missing event 상태표
- Kakao reverse geocoding을 core transaction 밖의 enrichment로 분리
- timeout, retry, fallback과 보정 job

Acceptance criteria:

- 정의된 모든 이벤트 순서가 deterministic한 Trip 결과를 만든다.
- Kakao 장애 중에도 핵심 운행 원본은 보존되고 나중에 보정된다.
- mileage와 trip distance 불변식이 DB 제약·domain test로 보호된다.

### P1-03 재시작 가능한 Statistics Batch

이 단계는 기존 개인 기여 [#172](https://github.com/Kernel360/KBE5-Thisway-BE/pull/172), [#213](https://github.com/Kernel360/KBE5-Thisway-BE/pull/213)의 현대화다.

범위:

- `targetDate`를 identifying JobParameter로 사용
- 날짜 backfill
- `(company_id, statistic_date)` unique/upsert
- 회사별 독립 transaction과 실패 상태
- restart와 다중 instance 중복 실행 제어
- `gpsCycle`, 누락/late event를 반영한 통계 정의

Acceptance criteria:

- 회사 100개 중 임의 회사가 실패하면 Job 결과와 실패 대상을 확인할 수 있다.
- 원인 해결 뒤 전체 성공 회사를 다시 처리하지 않고 안전하게 재시작한다.
- 같은 날짜 동시 실행에도 한 결과만 남는다.
- 원천 fixture로 계산한 기대값과 Batch 결과가 일치한다.

## Phase 2. 성능과 운영 증거

### P2-01 현실적인 부하 실험

- 고유 vehicle/MDN과 seed 고정 시간 series를 생성한다.
- direct와 RabbitMQ mode의 목적을 분리해 비교한다.
- 요청 응답만이 아니라 DB commit rate, queue lag, duplicate/loss, CPU, connection pool을 함께 측정한다.
- p50/p95/p99, throughput, error rate와 병목을 남긴다.
- composite index 전후는 같은 dataset에서 `EXPLAIN ANALYZE`와 query latency로 비교한다.

성과 문구는 raw result와 재현 환경이 있을 때만 작성한다. 기존 k6의 `vus`나 주석은 성과 수치가 아니다.

### P2-02 관측성과 배포 안전성

- 업무 metric과 Grafana dashboard JSON
- SLI/SLO, alert rule, runbook
- trace propagation과 MDC cleanup
- test를 포함한 build gate
- commit SHA image tag, GitHub OIDC, ECS 안정화 wait, health check, rollback

## Phase 3. 선택적 AI 기능

### 추천: 이상 운행 후보 랭킹 + 근거 설명

현재 데이터로 “예지 정비”나 “최적 배차”를 주장할 수 없다. 정비 고장 label과 예약·수요 데이터가 없기 때문이다. 가능한 문제는 다음처럼 제한한다.

> GPS 상태, 속도, 배터리 전압, 위치 점프, 운행 거리와 시동 전환을 이용해 평소와 다른 차량/운행을 운영자 검토 대상으로 우선순위화한다.

진행 gate:

1. Emulator에 seed 고정 정상·anomaly scenario와 기대 label을 만든다.
2. 단순 rule-based baseline을 먼저 구현한다.
3. 시간/차량 단위 split으로 leakage를 막는다.
4. 필요할 때만 통계/ML model을 baseline과 비교한다.
5. `Precision@K`, PR-AUC, 차량 100대당 false alert, detection latency를 측정한다.
6. 결과에 `modelVersion`, `featureVersion`, score, reason code를 저장한다.
7. shadow mode와 사용자 feedback을 거친다.

LLM을 추가한다면 model이 raw SQL이나 tenant ID를 선택하게 하지 않는다. 인증된 company 범위의 read-only tool만 제공하고, 이미 계산된 anomaly와 통계의 근거 있는 Fleet Brief를 생성하게 한다. evaluation에는 tool 선택, 숫자 근거성, abstention, prompt injection, tenant 격리, p95 latency, token cost를 포함한다.

피할 기능:

- label 없이 “고장 예측 AI”라고 부르기
- 데이터 없이 수요 예측·경로 최적화 주장
- 일반 챗봇만 추가하기
- LLM 판단으로 차량 차단 같은 irreversible action 자동 수행

## 추천 실행 순서

1. P0-01 재현 가능한 테스트
2. P0-02 보안 containment
3. P0-03 Flyway와 fresh MySQL
4. P1-01 telemetry idempotency/DLQ
5. P1-03 Statistics Batch restart
6. P1-02 Trip state/enrichment
7. P2 성능·운영 증거
8. Phase 3 AI go/no-go 평가

첫 구현 단위는 P0-01이다. 회귀 안전망이 green이 된 뒤 보안과 migration을 작은 변경으로 분리한다.
