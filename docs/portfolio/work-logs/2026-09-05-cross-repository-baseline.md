# CHANGE-001: 3개 저장소 연결 기준선

## 메타데이터

- 날짜: 2026-09-05 KST
- 작업자: Shin Dong Jun + AI assistant
- 기준 commit: BE `98bff23`, FE `be36f62`, Emulator `594c3d0`
- 관련 issue/PR: 없음
- 상태: Verified

## 1. 문제와 근거

- 최초 Backend 감사는 FE와 Emulator를 실제 clone하지 않은 상태였다.
- Backend telemetry와 SSE contract를 변경하면 다른 두 저장소가 동시에 깨질 수 있다.
- FE 안에도 Python 저장소와 별개인 browser Emulator가 존재한다.
- 세 저장소를 연결한 실행·검증 경계가 문서화되어 있지 않았다.

## 2. Acceptance criteria

- [x] FE와 Emulator를 실제 clone해 README와 source 구조를 확인한다.
- [x] 세 저장소의 기준 branch와 commit을 기록한다.
- [x] FE API/SSE 소비와 두 Emulator의 telemetry payload를 Backend contract와 대조한다.
- [x] runtime 검증과 정적 분석을 구분한다.
- [x] 제품 코드는 변경하지 않는다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| Backend만 계속 수정 | 빠름 | FE·Emulator 호환성 회귀 | 거절 |
| 세 저장소를 한 번에 현대화 | 전체 정합성 | change가 너무 커 원인과 기여를 설명하기 어려움 | 거절 |
| 연결 계약을 먼저 기록하고 작은 저장소별 change 진행 | 영향 범위와 검증 책임이 명확 | 초기 정적 감사 비용 | 채택 |

## 4. 구현과 실행 흐름

- 추가: [`../cross-repository-audit.md`](../cross-repository-audit.md)
- 제품 request/event 흐름은 변경하지 않았다.
- Backend의 기존 문서 branch에만 감사 기록을 추가했다.

## 5. 검증 결과

| 확인 | 결과 |
| --- | --- |
| FE Git 상태 | `develop@be36f62`, clean |
| Emulator Git 상태 | `main@594c3d0`, clean |
| FE API 검색 | Vehicle, Statistics, Trip, Admin, Member API와 SSE query token 확인 |
| Emulator 계약 대조 | GPS, power, geofence field와 Backend request record 대조 |
| tracked product 변경 | 없음 |

- 실행하지 않은 검증: FE build/test, Python test, 통합 기동, live HTTP/SSE.
- 따라서 이번 상태 `Verified`는 정적 cross-repository 기준선 문서에만 적용된다.

## 6. 실패 사례와 남은 위험

- FE telemetry는 실패를 무시하고 index를 진행할 수 있다.
- Python pending queue는 in-memory이며 lock/count 구현에 정지·오류 은폐 위험이 있다.
- device authentication과 contract test가 없다.
- `sum`, GPS 시간, `min`의 protocol 의미가 일관된 문서로 고정되지 않았다.

## 7. 학습 기록

- 공부할 개념: consumer-driven contract test, schema evolution, device identity, retry queue, SSE authentication
- 코드에서 확인할 위치: FE `utils/api.js`·`EmulatorPage.jsx`, Python `emulator_data.py`·`BaseLogHandler`, Backend log request·service
- 스스로 설명할 질문: “Backend DTO 변경을 독립 배포 가능한 방식으로 세 저장소에 어떻게 전파할 것인가?”

## 8. 예상 면접 질문

1. Emulator를 단순 demo가 아니라 신뢰성 시험 도구로 만들려면 무엇이 필요한가?
   - 답변 핵심: seed, fixture, 장애 주입, 기대 결과, replay, 자동화
2. SSE query token을 없애면 FE와 Backend를 어떤 순서로 배포해야 하는가?
   - 답변 핵심: 호환 기간, 단기 ticket 또는 cookie, feature flag, rollback
3. `sum` 필드 의미가 불명확한 상태에서 왜 바로 mileage code를 고치지 않았는가?
   - 답변 핵심: producer 둘과 기존 데이터 의미, 명세·fixture 우선, migration 위험

## 9. AI 활용과 사람의 검증

- AI에게 맡긴 범위: 세 저장소 source 검색, endpoint·payload·failure-path 후보 대조, 문서 초안
- 채택한 판단: Backend-only 대형 변경 전에 cross-repository 계약을 고정
- 보류한 판단: `sum`을 확정적 bug로 표현하는 것과 즉시 코드 수정
- 사용자가 후속 학습에서 직접 재현할 흐름: Emulator → `/api/logs/**` → 저장/Trip → SSE → FE
- 자동화 검증: Git status와 정적 source/contract 검색
- AI가 확인하지 못한 사항: runtime serialization, 통합 실행, 외부 단말 원 명세
