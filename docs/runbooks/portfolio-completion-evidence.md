# 취업 포트폴리오 증거 재현

추가 제품 기능보다 저장 신뢰성과 측정 근거를 우선한다. 모든 수치는 고정 synthetic fixture를 사용하는 로컬 실험이다. 실제 회사 사용자·차량·수신자는 사용하지 않는다.

## 저장 지연과 반복 부하

1. Java21, Docker, 인접 FE의 Node24·npm 의존성·Playwright Chromium을 준비한다.
2. `./gradlew test --console=plain`으로 회귀를 확인한다.
3. `./gradlew observabilityEvidenceTest --console=plain`으로 30초씩20/40/80RPS, consumer pause/recovery, duplicate, MySQL query 비교를 실행한다.
4. `python3 scripts/observability/repeat-evidence.py`는 각100초씩 세 단계, 회당5분을 fresh containers에서3회 실행한다. 이미 결과 디렉터리가 있으면 덮어쓰지 않는다. 재실험 시 기존 결과를 별도 날짜/실험 폴더로 보존하고 출력 경로를 변경한다.

한 packet의 장치 인증 후 `GpsLogProducer.sendGpsLog` 진입 시 서버 epoch millis를 AMQP header에 넣는다. 유한 publisher queue→broker queue→consumer→transactional service가 돌아오는 시점에 `gps.admitted.to.commit` timer를 기록한다. Spring transaction proxy 반환은 commit 뒤이며 broker consumer ACK 완료는 포함하지 않는다. 인증·본문 파싱 이전 HTTP ingress, 장치에서 발생한 시간, 직접 저장 모드는 이 측정 범위 밖이다.

저장 지연이 HTTP 왕복보다 짧을 수도 있다. 시작점이 인증 이후이고, HTTP 응답이 발행 확인을 기다리는 동안 consumer가 병렬로 commit할 수 있기 때문이다.

중복도 정상 commit된 소비 시도에 포함한다. timer count를 고유 GPS row count라고 부르지 않는다. 구형 메시지·수동 replay에 시작 시간이 없으면 missing, 형식 오류는 invalid, 미래 시각은 clock_skew counter에 남긴다. 성공 timer에0으로 넣지 않는다. 서로 다른 서버에서는 clock sync가 필요하며 과거 방향의 clock skew는 여기서 판별할 수 없다. fixture 원시 표본은 각 phase의 모든 소비 완료를 기다린 후 수집한다.

PromQL: `histogram_quantile(0.95, sum by (le) (rate(gps_admitted_to_commit_seconds_bucket[5m])))`. 미측정은 `sum by (outcome) (increase(gps_commit_latency_observations_total{outcome!="measured"}[5m]))`. Prometheus histogram은 bucket 근사치이며 fixture의 정렬 표본 p95/p99와 같을 필요가 없다.

## 조회 개선 후보 비교

각 부하 뒤 같은 MySQL 데이터에서 `(vehicle_id, occurred_time)` 후보 인덱스를 만들고, 기존 인덱스 경로와 후보 경로를 EXPLAIN ANALYZE로 보존한다. 최근100개 조회 형태에서10회씩 warmup 후60쌍을 번갈아 실행한다. 결과 row가 같은지 확인하고 후보 인덱스를 삭제한다. 측정은 JDBC 왕복·mapping을 포함한 warm-cache microbenchmark다. `LIMIT 1`인 현재 위치 API 자체의 before/after도, 전체 GPS 수집 성능 개선도 아니다. 운영 migration은 쓰기 비용·인덱스 크기·대표 workload 검증 뒤 결정한다.

## 로컬 경보·중앙 로그

`python3 scripts/observability/operations-evidence.py` 실행 전 위 관측성 테스트가 `build/reports/observability-evidence/http-completion.jsonl`을 생성해야 한다.

- 실제 Prometheus가 synthetic backlog exporter를 수집하고 규칙을 평가한다. 실제 Alertmanager가 로컬 HTTP receiver로 firing/resolved를 보낸다. 실 서비스 적체의 exporter 경로는 앞 단계의 실제 RabbitMQ 실험에서 별도로 검증한다.
- 실제 Spring LoggingFilter의 warmup HTTP 완료 로그16개만 allowlist 검사 후 Loki에 전송한다. 일반 app.log 전체를 업로드하지 않는다. 이는 제한된 batch 수집 검증이며 지속 tail/rotation/checkpoint collector 배포가 아니다.
- Loki는 host port가 없고, gateway만127.0.0.1 임시 포트를 사용한다. writer는 push만, reader는 query_range만 가능하다. 임시 암호는 파일·HTTP header로만 사용하며 결과에 저장하지 않는다. 컨테이너 내부 Loki는 gateway를 신뢰하므로 Docker network 관리자까지 격리하는 모델이 아니다.
- 24h retention, compactor 활성화,25시간 이전 로그 접수 거부를 검증한다. 실제24시간 경과 후 물리 삭제·디스크 사용량 감소는 관찰하지 않았다. 실험 종료 시 자체 생성 컨테이너·네트워크만 제거한다.
- 운영에는 TLS, 지속 collector, 실제 수신 경로, 접근 감사와 저장소 용량 경보가 추가로 필요하다.

설정 근거: [Alertmanager webhook](https://prometheus.io/docs/alerting/latest/configuration/), [Loki retention](https://grafana.com/docs/loki/latest/operations/storage/retention/), [Loki configuration](https://grafana.com/docs/loki/latest/configure/). Loki는 retention 설정만으로 디스크 사용량 상한을 보장하지 않는다.

## CI 범위

FE: `npm ci`, `npm test`, `npm run build`, `npx playwright install --with-deps chromium`, `npx playwright test`, `npx playwright test --config playwright.production.config.mjs`.

Emulator: Python3.11 venv에서 `python -m pip install -r requirements.txt`, `python -m unittest discover -s tests -v`. 루트 `test_emulator.py`는 실제 전송 가능성이 있는 수동 스크립트여서 자동 CI 대상에서 제외한다.

CI는 pull_request·수동 실행만 허용하고 contents:read, 실행 시간 제한, 동일 PR의 이전 실행 취소를 사용한다. 이 문서의 로컬 명령 통과와 GitHub runner에서 실제 green인 것은 별도 사실이다.
