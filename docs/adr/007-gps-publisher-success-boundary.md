# ADR-007: GPS 발행 성공은 저장 broker 접수, live는 best effort

2026-09-06, 채택. direct mode의 동기 DB 저장과 RabbitMQ mode의 성공 의미는 다르다.

저장 exchange → correlated confirm ack + mandatory return 없음 → 저장 접수 확정 → live fanout.
저장 nack/return/exception/confirm timeout에는 HTTP 503용 CustomException을 반환하고 live를 발행하지 않는다.
저장 접수 후 live만 실패하면 저장 접수 성공을 유지하고 별도 counter를 올린다.

원자적 dual publish는 구현하지 않는다. 두 발행을 모두 성공 조건으로 삼으면 live consumer가
없는 상황 때문에 이미 저장 접수된 원본을 계속 재전송할 수 있다. 운영 원천 저장을 우선한다.
publisher confirmation은 DB commit/consumer ack/브라우저 수신 확인이 아니다.
confirm 미확인은 broker 미수신 확정이 아니다. HTTP 재시도에는 기존 GPS observation unique가
중복 DB 효과를 제한하지만 live 중복이나 모든 장치 이벤트의 exactly-once를 보장하지 않는다.

메시지는 persistent, connection factory는 correlated confirms/returns, template은 mandatory다.
future 대기는 경로별 2초다. connection/소켓/DNS 전체 deadline은 아니며 느린 publish 동기 대기
및 max in-flight admission control은 후속이다. live의 자동 replay는 없다.

`gps.publisher.storage.confirmed`, `gps.publisher.storage.unconfirmed`,
`gps.publisher.broadcast.unconfirmed`는 packet 단위 저카디널리티 counter다.
queue lag, DB commit rate와 따로 해석해야 한다. 예외 원문/packet/좌표는 로그에 넣지 않는다.

참고: [Spring AMQP confirms/returns](https://docs.spring.io/spring-amqp/reference/amqp/template.html).
공식 현재 문서는 4.x이나 프로젝트 의존성을 업그레이드하지 않았고 실제 프로젝트 라이브러리로
compile 및 RabbitMQ 3.13.7 테스트를 수행했다. 최종 결과는 CHANGE-028 기준이다.
