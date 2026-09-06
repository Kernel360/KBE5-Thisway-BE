# V7 Trip 관측·거리 전환 점검

실제 운영 DB에는 적용하지 않았다. 대상/백업/복원본을 식별하고 승인된 환경에서 먼저 검증한다.
history 없는 과거 DB는 [legacy preflight](legacy-schema-preflight.md)를 먼저 수행하며
자동 baseline/repair, 데이터 삭제, 과거 거리 추정 보정은 하지 않는다.

## 적용 전

1. Flyway V1~V6 checksum, 전체 Trip 수, 미종료 수, 중복 운행 키의 수를 기록한다.
2. nullable tripMeter를 처리할 FE 배포를 준비한다. 새 FE는 구 서버의 거리 값 출처까지
   판별하는 도구는 아니므로 BE V7 전환 이전 값의 정확성을 주장하지 않는다.
3. 구 Power/Trip writer를 중지하는 승인된 전환 시간을 마련한다. 구버전은 NULL identity
   신규 행을 만들어 새 운행과 충돌할 수 있으므로 혼합 writer 운영은 지원하지 않는다.

아래 읽기 전용 집계는 원시 위치나 장치 정보를 출력하지 않는다.

```sql
SELECT COUNT(*) AS total, SUM(end_time IS NULL) AS unclosed FROM trip_log;
SELECT COUNT(*) AS duplicated_keys FROM (
    SELECT vehicle_id,start_time FROM trip_log GROUP BY vehicle_id,start_time HAVING COUNT(*)>1
) duplicates;
```

## 적용 후

- `identity_start_time`, `start_odometer`, `end_odometer`, `distance_meters`와 unique/check,
  조회용 `(vehicle_id,start_time)` index를 확인한다. 추가 index 성능은 실측하지 않았다.
- 기존 row 수·`total_trip_meter` 등 기존 값이 그대로인지 복원본에서 비교한다.
  신규 필드는 NULL이며 legacy API 거리는 확인 불가로 표시된다.
- 신규 ON1000 → OFF1500 → 재전송 / OFF-first → ON을 격리된 fixture로 실행해
  한 Trip/500m/완료 상태와 주소 보존을 확인한다. legacy의 같은 운행 키는 17003/409로 막혀야 한다.
- 잘못된 신규 거리 SQL은 MySQL check 3819로 차단한다. Spring이 이를
  UncategorizedSQLException으로 분류할 수 있어 모든 제약 위반이 같은 예외라고 가정하지 않는다.

## 거부와 복구

- 17002/409: 같은 운행·종류의 다른 계기값/좌표/시각. 무한 재시도하지 말고 장치 관측을 검토한다.
- 17003/409: legacy 또는 모호한 여러 행. 원본/장치/회사 소유권을 확인하고 별도 승인된
  변환 절차를 설계한다. 마지막 행 선택, 기존 행 삭제, identity 임의 채우기를 하지 않는다.
- 10000/400: 해석된 운행의 시간 순서/계기값/좌표가 잘못됨. 문자열 파싱 전체와 clock skew
  정책까지 통합한 수집 validator는 아직 별도다.
- 거부는 Power/Vehicle/Trip core를 rollback한다. 거부 payload는 DB에 자동 보관하지 않는다.
  credential/원시 위치를 임의 로그로 추가해 해결하지 않는다.
- 구버전 앱 rollback은 새 거리 필드를 읽지 못해 0 또는 잘못된 거리 표시를 만들 수 있다.
  안전한 읽기 버전과 수집 정지를 먼저 확보한다. 신규 컬럼 drop/legacy 값 덮어쓰기는 rollback 절차가 아니다.

원천 보정 후 통계 자동 backfill은 아직 없다. [V2 통계 보정 절차](statistics-formula-v2.md)를 별도로 따른다.
