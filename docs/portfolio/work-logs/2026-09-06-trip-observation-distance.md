# CHANGE-032: 운행 관측 멱등 처리와 계기값·거리 분리

- 날짜: 2026-09-06
- 작업: AI 지원 개인 현대화
- 브랜치/기준: BE `codex/statistics-batch-restart@d79e4c4`, FE `codex/frontend-dependency-security@4a0a2f8`
- 상태: Verified (로컬, 원격/운영 미반영)
- 연결된 FE 로컬 commit: `8fbd0da` (nullable 거리/SSE 계기값 차감/서버 상태 확인)

## 1. 문제와 근거

- ON마다 새 Trip을 만들었고 OFF는 단건 조회 결과를 덮었다. OFF-first 후 ON이 오면
  같은 운행이 두 행으로 갈라질 수 있었다. 재수신 시 주소도 NULL로 지우고 재조회했다.
- Trip.totalTripMeter가 ON에서는 시작 누적값, OFF에서는 종료 누적값이었다. 시작1000/종료1500을
  1500m 운행으로 응답했다. ON 없는 OFF는 모르는 거리를 0m로 저장했다.
- FE는 null/1000을 0.0km로 만들고, SSE 누적값을 운행 거리에 직접 대입했다.
- 원 팀 Power/Trip/FE 구현의 개인 현대화다. 원 개인 Vehicle/Statistics 기여는
  original-contributions.md의 Git/PR 근거를 따르며 팀 기능 전체를 개인 구현으로 표현하지 않는다.

## 2. Acceptance criteria

- [x] 동일 ON/OFF의 중복과 OFF-first를 하나의 종료 운행으로 처리
- [x] 충돌 관측은 409로 원본 보존, 주소 재조회/지우기 없음
- [x] 시작/종료 누적값과 차이를 분리, 누락·감소·legacy는 null/이유 반환
- [x] MySQL unique/check, 4-thread, core rollback, legacy duplicate 보존
- [x] HTTP/FE의 확인 불가와 실제 0 구분, SSE는 시작 계기값 차감
- [x] 전체 회귀와 최종 기록

## 3. 대안과 결정

[ADR-010](../../adr/010-trip-observation-identity-and-distance.md)에 구현 전 상태표를 기록했다.

- 전체 vehicle/startTime unique: 단순하지만 과거 중복 행을 정리해야 하므로 거절.
- 선조회만: 동시에 같은 운행을 만들 수 있어 DB 방어선이 필요.
- **신규 nullable identity unique + 차량 잠금**: legacy 증거를 보존하고 새 운행만 정합성을 적용.
  legacy 동일 키는 409 검토 gate라 운영 전환 때 과거 미종료 운행 처리 비용이 생긴다.
- **distance NULL**: 과거 혼합값을 추정하지 않고 누락/역전과 실제 0을 구분한다.
  FE 호환 수정과 명시적 거리 상태가 필요하지만 잘못된 정확성을 주장하지 않는다.

## 4. 구현과 흐름

1. Power 요청의 MDN → VehicleReference → 차량 행 잠금.
2. raw Power 저장, Vehicle odometer max/이벤트 시간 guard 적용.
3. Trip 입력의 필수값/시간 순서/계기값/좌표 검사 → Trip service도 차량 잠금 확보.
4. vehicle/startTime 최대 2행 조회. legacy 또는 여러 행은 17003/409.
5. 신규 운행 identity 생성 또는 domain 관측 적용. 동일 값은 no-op, 다른 관측은 17002/409.
6. start/end odometer가 모두 있고 감소하지 않았으면 차이 저장. OFF-first의 늦은 ON은
   종료를 취소하지 않고 누락된 시작 관측만 보충한다.
7. 실제 추가한 ON/OFF 관측만 AFTER_COMMIT 주소 보정. core 실패는 Power/Vehicle/Trip 함께 rollback.

V7에는 신규 필드, nullable unique, 관측/거리 CHECK, 조회용 vehicle/startTime index를 추가했다.
README의 모든 운행 데이터가 RabbitMQ로 수신된다는 기존 표현을 GPS/Power 실제 경계로 교정했다.
기존 total_trip_meter는 그대로 보존하며 신규 행의 이 legacy 필드는 호환용 0이다.
이 값은 API 거리의 원천이 아니며 신규 코드에서 거리로 사용하지 않는다.
`active=false/true`의 기존 미종료/종료 의미는 유지한다.

TripLogBriefInfo/TripLogDetailResponse의 tripMeter는 nullable 실제 거리와 distanceStatus,
CurrentDrivingInfo는 nullable 거리와 startOdometer를 반환한다. 실시간 raw totalTripMeter는
여전히 누적 계기값이므로 FE가 시작값을 차감한다. GPS 없음/계기값 감소는 확인 불가다.

FE의 GPS만으로 powerOn/startTime을 생성하던 추정을 제거했다. 서버 상태를 60초마다
조회해 미운행→운행 전환도 반영한다. 모든 열린 차량 상세 화면의 polling 요청이 늘어나며
최대 약 60초의 정상 polling 지연이 있다. 즉각적 Power push나 polling 부하 개선은 아니다.

## 5. 검증과 실패 기록

- 초기 기존 회귀 실행 성공: 25초.
- 신규 domain/MySQL 회귀: 37개 중 1개 실패, 22초. CHECK는 실제로 잘못된 거리 쓰기를
  차단했지만 테스트는 DataIntegrityViolationException을 기대했다. 실제는
  UncategorizedSQLException의 MySQL 3819였다. root SQLException의 오류 코드와
  저장값 보존을 검사하도록 교정했다. 제약을 완화하거나 실패를 숨기지 않았다.
- `./gradlew test --tests '*TripObservationTest' --tests '*TripLogServiceTest' --tests '*TripAddressEnrichmentIntegrationTest' --tests '*LegacySchemaPreflightIntegrationTest' --tests '*TripLogTenantIntegrationTest' --console=plain`
  → **57개 통과**, 실패/오류/skipped 0, 23초. 실제 MySQL과 HTTP tenant/거리 계약 포함.
- FE `npm test`: **13개 통과**. `npx playwright test`: **15개 통과**, 2.9초.
- FE `npm run build`: 성공, 3.67초, main 976.09kB/gzip 298.90kB. 500kB chunk 경고는 남음.
- `./gradlew test --console=plain`: **349개 통과**, 실패/오류/skipped 0, 1분 26초.
- `./gradlew sseBrowserTest --console=plain`: 실제 Boot/nginx/Chromium **2개 통과**, 11초.
  인증/tenant/live/재연결/idle timeout 회귀이며 전체 운행 업무 시연은 아니다.
- 양쪽 저장소 `git diff --check` 통과. Emulator 수정 없음, 수집 endpoint의 인증 미완료는 유지된다.

테스트 결과 XML/HTML은 BE build/test-results 및 build/reports/tests,
FE browser report는 Playwright 실행 결과를 따른다. CI/운영 측정 결과가 아니다.

CHANGE-027의 4-thread fixture는 같은 ON/OFF인데 다른 누적값을 보내고 있었다.
새 정책에서는 충돌이므로 서로 다른 운행 키의 1000/1500/1200과 같은 1500 재전송으로
수정해 odometer max 검증을 유지했다. 동일 운행 ON/OFF 동시 재전송과 충돌 rollback은
새 테스트로 별도 검증했다. 이전 기록을 새 정책 결과로 소급 해석하지 않는다.

## 6. 결과와 한계

- fixture 시작1000/종료1500 → 신규 거리500m. 재전송/역순이어도 Trip 한 행.
- OFF-only의 1500은 거리 미확정이며 기존 raw 값이나 legacy 값을 삭제하지 않는다.
- 원본 Power 재전송 행은 여전히 쌓인다. '모든 계층 exactly-once'나 무유실이 아니다.
- 같은 관측의 비교 대상은 Trip 필드이며 gcd/장치 메타데이터 전체가 아니다.
- 거부된 충돌 payload는 transaction rollback되어 DB에 별도 보관되지 않는다.
- device sequence/세션 없음, 같은 초 운행 충돌, 잘못된 미래 시각, strict 문자열 파싱/인증은 후속 gate다.
- Emulator OFF의 ON 시각 임의 fallback을 소스로 확인했다. 실제 장치/RFP 의미나 전체 Emulator 실행은 미검증.
- V2 통계는 이전처럼 endTime이 있는 유효 시간 구간을 사용한다. OFF-only의 전달된 시작 시각이
  실제 ON 관측이라는 보장은 없다. 거리 unknown 표시는 통계 시간 구간의 진실성을 새로 보증하지 않는다.
- 늦은 ON으로 주소/원천이 보정되어도 기존 통계 자동 backfill/revision은 없다.
- 과거 데이터 정정, 실제 배포/DDL 잠금 시간, 다중 JVM kill, p95/처리량은 미검증.
- [전환 runbook](../../runbooks/trip-observation-v7.md)의 legacy/혼합 writer/rollback gate를 따른다.

## 7. 공부할 개념과 코드 읽기

1. TripObservationTest에서 ON/OFF 순서를 바꾼 입력과 기대값을 먼저 읽는다.
2. TripLog.observe의 no-op/충돌/보충 흐름과 distanceFrom을 코드로 설명한다.
3. TripLogServiceImpl의 차량 잠금과 V7 unique가 각각 막는 경쟁 조건을 구분한다.
4. TripAddressEnrichmentIntegrationTest에서 commit 뒤 외부 API와 충돌 rollback을 확인한다.
5. FE tripDistance와 브라우저 테스트에서 누적값·거리·unknown·0의 차이를 확인한다.

개념: idempotent projection, observation identity, partial state, nullable data quality,
unique NULL 의미, CHECK/SQL NULL, READ_COMMITTED, 원자적 rollback, 호환 배포.
연습: 시작1000/종료900을 받고도 종료시각은 보존하되 거리에는 무엇을 반환해야 하는지 설명한다.

## 8. 예상 면접 질문

1. OFF가 먼저 왔는데 왜 운행을 삭제하거나 거절하지 않았나요?
   - 지연/유실과 구분할 수 없어 종료 관측을 보존하고 시작 관측만 누락으로 모델링했다.
2. 잠금과 unique를 둘 다 쓰는 이유는요?
   - 잠금은 같은 차량의 read-modify-write를 직렬화하고 unique는 우회한 신규 중복 insert도 막는다.
3. 왜 null과 0을 나눴나요?
   - 모르는 거리와 실제 이동 없음은 다르다. 계기값 감소도 임의 0으로 보정하지 않는다.
4. 기존 기록에 같은 키가 있으면 어떻게 하나요?
   - 자동 병합하지 않고 409 검토 gate를 둔다. 원본 근거 없는 과거 값 재해석을 피하는 비용이다.
5. AI로 만든 변경을 본인 역량으로 어떻게 설명하나요?
   - AI 지원 사실을 공개하고, 직접 재현한 반례·선택 이유·대안·한계를 설명한다.
     사용자의 독립 설명을 자동 테스트 통과로 대체하지 않는다.

## 9. AI와 최종 포트폴리오 검토

AI가 분석·설계 대안·코드·테스트·문서 초안을 작성하고 도구로 검증했다.
사용자는 다음 단계 진행을 위임했으며 legacy 자동 변환/중복 삭제는 채택하지 않았다.
실제 운영 효과와 사용자의 코드 이해도는 AI가 확인하지 않았다.
사용자 요청에 따라 [최종 학습·면접 검토 계획](../learning-review-plan.md)을 제출 checklist에 추가했다.
프로젝트 마지막에 원 개인 기여/AI 지원 현대화/실제 검증을 대조해 어필 사례를 확정한다.
