# ADR-001: 빈 MySQL의 schema 소유자를 Flyway로 통일

- 날짜: 2026-09-05
- 결정: 새 DB에 적용. 기존 데이터 DB 자동 전환은 승인하지 않음.

## 배경과 선택

기존 compose init SQL, Hibernate update, 별도 Batch schema가 서로 다른 테이블 집합을 만들었다. geofence 시간 컬럼 오타, trip index 대상 오류, statistics 누락을 발견했다. 선택지는 Hibernate update 유지, init SQL 수동 관리, Flyway versioned migration이다. update는 변경 이력과 재현성이 부족하고 init SQL은 이미 만들어진 volume에서 다시 실행되지 않는다.

Flyway V1(fleet/log/statistics)과 V2(Batch metadata)를 유일한 자동 DDL로 채택한다. dev/prod는 Hibernate validate, Batch 자동 초기화는 never다. compose의 SQL mount를 제거해 Flyway보다 먼저 기존 schema/seed가 실행되지 않도록 한다. 원본 SQL은 출처 비교용으로 보존한다.

## 기존 DB 전환 경계

`baseline-on-migrate=false`, `clean-disabled=true`로 설정한다. 이력이 없는 비어 있지 않은 DB에서는 실행을 거부한다. 기존 volume 삭제, Flyway clean, baseline/repair 자동 실행은 하지 않는다. 따라서 기존 dev/prod DB를 그대로 연결하면 시작이 실패할 수 있다. 자동 update로 돌아가는 fallback은 두지 않는다.

기존 데이터 전환 전에는 다음 증거가 필요하다.

1. 대상 DB·MySQL 버전·backup을 확인하고 별도 복원본에서 시작한다.
2. SHOW CREATE TABLE과 information_schema로 column/type/nullability/default/PK/FK/index를 V1/V2와 비교한다. 특히 occured_time, trip index, statistics, BATCH_*의 존재·버전을 확인한다.
3. 차이를 데이터 보존 ALTER/backfill로 교정하는 별도 migration 계획을 작성한다. rename, index 추가, Batch 기존 실행 이력 보존을 각각 검증한다.
4. row count·핵심 값·FK 무결성·Batch context 복원·query 결과를 전후 비교한다.
5. 실제 schema와 일치하는 baseline version을 검토한 후 대상·절차를 승인한다. V1이나 V2를 확인 없이 baseline하면 안 된다. rollback은 MySQL DDL의 transaction rollback에 의존하지 않고 검증된 backup 복원 또는 forward fix로 준비한다.

실제 기존 DB 상태를 조사하지 않았으므로 baseline 명령과 보정 SQL은 이번에 제공하거나 실행하지 않는다. 이는 새 DB용 migration을 기존 DB 업그레이드라고 오인하지 않기 위한 경계다.

## 결과와 비용

빈 MySQL에 같은 SQL을 재현하고 적용 이력을 남긴다. 비어 있지 않은 DB와 schema drift를 명시적으로 드러낸다. 대신 기존 DB의 자동 기동 호환성이 없어지고 별도 전환 작업이 필요하다. 기존 init-db.sql의 demo 데이터는 자동 생성하지 않는다. 관리 계정·seed는 별도 명시적 로컬 절차로 다룬다.

통계 날짜별 unique/upsert, GPS 멱등 key, index 성능 최적화는 후속 단계다. 이번 DDL에 검증되지 않은 업무 제약을 추가하지 않는다.
