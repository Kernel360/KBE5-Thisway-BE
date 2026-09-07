# Statistics orphan execution의 제한 복구

2026-09-07. 실제 배포 DB에 실행한 기록이 아니다. `statisticsJob`의 같은 `targetDate` 재시작을
막는 STARTING/STARTED/STOPPING metadata를, **모든 기존 writer가 중지됐다는 운영 증거 확인 뒤** 복구한다.
실행 시간이 오래됐다는 이유만으로 실행 중인 job을 실패 처리하지 않는다.

## 반드시 먼저 확인할 중지 증거

1. 해당 job을 실행할 모든 application instance·수동 launcher의 scheduler와 자동 재시작을 중지한다.
   기존 owner host/PID 또는 container identity, 종료 확인 시각, 재시작 차단 상태를 승인 기록에 남긴다.
2. 기존 owner의 DB connection/transaction도 끝났는지 담당자가 확인한다. JVM이 응답하지 않는다는
   사실이나 health check 실패, metadata age, 오래 같은 version인 상태만으로 판단하지 않는다.
3. application의 작업 재개를 차단한 상태에서 대상 execution/날짜/성공 company checkpoint를 점검한다.
   더 최근 실행이 있거나 기존 timestamp parameter를 쓰는 legacy job이면 이 도구를 쓰지 않는다.
4. 중지 증거를 추적할 수 있는 reference를 기록한다. 예: `INC-123/owner-stop-evidence`.

담당자가 읽을 수 있는 DB metadata 예시다. 권한·환경에 따라 보이는 session 범위가 다를 수 있다.
실제 query text, password, JWT, 원시 좌표를 로그·티켓에 복사하지 않는다.

```sql
SELECT ID, USER, HOST, DB, COMMAND, TIME
FROM information_schema.PROCESSLIST WHERE DB=DATABASE();

SELECT trx_id, trx_mysql_thread_id, trx_started
FROM information_schema.innodb_trx;

SELECT e.JOB_EXECUTION_ID,e.JOB_INSTANCE_ID,e.VERSION,e.STATUS,e.START_TIME,e.END_TIME,
       p.PARAMETER_VALUE AS target_date
FROM BATCH_JOB_EXECUTION e JOIN BATCH_JOB_INSTANCE i ON i.JOB_INSTANCE_ID=e.JOB_INSTANCE_ID
JOIN BATCH_JOB_EXECUTION_PARAMS p ON p.JOB_EXECUTION_ID=e.JOB_EXECUTION_ID
WHERE i.JOB_NAME='statisticsJob' AND p.PARAMETER_NAME='targetDate'
ORDER BY e.JOB_EXECUTION_ID DESC LIMIT 20;
```

## Preview와 정확한 대상 복구

CLI는 별도 `replay` source set에 있어 web application artifact에 포함되지 않는다.
V14까지 Flyway migration이 적용된 DB에, 승인된 loopback 접속 또는 tunnel을 사용한다.
아래 환경변수의 비밀 값은 secret manager/로컬 환경으로 주입한다. 명령행 인수나 Git 파일에 쓰지 않는다.

- `STATISTICS_RECOVERY_JDBC_URL`: `jdbc:mysql://127.0.0.1:포트/DB` 형태
- `STATISTICS_RECOVERY_USERNAME`, `STATISTICS_RECOVERY_PASSWORD`

```bash
./gradlew statisticsOrphanRecovery --args='preview 123'
```

출력의 execution ID, targetDate, 최신 execution ID, job/step status와 version을 읽는다.
주소·원시 payload·기존 exception 본문은 출력하지 않는다. 함께 출력된 `sha256`은
job version 외에도 step version/status와 날짜를 포함한 snapshot을 식별한다.

모든 writer 중지 증거를 확인한 담당자만 `STATISTICS_RECOVERY_OFFLINE_CONFIRMED=yes`를 설정한다.
다음 명령의 `123`, `2`, `확인한64자리sha256`, 승인 ID, 중지 증거 reference를 preview와 기록에 맞춰 넣는다.

```bash
./gradlew statisticsOrphanRecovery --args='execute 123 2 확인한64자리sha256 INC-123 INC-123/owner-stop-evidence'
```

이 플래그는 운영자의 확인을 표현한다. 도구가 원격 host의 liveness를 검증했다는 뜻이 아니다.
writer가 살아 있을 가능성이 남으면 execute하지 않는다.

실행은 짧은 단일 DB transaction으로 다음을 처리한다.

1. JobInstance→JobExecution→StepExecution을 잠그고 preview snapshot/version을 다시 비교한다.
2. 가장 최근의 미완료 `statisticsJob`인지, 유효한 단일 identifying `targetDate`인지 확인한다.
3. 진행 중 step와 job만 FAILED/종료 시각으로 바꾸고 metadata version을 증가시킨다.
   완료 step, context, 통계 결과, company checkpoint, JobInstance identity는 보존한다.
4. `statistics_orphan_recovery_audit`에 승인 ID, 중지 증거 reference, 변경 전 snapshot/version을
   같은 transaction으로 기록한다. 감사 insert가 실패하면 FAILED 전환도 rollback된다.

변경된 snapshot, 완료 실행, 더 최근 실행, legacy parameter, 지원하지 않는 step 상태는 거부한다.
네트워크가 commit 직후 끊기면 결과가 불명확할 수 있으므로 같은 명령을 무작정 반복하지 말고 아래를 조회한다.

```sql
SELECT JOB_EXECUTION_ID,VERSION,STATUS,END_TIME
FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID=123;
SELECT job_execution_id,before_version,after_version,approval_id,stopped_evidence_ref,recovered_at
FROM statistics_orphan_recovery_audit WHERE job_execution_id=123;
```

## 동일 날짜 재시작

복구 CLI는 job을 자동 실행하지 않는다. 담당자가 확인한 단일 application/내부 launcher에서
기존 Spring proxy `StatisticBatchConfig.runForDate(targetDate)`를 호출한다. 같은 날짜에는 같은
JobInstance와 새 JobExecution을 사용하며, 성공한 company checkpoint를 확인하고 남은 회사만 계산한다.
timestamp parameter를 추가해 새 JobInstance를 만들거나 business checkpoint를 지우지 않는다.

완료 후 새 execution의 COMPLETED, 동일 instance ID, 회사별 결과/checkpoint 수를 확인한다.
업무 통계에 뒤늦게 들어온 데이터가 있으면 별도 correction 흐름으로 다룬다. orphan recovery가
기존 성공 checkpoint를 무효화하거나 전체 날짜를 강제로 다시 계산하지는 않는다.

## 검증과 한계

```bash
./gradlew statisticsCrashRecoveryTest --console=plain
```

전용 disposable MySQL과 별도 Boot JVM을 실행한다. 첫 회사의 실제 계산·checkpoint commit 뒤
두 번째 회사 직전에서 fixture만 정지시키고, 테스트가 소유한 자식 process를 `destroyForcibly()`로 종료한다.
그 뒤 STARTED 잔류→정확한 metadata 복구→동일 날짜 restart→첫 회사 checkpoint 보존을 검증한다.

복구 전 모든 writer가 중지되어야 한다. version/CAS는 stale preview를 차단하지만 살아 있는
기존 JVM의 company business transaction을 fencing하지 않는다. 따라서 자동 takeover·split brain
방지·실제 운영 orchestration 완료로 주장하지 않는다.

설계·실제 실행 결과·학습: [CHANGE-042](../portfolio/work-logs/2026-09-07-statistics-orphan-recovery.md).
