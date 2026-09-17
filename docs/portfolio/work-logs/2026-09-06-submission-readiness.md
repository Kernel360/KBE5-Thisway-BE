# CHANGE-029: 제출 체크리스트와 교차 저장소 회귀 기록

- 날짜: 2026-09-06
- 기준: `22ff15d`, `codex/statistics-batch-restart`
- 상태: Verified (문서/명시된 회귀만. 전체 업무 시연 또는 전체 로드맵 완료 아님)

## 문제와 기준

연속 개선 후 README의 V3 설명, 향후 Batch 개선 문구, 남은 publisher 과제가 현재 상태와
어긋났다. 단계별 증거를 반영하되 테스트 완료와 사용자 학습/전체 업무 시연을 구분해야 한다.
원 팀의 기업 RFP 기반 배경은 참여자의 설명이고 원문 요구사항/납품 실적을 복원하지 않는다.

- [x] README V4/preflight, pitch 근거, remaining-work 갱신
- [x] submission-checklist의 독립 재현·학습 질문·업무 시연 gate 작성
- [x] 최신 BE 전체/BE-FE SSE/FE browser·unit·build 확인
- [x] 원격 미반영 및 전체 미완료/사용자 선택 대기 명시

## 결정과 흐름

PDF/PPT를 먼저 만드는 대신 검증된 소스 기반 Markdown 제출 체크리스트를 선택했다.
복사 가능한 재현 명령 → 대표 회사별 실패/재시작 사례 → 본인 설명 질문 → 실제 화면 시연
gate 순서다. 사용자 지식이나 측정하지 않은 운영 성과를 완료로 표기하지 않는다.

## 실제 검증

| 범위 | 명령 | 결과 |
| --- | --- | --- |
| BE 전체 | `./gradlew test --rerun-tasks --console=plain` | 313 통과, 실패/오류/skipped 0, 1분 26초 |
| 실제 Boot/nginx/Chromium | `./gradlew sseBrowserTest --rerun-tasks --console=plain` | 2 통과, 22초 |
| FE unit | `npm test` | 8 통과 |
| FE browser | `npx playwright test` | 9 통과, 2.5초 |
| FE build | `npm run build` | 성공, 3.89초. main bundle 974.81kB/gzip 298.29kB 경고 유지 |

BE 전체 첫 실행은 테스트 XML 313개 성공 뒤 병렬 Gradle artifact cache lock timeout으로
명령 실패했다. 단독 재실행 성공으로 복구했으며 CHANGE-028에 실패를 보존했다.
FE 소스는 변경하지 않았고 현재 `ec1c819` 로컬 브랜치를 대상으로 검사했다.
Emulator는 sum 계약/수동 test script의 상태 변경을 소스로 확인했지만 실행하지 않았다.
실제 운영 계정/위치 데이터, 원격 push/PR/merge/배포/운영 DB 변경은 수행하지 않았다.

## 남은 위험과 학습

가동률 공식 변경은 사용자 선택 대기다. Trip 상태 머신 전체, 장치 인증, 고정 부하 실험,
운영 전환 및 실제 업무 시연은 여전히 남아 있다. [남은 작업](../remaining-work.md)을 따른다.

1. 왜 테스트 개수로 개선율을 계산하지 않나요?
   - 시나리오 범위와 위험의 무게가 달라 개수는 기능 완성도나 성능 비율이 아니다.
2. 브라우저 테스트 성공이 전체 시연 완료인가요?
   - 아니다. SSE/라우팅 fixture 범위와 회사별 실제 업무 흐름을 구분한다.
3. AI를 어떻게 활용했다고 설명하나요?
   - 분석·초안·테스트 실행을 맡기고 원 기여와 신규 개선, 실패/한계를 분리했다.
     본인 독립 재현/설명 여부는 직접 확인해야 한다.

AI가 문서 초안과 교차 회귀 실행을 수행했다. 실제 업무 시연을 했다는 주장이나 전체 개선율은
채택하지 않았다. 사용자의 직접 설명 검증은 제출 전 남아 있다.
