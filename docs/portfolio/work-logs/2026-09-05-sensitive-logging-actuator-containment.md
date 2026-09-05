# CHANGE-005: 민감정보 로그 수집 최소화와 Actuator containment

## 메타데이터

- 날짜: 2026-09-05 KST
- 작업자: Shin Dong Jun + AI assistant
- 브랜치/기준 커밋: `codex/portfolio-foundation`, base `develop@98bff23`
- 관련 issue/PR: 없음
- 상태: Verified

## 1. 문제와 근거

- `LoggingFilter`는 query string 전체를 INFO로 기록했다. SSE가 `?token=`을 사용하므로 access log에 JWT가 남을 수 있었다.
- `RequestBodyLoggingAdvice`와 `ResponseBodyLoggingAdvice`는 거의 모든 HTTP body를 INFO로 직렬화했다. 로그인 password, 비밀번호 변경 code/newPassword, 응답 access/refresh token이 대상이었다.
- `@MaskingData` 기반 serializer는 있었지만 실제 request/response DTO에 annotation 사용처가 없어 보호 효과가 없었다.
- GPS producer/consumer와 RabbitMQ error handler는 전체 telemetry payload를 출력했고, service log와 일부 예외 메시지는 원시 MDN을 기록했다.
- dev/prod의 `spring.security.debug=true`와 framework DEBUG 출력은 DTO `toString()`을 통해 request/response secret을 다시 노출할 수 있었다. 변경 중 root logger 통합 테스트가 이를 실제로 재현했다.
- `ActuatorAuthorizationPolicy`는 모든 GET `/actuator/**`를 익명 허용했고, dev/prod는 exposure `*`, health detail `always`였다.
- 이번 변경은 원 팀의 로깅·Actuator 정책을 이후 개인 현대화에서 축소하고 검증한 것이다. reverse proxy, VPC/security group, Prometheus 인증까지 구성한 변경은 아니다.

## 2. Acceptance criteria

- [x] 애플리케이션 access log에 query string을 기록하지 않는다.
- [x] 로그인 password와 access/refresh token, 비밀번호 변경 code/newPassword를 request-time root logger 전체에서 발견할 수 없다.
- [x] 사용되지 않는 body logging advice와 무효한 annotation 기반 masking 경로를 제거한다.
- [x] GPS/RabbitMQ log에 전체 payload와 원시 MDN을 기록하지 않는다.
- [x] 익명 Actuator 접근은 정확한 GET `/actuator/health`, `/actuator/prometheus`만 허용한다.
- [x] dev/prod에서 health와 prometheus만 생성하고 health component detail은 숨긴다.
- [x] allowlist 밖 endpoint는 인증돼도 생성되지 않고, health 하위 경로와 GET 외 method는 익명 허용하지 않는다.
- [x] 좁은 테스트와 전체 회귀 테스트를 통과시키고 실패 과정도 기록한다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| 모든 body를 annotation으로 마스킹 | 기존 상세 로그 유지 | 새 DTO/필드 annotation 누락 시 즉시 재노출, nested/collection 관리 부담 | 거절 |
| password/token 필드명 문자열 치환 | 변경이 작음 | 우회·오탐·JSON 형식 의존, 원본 secret을 먼저 메모리에 수집 | 거절 |
| HTTP body/query를 기본 미수집하고 method/path/status만 기록 | secret 유출면과 로그 비용 최소화 | 장애 분석 시 payload 재현 정보 감소 | 채택 |
| `/actuator/**`를 Security에서만 제한 | endpoint별 role 통제 가능 | exposure `*`면 실수 한 번으로 민감 endpoint가 다시 노출 | 거절 |
| exposure allowlist + 정확한 Security matcher | endpoint 미생성과 접근 통제의 이중 방어 | Prometheus 네트워크 경계는 별도 필요 | 채택 |
| Prometheus도 인증 필수 | 외부 노출 축소 | 현재 scraper credential 운영 계약이 없음 | 후속 검토 |

선택 이유: password/JWT/인증 코드/위치 원본은 정상 운영에 반드시 필요한 로그가 아니다. 마스킹 완성도를 신뢰하기보다 애초에 수집하지 않는 data minimization을 기본값으로 삼았다. 관리 endpoint도 “생성하지 않음”과 “요청을 허용하지 않음”을 별도 방어선으로 뒀다.

## 4. 구현과 실행 흐름

주요 변경:

- `LoggingFilter`: query를 버리고 HTTP method + URI와 response status만 기록
- body logging advice, 전용 ObjectMapper, masking annotation/serializer 제거
- GPS/RabbitMQ: 전체 DTO/payload와 MDN 대신 항목 수·payload byte size 같은 비식별 metadata만 기록
- `GpsLogSaveService`: MDN을 포함한 예외 detail 제거
- dev/prod: Security debug off, Spring Web/Security INFO 하한 설정
- Actuator: health/prometheus만 exposure하고 health detail 비공개
- Security policy: 정확한 GET health/prometheus만 `permitAll`

HTTP 로그 흐름:

```text
HTTP request
  -> LoggingFilter: method + URI 기록, query 미수집
  -> Security filter / Controller
  -> request/response body logging advice 없음
  -> framework logger는 INFO 이상
  -> LoggingFilter: status만 기록
```

Actuator 방어 흐름:

```text
management exposure allowlist
  -> health, prometheus bean만 web endpoint로 생성
  -> Security exact method/path matcher
       GET /actuator/health      -> 익명 허용, components 미노출
       GET /actuator/prometheus  -> 익명 허용
       GET /actuator/env         -> endpoint 미생성(인증 사용자도 404)
       GET /actuator/health/db   -> exact allowlist 밖(익명 401)
       POST /actuator/health     -> GET 규칙 밖(익명 401)
```

## 5. 검증 결과

| 명령/실험 | 결과 | 증거 위치 |
| --- | --- | --- |
| 첫 P0-02B 좁은 실행 | compile FAILED: 삭제한 `LoggingConfig`를 `PasswordControllerTest`가 import | 아래 실패 기록 |
| 두 번째 좁은 실행 | 8 total / 5 passed / 3 failed | 로그 캡처 2건과 prometheus 404 |
| 세 번째 좁은 실행 | 11 total / 9 passed / 2 failed | Spring MVC DEBUG의 password/token/code 재노출을 root appender가 검출 |
| 최종 P0-02B 좁은 실행 | SUCCESS: 11/11 passed, 0 failed/skipped, Gradle 5초 | `build/test-results/test` 로컬 artifact |
| 변경 영향 서비스 + P0-02B 실행 | SUCCESS: 15/15 passed, 0 failed/skipped, Gradle 5초 | `build/test-results/test` 로컬 artifact |
| `./gradlew test --console=plain` | SUCCESS: 194/194 passed, 0 failed/skipped, Gradle 15초 | `build/test-results/test` 로컬 artifact와 이 문서에 결과 전사 |
| 커밋 전 `./gradlew cleanTest test --console=plain` | SUCCESS: 194/194 passed, 0 failed/skipped, Gradle 17초 | 2026-09-05 재실행한 `build/test-results/test` XML |
| 위험 문자열 정적 검색 | body/query/full payload/MDN/wildcard Actuator/debug true 일치 항목 없음 | 실행한 `rg` pattern과 이 문서에 결과 전사 |
| `git diff --check` | SUCCESS: whitespace error 없음 | 로컬 실행 결과 |

- Before: 직전 전체 183/183 green이었지만 secret/payload 로깅과 광범위 Actuator 공개를 검증하지 않았다.
- After: 새 11건을 포함한 전체 194건이 green이며 대표 인증 요청의 secret 비기록과 관리 endpoint allowlist를 실행 검증한다.
- 실행하지 못한 검증: 실제 reverse proxy/access log, 배포 환경 네트워크 ACL, Prometheus scrape, 컨테이너 기동, 원격 GitHub Actions는 확인하지 않았다.

## 6. 실패 사례와 남은 위험

- 커밋 전 첫 재검증은 sandbox가 사용자 Gradle cache의 `.lck` 파일을 열지 못해 코드 실행 전 실패했다. 같은 명령을 cache 접근 권한이 있는 환경에서 다시 실행해 194건을 실제 재검증했다.
- 삭제된 logging bean을 `PasswordControllerTest`가 직접 import해 첫 실행이 compile 실패했다. body advice 제거에 맞춰 불필요한 test import를 제거했다.
- 초기 `OutputCaptureExtension`은 기대한 SLF4J 메시지를 안정적으로 잡지 못했다. Logback `ListAppender`를 대상/root logger에 직접 연결했다.
- 테스트에서 prometheus가 404여서 endpoint와 registry export를 명시적으로 활성화했다. profile YAML은 별도 parameterized test로 검증한다.
- 자체 body advice를 제거한 뒤에도 Spring MVC DEBUG가 DTO `toString()`으로 secret을 출력했다. dev/prod Security debug를 끄고 Spring Web/Security 로그를 INFO로 제한해 같은 root logger test를 통과시켰다.
- SSE token은 여전히 URL query에 존재한다. 애플리케이션 로그에서는 제거됐지만 browser history, reverse proxy, APM에는 남을 수 있어 P0-02D에서 전달 방식을 바꿔야 한다.
- `/actuator/prometheus`는 애플리케이션 수준에서 익명 접근 가능하다. 운영 배포에서는 management port/network allowlist 또는 scraper 인증이 필요하다.
- 예외 메시지·새 dependency·개발자의 직접 logging으로 secret이 재도입될 수 있다. 대표 경로 test와 code review 규칙을 유지해야 한다.
- GPS event time과 count는 남겨 운영 추적성을 보존했다. 이 metadata의 보존 기간과 접근 권한은 아직 정하지 않았다.
- Rabbit consumer의 MDC는 처리 종료 후 clear되지 않아 thread 재사용 시 traceId가 섞일 위험이 있다. 관측성 변경에서 수정해야 한다.

## 7. 학습 기록

- 공부할 개념: data minimization, structured logging, log level inheritance, Spring MVC message converter logging, defense in depth, Actuator exposure와 authorization 차이
- 코드에서 확인할 위치: `LoggingFilter`, `SensitiveLoggingIntegrationTest`, `ActuatorAuthorizationPolicy`, `ActuatorApiSecurityTest`, dev/prod management 설정
- 스스로 설명해 볼 질문: 마스킹보다 미수집이 더 안전한 이유와, 그로 인해 잃는 운영 정보는 무엇인가?
- 실습: 새 password 필드를 DTO에 추가했다고 가정하고 annotation 방식과 root logger negative test 방식 중 어떤 것이 기본 실패 방향(fail-safe)인지 비교한다.

## 8. 예상 면접 질문

1. 모든 request/response body logging을 제거한 이유는 무엇인가?
   - 답변 핵심: annotation 누락은 fail-open이며 password/token/location은 정상 운영에 불필요하다. method/path/status/trace/metric으로 관측성을 유지하고 필요한 business event만 allowlist한다.
2. 자체 logging advice를 지웠는데도 비밀번호가 왜 로그에 남았는가?
   - 답변 핵심: Spring MVC DEBUG의 message converter가 DTO `toString()`을 출력했다. root logger test로 발견하고 profile의 framework log level을 INFO로 제한했다.
3. Actuator exposure와 Spring Security authorization은 어떻게 다른가?
   - 답변 핵심: exposure는 endpoint bean 자체를 web에 만들지 결정하고 authorization은 만들어진 경로의 호출자를 통제한다. 두 층을 함께 제한한다.
4. `/actuator/prometheus` 익명 허용이 완전히 안전한가?
   - 답변 핵심: 아니다. 필요한 scraper 계약 때문에 앱에서 허용했지만 management network/auth가 없으면 metric 정보가 외부에 노출될 수 있으며 배포 경계 검증이 남아 있다.

## 9. AI 활용과 사람의 검증

- AI에게 맡긴 범위: 민감 로그 sink 검색, 대안 비교, 코드·테스트·문서 초안, 반복 실행과 실패 분석
- AI가 제안한 대안: annotation masking 유지, 문자열 치환, body/query 미수집, Actuator Security-only 제한, exposure+exact matcher 이중 제한
- 채택/거절과 이유: 미수집과 이중 제한을 채택했다. 누락 시 secret이 노출되는 annotation/string 방식은 거절했다.
- 사람이 직접 확인할 실행 흐름: 로그인/비밀번호 변경 root log, query 제거, Actuator 200/401/404 matrix, telemetry metadata logging
- 자동화 검증: P0-02B 11건, 영향 범위 15건, 전체 194건, profile YAML, 위험 문자열·disabled·whitespace 검색
- AI가 확인하지 못한 사항: proxy/APM 실제 log, 운영 network, 실제 Prometheus scrape, log retention/IAM, 원격 CI
