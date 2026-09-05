# CHANGE-015: 실제 Boot·nginx·Chromium SSE 연결 검증

- 날짜: 2026-09-05
- 작업자: Shin Dong Jun + AI assistant
- 기준: BE `d45c6d4`, FE `7bb30fd`
- 상태: 아래 통합 시나리오 Verified. 운영 장애·무손실 복구 보장은 별도.

## 문제와 기여

기존 CHANGE-014는 broker/JVM과 browser fixture를 따로 검증했다. 이번에는 실제 Boot HTTP 서버, JWT 필터, tenant-scoped repository, SseEmitter, nginx, Chromium, FE adapter를 한 경로로 연결한다. 기존 팀 기능을 유지하며 이번 개인 기여는 격리된 통합 테스트와 재현 절차다. 원 개인 기여 출처는 original-contributions.md를 따른다.

## Acceptance criteria

- [x] 헤더 없는 실제 browser 요청은 401이다.
- [x] 다른 company claim의 유효한 JWT는 실제 차량 조회에서 404이다.
- [x] 허용된 JWT로 nginx를 거쳐 열린 SSE에서 이름·payload를 수신한다.
- [x] 브라우저 adapter close 후 새 연결을 열어 다시 수신한다.
- [x] 초기 이벤트를 받기 위해 stream 종료를 기다리지 않는다.

## 선택과 실행 흐름

기존 default 테스트의 Docker 사전조건에 FE checkout·Node·Chromium까지 추가하지 않도록 `sseBrowserTest` task와 `sse-browser` tag로 분리했다. 기본 test는 해당 tag를 제외하고 전용 task가 명시적으로 실행한다. Disabled나 조건부 skip으로 통과시키지 않는다.

JUnit은 RANDOM_PORT Boot 서버에 H2 회사·차량 데이터를 commit하고 테스트 JWT 두 개를 생성한다. Testcontainers nginx:1.28.0-alpine이 host port로 proxy한다. nginx는 HTTP/1.1, proxy_buffering off, proxy_cache off, read timeout 10초를 사용한다. 브라우저가 실제 FE adapter를 nginx에서 import하고 401/404를 확인한 뒤 허용된 연결을 두 번 순서대로 연다. JUnit은 production SseEventSender로 fixture 이벤트를 주입한다. production controller/service/repository/filter/emitter는 mock하지 않는다.

테스트용 JWT는 child process 환경변수로만 전달하며 URL·출력에 기록하지 않는다. nginx access log는 비활성화했다. 테스트 종료 시 해당 browser process, emitter, nginx만 정리한다. 실제 운영 데이터나 서비스에는 접근하지 않는다.

## 재현 및 검증

두 저장소를 나란히 checkout한다. FE에서 `npm ci`, `npx playwright install chromium` 후 BE에서 다음을 실행한다. Docker와 PATH의 Node, Java 21이 필요하다.

```sh
./gradlew sseBrowserTest --console=plain
./gradlew test --console=plain
```

- 전용 통합 테스트: 1/1 통과, Gradle 20초. child 결과: `Boot/nginx/Chromium: 401, tenant 404, live event and reconnect verified`.
- 기본 전체 회귀: 244/244 통과, 실패·오류·skip 0, Gradle 30초. 전용 통합 테스트 1건은 이 숫자에 포함하지 않는다. 양쪽 `git diff --check` 통과.
- 증거는 `build/test-results/sseBrowserTest/` XML 및 `build/reports/tests/sseBrowserTest/`에 재생성된다.
- 최초 실행은 fixture Company.memo NOT NULL 위반으로 실패했다. fixture를 보완했고 module import에 필요한 nginx MIME 설정도 추가했다.

## 한계와 후속 실험

Boot 서버는 한 개다. 두 JVM broker 검증은 CHANGE-014의 별도 시나리오다. DB는 H2이며 MySQL 검증이 아니다. 이벤트는 sender에서 주입하여 RabbitMQ부터 browser까지 연결한 전체 ingestion E2E는 아니다. 브라우저에서는 실제 adapter를 사용하지만 React 차량 화면과 Kakao SDK는 실행하지 않는다.

재구독은 client가 명시적으로 close한 뒤 새 연결을 여는 조건이다. nginx 강제 중단, idle timeout, silent half-open, ALB, JWT 만료·권한 회수, snapshot/live gap 및 durable replay는 보장하지 않는다. proxy_buffering off의 동작을 확인했으나 buffering on과의 성능 비교 실험은 하지 않았다. 이 테스트 설정을 실제 운영 nginx에 배포한 것은 아니다.

## 학습과 면접

1. MockMvc로 충분하지 않은 이유는? servlet socket 출력과 reverse proxy buffering, browser stream decoding 경로를 실행하지 않기 때문이다.
2. proxy_buffering off의 목적은? 작은 SSE 이벤트가 proxy에 머무르지 않고 열린 응답 중 client에 전달되게 하기 위해서다. 커널 버퍼·네트워크 지연까지 없애는 것은 아니다.
3. H2 통합 테스트가 보장하지 않는 것은? MySQL schema·SQL dialect·index·transaction 특성이다. Flyway/MySQL 단계에서 별도로 검증한다.
4. 테스트를 별도 task로 분리한 이유는? FE·브라우저 의존성을 명시하고 실행 여부를 분명히 하기 위해서다. 기본 suite 통과만으로 이 E2E를 수행했다고 주장하지 않는다.

실습: 토큰을 제거하거나 company claim을 바꾸었을 때 filter와 scoped query 중 어디서 거부되는지 설명한다. 사용자 실습 완료는 미확인이다.

## AI 활용

AI가 fixture, test task, nginx 구성, browser script를 작성하고 실행했다. 기존 제품 코드를 테스트 편의상 우회하거나 공개 endpoint를 추가하지 않았다. 사람의 운영 검증과 이해 확인은 수행하지 않았다. 검증한 연결 경로와 아직 실행하지 않은 장애 조건을 구분해 기록했다.
