# 경보 확인과 배포·rollback 절차

2026-09-07. 로컬 준비 문서. 실제 AWS/운영 DB/Alertmanager에 적용하거나 알림을 보내지 않았다.

## 경보의 범위

`infra/prod/monitoring/prometheus/rules/thisway.yml`에 아래 조건을 정의했고, Prometheus image가 해당 rules를 읽도록 포함했다. 초기 문턱은 fixture용 운영 초안으로 트래픽/SLO에 맞춰 조정해야 한다. 수신자·Alertmanager routing은 실제 운영 정보가 없어 연결하지 않았다.

| 조건 | 조사 순서 |
| --- | --- |
| storage publish unconfirmed / publisher deadline·포화 | 최근 배포, broker 연결/mandatory binding, storage counter, DB idempotency/queue drain 확인. 단순 HTTP 재시도 성공을 DB 저장으로 대체하지 않는다. |
| consumer rejected | 분류/DB 장애/identity stale 여부 확인. DLQ preview 후 승인된 한 메시지만 replay. raw GPS/key를 로그로 풀지 않는다. |
| 주소 worker 실패/시도 소진 | 보정 필요 여부, lease/backoff, quota·외부 API 상태 확인. EXHAUSTED 무한 자동 재개 금지. |
| 통계 correction 실패 | pending 회사/날짜, 최초 fleet snapshot 부재, DB 오류 확인. unknown snapshot은 현재 차량 목록 검토 후 명시적 seed 필요. |
| 409·429·503 증가 / 본문 413 | client nonce·서버 시계·장치 예산·Redis 상태를 error code로 구분. 여러 계층의 503을 같은 원인으로 단정하지 않는다. |
| application scrape 실패 | metrics 접근 정책·대상 discovery·실제 health 확인. scrape 실패만으로 앱 프로세스 사망을 단정하지 않는다. |

Counter는 event 발생 후에야 만들어질 수 있어 deadline/포화 식에는 빈 series 처리를 포함한다. HTTP handler까지 도달하지 않는 413은 전용 counter를 사용한다. 모든 새 metric label은 고정 outcome이며 회사·장치·날짜·좌표·key를 붙이지 않는다.

## 로컬 경보 검증

```sh
docker run --rm --entrypoint /bin/promtool \
  -v "$PWD/infra/prod/monitoring/prometheus/rules:/rules:ro" \
  -w /rules/tests prom/prometheus:v3.4.1 test rules thisway.test.yml
```

실패 발생 전, 2분 지속 후 발화, 정상으로 돌아온 뒤 해제, 일부 counter 미생성, 413 별도 지표를 synthetic series로 검사한다. 실제 exporter metric scrape·Alertmanager 수신·운영 장애 훈련의 대체 증거는 아니다.

## 배포 전 확정할 입력과 중단 조건

1. 배포할 BE/FE/Emulator의 정확한 commit, 통과한 tests, 불변 image digest와 이전 digest를 기록한다. `latest`나 버전 없는 force-new-deployment만으로 배포 버전을 식별하지 않는다.
2. 실제 ECS cluster/service/container/task definition ARN과 기존 task의 설정을 확인한다. 현재 CD는 새 task-definition image를 명시적으로 등록하지 않으므로, 실제 배포 운영자는 immutable image 전환/검증을 별도 반영해야 한다. 이 문서는 현재 CD가 안전한 rollback을 자동 수행한다고 주장하지 않는다.
3. DB backup이 실제 복원되는지 격리 복원본에서 검증한다. history/checksum/schema preflight와 migration V1–현재 전체를 검사한다. legacy Flyway baseline/repair를 추측해서 실행하지 않는다.
4. 새 nonce 헤더·credential 배포를 세 client/BE와 함께 확인한다. nonce Redis persistence/HA/eviction, clock sync, broker DLQ 정책과 queue inventory를 기록한다.
5. Pinpoint javaagent가 실제 task에서 제공되는지 확인한다. build.gradle의 agent 경로만으로 파일 존재를 보장하지 않는다. broker image 버전·지원 여부도 현재 인프라에서 확인한다.
6. 배치/주소/보정 scheduler 활성화와 rollout 중 writer 수를 결정한다. BEFORE/AFTER_MIGRATION 동시 구버전 writer를 허용할 수 있는지 검토한다. 주소 cron은 기본 비활성이다.

위 실제 대상·backup·digest가 없으면 승인 가능한 실제 배포 계획이 아직 아니므로 연결정보를 만들어 진행하지 않는다.

## 배포 확인과 rollback

- 승인된 불변 image의 새 task definition을 등록하고 이전 ARN을 보존한 뒤 대상 서비스를 갱신한다. 정상으로 표시된 task 개수만 보지 말고 target health, key 있는 수집, 저장 확인, tenant 거부, 통계 조회를 함께 확인한다.
- 오류 기준(예: 계속되는 503·consumer reject·새 인스턴스 health 실패)이 충족되면 신규 traffic/writer를 중지하고 이전 task definition으로 서비스를 되돌린다. 이 문서의 오류 기준은 확정 SLO가 아니므로 배포 계획에 숫자/담당자를 명시한다.
- Flyway를 역방향 실행하거나 history를 삭제하지 않는다. 스키마는 backward-compatible한 additive 변경이면 유지하고 이전 application이 validate/쿼리 계약을 만족하는지 복원본에서 먼저 검증한다. 불가능하면 이전 binary 재시작보다 roll-forward를 선택해야 한다.
- queue/DLQ를 purge하지 않는다. 인증 metadata가 없는 legacy 메시지는 자동으로 현재 장치에 귀속하지 않는다. 실패/불확실 replay는 원본을 보존한다.
- 복구 후 원천 row 수, 중복 효과, pending queue, 통계 revision, alert 해제를 전후 기록으로 남긴다. 운영자 승인·실행 로그가 없는 로컬 테스트를 운영 복구 완료라고 쓰지 않는다.
