# CHANGE-018: 기존 schema 읽기 전용 점검

- 날짜: 2026-09-05
- 작업자: Shin Dong Jun + AI assistant
- 기준: `codex/portfolio-foundation@a31001c`
- 범위: P0-03B 전환 사전 점검 도구. 실제 기존 DB 전환은 미수행.

## 문제·출처

V1/V2가 fresh MySQL을 만들지만 기존 update 기반 DB는 상태가 제각각일 수 있다. 알려진 과거 차이조차 확인하지 않고 baseline하면 호환되지 않는 schema를 정상으로 처리할 수 있다. 과거 팀 SQL을 그대로 fixture로 실행하고 metadata의 차이를 검출하는 개인 검증을 추가했다. 실제 운영 DB를 이 과거 fixture와 동일하다고 가정하지 않는다.

## Acceptance criteria

- [x] 과거 시간 오타, statistics/Batch 누락, 잘못된 trip index, history 부재 검출.
- [x] 점검 전후 fixture row와 table 수 보존.
- [x] 두 시간 컬럼 공존은 STOP으로 구분.
- [x] fresh migration에서도 history 내용 검토를 요구하며 자동 READY 판정 금지.

## 선택과 흐름

자동 ALTER/baseline 도구 대신 단일 SELECT 기반 known-difference audit를 선택했다. 실제 schema를 모르므로 자동 교정의 전제가 없다. 실행은 metadata 조회 → 항목별 finding → 사람이 전체 DDL/권한/backup/복원본 검토 순서다. migration 폴더 밖에 두어 Boot 시작 시 실행하지 않는다. 외부 state와 업무 row를 변경하지 않으며 transaction/메시징 경계는 변경하지 않았다.

전체 schema diff engine을 주장하지 않는다. index는 지정 이름과 첫 컬럼만, history는 기본 이름의 table 존재만 확인한다. 더 자세한 범위와 명령은 [runbook](../../runbooks/legacy-schema-preflight.md)에 있다.

## 검증과 실패

첫 좁은 테스트는 Docker 엔진이 꺼져 Testcontainers initializationError로 실패했다. Docker Desktop을 기동하고 엔진 28.3.0 준비를 확인한 뒤 재실행했다.

- `./gradlew test --tests '*LegacySchemaPreflightIntegrationTest' --console=plain`: 2/2 통과, Gradle 11초. skip 0.
- `./gradlew test --console=plain`: 252/252 통과, 실패·오류·skip 0, Gradle 42초. XML 집계 및 `git diff --check` 통과.

## 한계와 후속 작업

실제 DB·backup·권한·버전·collation·row 통계는 조사하지 않았다. 빈 값/metadata 권한 부족은 누락으로 표시될 수 있다. raw 업무 데이터와 credential을 출력하지 않는다. 현장 결과가 확보되기 전에는 baseline/repair/clean 또는 교정 DDL을 실행하지 않는다.

## 학습과 면접

1. 왜 CHECKED가 배포 가능을 뜻하지 않는가? 제한된 metadata 항목만 검사했고 업무 데이터/전체 constraint/checksum은 검증하지 않기 때문이다.
2. 두 시간 컬럼이 공존하면 왜 rename하면 안 되는가? 어느 컬럼이 최신·정상인지 확인하지 않고 덮어쓰면 데이터 손실이 생길 수 있다.
3. schema history table이 있으면 충분한가? 실패 migration, 다른 버전, checksum 변경을 추가로 확인해야 한다.
4. 읽기 전용 계정이 필요한 이유는? 점검에 불필요한 DDL/DML 권한을 제거하고 실수 영향 범위를 줄인다. 테스트 root 권한은 폐기 가능한 fixture 전용이다.

실습: legacy와 fresh 결과를 비교해 각 finding의 근거가 되는 information_schema 항목을 설명한다. 사용자 직접 수행은 미확인이다.

## AI 활용과 기여

AI가 점검 SQL·격리 테스트·runbook을 작성하고 검증했다. 원 팀 schema를 보존하고 자동 보정을 채택하지 않았다. 사람의 실제 DB 승인·schema 검토·복원 테스트는 수행하지 않았다. 전환 완료 성과로 표현하지 않는다.
