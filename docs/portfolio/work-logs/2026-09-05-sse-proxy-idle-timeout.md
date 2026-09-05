# CHANGE-016: nginx idle timeout 후 SSE 재구독 검증

- 날짜: 2026-09-05, Shin Dong Jun + AI assistant
- 기준: BE `54f38cf`, FE `b9ddac4`
- 범위: 테스트·문서 변경. 기존 팀 SSE 기능에 대한 개인 장애 검증 보강.

## 문제·선택·실행 흐름

CHANGE-015는 client close 후 재구독만 확인했다. proxy가 먼저 연결을 종료하는 경우는 미검증이었다. 이번에는 nginx `proxy_read_timeout 2s`를 두고 연결당 첫 이벤트만 전송한다. 이후 의도적으로 침묵해 실제 upstream timeout을 발생시킨다. 2초는 테스트 시간이며 운영 권장값이 아니다.

브라우저는 첫 이벤트 이후 onend 또는 onerror를 기다린다. 단절 알림 전에 client.close를 실행하지 않으며, 알림을 받으면 정리하고 새로운 요청으로 재구독한다. 새 UUID 구독에도 이벤트 한 개를 보내 재수신을 확인한다. 정상 client close 시나리오도 parameterized test로 유지했다.

## Acceptance criteria

- [x] 정상 close·재구독과 proxy idle timeout·재구독 두 조건이 통과한다.
- [x] 각 시나리오에서 401 및 foreign-company 404가 유지된다.
- [x] timeout 시나리오에서 nginx 로그에 `upstream timed out`가 실제 존재한다.
- [x] 구독 key가 정확히 두 개 생성되고 각 구독에 이벤트를 한 번씩 전달한다.
- [x] browser가 first/reconnected payload를 모두 수신한다.

## 검증 결과

- `./gradlew sseBrowserTest --console=plain`: 2/2 통과, Gradle 14초. `idleTimeout=false`, `idleTimeout=true` 두 child 성공 출력 확인.
- `./gradlew test --console=plain`: 244/244 통과, 실패·오류·skip 0, Gradle 28초. 전용 browser test 2건은 별도 집계다.
- BE·FE `git diff --check` 통과. XML: `build/test-results/sseBrowserTest/`.
- 제품 코드와 의존성, 운영 proxy 설정은 변경하지 않았다. 실행에 필요한 sibling FE 및 Docker/Chromium 조건은 CHANGE-015와 동일하다.

## 한계와 후속 판단

이번 검증은 TCP 종료를 감지할 수 있는 proxy idle timeout이다. packet blackhole처럼 종료 신호가 오지 않는 silent half-open, nginx process crash, ALB idle timeout, 자동 backoff, replay·snapshot/live gap은 검증하지 않았다. 브라우저 script가 재구독하며 실제 화면 버튼은 CHANGE-014의 별도 fixture 테스트에서 검증했다. 이를 자동 재연결 기능 구현으로 표현하지 않는다.

현재 heartbeat가 없어 긴 무이벤트 구간에는 proxy timeout으로 끊길 수 있다. 운영 heartbeat 주기는 proxy 설정과 함께 결정해야 한다. 이번 2초 값을 운영에 적용하지 않는다. 원래 팀 코드의 유지와 개인 검증 기여를 구분한다.

## 학습·면접

1. HTTP 요청 timeout과 proxy_read_timeout은 같은가? 전자는 전체 요청 제한으로 쓰일 수 있고 여기서는 upstream에서 읽을 데이터가 없는 구간에 proxy가 종료한다. 이벤트가 계속 오면 idle 조건이 달라진다.
2. timeout을 mock하지 않은 이유는? 실제 proxy socket 종료가 fetch reader에 전달되는 경로를 확인하기 위해서다. browser onerror 또는 EOF를 실제로 관찰했다.
3. 단절 감지가 되면 무손실인가? 아니다. 새 연결 사이의 이벤트는 replay 저장소와 cursor 없이는 복구를 보장하지 못한다.
4. heartbeat가 해결하는 범위는? 무이벤트 구간의 연결 유지·탐지에 도움을 주지만 durable replay나 인가 만료 처리를 대신하지 않는다.

실습: 이벤트를 지속 전송하면 idle timeout이 발생하지 않는 이유와 2초 설정을 운영에 복사하면 안 되는 이유를 설명한다. 사용자 실습은 미확인.

## AI 활용

AI가 장애 조건, parameterized test, browser 단절 처리 및 문서를 작성하고 실행했다. 검증 범위를 실제 timeout으로 제한하고 미측정 무손실·가용성 성과를 주장하지 않았다. 사람의 학습 이해와 운영 검증은 별도다.
