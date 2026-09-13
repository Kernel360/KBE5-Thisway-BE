# JWT의 현재 회원·회사 상태 admission 검증

## 메타데이터

- 날짜: 2026-09-12 KST
- 상태: Verified locally — 구현·변경 전 실제 MySQL 재현·변경 후 집중 회귀 79개, 기본 전체 509개 및 SSE/client/fleet 5개 성공.
- 담당: 통합 AI 설계·구현·실행, 백엔드 AI 실제 MySQL 테스트, 별도 AI 문서 작성
- 기준: 직전 전체 감사 완료 후 같은 로컬 작업 트리. BE HEAD `db28ee9` 위의 미커밋 변경이며 신규 commit/push/merge/배포를 수행하지 않았다.
- 관련 결정: [ADR-012](../../adr/012-current-member-admission.md)
- 범위: 사람 JWT admission과 로그인·현재 회원 식별. 장치 API key 인증은 유지한다.

## 1. 문제와 근거

JWT 서명·만료는 발급 당시 claim의 무결성을 확인할 뿐, 발급 후 회원·회사의 현재 활성
상태나 역할·소속과의 일치를 자동으로 보장하지 않는다. 현재 회원을 email로만 다시
조회하면 이전 email을 새 계정이 재사용할 때 과거 토큰과 다른 계정이 결합할 위험도 있다.

변경 전 실제 MySQL과 API 경계의 `MemberAccountAdmissionIntegrationTest` 12개를 실행해
**12/12 실패, build 21초**를 확인했다. 회원·회사 삭제 API commit 이후 이전 token 요청,
email 재사용 계정, 현재 DB와 다른 role/company, 누락·다른 회원 PK 등의 거부 시나리오가
401 대신 200을 반환했다. 실제 로그인 token의 `memberId`도 없었다. 원시 결과는
[before.json](../../experiments/2026-09-12-member-account-admission/before.json)에 보존했다.
이는 격리 MySQL/API 재현이며 실제 운영 계정에서 발생한 장애를 관찰했다는 뜻은 아니다.

이번 변경은 보호 요청마다 불변 회원 PK와 기존 email/company/단일 원소 `roles` claim을
현재 DB와 비교한다. 같은 12개 MySQL 회귀가 변경 후 12/12 성공했고, 관련 집중 회귀
전체 79개가 통과했다. 문서 담당도 전달받은 요약과 실행 로그를 읽어 결과를 확인했다.

기존 JWT 발급·Security·회원 모델은 원 팀 구현에 기반한다. 기존 사용자 개인 기여는
`original-contributions.md`의 Git/PR 근거를 유지한다. 이번 admission 보강은 AI 지원
개인 현대화이며 인증 시스템 전체를 사용자 원래 개인 구현으로 주장하지 않는다.

## 2. Acceptance criteria

- [x] 새 JWT의 `memberId`는 양수 필수이고 기존 `sub` email/companyId/단일 원소 `roles` 계약을 유지한다.
- [x] `memberId` 누락·잘못된 값의 구토큰은 401이며 email fallback으로 복원하지 않는다.
- [x] 현재 회원 PK/email/company/role 및 member.active/company.active가 모두 일치하면 허용한다.
- [x] 회원·회사 비활성화 또는 role/company 불일치가 다음 인증 요청에서 401로 거부된다.
- [x] 같은 email을 다른 PK 계정이 재사용해도 이전 계정 token으로 인증되지 않는다.
- [x] 로그인도 활성 회사 조건을 확인하고 현재 회원 조회는 memberId+companyId를 사용한다.
- [x] DB 오류에서는 admission을 허용하지 않고 고정 500을 유지한다. 이를 401로 바꾸지 않는다.
- [x] 실제 MySQL 허용·거부 12개 및 관련 집중 회귀 79개를 실행하고 결과를 기록했다.
- [x] 변경 후 BE 전체 509개 및 관련 SSE/client/fleet 5개 회귀를 실행하고 결과를 기록했다.
- [x] SSE 신규/재연결의 admission과 이미 열린 연결의 미지원 즉시 종료를 구분한다.
- [x] 재활성화 ABA·영구 폐기 미지원, 구토큰 재로그인 전환, 비용·미측정 성능을 문서화한다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| JWT 서명·만료만 검사 | 인증 DB I/O 없음 | 현재 계정 상태·역할 변경이 토큰 만료까지 반영되지 않음 | 제외 |
| email로 현재 회원만 조회 | 기존 subject를 활용 | email 재사용 계정을 구분하지 못하고 tenant/role 조건을 놓칠 수 있음 | 제외 |
| 불변 PK와 모든 현재 속성을 DB 단일 predicate로 비교 | 계정 identity와 현재 상태를 함께 검사 | JWT 인증 요청마다 query 1회·DB 의존성이 추가됨 | 채택 |
| TTL cache | 반복 DB 요청을 줄일 여지 | 비활성화 반영 지연·invalidation 설계 필요 | 이번 범위에서 제외 |
| authRevision/세션 denylist | 재활성화 후 과거 토큰 재허용 및 개별 영구 폐기를 설계 가능 | schema/mutation/보관/배포 범위 확대 | 후속 결정 |

불변 PK는 email 변경·재사용과 계정 identity를 분리한다. 현재 상태를 요청마다 확인하므로
TTL 동안의 상태 반영 지연을 피하지만, 처리량이나 latency가 좋아졌다고 주장하지 않는다.
기존 JWT 사용자는 다음 요청에서 401을 받으면 재로그인해야 한다. 과거 토큰에 PK를
추정해 넣는 호환 경로는 계정 구분이라는 목적을 약화하므로 채택하지 않는다.

## 4. 구현과 실행 흐름

구현한 production 파일과 책임은 다음과 같다. 경로는 `src/main/java/org/thisway/` 기준이다.

| 파일 | 변경 책임 |
| --- | --- |
| `member/domain/MemberReader.java` | 현재 계정 identity 검사를 port에 정의 |
| `member/infrastructure/MemberReaderImpl.java` | `isCurrentIdentity`를 repository에 위임하고 활성 회사 포함 로그인 조회 사용 |
| `member/infrastructure/MemberRepository.java` | `existsCurrentIdentity` 단일 predicate 및 `findActiveLoginMemberByEmail` fetch join |
| `support/security/infrastructure/JwtTokenGenerator.java` | 불변 `memberId` 발급, 기존 subject/companyId/roles 유지 |
| `support/security/filter/JwtAuthenticationFilter.java` | 양수 PK·claim 및 현재 DB identity 검증 후 인증 구성 |
| `support/security/config/FilterConfig.java` | JWT 필터에 `MemberReader` port 주입 |
| `support/security/dto/request/MemberDetails.java` | 인증 principal에 `memberId` 보존 |
| `support/security/service/SecurityService.java` | 현재 회원을 memberId+companyId로 조회 |

신규 테스트는 `MemberAccountAdmissionIntegrationTest`와
`MemberAccountAdmissionAvailabilityTest`다. 기존 `SecurityIntegrationTest`,
`JwtAuthenticationFilterTest`, `SecurityServiceTest`, `DeviceCredentialIntegrationTest`도
현재 회원 identity를 가진 fixture로 보완했다.

기존 malformed claim 회귀에는 검증 대상 이외의 claim을 실제 회원 PK/회사/email로
맞춰, 모든 시나리오가 단순히 `memberId` 누락 때문에 통과하는 잘못된 테스트를 피했다.
DeviceCredential의 role별 fixture는 DB의 실제 해당 역할 회원을 만들고 그 회원의 PK와
email로 token을 발급한다. 임의로 role만 바꾼 token이 admission에서 먼저 거부되어
장치 관리의 기존 role/tenant 검증을 건너뛰지 않도록 했다.

```text
로그인
  -> 활성 회원 + 활성 회사 fetch join 확인
  -> JWT 발급: memberId + 기존 subject/companyId/단일 원소 roles

보호 요청
  -> JWT 서명·만료·memberId claim 검증
  -> MemberReader: PK + email + company + role + 두 active 조건의 DB 조회
  -> 일치: memberId를 가진 MemberDetails로 SecurityContext 구성
  -> 미일치: 401
  -> DB 오류: admission 거부, 기존 고정 500

업무가 현재 회원을 필요로 하는 경우
  -> SecurityService.getCurrentMember()
  -> memberId + companyId로 조회
```

DB admission query가 하나라는 뜻이지 보호 요청 전체가 query 한 개라는 뜻은 아니다.
업무별 기존 repository 조회와 `getCurrentMember()` 조회는 추가로 발생할 수 있다.
역할·tenant별 실제 자원 접근 검증도 이 admission만으로 대체하지 않는다.

인증 상태를 확인한 뒤 진행 중인 요청과 계정 변경을 하나의 transaction/lock으로
직렬화하지 않는다. 이미 통과한 요청의 즉시 취소나 열린 SSE의 즉시 종료는 범위 밖이다.
SSE 신규 연결과 재연결은 현재 상태를 확인한다.

FE의 앞선 세션 처리는 401에서 해당 요청 token과 현재 token이 같을 때 세션을 정리하고,
403과 500에서는 token을 유지한다. DB 오류의 고정 500을 보존하여 장애를 계정 만료로
오인하지 않는다. 이 변경에서 FE 세션 저장 방식이나 장치 credential은 바꾸지 않는다.

## 5. 검증 결과

다음 수치는 **직전 전체 감사의 역사적 기준선**이며 이 변경의 성공 결과가 아니다.

| 직전 검증 | 완료된 기준선 | 출처 |
| --- | --- | --- |
| BE 기본 전체 | 488개 성공 | [직전 전체 감사](../fullstack-audit-2026-09-12.md) |
| BE opt-in 통합 | 5개 성공 | 직전 전체 감사. 기본 test와 별도 집계 |
| FE | unit 14 + browser 32 + production 8 = 54개 성공, build 성공 | [세션 처리 작업 기록](2026-09-12-auth-session.md) |
| Python Emulator | 36개 성공, 9.727s | [Emulator 감사 기록](2026-09-12-emulator-ops-audit.md) |

| 이번 변경 명령·실험 | 결과 | 증거 |
| --- | --- | --- |
| 변경 전 `MemberAccountAdmissionIntegrationTest` | **12개 중 12개 실패**, error/skip 0, build 21초 | [before.json](../../experiments/2026-09-12-member-account-admission/before.json), `/private/tmp/thisway-20260912-account-before.log` |
| 변경 후 집중 회귀 6개 suite | **79/79 성공**, failure/error/skip 0, build 43초. 아래 명령 사용 | [targeted-after.json](../../experiments/2026-09-12-member-account-admission/targeted-after.json), `/private/tmp/thisway-20260912-account-targeted.log` |
| 이 중 실제 MySQL admission 회귀 | **12/12 성공**, 변경 전과 동일한 12개 시나리오 | targeted-after.json의 `MemberAccountAdmissionIntegrationTest` |
| 이 중 DB availability 경계 | **3/3 성공**. Reader port 오류 주입으로 HTTP 401/500/복구 경계를 확인 | targeted-after.json의 `MemberAccountAdmissionAvailabilityTest` |
| 변경 후 BE 기본 전체 | **509/509 성공**, 76 suites, failure/error/skip 0 | [full-after.json](../../experiments/2026-09-12-member-account-admission/full-after.json) |
| 변경 후 opt-in 통합 | **5/5 성공**: SSE 2 + Python client 2 + 실제 fleet browser 1. 기본 전체와 함께 build **3분 57초** | full-after.json, `/private/tmp/thisway-20260912-account-full.log` |

변경 전 명령:

```bash
./gradlew test --tests org.thisway.support.security.MemberAccountAdmissionIntegrationTest --console=plain
```

변경 후 집중 회귀 명령:

```bash
./gradlew test \
  --tests org.thisway.support.security.MemberAccountAdmissionIntegrationTest \
  --tests org.thisway.support.security.MemberAccountAdmissionAvailabilityTest \
  --tests org.thisway.support.security.SecurityIntegrationTest \
  --tests org.thisway.support.security.filter.JwtAuthenticationFilterTest \
  --tests org.thisway.support.security.service.SecurityServiceTest \
  --tests org.thisway.emulator.credential.DeviceCredentialIntegrationTest \
  --console=plain
```

79개 구성은 MySQL admission 12, availability 3, Security API 20, JWT 필터 6,
SecurityService 4, 기존 DeviceCredential 통합 34다. 모두 신규 테스트 79개를 추가했다는
뜻이 아니며, build 43초는 이 전체 집중 실행의 시간이다.

변경 후 전체·교차 저장소 회귀 명령:

```bash
./gradlew test sseBrowserTest emulatorClientTest fleetBrowserTest \
  -Demulator.python=/private/tmp/thisway-20260912-emulator-venv/bin/python \
  --console=plain
```

실제 nginx/Chromium SSE의 인증·회사 격리·수신·재연결과 Python client→Boot→MySQL
장치 계약을 확인했다. 실제 FE `App`과 Boot/MySQL/Redis/RabbitMQ를 연결한 두 회사
로그인·차량·운행·통계·타회사 자원 404도 통과했다. [fleet 결과](../../experiments/2026-09-12-member-account-admission/fleet-browser-result.json)의
pageErrors·외부 요청은 0이다. 지도 provider/reverse geocoding은 fixture이며 운영 지도
SDK 검증은 아니다. 이번에는 FE 단독 54개·Python 단독 36개를 다시 실행하지 않았다.

[소스 SHA-256](../../experiments/2026-09-12-member-account-admission/tested-source-manifest.json)은
실행 대상의 변경·신규 BE 소스/테스트와 build 설정을 식별한다. 결과 JSON에는 원시 로그의
hash를 기록하고, 민감 claim/token이 포함될 수 있는 상세 로그 본문을 복사하지 않았다.

availability 3개는 현재 계정 미일치 시 401 및 업무 service 미호출, DB 예외 시 고정
500·민감 원문 비노출 및 같은 token의 다음 요청에서 reader 복구 후 204, 비정상 PK가
DB 조회 이전 401로 거부되는 경계를 확인한다. DB 장애는 `MemberReader` port에 주입했다.
실제 MySQL 프로세스 종료·failover·운영 DB 복구 또는 FE browser 상태를 새로 검증한
증거가 아니다. FE 500 token 유지 계약은 앞선 작업과 현재 설계를 따른다.

- Before→After: 같은 MySQL API 12개가 12개 실패에서 12개 성공으로 바뀌었다.
- 전체·opt-in 회귀까지 모두 통과했다. 이번 범위의 실행 검증은 완료했으며 운영 적용과 성능 측정은 별도다.
- 문서 담당은 코드 수정·Git 명령·테스트 실행을 수행하지 않았다.
- 로컬 DB 검증을 운영 계정 전체 폐기, 배포 완료, 처리량 또는 SLA 증거로 확대하지 않는다.

## 6. 실패 사례와 남은 위험

- `memberId` 없는 구토큰은 401로 재로그인이 필요하다. 배포 전 이 전환을 설명해야 한다.
- DB 오류는 fail-closed 500이므로 DB 장애 동안 보호 요청도 사용할 수 없다. FE는 token을
  유지하며, DB 복구 후 아직 유효한 token은 다시 현재 상태 확인을 받을 수 있다.
- 회원/회사/role/company가 원래 상태로 돌아오면 만료 전 과거 token이 다시 허용될 수 있다.
  이 재활성화 ABA를 막는 `authRevision` 또는 영구 폐기는 포함하지 않는다.
- 비밀번호 재설정 시 기존 token 폐기, 개별 session 폐기, migration도 이번 범위 밖이다.
- 이미 admission을 통과한 요청과 열린 SSE의 즉시 종료는 보장하지 않는다. 신규 요청과
  SSE 재연결에서 상태를 확인한다.
- JWT 인증 요청마다 DB 조회 1회가 추가된다. cache를 쓰지 않은 선택의 처리량·지연 비용은
  아직 측정하지 않았으며 기존 부하 시험 수치를 이 구현의 성능으로 재사용하지 않는다.
- 다른 회사 자원 접근을 막는 repository/service의 tenant 검증은 계속 필요하다.

## 7. 학습 기록

- 불변 identity와 변경 가능한 email/role/company 속성의 차이
- JWT 서명 검증, 현재 계정 admission, 자원별 인가의 역할 구분
- `MemberReader` port를 통한 security→application/infrastructure 의존성 경계
- 단일 predicate 조회와 두 번 조회 사이 상태 변경의 차이
- 401/403/500과 FE 세션 유지 정책, DB 장애 시 fail-closed 의미
- 현재 상태 비교와 영구 revocation, A→B→A 재활성화 한계

직접 연습: token을 발급한 뒤 회원 비활성화→요청→재활성화→요청 순서로 예상 결과를 먼저
적고 테스트와 대조한다. 이어 같은 email의 새 PK 계정을 만들었을 때 왜 결과가 다른지 설명한다.
실제 운영 회원을 조작하지 않고 격리 MySQL fixture에서 실행한다.

## 8. 예상 면접 질문

1. JWT가 stateless인데 왜 매 요청 DB를 조회하나요?
   - 서명·만료는 발급 당시 claim의 유효성이고 현재 계정 상태는 DB에 있다. 즉시성 요구와
     query/DB 가용성 비용의 선택이며 모든 stateless 이점을 유지한다고 주장하지 않는다.
2. email과 companyId를 확인하는데 memberId도 필요한 이유는 무엇인가요?
   - email은 다른 계정에서 재사용될 수 있다. 불변 PK가 이전 계정의 token이 새 계정에
     결합하는 것을 막고 companyId/role/email은 발급 당시 상태와 현재 상태를 추가로 비교한다.
3. DB 조회 실패를 왜 401로 바꾸지 않나요?
   - 현재 상태 확인 실패가 계정 무효라는 증거는 아니다. 요청은 허용하지 않되 고정 500으로
     장애를 표현하고 FE token을 보존한다.
4. 비활성화했다가 재활성화하면 과거 token도 영구 폐기되나요?
   - 아니다. 현재 상태가 다시 일치하면 미만료 token이 허용될 수 있다. 영구 폐기를 원하면
     authRevision/별도 session 정책과 상태 변경 경로를 함께 설계해야 한다.

## 9. AI 활용과 사람의 검증

- AI에게 맡긴 범위: 문제 분석, 대안·설계, 코드/실제 MySQL 회귀, 통합 실행, 문서 분담.
- 채택: 불변 memberId와 현재 DB 단일 predicate, MemberReader port, DB 오류의 고정 500.
- 제외: email fallback, TTL cache, 이번 변경에 authRevision/migration/영구 폐기까지 추가하는 범위 확대.
- 담당 분리는 여러 사람의 리뷰와 같지 않다. 사용자의 직접 설계 채택·실행·면접 설명은
  아직 확인하지 않았으며 AI 자동화 검증과 구분한다.
- 통합 구현 담당과 MySQL 회귀 담당을 나누어 검사하고, 별도 담당이 변경 계약을 독립 검토했다.
  문서 담당도 단일 repository predicate, 테스트 fixture의 실제 회원 identity, availability
  오류 주입 범위와 원시 결과를 읽어 문서의 주장을 대조했다. 이 역할 분담은 사람 리뷰의 증거가 아니다.
- 집중·전체·opt-in 자동화 검증 결과는 위 표에 확정했다. 운영 적용·실계정 상태·최대 용량은
  AI가 확인하지 않았다.
- 원 팀 기여, 기존 사용자 개인 기여, 이번 AI 지원 현대화의 경계를 유지한다.
