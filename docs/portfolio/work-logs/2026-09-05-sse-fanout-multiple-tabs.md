# CHANGE-013: 실제 broker fan-out과 다중 탭 구독 분리

## 메타데이터

- 날짜: 2026-09-05
- 작업자: Shin Dong Jun + AI assistant
- 기준: `codex/portfolio-foundation@9c0502a`
- 범위: P0-02D-3B의 broker 통합 검증과 구독 identity. 브라우저·proxy 및 두 JVM 검증은 후속 범위.

## 1. 문제와 출처

기존 팀의 RabbitMQ fan-out/AnonymousQueue 구조는 이전 CHANGE-012에서 queue 속성만 단위 테스트했다. 실제 broker 복제와 연결 종료 후 queue 정리는 확인하지 않았다. 또한 `category:resourceId:username` key는 같은 사용자의 두 탭을 같은 연결로 취급하여 앞 탭을 종료했다.

이번 개인 현대화는 연결별 UUID 발급과 실행 증거 추가다. 원 팀의 RabbitMQ 설계를 새로 만들었다고 주장하지 않는다. 기존 개인 기여는 `original-contributions.md`를 따른다.

## 2. Acceptance criteria

- [x] 동일 JWT로 동일 차량 SSE를 두 번 요청하면 두 구독이 유지된다.
- [x] resource prefix 매칭 및 role/tenant 거부 회귀 테스트가 통과한다.
- [x] 실제 RabbitMQ가 독립 connection/queue 두 개에 동일 이벤트를 전달하고 각 local SSE registry가 원래 이름으로 전송한다.
- [x] 한 connection 종료 후 해당 queue가 삭제되고 다른 구독은 후속 이벤트를 받는다.
- [x] 새 connection은 다른 queue로 연결되어 이후 이벤트를 수신한다.
- [ ] 실제 브라우저·proxy 및 두 JVM end-to-end 검증.

## 3. 선택지와 결정

클라이언트 tab ID를 신뢰하면 검증과 충돌 정책이 추가된다. 서버 UUID는 URL·FE 계약 변경 없이 요청별 독립 구독을 만든다. key를 `category:resourceId:username:UUID`로 바꾸었다. UUID는 인증 수단이 아니며 기존 principal 회사의 resource 조회 후에만 생성한다.

재접속은 새로운 구독이다. 이전 연결은 completion/error/timeout으로 정리된다. 네트워크 단절 감지 전에는 구·신 연결이 잠시 공존할 수 있다. 사용자별 연결 수 제한은 아직 없으므로 UUID가 자원 고갈까지 해결하지는 않는다.

## 4. 실행 흐름과 검증 구성

Bearer 인증 → role 및 회사 소유권 확인 → 서버 UUID key 생성 → 초기 데이터 전송 → 같은 resource prefix로 각 탭에 live 전달. DB schema와 transaction은 변경하지 않았다.

Testcontainers `rabbitmq:3.13.7-alpine`에 독립 `CachingConnectionFactory`, AnonymousQueue, listener, SseConnection을 각각 둘씩 연결한다. production `RabbitMQConfig`의 exchange와 queue 정의를 사용한다. 실제 broker publish → listener → SseEventSender → local registry → 기록용 EventWriter 순으로 확인한다. 첫 연결을 닫아 queue 삭제를 관찰하고 남은 연결과 재생성 연결에 추가 이벤트를 보낸다.

## 5. 검증

- `./gradlew test --tests '*Sse*Test' --tests '*TripLogTenantIntegrationTest' --console=plain`: 29/29 통과, 실패·오류·skip 0. Gradle 27초.
- `./gradlew test --console=plain`: 전체 243/243 통과, 실패·오류·skip 0, Gradle 26초. XML 합계 확인. `git diff --check` 통과.
- XML 증거: `build/test-results/test/TEST-org.thisway.support.component.streaming.SseFanoutIntegrationTest.xml` 등. build 출력은 Git에 포함하지 않으며 명령으로 재생성한다.
- 최초 Docker 조회는 sandbox socket 접근 제한으로 실패했고 승인된 실행으로 Docker 28.3.0을 확인했다. 테스트 컨테이너는 임시 포트와 독립 저장소를 사용하고 자동 정리한다.

## 6. 한계와 후속 검증

테스트는 한 JVM 내 두 독립 broker connection이다. 두 Spring Boot 프로세스, production StreamGpsLogConsumer의 DTO 변환/MDN 조회, 실제 servlet 출력, browser, reverse proxy는 포함하지 않는다. 기록용 EventWriter를 쓰므로 브라우저 수신을 증명하지 않는다. 재연결 전에 보낸 이벤트의 replay도 제공하지 않는다.

FE fetch adapter는 오류 시 close 정책이며 자동 reconnect가 없다. 이를 추가하려면 초기 chunk 재수신 때 지도 상태를 초기화하거나 중복을 제거하는 정책과 retry 종료 조건을 먼저 정해야 한다. 제품 브라우저 시나리오와 두 JVM 검증은 P0-02D-3B에 남긴다.

이전 CHANGE-012의 구독별 lock은 메모리 queue 경합을 제한하지만 `sendToPrefix`는 순차 호출이므로 느린 socket이 다른 구독 전송과 broker consumer도 지연시킬 수 있다. 초기 버퍼 count 제한을 완전한 slow-client 격리라고 표현하지 않는다. publisher confirm, dual publish 부분 실패, DB 멱등성·retry/DLQ/replay는 P1-01의 저장 파이프라인 검증 대상이다.

## 7. 학습 기록

공부할 개념: 사용자 identity와 connection identity의 차이, fan-out과 경쟁 소비, exclusive/auto-delete queue 수명, application instance와 TCP connection의 차이.

실습: 테스트의 두 queue를 하나의 이름으로 합치면 왜 모든 구독이 모든 이벤트를 받지 못하는지 설명하고, 연결 종료 후 재구독했을 때 과거 이벤트가 복구되지 않는 이유를 설명한다. 사용자 실습 완료는 확인하지 않았다.

## 8. 예상 면접 질문

1. 동일 사용자 key에 UUID를 붙인 이유는?
   - 사용자는 권한 주체이고 탭별 SSE는 독립 연결이다. 인증된 사용자라도 여러 연결을 가질 수 있다.
2. 실제 broker 테스트는 무엇을 증명하는가?
   - 독립 queue로의 복제, 한 connection 종료의 격리, queue 정리와 재구독 이후 전달이다. 두 JVM이나 브라우저까지 검증한 것은 아니다.
3. AnonymousQueue가 사라지면 미수신 이벤트는 어떻게 되는가?
   - 해당 queue의 이벤트는 복구되지 않는다. 이 화면 방송 경로에는 durable replay가 없고 저장 파이프라인과 역할이 다르다.
4. 자동 reconnect만 붙이면 충분한가?
   - 초기 snapshot 재전송으로 지도 중복이 생길 수 있다. 초기화/중복 제거, gap 정책, backoff와 인증 실패 종료 조건을 함께 설계해야 한다.

## 9. AI 활용과 검증 책임

AI가 변경과 통합 테스트를 구현하고 실행했다. 서버 UUID는 FE 계약 영향이 작아 채택했고 중앙 registry 추가는 기존 fan-out 구조에 불필요하여 채택하지 않았다. 사람의 코드 이해, 브라우저 실습과 두 JVM 검증은 미수행이다. 포트폴리오 설명은 위 실행 범위에 한정한다.
