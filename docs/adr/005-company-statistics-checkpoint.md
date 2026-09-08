# ADR-005: 회사별 통계 commit과 업무 checkpoint

상태: 채택. 2026-09-06. ADR-004의 전체 rollback 경계를 대체한다.

## 결정

`statisticsJob(targetDate)`의 JobInstance ID와 company ID를 완료 키로 사용한다.
별도 Spring bean `StatisticsCompanyWorker.process`가 `REQUIRES_NEW` transaction에서
회사 행 잠금 → checkpoint 조회 → 계산·통계 저장 → checkpoint insert를 수행한다.
통계와 marker는 동일 DataSource/JpaTransactionManager transaction에 참여한다.
Tasklet은 예외를 전파하여 FAILED를 기록한다. 앞 회사 commit은 보존된다.

재시작 시 active 회사 목록을 다시 조회하되 성공 marker가 있는 회사는 계산을 생략한다.
회사 목록을 최초 실행 시점으로 동결하는 방식은 아니다. 재시작 사이 비활성화된 회사는
제외되고 새 active 회사는 포함된다. 과거 보유 차량 수 또한 snapshot이 아니므로 역사적
fleet 재현을 보장하지 않는다.

직접 저장 API는 기존대로 명시적 재계산 경로로 유지한다. 같은 회사 행 잠금을 공유하여
배치와 직렬화하고, 결과를 대체한다. checkpoint는 해당 job에서 한 번 성공했다는 뜻이지
이후 원천 데이터나 수동 보정이 없었다는 뜻이 아니다. 완료 JobInstance를 새 timestamp로
속여 재실행하지 않는다. 자동 late-event correction 및 감사 revision은 후속 설계다.

## 대안과 비용

- catch 후 계속: 실패를 성공으로 오인할 수 있어 거절.
- REQUIRES_NEW만 추가: commit 후 Batch metadata 저장 전 실패 시 성공 회사 재계산. 업무 marker를 함께 저장.
- chunk(1) reader checkpoint: 프레임워크 표준이나 재시작 reader 정렬/회사 목록 snapshot 설계가 필요.
  현재 순차 tasklet을 유지하는 작은 전환을 채택했으며 대규모 회사 수에는 paging/partition 검토.
- 회사 행 잠금: 처음 통계 행이 없는 경우도 직렬화. 서로 다른 날짜의 같은 회사도 대기하며
  관리자 회사 수정과 경합한다. tasklet 외부 transaction이 있으므로 최소 두 DB connection 필요.

## DB 경계와 운영

V4는 generated `DATE(date)` column에 `(company_id, statistic_day)` unique를 둔다.
단순 datetime unique와 달리 같은 날 다른 시각도 중복으로 차단한다. legacy 중복은 자동
삭제/병합하지 않으며 ALTER 실패로 중단한다. 복원본에서
`src/main/resources/db/preflight/statistics-readiness.sql`을 먼저 실행한다.
MySQL DDL은 전체 migration rollback이 아니므로 부분 적용/실패 history를 조사한 뒤 복구한다.
운영 데이터에 Flyway repair나 DELETE를 자동 실행하지 않는다.

기존 timestamp-keyed FAILED/STARTED 실행은 새 파라미터 정책으로 자동 재시작하지 않는다.
배포 중 기존 scheduler를 중지하고 상태를 조사해야 한다. 강제 JVM 종료 후 STARTED 복구와
다중 JVM 최초 launch race는 이번 테스트가 증명하지 않는다.

## 검증

실제 MySQL에서 성공 회사 보존/재시작 생략, checkpoint FK 실패 시 통계 rollback,
직접 저장 4-thread 동시성, 같은 날 다른 시각 unique, legacy duplicate 보존을 검사한다.
전체 결과는 CHANGE-025에 기록한다.
