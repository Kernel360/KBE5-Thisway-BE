# CHANGE-019: GPS 입력 검증과 min 생략 호환성

- 날짜: 2026-09-05, Shin Dong Jun + AI assistant
- 기준: `codex/portfolio-foundation@2a90d59`
- 범위: P1-01A 입력 guardrail. 중복 제거·device 인증·DLQ는 아직 미구현.

## 문제와 출처

기존 팀 GPS controller/request에는 입력 검증이 없었다. cCnt와 cList 불일치, 빈/과대 packet, 날짜·숫자 오류가 저장 또는 publish 경로로 진입했다. SaveService의 `entry.min() != null & !entry.min().isEmpty()`는 null에서도 오른쪽을 평가했다.

Emulator 저장소의 `services/log_handlers/gps_log_handler.py`는 min을 생략하고 sec만 보낸다. `services/log_generators/gps_log_generator.py`는 min/sec를 모두 보낸다. 두 형식을 정적으로 확인했고 기존 min 생략 시 base minute 사용 의도를 유지했다. FE emulator도 JSON packet을 보내지만 이번에 실제 통합 실행한 것은 아니다. 개인 기여는 입력 경계와 null 처리 개선이며 원 팀 수집 기능 전체와 구분한다.

## Acceptance criteria와 결정

- [x] 잘못된 HTTP packet은 service/publish 전에 400.
- [x] consumer가 호출하는 SaveService에서도 emulator/DB 조회 전에 검증.
- [x] 정상 요청·최대 600건·12/14자리 시각·유효한 윤년 날짜 허용.
- [x] count 불일치, 빈/null/list null element, 601건, 불가능한 날짜/시간, NaN/Infinity/범위 밖 좌표, 잘못된 상태, int overflow 거부.
- [x] 생략된 min은 base time의 minute을 사용하며 예외가 발생하지 않음.

공유 순수 validator를 HTTP controller와 저장 service에서 호출한다. 단순 bean annotation만으로는 cCnt/list 교차 조건과 strict 날짜 검증을 표현하기 어려워 명시적 검증을 선택했다. 실패는 raw payload를 포함하지 않는 INVALID_INPUT_VALUE로 변환한다. converter의 SMART 날짜 파싱 전에 STRICT uuuu 날짜를 검사한다. 기존 power/geofence converter 동작은 변경하지 않았다.

## 입력 계약

- mdn: nonblank, 20자 이하(DB log 컬럼과 일치). tid: nonblank, 255자 이하. mid/pv/did: 0 이상 Java int.
- cList: 1~600건, null element 금지. cCnt: 실제 개수와 같은 정수 문자열.
- oTime: 12 또는 14자리 숫자, 유효한 실제 날짜/시간.
- min/sec: null 또는 빈 문자열은 기존 기준 시간 유지, 있으면 0~59.
- gcd: 기존 GpsStatus 코드. lat/lon: microdegree 숫자, finite, 각각 ±90,000,000/±180,000,000 이내.
- ang: 0~359. spd/sum/bat: 0 이상 Java int. 배터리 단위와 업무 속도 상한은 아직 확정하지 않아 추가 제한하지 않는다.

600은 운영 측정값이나 프로토콜 원문의 확정 상한이 아닌 임시 유한 guardrail이다. 실제 producer packet 분포를 측정해 조정한다. 구버전에서 허용됐던 잘못된 packet은 이제 거부될 수 있으므로 배포 전 producer fixture를 확인한다.

## 요청 흐름

HTTP deserialize → validator → direct 저장 또는 Rabbit publish. 저장 consumer → SaveService validator → emulator/vehicle 조회 → 날짜·좌표 변환 → JDBC batch insert. 입력 오류에서는 DB lookup/write를 호출하지 않는다. 유효 요청의 기존 transaction·broker acknowledgment 정책은 변경하지 않는다.

## 검증·실패

- 좁은 `./gradlew test --tests '*GpsLogValidationTest' --tests '*GpsLogSaveServiceTest' --console=plain`: 22/22 통과, Gradle 2초.
- 첫 컴파일은 새 테스트의 argThat static import 누락으로 실패해 보완했다.
- `./gradlew test --console=plain`: 전체 271/271 통과, 실패·오류·skip 0, Gradle 44초. XML 집계 및 `git diff --check` 통과.
- controller 테스트는 standalone MockMvc로 HTTP 400/200 및 service 미호출/호출을 확인한다. 인증 필터 검증이 아니며 기존 전체 보안 suite로 회귀 확인한다.
- SaveService에는 실제 converter와 mock 저장소를 사용해 min 생략 결과 시각을 검증했다. 실제 Python Emulator 실행은 하지 않았다.

## 한계·다음 단계

count 제한은 JSON 역직렬화 이후 적용되므로 HTTP body byte 제한·Jackson string 제한·rate limit을 대신하지 않는다. device 인증·요청 위조·replay 차단도 아니다. 중복·역순·자정/시간 경계·장치 재연결의 timestamp identity는 아직 정의해야 한다. min 생략은 저장 service에서 처리하며 기존 streaming comparator의 min 누락 처리와 timestamp gap은 후속 검토 대상이다.

broker에 이미 존재하는 invalid packet은 기존 retry 정책으로 재시도될 수 있다. non-retryable 분류·DLQ·replay는 이번 작업에서 만들지 않았다. HTTP raw malformed JSON 처리 정책도 별도다. 따라서 수집 신뢰성 완성이나 exactly-once를 주장하지 않는다.

## 학습·면접

1. 왜 controller와 consumer 경계에서 둘 다 검증하는가? HTTP 검증을 거치지 않은 과거/직접 발행 메시지가 저장 경로에 도달할 수 있기 때문이다.
2. &와 &&의 차이가 실제로 어떤 버그를 만들었는가? &는 오른쪽도 평가하여 min=null에서 isEmpty 호출이 실패한다. &&는 null일 때 평가를 중단한다.
3. 입력 검증을 하면 중복 처리가 해결되는가? 아니다. 유효한 요청도 재전송될 수 있어 event identity와 DB unique/transaction 정책이 필요하다.
4. 600건 제한으로 메모리 공격을 막았다고 말할 수 있는가? 아니다. 역직렬화 전 byte 제한과 rate limit, 동시 요청 수를 별도로 다뤄야 한다.
5. STRICT 날짜 검증의 이유는? 존재하지 않는 날짜를 다른 날짜로 보정하면 telemetry 원본 시간이 바뀌기 때문이다.

실습: 같은 유효 packet을 두 번 보내면 검증은 모두 통과한다. 이 상황에서 무엇을 중복 key로 삼을지 timestamp/min/sec/MDN 재연결을 고려해 설명한다. 사용자 수행은 미확인이다.

## AI 활용

AI가 세 저장소 호출 형식을 정적으로 비교하고 validator·테스트·문서를 작성 및 실행했다. 기존 optional min을 필수로 바꾸지 않고 null short-circuit을 교정했다. 입력 validation과 device 인증·중복 보장을 구분했다. 실제 장치/운영 트래픽 검증과 사용자 학습 확인은 수행하지 않았다.
