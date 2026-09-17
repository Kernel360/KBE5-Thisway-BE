# CHANGE-042: Statistics JVM crash 뒤 orphan execution 제한 복구

## 메타데이터

- 날짜: 2026-09-07
- 작업자: 개인 현대화, AI 구현·검증 보조
- 브랜치/기준: `codex/statistics-batch-restart@ef74fcf`, 기존 dirty 변경 유지
- 상태: Verified (전용 실제 JVM 종료·MySQL 복구 검증 완료)
- 선행: CHANGE-024 날짜별 Job identity, CHANGE-025 company checkpoint

## 1. 문제와 근거

기존 실제 MySQL 통계 테스트는 예외가 발생해 Spring Batch가 FAILED를 기록한 뒤 재시작하는 경우를 검증했다.
프로세스가 강제 종료되면 FAILED를 기록할 기회가 없어 JobExecution이 STARTED로 남고,
같은 `targetDate`를 다시 실행하면 AlreadyRunning으로 거부된다. 회사별 REQUIRES_NEW checkpoint는
이미 commit되어 있어도 metadata 복구 없이는 같은 JobInstance에서 재개할 수 없다.

원 팀의 Statistics 기능과 사용자의 기존 기여를 이번 개인 전체 구현으로 표현하지 않는다.
이번 변경은 별도 offline 운영 CLI·감사 schema·crash 재현 검증이며 기존 batch 계산/worker 코드는 수정하지 않는다.

## 2. Acceptance criteria

- [x] 실제 별도 Boot JVM을 강제 종료해 MySQL에 STARTED와 첫 회사 checkpoint가 남는 것을 검증한다.
- [x] writer 중지 확인 없이 복구할 수 없고, job/step snapshot/version이 바뀌면 복구를 거부한다.
- [x] metadata FAILED 전환과 감사 기록이 하나의 transaction이며 감사 실패 시 rollback한다.
- [x] 동일 날짜 restart는 같은 JobInstance/새 execution, 첫 회사 결과/checkpoint 보존, 미완료 회사 계산이다.
- [x] 완료 job/이전 snapshot 재사용을 거부하고 자동 takeover로 과장하지 않는다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| 오래된 STARTED를 자동 FAILED 처리 | 구현이 작음 | 느린 정상 JVM을 실패 처리하고 중복 business writer를 만들 수 있음 | 거절 |
| owner heartbeat/lease + business fencing | 자동 복구 가능 | 기존 모든 company transaction에 owner fencing 결합이 필요함 | 별도 운영 설계로 남김 |
| writer 중지 증거 + offline snapshot CAS | 기존 계산/transaction을 변경하지 않고 제한 복구 가능 | 운영자가 모든 owner/launcher의 중지를 실제로 확인해야 함 | 채택 |

metadata의 version만 올려서는 살아 있는 이전 JVM의 통계 저장을 막지 못한다. 따라서 version 검사를
분산 lock이나 liveness 증거로 표현하지 않는다. offline gate가 이 도구의 필수 전제다.

## 4. 구현과 실행 흐름

- `src/replay/java/org/thisway/ops/StatisticsOrphanRecovery.java`: web artifact 밖의 JDBC preview/execute CLI.
- `V14__statistics_orphan_recovery_audit.sql`: 승인·중지 증거 reference·변경 전 snapshot과 버전 감사.
- `StatisticsCrashRecoveryIntegrationTest`: 실제 자식 JVM 종료 및 CAS·audit 실패 검증.
- [운영 절차](../../runbooks/statistics-orphan-recovery.md): process/DB session 중지 증거, preview, 실행, 확인·재시작.

담당자가 모든 launcher와 기존 owner process/DB transaction을 중지→preview의 execution ID·version·
step 포함 snapshot digest 확인→명시적 offline 확인과 승인/evidence reference 전달→짧은 transaction에서
다시 잠그고 비교→진행 중 job/step FAILED 및 version 증가+감사 원자 저장→별도 통제된 launcher에서
같은 targetDate 재시작. 완료 step/context/business result/checkpoint는 수정하지 않는다.

CLI는 loopback DB/tunnel만 허용하고 credential은 환경변수로 받는다. password/JDBC URL/기존 오류 본문은
실패 출력에 포함하지 않는다. 이 도구는 자동 scheduler나 HTTP endpoint로 등록되지 않는다.

## 5. 검증 결과

실행 명령:

```bash
./gradlew statisticsCrashRecoveryTest --console=plain
```

- Before: 예외 FAILED restart는 검증됐으나 JVM kill 뒤 STARTED 재개 도구·실행 증거 없음.
- After: MySQL 8.0.40/Flyway V14에서 전용 4개 통과. 실패·오류·skipped 0, Gradle 19초. 실제 자식 Boot JVM을 강제 종료한 뒤 STARTED 잔류와 복구·재시작을 검증했다.
- 비영 fixture: 첫 회사 60분 통계와 checkpoint가 먼저 commit됨. JVM 종료 뒤 첫 회사 원천에 60분 운행을 추가해도 restart는 기존 60분 결과와 checkpoint 행을 그대로 보존하며, 미완료 둘째 회사는 120분으로 저장했다.
- CAS: job version drift, job version이 같고 step version만 변한 경우, 완료 execution/이전 snapshot 재사용을 거부했다. 감사 unique 충돌에서는 FAILED 전환과 version 증가가 함께 rollback됐다.
- XML: `build/test-results/statisticsCrashRecoveryTest/TEST-org.thisway.support.batch.StatisticsCrashRecoveryIntegrationTest.xml` — tests=4, failures=0, errors=0, skipped=0.
- CLI 연결: `./gradlew statisticsOrphanRecovery --args='--help' --console=plain` 통과(1초), DB 접속 없이 별도 운영 main class와 도움말 확인.
- `git diff --check` 통과. 이번 전용 검증에서 실패 명령은 없었다. JVM CDS warning은 출력됐지만 테스트 실패는 아니다.
- 전용 테스트는 `statistics-crash` tag이며 기본 `test`와 분리한다.
- 전체 통합 회귀 결과는 root의 최종 작업 기록에서 별도 확인한다.

## 6. 실패 사례와 남은 위험

- 운영자의 all-writers-stopped 확인이 사실과 다르면 안전하지 않다. metadata CAS는 살아 있는
  이전 JVM의 business transaction을 fencing하지 않으며 자동 takeover가 아니다.
- 강제 종료와 DB transaction 종료는 별개일 수 있어 runbook은 owner DB session/transaction도 확인한다.
- snapshot drift/감사 insert 실패는 전체 복구를 중단한다. commit 뒤 연결 단절의 불명확한 결과는
  metadata+DB 감사 조회 후 판단한다. 성공 여부를 추정해 반복 실행하지 않는다.
- 실제 운영 host/auto restart 정책/운영 DB에는 접근하지 않았다. 회사 목록의 역사적 snapshot을 추가하지 않는다.
- 기존 timestamp parameter job, 추가/비식별 parameter, unknown metadata 상태는 제한 도구가 거부한다.

## 7. 학습 기록

- 비즈니스 commit과 framework metadata update 사이에는 process crash 구간이 있다.
- JobInstance는 같은 업무 날짜, JobExecution은 실행 시도이며 company checkpoint는 성공한 업무 결과다.
- optimistic version/CAS는 읽은 상태의 변경을 감지한다. process가 살아 있는지 증명하거나 business writer를 fencing하지 않는다.
- 최소 복구 도구는 변경 범위·실행 전제·감사·rollback·불명확한 결과 확인 방법을 함께 갖춰야 한다.

## 8. 예상 면접 질문

1. 한 시간 된 STARTED를 FAILED로 바꾸면 안 되나요?
   - 느리지만 살아 있는 작업일 수 있다. owner liveness/lease와 business fencing이 없다면
     모든 writer의 중지 증거를 먼저 확인하는 offline 복구만 제공한다.
2. version이 같으면 job이 죽었다고 판단할 수 있나요?
   - 아니다. snapshot CAS는 preview 이후 변경만 검증한다. 별도의 process/DB transaction 중지 증거가 필요하다.
3. 복구할 때 checkpoint도 지워야 하나요?
   - 아니다. 회사 통계와 원자 commit된 성공 marker를 보존해야 restart에서 이미 성공한 회사를 생략한다.
4. 실제로 프로세스를 죽여 검증했나요?
   - 전용 테스트에서 disposable DB에 연결한 자식 Boot JVM을 실제 강제 종료했다.
     종료 코드와 process 중지, STARTED 잔류, company checkpoint 재시작을 확인했으며 운영 host 장애까지 검증한 것은 아니다.

## 9. AI 활용과 사람의 검증

- AI가 기존 Batch/checkpoint를 읽고 offline 대안, CLI, SQL audit, subprocess fixture와 문서를 작성했다.
- 단순 age 기반 자동 복구는 business writer 중복 위험 때문에 거절했다.
- 사용자는 남은 로컬 작업 진행을 요청했지만 실제 운영 복구 승인을 한 것은 아니다. 배포 DB는 변경하지 않았다.
- 사람은 PID/DB session 중지 확인→snapshot 비교→같은 날짜 restart 흐름을 독립적으로 설명해야 한다.
- AI가 확인하지 못한 것은 운영 owner topology·실제 중지 증거·사용자의 설계 이해다.
