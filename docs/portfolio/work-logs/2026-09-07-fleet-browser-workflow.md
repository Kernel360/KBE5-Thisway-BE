# CHANGE-045: 실제 브라우저의 회사별 업무 흐름 검증

## 메타데이터

- 날짜: 2026-09-07
- 상태: Verified locally — 실제 업무 화면·외부 회사 HTTP/SSE404·범례 가시성 확인, JUnit1/1 통과
- 브랜치: BE `codex/statistics-batch-restart`, FE `codex/frontend-dependency-security`
- 로컬 검증만 수행. 원격 배포·commit·push 없음.

## 1. 문제와 근거

이전 SSE browser fixture, 통계 mock browser test, CHANGE-041 API 시연은 회사 담당자가 실제
로그인해 화면을 이동하는 업무 흐름을 한 번에 검증하지 않았다. 서로 다른 fixture의 성공을
합쳐 전체 사용자 시연 완료로 표현할 수 없다.

원 팀의 화면/로그인/차량 관리 기능과 이후 개인 현대화의 기여 경계는 기존 기록을 따른다.
이번 변경은 실제 App, Boot, MySQL API를 연결한 독립 시연과 오류 발견·회귀 증거다.

## 2. Acceptance criteria

- [x] 합성 회사 2개와 회원/차량/운행/GPS/통계만 fresh MySQL에 준비한다.
- [x] `src/main.jsx -> App.jsx`의 실제 로그인과 메뉴·목록·상세·통계 화면을 사용한다.
- [x] 모든 업무 `/api` 호출은 실제 Boot로 proxy하고 응답을 mock으로 대체하지 않는다.
- [x] 2시간/500m 완료 운행, GPS2건, 날짜1/1 집계, 최초 fleet snapshot 의미가 화면과 DB에서 일치한다.
- [x] 회사 B의 회사 A 차량·운행 일반 HTTP와 SSE 모두404, 예상외5xx0을 확인한다.
- [x] 결과 JSON/화면 캡처를 보존하고 계정/password/token을 기록하지 않는다.
- [x] 실제 1440px 화면에서 통계 chart 제목과 업무시간/심야시간 범례를 읽을 수 있다.

## 3. 선택과 경계

| 선택지 | 장점 | 한계 | 판단 |
| --- | --- | --- | --- |
| 화면마다 API mock | 빠르고 실패 격리가 좋음 | 실제 API shape/권한/네트워크 오류를 놓침 | 기존 회귀용 유지 |
| 실제 운영/개발 서버 | 환경 충실도 | 데이터/계정/외부 호출 위험과 상태 불명 | 미사용 |
| Testcontainers Boot + 실제 App/Vite proxy | 실제 계약과 tenant 검증, 재현 가능 | 외부 지도/배포 환경은 별도 | 채택 |

지도 component/helper와 BE reverse geocoding만 합성 응답으로 격리한다. 외부 Google Fonts는
test server의 HTML 변환에서 제거하고 local fallback font로 렌더링한다. 그 외 외부 origin 요청이
발생하면 browser가 요청을 abort하고 검증을 실패시킨다.

## 4. 실행 흐름

1. Java test가 MySQL8.0.40/RabbitMQ3.13.7/Redis7.4.2와 Boot random port를 생성한다.
2. 회사A/B 회원·차량을 만들고 관리 HTTP로 A 장치 키를 발급한다.
3. 실제 수집 HTTP로 10:00ON, GPS2건, 12:00OFF를 저장한 뒤 내부 통계 service로 날짜를 집계한다.
4. 권한600 임시 fixture 파일에 합성 로그인 정보만 넣고 Node subprocess로 전달한다.
5. Node가 실제 App을 Vite로 제공하고 `/api`를 위 Boot random port에 연결한다.
6. Chromium에서 A 로그인→대시보드→차량관리/상세→운행기록/상세→통계 날짜적용을 수행한다.
7. 별도 browser context의 B 로그인으로 자기 차량만 표시되고 A 상세 접근이 거부되는지 확인한다.
8. method/path/status만 결과에 남기고 계정 표시를 마스킹한 캡처를 보존한다. fixture 파일은 삭제한다.

## 5. 재현과 결과

JDK21/Docker, sibling FE의 npm dependencies와 Playwright Chromium이 필요하다.

```bash
./gradlew fleetBrowserTest --rerun-tasks --console=plain
```

- BE 코드: `src/test/java/org/thisway/evidence/FleetBrowserIntegrationTest.java`
- FE 코드: `tests/live-fleet/workflow.mjs`, 지도 격리 fixture2개
- 실행 결과: `build/reports/fleet-browser/result.json`, 캡처5개, `browser-process.log`
- JUnit 결과: `build/test-results/fleetBrowserTest/`
- 기본 `test`에서는 `fleet-browser` 태그를 제외한다. 다른 Gradle과 병렬 실행하지 않는다.
- 최종 증거는 `--rerun-tasks`로 실제 실행했다. 초기 구성에서는 FE 파일이 Gradle 입력에 없어
  FE만 변경하면 잘못 `UP-TO-DATE`가 되었다. 이를 발견한 뒤 `fleetBrowserTest`/`sseBrowserTest`에
  sibling FE source/tests/package/Vite 입력을, `emulatorClientTest`에는 Python source/tests/requirements
  입력을 등록했다. 일반 실행도 이 파일 변경을 감지한다. task 입력 등록 검증은 통합 담당이 별도로 수행한다.
  `UP-TO-DATE` 자체를 새 브라우저 실행의 성공 증거로 쓰지 않는다.

최종 실행은 **56초, JUnit1/1 통과**이며 JUnit testcase 실행은3.386초다.
결과 시각은 `2026-09-07T11:08:36.063Z`다. 회사 A/B 실제 로그인200, 회사별 차량 표시,
2시간/500m 운행 상세와 GPS2건/집계1일 통계가 일치했다. 회사 B의 A 차량·운행 일반 HTTP2개 및
SSE2개 모두404, 예상외5xx0, browser pageerror0, 외부 origin 요청0이다.

보존한 [결과 JSON](../../experiments/2026-09-07-fleet-browser/result.json)과 화면:

- [회사 대시보드](../../experiments/2026-09-07-fleet-browser/01-company-dashboard.png)
- [회사 차량 관리](../../experiments/2026-09-07-fleet-browser/02-company-vehicles.png)
- [운행 상세](../../experiments/2026-09-07-fleet-browser/03-trip-detail.png)
- [회사 통계](../../experiments/2026-09-07-fleet-browser/04-company-statistics.png)
- [다른 회사 운행 접근 거부](../../experiments/2026-09-07-fleet-browser/05-foreign-trip-denied.png)

5장 모두 열어 표시값·계정 마스킹을 확인했다. 통계 범례는 고정350px 여백 때문에 각 글자가
세로로 줄바뀌는 문제가 있어 header 너비/flex wrap/gap 및 label nowrap으로 수정했다.
최종 캡처에서는 제목과 업무시간/심야시간 범례가 가로로 읽힌다. 데이터나 시각 정책은 바꾸지 않았다.
또한 다른 회사 운행 접근 거부 화면은 글자색에 배경용 `theme.palette.error.main`(`#FBDFDF`)을 사용해
읽기 어려웠다. 해당 오류 문구만 진한 빨강 `#b91c1c`로 변경했고 최종 캡처에서 문구가 읽히는 것을 확인했다.
배경 `#F5F7FA`에 대한 sRGB 상대 휘도 계산 대비는1.17:1에서6.03:1이다. 다른 화면의 전체 접근성을 검증한 수치는 아니다.

## 6. 실패·수정과 남은 한계

- 첫 시연은 모든 업무 assertion을 통과한 뒤 외부 Google Fonts 요청 검출로 실패했다.
  실제 외부 요청은 abort되었고 test server의 외부 font link만 제거했다. 업무 API 격리는 완화하지 않았다.
- 재실행을 이전 Gradle 종료 전에 시작한 실수로 XML 결과 출력이 잠시 충돌했다.
  새 실행을 즉시 중단하고 두 process 종료를 확인한 뒤 단독으로 다시 실행했다.
- 첫 성공 JSON에서 B의 foreign SSE500을 발견하여 완료를 보류하고 실제 browser 회귀 조건을 강화했다.
- BE 공통 오류 응답의 `application/json` Content-Type 명시 후 두 SSE가 실제404로 응답함을 재검증했다.
  기존 로그에서 converter 예외 class/stack을 확인하지 못했으므로 특정 converter 예외가 원인이었다고 단정하지 않는다.
- 최종 캡처 재실행 한 번은 Testcontainers Ryuk 연결 실패로 초기화 단계에서 종료됐다. 이전 산출물을
  새 성공 증거로 사용하지 않았고 process 종료 뒤 같은 task를 단독 재실행해 통과했다.
- Kakao SDK/지도 모양/실제 주소, 운영 TLS/배포 proxy, 운영 데이터, 사용자 독립 설명은 미검증이다.
- 캡처의 기준 차량 수는 최초 계산 snapshot이다. 과거 차량 소속 변경 이력 전체를 재현하는 증거는 아니다.

## 7. 학습과 면접 질문

1. API 테스트와 브라우저 테스트를 모두 통과했는데 왜 실제 업무 흐름을 또 검증하나요?
   - 서로 다른 fixture의 성공만으로 실제 response shape, router, browser 요청 헤더, 권한 오류의 결합을 보장할 수 없다.
2. `/api`를 proxy하면 API mock인가요?
   - HTTP를 실제 Boot로 전달하며 응답을 조작하지 않는다. 이 fixture에서는 DB 저장·인증·권한을 실제로 실행한다.
3. 왜 cross-tenant UI가 오류를 보여 주는데도500을 결함으로 다루나요?
   - 권한/없는 resource는 규정된404여야 하고500은 서버 장애로 분류된다. 화면 문구만 확인하면 이 오류를 놓친다.
4. 왜 계정 UI 일부가 캡처에서 가려져 있나요?
   - 시연 증거에 계정·credential을 포함할 필요가 없으며 실제 메뉴·업무 값만 남기기 위해서다.

## 8. AI 활용과 사람 확인

AI는 실제 연결 fixture, locator/API assertion, 원시 응답 상태 대조와 캡처 검사를 수행했다.
기존 여러 mock test 결과를 전체 업무 시연으로 합치는 주장은 거절했다.
사람의 독립 재실행·코드 설명은 별도 제출 기준으로 남긴다.
