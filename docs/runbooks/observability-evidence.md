# 로그·지표·대시보드와 재현 실험

## 로컬 대시보드

먼저 [metrics 수집키 설정](metrics-access.md)에 따라 backend hash와 수집기 파일을 준비한다. 키가 없으면 수집이 거부된다. 기존 개발 DB/queue/volume과 별도인 모니터링 전용 Compose다. 외부 `remote_write`와 알림 전송은 없다. 기존 개발 앱 8080, RabbitMQ Prometheus plugin 15692가 실행 중일 때:

```sh
docker compose -f infra/observability/compose.yml config --quiet
docker compose -f infra/observability/compose.yml up -d
```

Grafana는 `http://127.0.0.1:13000/d/thisway-reliability`, Prometheus는 `http://127.0.0.1:19090`이다. Grafana는 loopback 전용 익명 Viewer이며 외부 배포용 인증 설정이 아니다. `down`은 이 모니터링 project만 대상으로 하며 기존 dev project와 volume을 정리하지 않는다. 새 구성은 영구 volume을 두지 않고 Prometheus 보존 시간을 24시간으로 제한했다. 재시작 보존이 필요하면 경로·권한·보존기간을 정한 뒤 추가한다.

주로 볼 순서: HTTP 오류·p95 → broker 접수 → ready/unacked → consumer transaction → DB pool/CPU/GC → 보정 worker. 지표가 없으면 성공 0건이 아니라 **미수집 또는 미발생**일 수 있다. 짧은 창·적은 요청에서 p99와 오류율을 확정 SLO로 취급하지 않는다.

## 지표 의미

| 지표 | 경계 |
| --- | --- |
| HTTP timer / broker confirmed | 요청 처리·broker 수락. DB 저장이나 화면 표시 완료가 아님 |
| `gps_consumer_processing_seconds` | consumer 진입부터 transactional service proxy 반환까지. `committed`는 DB transaction 반환, `failed`는 예외. queue 대기/AMQP ack를 포함하지 않음 |
| 위 timer의 count | 소비 시도 수. 멱등 처리된 중복도 포함하므로 고유 GPS row 수가 아님 |
| `gps_publisher_active`, `gps_publisher_queued` | JVM별 실제 worker/대기 task, 각각 최대4/64 |
| RabbitMQ ready/unacked | broker exporter가 실제 수집돼야 표시됨. ready는 수집된 시계열의1분 최대값으로 짧은 적체를 보존해 표시하며 현재값이 아님. exporter의 다른 queue도 포함 |
| HTTP p95 | histogram 집계의 근사값. 실험 JSON의 개별 HTTP 표본 분위수와 계산 방식이 다름 |

회사·장치·MDN·trace ID·좌표를 metric label로 쓰지 않는다. 대시보드 JSON은 Git이 기준이며 수정은 코드 리뷰 대상으로 남긴다.

## 로그와 추적

HTTP access log는 서버 handler의 route template, 허용된 method, status, dispatch duration, async 시작 여부만 기록한다. 미매칭 path는 `UNMATCHED`로 남긴다. body/query/header 원문과 인증정보를 수집하지 않는다. `X-Correlation-ID` 응답은 서버 context 기준이고 같은 값이 publisher worker와 두 consumer의 MDC에 전달된다. 외부 trace header는 소문자 hex32만 허용하고 그 밖에는 새 ID로 대체한다. scope 종료는 기존 MDC를 복원한다.

이는 **로그 correlation**이며 완전한 distributed tracing span/parent 관계나 Pinpoint 수집 검증을 의미하지 않는다. SSE `asyncStarted=true`의 dispatch duration은 연결 수명이나 최종 전송 완료가 아니다. 공통 예외 진단은 예외 class와 첫 프로젝트 stack frame만 남기며 exception message/cause/rejected value를 남기지 않는다. 원문 stacktrace보다 진단 정보가 적다는 trade-off가 있다. code site와 correlation으로 원인을 좁히고 합성 재현 테스트에서 추가 진단한다.

기존 logback의 파일 보존 설정은 그대로다. 이번에는 중앙 로그 저장소를 설치하지 않았다. CloudWatch/Loki 도입에는 실제 접근 권한, 필드 허용 목록, retention/삭제, 저장 비용, 검색 권한을 먼저 정해야 한다.

## 격리 실험

```sh
./gradlew observabilityEvidenceTest --console=plain
```

Docker와 Node, 인접 FE checkout의 Playwright/Chromium(`npm ci`, `npx playwright install chromium`)이 필요하다. 실제 MySQL/RabbitMQ/Redis와 Prometheus/Grafana를 임시 컨테이너로 띄우고 종료한다. host app port는 Testcontainers의 SSH forwarding으로 이 컨테이너에 노출한다. 외부 지도는 합성 fixture이며 운영 데이터·credential은 사용하지 않는다.

장치8, warmup16, 20→40→80 requests/s 각각30초, 최대 client worker8/대기256을 사용한다. queue가 가득 차면 생성기 오류를 status -1로 남긴다. 요청별 HTTP latency는 executor 대기 시간을 제외한다. 단계별 실제 처리율과 전체 wall time을 같이 기록한다. 동일 호스트의 scheduler/DB probe도 부하에 영향을 주므로 최대 서버 용량 측정이 아니다. fixture 애플리케이션 logger는 WARN으로 제한하므로 운영 INFO 로그 비용까지 포함하지 않는다.

consumer를 정지한 상태에서80개 요청을 수락시키고 DB 증가0/ready80을 확인한 뒤 재시작해 기대 row와 queue drain을 검사한다. 과거24개 observation을 새 nonce로 재전송해 row 추가0을 확인한다. 이는 제어된 consumer pause 복구이며 broker crash·전원 장애 시험이 아니다.

결과: `build/reports/observability-evidence/result.json`, `dashboard.json`. 원시 표본·환경·compiled class SHA256·resource samples·실제 PromQL 응답·대시보드/API proxy 검증을 포함한다. 실패한 assertion은 실행 실패로 보고하며 테스트를 건너뛰지 않는다. 종료 전에 Chromium으로 viewport3개를 캡처하고 `browser.json`에 page error 결과를 남긴다. 화면의 픽셀/레이아웃은 캡처를 별도로 육안 검토한다.

## 운영 전 남은 gate

- CHANGE-051부터 `/actuator/prometheus`는 전용 수집키가 필요하다. 운영 ingress 차단·별도 management listener/보안그룹은 실제 배포 구조와 함께 확정해야 한다. 로컬 loopback 바인딩은 운영 보안을 검증하지 않는다.
- production Prometheus에는 기존 AWS remote_write가 있으나 이번 실험은 그 설정을 사용하지 않는다.
- 에러 예산/SLO는 측정 구간·분모·허용오류를 먼저 합의한다. 이번 목표 부하는 합격 성능 약속이 아니다.
- 후속 실험: 장기 soak, 동일환경 반복, 개선 전후 A/B, DB commit까지 개별 observation 지연, process/broker 장애, 실제 알림 수신·해제. 이번 baseline 수치로 개선율을 만들지 않는다.
