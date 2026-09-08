# CHANGE-051 — 취업용 포트폴리오 1단계: metrics 수집 권한 분리

2026-09-08. 사용자 요청: 추가 기능 개발은 보류하고 여섯 과제를 취업용 완료 기준으로 단계적으로 진행한다. [전체 완료 기준](../job-portfolio-completion.md)을 먼저 고정했다.

## 문제·기준선

BE `10edce3`의 `/actuator/prometheus`는 명시적으로 permitAll이었다. 실제 Grafana/Prometheus가 있다는 사실만으로 지표 접근이 보호된 것은 아니었다. 현재 로컬 `codex/observability-evidence`에서 기존 변경을 보존하며 후속 변경한다. 실제 운영 ingress/보안그룹을 변경하거나 조사한 작업은 아니다.

## acceptance

- 미인증·잘못된 키·query token·중복 Authorization 거부.
- 사람이 ADMIN이어도 수집 권한을 대신하지 못하고, 수집키로 업무 API/쓰기 불가.
- 미설정이면 fail-closed, 잘못된 hash 설정은 값 노출 없이 시작 거부.
- Actuator base path를 바꾸어도 동일 경계 적용.
- 실제 Prometheus bearer 파일 수집 up1, 기존 업무·로그 회귀 유지.
- secret이 이미지/Git/실험 결과에 들어가지 않고 로컬 생성은 기존 파일을 덮어쓰지 않음.

## 선택과 trade-off

IP allowlist만으로 제한하면 reverse proxy/컨테이너 네트워크에 의존해 로컬 재현과 배포 간 해석이 달라질 수 있다. 사람 JWT를 공유하면 서비스 계정과 회사 권한의 경계를 섞게 된다. 이번에는 실제 Prometheus endpoint에 우선 적용되는 별도 SecurityFilterChain과 전용 bearer credential을 선택했다. 인증뿐 아니라 네트워크 격리가 완성됐다고 주장하지 않는다.

애플리케이션은 원 token 대신 SHA-256을 보유하고 입력 형식·길이·중복 header 검사 후 고정 길이 digest를 비교한다. 빈 설정은 전부 거부하고 잘못된 hash 형식은 부팅 오류다. 단일 token 정책이라 rotation 때 짧은 수집 공백이 가능하다. 운영에서는 TLS·수집기 네트워크 제한·secret 전달·회전 계획이 필요하다.

기존 logging/JWT/error filter가 @Bean이면서 SecurityFilterChain에 삽입되어 있었다. 자동 servlet 등록까지 남기면 별도 metrics chain을 통과한 opaque token을 일반 JWT parser가 처리할 수 있다. 세 filter의 servlet 등록을 비활성화하고 명시적인 application chain에서만 실행한다. body size filter 등 다른 filter는 변경하지 않았다.

Prometheus dev/prod/격리 설정 모두 credentials_file을 사용한다. token은 이미지에 포함하지 않으며 production mount는 운영자가 별도로 제공해야 한다. 로컬 생성기는0700 디렉터리/0600 hash 환경 파일과 read-only bind용0644 token 파일을 생성한다. 부모 디렉터리의 탐색 제한과 컨테이너 내 읽기 권한의 차이는 [runbook](../../runbooks/metrics-access.md)에 설명했다.

## 실행과 실패 기록

- 첫 좁은 회귀: `./gradlew test --tests '*ActuatorApiSecurityTest' --tests '*MetricsCredentialTest' --tests '*SensitiveLoggingIntegrationTest' --console=plain`,13초 성공.
- 전체 회귀의 JUnit XML:473개, 실패/오류/skipped0.
- 임시 별도 디렉터리에서 생성기 실행: 디렉터리/파일 mode, hash 일치, 두 번째 실행 거부와 원래 token 보존 확인. token 원문은 출력하지 않았다.
- Docker/Gradle은 sandbox 외부 소켓·캐시 접근이 필요해 해당 실행에만 승인된 도구 권한을 사용했다. 실제 운영 변경은 수행하지 않았다.
- 최종 `./gradlew test observabilityEvidenceTest --console=plain`: 4분20초 성공. 전체473개와 실제 관측성 실험1개 모두 실패/오류/skipped0. 미인증·잘못된 키401 assertion을 통과했고 실제 Spring/RabbitMQ scrape up1, Grafana datasource proxy success를 확인했다.
- [원시 결과](../../experiments/2026-09-08-metrics-access/result.json)와 [검증·소스 hash](../../experiments/2026-09-08-metrics-access/validation-summary.json)를 보존했다. workload4200건 오류0, unique DB4296행, duplicate24건에도 추가행0, DLQ0. 이는 보안 변경 후 회귀 증거이며 성능 개선 비교가 아니다. 앞선 CHANGE-050 자료는 소급 변경하지 않았다.

## 범위와 학습

원 팀 Actuator/Micrometer 기반 위에 추가한 AI 지원 개인 현대화다. 소속 기업·독립 구현 경험·운영 보안 적용 성과를 대신 주장하지 않는다. 이번 단계의 로컬 권한 분리와 실제 운영 네트워크 격리는 별도다. 이후2단계는 수집 접수→DB commit 지연이며, 기존 HTTP timer는 broker 접수까지만 보여 준다는 전제를 유지한다.

직접 실습: 키 없는 scrape401과 올바른 키의 up1을 비교하고, application hash를 비운 상태에서 health와 metrics의 응답 차이를 확인한다. 실제 키를 curl command line이나 캡처에 쓰지 않는다.

면접 질문과 답변 체크포인트:

1. 왜 기존 JWT를 Prometheus에도 사용하지 않았나? 사람·회사 권한과 기계 수집 권한을 분리하고 token이 부여하는 endpoint/HTTP method 범위를 답한다.
2. hash만 저장하면 TLS가 필요 없는가? bearer 원문은 전송되므로 TLS가 필요하며 저장 보호와 전송 보호를 구분한다.
3. 별도 SecurityFilterChain만 추가하면 왜 충분하지 않을 수 있나? filter @Bean의 servlet 자동 등록과 chain 내 등록을 구분하고 opaque token이 JWT parser에 도달하는 경우를 설명한다.
4. 미설정과 잘못된 형식의 차이는? 미설정은 앱 기능을 유지하되 수집 거부, 잘못된 형식은 잘못된 배포 설정을 조기에 탐지한다.
5. 현재 rotation의 trade-off는? 단일 key의 서버 hash/파일 교체 순서에 따른 scrape 공백과 실제 배포 전환 계획을 설명한다.

AI가 분석·구현·검증·문서화를 수행했다. 사람 사용자는 여섯 과제의 우선 진행을 승인했으며, 실제 이해와 운영 적용은 별도 검증 대상이다.

최종 정적 검증: `git diff --check`와 격리 Compose config 검증 성공. Gitleaks 최초 검사는 credential 관련 파일명 옆 소스 SHA-256 세 개를 generic-api-key로 오인했다. 실제 token이 아닌 소스 hash임을 확인하고 manifest를 path/sha256 필드로 분리했다.
변경 파일과 새 실험 자료를 대상으로 재검사한 결과 Gitleaks 탐지0건.
