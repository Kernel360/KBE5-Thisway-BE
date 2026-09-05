# Thisway Backend Portfolio Modernization Guide

## 목적과 범위

이 저장소는 Kernel360 5인 팀 프로젝트에서 출발한 차량 관제 백엔드다. 현재 작업의 목표는 기능 수를 늘리는 것이 아니라, Java·Spring 신입 백엔드 포트폴리오로서 다음 역량을 재현 가능한 증거로 보여 주는 것이다.

- 멀티테넌트 권한 경계
- 차량 텔레메트리의 중복·지연·실패 처리
- 재시작 가능한 통계 배치
- MySQL 스키마와 트랜잭션 정합성
- 측정 가능한 성능과 운영 관측성

프론트엔드와 Emulator는 별도 저장소다. 백엔드 계약을 바꾸는 작업은 두 저장소에 미치는 영향과 후속 작업을 문서에 적되, 해당 저장소를 실제로 확인하기 전에는 호환된다고 단정하지 않는다.

## 출처와 소유권

- 원 팀 프로젝트 전체를 개인 구현으로 표현하지 않는다.
- 기존 개인 기여, 기존 팀 기여, 이후 개인 현대화를 명시적으로 구분한다.
- 개인 기여의 기준 자료는 Git/PR 이력이며, 현재 정리는 `docs/portfolio/original-contributions.md`에 있다.
- 향후 성과 문구는 코드가 아니라 통과한 테스트, 재현 명령, 실험 결과로 뒷받침한다.
- AI가 제안하거나 초안을 만든 부분도 최종 설계 판단과 검증 책임은 작업자에게 있다.

## 작업 전 확인

1. `git status --short --branch`로 브랜치와 사용자 변경을 확인한다.
2. 원격 변경이 필요할 때만 `git fetch --prune origin`을 수행한다.
3. dirty worktree의 기존 변경은 사용자 소유로 간주하고 덮어쓰거나 되돌리지 않는다.
4. 기준선, 관련 roadmap 항목, 기존 work log를 읽는다.
5. 공개 원격 push, remote merge, 기본 브랜치 변경, 브랜치 삭제는 사용자 승인 없이 하지 않는다.

현재 분석 기준선은 `develop@98bff23`이다. 최신 상태는 작업 시 다시 확인하고, 기준선이 바뀌면 감사 문서도 갱신한다.

## 구현 원칙

### 보안과 멀티테넌시

- 역할 검사만으로 인가가 끝나지 않는다. 모든 ID 기반 접근은 repository query부터 `tenantId + resourceId`로 제한하고 다른 tenant에 대한 negative test를 둔다.
- `companyId` 같은 tenant 식별자를 변경 가능한 요청값에서 신뢰하지 않는다. 인증 principal에서 얻고 실제 resource ownership을 다시 확인한다.
- password, JWT, 인증 코드, 장치 credential, 원시 위치 정보는 로그에 남기지 않는다. 마스킹보다 수집 최소화를 우선한다.
- Actuator와 운영 관리 API는 필요한 endpoint만 최소 권한으로 노출한다.
- 텔레메트리 수집은 device authentication, timestamp/nonce, replay 방지, payload size/rate limit을 명시적으로 설계한다.

### 텔레메트리와 메시징

- RabbitMQ 소비는 at-least-once delivery를 전제로 한다. 이벤트 식별자, DB unique constraint, idempotent consumer가 함께 있어야 한다.
- retryable과 non-retryable 오류를 구분하고, DLQ·재처리·관측 지표·runbook을 하나의 기능 단위로 다룬다.
- 중복, 순서 역전, 지연 도착, 일부 publish 실패, consumer 재시작을 테스트한다.
- 이벤트 시간과 처리 시간을 구분하며 서버의 현재 시각으로 원본 이벤트 순서를 덮지 않는다.
- ON/OFF 기반 Trip은 명시적 상태 전이와 불변식으로 표현한다. 외부 API 장애가 핵심 운행 기록 commit을 장시간 붙잡지 않게 한다.

### 데이터베이스와 배치

- 운영 스키마는 Flyway migration을 단일 진실 공급원으로 삼고 `ddl-auto=validate`를 목표로 한다.
- MySQL 고유 동작을 사용하는 query와 migration은 H2만으로 검증하지 않는다. Testcontainers MySQL 테스트를 둔다.
- 애플리케이션의 선조회는 unique constraint를 대체하지 않는다. 동시성 불변식은 DB 제약과 충돌 처리까지 포함한다.
- Spring Batch는 식별 가능한 `JobParameter`, 실패 상태, restart/backfill, 중복 실행 방지, 회사별 transaction 경계를 검증한다.
- 통계 공식은 누락 GPS, 수집 주기, 정차, late event의 영향을 문서화하고 예제로 검증한다.

### 구조와 코드

- Java 21과 현재 Spring Boot 기반을 유지하되, 의존성 업그레이드는 별도 변경으로 분리한다.
- Controller는 HTTP 변환, application service는 use case와 transaction, domain은 불변식, infrastructure는 외부 I/O를 담당한다.
- domain이 HTTP DTO나 infrastructure 구현을 직접 참조하는 의존은 새로 만들지 않는다.
- 순환 의존을 `@Lazy`로 해결하지 않는다. 명시적 port 또는 orchestration service로 책임을 분리한다.
- 외부 HTTP client에는 timeout과 실패 정책을 두고 DB transaction 범위를 불필요하게 늘리지 않는다.

## 테스트와 검증

최소 검증 명령은 다음과 같다.

```bash
./gradlew test --console=plain
```

기준선에서는 Redis가 실행되지 않은 환경에서 178개 중 4개가 실패했다. 이 결과를 green으로 오인하지 않는다. 변경 유형별로 다음 증거를 추가한다.

- API·인가: 허용 테스트와 role/tenant 조합별 거부 테스트
- DB·migration: 빈 MySQL에서 migration 후 repository/integration test
- RabbitMQ: publish/consume, duplicate, retry, DLQ, replay test
- Batch: 성공, 부분 실패, restart, 동일 날짜 재실행, 동시 실행 test
- 성능: 동일 환경·데이터·시나리오의 before/after와 p50/p95/p99, 처리량, 오류율, queue lag
- AI: baseline 비교, 고정 evaluation set, tenant 격리, 근거성, 실패·fallback, latency·비용

`@Disabled`로 핵심 실패를 숨기거나 mock 호출 여부만으로 실제 저장소 계약을 검증했다고 표현하지 않는다.

## 문서 계약

모든 의미 있는 변경은 `docs/portfolio/work-logs/`에 작업 기록을 하나 남기거나 기존 기록을 갱신한다. `.agents/skills/thisway-portfolio-modernization/assets/change-record-template.md`를 사용하며 최소한 다음 내용을 포함한다.

- 변경 전 문제와 코드·실행 근거
- acceptance criteria
- 고려한 선택지와 장단점
- 선택 이유와 실행 흐름
- 실제 검증 명령과 결과
- 측정한 before/after 또는 아직 측정하지 못한 한계
- 실패 사례와 남은 위험
- 공부할 개념
- 예상 면접 질문과 답변 핵심
- AI의 역할, 사람이 채택·거절한 판단, 검증 방법

새 아키텍처 선택은 `docs/adr/`, 성능·AI 평가는 `docs/experiments/`, 운영 절차는 `docs/runbooks/`에 별도 문서가 필요할 때만 추가한다. 빈 폴더나 내용 없는 문서는 만들지 않는다.

## 완료 기준

작업은 다음이 모두 충족되어야 완료다.

- 요구사항과 실패 조건이 테스트로 표현됐다.
- 관련 전체 테스트 결과를 사실대로 기록했다.
- 보안·tenant·중복·transaction 경계를 검토했다.
- README나 포트폴리오 문구가 구현보다 앞서지 않는다.
- work log의 구현, 검증, 학습, 면접, AI 사용 항목을 갱신했다.
- 원래 팀 기여와 이번 개인 변경의 경계가 명확하다.
