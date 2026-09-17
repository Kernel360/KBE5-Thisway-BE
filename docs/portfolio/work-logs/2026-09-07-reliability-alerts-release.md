# CHANGE-046 — 경보 규칙과 배포 복구 증거의 경계

2026-09-07. 로컬 규칙 검증 완료; 실제 운영 적용/수신 확인은 미실행.

## 문제·선택·실행

원 팀의 Prometheus 수집 설정에는 현재 개선한 publisher/consumer/worker 실패를 확인할 alert rule이 없었다. 이후 개인 현대화로 고정 outcome counter와 HTTP 집계에 연결한 8개 경보, Prometheus Docker rules 포함, 장애별 조사/배포 rollback runbook을 추가했다. 기존 metrics discovery와 remote_write 대상은 수정하지 않았다.

애플리케이션에 업체/장치별 label을 추가하는 대신 낮은 cardinality의 집계 지표를 채택했다. 상세 원인·조치 대상은 접근이 제한된 DB 점검으로 확인한다. API handler 이전 413은 URI template 관측이 없을 수 있어 별도 counter를 둔다. Alertmanager 실제 수신자와 AWS inventory를 알 수 없으므로 자동 external 알림이나 배포를 생성하지 않았다.

## acceptance·검증

- 8개 규칙을 synthetic series에서 발생 전/지속 후/해제/미생성 counter 상황으로 확인.
- `docker run --rm --entrypoint /bin/promtool -v "$PWD/infra/prod/monitoring/prometheus/rules:/rules:ro" -w /rules/tests prom/prometheus:v3.4.1 test rules thisway.test.yml` → SUCCESS.
- image digest `sha256:9abc6cf6aea7710d163dbb28d8eeb7dc5baef01e38fa4cd146a406dd9f07f70d`.
- 규칙 source 및 test: `infra/prod/monitoring/prometheus/rules/`.
- [조사·배포·rollback 절차](../../runbooks/reliability-alerts-and-release.md).

성능 개선 전후 측정 대상이 아니라 관측 정책 추가다. 임계값은 초기 제안이며 운영 SLO 최적화 결과가 아니다. 실제 scrape와 Alertmanager 전달, AWS task 교체/복원, backup 복원은 수행하지 않았다. 현재 CD의 mutable image/force rollout, Pinpoint agent 주입/실제 broker 버전은 운영 inventory를 확보해야 해결할 수 있는 별도 gate로 기록한다.

## 학습·면접·AI 검증

1. 왜 alert rule 통과가 알림 도착을 보장하지 않는가? exporter scrape, Prometheus evaluation, Alertmanager route/delivery는 서로 다른 단계다.
2. 왜 counter 증가량에 for를 붙이는가? 일시 신호와 지속 이상을 분리하지만 detection delay 비용이 있다. 운영 목표에 맞춰 조정해야 한다.
3. 왜 application rollback과 DB rollback이 다른가? additive schema 호환성과 이미 반영된 데이터를 함께 고려하며 Flyway history 삭제는 복구가 아니다.
4. 왜 높은 cardinality label을 피하는가? time series 수·비용·민감 정보 범위를 제한한다.

AI가 rule/runbook 초안을 작성하고 promtool fixture로 발화/해제 조건을 검증했다. 임의 운영 성과/수신자를 만들지 않았고, 원 팀 인프라 구현과 이번 개인 관측 정책 변경을 구분했다.
