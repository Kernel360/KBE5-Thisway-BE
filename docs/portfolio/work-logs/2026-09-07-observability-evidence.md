# CHANGE-050 — 로그 보호, 모니터링 대시보드와 지속 부하·복구 증거

2026-09-07 시작. 사용자 요청: 로그/모니터링 등을 시니어 차량 운영 백엔드 관점에서 검토하고 실행계획을 정리해 작업 시작. 실제 회사 소속을 주장하지 않는다.

## 문제와 기준선

BE `79b77c5`, 기존 draft PR #235의 소스를 기반으로 로컬 `codex/observability-evidence`를 만들었다. 원 팀의 Actuator/Micrometer/Prometheus/Grafana 설정과 기존 현대화의 경보8개는 있었지만 대시보드 JSON·실제 scrape/proxy 검증과 지속 부하 증거는 없었다. 이전 본측정240건/약0.53초를 지속 용량으로 일반화할 수 없었다.

LoggingFilter는 raw URI를 기록했고 공통 HTTP/security/Rabbit 예외 경로는 Throwable 또는 message를 출력했다. validation/JWT/DB 예외에 사용자 입력이나 비밀값이 들어갈 수 있다. GPS publisher의 별도 worker와 consumer의 MDC 관리도 명시적 전파·복원이 필요했다.

## 실행계획과 acceptance

[실행계획](../observability-execution-plan.md), [재현 runbook](../../runbooks/observability-evidence.md).

- 원문 URI/query/header/body/Throwable을 공통 경로에 출력하지 않고 악성 trace 입력·예외 후 MDC 복원을 검사한다.
- broker 수락, consumer transaction 반환, DB 고유 row 수, AMQP ack를 서로 다른 성공 조건으로 설명한다.
- 실제 Prometheus `up=1` 두 target과 PromQL 자료, Grafana18개 panel/API proxy를 확인한다. 화면 캡처를 별도 검토한다.
- 장치8, 20/40/80 requests/s 각각30초의4,200건, consumer pause80건, duplicate24건에서 기대 row/queue 결과를 검증한다.
- 기본 전체 회귀와 설정/규칙 검증을 통과하고 환경·실패·제약을 원시 자료와 함께 보존한다.

## 선택과 흐름

1. 완전한 trace backend 신규 설치 대신 우선 HTTP→publisher worker→consumer **로그 correlation**을 구현했다. 서버 MDC/검증된 hex32를 전달하고 scope 종료 시 이전 값을 복원한다. 이는 span 트리와 trace export의 대체가 아니다.
2. 예외 원문 마스킹 대신 원문을 수집하지 않는다. exception class와 프로젝트 code site만 기록한다. 진단 정보가 줄어드는 비용은 있지만 regex 마스킹 누락을 피한다. 합성 테스트에서 원인을 재현한다.
3. key-value access event에 route template/status/dispatch duration/async 여부만 남긴다. SSE 연결 수명으로 오해하지 않는다. 기존 파일 보존 설정은 유지하며 중앙 로그 저장소는 실제 권한·retention·비용 합의가 필요하다.
4. consumer timer는 transactional service proxy 반환을 기준으로 `committed`/`failed`를 나눈다. 이미 저장된 duplicate도 committed 시도에 포함되므로 count를 unique GPS row로 부르지 않는다. publisher active/queued gauge와 HTTP histogram을 추가했다.
5. 기존 운영 remote_write와 분리된 Compose를 만들었다. loopback Grafana Viewer,18개 JSON panel, Prometheus24시간 보존·외부전송없음. 별도 fixture는 실제 Prometheus/Grafana 컨테이너에서 질의와 화면을 확인한다.
6. 최대 처리량을 찾는 무제한 요청 대신 bounded generator(8workers/queue256)의90초 목표 부하를 사용했다. 실패·생성기포화(-1)도 기록한다. HTTP 시간은 client executor 대기/DB 저장을 포함하지 않는다.

## 발견한 실패와 수정

- 최초 compile에서 직접 생성하던 consumer test의 MeterRegistry 인자가 누락됐다. 테스트 생성 경로에 SimpleMeterRegistry를 명시했다.
- 단계별 코드 연결 중 실험 메서드 미완성과 SimpleMeterRegistry의 AutoCloseable 미지원으로 compile이 실패했다. 최종 소스 연결과 명시적 close 후 다시 검증했다.
- 첫 Prometheus 시작이 실패했다. 임시 파일을 컨테이너의 비root 사용자도 읽을 수 있도록 copy mode0644로 명시한 뒤 기동됐다. 첫 실패의 내부 에러 로그가 충분히 남지 않아 권한이 유일한 원인이었다고 단정하지 않는다.
- 다음 실행에서 실제 scrape `up=0`이 실패 조건을 검출했다. test resource가 운영 설정을 대신하므로 endpoint exposure/histogram을 fixture에 명시했다. 이후 두 target up1을 확인했다.
- 첫 fullPage screenshot은 Grafana 가상 스크롤 때문에 그래프가 비어 있었다. 증거로 채택하지 않고 viewport별 캡처를 테스트 종료 이전에 실행하도록 연결했다. 종료한 임시 컨테이너에 수동 재접속한 시도도 연결 거부됐으며 UI 성공으로 기록하지 않는다.

- 전체466개 회귀 첫 실행은2개가 실패했다. 기존 민감정보 테스트가 이전 `Request [...]` 형식을 기대하던 부분이며 새 route-template event 형식으로 양성 확인만 갱신했다. 비밀번호/토큰/인증코드 부재 assertion은 유지했다.

- 추가 읽기 검토에서 CustomException에 임의 message를 덧붙이는 생성자가 있음을 확인했다. 기존 Rabbit handler도 message 대신 고정 ErrorCode를 출력하도록 수정하고 원문 payload/custom message 부재 회귀를 추가했다. 예상하지 못한 security filter 예외는 ERROR 수준을 유지한다.

- 실제 캡처에서 오류율0일 때 Grafana 자동 축이10000%까지 표시되는 문제를 발견했다. 비율/health 패널의 축을0~1로 고정하고 최종 화면을 다시 확인한다.

- 화면에서 짧은 consumer pause 적체가 exporter/scrape 사이에 해소돼 그래프에 보이지 않는 한계를 확인했다. 최종 실험은 실제 PromQL에서 ready>=80이 보일 때까지 pause를 유지한 뒤 재시작한다. broker 전체 합계 패널과 storage queue 단독80건 assertion은 구분한다.

- Prometheus가 적체를 수집해도 Grafana range query의 더 큰 step이 짧은 peak를 생략할 수 있었다. ready 패널을 명시적인1분최대값으로 변경했다. 현재 backlog가 아닌 window peak임을 제목/설명에 밝히고 실제 적체 PromQL 응답도 raw report에 보존한다.

## 첫 성공 표본

[첫 실행 원본](../../experiments/2026-09-07-observability/first-run.result.json). 2026-09-07T14:46:30Z. 실제 관측성 test1개,2분2초 성공.

| 목표 requests/s | 실제 requests/s | HTTP p95 ms | HTTP p99 ms | 오류 |
| --- | --- | --- | --- | --- |
|20|20.03|14.18|21.22|0/600|
|40|40.01|8.97|11.08|0/1200|
|80|80.02|6.42|8.51|0/2400|

consumer 정지 중80건 DB증가0/ready80, 재시작→기대 row/ready0까지115.39ms.24개 중복 추가0, 최종4,296고유row/DLQ0.1초 간격90개 자원 표본에서 DB pending 최대0, active최대2, publisher queued최대0이었다. 순간 spike 부재를 보장하는 샘플링은 아니다.

## 해석과 남은 경계

- 단계별 응답 차이는 워밍업·시간순서·host 부하 효과가 섞여 있다. 변경 전후가 아니므로 성능 개선율을 쓰지 않는다. 이전0.53초 실험과 비교해 처리율 감소/증가를 주장하지 않는다.
- 같은 호스트의 부하 생성과 Docker이며 CPU/memory quota·background workload를 고정하지 않았다. 애플리케이션 `org.thisway` logger는 fixture에서 WARN이다. 운영 INFO 로그 비용을 포함한 benchmark가 아니다.
- 제어된 consumer stop은 crash/broker restart/전원 장애의 대체가 아니다. 복구시간은 batch 조건을20ms polling한 관측 상한이며 개별 message lag가 아니다.
- HTTP는 broker 접수 시간, consumer timer는 queue 대기를 제외한다. 개별 observation의 HTTP 접수→DB commit 지연 측정은 다음 실험으로 남긴다.
- 기존 `/actuator/prometheus` public policy의 운영 ingress 격리, 중앙 로그·실제 Alertmanager 수신, 장기 soak, 동일환경 A/B와 capacity 한계는 아직 검증하지 않았다.
- 이번 변경은 로컬 커밋으로 보존한다. 새 변경의 push/merge/운영 배포는 수행하지 않았다. 원 팀의 기초 모니터링 구성과 이번 AI 지원 개인 현대화를 구분한다.

## 학습과 면접

직접 실습: consumer를 정지하고 HTTP200/DB증가0/ready증가를 관찰한 뒤 재개한다. Grafana의 transaction count와 SQL unique row count가 중복 재전송 후 달라지는 이유를 설명한다.

1. HTTP200, publisher confirm, DB commit, AMQP ack가 다른 이유는? 네 경계 사이 실패와 재전달 가능성을 시간순으로 설명한다.
2. trace ID를 Prometheus label에 넣으면 왜 문제가 되는가? 요청마다 늘어나는 시계열 수와 비용을 설명하고 로그 correlation과 지표 집계의 역할을 구분한다.
3. p95가 80rps에서 더 작다고 확장성이 좋아졌다고 할 수 있는가? warmup/순서/표본/동일환경 반복과 A/B의 필요성을 답한다.
4. 예외 원문을 버리면 장애를 어떻게 조사하는가? code site+correlation+고정error code로 범위를 좁히고 합성 재현을 사용한다. 민감값을 다시 운영 로그에 찍지 않는다.
5. no data와0의 차이는? 수집 실패·미발생 counter·분모0·실제0을 나눠 설명한다.

AI가 코드 분석·구현·검증·문서화를 수행했다. 사용자는 시니어 관점의 실행을 요청했으며 세부 구현의 독립 이해·운영 경험을 검증한 것은 아니다. 분산추적/Loki를 곧바로 설치하는 확장 대신, 안전한 로그와 실제 수집 증거를 먼저 선택했다.


## 최종 결과 — 2026-09-08 KST

[최종 raw](../../experiments/2026-09-07-observability/result.json), [source manifest](../../experiments/2026-09-07-observability/source-manifest.json), [화면](../../experiments/2026-09-07-observability/dashboard-middle.png), [browser 기록](../../experiments/2026-09-07-observability/browser.json).

최종 raw 시각: `2026-09-07T15:05:46.046922Z`. 이전 성공 표본을 보존하고 최종 결과와 분리했다.

| 목표 rps | 측정 rps | HTTP p95 ms | HTTP p99 ms | 오류 |
| --- | --- | --- | --- | --- |
|20|20.02|17.40|21.54|0/600|
|40|40.02|9.87|12.29|0/1200|
|80|80.02|6.67|9.08|0/2400|

총 본측정4,200건의 관측 오류0. consumer 정지 중storage queue80건/DB증가0이며 Prometheus 실제 응답에서도80을 확인했다. 재개 후 기대 고유row4,296/ready0까지154.98ms.24개 중복 재전송의 추가row0, DLQ0. 이 복구시간은 현재 fixture의 조건 확인까지 걸린 시간이며 운영 RTO가 아니다.

실제 Chromium viewport3개에서 그래프/단위/축/적체80 peak를 육안 확인했다. page error0. 경보와 주소/통계 worker가 발생하지 않은 일부 패널은 No data이며 해당 worker 동작 증거로 사용하지 않는다. 중간 화면 상단/하단은 스크롤 경계로 일부 패널이 잘리지만3개 캡처가 서로 겹쳐 주요 패널을 확인할 수 있다.

검증 명령과 결과:

- `./gradlew test observabilityEvidenceTest --console=plain`:4분8초 성공. 기본467개 실패/오류/skipped0. 이후 변경은 실험 fixture와대시보드에 한정됐다.
- `./gradlew observabilityEvidenceTest --console=plain`: 적체 실제관측/1분peak/최종 raw·화면을 포함한 최종1개,2분9초 성공.
- `./gradlew fleetEvidenceTest --console=plain`: 기존 업무 API·통계·중복 경로1개,21초 성공.
- `docker compose -f infra/observability/compose.yml config --quiet`: 성공.
- `promtool check config --syntax-only ...`와 기존8개 경보 synthetic 규칙 검증: SUCCESS.
- 변경 파일 Gitleaks directory scan: 탐지0. `git diff --check`와문서local링크,26개source/configuration SHA256 대조 확인.

운영 metrics 접근 격리·로그 저장소/retention·실제 알림 수신·개별 관측의DB commit지연·장기soak/A-B는 [다음 실행 우선순위](../observability-execution-plan.md)에 남긴다. 새 숫자는 환경과 제한을 함께 적어 포트폴리오에 사용한다.
