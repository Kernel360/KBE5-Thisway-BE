# 장치 수집 인증: 로컬 실행과 운영 전환 gate

CHANGE-037의 코드 계약이다. 실제 인프라 전환/배포를 수행한 기록이 아니다.

## 클라이언트 계약

- POST /api/logs/gps, /power, /geofence 모두 X-Device-Id와 X-Device-Key가 필요하다.
- X-Device-Id는 `/api/emulators` 관리 조회의 DB id다. payload의 did(프로토콜 장치 필드)를 복사하지 않는다.
- 회사 관리자가 기존 POST /api/emulators/{id}/device-key로 키를 발급한다. 응답의 원문 key는 한 번만 표시된다.
  관리용 JWT를 장치에 복사하지 않는다. 키 조회 API로 원문을 복원할 수 없다.
- 인증 오류 15004/401이면 키·MDN·현재 연결·active·만료를 확인한다. 정상 key 없이 JWT만 보낸 요청도 거부된다.
- payload 계약은 유지한다. 클라이언트가 companyId/vehicleId/revision을 보내 인증 소속을 지정하지 않는다.
- 외부 통신은 HTTPS를 사용한다. Emulator는 loopback HTTP만 개발용으로 허용한다.

Emulator의 저장소 밖 파일에 다음 구조로 저장하고 파일 권한을 600으로 제한한다. 예시는 실제 credential이 아니다.

```json
{
  "실제 장치 MDN": {"device_id": 123, "key": "발급 응답의 key 전체"}
}
```

```bash
chmod 600 "$HOME/.config/thisway/device-credentials.json"
export DEVICE_CREDENTIALS_FILE="$HOME/.config/thisway/device-credentials.json"
```

키는 CLI 인자/URL/Git/config.json/telemetry body에 넣지 않는다. 파일은 매 전송마다 읽으므로 키 교체 후
같은 연결의 새 키로 파일을 안전하게 교체하면 재시도에 반영된다. 파일 누락/잘못된 권한/MDN 누락은 전송하지 않는다.
인증 거부는 기존 실패 큐에 남으며 큐는 메모리 기반·유한 보관 기간이다. 영구 보존이나 무유실 보장이 아니다.
장치 재연결 전 미전송 backlog를 별도로 검토한다. 새 연결용 키로 이전 연결의 backlog를 자동 재귀속하지 않는다.

## 브라우저 Emulator

FE Emulator 화면에서 등록된 MDN, Emulator DB ID, 발급받은 key를 직접 입력한다.
입력란은 실행 후 비워지고 현재 실행 ref에서만 키를 사용한다. 종료/오류 후 지우며 브라우저 저장소에는 저장하지 않는다.
외부 접속은 HTTPS가 필요하다. 브라우저 확장/개발자 도구/같은 origin의 악성 script로부터 key를 격리하는 보장은 없다.
ON/GPS/OFF 전송 실패는 화면에 표시하고 다음 전송을 중단한다. 오류나 시작/종료 중 화면 이탈 시에는
서버의 운행 상태를 확인한다. 서버 미종료 운행을 자동 복구하는 기능은 아니다.

## 저장과 재처리 정책

서버는 인증 시 emulatorId/vehicleId/companyId/mdn/assignmentRevision을 얻는다.
GPS AMQP header에는 identity version 1과 네 숫자 ID/revision만 담고 원문 키/해시는 보내지 않는다.
저장 consumer는 현재 active 소속과 revision을 잠금 안에서 재검증한다.

- 동일 소속 + 키만 폐기/만료/교체: 이미 인증한 메시지는 처리한다. 새 인증은 현재 키로 검증한다.
- 연결 변경/원복, inactive, 잘못되거나 누락된 identity: DB 저장 거부. 저장 queue의 DLQ 정책이 필요하다.
- live 방송 실패: best effort로 폐기하며 별도 DLQ/replay를 제공하지 않는다.
- replay: [기존 한 건 제한 CLI](gps-dlq-replay.md)를 사용하되 identity가 없는 메시지는 자동 replay하지 않는다.
  CLI는 검증한 identity allowlist만 보존하고 consumer는 현재 연결을 다시 확인한다.

## 운영에 적용하기 전

1. 운영 접근·중단 창·rollback 담당자의 승인을 받고 broker publish ACL, 네트워크 격리, HTTPS, DLQ policy를 확인한다.
2. 장치별 현재 등록/소속과 키 provisioning을 준비한다. 원문 key를 운영 로그/감사표에 붙이지 않는다.
3. 외부 수집 traffic과 관련 consumer를 일시 중지하고 queue/DLQ backlog를 조사한다.
   identity 없는 backlog는 원문/metadata를 보존해 제한된 검토 대상으로 분리한다. 현재 MDN 기반 identity를 임의 생성하지 않는다.
4. producer·저장 consumer·방송 consumer를 같은 계약 버전으로 전환한다. 구형 consumer와 혼합 실행하지 않는다.
5. 격리된 장치로 정상 저장, 키 없음/폐기 401, 연결 변경 거부/DLQ, 동일 GPS 중복 저장 제한을 확인한 후 traffic을 재개한다.
6. 인증 거부, gps.consumer.rejected, DLQ depth, publisher unconfirmed, lock wait를 확인한다. 자동 alert는 아직 구현하지 않았다.

rollback이 필요하면 먼저 traffic을 다시 막는다. 인증 없는 구버전으로 돌아간 상태에서 수집을 공개하지 않는다.
기존 데이터·credential·queue를 삭제하거나 revision을 감소시키지 않는다. 복구 후 동일한 인증 negative 검증을 통과해야 재개한다.

## 재현 가능한 로컬 검증

Java 21, Docker, Python 3.11과 sibling Emulator의 requirements.txt가 필요하다.
아래 임시 venv는 예시 경로이며 외부 운영 endpoint를 사용하지 않는다.

```bash
python3.11 -m venv /tmp/thisway-device-auth-venv
/tmp/thisway-device-auth-venv/bin/pip install -r ../KBE5-Thisway-Emulator/requirements.txt
./gradlew test --console=plain
./gradlew emulatorClientTest -Demulator.python=/tmp/thisway-device-auth-venv/bin/python --console=plain
```

Emulator 저장소에서는 다음을 실행한다.

```bash
/tmp/thisway-device-auth-venv/bin/python -m unittest discover -s tests -v
```

emulatorClientTest는 fresh MySQL과 임시 Boot 서버, 일회성 private key 파일을 사용해
세 HTTP API 저장 및 키 폐기 후 거부를 확인한다. 실제 장치/운영 HTTPS/broker ACL까지 검증하는 테스트는 아니다.

## CHANGE-038 전송 시도 헤더와 요청 제한

세 수집 API 모두 `X-Request-Id`에 새 소문자 UUID v4, `X-Request-Timestamp`에 전송 시점 epoch seconds를 보낸다. payload의 oTime/min/sec는 원래 관측 시각을 유지한다. 재시도는 새 nonce로 보내되 원본 이벤트 데이터는 유지한다. Redis는 장치별 nonce를 601초 보관하고 첫 접수부터 60초간 기본 120요청을 허용한다(`thisway.telemetry.requests-per-minute`). key rotation도 장치 예산을 초기화하지 않는다.

400은 malformed/freshness, 409는 이미 쓴 request ID, 413은 실제 본문 256 KiB 초과, 429는 장치 한도(`Retry-After: 60`), 503은 Redis admission 또는 broker 접수 미확인이다. 어느 503인지 고정 error code로 구분한다. 장치 키/UUID/raw GPS를 로그에 덤프하지 않는다. Redis 장애는 fail-closed이며 운영 HA/persistence/eviction/NTP 설정은 별도 배포 점검 대상이다. 이 정책은 bearer key 탈취 후 새 요청 생성까지 막는 전자서명 프로토콜이 아니다.
