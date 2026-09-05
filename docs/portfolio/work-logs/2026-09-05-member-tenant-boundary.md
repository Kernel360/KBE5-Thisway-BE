# CHANGE-006: Member tenant repository 경계

## 메타데이터

- 날짜: 2026-09-05 KST
- 작업자: Shin Dong Jun + AI assistant
- 브랜치/기준 커밋: `codex/portfolio-foundation`, base `0133c37`
- 관련 issue/PR: 없음
- 상태: Verified (`P0-02C Member` 단위, 이후 Vehicle·TripLog·Emulator도 별도 기록으로 완료)

## 1. 문제와 근거

- `CompanyChefMemberService.getActiveMember`는 `findByIdAndActiveTrue(id)`로 전역 Member row를 먼저 읽은 뒤 service에서 인증 회사와 비교했다.
- 이 구조는 새 호출 경로에서 사후 검사를 빼먹기 쉽고, 다른 tenant row를 persistence context까지 가져온 뒤 거부한다.
- 목록·요약 query는 이미 companyId 조건을 사용했지만 상세 조회·수정·삭제가 공유하는 단건 조회는 tenant predicate가 없었다.
- 원 팀 구현은 service 사후 비교로 cross-tenant 접근을 403 차단했다. 이번 변경은 이후 개인 현대화로 repository 경계와 실제 API negative test를 추가한 것이다.

## 2. Acceptance criteria

- [x] 단건 Member 조회는 인증 principal의 `companyId`와 path의 `memberId`를 repository query에서 함께 제한한다.
- [x] 같은 회사의 허용된 Member 상세 조회는 성공한다.
- [x] 다른 회사의 Member 조회·수정·삭제는 동일한 404/code `12000`으로 실패한다.
- [x] cross-tenant PUT/DELETE 이후 대상 Member의 name/email/active는 바뀌지 않는다.
- [x] 같은 회사의 시스템 `ADMIN`은 기존처럼 role 검사에서 403을 반환한다.
- [x] repository/service/API 경계의 허용·거부 테스트와 전체 회귀 테스트를 통과한다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| 기존처럼 전역 ID 조회 후 service company 비교 | 변경이 작고 403 구분 가능 | 다른 tenant row를 먼저 로드, 새 경로에서 검사 누락 가능 | 거절 |
| `id + companyId` repository query 후 404 | row를 application으로 가져오지 않고 존재 여부 노출 축소 | 기존 `MEMBER_NOT_FOUND`의 400→404 계약 변경 | 채택 |
| Hibernate filter/global tenant interceptor | 반복 predicate 감소 | 현재 규모에 과도하고 우회·관리 복잡성 증가 | 보류 |

선택 이유: tenant ownership은 role과 별도 불변식이며 가장 안쪽 데이터 접근 경계에서도 강제해야 한다. 다른 tenant와 실제 없는 ID를 동일한 404로 처리하되, 같은 tenant의 금지 role은 기존 403을 유지했다. `MEMBER_NOT_FOUND`의 기존 400은 HTTP 의미가 맞지 않아 404로 교정하고 Password API 계약 테스트도 함께 갱신했다.

## 4. 구현과 실행 흐름

- 변경 파일: `MemberRepository`, `CompanyChefMemberService`, `ErrorCode`, Member service/controller 통합 테스트
- 정상 흐름:

```text
Bearer JWT
  -> JwtAuthenticationFilter가 roles/companyId 검증
  -> MemberDetails(companyId) 생성
  -> CompanyChefMemberController
  -> CompanyChefMemberService.getActiveMember(memberId)
  -> MemberRepository.findByIdAndCompanyIdAndActiveTrue(memberId, companyId)
  -> 허용 role 확인
  -> 조회 응답 또는 update/delete transaction commit
```

- cross-tenant 흐름:

```text
company A JWT + company B memberId
  -> repository WHERE id = B.id AND company_id = A.id AND active = true
  -> empty
  -> MEMBER_NOT_FOUND
  -> HTTP 404/code 12000
  -> update/delete mutation 없음, transaction commit 대상 없음
```

- transaction 및 실패 경계: service의 class-level transaction 안에서 scoped query와 mutation이 수행된다. cross-tenant는 entity를 반환받기 전에 예외가 발생한다.

## 5. 검증 결과

| 명령/실험 | 결과 | 증거 위치 |
| --- | --- | --- |
| 첫 Member 좁은 실행 | FAILED: 27 tests / 22 passed / 5 failed | 기존 test의 principal stub 누락 3건, 기대값 교체 오류 2건 |
| 수정 후 Member 좁은 실행 | SUCCESS: 27/27 passed, 0 failed/skipped, Gradle 6초 | `build/test-results/test` 로컬 artifact |
| P0-02C 관련 묶음 실행 | SUCCESS: 76/76 passed, 0 failed/skipped, Gradle 8초 | 네 도메인 선택 test XML |
| 최종 전체 `./gradlew cleanTest test --console=plain` | SUCCESS: 218/218 passed, 0 failed/skipped, Gradle 18초 | 재실행한 `build/test-results/test` XML |

- Before: cross-tenant 요청은 service가 다른 회사 Member entity를 ID로 조회한 후 companyId를 비교했다.
- After: Member 새 7건과 P0-02C 전체 신규 test를 포함해 전체 218건이 green이다. H2 기반 실행 검증에서 repository가 다른 companyId에 empty를 반환하고, 실제 JWT API의 GET·PUT·DELETE가 404이며 데이터가 불변이다.
- 실행하지 못한 검증: MySQL 실제 query plan/index, 원격 GitHub Actions, reverse proxy 응답 변환은 아직 확인하지 않았다.

## 6. 실패 사례와 남은 위험

- query 순서를 principal-first로 바꾸자 기존 “없는 ID” service test 3건은 `SecurityService` stub이 없어 NPE가 났다. 인증된 use case라는 전제를 fixture에 명시해 수정했다.
- 첫 기대값 수정에서 같은 tenant `ADMIN`과 cross-tenant의 예상 code를 잘못 교체해 2건이 실패했다. ownership miss는 404, ownership 통과 후 role miss는 403으로 다시 고정했다.
- `MEMBER_NOT_FOUND`를 404로 바꿔 password lookup 등 동일 error code를 쓰는 API의 HTTP status도 변했다. 관련 controller test는 갱신했지만 FE 오류 분기 영향은 아직 확인하지 않았다.
- derived query는 H2에서 검증했다. 실제 MySQL 성능에는 `(company_id, id, active)` index 검토가 필요하지만 PK `id` 단건 조회 특성상 측정 없이 index를 추가하지 않았다.
- Vehicle·TripLog·Emulator는 `CHANGE-007~009`에서 완료했다. query-token SSE는 인증 계약이 달라 P0-02D에 남아 있다.

## 7. 학습 기록

- 공부할 개념: IDOR/BOLA, tenant predicate, fail-closed, 403과 404의 정보 노출 차이, Spring Data derived query, transaction persistence context
- 코드에서 확인할 위치: `CompanyChefMemberService.getActiveMember`, `MemberRepository`, `CompanyChefMemberTenantIntegrationTest`
- 스스로 설명해 볼 질문: role 검사를 통과한 COMPANY_CHEF가 왜 다른 company의 Member에는 접근할 수 없는가?
- 실습: 통합 테스트의 scoped repository method를 다시 전역 ID 조회로 바꾸었을 때 어떤 테스트가 실패하는지 확인하고 원복한다.

## 8. 예상 면접 질문

1. service에서 companyId를 비교하는 것만으로 부족한 이유는 무엇인가?
   - 답변 핵심: 다른 tenant row를 먼저 로드하고, 새 경로의 검사 누락 가능성이 있으며, repository predicate가 더 안쪽 방어선이다.
2. cross-tenant를 404로 응답한 이유는 무엇인가?
   - 답변 핵심: 실제 존재 여부와 소유 tenant를 숨기고 nonexistent와 동일한 공개 계약을 만든다.
3. 같은 회사의 ADMIN은 왜 404가 아니라 403인가?
   - 답변 핵심: tenant ownership query는 통과했지만 회사 담당자가 관리할 수 없는 system role이므로 role 실패를 구분한다.
4. API negative test에서 DB 불변성까지 확인한 이유는 무엇인가?
   - 답변 핵심: 우연한 4xx가 아니라 mutation 이전에 차단되어 실제 데이터가 보호됐음을 검증한다.

## 9. AI 활용과 사람의 검증

- AI에게 맡긴 범위: 호출 경로 감사, 403/404 대안 비교, 코드·테스트·문서 초안, 반복 테스트와 실패 분석
- AI가 제안한 대안: 사후 비교 유지, scoped query+403, scoped query+404, global tenant filter
- 채택/거절과 이유: 작은 범위에서 명시적인 scoped query+404를 채택했다. 사후 비교는 방어선이 늦고 global filter는 현재 구조에 과도해 거절/보류했다.
- 사람이 직접 확인할 실행 흐름: JWT companyId가 repository parameter가 되는 과정과 cross-tenant PUT/DELETE 후 DB 불변성
- 자동화 검증: repository 직접 assertion, service 허용/거부, 실제 JWT MockMvc GET/PUT/DELETE, 전체 Gradle test
- AI가 확인하지 못한 사항: 실제 운영 데이터 분포, MySQL 실행 계획, FE의 400→404 분기, 원격 CI 결과
