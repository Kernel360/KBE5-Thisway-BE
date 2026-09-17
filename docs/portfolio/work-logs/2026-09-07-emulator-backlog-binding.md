# CHANGE-047: Python Emulator 실패 backlog의 원래 credential binding 보존

## 메타데이터

- 날짜: 2026-09-07
- 작업자: 개인 현대화, AI 구현·검증 보조
- 기준: Emulator `main`의 기존 인증·nonce dirty 변경 보존; BE 기록은 `codex/statistics-batch-restart`
- 상태: Verified (기존 인증 7개와 신규 queue 검증 11개 통과)
- 선행: CHANGE-037 수집 인증 연결, CHANGE-038 nonce/timestamp 추가

## 1. 문제와 근거

기존 Python `BaseLogHandler.store_log()`는 실패 packet만 queue에 넣고, retry의
`send_log_to_backend()`가 MDN의 최신 key file을 다시 읽었다. 같은 MDN이 다른 차량에 연결된 뒤
새 키 파일을 적용하면 과거 backlog를 새 credential의 인증 헤더로 전송하여 다른 차량에
귀속시킬 수 있었다. 서버는 현재 credential의 소속은 알지만 이 payload가 과거 어느 binding에서
실패했는지 알 수 없다. README의 재연결 전 수동 backlog 확인만으로는 자동 재시도 경로를 보호하지 못했다.

원 팀 Emulator 생성/전송 기능 전체를 이번 개인 기여로 표현하지 않는다. 이번 변경은 후속 개인 현대화인
실패 queue의 source identity, 보수적 pause 정책, 해당 HTTP 검증 및 queue 집계 오류 수정이다.

## 2. Acceptance criteria

- [x] enqueue 시 실제 최초 전송에 사용한 MDN·device ID·key fingerprint를 고정하고 raw key를 queue에 넣지 않는다.
- [x] 같은 MDN의 새 credential 파일/다른 device ID로 바뀌면 HTTP 0회, queue 보존, paused 상태다.
- [x] 처음 credential이 없거나 legacy queue의 source identity가 없으면 나중의 키로 자동 귀속시키지 않는다.
- [x] 같은 credential retry는 성공하고 원래 event time/body를 유지하며 새 nonce를 사용한다.
- [x] 첫 HTTP 도중 key file 교체와 retry 검증 뒤 교체에도 같은 snapshot만 사용한다.
- [x] fingerprint/key/raw GPS가 retry 로그에 없고 paused queue도 실제 count에 포함된다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| MDN만 확인하고 최신 key로 retry | key rotation 후 자동 복구가 쉬움 | 동일 MDN 재연결 때 이전 이벤트가 새 소속에 저장될 수 있음 | 거절 |
| 원문 key를 queue에 저장 | 최초 key로 retry 가능 | backlog에 secret 보관이 늘어나고 폐기 key 수명 관리가 필요함 | 거절 |
| 원래 device ID+fingerprint 보존 후 일치할 때만 retry | raw key 없이 새 credential의 과거 backlog 사용 차단 | 같은 차량의 정상 key rotation도 자동 retry를 멈춤 | 채택 |

이 구현은 key rotation과 차량 재연결을 client만으로 구별하지 않는다. 소속을 확신할 수 없는
경우 편의보다 오귀속 방지를 우선하여 sticky paused 상태로 보존한다.

## 4. 구현과 실행 흐름

- Emulator `services/device_credentials.py`: immutable `CredentialBinding`과 짧은 수명의 `DeviceCredentialSnapshot`.
  fingerprint는 SHA-256이며 key/fingerprint를 dataclass repr에서 숨긴다. private JSON 권한/형식 검증은 유지한다.
- `services/log_handlers/base_log_handler.py`: event deep copy+최초 credential snapshot→HTTP→실패 시 binding만 queue 저장.
  retry는 현재 snapshot과 binding을 비교한 뒤 바로 그 snapshot으로 HTTP를 보낸다. 비교 후 재조회하지 않는다.
- `services/log_storage_manager.py`: `len(queue.Queue)` 대신 handler lock 안에서 `qsize()`를 합산한다.
- `tests/test_device_auth.py`: 실제 localhost HTTP와 credential 파일 교체/누락/경쟁 fixture.
- Emulator README: pending/paused, TTL, sticky pause, 재연결과 정상 rotation 제한을 코드와 맞춘다.

`pending + same binding`만 자동 재전송한다. source unknown, key/device 변경, 현재 credential 확인 실패는
`paused`로 바꾸고 시도 횟수도 늘리지 않는다. pause 뒤 키를 복원해도 자동 resume하지 않는다.
정상 pending에는 기존 TTL을 유지하지만 paused 검토 대상은 TTL로 자동 폐기하지 않는다.

raw key는 HTTP에 필요한 snapshot/header 동안만 사용하고 queue에는 넣지 않는다. 원시 위치나 fingerprint를
새 실패/재시도 로그에 출력하지 않는다. `get_pending_logs()`는 payload 사본을 반환하여 외부 객체 변경이
보관 중인 원본 event time/body와 binding을 바꾸지 않게 한다.

## 5. 검증 결과

실행 명령:

```bash
cd ../KBE5-Thisway-Emulator
/private/tmp/thisway-device-auth-venv/bin/python -m unittest discover -s tests -p test_device_auth.py -v
```

- Before: 실제 전송 credential의 source identity 없이 최신 file로 retry했다.
- After: Python 3.11.16 venv에서 18개(기존 7 + 신규 11) 통과, 실패·오류·skipped 0, 44.206초.
- 실제 localhost HTTP: GPS/Power/Geofence failed queue 생성 후 동일 MDN의 새 key file로 바꾸면 추가 HTTP 0회·각 queue 1개·paused 보존을 확인했다. 동일 credential retry는 3종 원본 body/event time을 유지하면서 새 nonce로 성공했다.
- 경쟁 조건: 최초 HTTP 도중 파일 변경에도 최초 fingerprint가 남고, retry 검증 직후 파일 변경에도 비교한 옛 snapshot으로만 발송함을 확인했다.
- unknown/legacy/current credential unavailable, device ID 변경, sticky pause·TTL 보존, 정상 pending TTL, queue manager 집계도 통과했다.
- 새 failure/retry stdout과 queue repr에 fixture key/fingerprint가 없고 stdout에 원시 좌표가 없는 것을 검증했다.
- 전용 실행에서 실패 명령은 없었다. `git diff --check`는 Emulator/BE 모두 통과했다.
- 이 테스트는 실제 localhost HTTP 경계이며, 실제 서버의 회사/차량 재연결은 BE 인증 통합 검증과 구분한다.
- nonce/timestamp, private-file 권한, HTTPS/loopback, redirect 차단의 기존 7개 테스트를 유지한다.

## 6. 실패 사례와 남은 위험

- queue는 메모리 기반이다. paused도 process 종료에는 유실될 수 있고 TTL 자동 삭제를 막아도 durable 보관이 아니다.
- paused가 쌓이면 메모리와 검토 부담이 늘어난다. 원래 소속을 확인하는 별도 승인 재처리가 필요하며
  이 수정은 새 key 강제 부착/자동 resume 기능을 제공하지 않는다.
- fingerprint는 서버 binding revision 자체가 아니다. 정상 key rotation도 보수적으로 멈추며, 실제
  현재 credential 소속 검증은 기존 BE device authentication/binding guard가 담당한다.
- 이미 발송 중인 이전 key의 HTTP를 client가 취소·새 소속으로 재작성하지 않는다. 서버가 폐기/재연결된
  이전 key를 거부하며, 다음 queue retry는 credential 변화 확인 후 pause한다.
- queue lock 안 HTTP 처리라는 기존 구조는 유지한다. 이 변경으로 큐 처리량·다중 MDN 지연 성능 개선을 주장하지 않는다.
- 프로젝트 다른 생성/UI 로그의 원시 위치 출력은 별도 검토 대상이며, 이 기록의 privacy 검증은 변경한 인증/retry 경계다.

## 7. 학습 기록

- 네트워크 재전송에는 payload 멱등성 외에도 원래 tenant/resource identity가 필요하다.
- MDN 재사용 가능성과 credential rotation 때문에 현재 key와 과거 event 소속을 같다고 추정하면 안 된다.
- 검증 때 읽은 credential을 발송 때 다시 읽으면 TOCTOU가 생긴다. 하나의 immutable snapshot을 사용한다.
- fingerprint 보관은 secret 복사를 줄이지만 원래 소속 정보가 없는 데이터를 복원해 주지는 않는다.

## 8. 예상 면접 질문

1. 서버에서 device 인증을 하는데 왜 queue binding이 필요한가요?
   - 서버는 현재 key의 소속을 검증한다. client가 과거 payload에 새 key를 붙이면 과거 소속을 알 수 없으므로
     client queue도 enqueue 당시 source identity를 보존해야 한다.
2. 같은 차량에서 key만 교체해도 왜 멈추나요?
   - 현재 client 정보만으로 rotation과 차량 재연결을 안전하게 구별할 수 없다. 불확실한 자동 귀속 대신 검토로 남긴다.
3. fingerprint를 비교한 뒤 기존 send 메서드를 호출하면 충분한가요?
   - send가 파일을 다시 읽으면 두 조회 사이 key가 바뀔 수 있다. 비교한 snapshot을 HTTP headers에도 그대로 사용해야 한다.
4. paused 데이터는 영구 보존되나요?
   - 아니다. TTL로 자동 폐기하지 않지만 in-memory queue이므로 process 종료에는 유실된다. durable spool은 별도 기능이다.

## 9. AI 활용과 사람의 검증

- AI가 P1 오귀속 경로를 분석하고 snapshot/pause 구현, localhost HTTP fixture와 문서를 작성·실행한다.
- 최신 key를 그대로 붙이는 대안과 queue에 원문 key를 보관하는 대안을 거절했다.
- 기존 사용자의 인증·nonce 변경과 private file 계약을 보존했다. 사람은 원본 event→최초 snapshot→
  실패 queue→현재 binding 비교→pause/같은 key retry 흐름을 설명해야 한다.
- 실제 운영 차량 데이터·운영 credential·사용자의 설계 이해는 검증하지 않았다.
