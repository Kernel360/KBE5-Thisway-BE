# CHANGE-041: 격리된 업무 API·GPS 성능 기준선

## 메타데이터

- 날짜: 2026-09-07
- 작업자: 개인 현대화, AI 지원
- 브랜치/기준: `codex/statistics-batch-restart`, `ef74fcf` 이후 기존 dirty 변경을 보존한 working tree
- 관련 issue/PR: 없음, 로컬 작업
- 상태: Verified — 전용 task 1/1 통과, 실패·오류·skipped 0

## 1. 문제와 근거

남은 작업에 성능·운영 증거와 회사별 업무 시연이 있었지만 기존 k6 script는 장치 인증 없는
과거 요청을 반복했다. 현재 정확성 테스트 개수로 처리량이나 전체 업무 흐름을 대신 설명할 수 없다.
기존 SSE browser test는 Boot/H2/nginx/Chromium 경로이며 MySQL 수집 전체 검증은 아니다.

원 팀의 RabbitMQ/SSE 구현, 사용자의 원래 Vehicle/Statistics/Batch 기여, 이후 개인 현대화는
`original-contributions.md`와 기존 work log 기준으로 분리한다. 이번에 추가한 것은 현행 흐름의
독립 측정 harness이며 원 팀 부하 시험을 이번 실적으로 바꾸어 표현하지 않는다.

## 2. Acceptance criteria

- [x] 실제 MySQL/RabbitMQ/Redis를 매 실행 격리하고 외부 운영 주소를 받지 않는다.
- [x] 2회사 login→차량→운행→통계 API 흐름과 다른 회사 상세 접근 거부를 검증한다.
- [x] warmup16/본측정240/observation중복24, 기대 GPS row256/추가중복0/queue0/DLQ0을 검증한다.
- [x] p50/p95/p99·처리량·오류율·drain·JDBC query timing·EXPLAIN ANALYZE와 raw samples를 보존한다.
- [x] token/key/실제 위치를 결과에 남기지 않으며 fixture 결과를 production 용량으로 표현하지 않는다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 판단 |
| --- | --- | --- | --- |
| 기존 k6 script 즉시 실행 | 실행 준비가 적음 | 인증/시간/중복 입력이 현행 계약과 달라 결과 의미가 없음 | 거절 |
| 외부 dev/staging 서비스에 부하 | 배포 환경에 가까움 | 대상 생존 여부·데이터·비용·권한 확인 필요 | 운영 과제로 유지 |
| 별도 tagged Testcontainers harness | 데이터·인증·DB count를 함께 통제하고 재현 가능 | 한 머신의 작은 fixture이며 포화 용량을 모름 | 채택 |

실제 Spring controller/인증/service/consumer를 사용한다. 외부 주소 변환만 synthetic stub이다.
Redis request 보호는 켜고 per-device rate만 10000/min으로 높여 부하 결과와 throttling을 분리한다.

## 4. 구현과 실행 흐름

- `FleetEvidenceIntegrationTest`와 `fleetEvidenceTest` 전용 task, 기본 test의 tag 제외.
- 2회사·8차량·8장치, 실제 BCrypt/login 및 관리 HTTP 키 발급.
- 요청별 UUID nonce/현재 epoch header, 고정 observation workload를 동시4로 전달.
- 실제 publish confirm 후 HTTP 응답, 비동기 consumer 저장, phase별 count/ready drain 확인.
- power ON/OFF 후 내부 집계, 회사 A/B 조회 및 cross-tenant 404 확인.
- `docs/experiments/2026-09-07-fleet-evidence.md`에 재현 정의와 운영 별도 경계를 기록.

HTTP 성공은 broker 접수이고 DB commit의 동의어가 아니다. 마지막 listener stop으로 진행 중 ack를
기다린 후 queue/DLQ/DB 최종 상태를 대조한다. compiled class tree의 SHA-256도 결과에 남긴다.

## 5. 검증 결과

`fleetEvidenceTest`: **1/1 통과**, 실패·오류·skipped 0. 최종 root 검증 묶음
`./gradlew test sseBrowserTest emulatorClientTest fleetEvidenceTest --console=plain`은 총2분54초,
이 전용 testcase는3.309초다. 초기 전용 실행23초와 구분한다.
실제 최종 측정 `2026-09-07T11:06:51.606876Z`(20:06 KST). 본측정240요청 p50/p95/p99
**8.581/10.625/12.381ms**, 454.87 request/s, 오류0. batch drain67.134/43.657/67.988ms.
256 GPS row와 중복 추가0, ready/DLQ/rejected/unconfirmed0, storage confirmed280을 확인했다.
2회사 login·차량/운행/통계 조회와 타회사404 검증도 통과했다.

증거는 [실험 정의·결과](../../experiments/2026-09-07-fleet-evidence.md)와
[최종 raw JSON](../../experiments/2026-09-07-fleet-evidence.result.json)이다.
[공유 deadline 도입 전 raw](../../experiments/2026-09-07-fleet-evidence.before-deadline.result.json)는 변경하지 않았다.
최종 compiled class SHA-256: `61184855d0d47d292cd3d6810ecc5560ab3fdba69bc837db1ce320f99c8d72f5`.
작은 fixture 반복 변동을 성능 개선율로 표현하지 않는다.

- Before: 현재 인증 계약을 포함한 고정 데이터 성능 기준선 없음.
- After: 실제 authenticated HTTP→broker→DB 기준선 확보, before/after 개선율 없음.
- 전체 회귀 suite: root가 최종 통합 검증을 담당한다. 이 변경 담당은 전용 task만 실행한다.

## 6. 실패 사례와 남은 위험

전체 FE 화면 시연, 실제 배포 인프라, broker/process crash, 최대 부하, 기존 데이터 migration,
CI 원격 실행, 사용자 독립 설명은 이 harness로 대체하지 않는다. 실제 운영 적용에는 실측 환경과
backlog·DB 복원본·image/task revision·rollback 호환성 확인이 필요하다.
첫 실행은 sandbox가 Gradle cache lock 쓰기를 거부했다. 같은 명령을 Gradle cache/Docker 접근
권한으로 재실행해 통과했으며 테스트나 데이터 조건을 축소하지 않았다.

## 7. 학습 기록

- percentile의 nearest-rank 정의와 sample 수, warmup과 JIT/DB cache.
- end-to-end latency, HTTP broker acceptance, DB commit과 batch drain의 차이.
- 논리 observation 중복과 HTTP nonce replay의 서로 다른 식별자.
- 실제 repository query의 JDBC 왕복 측정과 DB 내부 `EXPLAIN ANALYZE` 시간 차이.
- 코드 위치: `FleetEvidenceIntegrationTest.gpsBatch`, `workflow`, `queryEvidence`, `summary`.

## 8. 예상 면접 질문

1. HTTP p95가 낮으면 GPS 저장도 빨랐다고 말할 수 있나요?
   - publisher confirm 이후 HTTP가 끝나므로 DB 저장시간과 다르다. 저장 row/queue drain을 별도로 확인했다.
2. 24번 중복 요청의 row가 늘지 않았는데 nonce replay도 완전히 막았나요?
   - 이 검증은 매번 새 nonce의 동일 observation 저장 효과다. nonce 재사용 거부는 별도 보안 테스트 범위다.
3. 이 처리량으로 운영에서 몇 대를 지원한다고 말할 수 있나요?
   - 8장치·동시4·256행의 로컬 기준선이다. 포화 부하·장시간·실제 환경·event size 조건 없이는 추정하지 않는다.
4. 오류율이 0인데 아직 확인할 게 있나요?
   - DB 기대 count, DLQ/rejected/unconfirmed, 다른 회사 분리, 실제 UI 계약과 장애 복구를 추가 대조해야 한다.

## 9. AI 활용과 사람의 검증

- AI 지원: 기존 문서/코드 분석, 측정 조건 제안, harness·증거 문서 작성.
- 채택: 고정 workload·격리 서비스·실제 HTTP+DB 검증과 raw samples 보존.
- 거절: 과거 unauthenticated k6 결과 재사용, 로컬 fixture를 운영 SLA/개선율로 표현.
- 자동화 검증: 실제 전용 task 1/1 통과, DB/queue/counter/API assertion과 원시 결과를 대조했다.
- 사람 확인: 사용자의 코드 읽기·직접 재실행·독립 구두 설명은 아직 확인하지 않았다.
