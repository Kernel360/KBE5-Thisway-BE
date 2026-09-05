# CHANGE-007: Vehicle 사용자 CRUD tenant 경계

## 메타데이터

- 날짜: 2026-09-05 KST
- 작업자: Shin Dong Jun + AI assistant
- 브랜치/기준 커밋: `codex/portfolio-foundation`, base `0133c37` + uncommitted `CHANGE-006`
- 관련 issue/PR: 없음
- 상태: Verified (`P0-02C Vehicle` 단위만 완료)

## 1. 문제와 근거

- `VehicleService.getAuthorizedVehicle`는 전역 `findByIdAndActiveTrue(id)`로 차량을 읽은 뒤 service에서 회사 일치를 확인했다.
- GET/PATCH/DELETE가 이 helper를 공유하므로 한 곳의 사후 비교가 경계였지만, 다른 tenant entity를 먼저 로드했다.
- `getVehicleById`와 `getVehiclePowerState`는 RabbitMQ/telemetry/SSE 내부 흐름에서도 호출되므로 웹 principal 기반 사용자 조회와 신뢰 경계가 다르다.
- 원 팀 구현의 role 및 사후 company 비교를, 이후 개인 현대화에서 사용자 CRUD의 repository tenant predicate와 통합 테스트로 보강했다.

## 2. Acceptance criteria

- [x] 사용자 차량 상세·수정·삭제는 `vehicleId + companyId + active` repository query를 사용한다.
- [x] 같은 회사 차량 상세 조회는 실제 JWT 요청으로 200이다.
- [x] 다른 회사 차량 GET/PATCH/DELETE는 404/code `14000`이다.
- [x] 차단된 PATCH/DELETE 이후 carNumber/color/active가 바뀌지 않는다.
- [x] repository 직접 assertion과 service/controller/실제 API 좁은 테스트가 통과한다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| service 사후 company 비교 유지 | 변경 최소 | 다른 tenant row 선조회, 검사 누락 위험 | 거절 |
| 모든 Vehicle 조회에 웹 principal 강제 | 표면상 일관됨 | 인증 principal 없는 device/background 흐름 파손 | 거절 |
| 사용자 `getAuthorizedVehicle`만 scoped query | 현재 API 공격면을 작게 차단 | internal reader 책임 분리는 남음 | 채택 |

선택 이유: 인증 주체가 있는 사용자 CRUD와 device/internal 처리를 구분했다. 현재 단계에서는 사용자 공격면을 repository에서 차단하고, internal 조회의 device authentication은 별도 P0-04 범위로 남겼다.

## 4. 구현과 실행 흐름

```text
COMPANY_ADMIN JWT
  -> SecurityService.getCurrentMember()로 활성 Member/Company 확인
  -> role 검사
  -> findByIdAndCompanyIdAndActiveTrue(vehicleId, member.company.id)
  -> 허용: response 또는 mutation
  -> empty: VEHICLE_NOT_FOUND -> HTTP 404/code 14000
```

- 변경 파일: `VehicleRepository`, `VehicleService`, `ErrorCode`, Vehicle unit/controller/API integration tests
- transaction 경계: scoped 조회와 PATCH/DELETE mutation은 동일 service transaction이다. cross-tenant는 entity 반환 전에 실패한다.

## 5. 검증 결과

| 명령/실험 | 결과 | 증거 위치 |
| --- | --- | --- |
| Vehicle 좁은 실행 | SUCCESS: 27/27 passed, 0 failed/skipped, Gradle 7초 | `build/test-results/test` 로컬 artifact |
| P0-02C 관련 묶음 실행 | SUCCESS: 76/76 passed, 0 failed/skipped, Gradle 8초 | 네 도메인 선택 test XML |
| 전체 회귀 | SUCCESS: 218/218 passed, 0 failed/skipped, Gradle 18초 | 재실행한 `build/test-results/test` XML |

- Before: 사용자 Vehicle entity를 전역 ID로 읽고 company를 비교했다.
- After: H2 repository와 실제 JWT API에서 다른 companyId가 empty/404이고 PATCH/DELETE 후 상태가 불변이다.
- 실행하지 못한 검증: MySQL query plan/index, device authentication, 원격 CI, FE 400→404 분기

## 6. 실패 사례와 남은 위험

- `VEHICLE_NOT_FOUND`를 HTTP 400에서 404로 교정해 기존 controller 계약을 갱신했다.
- controller test status 세 곳을 기계적으로 바꾸는 과정에서 `COMPANY_NOT_FOUND` 기대값까지 404로 잘못 바뀐 것을 diff review로 발견해 400으로 원복했다.
- `getVehicleById/getVehiclePowerState`는 internal 호출 때문에 이번 tenant principal 범위에서 제외했다. device 인증이 없는 현재 ingestion 문제는 P0-04에 남아 있다.
- GET security policy는 MEMBER를 허용하지만 service의 기존 `validateCompanyAdminPermission`은 일반 MEMBER를 거부할 가능성이 있다. 이번 tenant 변경과 분리해 role 계약을 별도 확인해야 한다.

## 7. 학습 기록

- 공부할 개념: application trust boundary, user principal vs device identity, BOLA, scoped repository, least privilege
- 코드에서 확인할 위치: `VehicleService.getAuthorizedVehicle`, `VehicleRepository`, `VehicleTenantIntegrationTest`
- 스스로 설명해 볼 질문: background consumer에 HTTP principal을 재사용하면 왜 잘못된 설계인가?

## 8. 예상 면접 질문

1. 사후 company 비교를 왜 query predicate로 옮겼는가?
   - 답변 핵심: 다른 tenant row 비로딩, 공통 방어선, 존재 정보 축소.
2. internal Vehicle 조회는 왜 그대로 두었는가?
   - 답변 핵심: 웹 사용자가 아닌 device/background 신뢰 경계이며 별도 인증과 port가 필요하다.
3. cross-tenant PATCH/DELETE에서 무엇을 검증했는가?
   - 답변 핵심: 실제 JWT 404뿐 아니라 원본 필드와 active 불변.
4. H2 통과로 무엇을 말할 수 없는가?
   - 답변 핵심: MySQL dialect, index 선택, lock/실행 계획과 운영 성능.

## 9. AI 활용과 사람의 검증

- AI에게 맡긴 범위: 경로 분류, scoped query 구현, test/document 초안, diff review
- AI가 제안한 대안: 전역 사후 검사, 전체 principal 강제, 사용자 helper만 scoped query
- 채택/거절과 이유: 사용자 helper만 변경해 현재 공격면을 줄이고 background 계약 파손을 피했다.
- 사람이 직접 확인할 실행 흐름: HTTP와 internal 호출자의 차이, JWT subject가 실제 Member 조회로 연결되는 과정
- 자동화 검증: repository assertion, unit mock argument, controller status, 실제 JWT GET/PATCH/DELETE와 DB 불변성
- AI가 확인하지 못한 사항: 운영 device identity, MySQL plan, FE status handling, 원격 CI
