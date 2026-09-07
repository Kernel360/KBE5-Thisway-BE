# 원격 전달 검토안 — 2026-09-07

로컬 구현과 검증 결과를 세 저장소의 draft PR로 전달하기 위한 검토안이다. 아직 push/PR 생성/merge/배포는 하지 않았다.

## 정확한 전달 범위

| 저장소 | head branch | base | 이번 소스 커밋 |
| --- | --- | --- | --- |
| BE | `codex/statistics-batch-restart` | `develop` | `6cba824` 구현·증거, `43d7d00` CI action runtime |
| FE | `codex/frontend-dependency-security` | `develop` | `2a29252` |
| Emulator | `codex/emulator-telemetry-reliability` | `main` | `8d017d6` |

최신 origin fetch 후 BE는 기존 미전달 12개를 포함한 14개 커밋, FE는 기존 3개를 포함한 4개, Emulator는 1개다. 이 문서를 추가하는 BE 문서 커밋은 별도로 포함한다. 세 base 모두 head의 조상이며 뒤처진 커밋은 0개였다. 열린 PR은 세 저장소 모두 0개였다. 원격 상태는 공개 실행 직전에 다시 확인한다.

Emulator는 변경을 기존 main에서 먼저 커밋한 뒤 깨끗한 상태에서 전용 브랜치를 만들었다. 로컬 main도 `8d017d6`을 가리키지만 원격 main은 변경하지 않았다. 전용 branch만 push 대상으로 삼는다.

## 검토 순서와 호환성

1. 아래 세 draft PR을 서로 연결해 검토한다. BE의 인증/헤더와 통계 DTO 계약을 먼저 확인하고 두 client의 송신·표시 계약을 대조한다.
2. BE는 장치 credential과 nonce/시각 헤더를 필수로 받으므로 구 client와 혼합 배포하면 수집이 거부된다. PR 작성 순서가 안전한 배포 순서를 뜻하지 않는다. 장치 key 발급·배포, Redis, clock sync, 중지/전환 시간대를 실제 배포 계획으로 먼저 확정한다.
3. V10–V14뿐 아니라 base 이후 전체 migration을 복원본에서 검토한다. 원천 데이터, 기존 fleet 이력, legacy queue metadata를 추측해 채우지 않는다.
4. draft PR 생성 이후 원격 CI 결과를 확인한다. merge와 운영 배포는 이 공개 검토 요청에 포함하지 않는다. 실제 대상/backup/image가 필요한 [배포 절차](../runbooks/reliability-alerts-and-release.md)는 별도다.

## BE PR 초안

제목: `feat: 장치 수집 인증과 통계·주소 복구 신뢰성 강화`

장치 재할당 후 과거 메시지가 현재 차량에 귀속되거나, 지연 관측과 프로세스 중단으로 운행 통계가 원천과 어긋날 수 있는 경계를 보완한다. 장치 credential·assignment revision·nonce·수집 예산·본문 제한을 적용하고, 저장 메시지의 identity를 consumer에서도 재검증한다. Publisher에는 유한 queue와 공유 대기 예산을 적용한다.

차량 상태는 관측 시각 순서로 반영하고 누적 주행계와 운행 거리를 분리한다. 통계는 회사별 commit/checkpoint, 버전과 coverage, 원천 변경과 같은 transaction의 보정 queue, 최초 집계 fleet 목록과 revision 감사로 연결한다. 주소 조회는 핵심 commit 이후 durable retry로 처리하고, 중단된 배치는 모든 writer 정지를 전제로 offline 복구한다. V10–V14 및 기존 미전달 migration, 경보 규칙, 검증 자료와 운영 절차를 포함한다. CI action runtime 변경은 별도 커밋이다.

검증: BE 기본 459, SSE 2, 실제 Python 수집 2, 성능 fixture 1, 실제 업무 UI 1, JVM crash/restart 4개 통과. Prometheus synthetic 8개 규칙 통과. 정확한 명령과 실패·수정·재실행 기록은 [통합 검토](work-logs/2026-09-07-local-completion-review.md)에 있다.

경계: publisher confirm은 DB 저장 보장이 아니다. 실제 지도 API와 운영 AWS/DB/Alertmanager는 검증하지 않았다. 작은 fixture 수치를 운영 SLA나 개선율로 해석하지 않는다. 기존 fleet 자료는 자동 복원하지 않으며 주소 scheduler는 기본 비활성이다. FE/Emulator PR과 계약 검토가 필요하다.

## FE PR 초안

제목: `feat: 장치 인증 송신과 운행 통계 표시·경로 로딩 개선`

장치 credential과 요청별 nonce/시각을 붙여 변경된 수집 계약에 맞춘다. CSV 관측 시각을 KST로 보존하면서 시간·날짜 경계마다 packet을 나누고, key는 브라우저 영구 저장소에 보관하지 않는다. 버전이 있는 통계의 단위·coverage·fleet 기준과 미확인 운행 거리를 표시하며, 실시간 누적 주행계에서 시작 기준값을 뺀다.

기존 미전달 의존성 보안 업데이트와 React Router 변경을 포함한다. 페이지를 lazy load하고 chunk 로딩 실패 시 재시도를 제공한다. entry bundle은 약 976.82kB에서 282.30kB로 분리됐으며 전체 사용자 체감 속도 개선을 입증한 수치는 아니다.

검증: unit 14, 브라우저 fixture 26, production chunk 3개와 build 통과. 실제 Boot/MySQL/RabbitMQ/Redis 업무 UI 1개 통과. 업무 API는 실제 호출했고 지도 SDK와 외부 지도 helper는 fixture로 대체했다. BE 계약과 함께 반영해야 하며 원격 CI/운영 배포는 미실행이다.

## Emulator PR 초안

제목: `fix: 재시도 데이터의 장치 귀속과 GPS 관측 시각 보존`

장치 credential을 읽어 매 송신마다 새 nonce/시각을 생성하고 HTTPS를 기본으로 제한한다. 대기 중인 로그에는 원래 장치·MDN·key fingerprint를 고정해 저장한다. key가 바뀌거나 귀속을 확인할 수 없으면 과거 데이터를 새 장치로 보내지 않고 자동 재시도를 중지한다. 초기 송신과 재시도에서 credential snapshot을 공유해 조회 시점 간 변경도 막는다.

GPS는 관측 순서를 유지하면서 KST 시간·날짜 경계와 packet 크기에 따라 나눈다. 여러 packet을 반환하는 generator를 모든 호출부에 연결하고 원래 시각을 보존한다. queue는 메모리 기반으로 프로세스 재시작 시 내구성을 제공하지 않는다.

검증: Python unit/로컬 HTTP 32개 통과. 실제 Python→Boot→MySQL 2개에서 인증·폐기 및 자정 경계 5개 관측 시각 보존을 확인했다. 실제 Kakao 호출과 수동 전체 실행은 미검증이다. 새 BE 수집 계약과 연결한다.

## 공개 전 검사와 출처

- 검증 당시 [source manifest](../experiments/2026-09-07-local-source-manifest.json)의 모든 파일 SHA256이 커밋 후에도 동일하다. 이번 전달 정리에서는 구현을 변경하지 않아 전체 실행을 반복하지 않았다.
- `gitleaks 8.30.1 git --log-opts='origin/<base>..HEAD' --redact`로 BE 14/FE 4/Emulator 1개 커밋의 전체 추가 이력을 검사했고 탐지 0건이었다. 최신 문서 커밋도 다시 검사한다.
- 변경 파일 전체를 directory 방식으로 검사하면 기존 `src/test/resources/application.yml`의 JWT `secret-key` 1건이 탐지된다. 해당 값은 base 이전부터 존재하며 이번 diff에 추가되지 않았다. 실제 운영 사용 여부를 확인한 것은 아니므로 저장소 전체에 비밀정보가 없다는 뜻은 아니다. 값 자체는 보고서나 PR에 재기재하지 않는다.
- 원 팀 구현, 원래 개인 Vehicle/Statistics/Batch 기여, AI 지원 개인 현대화를 구분한다. AI는 구현·검증·문서화를 도왔고 사용자의 독립 이해나 실제 운영 성과를 대신 주장하지 않는다.

학습 확인: 인증된 HTTP와 DB 저장은 왜 별도 증거인가? client 교체와 BE 반영을 따로 배포하면 무엇이 실패하는가? commit 이후 파일 해시가 같다는 확인은 무엇을 보장하고 무엇을 보장하지 않는가?
