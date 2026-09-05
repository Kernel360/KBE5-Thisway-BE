# CHANGE-017: Flyway와 실제 MySQL schema 기반

- 날짜: 2026-09-05
- 작업자: Shin Dong Jun + AI assistant
- 기준: `codex/portfolio-foundation@4e602f6`
- 범위: P0-03 빈 MySQL migration 및 저장 계약. 기존 데이터 전환·실제 배포는 미수행.

## 문제와 기여 출처

기존 팀 init SQL의 `geofence_log.occured_time`은 JDBC의 `occurred_time`과 달랐다. `idx_trip_log_vehicle_id`가 power_log에 생성됐으며 statistics 테이블이 없었다. Batch metadata는 별도 SQL이고 dev/prod는 Hibernate update였다.

기존 팀 SQL과 Batch schema를 출처로 V1/V2를 만들었으며 개인 개선은 불일치 교정, migration 실행 경로 통합, 검증·문서화다. 원래 테이블 설계 전체를 개인 기여로 주장하지 않는다.

## Acceptance criteria

- [x] 빈 MySQL 8.0.40에서 V1/V2 적용 후 Boot/Hibernate validate 기동.
- [x] 재실행 시 추가 migration 0건.
- [x] 실제 JDBC geofence insert, GPS bulk insert 및 window-function 최신 GPS 조회.
- [x] MySQL tenant predicate의 자기 회사 허용·다른 회사 거부.
- [x] Statistics JPA 저장·조회.
- [x] Batch Job/Step sequence, metadata, ExecutionContext 기록·조회.
- [x] 임의 column drift 발생 시 Hibernate 검증 실패, 복원 후 검증 성공.
- [x] 이력 없는 비어 있지 않은 schema는 자동 baseline 없이 실패.

## 결정과 실행 흐름

선택지와 기존 DB 전환 경계는 [ADR-001](../../adr/001-flyway-fresh-schema.md)에 기록했다. Boot-managed Flyway core/mysql 의존성을 추가했다. Boot datasource 준비 → Flyway V1 fleet schema/V2 Batch metadata → Hibernate validate → repository 동작 순서다. SQL DDL은 Flyway가 소유하며 Batch initialize-schema=never다.

dev/prod ddl-auto를 validate로 바꾸고 compose의 docker-entrypoint-initdb.d mount를 제거했다. 따라서 새 volume도 기존 fixture seed를 자동 생성하지 않는다. 원본 schema/seed 파일은 보존한다. 기존 volume은 지우거나 수정하지 않았다. 적용 시 기존 DB가 비어 있지 않으면 검토된 baseline 전환이 필요하다.

기존 H2 회귀 테스트는 test resources에서 Flyway를 끄고 create-drop을 유지한다. 새 MySqlMigrationIntegrationTest만 Flyway를 켜고 validate를 사용한다. H2 결과를 MySQL 결과로 주장하지 않는다. 테스트 MySQL은 Testcontainers의 별도 instance/임시 port이고 실행 후 정리된다.

## 검증

- `./gradlew test --tests '*MySqlMigrationIntegrationTest' --console=plain`: 최초 5/5 통과(13초), drift 검증 추가 후 6/6 통과(11초).
- Batch Step context 검증을 추가한 `./gradlew test --console=plain`: 전체 250/250 통과, failures/errors/skipped 0, Gradle 45초. XML 집계 확인 및 `git diff --check` 통과.
- drift test는 테스트 컨테이너의 statistics 컬럼을 rename하여 실제 validator 실패를 확인하고 finally에서 원래 이름으로 복원한다. 데이터는 삭제하지 않는다.
- 실제 DB 저장 결과와 information_schema에서 trip index 소속을 확인한다. migration 두 건의 적용 이력 및 재실행 noop도 검사한다.
- schema 비추적 상태는 별도 history table 이름으로 모사해 non-empty schema 거부를 검사한다. 실제 운영 DB에 baseline을 실행하지 않는다.

## 한계와 실패 경계

기존 dev/prod 데이터의 schema 상태, backup 복원, 운영 MySQL 버전·collation·timezone, 권한 분리, 실제 compose 전체 기동 및 seed는 미검증이다. fresh schema 성공은 기존 데이터 자동 전환 성공이 아니다. Hibernate validate는 모든 index/default/업무 제약을 검증하지 않으며 이번 index는 별도 SQL assertion으로 확인했다.

통계 중복 방지와 GPS idempotency 제약은 별도 P1 작업이다. Batch metadata의 저장 계약을 확인했으며 통계 job 재시작·부분 실패 업무 정책까지 해결한 것은 아니다. 성능 before/after는 측정하지 않았다. MySQL 8.0.40 테스트 성공을 다른 버전 전체에 일반화하지 않는다.

## 학습·면접

1. Hibernate update 대신 validate를 선택한 이유는? schema 변경은 버전 SQL로 기록하고 누락은 시작 실패로 드러내기 위해서다. validate는 migration을 대신하지 않는다.
2. 기존 DB에 baseline-on-migrate를 켜면 왜 위험한가? 실제 schema가 baseline과 같은지 확인하지 않고 migration을 건너뛸 수 있다. 백업·복원본 비교와 교정 계획이 먼저다.
3. H2 테스트가 있는데 MySQL이 필요한 이유는? enum/type, window SQL, Batch sequence와 DDL 차이를 실제 dialect에서 검증해야 한다.
4. Flyway validate와 Hibernate validate는 같은가? 전자는 migration 이력·checksum, 후자는 ORM 매핑과 DB schema를 검사한다. DB index 등은 별도 확인이 필요하다.
5. migration 실패를 transaction rollback하면 되는가? MySQL DDL의 implicit commit 등으로 단순 rollback을 기대하면 안 된다. backup 복원·forward fix 절차가 필요하다.

실습: fixture의 geofence 시간 컬럼을 원래 오타로 되돌리면 어떤 JDBC 테스트가 실패할지 설명한다. 사용자 직접 실행/이해 확인은 아직 하지 않았다.

## AI 활용

AI가 기존 SQL·엔티티·JDBC 차이를 비교하고 migration, Testcontainers 테스트, 문서를 작성·실행했다. 기존 schema/seed 삭제와 자동 baseline은 채택하지 않았다. 버전 SQL과 validate 조합을 선택한 이유는 재현성과 변경 이력이다. 사용자 원 기여 및 기존 팀 코드와 이번 현대화를 구분하며 실제 운영 DB 조사는 수행하지 않았다.
