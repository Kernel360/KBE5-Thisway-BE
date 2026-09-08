# Prometheus 수집 접근 제어

## 계약

`GET /actuator/prometheus`는 사람 JWT와 별도의64자리 소문자hex bearer token이 필요하다. 애플리케이션은 token의 SHA-256만 보유하고 일정 길이 digest를 비교한다. `thisway.metrics.token-sha256`가 비어 있으면 모든 수집 요청을 거부한다. 잘못된 hash 형식은 부팅을 중단한다. 미인증/잘못된 token은401, 이미 인증된 사람도 수집키가 없으면403이다. 같은 키로 업무 API를 호출할 수 없다. query parameter와 중복 Authorization header는 허용하지 않는다.

별도 우선순위 SecurityFilterChain을 실제 Prometheus actuator endpoint에 적용한다. 일반 JWT parser를 metrics chain에서 실행하지 않으며, 기존 logging/JWT/error filter의 자동 servlet 중복 등록을 비활성화했다. `health`의 상태 요약만 공개하고 env/heapdump 등 노출 범위를 늘리지 않는다.

## 로컬 기동

```sh
python3 scripts/observability/init-metrics-credential.py
set -a
. infra/observability/.secrets/application.env
set +a
# 위 환경변수를 전달한 터미널에서 기존 방법으로 backend를 시작한다.
docker compose -f infra/observability/compose.yml up -d
```

생성 도구는 기존 디렉터리를 덮어쓰지 않으며 키를 출력하지 않는다. `.secrets`는 Git ignore와0700 디렉터리로 보호한다. application.env는0600, token 파일은0700 디렉터리 안의0644이며 Prometheus에 그 파일 하나만 read-only bind mount한다. 이 구성은 host의 다른 사용자에게 디렉터리 탐색을 허용하지 않으면서 컨테이너의 비root 수집기가 파일을 읽도록 한다. Docker 접근 권한은 이 경계를 우회할 수 있으므로 신뢰한 사용자만 부여한다. 루트 디렉터리를 외부에 공유하거나 secret 파일을 로그/보고서에 복사하지 않는다.

dev Compose도 같은 credential 파일을 mount한다. production Prometheus 설정에는 `/run/secrets/metrics-token` 참조만 추가했다. 운영 token 파일과 애플리케이션 hash는 배포 관리자가 secret 전달 수단으로 주입해야 한다. 이미지에 token을 COPY하지 않는다. 기존 앱이 새 설정으로 교체될 때 키가 없으면 scrape가 거부되므로 설정 전환 순서를 먼저 확인한다.

## 회전과 보안 경계

서버 hash를 갱신하고 수집기 token 파일을 교체해야 한다. 현재 구현은 단일 token이므로 변경 순서에 따라 짧은 scrape 공백이 생길 수 있다. 운영에서는 승인된 배포·roll-back 계획에 회전을 포함한다. 원래 파일을 삭제하거나 임의로 덮어쓰는 자동 회전은 제공하지 않는다.

이 키는 읽기 권한을 가진 bearer secret이다. hash 저장이 평문 전송을 안전하게 만들지 않는다. 실제 운영에서는 TLS와 metrics 경로의 외부 ingress 차단/보안그룹 제한을 함께 적용해야 한다. 이번 로컬 검증은 애플리케이션 권한 경계의 증거이며 운영 네트워크 격리 증거가 아니다.

## 검증

`./gradlew test --tests '*ActuatorApiSecurityTest' --tests '*MetricsCredentialTest' --console=plain`.
전체 회귀는 `./gradlew test --console=plain`.
실제 익명/잘못된키401과 올바른 Prometheus bearer 수집은 `./gradlew observabilityEvidenceTest --console=plain`의 격리 fixture로 검증한다. 테스트 token은 매실행 생성되고 임시 파일은 정리하며 raw report에 포함하지 않는다.
