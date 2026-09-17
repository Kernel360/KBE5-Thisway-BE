# CHANGE-031: 지연 Power 이벤트의 현재 차량 상태 덮어쓰기 방지

- 날짜: 2026-09-06
- 브랜치/기준: `codex/statistics-batch-restart`, `7158956`
- 상태: Verified (로컬, 운영 미적용)

## 1. 문제와 근거

기존 LogServiceImpl은 수신할 때마다 powerOn과 OFF 좌표를 덮었다.
CHANGE-027의 잠금과 odometer max는 lost update/거리 가산을 막지만 이벤트 역순을 해결하지 않는다.
정적 반례: 12시 ON → 늦은 11시 OFF → 이전 구현은 OFF/과거 좌표.
원 팀의 Power/Trip 처리에 대한 개인 현대화다. 원 개인 Vehicle/Statistics 기여와
팀 기여의 근거는 original-contributions.md를 따르며 전체 기능을 개인 구현으로 표현하지 않는다.

## 2. Acceptance criteria

- [x] 과거 ON/OFF는 현재 차량 상태·좌표를 바꾸지 않음
- [x] 같은 초 OFF 우선, 동일 종류·시각 재수신의 좌표 유지
- [x] 실제 MySQL의 역순 4-thread와 core rollback 검증
- [x] V5→V6 기존 차량 값 보존 및 전체 회귀 통과
- [x] 한계·학습·면접·운영 전환 기록

## 3. 선택과 실행 흐름

[ADR-009](../../adr/009-vehicle-power-event-ordering.md)에 상태표와 대안을 먼저 정의했다.
최종 선택은 Vehicle에 이벤트 시각을 저장하고 현재 상태 projection만 보호하는 작은 변경이다.
전체 Trip 상태 머신과 거리 분리를 동시에 교체하는 안은 기존 데이터 마이그레이션 범위가 커 분리했다.
처리 시각으로 legacy 기준을 만들어 채우는 안도 근거가 없어 거절했다.

MDN projection → 차량 잠금 → Power 원본 → OFF mileage max → 상태/좌표/time guard →
Trip 저장 → commit → 주소 보정. ON 좌표도 최신 이벤트에 맞춰 갱신한다.
과거 이벤트가 무시되는 것은 현재 상태 projection뿐이다. 원본/Trip 저장은 여전히 수행한다.
V6는 nullable 컬럼 하나를 추가한다. [전환 점검](../../runbooks/vehicle-power-watermark.md) 참고.

## 4. 검증

- 첫 targeted 실행: 33개 중 3개 실패, 23초. 새 fixture MDN의 UUID 36자가
  power_log.mdn VARCHAR(20)을 초과했다. 테스트 식별자만 20자로 맞췄으며 운영 schema를 느슨하게 바꾸지 않았다.
- 재실행: `./gradlew test --tests '*VehiclePowerEventTest' --tests '*LogServiceTest' --tests '*TripAddressEnrichmentIntegrationTest' --tests '*LegacySchemaPreflightIntegrationTest' --console=plain`
  → 33개 통과, 실패/오류/skipped 0, 23초.
- `./gradlew test --console=plain` → 334개 통과, 실패/오류/skipped 0, 1분 24초.
  결과 집계: `build/test-results/test/TEST-*.xml`, 보고서: `build/reports/tests/test/index.html`.
  추가한 domain 6개/MySQL Power 3개/legacy migration 1개가 포함된다.
- `./gradlew sseBrowserTest --console=plain` → Boot/nginx/Chromium 2개 통과, 13초.
  인증·tenant 거부·live·재연결·idle timeout 회귀이며 Power 역순 화면 시연은 아니다.
- `git diff --check` 통과. 새 로그에는 원시 좌표나 장치 credential을 추가하지 않았고 기존 tenant 경계를 유지했다.
- FE unit/build/전체 업무 시연, 실제 Emulator 실행은 재실행하지 않았다. 정적 계약 확인만 수행했다.
- Before는 코드 반례이며 변경 전 실제 장애/부하 측정 결과가 아니다.

## 5. 저장소 영향과 남은 위험

- VehicleResponse DTO 필드는 그대로다. Emulator power generator는 초 단위 onTime/offTime을
  보내며 OFF가 ON을 기억하지 못하면 한 시간 전을 임의 생성한다. 실장치/RFP 계약 검증은 아니다.
- FE CompanyCarDetailPage는 powerOn으로 배지를 표시하지만 GPS 수신 시 자체적으로 true로
  바꾸는 기존 코드가 있다. 서버 guard가 브라우저의 상태 추정까지 고쳤다고 말하지 않는다.
  FE/Emulator 수정·전체 업무 시연은 이번 범위 밖이다.
- 동일 timestamp/종류의 충돌 좌표는 최초 값 유지로 순서 의존성이 남는다.
- Trip 중복/역순/거리 혼합 의미, 원본 Power 중복, strict 입력·미래 시각 제한·장치 인증은 미완료.
- 기존 watermark NULL 차량은 첫 수신부터만 정렬한다. 이미 오염된 값은 자동 복원하지 않는다.
- GPS 위치와 Power 위치의 통합 시각/품질 모델, 다중 JVM 부하·운영 DDL/배포도 미검증이다.

## 6. 학습 기록

- event time vs processing time, 상태 projection, watermark, tie-break, pessimistic lock,
  READ_COMMITTED, additive migration, transaction rollback.
- 연습: 테스트에서 행 잠금이 없을 때 두 요청이 같은 이전 값을 읽는 순서를 그려 보고,
  timestamp 비교만으로 lost update가 해결되지 않는 이유를 설명한다.
- VehiclePowerEventTest → LogServiceImpl → TripAddressEnrichmentIntegrationTest 순서로 읽는다.

## 7. 예상 면접 질문

1. 행 잠금이 있는데 왜 시간 필드가 필요한가요?
   - 잠금은 처리의 직렬화이고 실제 발생 순서는 아니다. 지연 이벤트는 잠금 이후에도 과거다.
2. 같은 시각 ON/OFF 중 어느 쪽이 맞나요?
   - 현재 프로토콜에는 sequence가 없어 진실을 판별할 수 없다. OFF 우선으로 결과를 고정했으며
     같은 초 재시동은 구분하지 못한다. 장치 sequence 도입이 다음 대안이다.
3. 과거 이벤트를 아예 버리면 안 되나요?
   - 현재 상태 반영과 운행 이력 보정은 목적이 다르다. 과거 OFF가 과거 Trip을 종료할 수 있다.
4. 마이그레이션으로 과거 차량 상태도 정확해졌나요?
   - 아니다. 기존 시간 근거를 추측하지 않아 NULL로 남기고 신규 이벤트부터 정렬한다.
5. 미래 시각이 들어오면 어떻게 되나요?
   - 현재 guard만으로는 높은 watermark에 고정될 수 있다. 장치 인증과 시계 오차 정책이
     별도 수집 gate이며, 임의 watermark 초기화로 해결하면 과거 이벤트 덮어쓰기가 다시 열린다.

## 8. AI와 사람의 검증

AI가 코드 분석, 대안·상태표, 구현·테스트·문서 초안을 작성했다.
사용자의 다음 단계 위임 범위에서 bounded projection 개선안을 채택했으며 원 RFP 요구로 꾸미지 않았다.
자동 검증 결과와 사용자의 독립 설명 능력은 별개다. 사용자는 12시 ON/11시 OFF,
동시각 OFF 우선, legacy NULL 한계를 직접 재현·설명해야 한다. 실제 운영 확인은 하지 않았다.
