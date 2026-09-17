# Emulator 초기화 URL 보호와 운영·CI 감사

## 메타데이터

- 날짜: 2026-09-12 KST
- 작업자: 사용자 요청에 따른 AI 감사 담당. 사용자 개인 검토·학습 완료를 대신 주장하지 않는다.
- Emulator 기준: `codex/emulator-telemetry-reliability@fa7242f`, 시작 시 clean. 아래 변경은 로컬 미커밋 상태다.
- 상태: Verified. 변경 전 결함 재현, 변경 후 Python 전체 36/36 및 실제 BE 연결 2/2 검증 완료. 원격 반영·운영 적용은 별도다.
- 범위: Emulator 코드·README·회귀 테스트. BE `.github`, `infra`, `scripts`는 읽기 전용 감사.
- 적용 지침: BE `AGENTS.md`, `.agents/skills/thisway-portfolio-modernization/SKILL.md`, 기준선·roadmap·remaining-work·취업용 여섯 단계.

## 1. 문제와 근거

장치 credential loader는 외부 HTTP, URL userinfo/query/fragment를 거부하지만, 초기화 경로인
`get_backend_url()`과 `LogStorageManager.__init__()`은 같은 검증을 적용하지 않았다.
설정한 URL을 원문으로 출력한 뒤 health GET에 사용하므로 telemetry가 거부할 주소도
초기화에서 로그·네트워크에 사용됐다. 초기 연결 예외 원문도 로그와 상태값에 남았다.

초기 health 경로 `/api/auth/health`는 현재 BE의 공개 `/api/health` 계약과 달랐다.
이는 `HealthContoller`와 `AuthAuthorizationPolicy`에서 확인했다. 초기 GET은 redirect도
따라갔다. 신규 로컬 HTTP 회귀에서는 `/api/auth/health` 이후 redirect target 반복 요청을
실제로 관찰했다. 외부 서비스나 실제 credential을 이용한 유출 실험은 하지 않았다.

원 팀이 만든 Emulator 생성·저장·전송 흐름 위에 기존 개인 현대화가 장치 인증·재시도
source binding·KST packet 분할을 추가했다. 이번 변경은 초기화 경계를 보강한 별도 개인
현대화이며, 원 팀 전체 구현을 사용자 개인 성과로 바꾸지 않는다. 기존 개인 기여는
`original-contributions.md` 및 Git/PR 근거를 따른다.

## 2. Acceptance criteria

- [x] 잘못된 URL은 초기 health 요청과 handler 구성 전에 고정된 메시지로 거부한다.
- [x] credential load와 manager 초기화에 동일한 URL 정책을 사용한다.
- [x] 초기화 로그·실패 상태에서 URL/HTTP 예외 원문을 제거한다.
- [x] 공개 `/api/health`를 사용하고 redirect를 따라가지 않는다.
- [x] 기존 설정 파일 우선순위 및 기본 loopback 주소를 유지한다.
- [x] Python 전체 36/36 성공으로 기존 32개 및 신규 4개 초기화 회귀를 확인했다. 통합 담당 실행 후 감사 담당이 원시 로그를 확인했다.
- [x] BE 실제 `emulatorClientTest` 2/2 성공은 통합 담당 실행으로 확인했다. 최종 원시 결과는 전체 감사 증거를 따른다.
- [x] credential/backlog identity 및 관측시각 계약은 수정하지 않는다.
- [x] 운영·로컬·이전 문서의 증거 경계를 구분한다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| 초기화 URL 출력만 제거 | 변경이 작다 | 검증 전 외부 요청과 health 계약 오류가 남는다 | 제외 |
| credential 경계의 URL 검증을 공통 함수로 추출 | startup·telemetry의 정책 일치, malformed URL 예외도 고정 | 잘못된 설정은 이제 초기화부터 중단된다 | 채택 |
| startup probe 삭제 | 요청 경로를 줄인다 | 기존 연결 상태 확인 기능도 제거한다 | 보류 |

HTTP 실패 원문은 디버깅에 편하지만 설정값을 포함할 수 있으므로 고정 실패 상태를 사용했다.
설정 오류를 자동 보정해 외부 HTTP를 HTTPS로 바꾸거나 다른 endpoint로 전환하지 않는다.
담당 AI가 사용자 요청과 저장소 지침에 따라 선택했고 사용자의 독립 설계 검토는 별도다.

## 4. 구현과 실행 흐름

- `services/device_credentials.py`: `validate_backend_url()` 추출. origin/TLS/userinfo/port 검사,
  malformed URL의 예외 메시지 정규화, trailing slash 제거.
- `services/log_storage_manager.py`: 설정 선택 후 검증, 원문 로그 제거, `/api/health` 및
  `allow_redirects=False`, 고정 연결 실패 상태.
- `tests/test_backend_config.py`: 4개 회귀. 설정/환경의 거부 조합, 우선순위·기본값,
  실제 loopback redirect, 예외 원문 비노출.
- `README.md`: 실제 JSON/환경 변수 설정 경로와 초기화 계약 기록. 존재하지 않는
  `config.py`·`API_HOST`·`API_PORT` 안내를 현재 코드에 맞췄다.

실행 흐름은 설정 선택 → URL 검증 → credential 없는 단일 공개 health GET → handler 구성이다.
로그 전송은 기존대로 URL 검증 → 비공개 credential 파일 → 요청별 새 UUID/timestamp →
HTTP → 실패 시 최초 source binding과 원본 packet 보존 순서다. 재시도는 MDN/device ID/key
fingerprint가 동일한 경우에만 전송하고 바뀌면 sticky paused로 남긴다.

초기화에 DB transaction은 없다. 큐는 메모리 보관이므로 프로세스 재시작 시 영구 복구를
보장하지 않는다. HTTP 접수 성공과 broker consumer DB 저장 완료는 별도 경계다.

## 5. 실제 검증 결과

Python 환경은 `/private/tmp/thisway-20260912-emulator-venv/bin/python`이며 Python 3.11.16,
직접 의존성은 pydantic 2.4.2, requests 2.31.0, python-dotenv 1.0.0으로 설치했다.

| 명령·실험 | 결과 | 증거 |
| --- | --- | --- |
| system `python3.11` import | pydantic 미설치. 저장소 결함으로 판정하지 않음 | 실행 출력 |
| venv `pip install -r requirements.txt` | sandbox DNS 실패 후 승인 실행 성공 | 실행 출력 |
| 수정 전 `python -m unittest discover -s tests -v` | sandbox localhost bind 차단으로 32 중 18 error. 승인 재실행은 **32/32 성공, 9.127s** | `/private/tmp/thisway-20260912-emulator-before.log` |
| 신규 테스트만 수정 전 실행: `python -m unittest discover -s tests -p test_backend_config.py -v` | **4 tests, failures=16**. subtest 거부 조합과 최종 비노출 assertion 포함. 실제 redirect 경로·고정 실패상태·설정 정규화도 실패 | `/private/tmp/thisway-20260912-emulator-regression-before.log` |
| 변경 후 `python -m unittest discover -s tests -p 'test_*.py'` | **36/36 성공, 9.727s**. 통합 담당 실행 후 감사 담당이 원시 로그 확인 | `/private/tmp/thisway-20260912-emulator-after.log` |
| `./gradlew emulatorClientTest -Demulator.python=/private/tmp/thisway-20260912-emulator-venv/bin/python --console=plain` | **통합 담당 실행 2/2 성공**. 실제 Boot/MySQL 연결 | `/private/tmp/thisway-20260912-emulator-client.log` |
| Emulator `git diff --check` | 성공 | 실행 출력 |

최초 변경 후 Python 승인 대기 호출은 중단되어 결과가 없었다. 이후 통합 담당이 동일
discover 범위를 다시 실행했고, 원시 로그의 `Ran 36 tests in 9.727s`와 `OK`를 확인했다.
환경 차단·변경 전 회귀 실패와 변경 후 성공을 구분해 기록했다.

## 6. 정적 운영·CI 감사와 잔여 gate

| 우선순위 | 관찰한 현재 파일 계약 | 판단·다음 gate |
| --- | --- | --- |
| 운영 배포 전 필수 | BE CD는 main push → `clean jib` → ECS force-new-deployment. CD 자체 test, 불변 image digest/새 task definition 명시, stabilization wait, smoke, 자동 rollback이 없음 | 기존 runbook도 같은 한계를 명시한다. 실제 AWS inventory/이전 digest/복원 DB를 확인하고 별도 검토 가능한 배포 변경이 필요하다. 이번에는 수정·배포하지 않음 |
| 운영 배포 전 필수 | prod Prometheus는 전용 Bearer 파일과 metrics DNS를 참조하고 rules를 포함 | 실제 secret 주입·TLS/ingress·보안그룹·scrape·수신자는 로컬 설정만으로 검증되지 않음 |
| 운영 배포 전 필수 | operations-evidence는 격리 Docker 네트워크, loopback ingress, local webhook receiver. Loki writer/reader gateway와 24시간 retention 정책이 있음 | 외부 운영자 알림 및 실제 장기 retention 삭제는 별도. 이번 감사에서는 스크립트를 재실행하지 않음 |
| P1 운영 안정성 | Emulator pending retry가 각 handler의 모든 MDN이 공유하는 queue lock을 잡은 동안 HTTP를 실행하며 paused queue는 만료되지 않음 | 느린 전송의 다른 MDN 지연과 메모리 상한·영구 복구가 남는다. 이번에는 bounded queue/lock 구조 변경을 추가하지 않음 |
| P1 CI 강화 | Emulator PR/workflow_dispatch, read-only permissions, 10분 timeout, Python 3.11, 32→36개 discover 범위. manual `test_emulator.py`는 제외 | 현재 patch의 GitHub runner 결과는 아직 없음. 기존 workflow 성공은 현재 patch 성공과 다름 |
| P1 CI 강화 | BE PR CI는 `./gradlew test`만 실행. 전용 emulatorClientTest/browser evidence/부하·관측성 task는 기본 명령 외 별도 | 무거운 통합 전용 task를 어느 PR/수동 gate에서 수행할지 비용·시간과 함께 정해야 함 |
| P2 재현성 | Emulator requirements는 직접 3개 버전 고정, transitive lock/hash 없음 | 이번 런타임 전체 dependency 집합은 같은 direct pin만으로 미래까지 동일하지 않을 수 있음. 일괄 업그레이드는 별도 변경 |

KST 및 시간별 packet 분할은 `gps_timestamp`/`gps_packets`에서 확인했다. UTC-aware 입력의 KST
변환, legacy naive 입력의 명시적 KST 해석, 시간·날짜 경계 및 600개 상한은 기존 회귀에 있다.
nonce 시각과 원 관측시각을 서로 덮어쓰지 않는다. 운영 서버의 clock sync는 별도다.

기존 docs의 2026-09-08 부하·알림·CI 결과는 당시 snapshot이다. 이번 감사에서는 최대 처리량,
운영 SLA, AWS 배포 성공, 실제 장치 전체 호환, 사용자 학습 완료를 새로 주장하지 않는다.

## 7. 학습 기록

- 초기 연결 확인도 외부 I/O이므로 주 telemetry와 같은 destination 검증을 거쳐야 한다.
- URL parser는 검사 도구이며 보안 정책 자체를 제공하지 않는다. query/userinfo/port/redirect를
  별도로 다루고 parser 예외가 입력 원문을 포함할 가능성도 경계에서 처리한다.
- HTTP attempt UUID/timestamp는 재전송 인증을 위한 값이고 GPS 관측시각/DB observation identity와 다르다.
- 재시도 source binding을 고정하는 이유는 키 교체·장치 재배정 후 과거 데이터를 새 소속으로
  자동 보내지 않기 위해서다. 가용성을 일부 양보한 보수적 paused 정책을 설명할 수 있어야 한다.
- 실습: 테스트의 302 응답을 200으로 바꾸어 공개 health 성공과 장치 인증 성공의 차이를 설명한다.

## 8. 예상 면접 질문

1. credential loader에서 URL을 검사했는데 왜 초기화에도 검사가 필요한가?
   - loader 호출 전에 health 요청과 로그 출력이라는 별도 경로가 실행됐다. 모든 외부 I/O 입구에 동일한 계약을 적용한다.
2. health 200이면 telemetry 전송도 성공하는가?
   - health는 credential 없는 서버 응답 확인이다. 장치 소속/nonce/요청 제한과 consumer DB commit은 별도로 검증한다.
3. 잘못된 URL과 HTTP 장애는 어떻게 다르게 처리하는가?
   - 잘못된 설정은 네트워크 이전 fail-closed. 정상 주소의 연결 실패는 고정 상태로 보존하고 기존 큐 전송 흐름을 사용한다.
4. 테스트가 증명하지 못하는 부분은 무엇인가?
   - synthetic URL/loopback HTTP는 실제 TLS·DNS·운영 계정·AWS rollback·장기 queue 상한의 증거가 아니다.

## 9. AI 활용과 사람의 검증

- AI 담당이 Emulator/운영 코드를 읽고 초기화 우회와 health 불일치를 찾아 회귀를 먼저 작성했다.
- 팀 통합 AI가 diff를 독립 검토하고 실제 `emulatorClientTest`를 실행했다. 사용자 개인 검토와 동일시하지 않는다.
- source binding·원본 관측시각·BE 보안 구현 변경은 이번 좁은 수정에서 제외했다.
- 사용자는 실패 재현과 수정 후 실행 흐름을 직접 확인하고 이 선택을 면접에서 설명하는 것이 남는다.
- 원격 push/merge·운영 변경·외부 알림 전송은 수행하지 않았다.
