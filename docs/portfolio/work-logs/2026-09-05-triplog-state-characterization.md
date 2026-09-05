# CHANGE-003: TripLog 상태 전이 특성 테스트

## 메타데이터

- 날짜: 2026-09-05 KST
- 작업자: Shin Dong Jun + AI assistant
- 브랜치/기준 커밋: `codex/portfolio-foundation`, base `develop@98bff23`
- 관련 issue/PR: 없음
- 상태: Verified

## 1. 문제와 근거

- 기존 `TripLogServiceTest`의 10개 테스트는 모두 `@Disabled`였고 본문도 비어 있었다.
- 테스트 이름 일부는 현재 구현과 맞지 않았다. 예를 들어 시동 ON이지만 GPS 로그가 없을 때 `TRIP_LOG_NOT_FOUND`를 기대한다고 적혀 있었으나, 실제 `TripLogServiceImpl`은 `null`을 반환한다.
- `saveTripLog()`는 ON/OFF 이벤트 순서에 따라 신규 생성 또는 기존 운행 완료를 수행하지만, 그 상태 변화와 저장값을 검증하는 회귀 테스트가 없었다.
- 이번 변경은 원래 팀의 production 동작을 수정한 것이 아니라, 이후 개인 현대화 전에 현행 동작을 실행 가능한 특성 테스트(characterization test)로 기록한 것이다.

## 2. Acceptance criteria

- [x] 비어 있던 TripLog 테스트 10건을 실행 가능한 서비스 단위 테스트로 교체한다.
- [x] 시동 ON/OFF, GPS 유무, Trip 존재/부재의 정상·실패 분기를 검증한다.
- [x] ON은 미완료 운행을 생성하고, matching ON이 있는 OFF는 같은 운행을 완료하는 현재 상태 변화를 필드 수준에서 검증한다.
- [x] 선행 ON 없는 OFF가 만드는 현재 결과를 성공 동작으로 미화하지 않고 위험으로 기록한다.
- [x] 좁은 테스트와 Redis integration을 포함한 전체 테스트를 모두 통과시킨다.
- [x] 현재 테스트가 보장하지 않는 repository·transaction·중복/지연 정책을 명시한다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| 기존 `@SpringBootTest` 틀을 유지 | 실제 Spring bean 조립을 함께 확인 | 분기 테스트가 느리고 DB/H2 상태와 책임이 섞임 | 거절 |
| `TripLogServiceImpl` pure unit test | 상태 분기와 collaborator 계약의 실패 원인이 명확하고 빠름 | JPA query와 실제 transaction을 검증하지 못함 | 채택 |
| 이상한 현행 동작에 원하는 기대값을 바로 적용 | 목표 정책을 먼저 표현 가능 | 이번 변경이 production 정책 수정까지 커지고 현재 계약을 숨김 | 거절 |
| 현행 동작을 특성 테스트로 고정하고 위험을 후속 설계로 분리 | 리팩터링 기준선과 문제 증거를 동시에 확보 | 후속 P1-02 전까지 문제 동작은 남음 | 채택 |

선택 이유: P0-01의 목표는 재현 가능한 green baseline이다. Trip 상태 머신과 외부 주소 보강 분리는 P1-02에서 상태표·DB 제약·event-time 정책을 먼저 결정한 뒤 별도 변경해야 한다.

## 4. 구현과 실행 흐름

- 변경 파일: `src/test/java/org/thisway/vehicle/triplog/application/TripLogServiceTest.java`
- 기존 Spring context·repository 실사용·Mockito Spring bean override를 제거하고 `MockitoExtension`, mock collaborator, `ArgumentCaptor`를 사용했다.
- 조회 7건과 저장 상태 전이 3건을 검증한다.

현재 저장 흐름:

```text
PowerLog -> TripLogSaveInput -> TripLogServiceImpl.saveTripLog
  -> 좌표를 ReverseGeocodingConverter로 주소 변환
  -> offTime 없음(ON)
       -> active=false인 미완료 TripLog 생성
  -> offTime 있음(OFF)
       -> vehicleId + onTime으로 기존 TripLog 조회
       -> 있으면 finishTrip()으로 end/거리/도착지/active=true 변경
       -> 없으면 거리 0, 출발지 없음, active=true인 TripLog 생성
  -> TripLogRepository.save
```

현재 상태와 P1-02 설계 대상:

| 이벤트/선행 상태 | 현재 코드 결과 | 이번 테스트 | P1-02에서 결정할 정책 |
| --- | --- | --- | --- |
| ON / matching open trip 없음 | `active=false` 신규 row | 필드 수준 검증 | 정상 OPEN 전이와 odometer 의미 확정 |
| OFF / matching open trip 있음 | 기존 row를 `active=true`로 완료 | 동일 객체 변경 검증 | 정상 OPEN -> COMPLETED 전이 |
| OFF / matching open trip 없음 | 거리 0의 완료 row 생성 | 현행 동작 특성화 | orphan/pending 보관과 허용 지연 window |
| duplicate ON / open trip 있음 | 별도 조회 없이 신규 open row 가능 | 미검증, 위험 기록 | event identity, unique constraint, idempotent 처리 |
| duplicate/late OFF / 이미 완료 | 기존 완료 row를 다시 변경할 수 있음 | 미검증, 위험 기록 | 멱등 결과와 수정 허용 조건 |
| late GPS / Trip 완료 뒤 도착 | 이 서비스의 상태 전이에 반영되지 않음 | 미검증, 위험 기록 | event time 기반 보정 정책 |

`active=false`가 “삭제됨”이 아니라 “현재 운행 중”을 뜻하는 TripLog의 표현은 `BaseEntity.active` 의미와 충돌한다. 이번에는 호환성을 유지했고, P1-02에서 명시적 상태 enum으로 바꿀지 검토한다.

## 5. 검증 결과

| 명령/실험 | 결과 | 증거 위치 |
| --- | --- | --- |
| P0-01A 이후 전체 결과 | 180 total / 167 passed / 0 failed / 13 skipped | [`2026-09-05-redis-test-isolation.md`](2026-09-05-redis-test-isolation.md) |
| `./gradlew test --tests org.thisway.vehicle.triplog.application.TripLogServiceTest --console=plain` | SUCCESS: 10 tests / 0 failed / 0 skipped, Gradle 6초 | `build/test-results/test/TEST-org.thisway.vehicle.triplog.application.TripLogServiceTest.xml` 로컬 artifact |
| `./gradlew test --console=plain` | SUCCESS: 180 total / 177 passed / 0 failed / 3 skipped, Gradle 17초 | `build/test-results/test` 로컬 artifact와 이 문서에 결과 전사 |
| `git diff --check` | SUCCESS: whitespace error 없음 | 로컬 실행 결과 |
| `./gradlew spotlessCheck --console=plain` | FAILED: root project에 `spotlessCheck` task가 없음 | 아래 실패 기록에 전사 |

- Before: TripLog 10건이 본문 없이 skip되어 실제 요구사항이나 회귀를 검증하지 못했다.
- After: 같은 10건이 통과하며 전체 passed 수가 167에서 177로 늘고 skipped 수가 13에서 3으로 줄었다.
- 실행하지 못한 검증: GitHub Actions 원격 실행은 수행하지 않았다. pure unit test이므로 실제 MySQL repository query와 transaction rollback도 검증하지 않는다.

## 6. 실패 사례와 남은 위험

- `spotlessCheck` task가 프로젝트에 등록되어 있지 않아 자동 포맷 검증은 수행할 수 없었다. 대신 컴파일·테스트와 `git diff --check`를 통과했다.
- GPS 로그가 없는 실시간 요청은 예외가 아니라 `null`을 반환한다. Controller 응답 계약과 FE 처리 방식을 확인한 뒤 empty response 또는 명시적 상태로 바꿀지 결정해야 한다.
- 선행 ON 없는 OFF가 거리 0의 완료 Trip을 만드는 것은 데이터 왜곡 위험이 있다. 현재 동작을 검증한 것이 올바른 도메인 정책임을 승인한 것은 아니다.
- duplicate ON/OFF, out-of-order, late event, 동시 처리, DB unique constraint는 아직 보장하지 않는다.
- reverse geocoding이 `@Transactional` 저장 흐름 앞에서 동기 호출된다. 느린 응답과 실패가 핵심 Trip 저장을 막을 수 있어 P1-02에서 enrichment를 분리해야 한다.
- `getVehicleDetails()`는 차량 정보를 두 번 조회하고 repository에서 받은 목록을 직접 변경한다. 동작 결과만 검증했으며 효율과 가변성 문제는 별도 리팩터링 후보로 남긴다.

## 7. 학습 기록

- 공부할 개념: characterization test, unit test와 integration test 경계, state transition/invariant, event time과 processing time, idempotency
- 코드에서 확인할 위치: `TripLogServiceImpl.saveTripLog()`, `TripLog.finishTrip()`, `TripLogRepository`, `TripLogServiceTest`
- 스스로 설명해 볼 질문: 왜 잘못되어 보이는 현재 동작을 바로 바꾸지 않고 먼저 테스트로 기록했는가?
- 실습: 종이에 `OPEN`, `COMPLETED`, `ORPHAN` 상태를 두고 정상·중복·역순·지연 ON/OFF의 허용 전이를 작성한 뒤 각 전이에 필요한 event identity와 DB 제약을 적는다.

## 8. 예상 면접 질문

1. `@SpringBootTest` 대신 Mockito 단위 테스트를 선택한 이유는 무엇인가?
   - 답변 핵심: 이번 대상은 application service 분기와 domain mutation이며, 빠른 피드백과 실패 원인 격리를 얻는 대신 실제 JPA/MySQL 계약은 별도 integration test가 필요하다.
2. 선행 ON 없는 OFF를 거리 0의 완료 Trip으로 저장하는 현재 방식의 문제는 무엇인가?
   - 답변 핵심: 유실·역순 이벤트를 실제 운행 완료로 오인해 통계와 거리 의미를 왜곡한다. orphan/pending 상태, 허용 지연 window, 보정 정책이 필요하다.
3. duplicate ON/OFF를 어떻게 멱등하게 처리할 것인가?
   - 답변 핵심: stable event identity와 DB unique constraint를 최후 방어선으로 두고, 현재 Trip 상태에 따라 같은 결과를 반환하며 concurrent duplicate integration test로 검증한다.
4. `active=false`를 운행 중 상태로 사용하는 설계가 왜 위험한가?
   - 답변 핵심: 공통 soft-delete 의미와 충돌해 query 누락과 오해를 만든다. 삭제 여부와 운행 상태를 별도 필드/enum으로 분리하는 편이 명시적이다.

## 9. AI 활용과 사람의 검증

- AI에게 맡긴 범위: 비어 있는 테스트와 production 분기 분석, test case·상태표·문서 초안, 자동 검증 실행
- AI가 제안한 대안: Spring context 유지, pure unit test 전환, 현행 동작 즉시 수정, 특성 테스트 후 정책 분리
- 채택/거절과 이유: green baseline이라는 현재 단계에 맞춰 pure unit 특성 테스트를 채택하고, 검증되지 않은 원하는 정책으로 production 동작을 즉시 바꾸는 안은 거절했다.
- 사람이 직접 확인할 실행 흐름: ON 생성, matching OFF 완료, missing ON의 현재 결과와 GPS 조회 분기
- 자동화 검증: TripLog 좁은 10건과 전체 180건 Gradle test, whitespace 검사
- AI가 확인하지 못한 사항: 실차/Emulator의 `sum` 프로토콜 의미, FE의 null 응답 처리, 운영 데이터의 중복·역순 빈도, 원격 CI 결과
