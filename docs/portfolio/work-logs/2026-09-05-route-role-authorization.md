# CHANGE-004: 실제 API 경로 기반 role 인가 계약

## 메타데이터

- 날짜: 2026-09-05 KST
- 작업자: Shin Dong Jun + AI assistant
- 브랜치/기준 커밋: `codex/portfolio-foundation`, base `develop@98bff23`
- 관련 issue/PR: 없음
- 상태: Verified

## 1. 문제와 근거

- 기존 `AdminApiSecurityTest`와 `CompanyChefApiSecurityTest`는 실제 Controller가 없는 `/api/admin/test`, `/api/company-chef/test`를 요청했다.
- 허용 테스트의 기대값이 `404 Not Found`였으므로 해당 role이 실제 업무 Controller까지 도달하는지 증명하지 못했다.
- 비허용 role 테스트 2건과 JWT 성공 테스트 1건은 `@Disabled`였다.
- `MemberAuthorizationPolicy`의 회사 회원 삭제 pattern은 `/api/company-chef/members`였지만 실제 Controller와 FE 호출은 `/api/company-chef/members/{id}`였다. 이 때문에 실제 DELETE 요청은 구체 규칙에 매칭되지 않고 `anyRequest().authenticated()`로 내려가 모든 인증 role에 허용될 수 있었다.
- `POST /api/statistics/save`도 GET 전용 통계 규칙에 매칭되지 않아 모든 인증 사용자에게 허용될 수 있었다. 이 API는 요청자가 임의 `companyId`를 지정할 수 있다.
- 이번 변경은 원 팀의 URL 인가 정책을 이후 개인 현대화에서 교정하고 회귀 테스트를 추가한 것이다. tenant별 repository 조회까지 개선한 변경은 아니다.

## 2. Acceptance criteria

- [x] 존재하지 않는 테스트용 URL 대신 실제 회원 삭제 Controller 경로를 검증한다.
- [x] 허용 role은 Controller/service까지 도달하고 비허용 role은 403에서 차단된다.
- [x] 무인증·변조 JWT는 401이며, 정상 JWT는 `roles`와 `companyId` claim을 거쳐 보호 API에 도달한다.
- [x] 회사 회원 삭제 path와 policy pattern을 일치시킨다.
- [x] 임의 `companyId`를 받는 수동 통계 저장 API는 시스템 `ADMIN`만 허용한다.
- [x] 저장소의 `@Disabled` 테스트를 0개로 만들고 전체 회귀 테스트를 통과시킨다.
- [ ] cross-tenant resource 접근은 별도 P0-02 변경에서 repository/service/API negative test로 차단한다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| 없는 URL의 404를 허용 성공으로 유지 | service mock 불필요, 작성이 간단 | 실제 matcher와 Controller 계약을 검증하지 못함 | 거절 |
| 실제 DELETE 경로 + service mock | Security filter 통과 여부와 Controller 도달을 구분 | full context 비용, service 내부 tenant 경계는 미검증 | 채택 |
| 통계 저장을 회사 role에 허용하고 principal companyId로 대체 | tenant self-service 가능 | 수동 재집계 권한·운영 요구가 정의되지 않음 | 보류 |
| 통계 저장을 시스템 ADMIN 전용으로 제한 | 임의 tenant 재집계 노출을 즉시 축소 | 운영자 인증과 audit은 아직 없음 | 채택 |
| 통계 저장 HTTP endpoint 삭제 | 공격면 최소 | 수동 운영 경로 사용 여부를 추가 확인해야 함 | 후속 검토 |

선택 이유: 이번 단계는 확인된 matcher 우회와 disabled 테스트를 작게 닫는 containment다. 인증된 회사 사용자가 임의 회사의 통계를 저장할 이유가 확인되지 않았고 Batch는 HTTP가 아니라 `StatisticService`를 직접 호출하므로, 수동 endpoint를 ADMIN으로 제한했다.

## 4. 구현과 실행 흐름

변경 파일:

- `MemberAuthorizationPolicy`: 실제 회사 회원 삭제 URL인 `/api/company-chef/members/{id}`로 수정
- `StatisticsAuthorizationPolicy`: `POST /api/statistics/save`를 `ADMIN` 전용으로 명시
- `AdminApiSecurityTest`: 실제 관리자 회원 삭제 API의 허용·거부 검증
- `CompanyChefApiSecurityTest`: 실제 회사 회원 삭제 API의 허용·거부 검증
- `SecurityIntegrationTest`: 실제 서명 JWT의 무인증·정상·변조 흐름 검증
- `StatisticsApiSecurityTest`: 통계 저장 API의 ADMIN·회사 role·무인증 matrix 검증

인가 흐름:

```text
HTTP request
  -> JwtAuthenticationFilter 또는 @WithMockUser가 Authentication 구성
  -> SecurityFilterChain
  -> method + 실제 path에 맞는 EndpointRule 선택
  -> hasAnyRole 검사
       -> 실패: 401(미인증) 또는 403(권한 부족), service 미호출
       -> 성공: Controller -> mocked application service -> HTTP 응답
```

역할 계약:

| API | 허용 | 차단 증거 |
| --- | --- | --- |
| `DELETE /api/admin/members/{id}` | `ADMIN` | `COMPANY_CHEF`, `COMPANY_ADMIN`, `MEMBER` 조합 403 |
| `DELETE /api/company-chef/members/{id}` | `COMPANY_CHEF` | `COMPANY_ADMIN`, `MEMBER` 조합 403 |
| `POST /api/statistics/save` | `ADMIN` | 회사 role 403, 미인증 401 |

## 5. 검증 결과

| 명령/실험 | 결과 | 증거 위치 |
| --- | --- | --- |
| 변경 전 Security 3개 클래스 좁은 실행 | SUCCESS: 9 total / 6 passed / 0 failed / 3 skipped, Gradle 5초 | 변경 전 XML 결과를 이 문서에 전사 |
| 첫 통계 Security 포함 좁은 실행 | FAILED: 12 total / 9 passed / 3 failed | 아래 실패 기록 |
| 수정 후 Security 4개 클래스 좁은 실행 | SUCCESS: 12/12 passed, 0 failed, 0 skipped, Gradle 6초 | `build/test-results/test` 로컬 artifact |
| `./gradlew test --console=plain` | SUCCESS: 183/183 passed, 0 failed, 0 skipped, Gradle 16초 | `build/test-results/test` 로컬 artifact와 이 문서에 결과 전사 |
| `rg -n "@Disabled" src/test/java` | 일치 항목 없음 | 로컬 실행 결과 |
| `git diff --check` | SUCCESS: whitespace error 없음 | 로컬 실행 결과 |

- Before: 전체 180개 중 177 passed, 3 skipped였고 실제 회사 회원 DELETE와 통계 저장 role 경계를 검증하지 못했다.
- After: 새 통계 보안 테스트 3건을 포함한 전체 183개가 모두 통과하며 skipped가 0이다.
- 실행하지 못한 검증: 원격 GitHub Actions, 실제 MySQL 기반 tenant data integration, reverse proxy/API Gateway 경로 정책은 확인하지 않았다.

## 6. 실패 사례와 남은 위험

- 첫 `StatisticsApiSecurityTest` 실행에서 constructor parameter에 Spring test 주입 설정이 없어 `ParameterResolutionException` 3건이 발생했다. `MockMvc`를 `@Autowired` field injection으로 바꾼 뒤 같은 테스트가 통과했다.
- role 검사는 작업 종류만 제한한다. `CompanyChefMemberService`는 조회 후 companyId를 비교하지만 repository가 먼저 전역 ID로 row를 가져오므로, 다음 단계에서 `companyId + memberId` query와 cross-tenant negative test를 검토해야 한다.
- TripLog, Vehicle, Emulator, SSE의 tenant ownership은 이번 변경에서 수정하지 않았다.
- `POST /api/statistics/save`는 ADMIN으로 제한했지만 운영자 인증 강화, 실행 audit, 중복 실행 방지는 아직 없다. endpoint 자체가 필요한지도 확인해야 한다.
- 테스트의 application service는 mock이므로 실제 삭제 transaction과 통계 저장 결과를 검증한 것이 아니다.
- JWT에서 첫 번째 role을 domain role로 선택하면서 authorities에는 여러 role을 모두 추가한다. multi-role token 발급 정책은 별도로 단일 role 불변식을 명확히 해야 한다.

## 7. 학습 기록

- 공부할 개념: authentication과 authorization, RBAC, request matcher 우선순위, default rule, 401과 403, IDOR와 resource ownership
- 코드에서 확인할 위치: `SecurityConfig`, `EndpointRule`, `MemberAuthorizationPolicy`, `StatisticsAuthorizationPolicy`, 네 Security test
- 스스로 설명해 볼 질문: 왜 “인증된 요청”과 “해당 업무를 수행할 권한이 있는 요청”은 다른가?
- 실습: 실제 Controller mapping과 Security pattern을 표로 대조하고, pattern이 하나 틀렸을 때 `anyRequest().authenticated()`로 내려가는 과정을 손으로 추적한다.

## 8. 예상 면접 질문

1. 404를 기대한 기존 보안 테스트가 왜 충분하지 않았는가?
   - 답변 핵심: 없는 URL은 실제 업무 matcher에 매칭되지 않아 role 정책을 우회한 채 인증 여부만 검사할 수 있다. 실제 경로와 service 도달 여부가 필요하다.
2. 인증된 사용자가 왜 회사 회원 삭제 API를 호출할 수 있었는가?
   - 답변 핵심: DELETE pattern 불일치로 구체 role 규칙이 적용되지 않고 마지막 `anyRequest().authenticated()` 규칙이 허용했기 때문이다.
3. 통계 저장 API를 ADMIN으로 제한한 이유는 무엇인가?
   - 답변 핵심: 요청값으로 임의 companyId를 받으며 Batch 내부 호출에는 HTTP가 필요 없다. 요구사항이 없는 tenant self-service보다 공격면 축소를 우선했다.
4. role 테스트가 통과하면 멀티테넌트 보안도 보장되는가?
   - 답변 핵심: 아니다. role은 작업 종류, ownership은 특정 resource 소유권이다. 인증 principal의 companyId를 repository predicate에 포함한 negative test가 별도로 필요하다.

## 9. AI 활용과 사람의 검증

- AI에게 맡긴 범위: 기존 matcher와 실제 Controller/FE 경로 대조, 테스트·정책·문서 초안, 자동 검증 실행
- AI가 제안한 대안: 없는 URL test 유지, 실제 route test, 통계 endpoint 삭제, ADMIN 제한, tenant self-service 전환
- 채택/거절과 이유: 실제 route test와 ADMIN containment를 채택했다. endpoint 삭제와 tenant self-service는 운영 요구 확인 없이 계약을 크게 바꾸므로 보류했다.
- 사람이 직접 확인할 실행 흐름: path matcher -> role 검사 -> Controller -> service 호출과 401/403 차이
- 자동화 검증: Security 좁은 12건, 전체 183건, disabled 검색, whitespace 검사
- AI가 확인하지 못한 사항: 배포 환경의 upstream 경로 정책, 운영상 수동 통계 저장 endpoint 사용 여부, 실제 cross-tenant 공격 재현, 원격 CI 결과
