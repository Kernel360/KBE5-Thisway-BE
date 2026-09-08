# CHANGE-033: Power 입력 검증과 미래 이벤트 시각 제한

- 날짜: 2026-09-06
- 기준: `codex/statistics-batch-restart@b95f8aa`
- 상태: Verified (로컬, 원격/운영 미반영)
- 원 팀 Power 수집 코드의 AI 지원 개인 현대화. 원 개인/팀 기여는 original-contributions.md 기준.

## 문제와 근거

CHANGE-031의 현재 상태는 가장 큰 이벤트 시각을 기준으로 갱신한다. 이전에는 9999년 같은
미래 OFF가 저장되면 정상 이벤트가 계속 과거로 취급될 수 있었다. Power 날짜 converter는
SMART 파싱으로 존재하지 않는 날짜를 정규화할 수 있었고, 변환 예외가 일관된 400으로
처리되지 않았다. Trip 입력 검사는 이미 변환/일부 core 쓰기 이후 실행됐다.

## Acceptance criteria

- [x] 잘못된 Power 입력을 장치 조회·변환·저장 이전에 10000/400으로 거부
- [x] 존재하는 14자리 날짜, ON 필수, OFF>=ON, 좌표/숫자/길이 범위 검증
- [x] Asia/Seoul 서버 기준 5분 미래 경계와 1초 초과를 고정 Clock으로 검증
- [x] 과거 지연·윤년·동일시각 종료·숫자 경계 허용
- [x] 실제 MySQL에서 미래 요청 후 정상 운행 저장 및 전체 회귀

## 선택과 실행 흐름

PowerLogRequestValidator를 LogServiceImpl.savePowerLog 첫 줄에서 호출한다.
Controller에만 두는 안은 직접 서비스 호출이 우회할 수 있어 채택하지 않았다.
기존 converter 전체 변경은 GPS/Geofence 계약에 영향이 커 Power 전용 validator로 제한했다.

요청 → 순수 검증 → MDN 조회 → 차량 잠금 → 기존 Power/Vehicle/Trip transaction → 주소 보정.
서비스의 transaction 선언은 유지하므로 transaction 시작 자체를 없앴다는 뜻은 아니다.
유효성 실패는 저장 후 rollback에 의존하지 않고 업무 조회·쓰기 이전에 끝난다.

- 날짜: ASCII 14자리 `uuuuMMddHHmmss`, STRICT, MySQL DATETIME 범위의 최소 연도 1000.
- offTime은 null/빈 문자열이면 ON. 공백은 오류. OFF에는 원 운행 onTime이 필요하다.
- 비교 기준은 한 번 읽은 서버 Clock instant를 Asia/Seoul로 변환한 값이다.
  현재+5분까지 포함하여 허용하고 그 이후 거부한다. 이벤트 시간을 서버 시간으로 덮지 않는다.
- **5분은 이번 현대화의 허용 오차 정책이며 원 RFP/실장치 측정값이 아니다.**
  오차를 전혀 허용하지 않는 안은 작은 시계 차이도 거부하고, 무제한 허용은 watermark 오염을 방치한다.
- 과거 최대 보관/허용 기간은 두지 않아 지연 관측과 로컬 과거 fixture를 수용한다.
- mdn 20자, tid 255자; mid/pv/did/spd/sum은 0..INT_MAX의 십진 정수 문자열.
- 좌표는 signed integer microdegree, 위도 ±90,000,000·경도 ±180,000,000.
  소수/지수/NaN/Infinity는 거부한다. gcd는 기존 enum을 따른다.
- Power ang는 현행 Emulator의 0..365를 유지한다. 이 projection은 ang를 사용하지 않으며
  GPS validator의 0..359와 통합한 프로토콜 표준화는 별도다.

validator 내부 변환 오류는 원본 값을 포함하지 않는 기존 INVALID_INPUT_VALUE로 변환한다.
장치 credential/원시 위치를 로그에 추가하지 않았다. 새 DB migration이나 API 응답 필드 변경은 없다.

## 검증

- `./gradlew test --tests '*PowerLogValidationTest' --tests '*LogServiceTest' --console=plain`
  44개 통과, 실패/오류/skipped 0, 3초. Power 신규 28개 잘못된 입력의 HTTP/직접 호출과 2개 정상/시각 경계 테스트 포함.
- `./gradlew test --console=plain`: 380개 통과, 실패/오류/skipped 0, 1분 27초.
  실제 MySQL에서 미래 OFF 거부 후 Power/Trip 0행·watermark NULL 보존,
  이어진 정상 ON1000/OFF1500의 500m와 정상 종료시각 저장을 확인했다.
- 증거: build/test-results/test/TEST-*.xml, build/reports/tests/test/index.html.
  `git diff --check` 통과. 별도 sseBrowserTest/FE suite는 이번 Power 전용 변경에서 재실행하지 않았다.
- 첫 targeted 실행 실패 없음. 변경 전 반례는 코드 분석이며 운영 장애 측정이 아니다.

## 호환성과 남은 위험

- Emulator source는 local datetime.now()를 전송한다. 실행 환경의 시각을 Asia/Seoul로 맞춰야 한다.
  timezone 없는 payload로 실제 장치 시간대가 올바른지 확인할 수 없다. Emulator 전체 실행은 하지 않았다.
- 정수 microdegree와 0..365 angle 생성은 소스에서 확인했다. FE는 Power 송신 계약 변경 없음,
  이번에 FE/Emulator 수정·브라우저·업무 전체 시연은 하지 않았다.
- 5분 안의 잘못된 미래 관측은 여전히 허용되고 일시적으로 정상 이벤트의 최신 상태 반영을 막을 수 있다.
  현재보다 과도하게 미래인 값만 제한한다. 미래 요청 거부가 이미 저장된 watermark를 복구하지 않는다.
- 거부가 반복되면 장치 시간대/NTP와 서버 시계를 조사한다. 요청 시간이나 DB watermark를
  임의 현재 시각으로 치환하지 않는다. 기존 오염은 별도 승인된 근거 기반 보정이 필요하다.
- 입력 검증은 인증이 아니다. 위조된 정상 범위의 계기값, 장치 권한/재전송, size/rate 제한은 별도다.
- GPS/Geofence에는 이번 미래 시각 정책을 적용하지 않았다. 전체 수집 API 보안 완성으로 표현하지 않는다.
- 과거 무제한 재전송 방지나 오류별 metric/경보, 최대 body 크기 제한도 미완료다.

## 학습과 면접

읽기: PowerLogValidationTest → PowerLogRequestValidator → LogServiceImpl →
TripAddressEnrichmentIntegrationTest의 미래 OFF 테스트.

1. 형식 검증과 의미 검증은 어떻게 다른가요?
   - 14자리 형식 외에도 실제 존재하는 날짜, OFF>=ON, 허용 미래 범위를 검사한다.
2. 왜 Clock을 고정하나요?
   - 테스트 실행 시각/시스템 timezone과 무관하게 정확히 +5분과 +5분1초를 재현한다.
3. 왜 과거 이벤트를 막지 않나요?
   - 지연 운행 보충과 재처리를 허용하기 위해서다. 인증된 replay 통제는 별도 설계해야 한다.
4. 검증했으니 안전한 장치 요청인가요?
   - 아니다. 유효한 값과 정당한 발신자는 다르며 장치 인증/권한이 필요하다.

연습: 10시 서버에 10시05분/10시05분01초 OFF가 들어올 때 응답과 이후 watermark를 설명한다.
STRICT/SMART 날짜 파싱, event time/clock skew, 검증 위치, fail-fast, validation vs authentication을 공부한다.

## AI/사람의 검증

프로젝트 스킬의 변경 기록·회귀 gate를 적용했다. AI가 코드 분석, 정책 대안, 구현·테스트·문서를
작성하고 검증을 실행했다. 사용자 위임 범위의 제한 정책이며 실제 RFP 합의로 꾸미지 않았다.
실장치 시간 오차/운영 영향/사용자의 독립 설명 능력은 확인하지 않았다.
