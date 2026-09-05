# CHANGE-023: 제한적 DLQ replay 도구와 포트폴리오 설명

## 메타데이터

- 날짜: 2026-09-06
- 작업: 개인 현대화, AI 구현·검증 보조
- 기준: `codex/portfolio-foundation@2049316`
- 관련: P1-01C, CHANGE-022, 사용자의 프로젝트 설명 요청
- 상태: Verified (로컬 도구·통합 검증, 운영 사용 아님)

## 1. 문제와 근거

CHANGE-022에서는 replay protocol을 테스트했으나 사용 가능한 도구가 없었다. 관리 화면에서 임의 ack/delete하면 복구 작업 자체가 원본을 잃게 할 수 있다. 또한 사용자는 이 프로젝트를 무엇으로 소개하고 어떻게 어필할지 요청했다.

원래 5인 팀 프로젝트와 개인 Vehicle/Statistics 기여는 `../original-contributions.md` 기준이다. 이번 도구·검증·소개 문서만 신규 개인 현대화로 분리한다.

## 2. Acceptance criteria

- [x] CLI를 웹 애플리케이션과 분리하고 네트워크 없이 help 실행.
- [x] 한 건만 선택, preview 원문 비출력, 승인 ID/hash·입력·replay marker 검증.
- [x] confirm timeout/unroutable/audit 실패 시 ack 보류.
- [x] 실제 RabbitMQ/MySQL에서 도구의 부분 실패 후 재처리 결과 1행.
- [x] audit 0600 신규 파일만 생성, 기존 파일 보존.
- [x] 전체 회귀·runbook·포트폴리오 설명 및 학습 기록.

## 3. 선택지와 결정

- 웹 관리 API: 사용성이 좋지만 운영 인가·감사·tenant 문제가 추가된다. 이번 범위에서 거절.
- 자동 대량 replay: 장애 증폭과 poison 반복 위험으로 거절.
- 별도 Java CLI: 기존 DTO validator와 Rabbit client를 재사용하고 web jar에서 분리할 수 있어 선택. Java/Gradle 및 POSIX filesystem 의존은 남는다.
- audit append vs 새 파일: 기존 파일 덮어쓰기·혼합을 막기 위해 invocation별 CREATE_NEW·0600을 선택. 중앙 감사 시스템은 아니다.

## 4. 구현과 흐름

- `src/replay/java/org/thisway/ops/GpsDlqReplay.java`, Gradle replay source set/task.
- `GpsDlqReplayTest`, 기존 MySQL integration class에 실제 도구 검증 추가.
- [소개 문서](../project-pitch.md): 서비스 분류/30초 소개/이력서 3개 항목/증거·한계/AI 활용 설명.

도구 실행 → 승인·환경·신규 audit 준비 → DLQ manual get 1건 → body hash/입력/marker 확인 → audit INTENT fsync → 고정 저장 direct exchange에 mandatory publish → positive confirm 및 return 없음 → audit PUBLISH_CONFIRMED fsync → 원본 ack → 동기 channel barrier → ACK_COMPLETED 기록.

preview는 publish/ack 없이 연결을 닫아 원본을 돌려놓는다. 이것은 read-only peek가 아니므로 큐 순서·redelivery 상태를 바꿀 수 있다. replay body는 그대로, headers는 fixed type과 replay marker/approval만 재구성한다. fanout에는 재발행하지 않는다.

## 5. 검증 결과

- 초기 `./gradlew gpsDlqReplay --args=--help --console=plain`: 성공, 2초, broker 연결 없음.
- 초기 narrow `./gradlew test --tests '*GpsDlqReplayTest' --tests '*MySqlMigrationIntegrationTest' --console=plain`: 성공, 33초. 이후 파일 audit 보호 테스트와 loopback 제한 추가.
- 최종 `./gradlew test gpsDlqReplay --args=--help --console=plain`: 294/294 성공, failure/error/skipped 모두 0, 1분 5초. XML 집계로 확인. CLI help도 성공했다.
- `./gradlew bootJar --console=plain`: 성공, 882ms. `git diff --check` 통과. 이번 테스트 실행 실패는 없었다.
- Before: replay test helper와 운영 절차만 존재.
- After: 실제 처리 엔진과 CLI, 부분 실패 회귀·감사 파일 보호를 갖춘 제한적 도구. 운영 사용·처리량 향상을 주장하지 않는다.

## 6. 남은 위험과 실패 경계

- 승인 ID는 외부 승인 기록 참조이지 승인 서버 검증이 아니다. DB/tenant/장치 재할당 확인은 운영자 책임이다.
- loopback/승인된 tunnel만 허용한다. 도구가 tunnel의 실제 대상이나 조직 권한을 인증하지 않는다.
- marker는 조작 가능한 message header다. 전역 반복 제한·rate limit·동시 실행 lock이 아니며 원본 ack 누락 시 원본에는 marker가 없어 다시 실행될 수 있다.
- confirm은 DB commit이 아니다. producer dual-publish 신뢰성은 여전히 별도다.
- audit 실패 후 원본 보존을 검증했으나 ack 후 audit 실패는 이미 ack됐을 수 있다. 불확실한 결과를 자동 재시도하지 않는다.
- 로컬 audit는 변조 방지/중앙 보존이 아니며 credential은 환경 변수로 주입한다. 민감 body는 기록하지 않는다.
- 제한된 CLI 처리 엔진은 실제 broker/DB로 검증했지만 전체 CLI를 운영 URI/tunnel로 실행하거나 JVM kill하지 않았다. 실제 운영 메시지는 읽거나 재발행하지 않았다.
- 기존 classic DLX 무유실 한계, device 인증, legacy NULL key와 MDN 재할당은 남는다.

## 7. 학습 포인트

- consumer ack와 publisher confirm의 다른 역할, mandatory return, 실패 후 결과 불확실성.
- audit를 남기는 시점도 side effect 순서 설계다. 파일 write 성공과 force는 구분한다.
- 안전한 도구는 기능을 제한한다: source/destination 고정, 한 건 처리, payload 편집 금지, 인증 없는 웹 API 미제공.
- 공부 실습: PUBLISH_CONFIRMED 감사 쓰기 실패 뒤 DB와 DLQ 각각 어떤 상태가 가능한지 설명한다.
- 포트폴리오 연습: 원래 개인 담당/팀 기능/AI 보조 개인 개선을 30초 안에 나누어 설명한다.

## 8. 예상 면접 질문

1. 승인 ID만 입력하면 실제 승인이 확인되나요?
   - 아니다. 외부 변경 승인 기록 참조이며 조직 승인 시스템은 미구현이다. 권한은 운영 broker/tunnel 관리와 절차가 담당한다.
2. preview도 왜 read-only가 아닌가요?
   - manual get으로 ready→unacked→requeue 상태가 변한다. 원본 삭제·publish는 없지만 순서와 redelivery에 영향이 있다.
3. publish 성공 뒤 감사 파일 쓰기가 실패하면요?
   - 원본 ack를 보류한다. source에는 이미 전달됐을 수 있어 중복 가능성이 남으며 DB 멱등성과 수동 확인이 필요하다.
4. 이 프로젝트를 무엇이라고 설명하나요?
   - 차량 관제 서비스이며 개인 개선 주제는 데이터 정합성·장애 복구·권한 경계다. AI 서비스나 대규모 운영 성과로 포장하지 않는다.

## 9. AI 활용과 사람의 검증

- AI가 CLI와 테스트·문서 초안을 작성하고 실행했다. 팀 기여/개인 기여를 기존 문서와 대조했다.
- 자동 대량 복구·웹 API 확장보다 제한적 CLI를 선택했다. 사용자에게 근거와 남은 권한·audit 한계를 설명한다.
- 사용자가 코드를 독립 재현·설명했는지는 확인하지 않았다. 소개 문장의 1인칭은 본인 검토 후 사용하도록 명시했다.
- 공식 근거: [RabbitMQ confirms](https://www.rabbitmq.com/docs/confirms). 현재 문서와 로컬 3.13.7의 범위를 구분하고 실제 테스트로 보완했다.
