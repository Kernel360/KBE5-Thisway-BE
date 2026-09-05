# Thisway 예상 면접 질문

## 답변 원칙

기술 이름부터 말하지 않는다. `문제 -> 제약과 실패 조건 -> 선택지 -> 결정 -> 실행 흐름 -> 검증 수치 -> 한계` 순서로 답한다. 아직 개선하지 않은 내용은 현재 문제와 계획으로만 말하고 완료했다고 표현하지 않는다.

## 개인 기여와 프로젝트

### 1. 본인이 직접 담당한 범위는 어디까지인가?

답변 핵심:

- 5인 팀에서 Vehicle/VehicleModel과 회사별 Statistics/Spring Batch 담당
- 17 merged PR 근거
- RabbitMQ/SSE/Security/CI 전반은 팀 시스템이며 원구현으로 주장하지 않음
- 이후 개인 현대화 diff와 검증 결과를 별도 설명

### 2. 기존 팀 프로젝트를 다시 개선한 이유는?

답변 핵심:

- 기능 완성 중심이었던 v1에서 재현성·실패 처리·운영 증거가 부족했음
- AI-assisted 기준선 감사에서 기존 Batch의 restart 문제를 식별했고, 코드·재현 테스트로 직접 검증할 계획을 세움
- 단순 리팩터링이 아니라 실패 시나리오와 수치로 성장 증명

### 3. AI를 사용했다면 본인이 한 일은 무엇인가?

답변 핵심:

- AI는 대안·실패 scenario·test 후보를 넓히는 reviewer/assistant
- acceptance criteria와 domain invariant는 본인이 결정
- AI 제안을 채택·거절한 이유와 실행 test를 work log에 기록
- 이해하지 못한 코드는 병합하지 않음

## Security와 multitenancy

### 4. role 검사가 있는데 왜 IDOR가 생겼는가?

답변 핵심:

- role은 작업 종류만 허용하고 특정 resource 소유권은 보장하지 않음
- JWT companyId와 DB resource companyId를 repository predicate로 연결
- 다른 tenant ID negative test 필요

### 5. tenant 조건을 service 비교보다 repository query에 넣는 이유는?

답변 핵심:

- 잘못된 row를 애플리케이션으로 가져오지 않음
- 존재 여부 정보 노출과 누락된 검사 위험 축소
- 모든 query path에 일관된 predicate와 test 필요

### 5-1. 존재하지 않는 URL에서 404가 나온 것을 왜 인가 성공 증거로 쓰면 안 되는가?

답변 핵심:

- 요청이 실제 Controller와 해당 endpoint matcher에 도달했는지 알 수 없음
- pattern이 틀리면 구체 role rule 대신 `anyRequest().authenticated()`가 적용될 수 있음
- 실제 method/path에서 허용 role은 service 호출, 비허용 role은 403과 service 미호출을 함께 검증
- Thisway에서는 회사 회원 DELETE pattern 불일치를 이 방식으로 발견하고 회귀 테스트로 고정

### 5-2. 다른 tenant 자원을 403이 아니라 404로 응답한 이유는?

답변 핵심:

- `memberId + companyId` query 결과만 현재 tenant의 자원으로 취급
- 실제 존재 여부와 소유 회사를 응답 차이로 노출하지 않음
- 없는 ID와 다른 tenant ID에 동일한 `MEMBER_NOT_FOUND` 계약 적용
- 같은 tenant지만 허용되지 않은 `ADMIN` role은 별도 403으로 ownership과 role 실패를 구분

### 5-3. cross-tenant 테스트에서 status만 확인하면 부족한 이유는?

답변 핵심:

- filter나 validation에서 우연히 4xx가 나도 실제 ownership 검사가 동작했다는 증거가 아님
- 유효한 COMPANY_CHEF JWT와 실제 두 회사 row를 사용해 전체 호출 흐름을 통과
- GET 응답 차단뿐 아니라 PUT 이후 name/email, DELETE 이후 active가 그대로인지 확인
- repository 직접 test로 동일 ID라도 companyId가 다르면 empty인지 고정

### 5-4. 왜 Vehicle의 모든 ID 조회를 principal 기반 tenant query로 바꾸지 않았는가?

답변 핵심:

- HTTP 사용자 CRUD와 RabbitMQ/device/background 처리는 인증 주체가 다름
- internal telemetry 조회에 웹 principal을 강제하면 정상 consumer가 동작하지 않음
- 이번 단위는 `getAuthorizedVehicle`을 공유하는 사용자 GET/PATCH/DELETE만 제한
- 장기적으로 authorized reader와 internal device-authenticated reader를 분리하고 각각의 신뢰 경계를 테스트

### 5-5. TripLog나 Emulator에 companyId column이 없으면 tenant를 어떻게 검증하는가?

답변 핵심:

- 소유 관계인 `resource.vehicle.company.id`를 query predicate에 포함
- TripLog 상세은 `tripId + vehicle.companyId + active`, Emulator는 `emulatorId + vehicle.companyId`
- Emulator 생성·재연결은 대상 Vehicle도 동일 company인지 재검증
- 장기적으로 FK/index와 실제 MySQL 실행 계획을 함께 확인

### 5-6. P0-02C가 끝났는데 SSE와 telemetry까지 tenant-safe하다고 말할 수 있는가?

답변 핵심:

- 아니다. P0-02C는 JWT principal을 사용하는 일반 HTTP 자원 접근 범위
- query token SSE는 token 전달·resource binding·subscription key를 P0-02D에서 함께 변경
- MDN device 경로는 사용자 principal이 아니라 device credential/replay 방지가 필요한 P0-04 범위
- 완료 주장을 신뢰 경계별 테스트 증거로 제한

### 6. SSE에서 query token을 쓴 이유와 개선 방법은?

답변 핵심:

- browser EventSource의 header 제약이라는 배경
- URL, access log, browser history에 token이 남는 비용
- 짧은 수명의 one-time stream ticket 또는 cookie/BFF 등 대안
- stream ticket도 tenant/resource binding과 재사용 방지 필요
- CHANGE-011에서 FE fetch stream으로 전환해 Bearer header를 기존 JWT filter로 검증했다. URL token만으로 접근하면 401이고 cross-tenant SSE는 연결 생성 전 404다. 자동 reconnect/replay는 아직 없으며, token 만료 후 기존 연결 종료와 다중 instance 검증도 남아 있다.

### 7. Actuator는 어떻게 안전하게 노출하는가?

답변 핵심:

- public health와 내부 Prometheus 목적 분리
- 최소 endpoint, management port/network, authentication
- health detail 축소, heapdump/configprops 외부 차단
- Thisway P0-02B에서는 exposure를 health/prometheus로 제한하고 exact GET matcher, health detail 비공개, unlisted endpoint 404를 test로 고정

### 7-1. 민감정보 마스킹 기능이 있는데 왜 request/response body logging을 제거했는가?

답변 핵심:

- 실제 DTO에 annotation 사용처가 없어 현재 보호 효과가 없었음
- 새 필드마다 annotation을 기억해야 하는 방식은 누락 시 fail-open
- password/JWT/code/raw location은 정상 운영에 필요하지 않아 미수집이 더 안전
- method/path/status, traceId, metric과 allowlist된 business event로 관측 가능성 보완
- root logger test에서 자체 advice 제거 후에도 Spring MVC DEBUG가 secret을 출력하는 것을 발견해 framework log level도 제한

## RabbitMQ와 telemetry

### 8. RabbitMQ를 Kafka 대신 선택한 이유는?

답변 핵심:

- v1의 command/work-queue와 fanout SSE 요구에는 RabbitMQ routing과 낮은 운영 복잡도가 적합
- 장기 replay·대규모 event history가 핵심이면 Kafka 장점
- 선택은 traffic, 보존, ordering, replay, 운영 역량에 따라 달라짐

### 9. 같은 GPS message가 두 번 오면 현재와 개선 후 어떻게 다른가?

답변 핵심:

- 현재는 event ID/unique가 없어 중복 row 가능
- at-least-once에서는 중복 전달이 정상 scenario
- stable event identity + unique constraint + idempotent result
- ack 전 consumer crash test로 검증

### 10. retry 3회면 메시지 유실을 막을 수 있는가?

답변 핵심:

- 아니다. retry 종료 후 현재 recoverer는 drop할 수 있음
- retryable/non-retryable 구분, DLQ, alert, replay, idempotency가 한 세트
- 무한 retry는 poison message로 queue를 막음

### 11. publisher가 두 exchange 중 하나에만 전송하고 실패하면?

답변 핵심:

- DB 저장 경로와 SSE broadcast의 일관성 경계 정의 필요
- confirm/return으로 개별 publish 결과 관측
- core 저장을 우선하고 live delivery는 replay/fallback 가능한지 결정
- outbox 또는 단일 event에서 downstream 분기하는 대안 비교

### 12. bulk insert가 `saveAll`보다 빠르다는 근거는?

답변 핵심:

- 현재는 multi-row SQL 구현만 있고 비교 수치가 없어 빠르다고 단정 불가
- 같은 dataset·batch size·connection 조건에서 throughput/p95/DB CPU 비교
- SQL packet size와 transaction/rollback 비용도 측정

## Trip과 Batch

### 13. power OFF가 ON보다 먼저 도착하면 어떻게 할 것인가?

답변 핵심:

- event time 기반 상태 전이와 허용 지연 window 정의
- pending/orphan event 저장 후 matching 또는 보정
- 무조건 새로운 완료 Trip 생성은 데이터 의미를 왜곡할 수 있음
- duplicate·late·missing scenario table과 test
- 현재 P0-01B 특성 테스트는 거리 0의 완료 Trip이 생성되는 기존 동작을 증거로 남겼으며, 올바른 정책으로 승인한 것은 아님

### 14. Kakao API가 느릴 때 왜 DB transaction 문제가 되는가?

답변 핵심:

- network wait 동안 connection/lock과 transaction이 유지됨
- core Trip 저장과 address enrichment 분리
- timeout/circuit breaker/fallback, 비동기 보정과 상태 표시

### 14-1. 왜 잘못되어 보이는 현재 Trip 동작을 먼저 특성 테스트로 남겼는가?

답변 핵심:

- P0-01의 목적은 현재 기준선을 재현 가능하게 만드는 것
- 원하는 정책으로 테스트만 바꾸면 실제 production 동작과 불일치하고 변경 범위가 숨겨짐
- 현재 결과와 위험을 기록한 뒤 P1-02에서 상태표·불변식·DB 제약을 먼저 결정
- unit test 통과는 JPA/MySQL·transaction·동시성 보장이 아니므로 integration test를 추가해야 함

### 15. Batch가 회사 100개 중 37번째에서 실패하면?

답변 핵심:

- 현재 exception swallow/큰 tasklet은 실패 상태와 transaction 경계가 불명확
- company별 처리 경계, 실패 대상 기록, Job status 반영
- 원인 해결 후 restart 시 성공 항목 재처리 정책과 idempotency

### 16. 두 instance가 같은 날짜 Batch를 동시에 실행하면?

답변 핵심:

- identifying `targetDate`, DB `(company,date)` unique가 최후 방어선
- scheduler 단일화나 distributed lock은 실행 조정 수단
- race 상황 integration test와 upsert/conflict 정책

### 17. 가동률 공식을 어떻게 검증할 것인가?

답변 핵심:

- GPS 수와 3600초만 연결하면 `gpsCycle`, 누락, 정차 영향을 왜곡할 수 있음
- business definition부터 확정
- 고정 원천 event와 손계산 expected value
- late event 재집계·보정 정책

## 성능·운영·AI

### 18. “15,000대 처리”에서 반드시 함께 말할 수치는?

답변 핵심:

- generator·server·DB 사양, 요청당 event 수와 data size
- accepted/committed throughput, p50/p95/p99, error rate
- queue lag, DB connection/CPU/lock, duplicate/loss
- 동일 조건 before/after와 raw result

### 19. 어떤 SLI/SLO를 둘 것인가?

답변 핵심:

- ingestion acceptance와 durable commit latency
- invalid/duplicate/DLQ rate, queue lag
- Batch completion/freshness, SSE connected delivery latency
- alert threshold와 사용자가 느끼는 영향 연결

### 20. 왜 AI anomaly를 바로 고장 예측이라고 부르지 않는가?

답변 핵심:

- 현재 정비 이력·고장 label이 없음
- 먼저 운영자 검토용 anomaly candidate ranking
- rule baseline과 고정 eval set
- Precision@K, false alerts/100 vehicles, latency, drift, fallback
- model은 자동 차량 차단 같은 결정을 하지 않음
