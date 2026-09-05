# Thisway 3개 저장소 연결 기준선

## 목적과 범위

이 문서는 2026-09-05에 Backend, Frontend, Python Emulator를 함께 읽고 저장소 사이의 API·이벤트 계약과 현대화 영향 범위를 정리한 정적 기준선이다. 코드와 설정을 대조했지만 FE build, Python test, 세 애플리케이션 통합 실행은 아직 수행하지 않았다.

| 저장소 | 기준 commit | 역할 |
| --- | --- | --- |
| Backend | `develop@98bff23` | 인증·차량·텔레메트리 저장·Trip·통계·SSE |
| Frontend | `develop@be36f62` | 운영 화면, 차량·통계 API, SSE 소비, 브라우저 GPS scenario 전송 |
| Emulator | `main@594c3d0` | Python 기반 GPS·power·geofence 생성, 전송 실패 queue와 재시도 |

세 저장소에는 기존 `AGENTS.md`나 project skill이 없었다. 현재 작업 규칙은 Backend에만 추가했으며, FE와 Emulator를 실제 수정하기 전에는 각각의 기술 스택과 검증 명령에 맞는 지침을 별도 작성한다.

## 저장소 연결 흐름

### 운영 UI

1. FE가 `/api/auth/login`으로 JWT를 받는다.
2. 일반 API는 Axios interceptor가 `Authorization: Bearer ...`를 추가한다.
3. 차량·통계·Trip 화면은 Backend의 `/api/vehicles`, `/api/statistics`, `/api/trip-log`를 호출한다.
4. 차량 현재 위치와 Trip 상세 좌표는 SSE endpoint를 구독한다.

### 텔레메트리 입력

두 종류의 Emulator가 같은 Backend endpoint를 사용한다.

| 생산자 | GPS | 시동 | 지오펜스 |
| --- | --- | --- | --- |
| FE `EmulatorPage` | `POST /api/logs/gps` | `POST /api/logs/power` | 미지원 |
| Python Emulator | `POST /api/logs/gps` | `POST /api/logs/power` | `POST /api/logs/geofence` |

Backend는 `mdn`으로 Emulator와 Vehicle을 찾는다. GPS는 direct JDBC bulk insert 또는 RabbitMQ 경로로 처리하고, power는 동기 transaction 안에서 power log, Vehicle, Trip을 갱신한다.

## 계약에서 확인한 위험

### 1. 장치 인증이 없다

- FE의 `/emulator` route는 public path다.
- FE와 Python Emulator 모두 인증 header 없이 telemetry를 전송한다.
- Backend도 `/api/logs/**`를 공개한다.

MDN을 아는 주체가 다른 차량의 위치·시동 정보를 주입할 수 있으므로 Backend 보안 수정은 두 Emulator의 인증 방식 변경과 같은 작업 단위로 설계해야 한다.

### 2. SSE token이 URL과 log로 노출된다

- FE는 `EventSource` 제한을 우회하려고 JWT를 `?token=` query로 전달한다.
- FE console에는 연결 URL이 출력된다.
- Backend request logging은 query string을 기록한다.

Backend만 query 인증을 제거하면 FE 실시간 화면이 깨진다. cookie 기반 인증, 단기 SSE ticket, fetch streaming 중 하나를 선택하고 양쪽 contract test를 둬야 한다.

### 3. 브라우저 Emulator는 전송 실패를 유실할 수 있다

- GPS `fetch`에서 HTTP non-2xx를 확인하지 않는다.
- network exception도 무시한 뒤 다음 index로 진행한다.
- power OFF 전송 실패도 무시한다.
- CSV 또는 power ON 실패 전에 `isRunning=true`가 설정되어 오류 후 UI 상태가 남을 수 있다.

따라서 FE Emulator는 성능·신뢰성 실험의 기준 도구로 사용할 수 없다. 화면 demo와 재현 가능한 test harness의 책임을 분리해야 한다.

### 4. Python Emulator의 실패 queue도 신뢰성 도구로 쓰기 어렵다

- 실패 queue는 process memory에만 있어 종료 시 사라진다.
- 첫 전송 실패 후 `queue_lock`을 잡은 채 다시 `count_pending_logs()`로 같은 non-reentrant lock을 얻으려 해 정지할 가능성이 있다.
- 전체 pending 수 계산은 `queue.Queue`에 `len()`을 호출하고 예외를 0건으로 숨긴다.
- retry 횟수는 증가하지만 상한·오류 분류 없이 보관 시간까지 계속 재시도한다.
- pending 처리 중 lock을 잡은 채 network request를 수행한다.

이 구현은 Backend DLQ/idempotency 검증 전에 자체 unit test와 deterministic failure injection이 필요하다.

### 5. 시간과 `sum`의 의미가 하나의 명세로 고정되지 않았다

- GPS `oTime`은 12자리와 14자리 생성 경로가 함께 있다.
- Python model 설명의 power/geofence 시간 형식과 실제 14자리 generator가 다르다.
- GPS item의 `min`이 있는 경로와 없는 경로가 공존하지만 Backend는 null-safe하지 않은 단일 `&` 조건을 사용한다.
- `sum`은 일부 설명에서 checksum, 다른 위치에서는 누적 주행 거리로 표현된다.
- Emulator는 누적 거리로 생성하지만 Backend는 power OFF의 `sum`을 기존 Vehicle mileage에 더한다.

`sum`이 device lifetime odometer인지 한 Trip의 증가량인지 확정하지 않으면 mileage와 Trip 통계를 신뢰할 수 없다. 변경 전에 명세 표와 예제 payload를 먼저 고정한다.

### 6. 검증 자동화가 부족하다

- FE에는 build script만 있고 lint·test script가 없다.
- Python의 `test_emulator.py`는 assertion 기반의 격리 테스트가 아니라 sleep, network 상태, 전역 generator에 의존하는 실행 script다.
- 세 저장소 사이의 consumer-driven contract test가 없다.

## 포트폴리오에 미치는 영향

세 저장소를 모두 유지할 이유는 기능 수가 아니라 검증 가능성이다.

- Emulator: duplicate, out-of-order, late event, poison payload, disconnect를 seed 고정 scenario로 재현
- Backend: 인증, idempotency, state transition, DLQ/replay, Batch 결과를 검증
- FE: 운영자가 실패·재처리·anomaly 근거를 확인하는 recovery UI 제공

AI 기능을 추가할 때도 Emulator가 정상·이상 label을 재현하고 Backend가 rule baseline과 model 결과를 비교하며 FE가 reason code와 feedback을 보여 주는 구조가 되어야 한다.

## 단계별 적용 원칙

1. 첫 구현은 Backend P0-01 테스트 재현성으로 제한한다.
2. telemetry DTO를 바꾸기 전 Backend·FE browser Emulator·Python model의 fixture contract test를 만든다.
3. 인증 변경은 세 저장소의 migration 순서와 rollback을 함께 문서화한다.
4. Emulator를 reliability/AI evaluation 도구로 승격하는 작업은 별도 change로 진행한다.
5. FE와 Emulator를 수정할 때 각 저장소에 `AGENTS.md`, 사람용 `SKILLS.md`, 실제 project `SKILL.md`를 추가한다.

## 아직 검증하지 않은 것

- FE dependency 설치와 production build
- Python dependency 설치와 test 실행
- Backend·FE·Emulator 동시 기동
- 실제 JSON 직렬화와 HTTP 응답 contract
- SSE reconnect와 proxy 환경 동작
- `sum`의 원 외부 단말 protocol 의미

이 항목을 통과하기 전에는 “세 애플리케이션 통합 검증 완료”라고 표현하지 않는다.
