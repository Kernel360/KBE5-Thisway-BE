# CHANGE-044: 페이지 분리와 GitHub Actions Node24 runtime 정리

## 메타데이터

- 날짜: 2026-09-07
- 상태: Verified locally — FE unit14/browser20/production browser3/build 통과, 원격 CI·배포 미실행
- FE 브랜치: `codex/frontend-dependency-security`, 기존 Emulator 요청 보호 변경 보존
- BE 브랜치: `codex/statistics-batch-restart`, 기존 수집/통계/주소 변경 보존
- 커밋·push·PR·배포: 수행하지 않음

## 1. 문제와 근거

FE `App.jsx`는 모든 페이지를 정적 import하여 첫 entry chunk에 통계 차트, 관리 화면, Emulator까지
넣었다. 변경 직전 실제 `npm run build`의 entry는 976.82kB, gzip299.44kB이며 500kB 경고가 있었다.
통계 화면도 `quality.fleetBasis`를 사용하지 않고 계산 시점의 활성 차량이라고 고정 안내했다.

BE CI/CD는 checkout4/setup-java4/cache3, CD AWS 인증2였다. 공식 upstream action source를 확인해
Node24 runtime이 있는 버전으로 변경했다. 의존성/Java21/배포 동작 변경과 분리했다.

원래 팀 UI와 배포 workflow, 사용자의 원래 Vehicle/Statistics/Batch 기여는 기존 기여 기록을 따른다.
이번 개인 현대화는 화면 로딩 경계, 새 통계 계약 표시, action runtime 참조 정리다.

## 2. Acceptance criteria

- [x] 페이지별 dynamic import로 route에 필요한 code를 요청한다.
- [x] 로딩·chunk 다운로드 실패·사용자 새로고침 복구를 실제 production build로 검증한다.
- [x] 기존 로그인/인가/SSE/Emulator/통계 browser 회귀를 유지한다.
- [x] 새 최초집계 snapshot/과거 unknown/구버전 current fleet 기준을 구분해 표시한다.
- [x] CI/CD는 검증된 action version 참조만 바꾸고 배포 관련 나머지 YAML 바이트를 유지한다.

## 3. 선택지와 결정

| 선택지 | 장점 | 비용·위험 | 판단 |
| --- | --- | --- | --- |
| 경고 limit만 높임 | 변경이 작음 | 불필요한 초기 로딩 그대로 | 거절 |
| manualChunks로 vendor 분리 | 캐시 조정 가능 | 페이지별 lazy 없이 첫 방문 의존관계가 남음 | 이번 범위에서 제외 |
| React.lazy/Suspense + 오류 경계 | 페이지 로딩 지연과 실패를 사용자 흐름에서 처리 | chunk 로딩 실패 경로 검증 필요 | 채택 |

route 전체에 로딩 상태를 보이고 page load 실패에는 새로고침을 제공한다. 같은 lazy import가 실패하면
module cache 상태가 남으므로 단순 state reset 대신 새 문서를 받아 다시 로드한다.
요청하지 않은 자동 새로고침 loop는 만들지 않았다.

## 4. 실행 흐름과 변경

- FE `App.jsx`: page들을 `lazy()`로 선언, `Suspense` 로딩, pathname별 오류 경계.
- `statisticsPresentation.mjs`: `INITIAL_CALCULATION_FLEET_SNAPSHOT`,
  `LEGACY_FLEET_SNAPSHOT_UNKNOWN`, `CURRENT_ACTIVE_FLEET_AT_CALCULATION` 안내 구분.
- `CompanyStatisticsPage`: BE quality 값으로 기준 차량 설명, 집계 반영 지연 설명.
- `playwright.production.config.mjs`, `tests/production/lazy-routes.spec.mjs`: Vite preview가 제공하는
  실제 hashed JS chunk의 지연·실패·복구 검증. 업무 API를 검증하는 test와 구분한다.
- BE `.github/workflows/ci.yml`, `cd.yml`: action 참조만 변경.

## 5. 검증 결과

| 명령 | 실제 결과 |
| --- | --- |
| 변경 전 `npm run build` | entry976.82kB, gzip299.44kB, 큰 chunk 경고 |
| 변경 후 `npm run build` | entry282.30kB, gzip96.39kB, 최대chunk406.14kB, 큰 chunk 경고 없음 |
| `npm test` | 14/14 통과 |
| `npx playwright test` | 20/20 통과 |
| `npx playwright test --config playwright.production.config.mjs` | 3/3 통과 |
| CI/CD 원본 대비 참조 치환 대조 | action 7개 참조 외 나머지 YAML 바이트 동일 |

CHANGE-048 시간 경계 회귀 추가 후 기본 browser는26/26, production browser는3/3 통과했다.
CHANGE-045 범례/오류 가시성 수정까지 포함한 마지막 production build는 entry282.30kB/gzip96.39kB,
최대 CompanyStatistics chunk406.25kB/gzip119.30kB이며 큰 chunk 경고가 없다.

위 수치는 entry 파일 크기다. 최초 로그인에는 공통 API와 로그인 page chunk도 필요하므로 전체
다운로드 크기나 첫 화면 표시시간의 개선율로 표현하지 않는다. 실제 느린 네트워크 FCP/LCP는 미측정.
production test는 로그인 때 통계 chunk가 로드되지 않음, reset 메뉴 이동 때 해당 chunk 로드,
chunk 지연 중 상태 표시, 다운로드 실패 후 새로고침으로 복구를 각각 확인했다.

기본 sandbox 실행은 Vite 포트 listen을 EPERM으로 막았다. 같은 명령에 로컬 서버/Chromium 실행
권한을 사용해 통과했다. Python PyYAML과 Node js-yaml은 설치되어 있지 않아 parser 검증은 하지 않았다.
새 의존성을 추가하지 않고 HEAD workflow와 action 참조만 치환한 예상 바이트를 대조했다.
이 정적 검증을 원격 CI 통과로 표현하지 않는다.

## 6. 공식 runtime 근거와 남은 운영 검증

직접 열어 확인한 공식 source:

- [checkout v5](https://raw.githubusercontent.com/actions/checkout/v5/action.yml): node24
- [setup-java v5](https://raw.githubusercontent.com/actions/setup-java/v5/action.yml): node24, Temurin21 입력 유지
- [cache v5](https://raw.githubusercontent.com/actions/cache/v5/action.yml): node24
- [cache README](https://github.com/actions/cache): v5의 runner 최소 `2.327.1`
- [configure-aws-credentials v6](https://raw.githubusercontent.com/aws-actions/configure-aws-credentials/v6/action.yml): node24,
  현행 access key/secret/region 입력 유지. v5는 node20이므로 번호를 일괄 v5로 맞추지 않음.
- [amazon-ecr-login v2](https://raw.githubusercontent.com/aws-actions/amazon-ecr-login/v2/action.yml): 현재 태그가 node24이며 변경 불필요.

GitHub-hosted `ubuntu-latest`를 유지했다. 원격 CI의 runtime 경고 소멸과 실제 AWS credentials/ECR/ECS
실행은 push/CI/배포 단계에서 확인할 항목이다. 배포 대상·image tag·task definition·rollback은
이번 action version 변경으로 해결되었다고 주장하지 않는다.

## 7. 학습 기록

- 코드 분리와 지연 로딩의 차이: 파일을 나누는 것과 지금 필요한 파일만 받는 것은 다르다.
- React.lazy Promise rejection의 오류 경계, Suspense pending 상태와 실패 상태의 차이.
- Vite dev server 검증과 실제 hashed build chunk 검증의 차이.
- 날짜별 통계 snapshot의 기준 차량 수와 과거 fleet history의 차이.

## 8. 예상 면접 질문

1. entry가 976kB에서 282kB가 되었으니 사용자가 71% 빨라졌나요?
   - 파일 크기 비교다. 실제 다운로드·parse·render·네트워크 cache 조건의 시간은 별도 측정해야 한다.
2. chunk가 404라면 왜 로딩 spinner만 계속 표시하지 않나요?
   - pending은 Suspense가, Promise rejection은 오류 경계가 처리한다. 새로고침으로 새 entry와 chunk를 받는다.
3. GitHub Action의 v5가 전부 같은 Node 버전인가요?
   - action별 source의 runs.using을 확인해야 한다. AWS credentials v5는 node20, v6은 node24였다.

## 9. AI 활용과 사람의 검증

AI가 bundle 경계를 분석하고 lazy/error handling 및 회귀 시나리오를 작성했다.
사용자의 전체 남은 작업 진행 지시 범위에서 작은 local 변경을 채택했다.
실제 build 출력·Chromium chunk 요청·실패복구·통계 계약 검증을 대조했다.
사용자의 직접 코드 설명, 실제 배포 latency와 원격 CI 결과는 아직 확인하지 않았다.
