# CHANGE-022: GPS 오류 분류·DLQ와 제한적 replay 검증

## 메타데이터

- 날짜: 2026-09-05~06
- 작업: 기존 팀 구조 위 개인 현대화, AI 구현·검증 보조
- 브랜치/기준: `codex/portfolio-foundation`, `9e777ac`
- 관련: P1-01C, CHANGE-020~021
- 상태: Verified (로컬 통합 검증, 운영 policy 미적용)

## 1. 문제와 근거

기존 `rabbitListenerContainerFactory`는 모든 예외를 즉시 최대 3회 시도하고 `RejectAndDontRequeueRecoverer`로 끝냈다. source에 DLX 설정이 없어 실패 메시지가 버려질 수 있었고 입력 오류와 일시적 DB 오류를 구분하지 않았다. CHANGE-021은 broker 재전달/DB 멱등성만 확인했으며 managed container retry/DLQ는 포함하지 않았다.

팀의 direct/fanout 수집 구조와 원래 기여는 유지한다. 개인 원래 기여의 근거는 `../original-contributions.md`이고, 이번 오류 정책·테스트·문서가 개인 현대화 범위다.

## 2. Acceptance criteria

- [x] 정상 및 일시 오류 후 성공: 3번째 처리 성공, DB 1행, DLQ 없음.
- [x] invalid input/JSON/무결성/unknown: 1회 후 DLQ, DB 0행.
- [x] 일시 오류 소진: 3회 후 DLQ, payload 보존 및 x-death rejected 확인.
- [x] replay routing 실패 시 원본 보존, publish 이후 DLQ ack 누락·재전달에도 DB 1행.
- [x] rejection counter, source queue arguments 보존 및 저장 전용 factory 연결 확인.
- [x] 전체 회귀와 운영·학습·면접 문서 기록.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점 | 판단 |
| --- | --- | --- | --- |
| 모든 예외 반복 | 간단 | poison message와 DB 자원 낭비 | GPS 저장에서는 제거 |
| DB 일시 오류 allowlist | 제한된 반복, 예측 가능 | 미분류 일시 장애도 수동 조사 필요 | 선택 |
| source x-arguments에 DLX 추가 | 앱만으로 fresh topology 생성 | 기존 큐 재선언 충돌, 삭제 유혹 | 거절 |
| broker policy + durable DLQ | source 삭제 없이 적용 | 별도 운영 배포 gate 필요 | 선택 |
| 자동 DLQ 재순환 | 자동 복구 가능성 | poison loop·장애 증폭 | 거절 |

DLQ 설정은 앱 배포만으로 완료되지 않는다. [runbook](../../runbooks/gps-dlq-replay.md)의 policy·routing 확인이 운영 적용 전제다. 운영 broker는 이번에 수정하지 않았다.

## 4. 구현과 흐름

- `RabbitMQConfig`: DLX/DLQ/binding, GPS 저장 전용 factory, retry allowlist, 200ms·400ms backoff, prefetch=1, rejection counter.
- `SaveGpsLogConsumer`: 전용 factory 선택. 기존 SSE factory 정책은 그대로 유지.
- `MySqlMigrationIntegrationTest`: 실제 broker policy, production factory로 생성한 Spring managed container, 실제 converter/save service/MySQL 조합.
- `RabbitMQConfigTest`: annotation factory 선택 및 source/DLQ arguments를 확인.

source delivery → listener retry advice → JSON 변환·저장 service → repository transaction → 성공 시 AUTO ack. 실패하면 cause chain에서 `TransientDataAccessException`, `RecoverableDataAccessException`, `CannotCreateTransactionException`만 최대 3회 시도한다. 나머지는 1회 후 recoverer에서 counter 증가·reject/no-requeue → broker policy → DLQ로 보낸다.

recoverer와 GPS error handler는 원문 메시지·예외 stack을 로그에 넣지 않는다. counter는 태그 없는 최종 거부 시도 횟수이며 DLQ 보관 성공 횟수가 아니다. 실제 도착은 queue 상태로 별도 확인한다.

## 5. 검증 결과

- 첫 좁은 검증: MySQL integration class 18개 성공, 33초. 이후 unroutable replay 사례를 같은 replay test에 추가했다.
- 최종 좁은 검증: `./gradlew test --tests org.thisway.support.config.MySqlMigrationIntegrationTest --console=plain` — 18/18 성공, 32초. unroutable replay 보존 포함.
- 전체: `./gradlew test --console=plain` — 287/287 성공, failure/error/skipped 0, 1분 5초. `build/test-results/test/TEST-*.xml` 집계로 확인. `git diff --check` 통과. 테스트 실패는 없었다.
- 환경: 실제 Docker MySQL 8.0.40, RabbitMQ 3.13.7-alpine, Spring AMQP 3.2.5 / Spring Retry 2.0.11.
- Before: 분류 없이 3회 반복, DLQ/replay 검증 없음.
- After: permanent 1회 / transient 최대 3회와 broker DLQ 도착·재처리 중복 효과 제한을 실행으로 확인. 처리량·지연 개선 수치는 주장하지 않는다.

## 6. 실패 사례·남은 위험

- policy 누락, DLX 부재·권한·binding 오류이면 보관 실패가 가능하다. source argument를 강제 변경하거나 큐를 삭제하지 않는다.
- classic DLX는 target 장애 시 무유실을 보장하지 않는다. quorum/HA, broker restart, producer dual-publish 부분 성공과 confirm/return은 후속이다.
- replay의 unroutable publish 및 publish/ack 사이 장애는 테스트하지만 운영 replay CLI/API·권한·audit·rate limit은 아직 없다.
- 테스트 container는 production factory와 실제 service를 쓰지만 annotation method adapter 전체를 통과하지 않는다. annotation의 factory 선택은 별도 reflection 회귀 테스트다. DB 일시 장애는 예외 주입이며 실제 DB 중단/deadlock 유발 실험은 아니다.
- device 인증·MDN 재할당, legacy NULL key, SSE 중복/원본 순서 보장, tenant isolation의 추가 증명은 이번 범위 밖이다.
- DLQ 보관에 따른 위치정보 접근권한·보관 기간·용량 경고와 자동 alert는 운영 적용 전 추가 필요하다.
- FE/Emulator payload 및 HTTP 계약 변경 없음. 양쪽 저장소 새 실행 검증은 하지 않았다.

## 7. 학습 포인트

- retry advice와 repository transaction 범위: 실패 transaction을 그대로 다시 사용하지 않고 다음 호출에서 새 transaction을 시작한다.
- `ListenerExecutionFailedException` wrapper 때문에 cause chain 분류가 필요하다.
- publisher confirm과 mandatory return은 다른 질문이다: broker 수신 여부와 실제 routing 여부. 둘 다 확인해도 DB commit 보장은 아니다.
- DLQ도 장애가 발생할 수 있는 queue다. 보관·관측·권한·재처리 절차가 함께 있어야 한다.
- 실습: routing key를 틀리게 하면 confirm만 성공할 수 있는 이유와 DLQ ack를 보류해야 하는 이유를 설명한다. prefetch를 크게 늘렸을 때 backoff 중 다른 consumer에 미치는 영향을 예상한다.

## 8. 예상 면접 질문

1. 왜 모든 예외를 retry하지 않나요?
   - 잘못된 JSON/FK/입력/코드 오류는 같은 데이터로 즉시 반복해도 해결되지 않는다. DB 일시 오류만 제한적으로 반복하고 나머지는 격리·조사한다.
2. 기존 queue에 DLX arguments를 추가하면 안 되나요?
   - 기존 선언과 불일치할 수 있다. 삭제가 아닌 broker policy로 적용하고 기존 policy 우선순위·권한·routing을 점검한다.
3. replay 성공 뒤 원본을 언제 ack하나요?
   - mandatory return 없음과 positive confirm을 확인한 뒤 ack한다. 그 사이 장애는 중복을 만들 수 있으므로 저장 멱등성이 여전히 필요하다.
4. DLQ로 보냈으니 무유실인가요?
   - 아니다. policy 누락·target 장애·classic dead lettering과 producer 일부 publish 실패 등 별도 경계가 남는다.
5. 테스트가 실제로 검증한 범위는 무엇인가요?
   - 실제 Spring container retry와 broker policy/DLQ, 실제 MySQL 저장을 연결했다. 장애 예외는 주입했고 annotation adapter·운영 broker 장애·운영 replay 도구는 아직 검증하지 않았다.

## 9. AI 활용과 사람의 검증

- AI가 현행 코드 분석, 정책·대안 제시, 구현·실패 주입 테스트와 문서 초안·실행을 수행했다.
- 큐 args 변경 및 자동 DLQ loop 제안은 기존 메시지 보존·장애 증폭 위험 때문에 채택하지 않았다.
- 자동 검증 결과와 공개 참고 자료를 기록한다. 사용자가 독립적으로 실행·설명했는지는 아직 확인하지 않았다.
- 사람이 확인할 핵심: 배포 전 policy gate, confirm/return/DB commit 차이, source와 DLQ의 서로 다른 ack 시점.
- 공식 자료: [Spring AMQP recovery](https://docs.spring.io/spring-amqp/reference/amqp/resilience-recovering-from-errors-and-broker-failures.html), [RabbitMQ DLX](https://www.rabbitmq.com/docs/dlx). 최신 문서와 현재 라이브러리 버전 차이는 실제 테스트로 보완했다.
