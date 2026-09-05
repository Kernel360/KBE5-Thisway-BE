# CHANGE-012: SSE bounded buffer와 초기/live 순서 보존

## 메타데이터와 기여 범위

- 날짜: 2026-09-05
- 작업자: Shin Dong Jun + AI assistant
- 기준: `codex/portfolio-foundation@898e7eb`
- 상태: P0-02D-3A 구현 및 자동 검증 완료. P0-02D 전체는 미완료.

기존 팀 코드는 초기 GPS chunk를 보내는 동안 들어온 live event를 process-local queue에 보관하는 구조였다. 이번 개인 현대화는 그 기능의 event type, 용량, 동시성 계약과 다중 instance topology 검증을 보강한 것이다. 기존 RabbitMQ producer, fan-out exchange, anonymous queue, consumer 설계 전체를 개인 기여로 주장하지 않는다.

## 문제와 재현 근거

기존 `SseContext`는 `ConcurrentLinkedQueue<Object>`를 사용해 event name을 저장하지 않았고 개수나 byte 상한도 없었다. flush는 모든 데이터를 존재하지 않는 FE 계약인 `live_gps` 이름으로 전송했다. 또한 `initialChunkCompleted=true` 설정과 queue drain 사이에 live thread가 상태를 읽고 늦게 enqueue하면, flush가 빈 queue를 확인한 뒤 그 이벤트가 영원히 남을 수 있었다.

메모리 상한 문제는 slow subscriber나 큰 초기 조회에서 JVM 자원 고갈로 번질 수 있다. 이름 손실은 FE listener가 이벤트를 받지 못하게 한다. 순서 경쟁은 재현 빈도가 낮아도 차량 위치가 조용히 유실되는 correctness 문제다.

## Acceptance criteria

- [x] 초기화 중 live event는 최대 256건만 보관한다.
- [x] 257번째 event에서는 오래된 좌표를 조용히 버리지 않고 해당 연결을 오류 종료하고 registry에서 제거한다.
- [x] buffered event의 원래 event name과 FIFO 순서를 보존한다.
- [x] flush와 동시에 도착한 live event가 buffered event를 앞지르거나 queue에 고립되지 않는다.
- [x] 종료·재접속된 context에는 전송하지 않는다.
- [x] 각 application instance가 exclusive·auto-delete anonymous broadcast queue를 갖는 topology를 test로 고정한다.
- [ ] 실제 RabbitMQ에 두 application instance를 연결한 end-to-end broadcast 검증은 후속 작업이다.
- [ ] 실제 브라우저와 reverse proxy에서 disconnect/reconnect를 검증하는 작업은 후속 작업이다.

## 선택지와 판단

### Overflow policy

가장 오래된 좌표를 버리는 정책은 화면을 계속 살릴 수 있지만 위치 궤적의 누락을 숨긴다. 무제한 queue는 가용성을 위해 process memory를 무제한 소비한다. 이번에는 256건에서 구독 하나만 fail-closed로 종료했다. 실패가 사용자에게 드러나고 다른 구독과 JVM을 보호한다는 장점이 있다. 반면 현재 FE에는 자동 재연결이 없어 화면 갱신이 중단되므로 운영 UX는 P0-02D-3B에서 보완해야 한다.

256은 측정으로 최적화한 값이 아니라 기존 60건 chunk 크기와 일시적인 초기 전송 겹침을 감당하기 위한 보수적 guardrail이다. 실측 event rate, 평균 payload byte, 초기 조회 시간으로 다시 산정해야 하며 count limit이 byte limit을 대신하지 않는다.

### Concurrency control

atomic flag와 concurrent queue를 따로 조작하는 방식은 각 연산은 안전해도 둘을 합친 상태 전이는 원자적이지 않다. 이번에는 subscription context별 monitor와 `INITIALIZING → LIVE → CLOSED` 상태를 사용했다. flush와 live send를 같은 context에서 직렬화해 이전 buffered event가 모두 전송된 뒤 live event가 전송되도록 했다. lock은 전체 registry가 아니라 한 구독에만 적용되지만, 해당 client의 `send`가 느리면 같은 client의 후속 전송은 대기한다.

### Multi-instance

중앙 SSE registry를 Redis로 옮기는 선택지는 연결 객체를 직렬화할 수 없어 맞지 않는다. 기존 구조는 producer가 `gps_log.broadcast.exchange`에 발행하고 각 application instance가 자기 `AnonymousQueue`를 binding한 뒤 로컬 registry에 전달한다. 방향은 타당하므로 새 broker를 추가하지 않고 queue의 unique, exclusive, auto-delete 특성을 단위 테스트로 고정했다. 이 테스트는 실제 broker 선언·두 consumer 수신을 증명하지 않는다.

## 상태와 event 흐름

```text
HTTP subscribe
  -> local SseContext(INITIALIZING)
  -> initial DB GPS chunks 전송

RabbitMQ fan-out event
  -> 각 instance의 AnonymousQueue
  -> StreamGpsLogConsumer
  -> instance-local SseConnection
  -> INITIALIZING: (eventName, data)를 bounded FIFO에 저장
  -> buffer full: 그 subscription만 오류 종료

initial chunks 완료
  -> context lock 획득
  -> buffered (eventName, data)를 FIFO flush
  -> LIVE 전환
  -> 이후 event는 같은 lock에서 즉시 전송
```

DB transaction은 이번 변경에 관여하지 않는다. RabbitMQ 저장용 direct queue와 화면 방송용 fan-out queue도 기존대로 분리되어 있다.

## 검증 결과

- `./gradlew test --tests '*SseConnectionTest' --tests '*RabbitMQConfigTest' --console=plain`: 10/10 통과, 실패·오류·skip 0.
- 결정적 concurrency test는 첫 buffered write를 latch로 멈추고 concurrent live send가 끝나지 못함을 확인한 다음, lock을 풀어 `buffered → live` 순서와 유실 없음을 검증한다.
- overflow test는 테스트 상한 2에서 세 번째 enqueue가 `OVERFLOW`를 반환하고 `completeWithError` 및 registry 제거를 수행하는지 확인한다.
- event writer를 주입한 테스트는 서로 다른 두 event name과 세 payload의 정확한 FIFO를 확인한다.
- `RabbitMQConfigTest`는 두 application instance를 모사해 anonymous queue 이름이 다르고 두 queue가 exclusive·auto-delete인지 확인한다.
- `./gradlew test --console=plain`: 전체 242/242 통과, failures 0, errors 0, skipped 0, Gradle 20초. XML 집계로 확인했다.
- `git diff --check`: 통과.

첫 sandbox 실행은 사용자 Gradle cache의 `.lck` 파일 접근 제한으로 실패했고, 승인된 Gradle 실행으로 다시 검증했다. 이는 제품 테스트 실패가 아니다.

## 남은 한계와 주장 금지선

- 실제 RabbitMQ broker와 두 JVM을 띄운 fan-out end-to-end test는 하지 않았다. “다중 instance 전달 검증 완료”라고 주장하지 않는다.
- 실제 Chrome/Safari, nginx/ALB buffering, mobile network 단절, JWT 만료 후 reconnect는 검증하지 않았다.
- FE fetch adapter에는 backoff, retry limit, `Last-Event-ID`, replay가 없다. 연결 오류 후 자동 복구를 주장하지 않는다.
- 보장은 한 JVM의 한 subscription context에 도착한 순서다. 여러 publisher의 전역 event-time 순서, broker 재전달 순서, reconnect 사이 gap은 보장하지 않는다.
- 256건은 count 상한이며 payload byte와 servlet container의 socket/output buffer는 별도다.
- 같은 username이 동일 resource를 여러 탭에서 열면 뒤 연결이 앞 연결을 대체하는 기존 key 정책이 남아 있다.
- 초기 데이터 조회가 emitter 등록보다 먼저라 그 사이의 GPS event gap 가능성은 남아 있다. 완전한 snapshot+live handoff에는 sequence/cursor와 replay source가 필요하다.

## 학습 포인트

1. `ConcurrentHashMap`, `AtomicBoolean`, `ConcurrentLinkedQueue`를 조합했다고 전체 protocol이 thread-safe인 것은 아니다. correctness 단위는 자료구조 한 개가 아니라 “상태 확인 → enqueue 또는 flush”라는 복합 연산이다.
2. backpressure는 단순 성능 최적화가 아니라 격리 정책이다. 느린 client 하나가 process memory를 잠식하지 못하도록 용량과 overflow 동작을 명시해야 한다.
3. SSE의 `event` 필드는 client listener routing 계약이다. payload만 보존하고 event name을 바꾸면 transport 성공과 business delivery 성공이 달라진다.
4. 다중 instance SSE는 연결을 중앙 저장하는 문제가 아니라, 각 instance가 동일 domain event를 받고 자기 local connection에 fan-out하는 문제다.

직접 실습: buffer 상한을 2로 둔 뒤 세 이벤트를 넣고, drop-oldest 정책으로 바꿨을 때 사용자가 보게 될 궤적과 장애 탐지성이 어떻게 달라지는지 설명한다. 사용자 본인의 수행은 아직 확인하지 않았다.

## 예상 면접 질문

1. concurrent collection을 썼는데 왜 race condition이 발생했나요?
   - 개별 `set`, `isEmpty`, `add`, `poll`은 안전하지만 완료 상태와 queue drain을 하나의 원자적 상태 전이로 만들지 않았기 때문이다. context lock으로 복합 불변식을 보호했다.
2. overflow에서 오래된 GPS를 버리지 않고 연결을 끊은 이유는 무엇인가요?
   - 조용한 데이터 왜곡보다 명시적 실패를 택했다. 구독 단위로 격리해 JVM을 보호하지만, 재연결 UX가 필요하다는 비용도 기록했다.
3. 이 구현이 이벤트 순서를 어디까지 보장하나요?
   - 한 process, 한 subscription context에 도착한 buffered/live 전송 순서만 보장한다. 여러 producer의 event-time ordering이나 reconnect gap은 보장하지 않는다.
4. 서버가 여러 대면 in-memory SSE registry가 왜 가능한가요?
   - 연결은 각 서버 로컬에 둔다. 대신 fan-out exchange가 GPS event를 각 instance의 anonymous queue에 복제하고, 각 서버가 자기 구독자에게 보낸다. 실제 broker E2E는 아직 남아 있다.
5. 256이라는 수치는 어떻게 정했나요?
   - 검증된 capacity가 아니라 안전한 유한 상한을 먼저 둔 값이다. event rate, payload byte, initial load p95를 측정해 memory budget에서 역산해야 한다.

## AI 사용과 검증 책임

AI가 기존 race와 event contract를 분석하고 상태 모델, bounded buffer, 결정적 동시성 테스트와 문서 초안을 작성했으며 전체 테스트를 실행했다. 사람은 state transition, overflow trade-off, RabbitMQ fan-out 범위와 미검증 조건을 설명할 수 있어야 한다. 자동 테스트가 실제 browser, proxy, broker, 두 JVM 검증을 대신했다고 기록하지 않는다.
