# CHANGE-008: TripLog 일반 HTTP tenant 경계

## 메타데이터

- 날짜: 2026-09-05 KST
- 작업자: Shin Dong Jun + AI assistant
- 브랜치/기준 커밋: `codex/portfolio-foundation`, base `0133c37` + uncommitted `CHANGE-006/007`
- 관련 issue/PR: 없음
- 상태: Verified (`P0-02C TripLog` 일반 HTTP 단위, SSE는 P0-02D)

## 1. 문제와 근거

- 운행 목록은 principal companyId로 제한됐지만 `getTripLogDetails`는 `tripId`만으로 전역 조회했다.
- 현재 GPS는 권한 없는 internal `getVehiclePowerState(vehicleId)`를 사용해 다른 회사 차량 ID를 확인할 수 있었다.
- 차량 운행 요약은 Vehicle 권한 검사를 먼저 했지만 뒤의 최근 TripLog query에는 company predicate가 없었다.
- query-token SSE 두 경로는 별도 인증 계약이므로 이번 일반 HTTP 변경에 포함하지 않았다.
- 원 팀의 TripLog 조회를 이후 개인 현대화에서 연관 Vehicle의 company predicate와 실제 JWT negative test로 보강했다.

## 2. Acceptance criteria

- [x] 차량 운행 요약은 Vehicle 권한 검사와 `vehicleId + vehicle.companyId` 최근 운행 query를 사용한다.
- [x] 현재 GPS는 scoped `getVehicleDetail`로 차량 소유권을 먼저 검증한다.
- [x] 운행 상세는 `tripId + vehicle.companyId + active` query를 사용한다.
- [x] 다른 회사 차량 요약·현재 GPS와 다른 회사 운행 상세는 404이고 LogService를 호출하지 않는다.
- [x] 같은 회사 운행 상세는 실제 JWT로 200이다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| controller에서 company 비교 | 눈에 보임 | 다른 호출자가 우회, persistence 경계 밖 | 거절 |
| 일반 HTTP와 SSE를 한 번에 변경 | 표면상 일괄 완료 | query token/resource binding 설계가 반쪽 변경 | 거절 |
| principal HTTP만 scoped query, SSE 별도 단계 | 작은 검증 단위와 명확한 계약 | P0-02D까지 SSE 위험 잔존 | 채택 |

선택 이유: 인증 방식이 다른 HTTP principal과 SSE query token을 분리했다. 일반 API는 지금 fail-closed로 만들고 SSE는 token 전달과 subscription lifecycle까지 한 단위로 다룬다.

## 4. 구현과 실행 흐름

```text
JWT companyId
  -> vehicle summary/current: VehicleService.getVehicleDetail(vehicleId)
     -> vehicleId + companyId scoped query
  -> trip detail: findByIdAndVehicleCompanyIdAndActiveTrue(tripId, companyId)
  -> ownership 성공 뒤에만 GPS LogService 호출
  -> scoped miss는 404
```

- `getVehicleDetails`의 중복 `getVehicleDetail` 호출도 한 번으로 줄였다.
- cross-tenant 예외는 GPS 조회 전에 발생하므로 외부 log store query를 실행하지 않는다.

## 5. 검증 결과

| 명령/실험 | 결과 | 증거 위치 |
| --- | --- | --- |
| TripLog 좁은 실행 | SUCCESS: 15/15 passed, 0 failed/skipped, Gradle 6초 | service + 실제 API integration XML |
| P0-02C 관련 묶음 | SUCCESS: 76/76 passed, 0 failed/skipped, Gradle 8초 | 선택 test XML |
| 전체 회귀 | SUCCESS: 218/218 passed, 0 failed/skipped, Gradle 18초 | 전체 test XML |

- Before: current/detail 일부는 Vehicle 또는 TripLog ID만 신뢰했다.
- After: 일반 JWT API의 세 cross-tenant 경로가 404이며 LogService interaction 0건이다.
- 미검증: query-token SSE, MySQL plan/index, 실제 GPS 저장소, 원격 CI

## 6. 실패 사례와 남은 위험

- 구현/좁은/전체 테스트에서 실패는 없었다.
- `getGpsLogsInTripLog`와 `getLastStartTimeByVehicle`는 SSE/internal 호출이라 이번 principal 경계에서 제외했다.
- `TRIP_LOG_NOT_FOUND`는 HTTP 400에서 404로 교정됐다. FE 오류 처리 영향은 확인하지 않았다.
- `findTop6...`의 연관 company 조건은 H2에서만 실행했다. MySQL index/plan은 P0-03에서 검토한다.

## 7. 학습 기록

- 공부할 개념: transitive ownership, authorization before external I/O, data access scoping, trust-boundary separation
- 코드 위치: `TripLogServiceImpl`, `TripLogRepository`, `TripLogTenantIntegrationTest`
- 실습 질문: GPS 조회 전에 ownership을 검사하지 않으면 어떤 정보와 비용이 노출되는가?

## 8. 예상 면접 질문

1. TripLog에 companyId가 없는데 어떻게 격리했는가?
   - 답변 핵심: `trip.vehicle.company.id` 연관 predicate.
2. current GPS에서 기존 power-state 메서드를 쓰지 않은 이유는?
   - 답변 핵심: internal unscoped reader라 사용자 authorization을 보장하지 않음.
3. 왜 LogService 미호출을 검증했는가?
   - 답변 핵심: 응답 코드뿐 아니라 권한 없는 GPS 데이터 접근과 비용이 시작되지 않았음을 증명.
4. SSE를 남긴 이유는?
   - 답변 핵심: query token, resource binding, key matching, lifecycle을 P0-02D 한 단위로 검증.

## 9. AI 활용과 사람의 검증

- AI 범위: 경로별 신뢰 경계 분류, query/test/document 초안과 실행
- 대안: controller 비교, 모든 내부 메서드 principal 강제, HTTP/SSE 분리
- 판단: HTTP/SSE 분리를 채택해 배경 처리 계약 파손과 반쪽 인증 변경을 피했다.
- 사람이 확인할 흐름: ownership 실패가 LogService보다 먼저 발생하는 순서
- 자동화: repository assertion, service mock interaction, 실제 JWT API, 전체 218 tests
- 미확인: SSE 운영 흐름, MySQL plan, FE, 원격 CI
