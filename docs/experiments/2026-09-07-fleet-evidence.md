# 고정 fixture의 업무 API·GPS 수집 기준선

CHANGE-041. 상태: Verified. 2026-09-07 20:06 KST 전용 task 1개 통과, 실패·오류·skipped 0.

## 목적과 범위

회사 담당자가 로그인해 차량 현황, 운행 기록, 회사 통계를 확인하는 API 흐름과 GPS의
HTTP → 장치 인증/요청 보호 → RabbitMQ → MySQL 경로를 격리된 로컬 환경에서 실행한다.
성능 개선 주장이 아니라 이후 같은 조건으로 재현할 수 있는 소규모 기준선이다.
UI 전체 화면 시연이나 사용자의 독립 설명을 완료했다는 의미는 아니다.

기존 `infra/stress/stress_test.js`와 `stress_avg.js`는 장치 인증이 없고 오래된 시각과 동일
observation을 반복한다. 현재 수집 계약의 처리량 근거로 사용하지 않는다. 원 팀 파일은 보존한다.

## 재현

요구사항은 JDK 21, Docker, Gradle 의존성 및 MySQL/RabbitMQ/Redis 이미지 접근이다.
서비스 주소는 Testcontainers가 생성한 포트에서 얻으며 외부 DB URL을 받지 않는다.
운영 데이터, 실제 계정, 지도 API 키를 사용하지 않는다.

```bash
./gradlew fleetEvidenceTest --rerun-tasks --console=plain
```

실행 위치는 BE 저장소다. 다른 Gradle task와 동시에 실행하지 않는다.
`@Tag("fleet-evidence")`를 기본 `test`에서 제외했으므로 일반 회귀 테스트를 실행했다고
이 측정까지 실행한 것으로 취급하지 않는다. 전용 task에는 성능 harness 한 개만 들어간다.

- 코드: `src/test/java/org/thisway/evidence/FleetEvidenceIntegrationTest.java`
- 매 실행 JSON: `build/reports/fleet-evidence/result.json`
- JUnit XML: `build/test-results/fleetEvidenceTest/`
- 보존 JSON: [실제 결과와 raw samples](2026-09-07-fleet-evidence.result.json).

## 고정 조건

| 항목 | 값 |
| --- | --- |
| 논리 데이터·순서 seed | `20260907` |
| synthetic event 날짜 | `2026-09-01`, 한국 시간 의미 |
| 회사 / 회사별 장치 | 2 / 4, 총 8장치 |
| GPS observation/request | 1 |
| 동시 HTTP 작업 | 4 |
| Warmup | 장치당 2, 총 16요청 |
| 본측정 | 장치당 30, 총 240요청, 80요청씩 3회 |
| 동일 observation 재전송 | 장치당 3, 총 24요청, HTTP nonce는 매번 새 UUID |
| 최종 예상 GPS row | `16 + 240 = 256`, 회사별 128 |
| MySQL / RabbitMQ / Redis | `8.0.40` / `3.13.7-alpine` / `7.4.2-alpine` |
| 수집 rate 설정 | 장치당 분당 10000, 보호 로직은 사용하되 측정용 상한 |
| 일정 실행 | 통계 정규/자동 보정, 주소 worker cron 비활성화 |
| 외부 주소 변환 | synthetic 고정 응답, 외부 호출 없음 |

기준 행이 달라지지 않게 각 phase가 기대 row 수와 ready queue 0을 확인한 후 다음 phase로
넘어간다. 마지막에는 listener를 중지해 진행 중 처리와 ack가 끝나기를 기다린다.
JVM/OS/architecture/사용 가능 CPU/최대 heap은 JSON에 기록한다. Docker Desktop의 CPU·메모리
할당과 호스트 백그라운드 부하는 이 test가 고정하지 않으므로 재실행 수치의 동일성을 보장하지 않는다.
DB 중복효과 검증은 새 nonce의 동일 observation을 다시 보냈을 때 24행이 늘어나지 않는다는
조건이다. 같은 nonce의 요청을 거부하는 replay test는 별도 수집 보호 테스트가 담당한다.

## 측정 정의와 성공 조건

- HTTP p50/p95/p99: 각 실제 HTTP 요청의 `System.nanoTime()` 왕복시간, nearest-rank 방식.
  JWT 로그인, 키 발급, warmup, 중복검증 요청은 본측정 240개에서 제외한다.
- 요청 처리량: 본측정 요청 수 / 세 batch의 HTTP 실행 wall time 합. phase 사이 drain 대기는 제외한다.
- 오류: HTTP 200 이외 상태와 client exception(상태 0). 본측정·warmup·중복 모두 오류 0 요구.
- drain: 해당 batch HTTP 작업 완료부터 기대 DB row 수 및 ready queue 0 확인까지 시간.
  20ms polling 오차가 있고 unacked 별도 표본이나 메시지별 latency는 아니므로 `queue lag p95`로 부르지 않는다.
- 최종 DB row 256, 24회 observation 중복의 추가 row 0, 저장 queue ready 0, DLQ 0,
  consumer rejected 0, publisher storage confirmed 280 및 unconfirmed 0을 요구한다.
- 관측 수 SQL: 현행 `LogRepository.countGpsObservations`와 같은 query를 5회 warmup 후
  30회 실행한다. JDBC 왕복 p50/p95/p99와 MySQL `EXPLAIN ANALYZE`의 실제 plan을 JSON에 보존한다.
  256행짜리 검증이므로 대규모 index 효율이나 전체 통계 job 성능을 증명하지 않는다.
- 지연/처리량에 임의 운영 SLA를 설정하지 않는다. 정확성 조건을 통과한 측정값을 그대로 보고한다.

## 업무 API 검증

실제 비밀번호 hash를 저장한 회사 관리자 2명의 `/api/auth/login` 응답 token을 사용한다.
장치 키는 실제 관리 HTTP endpoint로 발급하며 원문 token/key는 JSON·assertion 본문에 보존하지 않는다.

1. 회사 A 장치의 10:00 ON / 12:00 OFF를 실제 수집 HTTP로 전달한다.
2. 내부 통계 service를 호출해 해당 날짜를 집계한다. 회사 관리자에게 운영 통계 저장 권한을 주지 않는다.
3. A 차량 목록 4건, 운행 1건, 누적값 1500, 운행 거리 500m를 확인한다.
4. A 회사 통계 완료 운행 120분, B 0분, 회사별 GPS observation 128건을 확인한다.
5. B token으로 A 차량·운행 상세를 요청하면 둘 다 404인지 확인한다.

브라우저 렌더링, 메뉴 이동, 지도, 실제 화면 캡처는 이 API 검증에 포함되지 않는다.

## 결과

최종 실행은 root의 `./gradlew test sseBrowserTest emulatorClientTest fleetEvidenceTest --console=plain`
검증 묶음(총2분54초)에 포함되었으며 이 전용 testcase는3.309초다. 초기 전용 실행23초와 구분한다.
macOS 26.6.2/aarch64, Java 21.0.7, JVM 최대 heap 512MiB, 인식 CPU 14개에서 실행했다.
원시 결과의 compiled class SHA-256은 `61184855d0d47d292cd3d6810ecc5560ab3fdba69bc837db1ce320f99c8d72f5`이다.

| 본측정 240요청 | 결과 |
| --- | ---: |
| HTTP p50 / p95 / p99 | 8.581 / 10.625 / 12.381 ms |
| HTTP 처리량 | 454.87 request/s |
| 오류 | 0 / 240, 0% |
| 3회 batch별 drain | 67.134 / 43.657 / 67.988 ms |
| 최종 GPS / 중복 추가 row | 256 / 0 |
| ready / DLQ / consumer rejected | 0 / 0 / 0 |
| storage confirmed / unconfirmed | 280 / 0 |
| broadcast unconfirmed | 0 |
| JDBC 관측 수 query p50 / p95 / p99 | 0.363 / 0.474 / 0.544 ms |

최종 JSON 측정 시각은 `2026-09-07T11:06:51.606876Z`다.
[공유 deadline 도입 전 원시 기준선](2026-09-07-fleet-evidence.before-deadline.result.json)은 별도 보존했다.
두 실행은 소규모 fixture 반복이며 host load 등을 통제한 대조 실험이 아니므로 개선율을 주장하지 않는다.

warmup16·중복24 요청도 모두 200이다. 실제 login 2개 회사, A 차량4/운행1/거리500m/120분,
B 0분, 각 회사 observation128, B→A 차량/운행 상세404 검증을 통과했다.
MySQL plan은 company_id index로 차량4개를 찾고 vehicle_id index로 차량당GPS32행을 읽었다.
이 256행에서는 적은 비용이지만 날짜 범위 index가 대규모 데이터에도 적절하다는 증거는 아니다.

테스트 실행 전 기본 sandbox는 Gradle wrapper cache lock 파일 쓰기를 거부했다. Gradle cache와
Docker 접근 권한을 사용한 동일 명령 재실행은 통과했으며 데이터나 검증 조건을 완화하지 않았다.
성능 수치를 보존하기 위해 실행하지 않은 before/after 비교나 개선율은 작성하지 않았다.

## CI와 운영 적용의 별도 경계

최초 조사 당시 CI/CD의 `checkout@v4`, `setup-java@v4`, `cache@v3`는 runtime 경고 정리 후보였다.
공식 upstream의 아래 v5 `action.yml`을 확인했으며 모두 `runs.using: node24`를 사용한다.
runtime 경고 해결 범위를 좁히려면 이 세 action만 v5로 바꾸고 JDK 21·기존 캐시 입력은 유지할 수 있다.

- [checkout v5의 실제 action.yml](https://raw.githubusercontent.com/actions/checkout/v5/action.yml)
- [setup-java v5의 실제 action.yml](https://raw.githubusercontent.com/actions/setup-java/v5/action.yml)
- [cache v5의 실제 action.yml](https://raw.githubusercontent.com/actions/cache/v5/action.yml)
- [공식 cache README](https://github.com/actions/cache): v5는 runner `2.327.1` 이상 필요.

최초 분석 이후 CHANGE-044에서 위 action v5 및 AWS credentials v6 참조만 실제 변경했다.
원격 CI는 실행하지 않았다. 공식 upstream 최신 major 선택과
Node runtime 경고 정리는 서로 다른 변경이다. AWS 인증 action은 같은 번호의 v5라도 runtime을
추측해 올리지 않는다.

- [configure-aws-credentials v5](https://raw.githubusercontent.com/aws-actions/configure-aws-credentials/v5/action.yml)는 node20이다.
- [configure-aws-credentials v6](https://raw.githubusercontent.com/aws-actions/configure-aws-credentials/v6/action.yml)는 node24이며
  현행 `aws-access-key-id`, `aws-secret-access-key`, `aws-region` 입력이 존재한다. 새 profile 파일 모드와
  구분해 기존 환경변수 모드를 유지할 때 `output-env-credentials: true`를 명시하는 변경을 검토할 수 있다.
- [amazon-ecr-login v2](https://raw.githubusercontent.com/aws-actions/amazon-ecr-login/v2/action.yml)는 현재 태그가 node24를 사용하므로
  runtime만을 위해 major를 바꿀 필요가 없다.

배포 workflow 검증은 실제 AWS 호출 없이 static 검토까지만 가능하다. 현행 CD는 불변 image
tag/digest와 task definition을 기록하지 않고 강제 재배포를 요청한다. Jib의 Pinpoint javaagent
파일은 실제 ECS에서 어떻게 주입되는지 확인해야 한다. 개발·운영 RabbitMQ 이미지도 `latest`를
사용하므로 이 fixture의 고정 버전과 동일하다고 간주하지 않는다.

운영 적용에는 아래 실제 상태와 소유자 판단이 필요하다.

- 배포 대상 계정/리전/cluster/service, 현재 running task definition과 image digest, 재배포 권한.
- 기존 DB 복원본의 schema preflight·migration 결과·백업 복구 확인. 운영 DB 자동 baseline 금지.
- broker의 현행 queue arguments/policy, backlog·DLQ 수, 소비자 혼재 여부와 장치 키 배포/교체 범위.
- 성공 상태를 판단할 health/5xx, queue ready/unacked, 저장 confirmed/unconfirmed,
  consumer rejected, DB connection/lock/오류, 신규 통계·주소 실패 지표 및 관찰 시간.
- rollback은 알려진 이전 image **digest/task definition**으로 수행하고 새 migration과 이전 app의
  호환성을 먼저 확인한다. `latest` 재배포 또는 Flyway down migration을 rollback으로 가정하지 않는다.
- 장치 인증 전후 backlog는 자동으로 현 소속에 귀속하지 않는다. device identity 없는 기존 메시지는
  검토·승인된 복구 절차가 필요하다.

여기서 운영 점검·배포·rollback을 실행했다는 주장은 하지 않는다.
