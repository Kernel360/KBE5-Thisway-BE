# CHANGE-009: Emulator CRUD와 연결 Vehicle tenant 경계

## 메타데이터

- 날짜: 2026-09-05 KST
- 작업자: Shin Dong Jun + AI assistant
- 브랜치/기준 커밋: `codex/portfolio-foundation`, base `0133c37` + uncommitted `CHANGE-006~008`
- 관련 issue/PR: 없음
- 상태: Verified (`P0-02C Emulator` 사용자 CRUD 단위, MDN device 경로는 P0-04)

## 1. 문제와 근거

- Emulator 상세·목록·수정·삭제는 `findById/findAll` 전역 repository를 사용했다.
- 등록과 수정의 연결 Vehicle도 company 조건 없는 `findByIdAndActiveTrue(vehicleId)`로 조회했다.
- 따라서 유효한 COMPANY_ADMIN/CHEF가 다른 회사 Emulator를 열람·변경·삭제하거나 다른 회사 Vehicle에 Emulator를 연결할 수 있었다.
- MDN으로 VehicleReference를 찾는 telemetry 메서드는 사용자 CRUD가 아닌 device trust boundary다.
- 이번 변경은 원 팀 CRUD에 이후 개인 현대화로 principal company 조건과 negative test를 추가했다.

## 2. Acceptance criteria

- [x] Emulator 단건·목록은 `emulator.vehicle.company.id`로 제한한다.
- [x] update/delete는 현재 회사 Emulator만 대상으로 한다.
- [x] register와 vehicle 재연결은 현재 회사의 active Vehicle만 허용한다.
- [x] 다른 회사 CRUD는 404이며 필드/row가 불변이다.
- [x] 목록에는 다른 회사 Emulator가 포함되지 않는다.
- [x] 다른 회사 Vehicle 등록/재연결은 404이고 관계가 바뀌지 않는다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| Emulator만 scoped, vehicleId는 전역 조회 | 변경 작음 | association을 통한 tenant 침범 가능 | 거절 |
| DTO에 companyId를 받아 비교 | 구현 쉬움 | 공격자가 바꿀 수 있는 입력을 신뢰 | 거절 |
| principal company로 Emulator와 Vehicle 모두 scope | 생성·변경 전 구간 보호 | 반복 query method | 채택 |

선택 이유: aggregate 자체뿐 아니라 새로 연결할 연관 자원도 ownership을 확인해야 tenant 불변식이 유지된다. companyId는 request가 아니라 인증 principal에서만 가져온다.

## 4. 구현과 실행 흐름

```text
JWT companyId
  -> list: findAllByVehicleCompanyId(companyId)
  -> detail/update/delete: findByIdAndVehicleCompanyId(id, companyId)
  -> register/relink vehicle: findByIdAndCompanyIdAndActiveTrue(vehicleId, companyId)
  -> scoped miss 404
  -> 성공한 경우에만 save/update/delete
```

- MDN 기반 `findVehicleByMdn`은 telemetry device 흐름 보존을 위해 변경하지 않았다.

## 5. 검증 결과

| 명령/실험 | 결과 | 증거 위치 |
| --- | --- | --- |
| Emulator 좁은 실행 | SUCCESS: 7/7 passed, 0 failed/skipped, Gradle 5초 | 실제 JWT integration XML |
| P0-02C 관련 묶음 | SUCCESS: 76/76 passed, 0 failed/skipped, Gradle 8초 | 선택 test XML |
| 전체 회귀 | SUCCESS: 218/218 passed, 0 failed/skipped, Gradle 18초 | 전체 test XML |

- Before: 모든 회사 Emulator list/ID와 모든 active Vehicle ID를 전역 조회했다.
- After: 두 회사 실제 row로 목록·CRUD·등록·재연결 tenant 차단과 DB 불변을 검증했다.
- 미검증: device credential, MySQL plan/index, FE 400→404 처리, 원격 CI

## 6. 실패 사례와 남은 위험

- 구현/좁은/전체 테스트에서 실패는 없었다.
- `EMULATOR_NOT_FOUND`를 HTTP 400에서 404로 교정했다.
- MDN은 global unique라 다른 회사 MDN 중복 여부를 추측할 여지가 있다. device identifier 공개 정책과 generic conflict 응답을 별도 검토한다.
- `getVehicleReferenceByMdn`은 public telemetry 경로에서 쓰이며 device authentication/replay 방지가 아직 없다.
- Emulator는 BaseEntity soft delete가 아닌 hard delete다. 감사 이력/복구 요구는 정의되지 않았다.

## 7. 학습 기록

- 공부할 개념: association ownership, confused deputy, principal-derived tenant, hard delete auditability
- 코드 위치: `EmulatorService`, `EmulatorRepository`, `EmulatorTenantIntegrationTest`
- 실습 질문: 자기 회사 Emulator를 다른 회사 Vehicle에 연결하는 것도 왜 IDOR인가?

## 8. 예상 면접 질문

1. Emulator에 companyId가 없는데 어떻게 격리했는가?
   - 답변 핵심: 소유 Vehicle의 companyId를 연관 predicate로 사용.
2. 수정 대상만 확인하면 충분한가?
   - 답변 핵심: 새 vehicleId도 같은 tenant인지 재검증해야 association 침범 방지.
3. request companyId를 받지 않은 이유는?
   - 답변 핵심: 변경 가능한 입력 대신 검증된 JWT principal을 신뢰.
4. MDN 조회를 왜 남겼는가?
   - 답변 핵심: device 흐름은 별도 credential/replay 모델이 필요하며 사용자 principal을 억지로 적용하지 않음.

## 9. AI 활용과 사람의 검증

- AI 범위: 전역 query 탐색, association 공격 시나리오, 구현·통합 테스트·문서 초안
- 대안: 대상만 scope, DTO companyId, 대상과 연관 Vehicle 모두 scope
- 판단: association까지 scope하는 방식을 채택했다.
- 사람이 확인할 흐름: principal companyId가 Emulator/Vehicle 두 query에 전달되는 과정
- 자동화: 목록 격리, repository assertion, cross GET/PATCH/DELETE, foreign vehicle register/relink, 전체 218 tests
- 미확인: device 인증, 운영 감사 요구, MySQL plan, FE, 원격 CI
