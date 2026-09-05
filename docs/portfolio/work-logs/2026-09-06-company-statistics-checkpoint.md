# CHANGE-025: 회사별 통계 checkpoint와 DB 일자 중복 방지

## 메타데이터

- 날짜: 2026-09-06
- 기준: `codex/statistics-batch-restart@bc20e7d`
- 상태: Verified
- 원래 팀 서비스/개인 Statistics·Batch 기여를 기반으로 한 개인 현대화. 원 기여는 original-contributions.md 기준.

## 문제와 acceptance criteria

CHANGE-024는 두 번째 회사 실패 시 첫 회사도 rollback하고 재계산했다.
선조회 후 insert만으로는 직접 저장 동시성도 보호할 수 없었다.

- [x] 같은 JobInstance restart에서 성공 회사 계산 생략
- [x] 통계와 완료 marker의 원자 commit, marker 저장 실패 시 rollback
- [x] 직접 저장 동시성 및 DB calendar-date unique
- [x] 기존 중복 데이터는 migration 실패 후 보존
- [x] 전체 회귀와 기록 완료

## 선택과 실행 흐름

[ADR-005](../../adr/005-company-statistics-checkpoint.md)에 대안과 비용을 기록했다.
Tasklet → 별도 worker proxy → 새 transaction → 회사 lock → marker 검사 → 기존 계산/저장
→ marker insert → 회사 commit. 오류가 나면 해당 회사만 rollback하고 Job은 FAILED.
재시작은 앞 회사 marker를 확인하고 실패 회사부터 실제 계산한다.
회사 row lock을 직접 저장 경로에도 적용하고 DB unique를 최종 방어선으로 둔다.

## 검증

- 첫 좁은 테스트: `./gradlew test --tests '*StatisticsBatchIntegrationTest' --console=plain`, 3개 통과, 15초.
- 전체: `./gradlew test --console=plain`, 300개 통과, 실패/오류/skipped 0, 1분 9초.
- Before: 성공 회사도 다시 계산. After: 같은 instance marker가 있는 회사는 계산 1회 유지.
- 운영 DB, 다중 JVM crash recovery, 성능 향상은 측정하지 않았다.

## 위험과 학습

- 회사 목록은 매 실행 시 active 목록으로 다시 조회한다. 최초 목록 동결/역사적 fleet snapshot 아님.
- 같은 회사 다른 날짜도 lock 경합, 외부 tasklet transaction 때문에 connection pool 여유 필요.
- 명시적 직접 저장은 통계를 재계산하며 Batch 완료 marker를 지우지 않는다.
- MySQL DDL 전체 rollback 불가: legacy 중복은 수동 조사하고 자동 데이터 정리 금지.
- 공부: Spring proxy/self-invocation, REQUIRED/REQUIRES_NEW, DB row lock, generated unique, atomic checkpoint.

## 예상 면접 질문

1. 왜 REQUIRES_NEW만 붙이지 않았나요?
   - 회사 commit과 job metadata 사이 실패 구간이 있어 업무 marker를 결과와 함께 commit한다.
2. 통계가 아직 없는데 무엇을 잠그나요?
   - 항상 존재하는 회사 row. lock 범위가 넓은 비용이 있으나 최초 insert race도 제어한다.
3. 애플리케이션 lock이 있는데 unique가 필요한가요?
   - 다른 writer/미래 코드의 우회에도 DB가 일자 불변식을 보호한다.
4. Job이 FAILED인데 통계가 남아도 되나요?
   - 회사별 성공을 확정하는 정책이다. 실패 상태와 성공 marker를 분리해 재시작한다.

## AI와 검증 책임

AI가 코드/테스트/ADR 초안을 작성하고 실제 MySQL 실행을 수행했다. catch-and-continue 및
선조회만으로 중복 방지하는 대안을 거절했다. 사용자 본인의 설계 이해는 아직 검증하지 않았으며
테스트 2회사 fixture를 직접 실패/재시작해 설명하는 연습이 필요하다. 운영 환경은 AI가 확인하지 않았다.
