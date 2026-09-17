# 비밀번호 재설정 시도 제한과 일회성 인증코드

## 메타데이터

- 날짜: 2026-09-12 KST
- 상태: Verified locally — BE 기본 전체 534개, opt-in 연동 5개, FE 65개와 build 성공
- 기준: BE `db28ee9`, FE `438f03a` 위의 기존 로컬 변경을 보존하고 진행
- 담당: 통합 AI 설계·SMTP timeout·전체 실행·문서, 백엔드 AI production·기존 회귀,
  Redis/MySQL AI 신규 통합 회귀, 프론트 AI 화면·브라우저 회귀
- 관련: [ADR-013](../../adr/013-password-reset-challenge.md), [통합 점검](../fullstack-audit-2026-09-12.md)

## 1. 문제와 근거

`PasswordService`의 Redis 조회→비밀번호 저장→Redis 삭제는 동일 코드의 동시 소비를
막지 못한다. 발송·오입력 시도 제한도 없다. 이 내용은 작업 시작 시 정적 코드에서
확인했다. 실제 MySQL/Redis의 신규 4개 테스트에서 **4/4 실패**를 재현했다. 동시 두 요청이
모두 200, 즉시 재발송도 200, 이메일을 재사용한 새 회원의 과거 코드 요청도 200이었다.
오입력 5회째도 한도 초과 429 대신 400이어서 시도 예산이 적용되지 않았다.

프론트는 한 번 발송하면 타이머 만료 후에도 재발송 버튼을 활성화하지 않는다. 요청 중
중복 제출과 발송 이후 이메일 수정도 별도 제어하지 않는다. 원 팀의 인증·메일·화면 구현을
기반으로 보강하며, 원 팀 전체를 사용자의 개인 구현으로 표현하지 않는다. 기존 개인
기여는 [기여 자료](../original-contributions.md)를 따른다.

## 2. Acceptance criteria

- [x] 서버의 계정별 발송 간격·고정 시간 창 예산을 실제 Redis에서 검증한다.
- [x] TTL 만료·오입력 한도·동일 코드 동시 소비·재사용 거부를 검증한다.
- [x] 실제 MySQL 비밀번호 commit과 실패 후 코드 미복원을 검증한다.
- [x] 불변 회원 PK, 현재 회원·회사 상태, 이전 발송 실패와 새 발급 경쟁을 검증한다.
- [x] 400/429/500을 구분하고 null/잘못된 입력이 안전하게 거부된다.
- [x] FE의 재발송·중복 제출·대상 이메일·오류별 복구를 브라우저에서 검증한다.
- [x] 관련 전체 회귀, 증거 파일, 한계, 학습·면접 기록을 확정한다.

## 3. 선택지와 결정

[ADR-013](../../adr/013-password-reset-challenge.md)에 GET/DELETE, Redis Lua, DB challenge
테이블, 분산 lock·보상의 장단점을 비교했다. 기존 Redis를 활용하는 원자적 코드 소비를
채택한다. 두 저장소 사이 commit 불확실성 때문에 DB 실패 시 코드를 되살리지 않는다.

## 4. 구현과 실행 흐름

| 변경 위치 | 책임 |
| --- | --- |
| `member/application/PasswordResetChallengeStore` | issue/consume/cancel port와 결과 계약 |
| `member/infrastructure/RedisPasswordResetChallengeStore` | 회원 PK key, 고정 발송 예산·TTL·오입력·소비·조건부 취소 Lua |
| `member/application/PasswordService` | 입력·활성 계정 조회, Redis·SMTP orchestration, BCrypt |
| `member/application/PasswordResetPasswordUpdater`, `MemberRepository` | 별도 DB transaction에서 현재 계정을 잠금 재확인하고 저장 |
| `PasswordController`, 요청 DTO, `ErrorCode` | `@Valid`, bounded 입력, 429/13006 |
| `support/config/MailConfig` | 직접 만드는 mail sender에 양수 연결·읽기·쓰기 timeout 반영 |
| dev/prod/test application YAML | 사용하지 않는 구형 코드 TTL/prefix 설정 제거 |
| FE `PasswordResetPage.jsx` | 재발송, 요청 중 중복 방지, 이메일 결합, 오류별 복구·입력 보존 |

BE 경로는 `src/main/java/org/thisway` 기준이다. 사용처가 없어진 `VerificationPayload`도
제거했다. 기존 민감 로그 회귀의 새 비밀번호 fixture를 허용 길이 안으로 보정하여
성공 요청에서 인증코드·비밀번호 비노출을 계속 확인했다. FE 변경 상세는
[프론트 작업 기록](../../../../KBE5-Thisway-FE/docs/portfolio/password-reset-recovery.md)에 있다.

```text
발송: 입력 검증 → 활성 계정·회사 조회 → 회원 PK 기준 발송 예산 예약·코드 저장
      → SMTP 발송 → 실패하면 발급 ID가 일치하는 현재 코드만 무효화

변경: 입력 검증 → 현재 활성 계정·회사 조회 → Redis 오입력 차감 또는 정답 원자 소비
      → BCrypt → 별도 updater transaction에서 PK/email/company/active를 잠금 재확인
      → MySQL 비밀번호 변경·commit → 성공 200
      → 소비 후 DB 실패라면 코드 미복원, 새 코드 요청
```

발송 실패도 발송 예산을 사용한다. 코드 소비와 비밀번호 commit은 서로 다른 원자성
경계다. 프론트 타이머와 버튼 상태는 서버의 TTL·Lua 검증을 대신하지 않는다.
업무 transaction과 row lock은 updater에 두고 Redis/SMTP 대기를 포함하지 않는다.
기존 POST `/api/auth/verify-code`, PUT `/api/auth/password`와 성공 200을 유지한다.
오입력 1~4회는 400/13000, 5회째와 이후 정답도 429/13006이며 새 발송은 별도 예산을
통과해야 한다. 활성 계정을 찾지 못한 입구 요청은 기존 404/12000, 소비 후 계정 재확인
불일치는 400/13000이다. 잘못된 비밀번호 400/12005는 코드 소비 전에 거부한다.

발송 간격은 60초, 첫 발송부터 고정 1시간 창에 최대 5회다. 임의의 연속 60분에 대한
rolling window가 아니다. 코드 TTL은 Lua의 600,000ms로 통일했다. 새 Redis namespace는
`thisway:password-reset:{memberId}:*`이며 기존 코드의 자동 이행은 없다.

## 5. 검증 결과

| 실행 | 결과 | 증거 |
| --- | --- | --- |
| 변경 전 새 회귀 4개 | **4개 모두 실패**, error/skip 0, build 16초 | [before.json](../../experiments/2026-09-12-password-reset-protection/before.json) |
| 변경 전 FE 신규 회귀 중 3개 | **3개 모두 실패**: 재발송, 중복 발송, 이메일 변경 | [frontend-before.json](../../experiments/2026-09-12-password-reset-protection/frontend-before.json) |
| 변경 후 BE 집중 회귀 | **42/42 성공**, failure/error/skip 0, build 20초 | [targeted-after.json](../../experiments/2026-09-12-password-reset-protection/targeted-after.json) |
| 변경 후 FE 집중 회귀 | **10/10 성공**, 6.5초. 이후 지연 응답 타이머 회귀 1개 추가 | `/private/tmp/thisway-20260912-password-fe-targeted.log` |
| 변경 후 전체 BE | **534/534 성공**, 77 suites, failure/error/skip 0 | [full-after.json](../../experiments/2026-09-12-password-reset-protection/full-after.json) |
| 변경 후 opt-in 연동 | **5/5 성공**: SSE 2 + Python client 2 + 실제 fleet browser 1. 기본 전체 포함 build **3분 50초** | full-after.json, `/private/tmp/thisway-20260912-password-full.log` |
| 변경 후 FE unit/build/browser/production | **14 + 43 + 8 = 65개 성공**, build 3.73초. browser 8.6초, production 4.1초 | [frontend-after.json](../../experiments/2026-09-12-password-reset-protection/frontend-after.json) |

직전 작업의 BE 기본 509개·opt-in 5개 성공은 이번 변경 전의 역사적 기준이다.
새 코드의 성공 결과로 재사용하지 않는다.

변경 전 명령: `./gradlew test --tests org.thisway.member.application.PasswordResetIntegrationTest --console=plain`.
첫 sandbox 실행은 Gradle wrapper cache lock 접근 거부로 테스트가 시작되지 않았다.
권한 검토를 거쳐 같은 명령을 실제 Docker 접근 가능 환경에서 실행했다. 원시 로그는
`/private/tmp/thisway-20260912-password-before.log`이며 hash와 assertion 결과를 JSON에
보존했다. 이때 PasswordService/Redis 동작은 수정 전이었고, 별도 SMTP timeout 설정만
추가된 상태였다. 메일은 mock이므로 이 설정이 재현 결과에 영향을 주지 않는다.

FE 변경 전 명령: `npx playwright test tests/browser/password-reset.spec.mjs --grep 'resend becomes|duplicate send|editing the email' --workers=2 --timeout=10000`.
최초 sandbox 실행은 loopback bind가 거부되었고, 담당의 실행 승인 요청이 응답 없이 대기해
통합 담당이 실행을 인수했다. 실제 브라우저에서 3개 실패를 재현한 결과만 before로 집계했다.
실제 React 화면에 API 응답을 fixture로 연결했으며 SMTP·실제 backend의 증거가 아니다.

변경 후 집중 명령:

```bash
./gradlew test \
  --tests org.thisway.member.application.PasswordResetIntegrationTest \
  --tests org.thisway.member.application.PasswordServiceTest \
  --tests org.thisway.member.interfaces.PasswordControllerTest \
  --tests org.thisway.support.logging.SensitiveLoggingIntegrationTest \
  --tests org.thisway.support.component.EmailComponentTest --console=plain
```

42개는 실제 Redis/MySQL 18, service 12, Controller 6, mail 4, 민감 로그 2개다.
이 중 수정 전 실행한 동일한 4개 시나리오가 모두 성공으로 바뀌었다. 나머지 14개 실제
통합 시나리오는 이후 추가했으므로 변경 전 18개 실패로 확대하지 않는다.

통합 검증은 MockMvc의 실제 Spring 필터/controller/service와 MySQL 8.0.40·Redis 7.4.2
Testcontainers를 사용했다. BCrypt는 실제 구현이며 일부 경쟁 조건을 만들 때만 spy로
진입 시점을 제어했다. 이메일은 mock이다. MySQL CHECK 제약으로 실제 UPDATE/commit을
실패시켜 rollback과 코드 미복원을 확인했다. 이는 운영 DB 중단·commit 응답 유실 훈련은
아니다. Redis 타입 오류도 격리 key로 주입했으며 Redis failover를 실행하지 않았다.

쿼터 창·재발송·코드 만료 검증 중 일부는 fixture key 삭제/TTL 단축으로 시간을 가속했다.
브라우저 시간도 Playwright clock으로 가속했다. 1시간 실제 대기나 장시간 부하 증거가 아니다.

최종 전체·연동 명령:

```bash
# BE
./gradlew test sseBrowserTest emulatorClientTest fleetBrowserTest \
  -Demulator.python=/private/tmp/thisway-20260912-emulator-venv/bin/python --console=plain

# FE
npm test
npm run build
npx playwright test --workers=2
npx playwright test --config playwright.production.config.mjs --workers=2
```

BE 수치는 이전 509개에 실제 재설정 통합 18개, service 기존 7→12개, Controller 기존
4→6개의 순증 25개를 더한 534개다. FE browser는 기존 32개에 신규 11개를 더한 43개다.
사례 수를 제품 완성도 퍼센트로 환산하지 않는다.

실제 nginx/Chromium SSE, 실제 Python client→Boot→MySQL, 실제 FE App→Boot→
MySQL/Redis/RabbitMQ의 두 회사 로그인·차량·운행·통계·타회사 404도 회귀했다.
[fleet 결과](../../experiments/2026-09-12-password-reset-protection/fleet-browser-result.json)의
pageErrors·외부 요청은 0이다. 이 fleet 검증은 비밀번호 이메일 발송의 E2E가 아니다.
지도와 reverse geocoding은 fixture이며 캡처는 `build/reports/fleet-browser/`에 있다.
이번 Python 단독 36개는 다시 실행하지 않았고 실제 client 연동 2개만 재실행했다.

[검증 소스 SHA-256](../../experiments/2026-09-12-password-reset-protection/tested-source-manifest.json)은
기존 변경을 포함한 BE/FE 변경·신규 소스/테스트와 설정, 삭제 파일 상태를 식별한다.
로그 원문 대신 집계와 hash를 보존했다. 세 저장소 `git diff --check`와 문서 링크를 확인했고,
기존 사용자 FE 차량 상세 파일의 hash가 앞선 감사와 같음을 확인했다. 신규 commit/push/
merge/운영 배포, 수동 local-demo 재시작, 운영 코드 삭제는 수행하지 않았다.

## 6. 실패 사례와 남은 위험

- DB 실패·commit 결과 불확실·소비 직후 프로세스 종료 시 새 코드가 필요하다.
- 기존 Redis 코드 구조는 이행하지 않으므로 새 버전 적용 후 재발급이 필요하다.
- 계정별 예산이며 IP/전체 서비스 제한과 계정 존재 응답 통합은 미포함이다.
- Redis 데이터 유실·failover, SMTP 수신함 도착, 운영 배포와 성능은 별도 검증이다.
- SMTP 실패 뒤 Redis 취소도 실패하면 발급 코드가 TTL까지 남을 수 있다. 원래 발송 오류와
  사용한 발송 예산을 유지하고 새 코드 요청을 안내한다.
- SMTP 각 I/O 기본 timeout 5초는 전체 HTTP 요청의 5초 deadline이 아니다.
- Redis SHA-256 저장은 원문 저장 최소화이며 6자리 코드의 offline 추측 방지 보장은 아니다.
- Lua 원자 실행은 스크립트 오류 이전 쓰기의 rollback 보장이 아니다. 예산이 보수적으로
  차감될 수 있다. Redis eviction/데이터 유실이 없다는 운영 보장도 추가하지 않았다.
- 비밀번호 변경 후 기존 JWT 영구 폐기와 이미 열린 SSE 종료는 이번 범위 밖이다.

## 7. 학습 기록

- Redis Lua의 원자성 범위와 Redis/MySQL 분산 transaction의 차이
- read-then-delete 경쟁, 고정 시간 창 예산, TTL, compare-and-delete
- 불변 계정 PK와 이메일 재사용, DB collation과 rate limit identity
- DB rollback·commit 불확실성·프로세스 종료 시 안전성과 재시도 편의의 선택
- UI 중복 클릭 차단과 서버 동시성 보장의 차이

직접 연습: 두 요청이 같은 코드로 동시에 진입할 때 Redis 소비와 DB commit을 시간순으로
그린다. 소비 직후 예외가 발생하면 비밀번호와 코드 각각이 어떤 상태인지 먼저 예측한 뒤
격리 테스트 결과와 대조한다.

## 8. 예상 면접 질문

1. `@Transactional`만 붙이면 왜 충분하지 않나요?
   - MySQL transaction이 Redis 조회·삭제까지 원자적으로 묶지 않는다. 각 저장소 경계를
     구분하고 코드 소비는 Lua로 직렬화한다.
2. DB rollback 때 코드를 복원하지 않는 이유는 무엇인가요?
   - commit 여부를 확신할 수 없는 실패와 복원 경쟁이 있다. 같은 코드 재사용보다 새 발급을
     요구하는 안전한 실패를 택했다. 비밀번호 변경이 반드시 성공하는 보장은 아니다.
3. 이메일 대신 회원 PK로 제한하는 이유는 무엇인가요?
   - 동일 계정의 이메일 표기 차이와 다른 계정의 이메일 재사용을 분리한다. 실제 DB에서
     확인한 계정에 발송 예산과 challenge를 귀속한다.
4. 프론트 버튼을 막으면 동시 요청 문제도 해결되나요?
   - 여러 탭·client·직접 API 호출이 가능하므로 서버의 원자 소비가 필요하다.

## 9. AI 활용과 사람의 검증

- AI 역할: 담당별 설계·구현·테스트·통합 리뷰·문서 초안.
- 채택: 전용 challenge port, 회원 PK, Lua 원자성, DB 실패 후 미복원.
- 제외: 임의 전체 계정 rate limit 보장, 불확실한 코드 복원, 이번 변경의 schema 확장.
- 담당 분리는 AI 간 검토이며 여러 사람의 독립 리뷰를 대신하지 않는다.
- 사용자 직접 실행·설계 설명·실제 운영 성과는 확인하지 않았다.
- 자동화 실행 결과는 위 표와 증거 JSON에 확정했다. 통합 담당과 별도 테스트 담당이
  Lua·회원 identity·DB transaction 경계를 검토했고, 프론트 TTL 표시의 응답 지연 문제는
  요청 시작 기준 deadline과 회귀 추가로 반영했다. 운영·사람의 독립 설명은 미확인이다.
