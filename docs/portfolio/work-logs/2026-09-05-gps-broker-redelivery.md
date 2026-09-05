# CHANGE-021: DB commit과 RabbitMQ ack 사이의 재전달 검증

## 메타데이터

- 날짜: 2026-09-05
- 작업: 개인 현대화, AI 구현·검증 보조
- 브랜치/기준: `codex/portfolio-foundation`, `42c406d`
- 관련 항목: P1-01B, CHANGE-020
- 상태: Verified (아래 한정된 재전달 경계)

## 1. 문제와 근거

CHANGE-020은 실제 MySQL에서 중복·동시 입력을 검증했지만 broker가 메시지를 재전달하는 경계는 포함하지 않았다. DB transaction과 RabbitMQ ack는 별개이므로 DB에 저장되어도 ack가 누락되면 같은 메시지가 다시 처리될 수 있다.

기존 팀의 수집·저장 구조를 유지하고 개인 현대화로 실패 주입 테스트만 추가했다. 기존 개인 기여는 `../original-contributions.md` 기준이며, 팀 메시징 구현 전체를 개인 성과로 표현하지 않는다.

## 2. Acceptance criteria

- [x] 실제 RabbitMQ에서 최초 delivery는 redelivered=false다.
- [x] 저장 전 연결 종료 시 DB 0행에서 재전달 처리 후 1행이 된다.
- [x] 저장 후 ack 전 연결 종료 시 이미 1행이며 재처리 후에도 1행이다.
- [x] 재전달의 redelivered=true 및 JSON payload 동일성을 확인한다.
- [x] ack 후 연결을 다시 열어 큐가 비었음을 확인한다.
- [x] 전체 회귀 테스트 결과를 기록한다.

## 3. 선택지와 결정

- mock broker: 빠르지만 실제 unacked 메시지 재전달을 증명하지 못한다.
- 실제 broker + manual-ack harness: DB commit 직후에 정확하게 연결을 닫을 수 있다. 대신 Spring listener container 자체의 ack/retry는 검증하지 않는다. 이번 범위로 선택했다.
- 별도 JVM consumer 강제 종료: 운영 경로에 더 가깝지만 프로세스 장애 주입과 commit 동기화 장치가 추가로 필요하다. 후속 검증으로 남긴다.

프로덕션 변경 없이 실제 broker와 실제 저장 service를 연결하는 작은 회귀 안전망을 선택했다.

## 4. 구현과 실행 흐름

변경 파일: `MySqlMigrationIntegrationTest`에 RabbitMQ 컨테이너와 2개 parameterized case 추가.

1. Flyway V1~V3가 적용된 MySQL에 회사·차량·Emulator fixture를 저장한다.
2. 프로덕션 exchange/queue/routing-key와 같은 durable direct topology를 별도 테스트 broker에 만든다.
3. JSON 요청 publish 후 테스트 publisher confirm으로 broker 수신을 기다린다.
4. `basicGet(autoAck=false)`로 수신한다. case에 따라 실제 `GpsLogSaveService`를 실행하거나 실행하지 않는다.
5. service 호출 반환 뒤 별도 JDBC 조회로 committed 행 수를 확인하고 ack 없이 연결을 닫는다.
6. 새 연결로 동일 메시지의 재전달을 확인하고 실제 service를 실행한다. repository transaction 내 unique key가 중복을 no-op 처리한다.
7. DB 1행 확인 후 ack한다. 같은 channel의 동기 응답을 기다린 뒤 연결을 다시 열어 큐가 비었는지 확인한다.

Spring context는 기존 direct mode 그대로다. 별도 broker의 수동 소비 harness가 실제 저장 service를 호출한다. 임의 고정 sleep 대신 최대 5초의 조건 대기를 사용하며 automatic connection recovery는 비활성화했다. DB test method를 transaction으로 감싸지 않아 저장 후 미commit 상태를 성공으로 오인하지 않는다.

## 5. 검증 결과

- 좁은 검증: `./gradlew test --tests org.thisway.support.config.MySqlMigrationIntegrationTest --console=plain` — 11/11 성공, 17초.
- 환경: MySQL `8.0.40`, RabbitMQ `3.13.7-alpine`, Docker Testcontainers. 실제 운영 인프라는 사용하지 않는다.
- 전체 검증: `./gradlew test --console=plain` — 279/279 성공, failure/error/skipped 모두 0, 47초. `build/test-results/test/TEST-*.xml` 집계로 확인했다. `git diff --check` 통과.
- Before: DB 차원의 재호출 증거만 존재했다.
- After: 실제 broker 재전달과 DB 최종 1행을 두 장애 시점에서 검증했다. 성능 향상 수치는 측정하지 않았다.

## 6. 한계와 후속 작업

- 정상적인 channel/connection close로 ack 누락을 주입했다. JVM kill, 네트워크 단절, broker 재시작·디스크 durability는 검증하지 않았다.
- Spring `@RabbitListener`, 메시지 converter, retry advice, recoverer, DLQ/replay를 통과하는 테스트가 아니다.
- 테스트 publisher의 confirm 사용은 프로덕션 producer에 confirm을 구현했다는 의미가 아니다.
- exact observation identity의 한계, legacy NULL key, 장치 ID/sequence, tenant/device 인증, SSE 중복은 CHANGE-020과 동일하게 남아 있다. fixture 회사 하나로 tenant 격리를 추가 입증하지 않는다.
- production 코드 및 FE/Emulator 계약 변경 없음. 전체 시스템 exactly-once 또는 무유실을 주장할 수 없다.
- 다음 단계: non-retryable/retryable 분류와 DLQ·replay·관측·runbook을 묶어 검증한다.

## 7. 학습 기록

- delivery tag는 channel 범위다. 재연결 후에는 새 delivery tag로 ack해야 한다.
- DB commit과 ack는 원자적이지 않다. ack를 먼저 하면 저장 실패 시 유실될 수 있고, 저장을 먼저 하면 ack 누락 시 중복될 수 있다.
- 이번 선택은 저장 먼저 + DB 멱등 처리다. 중복 실행 자체가 아니라 중복 저장 효과를 제한한다.
- 실습: 테스트의 `basicAck`를 제거하면 마지막 큐 검증이 왜 실패하는지 설명해 보기. unique key를 제거했을 때 저장 후 장애 case의 최종 행 수를 예상해 보기(공유 DB에서 제약을 제거하지 말 것).

## 8. 예상 면접 질문

1. DB 저장에 성공했는데 왜 다시 메시지가 오나요?
   - broker는 DB commit을 모른다. ack되지 않은 delivery는 연결 종료 시 재전달될 수 있다.
2. exactly-once를 구현한 건가요?
   - 아니다. at-least-once 재전달 아래 신규 동일 normalized observation의 DB 저장 효과를 unique key로 제한했다. 외부 부수 효과와 legacy 데이터까지 보장하지 않는다.
3. 이 테스트가 실제 consumer 장애 복구를 모두 증명하나요?
   - 아니다. 실제 broker/DB와 저장 service를 사용하지만 manual-ack harness다. managed listener retry/DLQ 및 JVM 장애는 별도 검증해야 한다.
4. ack 이후 queue 조회만 하면 충분한가요?
   - ready 메시지가 0이어도 unacked delivery가 있을 수 있다. 연결 종료 뒤 새 연결에서도 메시지가 없는지 확인했다.

## 9. AI 활용과 사람의 검증

- AI가 장애 경계, parameterized test, 문서 초안을 작성하고 명령을 실행했다.
- mock만 사용하는 대안은 broker 재전달 증거가 없어서 채택하지 않았다. 프로세스 kill 테스트는 이번 범위에서 보류했다.
- 자동화 검증은 실제 컨테이너와 상태 assertion이다. 사람이 독립적으로 재현·설명했는지는 아직 확인하지 않았다.
- 사용자가 확인할 사항: 저장 먼저/ack 나중 순서의 선택 이유, duplicate no-op의 한계, harness와 실제 listener의 차이.
