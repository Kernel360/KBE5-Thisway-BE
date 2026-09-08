# CHANGE-052~056 — 취업 포트폴리오2~6단계

## 기준과 목표

BE `d3404c0`, FE `2a29252`, Emulator `8d017d6`의 clean checkout에서 시작했다. 사용자는 추가 기능을 보류하고 여섯 단계 완료를 요청했다. 원 팀 차량 관제 시스템 위에 추가하는 AI 지원 개인 현대화이며 원 팀 전체 구현을 개인 기여로 바꾸지 않는다.

기존 HTTP timer는 broker 접수 응답만, consumer timer는 queue wait를 제외한 처리만 보여 줬다.90초 부하와 정적 경보 규칙으로 장시간·반복·실제 수신을 주장할 수 없었다. FE/Emulator에는 PR workflow가 없었다.

## 완료 판정

- 2: 서버의 인증 후 publisher 진입→transaction proxy 반환 경계를 추가. 정상/적체/중복의 모든 소비 시도 원시값과 p95/p99, 미측정 수를 보존한다. 실패 서비스 호출은 성공 측정을 남기지 않는다.
- 3: seed·8장치·20/40/80RPS 각100초·client8worker·queue256·Java heap512MiB·이미지 버전을 고정하여 fresh containers3회. 호스트 자원과 백그라운드 작업은 고정하지 못했으므로 용량/SLA 실험은 아니다.
- 4: 같은 데이터·query·parameter에서 기존/후보 인덱스60쌍 교대 비교, EXPLAIN ANALYZE와 결과 동등성. API 전체 개선 주장 없이 후보 채택 판단 근거로 남긴다.
- 5: 실제 Prometheus→Alertmanager→로컬 수신함 firing/resolved, 실제 HTTP 완료 로그의 Loki correlation 검색, 읽기/쓰기 분리, retention 설정 및 오래된 접수 거부.
- 6: FE/Emulator workflow와 로컬 동일 핵심 명령 검증, 제출 요약·학습/기여 경계·리뷰 가능한 로컬 변경 단위. 공개 push/merge 및 원격 CI 결과는 별도다.

## 설계 선택과 흐름

DB schema에 계측 필드를 넣는 대신 서버 생성 AMQP header를 사용했다. 업무 데이터·멱등성 key를 바꾸지 않는다. JVM monotonic time은 서로 다른 process 간 비교할 수 없어 epoch millis를 선택했으며 clock sync 의존을 명시한다. header를 클라이언트 요청에서 복사하지 않는다. missing/invalid/clock_skew는 별도 counter여서 빠른 저장으로 오인되지 않는다. 중복은 commit 시도이고 고유 row가 아니다.

latency Measurement 이벤트에는 outcome·duration만 있고 장치/회사/trace를 담지 않는다. fixture만 이 이벤트를 모아 exact sample percentile을 만든다. 운영 timer에는 고카디널리티 label을 추가하지 않는다.

조회 후보는 disposable DB에만 만든다. 읽기가 빨라져도 인덱스 쓰기·저장 비용이 있으며, 최근100개 조회 실험을 LIMIT1인 실제 현재 위치 API의 속도 향상으로 주장하지 않는다. 추가 기능·근거 없는 migration은 넣지 않았다.

중앙 로그는 전체 로그를 무차별 전송하는 대신 고정 HTTP 완료 이벤트16개를 제한 수집한다. Loki+gateway와 역할별 임시 인증으로 실제 검색·거부를 검증한다. 지속 collector와 운영 retention 물리 삭제는 이 실험이 증명하지 않는다. 알림은 외부 사람에게 보내지 않고 실제 Alertmanager의 로컬 수신까지 검증한다.

## 실행·실패 기록

- 첫 compile에서 SimpleMeterRegistry가 AutoCloseable이 아닌 점, 기존 직접 생성 consumer의 생성자 변경 누락을 발견하고 수정했다.
- 좁은2개 테스트 + 실제 관측성 실험은2분12초 성공.30초 단계 원시 결과에서 정상 p95 11/7/5ms, 적체 p95 3328ms, 중복 p95 4ms였다. 이후 최종 반복 실험을 대표 결과로 사용한다.
- 실제 로그 캡처를 추가할 때 일반 fleet warmup까지 치환해 compile이 실패했다. 적용 위치를 관측성 분기로 제한하고 재실행했다. 실패 로그는 임시 로컬 진단으로 분리했다.
- 경보 첫 실행은 exporter Content-Type 누락으로 수집이 되지 않아 firing 대기90초가 만료됐다. text/plain; version=0.0.4와 사전 up1 gate를 추가했다.
- 시스템 Python3.14에는 Pydantic이 없어 Emulator 테스트 import가 실패했다. 기존 전용 Python3.11.16 venv의 requirements 일치(pydantic2.4.2, python-dotenv1.0.0, requests2.31.0)를 확인해32개 테스트를 실행했다.
- FE 단위14개, browser26개, production3개 성공, Vite build3.49초. 실제 API 연결을 대신하는 fixture 테스트와 production chunk 검증의 차이를 유지한다.
- 로컬 경보·중앙 로그 최종 실행은 성공했다. firing/resolved 수신, 실제 HTTP 완료16개 수집, correlation 검색, anonymous read/writer read/reader write401, config403,25시간 이전 접수400을 확인했다.
- 첫 반복 회차는 FE/npm 및 경보·로그 검증과 일부 겹쳤다. workload·이미지·데이터는 고정했으나 호스트 활동까지 통제한 실험이 아니므로 최대용량/메모리 누수 부재/운영 SLA를 주장하지 않는다.
- 최종 반복·전체 회귀 수치는 아래 증거 요약에 확정한다.

## 공부·직접 설명

공부할 개념: transaction proxy의 commit 시점, wall clock vs monotonic clock, at-least-once의 중복 계측, histogram bucket과 exact quantile, B-tree 복합 인덱스의 equality/range/order, exporter→rule→Alertmanager 경계, 로그의 label cardinality와 retention.

직접 실습: consumer를 멈췄을 때 HTTP200과 DB row 정지·저장 지연 상승을 함께 설명한다. 후보 EXPLAIN에서 Sort가 사라지는 이유와 scan row 수를 읽는다. reader로 push·writer로 query가 거절되는지 재현한다.

면접 질문과 답변 체크포인트:

1. HTTP200이면 GPS 저장 완료인가? broker 접수와 DB commit, ACK를 구분하고 지연 timer의 시작·끝을 답한다.
2. p99가0인데 미측정 메시지가 많다면? 누락을0으로 합치지 않고 coverage와 outcome counter를 확인한다.
3. 중복 요청도 지연 표본에 들어가는가? 소비 시도 단위이며 unique row 수와 별도임을 설명한다.
4. 복합 인덱스로 얼마나 빨라졌나? 실제 원시 비교 범위·동일 조건·warm cache·API가 아닌 query shape·쓰기 비용을 함께 답한다.
5. 로그24h retention을 검증했다는 의미는? effective 설정과 오래된 접수 거부를 확인했으며24시간 뒤 물리 삭제 관찰은 없다고 구분한다.
6. CI yaml이 있으면 배포가 검증된 것인가? 로컬 핵심 명령/원격 runner/실 배포를 나눈다.

AI가 구현·실험·분석·문서화를 수행했고 사용자는 포트폴리오 우선순위와 단계 진행을 승인했다. 사용자의 독립 설명·면접 숙련은 자동 완료 표시하지 않는다.

CI 추가 검증: npm ci 후 단위14/browser26/production3을 다시 통과했다. 선택적 YAML 검증 시 js-yaml이 없어 Node validator가 실패했으며 새 의존성을 추가하지 않고 Ruby 표준 YAML parser로 두 workflow의 trigger·contents:read를 확인했다. FE94dc5f1/Emulatorfa7242f 로컬 commit을 보존했다.

반복 실험3회가 모두 성공했다. 각5분, 정상 요청 합계42,000건 오류0. 세 실행의 compiledClassesSha256·fixture·runtime 환경이 같음을 summary 생성기에서 확인했다. 이후 변경은 rollback 검증과 dashboard 보강이며 이 반복 측정 소스는 별도 commit으로 고정한다.

최종 기본 회귀475개와 기존 fleetEvidenceTest1개가 실패/오류/skipped0으로 통과했다. disposable MySQL의 AFTER INSERT trigger가 오류를 발생시키게 하여 실제 rollback·GPS행0·성공 commit timer 미생성·consumer failed1을 검증했다. 이 fixture에만 trigger 생성을 허용하는 MySQL 설정을 적용했다.

최종 `./gradlew test fleetEvidenceTest observabilityEvidenceTest --console=plain`은4분36초 성공.475+1+1개 모두 실패/오류/skipped0. 실제 Prometheus 새 commit 지표 존재와 Grafana20패널을 검증하고 Chromium 화면에서 p95 곡선과 미측정0 표시를 확인했다. 통계/주소 panel의 No data는 이번 GPS fixture가 해당 시나리오를 실행하지 않았기 때문이며0으로 바꾸지 않았다. [최종 검증/이미지 hash](../../experiments/2026-09-08-portfolio-completion/final/validation.json).

반복 실험 결과의 정확한 재현 기준선은69f34cd이다. 이후에는 실제 rollback negative test, 로그 캡처의 동시 append 보호, 새 timer의 Prometheus/Grafana 확인만 보강했고 production 지연 계측 코드는 바꾸지 않았다. 전체 변경/원시 자료 Gitleaks0건, 문서 상대 링크와 git diff --check를 확인했다.
