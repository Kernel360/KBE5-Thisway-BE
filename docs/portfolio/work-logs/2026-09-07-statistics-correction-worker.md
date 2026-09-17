# CHANGE-040: 통계 영속 보정·revision 감사와 최초 fleet snapshot

## 메타데이터

- 날짜: 2026-09-07
- 기준: `codex/statistics-batch-restart@ef74fcf` 및 기존 미커밋 CHANGE-036/037
- 상태: Verified (좁은 MySQL 통합 30개 통과). 전체 저장소 최종 회귀는 통합 작업 기록에서 별도로 확인한다. 운영 적용 기록이 아니다.
- 관련: P1-03C, CHANGE-024/025/030
- 원래 팀 프로젝트와 사용자의 기존 Vehicle·Statistics/Batch 기여는 `../original-contributions.md` 기준이다. 이번 코드는 그 위의 개인 현대화다.

## 1. 문제와 근거

완료된 `statisticsJob(targetDate)`는 같은 날짜를 다시 launch할 수 없다. 늦은 ON/OFF/GPS가
들어와도 자동 보정 요청이 없고, 직접 저장은 같은 통계 row를 덮어 이전 값을 보존하지 않았다.
특히 OFF가 하루 늦으면 완료 운행 시간 외에 이후 집계일의 `unclosedTripCount`도 바뀐다.
OFF 날짜까지만 보정하면 그 이후 날짜에 남은 미종료 수가 틀린 채 유지될 수 있다.

## 2. Acceptance criteria

- [x] 원천 변경과 기존 집계일의 보정 요청을 같은 transaction으로 commit/rollback한다.
- [x] 동일 회사·날짜 중복 요청은 한 queue row에 누적하고 두 worker가 한 세대를 중복 확정하지 않는다.
- [x] 보정 도중 도착한 새 원천 변경은 완료 marker로 지워지지 않는다.
- [x] 기존 통계와 완료 JobInstance를 보존하면서 변경된 통계 값만 revision으로 기록한다.
- [x] 의미가 같은 재계산은 revision과 calculatedAt을 바꾸지 않는다.
- [x] 새 계산부터 fleet 차량 ID 목록을 보존하며 차량 삭제·추가가 과거 분자·분모를 바꾸지 않는다.
- [x] 기존 snapshot 없는 row는 자동 추정하지 않고 ADMIN의 명시적 현재 fleet 검토만 허용한다.
- [x] 회사별 보정 실패는 값·감사·완료 marker를 모두 rollback하고 backoff 후 재시도한다.
- [x] 실제 MySQL migration/JPA validate 및 좁은 회귀 결과를 기록한다. 전체 suite는 최종 통합 단계에서 실행한다.

## 3. 선택지와 결정

| 선택지 | 장점 | 비용·위험 | 결정 |
| --- | --- | --- | --- |
| 매번 과거 모든 날짜 재계산 | 구현 단순 | 불필요한 계산·변경 이력, 종료 범위 불명확 | 거절 |
| AFTER_COMMIT 메모리 이벤트만 처리 | 원천 transaction 짧음 | commit 직후 JVM 종료로 보정 요청 유실 | 거절 |
| 원천 transaction에서 영속 요청, 별도 worker | 원천·보정 요청 원자성, 재시작 가능 | source transaction에 회사 lock·queue I/O 추가 | 채택 |

첫 계산과 source enqueue 모두 회사 row를 잠근다. 현재 통계 row를 locking read하므로
기존 REPEATABLE READ snapshot 때문에 새 집계일을 놓치지 않는다. 날짜 범위는 inclusive
한국 날짜이며 오늘 이후는 제외한다. **이미 통계가 있는 날짜만** queue에 넣는다.
미집계일 신규 backfill이나 원천 이벤트 이전에 있었던 운영 이력을 자동 추정하지 않는다.

## 4. 구현과 실행 흐름

`StatisticsSourceChanged(companyId, fromDate, throughDate, reason)`를 원천 transaction 안에서
동기 publish → 회사 lock → 기존 집계일 current locking read → 회사·날짜 request generation
증가 → source와 함께 commit → 5분 주기 scheduler가 due 요청 최대 20개 선택 → 각 요청을
별도 REQUIRES_NEW worker가 회사 lock 아래 재계산 → 변경된 결과·revision snapshot·완료 세대
같은 transaction으로 commit한다. JVM이 transaction 중 종료되면 pending 요청이 남아 재시도한다.

- Trip ON/OFF: 변경이 적용된 경우 ON 날짜부터 한국 어제까지. OFF 이후 미종료 수 변경도 포함한다.
- GPS: 실제 observation의 발생일 범위. 완전 중복 수신이 queue에 들어와도 같은 계산 결과면 감사 이력이 늘지 않는다.
- `V11__statistics_correction_queue.sql`: `(company_id,target_date)` unique, requested/completed generation,
  attempts, nextAttemptAt, 오류 **클래스명**만 저장한다.
- `V12__statistics_revision_audit.sql`: 기존 통계에 revision=0 추가. 숫자는 변경하지 않는다.
  첫 의미 있는 수정 시 기존 row의 revision0 snapshot을 보존한다. 이후 실제 변경만 증가한다.
- `V13__statistics_fleet_snapshot.sql`: 새 집계의 최초 계산 당시 active 차량 ID 목록을 기록한다.
  header를 별도로 둬서 차량 0대인 known snapshot과 목록을 모르는 legacy row를 구분한다.
  migration은 기존 row의 소속 이력을 추정하는 데이터 INSERT를 수행하지 않는다.
- 후속 보정의 Trip/GPS/시작 위치 집계는 저장된 vehicle ID 목록에 한정한다. 이후 차량이
  비활성화되거나 새 차량이 등록돼도 같은 집계일의 fleet 분모와 원천 차량 범위는 유지된다.
  API `quality.fleetBasis`는 `INITIAL_CALCULATION_FLEET_SNAPSHOT` 또는
  `LEGACY_FLEET_SNAPSHOT_UNKNOWN`으로 이 차이를 표현한다.
- snapshot은 공식 버전·fleet 수·GPS 수·미종료 수·계산시각·기존 집계 지표·24시간 가동률을 보존한다.
  raw 좌표·credential·개인정보를 저장하지 않는다.
- 일반 직접 저장과 정규 batch는 현재 `DIRECT_OR_BATCH` 이유로 구분 없이 기록한다.
  운영자 identity/승인 ticket까지 포함하는 관리 감사 API는 이번 범위가 아니다.
- Micrometer `thisway.statistics.correction{outcome=processed|noop|failed}`를 기록한다.
  태그에 company/date/원천 데이터를 넣지 않는다. pending 상세는 읽기 query로 조사한다.

## 5. 검증 결과

전용 명령:

```bash
./gradlew test --tests '*StatisticsCorrectionIntegrationTest' --tests '*StatisticsBatchIntegrationTest' --tests '*CompletedTripTimeTest' --console=plain
```

첫 실행: 신규 13개 + 기존 Batch 10개 + pure 계산 5개, 총 28개 통과, 실패/오류/skip 0, 21초.
실제 원천 hook·metrics 추가 후 최종 실행: **신규 15개 + 기존 Batch 10개 + pure 계산 5개, 총 30개 통과,
실패/오류/skip 0, BUILD SUCCESSFUL 20초**. `build/test-results/test/TEST-*.xml`로 집계했다.
`git diff --check` 통과. 전용 테스트는 실제 MySQL 8.0.40/Flyway/JPA 계산·저장을 사용하고
통계 서비스 경계의 실패·대기 시점만 spy로 제어한다. 이벤트 enqueue 검증은 동일 transaction의
실제 source fixture 변경과 Spring event publisher를 사용한다. production `TripLogService.saveTripLog`와
인증된 `GpsLogSaveService.saveGpsLog`에서도 실제 저장→queue 연결, 중복 처리, queue 실패→원천 rollback을
검증한다. 외부 지오코딩만 mock으로 격리한다. 테스트 fixture의 수치는 운영 성능 개선 수치가 아니다.

## 6. 실패 사례와 남은 위험

- 첫 명령은 sandbox에서 Gradle wrapper cache lock을 열지 못해 실행 전 실패했다. 승인된 동일 테스트
  명령으로 cache와 Docker 접근을 허용한 후 실제 MySQL 검증을 수행했다.
- source hook 실패 주입 테스트의 첫 실행은 30개 중 4개 실패했다. MANDATORY listener의 Spring proxy에
  Mockito stubbing을 직접 걸어 transaction 없음 오류가 발생했고 unfinished stubbing이 뒤 테스트에 전파됐다.
  실제 target spy를 unwrap하여 실패를 주입하도록 고쳤다. production transaction 요구를 완화하거나 skip하지 않았다.
- 최초 계산 시점 이전의 실제 가입·탈퇴·소속 이력은 없다. snapshot은 **최초 계산 시점에 관측한 차량 목록**이며
  과거 대상 날짜 당시의 실제 fleet을 복원한 자료가 아니다. 기존 snapshot 없는 row의 자동/일반 재계산은
  `STATISTICS_FLEET_REVIEW_REQUIRED`(409)로 거부하고 값을 보존한다.
- 현재 관리 API에는 기존 Vehicle의 회사 자체를 이전하는 기능이 없다. 외부 SQL로 소속을 바꾸거나
  과거 raw Trip/GPS를 다른 회사 의미로 수정하는 운영은 snapshot만으로 정당화할 수 없다.
- 이 scheduler는 Spring Batch의 오래된 STARTED metadata 복구를 수행하지 않는다.
  별도 killed JVM 검증·운영 절차가 필요하다.
- 감사 없는 기존 row의 revision0은 첫 변경 직전에 캡처한다. 그 전에 외부 SQL로 덮은 과거 값을
  소급 복원할 수 없다. 감사 테이블은 append만 하는 application 정책이며 DB 운영자 불변 저장소가 아니다.
- 회사 row lock은 source 수집과 통계 계산을 직렬화하므로 큰 회사의 통계 비용이 수집 지연에 영향을 준다.
  worker는 20개씩 처리하지만 source 날짜 범위의 기존 통계 행은 전부 enqueue하므로 긴 역사 범위는 측정이 필요하다.
- snapshot member에는 vehicle FK를 두지 않는다. 회사→snapshot 저장 시 FK 검사가 vehicle lock을 얻으면
  수집의 vehicle→company lock과 역순이 된다. ID 목록을 독립 보존하고 원천 조회 시 join한다.
- backoff는 30초 지수 정책으로 최대 1시간이다. 실패 원인이 해결되지 않으면 pending을 유지한다.
  스케줄러 주기가 5분이므로 실제 다음 시도는 due 시각 이후 스케줄 tick이다.
- 정규 batch와 다른 직접 저장이 같은 계산값을 만들면 revision이 증가하지 않는다.
  이것은 업무 값 변경 감사이며 모든 호출 횟수를 추적하는 access audit가 아니다.

## 7. 운영 확인

기본 correction cron은 `0 */5 * * * ?`(Asia/Seoul), `thisway.statistics.correction-cron=-`로 끈다.
운영 데이터 변경 없이 아래 읽기 query로 pending/재시도와 전후 revision을 확인할 수 있다.

```sql
SELECT company_id, target_date, requested_generation, completed_generation,
       attempts, next_attempt_at, last_error
FROM statistics_correction_request
WHERE requested_generation > completed_generation
ORDER BY next_attempt_at, id;

SELECT company_id, target_date, revision, reason, recorded_at, snapshot_json
FROM statistics_revision
WHERE company_id = :company_id AND target_date = :target_date
ORDER BY revision;
```

운영 실패 시 source 입력/현재 fleet/DB 오류를 먼저 조사한다. 완료 marker만 임의로 올리거나
queue row를 삭제하여 실패를 숨기지 않는다. 수정 후 자동 due 처리 여부를 확인한다.

기존 row에 당시 차량 목록이 없으면 먼저 원천/현재 차량 목록과 대상 날짜를 검토한다. 그 목록으로
재계산한다는 명시적 운영 판단 후 기존 ADMIN 헤더 인증 경로에
`POST /api/statistics/save?companyId=…&targetDate=yyyy-MM-dd&captureCurrentFleet=true`를 보낸다.
이 플래그는 **현재 차량 목록을 검토한 seed**이며 과거 가입 이력 복원이 아니다. 검토 참조는 외부 작업 기록에
남긴다. 이미 snapshot이 있으면 플래그를 주더라도 기존 목록을 덮어쓰지 않는다. 새로운 snapshot의 생성 자체는
숫자가 동일하더라도 `REVIEWED_FLEET_SNAPSHOT` 감사 revision으로 기록한다. COMPANY_ADMIN은 이 관리 요청에 403이다.

## 8. 학습과 면접

- `AFTER_COMMIT` 이벤트가 유실될 수 있는 정확한 crash 구간을 그려 보고 source transaction의
  durable marker가 그 구간을 어떻게 없애는지 설명한다.
- company lock → queue lock 순서와 requested/completed generation을 사용해 처리 중 새 이벤트가
  누락되지 않는 이유를 테스트에서 확인한다.
- JobInstance 완료와 업무 값 보정은 별개다. 날짜 job을 timestamp로 다시 만드는 대신 같은 row의
  감사 revision을 갱신한다.

1. OFF가 끝난 날 이후 통계도 왜 보정하나요?
   - 이전 계산에서 열린 Trip이 모든 후속 날짜의 미종료 수에 포함됐으므로 그 수도 수정해야 한다.
2. 중복 GPS 요청이 여러 번 와도 revision이 왜 하나인가요?
   - 요청 세대와 결과 revision은 다르다. worker는 요청을 처리하되 공식과 모든 집계 지표가 같으면 새 감사는 없다.
3. 회사 lock 대신 queue row만 잠그면 안 되나요?
   - 아직 queue나 통계 row가 없는 최초 생성과 source 동시 실행을 직렬화하지 못한다.
4. worker가 계산 후 죽으면 무엇이 남나요?
   - 통계·감사·완료 marker transaction이 함께 rollback되고 기존 pending 요청으로 다시 계산한다.
5. 과거 차량 소속은 정확히 보장하나요?
   - 새 계산부터 최초 계산의 차량 목록을 고정한다. 그보다 이전 날짜의 실제 소속은 별도 당시 이력이 있어야 한다.

## 9. AI 활용과 사람의 검증

AI가 현행 코드·CHANGE-024/025/030 분석, 설계 선택지, 구현·테스트·문서 초안을 수행했다.
메모리 AFTER_COMMIT만으로 요청을 처리하거나 기존 과거 fleet을 추정해 채우는 방식을 거절했다.
사용자는 남은 작업 실행을 위임했지만 직접 코드 흐름을 재현·설명했는지는 확인하지 않았다.
운영 DB, 실제 소속 이력, 실제 처리량·지연은 확인하지 않았으며 사람의 배포 판단과 별도 실험이 필요하다.
