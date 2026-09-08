# 관측성과 성능 증거 실행계획

2026-09-07. 기준 BE `79b77c5`, 새 로컬 branch `codex/observability-evidence`. 기존 draft PR과 운영 환경은 유지한다.

1. P0 로그: 예외 message/Throwable, raw URI, payload를 공통 경로에서 제거한다. HTTP correlation을 서버에서 생성하고 RabbitMQ worker/consumer까지 전달하며 기존 MDC를 복원한다. 악성 header와 실패 경로 회귀 테스트를 둔다.
2. P1 지표: HTTP histogram, publisher queue/active, consumer transaction 완료 시간과 성공/실패를 측정한다. 회사/장치/좌표/trace는 metric label로 쓰지 않는다. 저장 완료는 transaction proxy 반환 기준이며 broker ack/고유 row 증가와 구분한다.
3. P1 화면: 버전 관리되는 Grafana 대시보드와 loopback 전용 로컬 Prometheus/Grafana 구성을 제공한다. 외부 remote_write 없이 실제 scrape/PromQL/대시보드 로딩을 확인한다. 기존 운영 설정을 자동 변경하지 않는다.
4. P1 실험: 기존 MySQL/RabbitMQ/Redis fixture를 활용해 고정 지속 부하와 consumer 일시 정지/복구를 실행한다. 응답 p95/p99와 DB row/queue 해소를 함께 기록한다. 실행 시간/동시성/장치 수/seed/commit/제약을 원시 결과에 보존한다.
5. 검토: 관련 회귀와 전체 BE suite, rule 검사, 로그 유출 검증을 완료한다. 실측되지 않은 운영 SLA, 무유실, 최대 처리량, 개선율은 주장하지 않는다.

이번 완료 기준: 로그 민감정보 회귀 green, HTTP→worker→consumer correlation 및 복원 확인, 지표와 대시보드 계약 확인, 실제 격리 수집/부하·복구 결과, 재현 runbook과 학습 기록. 실제 CloudWatch/Loki/Alertmanager 수신과 AWS 배포는 대상·보존기간·권한·비용 합의 후 별도 진행한다.

## 운영 적용 전 우선순위

- P0: 실제 ingress/보안그룹 기준의 metrics endpoint 접근 격리. CHANGE-051에서 application 수집 권한을 분리하며 실제 운영 네트워크 격리 여부는 별도로 확인한다.
- P1: 관측 시점→수집 접수→DB commit의 시간 경계를 나눠 개별 저장 지연 측정, 장기 soak와 반복 A/B. 이번 단계 부하를 최대 용량으로 표현하지 않는다.
- P1: 실제 경보 수신자·중앙 로그 보존기간/접근 권한·비용·삭제 정책 확정 후 장애 알림 훈련.
- P2: 필요한 경우 trace backend와 로그 저장소를 연결한다. correlation ID가 있다는 사실만으로 분산 tracing 완성을 주장하지 않는다.

## 이번 실행 완료 — 2026-09-08 KST

1~5단계의 로컬 구현·격리 검증을 완료했다. BE467개 전체, 기존 업무 fixture1개, 관측성 fixture1개 통과. 실제 Grafana18개 panel/API proxy·Prometheus2target·적체80 관측과3개viewport를 확인했다. 최종20/40/80rps 각30초 본측정4,200건 오류0, pause/recovery 후4,296고유row/중복추가0. 수집예산을10,000/device/min으로 상향한 합성 실험이며 운영성능약속이 아니다. [CHANGE-050](work-logs/2026-09-07-observability-evidence.md)에 실패와 한계·재현 명령을 보존한다.
