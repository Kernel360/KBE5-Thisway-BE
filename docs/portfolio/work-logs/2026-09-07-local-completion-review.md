# CHANGE-049 — 남은 개발 작업 통합 검토와 실제 검증

2026-09-07. 사용자 요청: 남은 작업을 확인하고 모두 진행. 현행 로컬 개발 범위와 실제 운영/원격/데이터 전환을 구분한다.

## 기준선과 출처

- BE: `codex/statistics-batch-restart`, HEAD `ef74fcf` + 보존된 기존 dirty 변경 + 이번 변경.
- FE: `codex/frontend-dependency-security`, HEAD `8fbd0da` + 보존된 기존 dirty 변경 + 이번 변경.
- Emulator: `main`, HEAD `594c3d0` + 보존된 기존 dirty 변경 + 이번 변경.
- commit/push/merge나 운영 DB 변경을 수행하지 않았다. 원 팀 코드, 원래 개인 Vehicle/Statistics/Batch 기여와 이번 AI 지원 개인 현대화를 구분한다.

## 완료한 구현과 교차 검토

1. CHANGE-038: 세 수집 endpoint의 nonce/freshness·Redis atomic budget·본문 byte 제한·GPS/Geofence 시각 검증. 두 client와 CORS를 함께 연결.
2. CHANGE-039/040: 주소 durable retry, 통계 원천/dirty queue 원자성·revision·최초 fleet ID snapshot. 기존 unknown 이력은 검토 없이 추정하지 않음.
3. CHANGE-042/043: 실제 JVM crash 후 offline batch 복구, publisher의 유한 worker/queue·공유 대기 예산.
4. CHANGE-041/044/045/046: 고정 fixture 성능·실제 업무 API/UI, route chunk 분리/오류 복구, CI action runtime 참조, 경보 규칙/배포 rollback 절차.
5. CHANGE-047/048: 과거 Emulator backlog를 새 key에 자동 귀속하는 문제와, GPS의 시간/날짜/timezone 유실을 추가 발견해 수정.

병렬 AI 검토 결과만으로 완료를 선언하지 않고 해당 경계를 실제 HTTP/DB 또는 실패 주입 test로 확인한다. 통계 snapshot/generation/감사와 producer 공유 budget은 별도 읽기 전용 리뷰에서 새로운 재현 가능한 P1/P2가 없었으며, 이는 모든 버그가 없다는 보장이 아니다.

## 통합 과정에서 발견한 실패

- 기존 PasswordController MVC slice가 새 filter의 MeterRegistry 빈을 포함하지 않아 456개 중4개가 실패했다. 테스트 slice에 SimpleMeterRegistry를 추가했고 production filter는 유지했다.
- 실제 browser의 `Accept: text/event-stream`을 가진 타회사 SSE 요청이404 대신500이 되는 사례를 발견했다. ApiErrorResponse가 JSON content type을 명시하도록 고치고 두 endpoint의 Accept/404 단위 회귀와 실제 UI의 foreign SSE404·5xx0 assertion을 추가했다.
- 실제 캡처에서 통계 chart 범례의 고정 margin으로 글자가 세로 줄바꿈되는 것을 발견해 flex 너비/줄바꿈·nowrap을 조정했다.
- 외부 FE/Python source를 사용하는 Gradle opt-in task에 해당 fileTree를 입력으로 등록했다. FE만 수정했을 때 UP-TO-DATE로 과거 결과를 재사용하던 누락을 발견했으며, 발견 당시 최종 UI 검증은 --rerun-tasks로 실행했다.
- browser fixture 첫 실행의 외부 Google Fonts 요청은 차단됐고 test server에서 해당 stylesheet만 제외했다. 업무 API는 mock하지 않았다. 최종 범례 재캡처 중 Ryuk 연결 실패는 애플리케이션 assertion 실패와 구분하고 재실행 결과를 남긴다.

## 검증 결과

최종 BE 전체와 별도 SSE/Python/성능 task가 2분54초에 모두 성공했다. 실패/오류/skipped0. JUnit XML과 원시 측정 JSON을 대조했다.

| 검증 | 결과 |
| --- | --- |
| BE 기본 전체 | 459개 통과 |
| 실제 Boot/nginx/Chromium SSE | 2개 통과 |
| 실제 Python→Boot→MySQL | 2개 통과, 인증/폐기 및 자정5개 observation 시각 보존 |
| 고정 데이터 API/DB 측정 | 1개 통과, 최종 raw는 CHANGE-041 |
| 실제 FE 업무 UI | 1개 통과, 최종 캡처는 CHANGE-045 |
| Python 전체 unit/로컬 HTTP | 32개 통과, 44.189초, 실패/오류0 |
| FE unit | 14개 통과 |
| FE 브라우저 fixture | 26개 통과, hour/day × 3개 timezone 포함 |
| FE production chunk | 3개 통과, 2.3초 |
| FE build | 성공. entry 282.30kB/gzip약96.4kB, 최대chunk406.25kB. 기존약976.82kB 단일entry의500kB경고해소 |
| 실제 JVM crash/restart | `statisticsCrashRecoveryTest` 4개 통과, 19초 |
| Prometheus rules | 8개 규칙 synthetic 발생/해제/미생성counter/413 검증 SUCCESS |

주요 재현 명령:

```sh
./gradlew test sseBrowserTest emulatorClientTest fleetEvidenceTest -Demulator.python=/사용할/venv/bin/python --console=plain
./gradlew fleetBrowserTest --console=plain
./gradlew statisticsCrashRecoveryTest --console=plain
```

Emulator: `python -m unittest discover -s tests -p 'test_*.py'`.
FE: `npm test`, `npm run build`, `npx playwright test`, `npx playwright test --config=playwright.production.config.mjs`.

같은 BE checkout의 Gradle 검증은 동시에 실행하지 않는다. 각 test는 격리된 컨테이너/fixture이고 지도 SDK·외부 실제 Kakao 호출과 운영 인프라가 검증됐다고 대신 말하지 않는다.

## 설명 가능한 핵심 흐름

GPS 5개가 23:59:58부터1초 간격으로 관측되면 client는 전날23시2개/다음날0시3개 packet으로 나눈다. 각 HTTP는 새 nonce와 전송 시각을 붙인다. 서버는 장치와 현재 연결을 확인하고 Redis에서 그 HTTP 시도만 중복/예산 검사한다. broker 수락 뒤 DB unique는 다시 전달된 같은 observation의 저장 효과를 제한한다. 과거 날짜에 이미 통계가 있으면 같은 source transaction이 보정 generation을 늘리고, worker는 최초 fleet ID 목록을 유지하며 변경된 집계만 revision으로 남긴다.

HTTP nonce, GPS observation key, Trip identity, 통계 generation은 서로 다른 중복·시간 경계를 담당한다. publisher confirm은 DB commit이 아니다. queue의 key가 바뀌면 client도 과거 event의 원래 소속을 자동 추측하지 않는다.

## 남은 외부 조건·학습

[남은 작업](../remaining-work.md)의 실제 운영 DB/backup/legacy 원천, 과거 fleet 자료, broker/Redis/Kakao 설정, 경보 수신·AWS immutable image/rollback, 원격 반영과 사용자의 독립 설명이 남는다. 원래 protocol에 없는 영구 sequence, durable live replay, 평가 근거 없는 AI 기능은 완료를 꾸미기 위해 추가하지 않았다.

직접 답할 질문:

1. 같은 관측의 새 HTTP nonce와 broker 재전달을 각각 어디서 막는가?
2. 첫 회사 commit 후 JVM이 죽었을 때 무엇을 보존하고, 살아있는 writer가 있으면 왜 orphan 복구를 실행하면 안 되는가?
3. 과거 통계에서 현재 active 차량을 다시 조회하면 어떤 오류가 생기며, 새 fleet snapshot이 실제 역사 전체를 복원하지는 못하는 이유는?
4. 503을 받은 GPS가 나중에 DB에 남을 수 있는가? 어떤 증거로 수락/저장/화면 수신을 나누는가?
5. AI가 발견·수정·검증한 작업과 본인이 독립적으로 설명한 내용의 차이를 어떻게 밝힐 것인가?

AI는 분석/구현/테스트/문서/상호 리뷰를 수행했다. 사용자의 실제 이해·독립 구현 경험·운영 성과를 검증 없이 대신 주장하지 않는다.

## 최종 보존·확인

- [소스 manifest](../../experiments/2026-09-07-local-source-manifest.json): 세 저장소의 HEAD/branch와 이번 dirty source/configuration 파일 SHA256을 기록했다. 문서/실행 credential/비밀 환경 파일은 소스 manifest에서 제외했다.
- [실제 화면 응답·캡처](../../experiments/2026-09-07-fleet-browser/result.json): 2026-09-07 20:08 KST 최종1/1,56초. 허용 업무 흐름과 타회사 HTTP/SSE404, 예상외5xx0. 범례·오류 문구 수정본도 육안 확인했다.
- [최종 성능 표본](../../experiments/2026-09-07-fleet-evidence.result.json): 20:06 KST, p50/p95/p99=8.581/10.625/12.381ms, 454.87 requests/s, 본측정240오류0, GPS256/중복추가0. 작은 fixture의 기준선이며 개선율/운영SLA 주장이 아니다.
- Gradle build model에서 FE App/Emulator/실제 업무 script와 Python credential/client script가 세 opt-in task의 input에 들어가는지 확인했다. `verifyCrossRepositoryContractInputs` 진단 task1초 성공. 비밀 `.env`는 포함하지 않았다.
- 세 저장소 `git diff --check` 통과. 이번 작업 로그와 주요 tracker의 local Markdown 링크가 모두 존재함을 확인했다.
- 전체 테스트의 임시 MVC slice 실패와 fixture/Ryuk/cache 문제는 각각의 원인·수정·재실행으로 기록했고 최종 성공으로 덮어 지우지 않았다.
