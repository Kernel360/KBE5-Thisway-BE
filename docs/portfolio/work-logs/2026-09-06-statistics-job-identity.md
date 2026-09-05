# CHANGE-024: Statistics 날짜 식별·실패 상태·전체 재시작

## 메타데이터

- 날짜: 2026-09-06
- 기준: `develop@0445c2b`, 브랜치 `codex/statistics-batch-restart`
- 관련: P1-03A, 기존 개인 Statistics/Batch 기여
- 상태: Verified (로컬 MySQL 통합 검증, 운영 실행 아님)

## 1. 문제와 근거

`StatisticBatchConfig`는 timestamp를 JobParameter로 사용하고 tasklet은 `LocalDate.now().minusDays(1)`을 다시 계산했다. 따라서 과거 날짜 재처리와 실패 JobInstance 재시작의 의미가 약했다. 서비스와 tasklet이 REQUIRED transaction을 공유하는데 예외를 잡고 순회하므로, 성공 로그가 실제 commit을 의미하지 않았다.

모든 실패가 기존에 반드시 COMPLETED였다고 주장하지 않는다. 내부 transactional proxy에서 예외가 발생하면 rollback-only가 설정되어 마지막에 실패할 수도 있다. 이번 목적은 날짜와 실패 경계를 명시적으로 만드는 것이다.

원래 팀 프로젝트 및 사용자의 기존 Vehicle/Statistics 기여는 `../original-contributions.md`에 따르며 새 변경은 개인 현대화다.

## 2. Acceptance criteria

- [x] 유효한 식별 targetDate 한 개만 허용하고 실행 중 현재 날짜로 덮지 않는다.
- [x] 두 번째 회사 실패 시 Job/Step FAILED, 실패 회사·날짜 확인, 첫 회사 통계도 rollback된다.
- [x] 동일 날짜 재시작은 같은 JobInstance/다른 JobExecution이며 2개 회사 결과가 저장된다.
- [x] 완료 날짜와 진행 중인 동일 날짜의 중복 실행을 거부한다.
- [x] 전체 회귀 결과와 한계를 문서화한다.

## 3. 선택과 결정

[ADR-004](../../adr/004-statistics-job-date-and-failure-boundary.md)에 기록했다. 이번에는 전체 tasklet transaction을 유지한 fail-fast를 선택했다. 회사별 독립 commit/checkpoint는 unique 및 재시작 정책과 함께 후속 구현한다. 단순 REQUIRES_NEW 추가나 예외를 무시하는 방식은 선택하지 않았다.

## 4. 실행 흐름

Asia/Seoul 매일 02:00 → 한국 시간 전일을 identifying `targetDate`로 전달 → validator → JobRepository → tasklet이 parameter 날짜로 active 회사 순회 → 실제 통계 service/repository → 전체 성공 시 commit, 실패 시 예외를 던져 전체 rollback/FAILED.

`runForDate(LocalDate)`는 내부 Java 진입점이다. 과거 날짜로 테스트했지만 신규 운영 CLI/HTTP API를 제공한 것은 아니다. 기존 `/api/statistics/save`는 직접 저장 경로이며 Job launch 경로로 변경하지 않았다.

RunIdIncrementer와 timestamp를 제거했다. `thisway.statistics.cron=-`로 테스트 중 스케줄 자동 실행을 막는다. 통계 계산식과 DB schema는 바꾸지 않았다.

## 5. 검증 결과

- `./gradlew test --tests '*StatisticsBatchIntegrationTest' --console=plain`: 3/3 성공, 42초.
- 실제 MySQL 8.0.40 + Flyway V1~V3 + Spring JobLauncher/JobRepository + 실제 통계 service/JPA 저장 사용.
- 회사 목록과 두 번째 회사의 예외·동시 launch 대기 시점만 spy로 제어한다. 계산/저장은 실제 코드다. 차량·운행 없는 fixture에서 기대 power_on_count=0을 검증하며 비영 통계 공식 정확성을 증명하지 않는다.
- 실패 후 DB 통계 0행, metadata FAILED → 같은 날짜 restart 후 2행/COMPLETED. 처음 회사가 다시 호출됨도 확인해 전체 재시작임을 명시한다.
- 동시 실행: 한 thread를 서비스 경계에서 대기시킨 뒤 같은 MySQL repository에 두 번째 launch가 AlreadyRunning으로 거부되는지 확인한다.
- 날짜 누락/불가능한 날짜/비식별 targetDate/추가 timestamp는 회사 조회 전에 거부한다.
- 전체 회귀: `./gradlew test --console=plain` — 297/297 성공, failure/error/skipped 모두 0, 1분 8초. `build/test-results/test/TEST-*.xml` 집계로 확인했다. `git diff --check` 통과. 이번 테스트 실행 실패는 없었다.

## 6. 남은 위험과 다음 작업

- 실패 회사만 재개하지 않는다. 회사별 chunk/checkpoint와 active company snapshot 정책은 P1-03B다.
- DB `(company_id,date)` unique가 없어 HTTP 직접 저장 등 다른 경로의 동시성은 보호하지 못한다.
- JobInstance 완료 후 late event 반영은 correction/backfill 운영 정책이 필요하다. timestamp를 억지로 추가해 우회하지 않는다.
- 현재 valid calendar date만 검사한다. 미래 날짜 실행 방지·운영자 권한·감사와 별도 backfill CLI는 후속이다.
- 운영 DB·Batch metadata를 변경하지 않았다. 기존 timestamp 기반 실패 이력은 새 validator로 재시작할 수 없으므로 이력/날짜 데이터 확인 후 전환해야 한다.
- 서로 다른 JVM·동시 최초 생성 race·프로세스 kill·재시작 시 회사 목록 변화까지 검증하지 않았다.
- GPS 수집주기/late event/운행 경계에 따른 통계 공식 정확성은 별도이며 이번 결과로 완료 표시하지 않는다.

## 7. 학습 포인트

- JobInstance는 업무 단위, JobExecution은 시도 이력이다. 대상 날짜는 식별 parameter이며 재시작 시 보존되어야 한다.
- REQUIRED service는 tasklet의 transaction에 참여한다. 메서드 반환 또는 성공 로그와 DB commit은 다르다.
- 예외를 catch해도 이미 rollback-only이면 transaction을 되살릴 수 없다.
- 실습: 두 번째 회사 실패 때 첫 회사가 남지 않는 이유, 회사별 독립 commit으로 바꾸려면 checkpoint와 unique가 왜 필요한지 설명한다.

## 8. 예상 면접 질문

1. 왜 timestamp를 제거했나요?
   - 날짜별 업무 단위가 매 실행마다 달라져 실패한 같은 날짜를 같은 instance로 재시작하기 어렵기 때문이다.
2. 이번 변경은 실패 회사부터 이어서 실행하나요?
   - 아니다. 전체 rollback 후 전체 재실행이다. 성공 회사 생략은 다음 checkpoint 설계가 필요하다.
3. JobRepository가 있으면 통계 중복도 모두 막나요?
   - 동일 identifying parameter의 Job launch만 제어한다. HTTP 직접 저장/다른 작업은 DB unique로 보호해야 한다.
4. 예외를 잡으면 transaction은 계속 쓸 수 있나요?
   - 내부 transactional proxy가 rollback-only를 표시했다면 불가능할 수 있다. 공유 transaction인지와 예외 발생 위치를 확인해야 한다.

## 9. AI 활용과 사람의 검증

- AI가 코드 분석, 작은 변경 단위 제안, 구현·MySQL 테스트·문서 초안을 수행했다.
- 전체 실패를 무조건 가짜 성공이라고 단정하지 않고 기존 proxy transaction 동작 가능성을 구분했다.
- 사용자가 독립 재현·설명했는지는 아직 확인하지 않았다. 본인이 만든 기존 통계 코드와 이번 diff를 함께 읽고 transaction 경계를 설명하는 연습이 필요하다.
- 참고: [Spring Batch domain language](https://docs.spring.io/spring-batch/reference/domain.html). 현재 문서 버전과 프로젝트 라이브러리 버전 차이는 실제 로컬 테스트로 보완했다.
