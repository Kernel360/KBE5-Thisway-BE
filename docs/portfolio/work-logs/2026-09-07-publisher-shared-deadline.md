# CHANGE-043 — 발행 두 경로의 공유 대기 예산과 bounded worker

2026-09-07. 구현·최종 회귀 완료. BE 전체459개 중 producer 단위4/실제RabbitMQ4 포함, 실패/오류/skipped0. 별도 성능 task1도 통과.

## 문제·기여 경계

원 팀의 RabbitMQ 경로에 CHANGE-028로 confirm/return과 저장·live 부분 실패를 도입했다. 이전 구현은 저장 confirm 2초 + live confirm 2초를 각각 기다렸고 `RabbitTemplate.send` 자체의 연결/쓰기 지연은 caller를 붙잡을 수 있었다. 이번 개인 현대화는 broker I/O를 유한 worker로 제한하고 발행 호출의 대기 예산을 합친다.

## 설계와 실행 흐름

인증 identity와 trace metadata로 메시지를 변환한 뒤 최대 4개 worker, 대기 64개 executor에 제출한다. 동일한 monotonic deadline 2초를 queue 대기, 저장 send/confirm, live send/confirm에 공유한다. 포화 시 503, deadline 시 caller는 task를 취소하고 대기 queue에서 제거한다. 만료된 queued task는 새 send를 시작하지 않는다. 저장용 ack와 return 없음이 확인된 경우 live 전달 지연이 그 접수를 취소하지 않는다.

동기 send에 timeout 설정만 추가하는 안은 socket/library 지연을 포함한 호출자 대기를 제한하지 못해 채택하지 않았다. 무제한 async thread/queue도 메시지와 thread가 누적되므로 채택하지 않았다. 요청당 별도 worker를 만들지 않고 bean 종료 시 executor를 정리한다. trace ID는 caller에서 복사하며 raw GPS/key를 log/exception에 넣지 않는다.

## Acceptance와 검증

- blocked send가 interrupt를 무시해도 caller는 공유 예산 후 503으로 돌아온다.
- 저장 confirm에 1.5초를 썼을 때 live는 남은 약 0.5초만 기다리고 저장 접수 성공을 유지한다.
- nack/timeout/mandatory return과 live 부분 실패의 기존 실제 RabbitMQ 회귀를 유지한다.
- 최종 전용 명령: `./gradlew test --tests '*GpsLogProducer*' --console=plain`.
- 부하 측정은 CHANGE-041의 동일 seed/장치/시나리오를 다시 실행해 결과를 갱신한다. 코드만으로 개선율을 주장하지 않는다.

## 한계

Java scheduler/GC가 있는 환경의 2초 예산은 실시간 시스템의 절대 deadline이 아니다. HTTP body 수신, Redis 인증, DB 처리 전체의 2초 상한도 아니다. 이미 실행된 broker send는 취소나 timeout 뒤 늦게 접수될 수 있어 503이 무발행을 뜻하지 않는다. 실제로 interrupt를 무시하는 I/O가 회복하지 않으면 최대 4개 worker가 점유되고 신규 요청은 유한 queue/503으로 제한된다. broker socket timeout/회복 점검과 DB idempotency가 여전히 필요하다.

storage.confirmed는 broker ack의 관측이고 DB/consumer 완료가 아니다. deadline/포화와 storage.unconfirmed counter는 서로 다른 단계이며 단순 합산해서 요청 수를 계산하지 않는다. live 이벤트 replay/journal은 제공하지 않는다. 화면은 재접속 뒤 REST 현재 상태/운행 데이터를 다시 조회하는 기존 정책을 유지한다.

## 학습·면접·AI 검증

1. Future.cancel(true)는 왜 broker publish를 되돌리지 못하는가? interrupt는 협력적 취소이고 이미 네트워크에 전달된 부작용은 rollback되지 않는다.
2. queue도 왜 제한해야 하는가? worker 수만 제한하면 대기 payload와 만료 요청이 메모리를 계속 차지한다.
3. 왜 저장 confirm 뒤 live 실패가 503이 아닌가? 저장 접수 계약과 best-effort 화면 갱신을 분리했다.
4. 왜 nanoTime인가? duration/남은 예산은 wall clock 조정의 영향을 받지 않아야 한다.

AI가 구현·경계 테스트를 제안했고 실제 blocked-send fixture, 기존 RabbitMQ 라우팅 회귀, 최종 부하 결과로 교차 검증한다. 원 팀 구현·개인 변경·아직 미검증 운영 부하를 구분한다.


### 유한 용량 회귀

interrupt를 무시하는 broker send에 72개 동시 caller를 보내 실제 send 진입은 4개로 제한되고, queue64를 넘는 요청이 즉시 reject counter와 503을 남기는 사례를 추가했다. 대기 요청도 2초 공유 예산 뒤 반환해야 하며 시험 종료에서 latch/worker를 정리한다. 최종 전체459개에서 통과했다. [통합 기록](2026-09-07-local-completion-review.md)을 참고한다.
