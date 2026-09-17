# CHANGE-048: GPS packet의 시간 경계와 FE·Python KST 시각 보존

## 메타데이터

- 날짜: 2026-09-07
- 상태: FE·Python Verified — 전체 browser26/26, Python 경계 단위14/14, 실제 Python→BE→MySQL contract2/2 통과.
- 범위: FE/Python Emulator의 GPS packet 시간 경계 및 Power/GPS/Geofence KST 표현
- 기존 작업: 장치 인증·nonce/size/rate 변경을 보존. 커밋/push/배포 없음.

## 1. 문제와 근거

기존 FE Emulator의 `sendBatch`는 interval로 자른 모든 관측을 하나의 `oTime`과 entry `min/sec`로 보냈다.
서버는 `oTime`의 년/월/일/시를 유지하고 각 entry의 분/초를 대입한다. 따라서 09:59:58에서 시작한
5초 묶음의 10:00:00은 잘못된 09:00:00으로 저장될 수 있었다. 자정이면 날짜도 틀어진다.

브라우저 `getHours/getMinutes` 기반 표현도 서버의 KST 해석과 달라 UTC/미국 브라우저에서 같은
Instant를 다른 이벤트 시각으로 보냈다. 실제 CSV 첫 필드는 `발생시간(초)`의 0,1,2... 상대 offset이다.
절대 시각 문자열을 다른 timezone으로 재해석하는 변경이 아니다.

원 팀 프로토콜 형식은 유지한다. 새 개인 현대화는 시간 정보가 소실되는 packet 경계를 바로잡고
실제 브라우저에서 변환 후 시각을 대조하는 것이다.

## 2. Acceptance criteria

- [x] 각 GPS packet의 관측은 같은 `YYYYMMDDHH`를 공유한다.
- [x] 09:59:58→10:00:02와 23:59:58→다음 날00:00:02가 각각 2/3개 관측의 두 packet으로 나뉜다.
- [x] 서버 방식으로 복원한 모든 관측 5개의 시각이 원본 순서·시각과 같다.
- [x] Asia/Seoul, UTC, America/Los_Angeles 브라우저가 같은 Instant를 같은 KST Power/GPS 문자열로 보낸다.
- [x] 분리된 각 HTTP packet은 fresh nonce를 사용하며 기존 인증·중단 동작을 유지한다.
- [x] Python은 원본 timestamp·순서·누적 거리 연속성을 유지하고 같은 시 범위에서도 600개를 넘으면 분리한다.
- [x] Python의 전체 packet을 realtime/수동 CLI/fixture script의 저장 경로까지 전달한다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 판단 |
| --- | --- | --- | --- |
| 서버가 분/초 감소를 보고 다음 시각으로 추정 | client 변경 적음 | 순서 역전·지연과 경계 통과 구분 불가 | 거절 |
| entry마다 전체 timestamp로 프로토콜 변경 | 표현이 명확함 | BE/Python/기존 장치 계약 변경 필요 | 이번 범위 제외 |
| 기존 시/날짜 범위별 packet 분리 | 호환성을 유지하며 잃는 정보 없음 | 경계에서는 HTTP 요청 수 증가 | 채택 |

브라우저 Instant는 `Intl.DateTimeFormat(...timeZone:'Asia/Seoul',hourCycle:'h23')`로 변환한다.
`min/sec`도 그 결과 문자열에서 추출해 packet 기준과 일치시킨다.

## 4. 구현 흐름

1. 실행 순간의 `session.base`에 CSV의 상대 초를 더한다.
2. Power ON/OFF와 GPS의 시각을 KST 14자리 문자열로 만든다.
3. 전송 interval 안에서 같은 년/월/일/시인 연속 관측끼리 묶는다.
4. 각 packet의 `oTime`, `cCnt`, `cList`를 보내며 각 `send`가 새 nonce와 현재 요청 timestamp를 만든다.
5. 확인된 packet만큼 마지막 관측과 진행 index를 갱신한다. HTTP 실패 시 기존 정책대로 전송을 중단한다.

Python의 `gps_timestamp`는 timezone 없는 기존 timestamp를 KST로 해석하고 timezone 있는 값은 KST로 변환한다.
`gps_log_generator.py`와 `gps_log_handler.py`는 같은 `YYYYMMDDHH` 및 최대 600개 경계로 분리한다.
missing timestamp는 누적 거리를 바꾸기 전에 실패한다. `data_generator.py`, `main.py`, `test_emulator.py`까지
단일 packet 대신 전체 목록을 전달하며, 한 packet의 저장이 실패해도 뒤 packet의 로컬 저장을 시도한다.
전송 중단을 선택하는 FE와 Python의 로컬 저장 정책은 구분한다.

Power/Geofence/EmulatorManager가 새로 만드는 시각은 명시 KST다. ON을 모르는 OFF에서 과거 한 시간을
임의로 만들어 운행을 생성하던 fallback은 보류(`None`)로 바꾸고, Geofence angle은 0..359로 제한했다.
이 경로에서 raw GPS/key/header/body/exception 출력을 제거하고 Kakao HTTP timeout은 connect 3초/read 5초다.

## 5. 검증

- 최초 한국 시간대 경계 수정: Emulator 인증4 + hour/midnight2 = 6/6 통과.
- 명시 KST 수정 후 `npx playwright test`: 전체 **26/26 통과**.
- 그중 6개는 2종 경계 × 3종 브라우저 timezone, 실제 fetch payload와 header를 관찰한다.
- 모든 새 packet의 header nonce가 서로 다르고 Power ON도 원본 KST 시각인지 확인한다.
- Python `python3 -m unittest discover -s tests -p 'test_gps_time_grouping.py' -v`: **14/14, 0.071초**.
  자정/분 경계, UTC, 600+1, missing clock, handler batch/부분 실패, realtime facade,
  manual CLI/script 경로, KST producer/worker, unknown ON, 로그 민감값 비노출을 검사했다.
- Python `compileall`, FE/BE/Emulator `git diff --check` 통과. FE production build 및 lazy route browser3/3도 통과.
- root 최종 `emulatorClientTest`는 **2/2 통과**, 실패·오류·skipped0이다. 그중
  `actualPythonMidnightPacketsPreserveAllFiveMysqlObservationTimes`는 실제 Python subprocess의 두 packet을
  Boot로 전송한 뒤 MySQL의 시각이 `2020-01-01 23:59:58`부터 다음 날`00:00:02`까지
  5개 원본 시각과 정확히 같은지 확인했다. 이 testcase는0.201초, XML 시각은`2026-09-07T11:06:28.956Z`다.

Before: 09:59:58 묶음의 10:00:00을09:00:00으로 복원 가능한 코드였다.
After: 각 packet의 hour prefix를 사용해 5개 원본 시각을 정확히 복원한다.
FE 경계 검증은 관측 payload를 test double로 수집한다. 실제 parser/MySQL 경계는 위 Python 통합 검증이 별도로 확인했다.
이 결과로 운영의 과거 잘못 저장된 시각을 자동 수정하지 않는다.

## 6. 남은 위험

HTTP packet 수가 시간 경계마다 늘며 일부 packet만 수락될 수 있다. 이미 성공한 앞 packet을
원복하지 않고 오류를 표시한다. client는 운영 replay queue나 영속 재전송을 새로 제공하지 않는다.
과거 시각 데이터는 근거 없이 자동 수정하지 않는다. 서버는 정렬·중복·late event를 별도로 처리한다.
실제 Kakao 호출/실차/운영 로그/수동 `main.py` 전체 실행은 검증하지 않았다. Python 단위 검증은
합성 입력과 HTTP double을 사용한다. 실제 저장 시각의 증거는 위 별도 Python 통합 테스트2/2와 구분한다.

## 7. 학습 기록

- Instant와 timezone 없는 LocalDateTime의 차이, KST 프로토콜의 명시적 경계.
- header의 요청 시각과 payload의 event 시각은 서로 다르다.
- 정보가 부족한 packet을 서버의 추측으로 복원할 수 없는 이유.
- packet 분할과 부분 성공, observation 식별자와 HTTP nonce의 차이.

## 8. 면접 질문

1. min이 59에서 0으로 바뀌면 서버가 한 시간을 더하면 되지 않나요?
   - 순서 역전된 관측인지 경계 통과인지 정보가 부족하다. 보내는 쪽에서 hour/date를 보존해야 한다.
2. 장치 timestamp header도 CSV 시각으로 보내나요?
   - nonce/replay 보호 header는 현재 요청 시각이다. CSV event 시각은 payload에 별도 보존한다.
3. UTC 브라우저인데 왜 KST를 보내나요?
   - 서버의 기존 프로토콜이 timezone 없는 값을 KST로 해석한다. 동일 Instant를 계약의 timezone으로 변환한다.

## 9. AI 활용과 검증 책임

AI 검토가 시간 소실 위험을 발견했고 사용자 위임 범위에서 packet 분리/KST 변환을 구현했다.
프로토콜 확장이나 서버의 시간 추정은 채택하지 않았다. 실제 CSV 의미를 확인하고 3개 timezone에서
브라우저 요청 payload를 원본 시각과 대조했다. 과거 운영 데이터·실제 장치 firmware는 확인하지 않았다.


## 교차 저장소 최종 검증 추가

Python 전체 `python -m unittest discover -s tests -p 'test_*.py'`는 32개, 실패/오류0, 44.189초 통과했다. root는 별도 `emulatorClientTest`에 Python의 실제 midnight 2개 packet → HTTP → MySQL 5개 occurred_time 보존 검증을 추가했다. 실제 MySQL의2020-01-01 23:59:58부터2020-01-02 00:00:02까지5개 시각이 정확히 보존되는 검증을 포함해 emulatorClientTest2개가 통과했다. 과거 잘못 저장된 데이터는 수정하지 않는다.
