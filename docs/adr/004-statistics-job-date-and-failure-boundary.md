# ADR-004: 통계 배치의 날짜 식별과 fail-fast 경계

- 날짜: 2026-09-06
- 상태: 채택 — 회사별 checkpoint 전의 중간 단계
- 기존 개인 기여: Statistics/Batch. 근거는 `../portfolio/original-contributions.md`.

## 문제

기존 Job은 timestamp와 RunIdIncrementer로 새로운 실행 단위를 만들고 tasklet 내부에서 실행 당일 기준 전일을 다시 계산했다. 같은 날짜를 다음 날 재처리하려는 목적을 Job identity에 담지 못했다. 회사별 예외를 잡고 계속 순회하면서 성공 로그를 남겼지만 실제 transaction은 전체 tasklet 단위였다. 예외 종류/발생 위치에 따라 마지막에 rollback-only 실패가 날 수 있어 회사별 성공으로 볼 수 없다.

## 결정

1. `statisticsJob + identifying targetDate(yyyy-MM-dd)`를 실행 단위로 정한다. timestamp/run.id 및 추가 parameter로 같은 날짜의 실행 제한을 우회하지 못하도록 validator가 정확히 하나의 식별 String을 요구한다.
2. tasklet은 JobParameter의 날짜만 사용한다. scheduler는 Asia/Seoul 02:00에 한국 시간 전일을 넘긴다. 테스트에서는 cron을 `-`로 비활성화한다.
3. 기존 전체 tasklet transaction을 유지하면서 회사 실패를 즉시 전달한다. 회사 ID·대상 날짜를 실패 설명에 포함한다. 처리 로그는 commit 성공이 아니라 commit 대기임을 표시한다.
4. 실패한 날짜는 같은 parameter로 새 JobExecution을 만들어 전체 재시작한다. 완료된 날짜의 실행과 이미 진행 중인 같은 날짜의 중복 실행은 JobRepository가 거부하도록 한다.

## 대안·트레이드오프

- 예외 수집 후 계속 진행: 전체 transaction이 이미 rollback-only일 수 있어 회사별 독립 성공처럼 다루기 어렵다.
- 회사별 REQUIRES_NEW만 추가: 일부 결과 commit 뒤 checkpoint/재시작/unique가 없는 과도 상태가 생긴다. 이번에는 선택하지 않는다.
- chunk/partition + 회사별 checkpoint: 최종 목표에 적합하지만 DB unique, 성공 회사 생략, active company snapshot 정책과 함께 구현해야 하므로 후속 단계다.

현재는 첫 회사 결과도 나중 회사 실패 시 rollback되고 재시작 때 다시 계산된다. 실패 회사만 재개하거나 모든 동시 저장 경로를 보호하는 완성형 배치가 아니다. API 직접 저장은 JobRepository를 우회하며 DB company/date unique는 아직 필요하다.

## 전환·검증 한계

- 기존 timestamp 기반 JobInstance를 새 validator로 그대로 restart하지 않는다. 과거 실행 이력과 해당 날짜의 저장 결과를 확인하고 새 targetDate 실행 여부를 결정해야 한다. metadata를 삭제하거나 임의 status 수정으로 강제 재실행하지 않는다.
- 완료 날짜의 late-event 재계산은 별도 correction 정책으로 다룬다. run.id 추가로 우회하지 않는다.
- 동일 MySQL JobRepository를 쓰는 2개 thread에서 중복 launch 거부를 검증한다. 다중 JVM/운영 scheduler 분산 장애나 프로세스 kill은 미검증이다.
- [작업 기록](../portfolio/work-logs/2026-09-06-statistics-job-identity.md)
