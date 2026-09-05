# Backend 학습 지도

## 사용법

각 주제는 `개념 설명 -> 현재 코드에서 문제 찾기 -> 작은 실패 test -> 개선 구현 -> 내 말로 설명` 순서로 학습한다. 단순 요약을 읽은 것으로 완료 처리하지 않는다.

| 순서 | 주제 | Thisway 실습 | 완료 증거 |
| ---: | --- | --- | --- |
| 1 | Test pyramid, test double, Testcontainers | Redis 외부 의존 실패를 unit/integration으로 분리하고 MySQL/Redis 실제 계약 검증 | fresh clone test green, 실패 원인 설명 |
| 2 | RBAC, resource ownership, IDOR, multitenancy | role은 맞지만 다른 company ID인 요청을 먼저 실패 test로 재현 | role × tenant matrix test |
| 3 | 민감정보 logging과 Actuator hardening | JWT/password query/body 노출 제거, management endpoint allowlist | log capture와 endpoint security test |
| 4 | Flyway와 schema migration | raw init SQL을 versioned migration으로 전환, `ddl-auto=validate` | 빈 MySQL migration·context test |
| 5 | RabbitMQ ack, retry, DLQ, publisher confirm | DB 장애·poison message·consumer restart scenario | DLQ/replay integration test와 runbook |
| 6 | Idempotent consumer와 unique constraint | 동일 GPS packet 재전달 시 한 번만 반영 | duplicate/concurrency test |
| 7 | Event time, late/out-of-order event, state machine | power OFF가 ON보다 먼저 오는 경우의 Trip 정책 정의 | 상태 전이 표와 domain test |
| 8 | Spring Batch JobInstance/Execution/Parameter/restart | targetDate backfill, 부분 실패, 동시 실행 | restart·same-date·concurrency test |
| 9 | Index, window function, `EXPLAIN ANALYZE` | 최신 GPS와 기간 query의 composite index 비교 | 고정 dataset 전후 실행 계획·latency |
| 10 | SLI/SLO, metric, log, trace | ingest/DLQ/lag/SSE 업무 metric과 alert | dashboard JSON, alert, 장애 runbook |
| 11 | 부하 모델과 queueing | 고유 차량·시간 series로 direct/MQ 비교 | 재현 환경, p95/p99, throughput, loss/duplicate |
| 12 | AI baseline, evaluation, drift, fallback | rule anomaly를 model과 비교하고 false alert 측정 | 고정 eval set과 model card |

## 1단계 학습 과제: 테스트 재현성

설명할 수 있어야 하는 것:

- mock Redis test와 실제 Redis integration test가 각각 잡는 오류
- Testcontainers가 해결하는 문제와 Docker 의존이라는 비용
- CI service container와 Testcontainers의 차이
- 현재 mock 기반 repository test가 init DDL과 실제 JDBC SQL을 함께 실행하지 않아 column typo를 잡지 못한 이유
- H2만으로 MySQL dialect와 index 동작을 보증할 수 없는 이유

실습:

1. 현재 4개 실패를 그대로 재현하고 test report에서 원인을 찾는다.
2. `RedisComponent`의 pure unit boundary와 실제 serialization/TTL integration boundary를 나눈다.
3. 정상 저장·조회뿐 아니라 만료, malformed JSON, connection failure를 검증한다.
4. 한 명령으로 실행되는 상태와 실행 시간을 기록한다.

### TripLog 특성 테스트 복습

`TripLogServiceTest`는 현재 application service의 분기와 domain mutation을 빠르게 고정한다. 이 테스트가 통과해도 실제 JPA query, MySQL 제약, transaction rollback, 동시성은 검증되지 않는다. 테스트 종류별 책임을 구분해서 설명해야 한다.

직접 해볼 것:

1. ON 이벤트로 만들어진 `TripLog`의 각 필드와 `active=false`의 실제 의미를 말한다.
2. matching ON이 있는 OFF와 없는 OFF가 서로 다른 row 상태를 만드는 과정을 그린다.
3. duplicate ON, duplicate OFF, OFF-before-ON, late GPS에 원하는 결과를 먼저 적고 필요한 unique constraint와 event identity를 연결한다.
4. `@SpringBootTest`로 다시 작성했을 때 추가로 검증되는 것과 실행 비용을 비교한다.

## 2단계 학습 과제: tenant 경계

다음 차이를 코드로 설명한다.

- Authentication: 호출자가 누구인가
- Role authorization: 어떤 종류의 작업을 할 수 있는가
- Resource authorization: 이 특정 vehicle/trip/emulator가 호출자 company 소유인가

실습에서는 `company A` token으로 `company B`의 ID를 요청하는 negative test를 먼저 작성한다. Service에서 조회 후 비교하는 방법과 repository에서 `companyId + id`로 제한하는 방법의 정보 노출·query 비용·실수 가능성을 비교한다.

### Member tenant predicate 복습

`CHANGE-006`의 Member 첫 단위에서는 `findByIdAndActiveTrue(id)`로 전역 row를 가져온 뒤 company를 비교하던 흐름을 `findByIdAndCompanyIdAndActiveTrue(id, companyId)`로 바꿨다.

직접 설명하고 실행할 것:

1. JWT의 `companyId`가 `MemberDetails`를 거쳐 repository predicate가 되는 호출 순서를 그린다.
2. 다른 회사 ID와 실제로 없는 ID가 모두 같은 `404 MEMBER_NOT_FOUND`가 되는 이유를 설명한다.
3. 같은 회사의 `ADMIN`은 row가 tenant query를 통과한 뒤 role 검사에서 `403 MEMBER_ACCESS_DENIED`가 되는 차이를 설명한다.
4. company A token으로 company B member의 GET·PUT·DELETE를 실행하고, status뿐 아니라 name/email/active가 불변인지 확인한다.
5. H2 통합 테스트가 query derivation과 application 흐름은 확인하지만 MySQL 실행 계획·index 효율은 보증하지 못하는 이유를 정리한다.

### Vehicle 사용자 경로와 내부 경로 구분

`CHANGE-007`에서는 사용자 HTTP CRUD가 공유하는 `getAuthorizedVehicle`만 tenant scoped query로 바꿨다. telemetry consumer가 사용하는 `getVehicleById`, SSE 내부 상태 확인이 사용하는 `getVehiclePowerState`까지 무조건 principal 기반으로 바꾸면 인증 주체가 없는 background 처리 흐름을 깨뜨린다.

직접 해볼 것:

1. HTTP 사용자 use case와 device/background use case의 인증 주체 차이를 표로 만든다.
2. 두 use case가 같은 `VehicleService`에 섞인 구조의 장단점을 설명한다.
3. 향후 `AuthorizedVehicleReader`와 internal `VehicleReader` port로 나눌 때 호출자를 분류한다.

### TripLog와 Emulator의 연쇄 ownership

`CHANGE-008`, `CHANGE-009`에서는 직접 company column이 없는 자원의 tenant를 연관 entity로 판정한다.

- TripLog tenant: `trip_log -> vehicle -> company`
- Emulator tenant: `emulator -> vehicle -> company`

직접 해볼 것:

1. Spring Data derived query의 `findByIdAndVehicleCompanyId...`를 실제 join 조건으로 풀어 쓴다.
2. Emulator 수정에서 Emulator 자체가 company A여도 새 `vehicleId`가 company B라면 왜 다시 ownership을 검사해야 하는지 설명한다.
3. TripLog 일반 HTTP와 query-token SSE가 같은 service를 공유하지만 이번 완료 범위가 다른 이유를 설명한다.
4. `404 status`뿐 아니라 list 결과, 수정 필드, soft/hard delete 상태를 각각 확인해야 하는 이유를 말한다.

### 실제 경로 기반 role 인가 복습

`CHANGE-004`에서는 없는 `/test` URL의 404 대신 실제 DELETE/POST 경로와 mocked service 호출을 사용했다. 다음 세 층을 구분해 설명한다.

1. Authentication: JWT가 유효하고 호출자 정보가 만들어졌는가?
2. Role authorization: ADMIN 또는 COMPANY_CHEF가 해당 종류의 작업을 할 수 있는가?
3. Resource authorization: 요청한 member/vehicle/trip이 호출자의 company 소유인가?

이번 변경은 1번과 2번의 일부만 검증했다. 3번은 다음 cross-tenant negative test의 대상이다. `anyRequest().authenticated()`가 구체 matcher 누락을 어떻게 허용하는지 실제 path로 추적해 본다.

## 3단계 학습 과제: 메시지 신뢰성

종이에 다음 scenario의 기대 상태를 먼저 적는다.

- 같은 GPS event 두 번 전달
- DB 저장 직후 ack 전에 consumer 종료
- poison payload 세 번 실패
- DLQ replay를 두 번 실행
- power OFF가 ON보다 먼저 도착
- 10분 늦은 GPS가 일일 Batch 종료 후 도착

각 scenario에서 DB row, message 상태, metric, alert, 운영자 action을 설명할 수 있어야 한다.

## 보안 로깅과 관리 endpoint 복습

`CHANGE-005`에서는 마스킹 annotation을 확대하지 않고 HTTP body/query 자체를 기본 미수집으로 바꿨다. 다음 차이를 설명한다.

- masking: 수집한 원본의 일부를 변환하므로 누락·nested 구조·새 필드에 취약
- data minimization: 필요 없는 원본을 처음부터 수집하지 않아 기본 실패 방향이 안전
- application log level: 자체 logger뿐 아니라 Spring MVC/Security logger가 DTO를 출력할 수 있음
- Actuator exposure: endpoint가 web에 생성되는 범위
- Actuator authorization: 생성된 endpoint에 누가 접근할 수 있는지

실습에서는 로그인 DTO에 새 secret 필드를 하나 추가하고, root logger negative test가 이를 잡는지 확인한다. `/actuator/env`, `/actuator/health/db`, `POST /actuator/health`가 각각 왜 404 또는 401인지 filter와 endpoint 생성 순서로 설명한다.

## 자기 점검 기준

한 주제는 다음을 보지 않고 말할 수 있을 때 완료다.

- 현재 코드가 어떤 실패를 만드는지
- 선택한 설계가 지키는 불변식
- 대안 하나와 선택하지 않은 이유
- 실패를 재현하는 test
- 운영에서 발견하고 복구하는 방법
- 포트폴리오에 쓸 수 있는 측정된 결과와 아직 말하면 안 되는 주장
