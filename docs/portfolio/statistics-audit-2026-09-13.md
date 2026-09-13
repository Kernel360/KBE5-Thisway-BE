# 시스템 정의와 통계 계산 감사

2026-09-13, 현재 작업 트리 읽기 감사. 서비스 로직 변경 및 DB 쓰기 없음.

## 어떤 시스템인가
회사별 업무 차량을 등록하고 장치에서 보낸 시동 ON/OFF·GPS·누적 주행계 관측을 수집하여 현재 위치, 운행 이력, 과거 회사별 가동 통계를 제공하는 멀티테넌트 차량 관제·운영 관리 시스템이다. 배차 최적화·차량 예약·정비·비용 정산 시스템 전체를 구현한 것은 아니다. 배치는 이 원천 데이터의 회사별 하루 통계를 미리 계산하는 내부 작업이다.

## 원천과 공식
- 운행 시간 원천: trip_log의 active=true, 유효한 start/end. 시동 시간이며 정차 포함. GPS 건수로 시간을 추정하지 않는다.
- 같은 차량의 겹치거나 인접한 구간을 합집합 처리하고, 자정 기준 [시작, 다음날 시작)으로 자른다. 다른 차량 시간은 합산한다.
- 총 가동 시간: 차량별 합집합 초 합 /60의 내림. 일별 내림 후 기간 합산하므로 기간 전체 초를 합쳐 내림한 것과 최대 집계일 수 미만 분 차이가 날 수 있다.
- 시간대 가동률: 해당 1시간에 차량들이 가동한 시간 합 /(fleet 차량수×3600초)×100.
- 하루 평균 가동률: 24시간 가동률 평균. 일 합계 초/(fleet×86400)×100과 같다.
- 시동 횟수: 해당 날짜의 DISTINCT(vehicle_id,start_time) 수. 미종료도 포함하므로 완료 운행 건수가 아니다.
- GPS 관측 수: 해당 날짜의 gps_log 저장 행 수. 중복 방지 후 관측 수이며 거리·가동 시간·수신 성공률이 아니다.
- 미종료: 날짜 끝 이전에 시작하고 현재 end가 없는 운행. 여러 날 걸치면 기간 조회의 합계는 고유 건수 아닌 건·일이다.
- 기간 평균: formulaVersion2가 있는 집계일만 단순 평균. 미집계일을0으로 넣지 않는다. 회사 차량수가 날짜별로 달라도 차량·시간 가중 평균은 아니다.
- 출발지: 범위 내 trip_log의 on_addr별 건수와 비율 상위3개. 주소문자열 그룹이며 행정구역 분석·GPS 방문지 빈도와 다르다. null 주소도 그룹에 포함될 수 있다.

## 독립 재계산 결과
읽기 전용 Python은 서비스의 계산 함수를 호출하지 않고 원천 구간 정렬/합집합 및 초 합계를 별도로 계산했다. 현재 V2 통계1행(2026-09-08)을 대조했다.
- fleet2, 구간 합3721초 →62분 저장 일치.
- 3721/(2×86400)×100 =2.1533564814814814%, 저장 일치.
- 시동34회, GPS737행 일치.
- 이 결과는 로컬 합성 데이터 한 날짜의 대조이며 실제 사업장 데이터 품질을 보장하지 않는다.
- 결과와 재현 스크립트: ../experiments/2026-09-13-statistics-audit/.

## 정확성 판단과 남은 한계
1. 시간 합집합·일 경계·시간대 분할의 수학은 타당하다. 미종료 시간을0으로 가정하지 않고 계산에서 제외하므로 아직 닫히지 않은 운행이 있으면 가동 시간은 잠정 과소 집계다.
2. **역사적 분모 한계:** fleet는 대상 날짜 실제 보유차량 이력이 아닌 최초 계산 당시 active 차량 목록이다. 오래된 날짜를 처음 계산할 때 당시 차량수를 복원할 수 없다. 한번 저장한 뒤의 분자/분모 재현성은 보장하지만 역사적 사업 지표의 정확성과 다르다.
3. **평균 의미:** 하루 1대100%, 다음날9대0%면 코드의 일평균은50%, 차량시간 가중 평균은10%다. 둘 중 어떤 질문에 답할지 정해야 한다. 현재 FE는 일별 평균이라고 설명한다.
4. **최소 명칭:** 0% 시간은 제외한다. UI의 '최소 가동 시간대'를 '가동이 있었던 시간 중 최소'로 읽어야 한다. 일반적인 24시간 최솟값은 아니다. 동률은 더 이른 시간이다. 전부0이면 API hour0을 반환하지만 FE는 '-'로 표시한다.
5. **원천 품질:** ON/OFF 관측 구간은 실제 물리적 이동 시간과 다르고 OFF만 먼저 받은 경우에도 onTime/endTime이 있으면 포함될 수 있다. 차량 주행거리는 별도 누적주행계 차이이며 이 통계 API의 핵심 시간 지표와 분리된다.
6. **집계 누락:** 새벽2시 전날 배치가 기본이다. 서버가 꺼져 놓친 날짜를 자동으로 전부 메우는 것은 아니다. correction은 기존 날짜의 늦은 관측 보정이며 미집계일 backfill이 아니다. 현재 시연 DB 저장 통계가9월8일1행인 점도 조회 coverage와 함께 해석해야 한다.
7. **출발지 시점:** 시간/가동률은 저장된 일 집계를 읽지만 출발지는 원천 주소를 실시간 조회한다. 주소 재시도 등으로 다른 시점의 정보가 섞일 수 있다. 동일 스냅샷의 보고서로 주장하지 않는다.

## 배치 실패와 재시작
StatisticBatchConfig의 targetDate만 identifying parameter다. 같은 날짜 실패 재시작은 같은 JobInstance, 새 JobExecution이다. StatisticsCompanyWorker는 REQUIRES_NEW에서 통계와 checkpoint를 함께 저장한다. 회사A 완료 후B 실패면A commit을 보존하고 재시작 때A를 건너뛴다. 이미 COMPLETED인 날짜에 늦은 OFF/GPS가 도착하면 기존 job 강제 재실행 대신 correction worker와 revision을 사용한다.

## 코드 근거
- company/statistics/domain/CompletedTripTime.java
- company/statistics/domain/StatisticCalculationService.java, StatisticQueryService.java
- company/statistics/infrastructure/StatisticsSnapshotSourceRepository.java
- company/statistics/domain/StatisticPersistenceService.java
- company/statistics/StatisticBatchConfig.java 및 application/StatisticsCompanyWorker.java
- FE utils/statisticsPresentation.mjs, CompanyStatisticsPage.jsx

## 권장 표현
'최초 집계 시점의 차량 목록을 기준으로, 완료 운행의 시동 시간을 하루 단위로 합산하고 실패·지연 도착을 재시작/보정하는 차량 관제 시스템'으로 설명한다. 실제 이동 효율, 운전자 생산성, 실시간 배차율 또는 역사적 차량 보유 이력 정확성을 보장한다고 표현하지 않는다.

## 집중 회귀
CompletedTripTimeTest, StatisticsBatchIntegrationTest, StatisticsCorrectionIntegrationTest를 --rerun-tasks로 실행: {'tests': 30, 'failures': 0, 'errors': 0, 'skipped': 0} . 기본 전체539개 회귀와 별도로 통계 관련 사례를 재실행했다.
