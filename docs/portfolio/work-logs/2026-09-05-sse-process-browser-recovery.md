# CHANGE-014: 별도 JVM fan-out과 브라우저 복구 검증

- 날짜: 2026-09-05, Shin Dong Jun + AI assistant
- BE 기준: `25b8ff4`, FE: `codex/sse-header-auth`
- 상태: 아래 검증 범위 완료. P0-02D의 proxy 검증은 미완료.

## 문제·기여와 결정

CHANGE-013은 한 JVM의 독립 connection만 검증했다. 이번에는 두 child JVM이 각각 생산 코드의 RabbitMQ topology와 SSE registry/sender를 실행하도록 확장했다. 기존 팀 fan-out 구현은 유지하며 테스트 증거를 개인 기여로 추가한다.

동시에 FE가 EOF를 조용히 무시하는 결함을 수정했다. 차량 화면에 단절·인증·접근 불가 안내와 수동 재연결을 추가했다. 자동 재시도보다 먼저 snapshot 중복과 인증 실패 복구를 명시했다. 상세 FE 설계·검증·학습 기록은 FE 저장소 `docs/portfolio/sse-recovery.md`에 있다.

## Acceptance criteria와 흐름

- [x] 실제 RabbitMQ publish가 두 별도 JVM의 queue → listener → SseEventSender → registry → EventWriter에 전달된다.
- [x] child 준비 확인 후 publish하고 양쪽 결과를 timeout 내 확인한다. 테스트 종료 시 stdin EOF로 종료하고 지연 시 해당 child만 강제 종료한다.
- [x] 기존 한쪽 종료·queue 삭제·재구독 회귀 유지.
- [x] 실제 Chromium에서 차량 화면 단절 안내와 재연결·중복 방지·갱신 토큰·401/403/404 분기 검증.
- [ ] 실제 Spring Boot HTTP 서버 두 개와 browser를 연결한 end-to-end, nginx/ALB proxy 검증.

Gradle가 runtime classpath를 test property로 제공하고 ProcessBuilder가 같은 JDK로 worker 두 개를 실행한다. worker마다 독립 connection과 queue가 생성된다. publisher가 이벤트를 발행하고 parent가 두 worker의 수신 marker를 확인한다. 테스트가 만든 프로세스와 Testcontainers만 정리하며 기존 서비스에는 접근하지 않는다. DB·tenant 정책 변경은 없다.

## 검증

- `./gradlew test --tests '*SseFanoutIntegrationTest' --console=plain`: 2/2 통과, Gradle 15초.
- `./gradlew test --console=plain`: 244/244 통과, 실패·오류·skip 0, Gradle 30초. XML 집계 및 양쪽 저장소 `git diff --check` 통과.
- FE unit 8/8, Chromium 5/5, build 성공. API/지도는 fixture이며 FE 문서에 정확한 범위를 기록했다.

## 한계와 후속 작업

child JVM은 전체 Spring Boot app이 아닌 테스트 worker다. 실제 broker와 production registry/sender를 쓰지만 DTO 변환, MDN 조회, servlet socket은 포함하지 않는다. browser 테스트와 broker 테스트는 각각 수행했으며 하나의 end-to-end 테스트로 주장하지 않는다. proxy buffering/timeout/heartbeat와 silent half-open, snapshot/live gap, DB 멱등성·DLQ/replay는 미검증이다. 느린 emitter가 순차 sender를 지연시키는 위험도 남아 있다.

## 학습·면접

1. 두 connection과 두 JVM 테스트는 어떻게 다른가? 전자는 메모리 격리가 모사되고 후자는 프로세스와 static 상태가 실제로 분리된다. 두 경우 모두 전체 app 기동과는 다르다.
2. 준비 marker를 기다리는 이유는? consumer 등록 전에 publish하면 fan-out 메시지가 아직 없는 queue로 전달되지 않는다. 임의 sleep 대신 조건으로 동기화한다.
3. JVM 테스트와 Chromium 테스트를 합치면 E2E인가? 아니다. 중간 HTTP/serialization/인가 경로를 실제로 연결한 단일 시나리오가 필요하다.

실습: 두 worker 중 하나의 준비 신호를 생략했을 때 생길 race와, browser retry가 snapshot을 다시 받는 이유를 설명한다. 사용자 실습 완료는 미확인.

## AI 기록

AI가 subprocess 테스트, FE 복구 UX, fixture와 문서를 작성·실행했다. 기존 fan-out을 유지하고 서버 연결 객체를 중앙 저장하려는 설계는 추가하지 않았다. 사람의 이해 확인과 실제 배포 환경 검증은 수행하지 않았다.
