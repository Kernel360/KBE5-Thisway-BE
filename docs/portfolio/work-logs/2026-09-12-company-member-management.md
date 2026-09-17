# 회사 구성원 관리 안정화

- 날짜: 2026-09-12 KST
- 상태: Verified — 승인된 구성원 관리 범위의 구현·최종 회귀 완료
- 소스: BE `db28ee9`, FE `438f03a` 위의 기존 dirty worktree를 보존한 로컬 변경. 최종 소스 manifest와 결과는 [증거 폴더](../../experiments/2026-09-12-company-member-management/).
- 범위: `/company/user-management` 및 `/api/company-chef/members`. 추가 placeholder 개발·배포·push·merge 없음.
- 기여: 원 팀의 회원 API·화면 위에 AI 지원 현대화를 추가했다. 원래 개인 Vehicle/VehicleModel·Statistics/Batch 담당과 구분하며, 회원·보안 전체를 개인의 최초 구현으로 주장하지 않는다.

## 문제와 변경 전 근거

검색 버튼과 effect가 중복 요청하고, 늦은 응답을 걸러내지 않았다. CRUD 후 로컬 행·count 수정 또는 검색어 없는 재조회는 필터·페이지·서버 summary와 불일치할 수 있었다. 오류와 빈 목록이 구별되지 않고 오류 객체 전체를 console에 기록했다. 화면 진입에는 역할 guard가 없었다.

수정 전 Chromium 회귀 4개가 실제 실패했다: ADMIN/COMPANY_ADMIN/MEMBER의 직접 URL 거부 3개, 목록 실패 표시/재시도 1개. 최초 sandbox 실행은 로컬 포트 EPERM으로 시작하지 못했고, 허용된 로컬 테스트 실행으로 기능 실패를 별도로 확인했다. 응답 역전 등 나머지는 코드 관찰 후 신규 회귀로 확인했으며 변경 전 실행했다고 주장하지 않는다.

## 선택과 실행 흐름

| 선택지 | 장점 | 한계 | 결정 |
| --- | --- | --- | --- |
| CRUD 후 낙관적 로컬 행/count 갱신 | 요청 절약 | 검색 조건과 페이지 경계·다른 사용자의 변경을 재계산해야 함 | 제외 |
| 동일 검색/페이지로 목록·summary 재조회 | 서버 결과와 화면 복구가 명확 | 조회 두 건; 두 응답은 하나의 원자적 snapshot이 아님 | 채택 |
| AbortController만 사용 | 불필요한 이전 통신 중단 | 이미 처리 중인 후속 작업까지 구별 필요 | 요청 버전 및 cleanup 조건 병행 |
| 이메일 중복 선조회만 사용 | 친절한 오류 | 동시 요청이 모두 선조회를 통과 가능 | DB unique 유지 + 명시적 flush에서 알려진 constraint만 400/12001 변환 |
| 자체 focus trap | 자유로운 UI | Escape/복원/스크롤 유지 비용 | 기존 의존성 MUI Dialog 사용 |

1. `RequireCompanyChef`가 페이지 mount 전에 로그인/역할을 확인한다. JWT decode는 UI 흐름 제어일 뿐 서명 검증이 아니다. 서버의 실제 계정·역할·회사 검사가 권한 경계다. 거부 시 정상 세션을 지우지 않는다.
2. 검색 제출은 query 상태 한 번만 바꾼다. effect가 목록과 회사 summary를 요청하고 이전 요청을 취소한다. 현재 요청의 응답만 반영한다. 실패를 빈 목록과 분리하고 같은 조건으로 재시도한다.
3. 저장/삭제 시작 시 ref로 중복 진입을 막고 대상과 payload를 고정한다. 입력·닫기를 잠근다. 실패는 고정 메시지로 알리고 입력을 보존한다. 400 중복/403/404/서버·통신 실패를 구별하며 Axios 원문을 기록하지 않는다.
4. 성공하면 같은 검색 조건으로 다시 읽는다. 삭제 후 총 페이지가 줄면 서버 totalPages로 현재 페이지를 보정한다. 요약 실패는 0명으로 위장하지 않고 별도 오류와 재시도를 제공한다.
5. 서버는 이름/메모 255자, 기존 이메일/비밀번호 정책, 전화번호를 검증한다. 정렬은 id/name/email/role/phone/createdAt만 허용하고 size 최대 100, 정렬 동률에는 id를 추가한다.
6. PUT의 이메일 중복 조회는 자기 id를 제외해 MySQL 대소문자 비교와 일치시킨다. 등록과 수정의 flush에서 알려진 이메일 unique 제약만 기존 오류로 변환한다. 그 외 DB 오류는 숨기지 않고 transaction rollback으로 전파한다.

## 검증

실행 환경은 로컬 Java 21, MySQL 8.0.40 Testcontainers, 실제 Spring Security/로그인 및 Chromium이다. 외부 네트워크는 실제 브라우저 시나리오에서 차단한다. 기존 실행 중인 데모 DB·서버에는 쓰지 않는다.

| 검증 | 집중 결과 |
| --- | --- |
| 구성원 기존 service/controller/tenant | 성공 |
| 신규 실제 MySQL 통합 5개 | 역할별 6개 API 거부, 타회사 404와 DB 불변, 입력/정렬/size 4xx, case-only 수정, 동시 등록 8건 중 1건 commit, 동률 정렬/삭제 후 서버 결과 성공 |
| FE 구성원 브라우저 12개 | 역할 mount 차단, 오류 재시도, A/B 응답 역전, 중복 입력 보존·잠금·필터 유지, 마지막 페이지 삭제·오류, 390px 키보드/포커스, 403/404/네트워크 오류, 수정 후 필터 이탈 성공 |
| 실제 브라우저→Boot→MySQL | 실제 로그인→등록→검색→수정→모바일 모달→삭제 성공, 저장된 이름/회사/active=false 확인. 구성원 API mock 없음 |
| 최종 전체 회귀 | BE 기본 539개 + SSE 2개 + Python client 2개 + 실제 UI 2개, FE 단위 14개 + 개발 Chromium 55개 + production Chromium 20개, FE build 성공. 실패/skip 0 |

재현 명령(각 저장소에서 실행):

```bash
# BE: Docker와 Java 21 필요. 기본 test는 MySQL 통합 5개를 포함한다.
./gradlew test --tests '*CompanyMemberWorkflowIntegrationTest' --console=plain
./gradlew test sseBrowserTest emulatorClientTest fleetBrowserTest \
  -Demulator.python=/private/tmp/thisway-20260912-emulator-venv/bin/python --console=plain
# FE
npm test
npm run build
npx playwright test --workers=2
npx playwright test --config playwright.production.config.mjs --workers=2
```

Python 경로는 이번 로컬 환경이다. 다른 환경은 에뮬레이터 의존성이 설치된 Python 경로로 대체한다. `tests/live-company-members/workflow.mjs`는 BE의 `fleetBrowserTest`가 만든 일회용 계정 파일·random port로 실행한다. 실제 비밀번호/토큰을 공개 증거로 복사하지 않는다.

## 한계와 마무리 범위

- 목록과 summary는 별도 조회다. 다른 관리자가 계속 쓰는 동안 전역 원자적 snapshot 또는 실시간 동일 건수를 보장하지 않는다. 변경 성공 후 재조회 실패는 성공 취소가 아니며 화면 재시도로 복구한다.
- 자기 삭제·마지막 COMPANY_CHEF 삭제 보호 정책은 기존에 없으며 추가하지 않았다. 자기 삭제/자기 이메일 변경 후 기존 JWT가 다음 요청에서 거부되는 현재 identity 정책을 유지한다.
- 역할 편집 API는 만들지 않았다. 생성은 기존 3개 역할을 선택하고 PUT은 이름·이메일·전화·메모만 변경한다.
- 동시 등록 8건은 제한된 로컬 기능 회귀다. 부하/최대 처리량/운영 SLA가 아니다. 이번 작업은 별도의 장시간 부하 실험이나 외부 지도·메일 전달을 재검증하지 않는다.
- ADMIN/MEMBER 9개 placeholder 화면은 범위 밖이다. 전체 역할의 완성형 서비스로 표현하지 않는다.
- 모바일 스크린샷 수동 검토 중 제목 줄바꿈 문제를 발견해 수정하고 회귀를 추가했다. 디자이너 검수나 사용자 수용 검증으로 표현하지 않는다.

## 학습·면접

1. 메뉴 숨김, route guard, 서버 인가의 차이는?
   - 메뉴/guard는 UI 경험; 변조 요청도 막는 것은 서버 역할 검사와 companyId+id 조회다.
2. 요청 취소 후에도 버전 검사가 필요한 이유는?
   - 통신 취소와 이미 시작한 후속 처리의 결과 반영은 별개다. cleanup과 최신 요청 번호를 함께 검사한다.
3. 중복 이메일 선조회가 있는데 DB 제약을 처리하는 이유는?
   - 경쟁 요청이 선조회를 모두 통과할 수 있다. unique가 최종 불변식이고 flush로 예외 경계를 서비스 안에 둔다. 알려지지 않은 DB 오류를 중복으로 바꾸면 장애를 숨긴다.
4. 삭제 후 count를 1 빼면 안 되는 이유는?
   - 검색 필터·현재 페이지와 다른 관리자의 변경을 반영하지 못한다. 서버의 totalPages로 페이지를 보정한다.
5. 대소문자만 바꾸는 이메일 수정이 왜 실패할 수 있는가?
   - Java 문자열 비교와 DB collation이 다르다. DB 조회에서 자기 id를 제외한다.

직접 실습: 테스트의 지연 A→B 조건과 마지막 페이지 11번째 행 삭제를 먼저 손으로 예측한다. 집중 브라우저 테스트를 실행해 비교하고, MySQL 동시 등록에서 선조회와 unique의 책임을 설명한다. `flushEmailWrite`에서 알 수 없는 constraint까지 400으로 바꾸면 어떤 장애가 가려지는지도 설명한다.

## AI와 사람의 경계

이번 구현·테스트·캡처 검토·기록은 Codex가 수행했다. 앞선 계획의 AI 담당 배분은 과거 계획이며 이번 실행은 단일 에이전트 통합 수행이다. 사용자 독립 코딩·재현·설계 이해를 확인한 상태는 아니다. 사용자의 직접 시연·설명과 Figma 디자인 검토는 별도로 남는다.

최종 수치와 실행 요약: [verification.json](../../experiments/2026-09-12-company-member-management/verification.json), [execution-summary.txt](../../experiments/2026-09-12-company-member-management/execution-summary.txt), [source SHA-256](../../experiments/2026-09-12-company-member-management/source-sha256.json). 최종 화면 증거는 같은 폴더의 desktop/mobile PNG다. EOF 공백 정리만 최종 실행 후 수행했으며 기능 변경은 없다.
