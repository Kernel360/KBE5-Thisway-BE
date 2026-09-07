# CHANGE-037: 수집 API 장치 인증과 비동기 소속 재검증

## 메타데이터

- 날짜: 2026-09-07
- 작업자: 사용자 + AI 구현·검증 보조
- 브랜치/기준: BE codex/statistics-batch-restart / ef74fcf + 미커밋 CHANGE-036
- Emulator: main 로컬 변경, FE: codex/frontend-dependency-security 로컬 변경 / 원격 PR·배포 없음
- 관련: P1-01C, ADR-011
- 상태: Verified — 로컬 수집/소비/두 Emulator 계약, 운영 배포는 미실행

## 1. 문제와 근거

CHANGE-036의 키 검증기는 수집 API에 연결되지 않았다. LogController는 요청 MDN만으로 저장·발행하고,
GPS 저장 consumer와 방송 consumer는 나중에 MDN을 다시 조회했다. 따라서 장치가 그 사이 다른 차량에
연결되면 이전 메시지를 다른 차량/회사에 저장하거나 방송할 수 있었다. 이는 코드 분석 결과이며 운영 침해 사례가 아니다.

원 팀의 수집·Python/브라우저 Emulator 구현에 추가한 개인 현대화다. 사용자의 기존 Vehicle/Statistics/Batch 기여와 구분한다.
Emulator의 인증 실패 경로를 점검하면서 store_log가 Lock 안에서 count_pending_logs를 호출해
같은 non-reentrant Lock을 다시 잡는 문제도 확인했다. 새 인증 거부가 정상적인 실패 경로가 되므로 함께 수정했다.

## 2. Acceptance criteria

- [x] GPS/Power/Geofence 수집은 정상 장치 키를 요구하며 사람 JWT만으로 통과하지 못한다.
- [x] 다른 장치 키·잘못된 locator·키 폐기를 거부하고 업무 row를 쓰지 않는다.
- [x] GPS 저장/broadcast 메시지에는 서버에서 확인한 identity만 전달하고 원문 키/해시는 넣지 않는다.
- [x] 저장/방송 시 현재 active 소속과 연결 revision을 확인하고, 저장 commit까지 재연결을 잠금으로 제한한다.
- [x] 연결 변경/원복 및 identity 없는 메시지는 저장하지 않는다. 저장 consumer는 retry 없이 DLQ로 보낸다.
- [x] 정상 identity의 transient retry·중복 저장 제한, replay의 identity 보존/legacy 거부를 검증한다.
- [x] 실제 Python Emulator→Boot→MySQL 저장과 폐기 후 거부를 검증한다.
- [x] 전체 BE/SSE/Emulator 회귀와 한계를 기록한다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| HTTP 인증만 추가하고 MDN 소비 유지 | 변경량 작음 | 인증 후 재연결로 오귀속 가능 | 제외 |
| 서버 identity를 AMQP header에 보존하고 소비 transaction에서 재검증 | 기존 GPS body/중복 key 유지, 명시적인 소속 세대 | broker publish ACL이 신뢰 경계, 구형 backlog 전환 필요 | 채택 |
| 메시지를 새 JSON envelope로 변경 | payload와 identity가 한 구조 | 기존 저장/broadcast/replay body 계약 변경이 큼 | 이번에는 제외 |
| consumer마다 키 유효성을 다시 검사 | 폐기 후 backlog도 차단 | 이미 인증·접수한 관측이 만료/교체로 거부됨 | 이번 정책에서 제외 |

키 폐기/만료/교체는 **이후 인증 요청**을 차단한다. 이미 인증된 메시지는 동일한 active 소속·revision이면 처리한다.
소속 변경은 이전 메시지를 거부한다. 이는 새로 채택한 포트폴리오 정책이며 원 RFP의 복원이 아니다.
HTTP 인증의 snapshot 직후 폐기가 일어나도 이미 인증된 요청은 진행할 수 있다. 진행 중 요청의 즉시 취소를 보장하지 않는다.

## 4. 구현과 실행 흐름

1. 세 POST 경로가 X-Device-Id(emulator DB ID), X-Device-Key를 받는다. payload did와 X-Device-Id는 다르다.
2. 기존 GPS/Power 입력 검증 후 DeviceAuthenticationService가 키와 payload MDN의 소속을 확인한다.
   malformed body/기존 validation 오류는 400이 먼저 나올 수 있다. 정상 body에 인증이 없거나 틀리면 401이다.
3. Power/Geofence는 DeviceTelemetryService의 READ_COMMITTED transaction에서 DeviceBindingGuard를 호출한다.
   guard는 현재 emulator/vehicle/company JOIN을 FOR UPDATE로 읽고 identity와 비교한다.
   기존 핵심 저장 service의 MDN 조회는 해당 잠금 안에서 수행되므로 저장 중 연결이 바뀌지 않는다.
4. direct GPS는 같은 guard 뒤 **identity.vehicleId**로 저장한다. RabbitMQ GPS는 body를 유지하며
   version/emulatorId/vehicleId/companyId/assignmentRevision만 서버 AMQP header로 보낸다.
5. SaveGpsLogConsumer는 필수 identity header를 파싱하고 transactional 저장 경계에서 guard를 확인한다.
   일시 DB 오류는 기존 3회 retry, 잘못된/구형/이전 소속 identity는 non-retryable 거부다.
6. StreamGpsLogConsumer도 guard 후 identity의 vehicle/company로 방송한다. 별도 best-effort factory에서
   실패한 live 메시지를 requeue하지 않는다. live queue는 DLQ/replay 대상이 아니다.
   min/sec 생략은 저장과 같은 oTime 기본값으로 채워 기존 SSE 변환을 호출한다. consumer MDC는 finally에서 정리한다.
7. replay CLI는 identity 필수 항목을 검증하고 그 allowlist만 복사한다. 임의 header/키/기존 x-death는 복사하지 않는다.
   identity 없는 legacy는 publish/ack 전에 거부한다. 잘못된 소속의 replay는 소비 시 다시 거부된다.

GpsLogService/Producer의 identity 없는 발행 진입점을 제거했다. GpsLogSaveService의 기존 한 인자 메서드는
과거 저장 특성 테스트용 내부 경계이며 HTTP/consumer가 호출하지 않는다. 새 호출은 반드시 identity 경로를 사용한다.
DB migration은 추가하지 않으며 V8/V9 credential·revision을 사용한다.

Emulator는 DEVICE_CREDENTIALS_FILE의 MDN별 키를 매 전송에 읽어 header로 주입한다. 원문은 telemetry body/queue에 저장하지 않는다.
외부 HTTP·userinfo/query 포함 URL을 거부하고 loopback HTTP만 개발용으로 허용하며 redirect를 따라가지 않는다.
오류 응답 body/HTTP 예외 원문을 출력하지 않는다. 실패 큐 재진입 Lock은 현재 Queue.qsize() 조회로 제거했다.
GpsLogItem.min은 BE 계약처럼 생략 가능하게 고쳤다. 재시도 횟수/영속 큐/기존 위치 디버그 로그 전체 정리는 별도다.

FE EmulatorPage에도 키 없는 직접 수집 호출이 있어 호환성 범위에 포함했다.
장치 ID·키를 명시적으로 입력하며 실행 ref에만 유지하고 시작 후 입력란·종료 후 ref를 비운다.
localStorage/sessionStorage에 키를 저장하지 않고 모든 ON/GPS/OFF에 같은 실행 identity를 사용한다.
인증/전송 오류를 무시하지 않고 다음 timer를 중단해 서버 운행 상태 확인을 안내한다.
기존 did="LTE 1.2"는 현재 Power strict 숫자 계약에 맞춰 "1"로 수정했다.
마지막 GPS batch 후 OFF 전송을 호출하고, 종료/화면 이탈 시 timer·진행 요청을 정리한다.
Chromium fixture가 정상 ON/GPS/OFF 헤더·저장소 비저장·마지막 종료·ON/GPS 401 중단을 검증한다.
브라우저 fixture의 수집 응답은 mock이며 실제 BE 연결은 별도 Python 계약 테스트로 검증했다.

## 5. 검증 결과

| 명령 | 결과 | 증거 |
| --- | --- | --- |
| 최초 입력/replay 테스트 명령 | 테스트 mock static import 누락으로 compile 실패 → 수정 | Gradle 출력 |
| ./gradlew test --tests '*DeviceCredentialIntegrationTest' --tests '*MySqlMigrationIntegrationTest' --tests '*GpsDlqReplayTest' --tests '*GpsLogProducer*' --console=plain | 중간 59개 통과, 실패·오류·skipped 0 / 57초 | build/test-results/test |
| ./gradlew emulatorClientTest -Demulator.python=/private/tmp/thisway-device-auth-venv/bin/python --console=plain | 중간 실제 client 1개 통과 / 20초 | build/test-results/emulatorClientTest |
| ./gradlew test sseBrowserTest emulatorClientTest -Demulator.python=/private/tmp/thisway-device-auth-venv/bin/python --console=plain | BE 415/415, SSE 2/2, 실제 client 1/1 / 실패·오류·skipped 0 / 총 2분 9초 | build/test-results의 각 task 디렉터리 |
| /private/tmp/thisway-device-auth-venv/bin/python -m unittest discover -s tests -v (Emulator) | 7/7 통과 / 38.584초 | unittest 출력 |

| npm test (FE) | 13/13 통과 | Node test 출력 |
| npx playwright test (FE) | 19/19 통과 / 2.8초 | Chromium/Playwright 출력 |
| npm run build (FE) | 통과 / 3.48초, main 976.73kB 경고 유지 | Vite 출력 |
| git diff --check (세 저장소) | 통과 | Git 출력 |

기본 Python에는 pydantic이 없어 임시 Python 3.11 venv에 저장소 requirements.txt를 설치했다.
실제 Emulator test는 별도 emulatorClientTest task다. 기본 test에서 tag를 제외하며 실행 없이 완료로 주장하지 않는다.
실제 client test는 direct mode의 세 HTTP 경로/서버 키 발급/폐기/MySQL row를 검증한다.
RabbitMQ 경로는 실제 controller+auth service의 MockMvc HTTP adapter와 실제 broker/MySQL/consumer로 검증했다.
Python부터 RabbitMQ listener까지 하나의 전체 프로세스로 묶은 시나리오는 실행하지 않았다.
외부 reverse geocoding은 이 인증 테스트에서 mock이며 기존 전용 테스트의 책임이다.

## 6. 실패와 남은 위험

- 구형 consumer는 새 identity를 무시할 수 있으므로 구/신버전 혼합 배포를 허용하지 않는다.
  [전환 runbook](../../runbooks/device-ingestion-authentication.md)의 traffic pause/backlog 검토가 필요하다. 실제 운영 전환은 하지 않았다.
- AMQP identity는 서명된 token이 아니다. broker write 권한을 얻은 주체가 위조할 수 있으므로 publish ACL/네트워크 격리가 필수다.
- 이미 접수된 데이터의 키 폐기 후 처리 정책과 소속 변경 후 DLQ 정책은 운영 요구에 따라 재검토할 수 있다.
- replay 승인 digest는 기존처럼 body hash다. identity header까지 포함한 암호학적 승인이나 조직 승인 시스템은 구현하지 않았다.
- FOR UPDATE JOIN 잠금은 장치뿐 아니라 차량/회사 행까지 넓어질 수 있다. SSE 전송도 짧은 guard transaction 안에서 수행한다.
  느린 subscriber/동시 재연결의 운영 처리량과 교착 복구는 측정하지 않았다. 권한 경계를 먼저 검증한 단계다.
- 직접 SQL로 revision을 우회한 A→B→A, 회사 이력/active 복원은 기존 한계가 유지된다.
- timestamp/nonce replay 통제, Geofence strict 검증, GPS 미래 시각, body size/rate limit, 중앙 감사/자동 alert는 미완료다.
- Emulator 실패 큐는 기존 메모리 큐이며 설정된 보관 기간이 지나면 폐기된다. 비밀 파일 갱신으로 재시도할 수 있지만
  다른 연결로 재발급한 키를 과거 backlog에 적용하는 장치 측 이력/승인 절차는 별도 과제다.
- FE에서 시작/종료 요청 중 창을 닫거나 네트워크가 끊기면 서버 접수 여부가 불명확할 수 있다.
  자동 OFF 복구·브라우저 재시작 가능한 전송 journal은 제공하지 않는다. 오류 시 서버 운행 상태를 확인해야 한다.
- Java unchecked/JVM class-sharing, Playwright NO_COLOR/FORCE_COLOR와 기존 FE bundle 크기 경고는 유지된다.
  이 변경을 성능 개선이나 운영 무유실로 표현하지 않는다.

## 7. 학습 기록

읽기 순서: LogController → DeviceAuthenticationService → DeviceIdentity/GpsMessageIdentity →
SaveGpsLogConsumer → GpsLogSaveService → DeviceBindingGuard → LogRepository.
동기 경로는 DeviceTelemetryService → 기존 Power/Geofence service다.
핵심 개념은 authentication/authorization, admission과 execution 시점, TOCTOU/ABA,
transaction lock과 비동기 경계, at-least-once/멱등성, poison message/DLQ, secret provisioning이다.

실습: 저장 consumer를 시작하지 않고 GPS를 발행한 뒤 장치 연결을 A→B→A로 변경한다.
consumer 시작 시 DB에 쓰지 않고 DLQ로 이동하는 이유를 revision과 함께 설명한다.
반대로 키만 폐기하면 이미 접수한 메시지는 왜 저장되는지 정책 차이를 설명한다.

## 8. 예상 면접 질문

1. HTTP에서 인증했는데 저장 전에 왜 다시 확인하나요?
   - 큐 대기 중 소속이 바뀔 수 있다. 당시 identity를 보존하고 현재 revision/active 소속과 비교해야 한다.
2. 재확인 직후 소속이 바뀌면 어떻게 하나요?
   - guard와 저장을 같은 transaction으로 묶고 관련 행을 잠근다. 단순 선조회만으로 충분하지 않다.
3. 키 폐기 후 이미 큐에 들어간 데이터는 왜 처리하나요?
   - 인증 당시 접수된 관측은 보존하는 정책이다. 새 인증은 거부하되 연결 변경은 별도 격리 사유로 취급한다.
4. header에 companyId가 있으면 신뢰할 수 있나요?
   - HTTP 클라이언트의 값은 사용하지 않는다. 서버가 구성한 AMQP metadata와 현재 DB 소속을 비교하며 broker publish ACL을 전제로 한다.
5. 구형 메시지를 현재 MDN으로 조회해 복구하면 안 되나요?
   - 발생 당시 소속/세대를 증명하지 못한다. 잘못된 차량으로 귀속될 수 있어 자동 보정하지 않는다.
6. Python에서 인증 실패 후 왜 멈췄나요?
   - 이미 보유한 non-reentrant Lock을 count_pending_logs가 다시 획득했다. 같은 임계 구역 안에서는 Queue.qsize()를 직접 읽는다.

## 9. AI 활용과 사람의 검증

사용자가 수집 인증/API 연결·비동기 소속 재검증·Emulator 연결을 승인했다.
AI는 선택지 검토, 구현, negative/concurrency/broker/client 테스트, runbook과 학습 기록을 작성했다.
HTTP-only 인증, MDN fallback, consumer의 원문 키 전달을 배제했다.
사용자의 독립 코드 이해·실습·면접 답변은 아직 확인하지 않았다. 자동화 통과와 운영 적용/사람의 이해를 구분한다.
