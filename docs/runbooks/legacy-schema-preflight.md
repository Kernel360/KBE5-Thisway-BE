# 기존 DB schema 읽기 전용 preflight

## 목적과 실행 조건

이 점검은 알려진 과거 schema 차이를 찾는 첫 단계다. baseline version이나 운영 전환 가능 여부를 자동 결정하지 않는다. 대상 DB와 backup/복원본을 먼저 식별하고 metadata 조회가 가능한 읽기 전용 계정으로 실행한다. 실제 대상 DB는 이번 작업에서 조사하지 않았다.

SQL: `src/main/resources/db/preflight/schema-readiness.sql`. Flyway migration 경로 밖에 있어 application 시작 시 실행되지 않는다. SELECT와 information_schema만 사용하며 업무 row·credential을 출력하지 않는다. 접속할 DB를 반드시 선택해야 한다. 아래는 절차 예시이고 실제 계정·DB 이름을 확정한 후 사용한다.

```sh
mysql --login-path=thisway-readonly --database=검토한_DB명 < src/main/resources/db/preflight/schema-readiness.sql
```

비밀번호를 명령행·URL에 넣지 않는다. 출력과 서버 버전, lower_case_table_names, 대상 식별자를 접근이 제한된 작업 기록에 보관한다. 외부 DB에 연결한 상태에서 자동 보정 SQL을 실행하지 않는다.

## 결과 해석

| Finding | 해석과 다음 행동 |
| --- | --- |
| LEGACY_TYPO | geofence_log에 occured_time만 존재. 복원본에서 column rename과 JDBC 쓰기를 검토한다. |
| STOP_AMBIGUOUS_COLUMNS | 두 시간 컬럼이 공존한다. 값과 null 분포·생성 경위를 확인하기 전 rename/drop하지 않는다. |
| MISSING_COLUMN / MISSING_TABLE | 확인 대상 컬럼·테이블이 없거나 계정에서 보이지 않는다. 권한과 schema를 확인한다. |
| MISSING_OR_DIFFERENT_INDEX | trip_log의 지정 인덱스 첫 컬럼이 vehicle_id인지 확인되지 않았다. SHOW INDEX로 전체 정의를 검토한다. |
| LEGACY_WRONG_TABLE | 과거 trip 인덱스가 power_log에 존재한다. 필요성과 중복 인덱스를 확인한다. |
| MISSING_BATCH_TABLES | 대문자 BATCH_* 9개 집합이 충족되지 않았다. 서버 대소문자 정책과 Batch 버전을 확인한다. |
| NO_HISTORY | 기본 Flyway history table이 없다. 자동 baseline 근거가 아니다. |
| HISTORY_REQUIRES_REVIEW | history table 존재만 확인했다. version/checksum/success 및 custom table 설정은 별도 검토한다. |
| CHECKED | 해당 제한된 검사만 통과했다. 전체 schema 일치·데이터 무결성은 보장하지 않는다. |

이 SQL은 모든 column/type/default/FK/index/charset/collation을 비교하지 않는다. migration checksum 검증도 아니다. CHECKED만 나오더라도 ADR-001의 SHOW CREATE TABLE 전체 비교, backup 복원, data 검증이 필요하다. metadata가 보이지 않는 계정은 false-negative를 낼 수 있다.

## 로컬 재현

`./gradlew test --tests '*LegacySchemaPreflightIntegrationTest' --console=plain`

Testcontainers가 실제 MySQL 8.0.40에 과거 SQL과 fresh migration DB를 각각 만든다. 과거 DB에 fixture 회사 row를 넣고 알려진 차이 검출·점검 전후 row 보존·table 수를 확인한다. ambiguous column도 재현한다. root 사용과 CREATE DATABASE/ALTER는 이 폐기 가능한 테스트 컨테이너에만 한정하며 preflight SQL에는 포함하지 않는다.

실제 기존 데이터의 업그레이드 SQL·baseline/repair는 아직 없다. 읽기 전용 점검 결과가 확보되면 복원본 전환 작업을 별도 변경으로 만든다. [ADR-001](../adr/001-flyway-fresh-schema.md)의 전환 경계를 따른다.
