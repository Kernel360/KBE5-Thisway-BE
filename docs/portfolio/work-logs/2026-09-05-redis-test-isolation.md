# CHANGE-002: Redis 테스트 격리와 자동 기동

## 메타데이터

- 날짜: 2026-09-05 KST
- 작업자: Shin Dong Jun + AI assistant
- 브랜치/기준 커밋: `codex/portfolio-foundation`, base `develop@98bff23`
- 관련 issue/PR: 없음
- 상태: Verified

## 1. 문제와 근거

- 변경 전 `./gradlew test --console=plain`은 178개 중 4개가 실패했다.
- `RedisComponentTest` 3건과 `PasswordServiceTest` 1건이 수동 `localhost:6379` Redis에 의존했다.
- CI만 별도 Redis service를 선언해 로컬과 CI의 테스트 사전조건이 달랐다.
- `PasswordServiceTest`의 목적은 비밀번호 변경 use case인데 `@MockitoSpyBean` 때문에 실제 Redis I/O까지 수행했다.
- 원래 팀 구현의 테스트 경계를 이후 개인 현대화에서 분리하는 변경이다. Redis production component의 동작은 바꾸지 않는다.

## 2. Acceptance criteria

- [x] Redis 없이 수행할 service/unit test는 mock으로 외부 I/O를 격리한다.
- [x] 실제 Redis JSON round-trip과 TTL 만료는 Testcontainers로 검증한다.
- [x] 로컬과 CI 모두 같은 Gradle test task를 사용한다.
- [x] Docker가 없을 때 Redis integration test를 성공으로 위장하거나 skip하지 않는다.
- [x] 전체 test 결과와 남은 skipped 항목을 사실대로 기록한다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| README에 로컬 Redis 실행만 추가 | 변경이 작음 | 환경 오염과 CI/로컬 차이를 유지 | 거절 |
| 모든 Redis test를 mock으로 변경 | 빠르고 Docker 불필요 | 실제 serialization·TTL 계약을 잃음 | 거절 |
| unit/service는 mock, adapter는 Testcontainers | 빠른 실패와 실제 계약을 모두 검증 | Docker와 image pull 필요 | 채택 |

## 4. 구현과 실행 흐름

- `PasswordServiceTest`: `RedisComponent`를 mock으로 교체해 use case만 검증한다.
- `RedisComponentTest`: Redis client·ObjectMapper interaction과 예외 변환을 pure unit test로 검증한다.
- `RedisComponentIntegrationTest`: Testcontainers Redis에 JSON을 저장·조회하고 TTL 만료를 확인한다.
- CI의 수동 Redis service를 제거하고 로컬과 동일한 `test` task를 실행한다.

테스트 흐름:

```text
PasswordServiceTest
  -> mocked RedisComponent
  -> member/email/password use case 검증

RedisComponentIntegrationTest
  -> Testcontainers가 Redis 시작
  -> LettuceConnectionFactory
  -> StringRedisTemplate
  -> RedisComponent JSON 저장/조회/TTL 만료
```

## 5. 검증 결과

| 명령/실험 | 결과 | 증거 위치 |
| --- | --- | --- |
| 변경 전 `./gradlew test --console=plain` | FAILED: 178 total / 161 passed / 4 failed / 13 skipped | [`baseline-audit.md`](../baseline-audit.md) |
| 최초 좁은 test 14건 | FAILED: 3건 | 로컬 XML 결과를 아래 실패 기록에 전사 |
| 수정 후 Redis/Password 좁은 test | SUCCESS: 14 tests / 0 failed / 0 skipped, 6초 | `build/test-results/test` 로컬 artifact |
| 수정 후 `./gradlew test --console=plain` | SUCCESS: 180 total / 167 passed / 0 failed / 13 skipped, 14초 | `build/test-results/test` 로컬 artifact와 이 문서에 결과 전사 |
| `./gradlew dependencyInsight --dependency org.testcontainers:testcontainers --configuration testRuntimeClasspath --console=plain` | Spring Boot dependency management가 Testcontainers `1.20.6` 선택 | 이 문서에 결과 전사 |

- Before: service test와 adapter test 모두 `localhost:6379`라는 수동 상태에 결합됐다.
- After: service/unit test는 network 없이 실행되고, 실제 Redis test는 격리 container의 동적 port를 사용한다.
- 테스트 수가 2개 늘어난 이유: 기존 Redis test 5건을 책임이 명확한 unit test 6건으로 교체하고 실제 integration test 1건을 추가했다.
- P0-01 전체는 완료가 아니다. 기존 TripLog 10건과 Security 3건의 skipped 상태가 남아 있다.

## 6. 실패 사례와 남은 위험

- P0-01A 범위에서는 기존 13개 skipped TripLog/Security test를 다루지 않는다.
- 최초 좁은 실행에서 공통 `setUp()` stubbing을 사용하지 않는 test 2건이 Mockito `UnnecessaryStubbingException`으로 실패했다. 필요한 test 안으로 stubbing을 이동했다.
- 같은 실행에서 300ms TTL을 초 단위로 조회해 `0`이 반환됐다. millisecond 단위 조회로 수정하고 CI 지연에 덜 민감하도록 TTL을 1초로 늘렸다.
- `RedisComponent.delete()`가 모든 예외를 무시하는 production 정책은 유지했다. 비밀번호 변경에서 Redis 삭제 실패 시 인증 코드 재사용 가능성이 있으므로 별도 보안 변경에서 결정해야 한다.
- Testcontainers image tag의 장기 공급망 고정은 digest pinning과 dependency update 정책을 정할 때 다룬다.

## 7. 학습 기록

- 공부할 개념: test double, mock과 spy 차이, test pyramid, Testcontainers lifecycle, TTL
- 코드에서 확인할 위치: `PasswordServiceTest`, `RedisComponentTest`, `RedisComponentIntegrationTest`
- 실습: `@MockitoSpyBean`과 `@MockitoBean`이 `sendVerificationCode()` 실행 시 Redis 호출에 미치는 차이를 직접 설명한다.

## 8. 예상 면접 질문

1. Redis를 mock한 테스트와 실제 Redis 테스트를 왜 둘 다 두었는가?
   - 답변 핵심: 비즈니스 분기와 adapter 계약의 실패 원인·속도·책임을 분리
2. CI service container 대신 Testcontainers를 선택한 이유는?
   - 답변 핵심: 동일 명령, 격리된 lifecycle, 동적 port, 로컬·CI parity와 Docker 비용
3. Docker가 없을 때 integration test를 skip하지 않은 이유는?
   - 답변 핵심: 핵심 계약 미검증을 green build로 오인하지 않기 위해 명시적으로 실패

## 9. AI 활용과 사람의 검증

- AI에게 맡긴 범위: 실패 경계 분석, 대안 비교, test·CI·문서 초안
- 채택한 판단: unit/service mock과 실제 adapter integration 분리
- 거절한 판단: 외부 Redis 수동 기동과 전체 mock 전환
- 사람이 직접 확인할 실행 흐름: verification payload가 JSON과 TTL로 저장되고 만료되는 과정
- 자동화 검증: 좁은 Redis/Password test와 전체 Gradle test
- AI가 확인하지 못한 사항: GitHub Actions 원격 실행과 장기 image 공급망
