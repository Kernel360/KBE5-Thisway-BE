# CHANGE-000: 포트폴리오 현대화 기반 수립

## 메타데이터

- 날짜: 2026-09-05 KST
- 작업자: Shin Dong Jun + AI assistant
- 브랜치/기준 커밋: `codex/portfolio-foundation`, base `develop@98bff23`
- 관련 issue/PR: 없음
- 상태: Verified

## 1. 문제와 근거

- 공개 README는 대용량·정합성·안정 운영을 지향하지만 실행 결과와 장애 검증 문서가 없다.
- 구현과 README 사이에 RabbitMQ 적용 범위, 통계 단위, Batch 종류, package 구조 차이가 있다.
- 기본 test는 Redis 외부 상태에 의존해 red이고 핵심 TripLog/Security test 13개가 skipped다.
- 팀 프로젝트 전체 기능과 사용자 원 기여를 구분한 문서가 없다.
- 이후 모든 변경에서 설계 이유·검증·학습·면접 질문·AI 활용을 남길 공통 규칙이 없었다.

원래 팀 구현과 기존 개인 기여의 경계는 [`../original-contributions.md`](../original-contributions.md)에 기록했다. 이번 변경은 애플리케이션 기능을 수정하지 않고 현대화 작업 규칙과 기준선만 추가한다.

## 2. Acceptance criteria

- [x] 실제 README, code, configuration, Git/PR 이력을 근거로 기준선을 작성한다.
- [x] 전체 test를 한 번 실행하고 성공·실패·skipped를 그대로 기록한다.
- [x] 포트폴리오 사용 가능 여부와 AI 우선 여부를 결정한다.
- [x] 기존 개인·팀·향후 개인 기여를 분리한다.
- [x] 모든 후속 변경에 적용할 `AGENTS.md`, project skill, change template을 만든다.
- [x] 학습 지도, 예상 면접 질문, AI 협업 정책을 만든다.
- [x] 원격 저장소나 제품 코드를 변경하지 않는다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| AI 기능부터 추가 | 눈에 띄는 demo를 빨리 만들 수 있음 | 데이터·평가·운영 기반 없이 gimmick이 될 가능성이 큼 | 보류 |
| CRUD와 UI 정리 | 빠르고 위험이 작음 | 기존 프로젝트와 차별화가 약함 | 보조 범위 |
| telemetry·Batch 신뢰성 우선 | Java/Spring, DB, messaging, transaction, 운영 역량을 함께 증명 | 실패 test·infra·측정까지 필요해 시간이 듦 | 채택 |

선택 이유: 사용자의 기존 Vehicle·Statistics 기여와 자연스럽게 연결되며, 현재 코드의 가장 큰 위험을 해결하면서 백엔드 면접에서 깊게 설명할 수 있다.

## 4. 구현과 실행 흐름

추가 문서:

- `AGENTS.md`: 작업 안전·설계·테스트·문서 계약
- `SKILLS.md`: 사람용 skill index
- `.agents/skills/thisway-portfolio-modernization/`: 실행 가능한 Codex skill과 template
- `docs/portfolio/baseline-audit.md`: 현재 코드·README·test 기준선
- `docs/portfolio/original-contributions.md`: 개인·팀 기여 경계
- `docs/portfolio/modernization-roadmap.md`: P0부터 선택적 AI까지의 순서
- `docs/study/backend-topic-map.md`: 구현과 연결된 학습 과제
- `docs/interview/question-bank.md`: 질문과 답변 checkpoint
- `docs/ai/usage-policy.md`: AI 협업·제품 기능 gate

제품 request/event 실행 흐름은 바뀌지 않았다. 현재 GPS, power/Trip, Statistics 흐름은 기준선 감사 문서에 기록했다.

## 5. 검증 결과

| 명령/실험 | 결과 | 증거 위치 |
| --- | --- | --- |
| `git status --short --branch` | 분석 시작 시 `develop...origin/develop`, clean | 이 표에 결과 전사; 원문 터미널 출력은 미보존 |
| `git rev-list --left-right --count origin/main...origin/develop` | `0 707` | 기준 commit과 재현 명령을 이 문서에 보존 |
| `./gradlew test --console=plain` | FAILED, 178 total / 161 passed / 4 failed / 13 skipped | 결과를 이 표와 [`baseline-audit.md`](../baseline-audit.md)에 전사; HTML report는 `build/` 아래의 로컬 전용 ignored artifact |
| Git/PR author 분석 | merged PR 17개, Vehicle 14개, Statistics 3개 | [`original-contributions.md`](../original-contributions.md)의 PR 링크 |
| project skill 형식 검증 | frontmatter와 placeholder 규칙 통과 | 아래 검증기 환경 한계 참고 |

- Before: 공통 작업 규칙, 검증 template, 기여 경계, 기준선, 학습·면접·AI 정책 문서가 없었다.
- After: 후속 변경을 동일한 증거 구조로 남길 수 있는 문서 기반을 만들었다.
- 실행하지 못한 검증: fresh MySQL/RabbitMQ/application/k6. 이번 변경의 범위가 정적 감사와 문서 기반 수립이므로 제품 동작 검증으로 표현하지 않는다.
- 공식 `quick_validate.py`는 system Python과 번들 Python 모두 `PyYAML` 미설치로 실행되지 않았다. 패키지를 설치하지 않고 Ruby 표준 YAML parser로 동일 검증기의 frontmatter key, name pattern/length, description, TODO placeholder 규칙을 대조해 통과했다.

## 6. 실패 사례와 남은 위험

- 기본 Gradle test가 Redis 미기동으로 4개 실패한다.
- fresh DB schema, geofence insert, Batch metadata, SSE prefix collision은 코드 근거가 있으나 runtime 재현이 남았다.
- 원격 개인 fork와 공개 기본 브랜치 전략은 아직 만들지 않았다.
- 문서가 생겼을 뿐 P0 제품 위험은 아직 해결되지 않았다.

후속 작업은 roadmap의 P0-01 재현 가능한 test부터 작은 change로 진행한다.

## 7. 학습 기록

- 공부할 개념: test isolation, Testcontainers, IDOR, idempotent consumer, Batch restart, Flyway
- 코드에서 확인할 위치: Redis test/config, Security policy, RabbitMQ config, StatisticBatchConfig, init SQL
- 스스로 설명할 질문: “왜 테스트를 먼저 green으로 만들어야 이후 보안·migration 변경을 믿을 수 있는가?”

## 8. 예상 면접 질문

1. AI 기능보다 telemetry 신뢰성을 먼저 선택한 이유는?
   - 답변 핵심: 채용 직무 적합성, 현재 위험, 검증 가능한 backend depth, AI data/eval gate
2. 팀 프로젝트에서 개인 기여를 어떻게 증명했는가?
   - 답변 핵심: author-filtered PR, commit/blame은 보조 자료, 실제 설계·test·후속 개인 diff
3. 테스트 178개가 있는데 왜 baseline을 red라고 평가했는가?
   - 답변 핵심: 4 failure, 13 skipped, 외부 Redis 의존, 핵심 failure path 부재, 숫자와 신뢰도의 차이

## 9. AI 활용과 사람의 검증

- AI에게 맡긴 범위: repository 탐색, README-구현 대조, risk 후보와 문서 초안
- AI가 제안한 대안: AI-first, CRUD 정리, backend reliability-first
- 채택/거절과 이유: telemetry·Batch reliability-first를 채택하고 AI-first는 기반·평가 부재로 보류
- 사용자가 후속 학습에서 직접 재현할 실행 흐름: GPS direct/MQ 분기, power에서 Trip 생성, 일일 통계 집계
- 자동화 검증: Gradle 전체 test 1회와 Git 비교 명령
- AI가 확인하지 못한 사항: 실제 infra 기동, 성능, 운영 AWS 상태, Emulator protocol의 `sum` 의미
