# 남은 작업과 로컬 마무리 상태

기준일: 2026-09-07. 전체 로드맵을 임의 완료율로 표시하지 않는다. 구현·격리 검증·원격 반영·실제 운영 적용·사용자의 독립 설명은 각각 확인한다.

## 이번에 처리한 범위

| 작업 | 결과·증거 |
| --- | --- |
| 장치 수집 인증 | CHANGE-034–037: 키 발급/교체/폐기, 연결 revision, 세 수집 API 인증, RabbitMQ identity/소속 재검증, Python/브라우저 client 연결 |
| 재전송·과다 요청·본문·시각 방어 | [CHANGE-038](work-logs/2026-09-07-telemetry-request-protection.md): Redis atomic nonce/freshness/장치별 예산, 실제 byte 상한, GPS·Geofence strict 시각, CORS |
| 주소 자동 재시도 | [CHANGE-039](work-logs/2026-09-07-trip-address-worker.md): durable scan/lease/backoff/최대 시도와 명시적 재개. 기본 cron 비활성 |
| 통계 지연 원천 보정·감사 | [CHANGE-040](work-logs/2026-09-07-statistics-correction-worker.md): 원천 저장과 dirty queue 원자성, generation/회사 lock/retry, 변경 revision |
| 통계 fleet 기준 보존 | CHANGE-040: 신규 계산부터 최초 차량 ID 목록 snapshot, 이후 삭제/추가와 계산 범위 분리. snapshot 없는 기존 row는 409, 검토한 ADMIN seed만 허용 |
| 고정 데이터 성능·업무 API 증거 | [CHANGE-041](work-logs/2026-09-07-fleet-evidence.md): 실제 MySQL/Redis/RabbitMQ, 로그인/회사 격리/중복/queue drain, p50·p95·p99 원시 표본 |
| JVM 종료 후 STARTED 복구 | [CHANGE-042](work-logs/2026-09-07-statistics-orphan-recovery.md): 실제 자식 JVM 종료, offline snapshot CAS/감사, 첫 회사 checkpoint 보존 후 동일 JobInstance 재시작 |
| 발행 대기·포화 경계 | [CHANGE-043](work-logs/2026-09-07-publisher-shared-deadline.md): 저장/live 공유 2초 대기 예산, 유한 worker/queue, 접수 불확실성 유지 |
| FE bundle·CI Node 참조 | [CHANGE-044](work-logs/2026-09-07-frontend-bundle-ci.md): 페이지 lazy chunk/실패 복구, 초기 bundle 축소, 공식 action runtime 참조 갱신 |
| 실제 화면 업무 흐름 | [CHANGE-045](work-logs/2026-09-07-fleet-browser-workflow.md): 실제 App→격리 Boot→MySQL의 로그인/차량/운행/통계/타회사 거부 검증. 지도 SDK는 별도 격리 |
| 경보·배포 복구 절차 | [CHANGE-046](work-logs/2026-09-07-reliability-alerts-release.md): Prometheus 8개 규칙 synthetic 발생/해제 검증, 실제 운영 gate/rollback runbook |
| Emulator 과거 queue의 새 장치 재귀속 방지 | [CHANGE-047](work-logs/2026-09-07-emulator-backlog-binding.md): enqueue credential identity 고정, 변경/unknown은 자동 retry 보류 |
| GPS hour/day·timezone 정확성 | [CHANGE-048](work-logs/2026-09-07-gps-hour-boundary.md): FE/Python의 원본 관측시각 기반 시간별 packet 분할, 명시적 KST 전송 규약 |

최종 전체 회귀와 교차 저장소 검증은 [CHANGE-049 통합 기록](work-logs/2026-09-07-local-completion-review.md)에 확정한다. 앞선 작업 로그의 테스트 수는 당시 스냅샷이며 최신 개수로 소급 변경하지 않는다.

## 관측성 후속 작업

[CHANGE-050](work-logs/2026-09-07-observability-evidence.md): 안전한 공통 로그·correlation/MDC 복원, 실제 Prometheus/Grafana와18개 panel,90초 단계 부하·consumer pause/recovery 증거를 보강한다. 구체적인 실행 결과는 해당 work log가 기준이다. 후속 gate는 운영 metrics 접근 격리, 중앙 로그 retention/권한, 실제 알림 수신, 개별 observation의 접수→DB commit 지연, 장기 soak·동일환경 A/B다.

## 실제 입력 또는 별도 실행이 필요한 것

| 남은 항목 | 필요한 근거·다음 행동 |
| --- | --- |
| 운영 DB migration/기존 Trip·부풀린 mileage 정정 | 대상 DB/backup 복원본/read-only preflight, 원래 odometer·Trip 근거와 변경 승인. 원천 없이 과거 숫자나 event identity를 만들어 정정하지 않는다. |
| 최초 계산 이전의 실제 fleet 가입·소속 이력 | 당시 원천 자료가 필요하다. 새 snapshot과 ADMIN의 현재 목록 seed는 과거 이력 복원 결과가 아니다. |
| 운영 broker/Redis/주소 API 전환 | 실제 queue/DLQ policy, credential/nonce client 동시 전환, Redis persistence/HA/eviction/time sync, Kakao quota와 worker cron 활성화 판단. 로컬 fixture가 운영 설정을 바꾸지 않는다. |
| 운영 경보 수신·배포·rollback 훈련 | 실제 scrape/Alertmanager 수신자, AWS task/image digest/inventory와 복원 검증. [절차](../runbooks/reliability-alerts-and-release.md). |
| Git 공개 반영 | 앞선 범위는 BE #235 / FE #84 / Emulator #22 draft PR 생성, BE CI 성공까지 확인했다. merge/운영 배포는 미실행. CHANGE-050 관측성 후속 변경은 별도 로컬 branch에서 검증한다. |
| 독립 설명·시연 | 사용자가 [학습 계획](learning-review-plan.md)과 [체크리스트](submission-checklist.md)의 핵심 테스트를 직접 실행하고 설계 이유를 설명해야 한다. AI가 대신 완료 표시할 수 없다. |

## 의도적으로 확정하지 않은 추가 범위

- 실제 장치의 영구 event sequence/session 규약: 현재 protocol에는 그 근거가 없다. 신규 GPS observation key/Trip 충돌 검증/요청 nonce의 서로 다른 역할은 구현돼 있다. 임의 field를 추가해 과거 이벤트를 구분했다고 주장하지 않는다.
- live stream의 durable replay/journal: 현재 live 경로는 best-effort이며 저장 경로와 성공 조건을 분리한다. 끊긴 화면은 재연결/REST 재조회로 확인한다. 무유실 live·원자적 dual publish는 계약으로 채택하지 않았다.
- 누락된 모든 과거 날짜를 무한 자동 생성하는 bulk backfill: 회사/날짜를 확인해 기존 batch 또는 명시적 계산으로 수행한다. 자동 correction은 이미 집계된 날짜의 지연 원천을 보정한다.
- 선택 과제 AI: 고정 평가 데이터와 rule baseline 없는 예지정비/최적배차 성과를 만들지 않는다.
- 최대 운영 부하/장기 soak/SLA: 작은 고정 fixture 기준선은 확보했지만 실제 용량과 운영 성과로 일반화하지 않는다.

제품 설명은 **기업용 차량 운영 관리 서비스**다. 원 팀 작업, 기존 개인 Vehicle/Statistics/Batch 기여, 이후 AI 지원을 포함한 개인 현대화를 구분한다. 공개 배포·기존 데이터 정정·사용자의 학습 완료까지 모두 끝났다는 뜻으로 '완료'를 사용하지 않는다.
