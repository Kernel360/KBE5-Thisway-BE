# 코드 읽기와 최종 면접 검토 계획

2026-09-06 사용자 요청: 구현을 이어가되 프로젝트 마지막에 면접 어필 가능 항목을 확인한다.
이 문서는 학습·검토 계획이며 사용자가 이미 코드를 이해했다는 인증이나 최종 성과표가 아니다.

## 읽는 순서

처음부터 모든 파일을 줄 단위로 외우기보다 서비스의 전체 흐름을 먼저 파악하고,
대표 문제를 아래 순서로 깊게 읽는다. 읽지 않은 코드를 본인이 설명 가능한 구현이라고 말하지 않는다.

| 순서 | 읽을 코드/테스트 | 설명할 핵심 |
| --- | --- | --- |
| 1 | VehicleService, VehicleRepository, Vehicle, VehiclePowerEventTest | 회사 권한 범위, 차량 잠금, 이벤트 시각과 누적 계기값 |
| 2 | LogServiceImpl → TripLogServiceImpl → TripLog, TripObservationTest | 요청부터 저장까지, duplicate/conflict, OFF-first, 확인 불가 거리 |
| 3 | TripAddressEnrichment, TripAddressEnrichmentIntegrationTest | commit 이후 외부 API, 실패 보존, 자동 비동기가 아닌 이유 |
| 4 | StatisticsCompanyWorker, StatisticCalculationService, StatisticsBatchIntegrationTest | 회사별 commit/checkpoint, restart, 완료 운행 시간 V2 |
| 5 | GpsLogProducer, GPS 저장/소비 코드와 관련 테스트 | broker confirm과 DB commit의 차이, unique, retry/DLQ/replay |
| 6 | tenant API 테스트, SSE 테스트, FE 거리/SSE adapter | 보장하는 경계와 화면 계약, 재연결의 한계 |

모든 이름은 저장소에서 검색해 실제 위치/호출자를 확인한다. 코드 탐색에서 발견한
구현과 문서의 차이는 work log로 보완한다. 원 기여 범위는 original-contributions.md를 따른다.

## 한 주제당 독립 설명 확인

1. 테스트를 실행하기 전에 입력과 기대 결과를 먼저 말한다.
2. 요청 → 조회/잠금 → 상태 변경 → commit/실패 → 응답을 코드에서 짚는다.
3. 반례 한 가지를 직접 만든다. 예: OFF를 먼저 보내거나, 두 회사 중 한 회사에서 실패시키기.
4. 선택하지 않은 대안과 채택한 방법의 비용을 설명한다.
5. 테스트로 증명하지 못한 운영 조건을 한 가지 이상 말한다.

## 프로젝트 마지막에 할 어필 검토

- 기업용 차량 운영 관리 서비스라는 문제 정의와 시연 흐름을 간결하게 정리한다.
- 원 팀 구현 / 원 개인 담당 / AI 지원 개인 현대화의 경계를 Git·PR로 확인한다.
- 대표 사례 2~3개를 선정하고 문제 → 선택 → 검증 → 한계 순서로 발표한다.
- 주장마다 해당 commit·테스트·측정 자료를 연결한다. 테스트 개수를 개선율로 환산하지 않는다.
- 사용자가 직접 설명한 내용만 이력서/면접 답변의 주력 사례로 채택한다.
- 미측정 처리량·p95, 무유실, 실제 기업 납품/운영, 구현하지 않은 AI 기능을 성과로 넣지 않는다.
- 최종 코드/문서/원격 CI/시연 상태를 다시 확인한 뒤 최종 소개문을 확정한다.

현재 예상 후보는 회사별 통계 재시작, 운행 관측의 정합성, 외부 API 장애 경계다.
이 후보 선정은 최종 면접 검토를 대신하지 않는다.
