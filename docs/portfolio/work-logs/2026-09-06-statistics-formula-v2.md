# CHANGE-030: 완료 운행 시간 가동률 V2와 GPS 관측 분리

## 메타데이터

- 날짜: 2026-09-06
- 기준: BE `9694d04`/`codex/statistics-batch-restart`, FE `ec1c819`/`codex/frontend-dependency-security`
- 상태: Verified
- 사용자 판단 위임에 따라 완료 운행 가동 시간 기준을 채택. 실제 회사 소속이나 원 RFP 요구사항을 주장하지 않는다.
- 원래 개인 Statistics/Batch 기여를 잇는 개인 현대화. 기존 팀 전체 구현은 별도 기여 문서 기준.

## 문제와 acceptance criteria

기존 GPS row 수의 1초 가정은 수집 주기/누락과 가동을 혼동했고, 완료 Trip도 시작 날짜에
전체 시간을 배정했다. FE는 분을 h로 표시했다. 이전/새 공식을 섞으면 개선 후 숫자도 의미가 없다.

- [x] per-vehicle union·자정/시간대 clip·미종료/역전 제외·나노초 합산 후 분 내림
- [x] 실제 MySQL 비영 원천 fixture: 2대/300분/10.4167%, 다른 tenant 제외
- [x] GPS 관측·gpsCycle 변경이 가동률을 바꾸지 않음
- [x] 늦은 OFF 명시적 재계산, 동일 row와 완료 JobInstance 정책 유지
- [x] V5 기존 숫자 보존·V1 표시, V1/V2 혼합 거부, 이전 checkpoint 업그레이드
- [x] FE 분 표시·legacy/미집계/부분 집계 경고 및 browser 검증
- [x] 실제 HTTP 회사 경계/quality JSON·잘못된 기간 400 및 최종 전체 회귀

## 선택과 실행 흐름

GPS 주기를 곱하는 임시 수신율 대신 완료 운행 union을 선택했다. 분모는 계산 당시 현재
active fleet이며 역사적 fleet 재현이 아니다. 상세 대안/공식은 [ADR-008](../../adr/008-completed-trip-operation-rate.md).

회사 lock → 당일 `[start,nextStart)` → 완료 겹침 Trip + fleet 수 조회 → 차량별 union →
시간별 나노초/일분 집계 → GPS row 수와 미종료 수 별도 조회 → V2 row 저장 + checkpoint.
GET은 현재 버전 일자만 합산하고 quality를 추가한다. FE는 coverage가 없으면 `-`를 표시한다.
원래 totalDrivingTime 필드는 API 호환상 유지하되 단위는 분이다. peak/low는 기존 earliest tie/양수 최솟값 정책 유지.

## 검증 결과

- 기존 Batch 좁은 회귀: 5개 통과, 13초.
- 새 pure 계산/Batch: 14개 통과, 13초.
- 1차 전체: 323개 통과, 실패/오류/skipped 0, 1분 25초.
- HTTP 계약 test 추가 후 `./gradlew test --console=plain`: **324개 통과, 실패/오류/skipped 0, 1분 27초**.
- 이후 순차 `./gradlew sseBrowserTest --console=plain`: 실제 Boot/nginx/Chromium 2개 통과, 12초.
- FE `npm test`: 11개 통과. `npx playwright test`: 12개 통과, 2.9초.
- FE `npm run build`: 성공, 3.29초. bundle 976.17kB/gzip 298.91kB 경고 유지.
- FE 구현/기록 로컬 commit: `4a0a2f8`. 두 저장소의 원격 push/merge는 수행하지 않았다.
- 초기 patch가 같은 파일 delete/add를 한 요청에 넣어 거부됐다. 파일 변경 전 검증 실패이며
  허용되는 patch 단위로 나누어 반영했다. 테스트 실패를 숨기거나 skip하지 않았다.
- FE 통계 browser는 API fixture 응답이다. 실제 BE/MySQL HTTP와 browser 양쪽을 별도 검증하며
  전체 회사 업무 live E2E/운영 성능 측정으로 표현하지 않는다.

## Before/after와 한계

GPS 없는 완료 운행은 기존 0%에서 V2의 비영 가동률로 계산된다. 이것은 **정의 변경**이지
성능 향상률이 아니다. 중복/겹침은 통계 효과만 union하며 raw Trip 정합성은 별도 과제다.
미종료 Trip, 비정상 시간 구간은 가동에서 제외한다. late OFF 자동 탐지·수정 revision은 없다.
V1 숫자는 삭제하지 않는다. 기존 ADMIN 경로로 승인된 날짜를 재계산하고 [runbook](../../runbooks/statistics-formula-v2.md)을 따른다.
실제 운영 적용, 과거 fleet snapshot, 진짜 GPS 수신율, 대규모 query 성능은 검증하지 않았다.

## 학습 기록

- `[start,end)`는 자정 중복을 없애고, clip은 다른 날의 시간을 넘겨 계산하지 않게 한다.
- 같은 차량 구간 union은 overlap 이중 계산을 방지한다. 서로 다른 차량은 합산한다.
- 원천 미수신, 실제 0, 미집계는 다른 상태다. 공식 version과 coverage를 API 계약으로 표현한다.
- 직접 2대/300분 fixture를 그려 0/9/10/11/23시 가동률을 계산하고 테스트 기대값과 비교한다.

## 예상 면접 질문

1. GPS가 없으면 가동률 0 아닌가요?
   - 수신 실패와 비가동은 다르다. 가동은 완료 Trip 시간, GPS는 저장된 관측 수로 분리한다.
2. 중복 Trip을 DB에서 지우지 않고 union한 이유는요?
   - 통계 중복 효과를 제한하면서 원천 보존. 원본 상태 머신/정리는 별도 근거가 필요하다.
3. 완료 기록만 쓰면 늦은 OFF는 어떻게 하나요?
   - 미종료를 노출하고 영향 날짜를 명시적으로 보정한다. 자동 보정/revision은 아직 없다.
4. 평균을 모든 요청 일수로 나누지 않은 이유는요?
   - 미집계일을 0으로 간주하면 의미가 바뀐다. coveredDays 일별 평균임을 표시한다.
5. 기존 데이터에도 V2 표시만 붙이면 안 되나요?
   - 정의가 다른 숫자를 오인하게 된다. V1을 보존하고 원천 재계산 후에만 V2로 기록한다.

## AI와 사람의 검증

AI가 계약 조사/설계 대안/구현/fixture/회귀/문서 초안을 수행했다. 사용자가 방향 판단을 위임했고
임의 GPS 수신율 보간·과거 데이터 relabel은 거절했다. 사용자의 독립적인 이해는 아직 검증하지 않았다.
면접 전에 위 계산을 손으로 재현하고 미완료 한계를 본인 말로 설명해야 한다.
