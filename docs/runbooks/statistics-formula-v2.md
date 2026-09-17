# 통계 V2 전환·늦은 종료 보정

2026-09-07. CHANGE-040의 자동 보정·fleet snapshot과 명시적 재계산 절차다. 실제 운영 실행 완료 기록이 아니다.

## 배포 전

1. 승인된 복원본에서 V4 preflight/중복, Batch 실행 상태와 원천 Trip 품질을 확인한다.
2. 이전 scheduler를 중지하고 미종료 STARTED 실행을 조사한다. 임의 metadata 삭제/상태 변경 금지.
3. V5를 적용하여 기존 row가 formula_version=1이고 기존 숫자가 보존되는지 확인한다.
4. V2 quality 필드를 지원하는 FE와 함께 전환한다. 새 FE는 구 BE 숫자를 새 공식으로 표시하지 않는다.
   새 BE와 구 FE는 분/h 오류와 coverage 경고 누락이 있으므로 완전 호환이라고 간주하지 않는다.
5. raw 좌표/credential 없이 이전 집계와 대상 회사·날짜·변경 사유·승인 참조를 기록한다.

```sql
SELECT company_id, DATE(date) AS target_date, formula_version,
       power_on_count, total_driving_time, average_operation_rate, calculated_at
FROM statistics
WHERE company_id = :approved_company_id
  AND date >= :approved_day_start AND date < :approved_next_day_start;
```

위 SQL의 `:name`은 사용 중인 DB client의 바인딩 파라미터 자리다. 원시 SQL 문자열 연결로
사용자 입력을 넣지 않는다. 예전 V4 DB에는 새 column이 없으므로 migration 이후 조회용이다.

## 명시적 보정

- 사용 경로: `POST /api/statistics/save?companyId=…&targetDate=yyyy-MM-dd`, 기존 ADMIN 헤더 인증.
- 회사 역할/비로그인은 해당 관리 경로에 접근할 수 없다. 토큰을 URL에 넣지 않는다.
- 종료된 한국 날짜만 허용. 보정 사유는 구버전 전환/늦은 OFF 등으로 외부 작업 기록에 남긴다.
- 회사 lock 후 전체 당일 통계를 재계산하여 같은 unique row를 바꾼다. 자동 통계 삭제는 없다.
- 완료 JobInstance를 새 timestamp로 강제 실행하지 않는다. 기존 STARTED 복구를 이 API가 해결하지 않는다.
- 전후 formulaVersion/계산 시각/총 시간/GPS 수/미종료 수와 GET quality를 비교한다.
- 늦은 OFF가 여러 날짜를 가로지르면 영향을 받는 **각 날짜**를 확인 후 재계산한다.

실패하면 회사 transaction은 rollback한다. 이미 성공한 다른 회사는 유지된다.
CHANGE-040에서 통계 값이 바뀌면 `statistics_revision`에 이전/새 revision을 보존한다. 같은 값의 재계산은 revision을 늘리지 않는다. 최초 신규 집계는 당시 active fleet의 차량 ID 목록을 고정하고 이후 계산은 이 목록의 원천을 사용한다. 기존 통계에 snapshot이 없으면 409 `STATISTICS_FLEET_REVIEW_REQUIRED`로 보존한다. ADMIN이 현재 차량 목록과 영향을 검토한 경우만 기존 save 경로에 `captureCurrentFleet=true`를 명시해 seed한다. 이 seed는 실제 과거 소속/활성 이력의 복원이 아니다.

Trip/GPS 저장 transaction에서 기존 통계 날짜에 보정 요청을 기록한다. queue 기록이 실패하면 원천 저장도 rollback하여 조용한 보정 누락을 방지한다. 회사/날짜별 requested/completed generation을 사용하고 5분 주기의 worker가 최대20개씩 처리한다(`thisway.statistics.correction-cron`). 실패는 backoff 후 재시도하며 원천이 같은 중복 GPS는 불필요한 revision을 만들지 않는다. 완료되지 않은 과거 날짜 자체를 자동 생성하는 backfill은 아니므로 누락 날짜는 기존 배치/명시적 날짜 계산으로 처리한다. bulk 운영 전환은 실제 데이터 조사와 실행 결과 기록이 필요하다.

## 재현

```bash
./gradlew test --tests '*CompletedTripTimeTest' --tests '*StatisticsBatchIntegrationTest' --console=plain
./gradlew test --tests '*LegacySchemaPreflightIntegrationTest' --console=plain
```

자동화 테스트는 컨테이너 fixture만 사용한다. 운영 DB나 기존 volume에는 접근하지 않는다.


## STARTED 실행의 복구

프로세스가 강제 종료되어 STARTED가 남으면 [offline 복구 절차](statistics-orphan-recovery.md)를 따른다. age만 보고 살아있는 실행을 탈취하지 않는다. 모든 writer 중지, execution/version/step snapshot 검토와 감사 기록이 필요하며 원천 통계와 성공 checkpoint는 삭제하지 않는다.
