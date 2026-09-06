# CHANGE-028: GPS publisher confirm/return 및 부분 실패

- 날짜: 2026-09-06
- 기준: `149d5ef`, `codex/statistics-batch-restart`
- 상태: Verified

## 문제와 기여 경계

기존 팀 producer는 저장 exchange와 live fanout에 순차 send하고 broker 접수 여부를 확인하지 않았다.
저장 routing이 없어도 HTTP 성공처럼 보일 수 있었다. 이번 개인 현대화는 broker 접수 경계를
명시적으로 분리하며 원 팀의 RabbitMQ 도입 자체를 개인 구현으로 주장하지 않는다.

## Acceptance criteria

- [x] 실제 broker 저장/live routing 성공 및 persistent 속성
- [x] 저장 unroutable ack+return을 실패로 처리하고 live 발행 중지
- [x] 저장 성공/live unroutable, live exchange 없음의 부분 실패 계수
- [x] nack 및 실제 2초 confirm 미도착 단위 테스트
- [x] 전체 회귀와 기록

## 결정과 흐름

[ADR-007](../../adr/007-gps-publisher-success-boundary.md)에 성공 의미/대안을 기록했다.
저장 broker 접수를 먼저 확인하고 live는 best effort로 보낸다. 저장 미확인은 503용 error code
05000, live만 미확인이면 저장 접수 성공을 유지한다. 메시지는 persistent.
correlation ID는 매 발행 UUID로 만들고 return이 없는 ack만 성공으로 인정한다.
각 경로 future 대기 2초, 인터럽트는 interrupt flag를 복원한다.
판정 counter는 packet 단위이며 payload/MDN/회사 ID를 metric tag로 사용하지 않는다.

## 검증

- `./gradlew test --tests '*GpsLogProducer*' --console=plain`: 5개 통과, 12초.
- 통합 4개는 실제 RabbitMQ 3.13.7, unit 1개 안에서 nack/timeout 두 분기.
- 통합 payload는 serialization fixture이고 DB consumer와 연결하지 않았다. controller validator와
  기존 GPS DB 멱등성/재전달은 별도 회귀 테스트다. 전체 E2E DB 저장 측정으로 표현하지 않는다.
- 첫 전체 회귀: XML 313개/실패·오류·skipped 0이나 명령은 **실패**(2분 29초).
  AI가 별도 Gradle SSE task를 겹쳐 실행하여 modules-2 artifact cache lock timeout이 발생했다.
  테스트 실패와 실행 환경/도구 실패를 구분하며, 정상 build 성공으로 표현하지 않는다.
  다른 task가 종료된 후 `./gradlew test --rerun-tasks --console=plain`을 단독 재실행:
  **313개 통과, 실패/오류/skipped 0, BUILD SUCCESSFUL, 1분 26초**.

## 위험·실패·후속

확인 timeout은 유실 확정이 아니다. caller retry에는 중복이 가능하다. 기존 DB unique가
동일 observation의 DB 효과를 제한하지만 live 중복/순서/누락은 해결하지 않는다.
broker 확인 이후 실제 DB 장애는 consumer retry/DLQ 영역이다. queue가 실제 운영 policy를
가졌는지와 이 broker 테스트는 별개다. 운영 데이터/설정에는 접근하지 않았다.
두 단계 동기 발행의 throughput/latency, connection 전체 deadline, rate limit은 미측정/미구현이다.
HTTP controller를 통한 503 응답 통합 검증은 아직 별도이며 CustomException/ErrorCode 연결을 사용한다.

## 학습·면접

- 공부: publisher confirm vs consumer ack, mandatory return, partial success, timeout ambiguity,
  idempotency vs exactly-once, metrics cardinality.
1. ack인데 왜 실패일 수 있나요?
   - exchange가 메시지를 받았어도 목적 queue가 없어 return될 수 있다. 둘을 함께 확인한다.
2. live 실패에도 성공 응답을 하는 이유는요?
   - 원천 저장 broker 접수가 성공 기준이고 화면 전달은 best effort라는 선택이다.
3. confirm 성공이면 DB에 저장됐나요?
   - 아니다. 이후 consumer 처리/DB commit/retry/DLQ는 별도 단계다.
4. timeout이면 안전하게 같은 요청을 보내도 되나요?
   - 접수 여부가 불명확하므로 중복을 예상해야 한다. DB observation unique가 해당 중복 효과를 제한한다.

## AI 활용

AI가 성공 경계 대안, 구현, broker failure fixture, 문서 초안을 작성하고 검증을 실행했다.
원자적 dual publish/전체 exactly-once 주장은 거절했다. 사용자는 return/nack/timeout fixture를
재현하고 HTTP 성공과 DB 완료 차이를 직접 설명해야 한다. 운영 throughput은 AI가 확인하지 않았다.
