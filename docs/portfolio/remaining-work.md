# 남은 작업과 포트폴리오 마무리 기준

기준일: 2026-09-06. 전체 로드맵의 완료율을 임의 %로 계산하지 않는다. 개별 작업의 구현·검증·원격 반영·운영 적용을 구분한다.

## 현재 기반

- 회사별 접근 경계, 민감 로그 축소, SSE lifecycle/다중 탭/실제 브라우저 회귀, Flyway fresh MySQL 기반을 개선했다. 보장 범위는 각 work log 기준이다.
- 신규 동일 GPS observation의 DB 중복 효과 제한, broker 재전달, retry/DLQ, 제한적 replay CLI를 검증했다. 운영 무유실·장치 인증·전체 replay 통제는 완료가 아니다.
- FE 보안 정리 `ec1c819`: audit 취약 패키지 16→0, 단위 8/browser 9/실제 BE SSE 2 통과. 현재 로컬 작업 브랜치이며 원격 push/병합은 아직 아니다.
- Statistics 첫 단계는 [CHANGE-024](work-logs/2026-09-06-statistics-job-identity.md): targetDate 식별·실패 상태·전체 rollback/restart·진행 중 동일 날짜 중복 실행 거부다. 회사별 checkpoint의 완성으로 읽지 않는다.
- 이후 [CHANGE-025](work-logs/2026-09-06-company-statistics-checkpoint.md)로 회사별 commit/checkpoint·성공 회사 생략·일자 unique를 검증했다. CHANGE-024의 전체 rollback은 과거 단계다.
- CHANGE-026 주소 AFTER_COMMIT 보정, CHANGE-027 차량 누적 거리 가산 교정, CHANGE-028 publisher confirm/return 및 부분 실패 계수를 추가했다. 전체 313/313, BE/nginx/Chromium 2/2, FE unit 8/browser 9/build 통과. 각 실패 조건/범위는 work log 기준이다.

## 다음 우선순위

| 우선순위 | 작업 | 완료 증거 |
| --- | --- | --- |
| 완료 | 회사별 통계 transaction/checkpoint + DB company/date unique | CHANGE-025: 성공 회사 생략, 직접 저장 4-thread, legacy duplicate 보존, 전체 300/300 |
| 2 | 통계 의미와 correction/backfill | 가동률 의미 사용자 확인 대기. gpsCycle·GPS 누락·late event·운행 경계 fixture, 완료 날짜 보정 정책 |
| 3 | Trip 상태와 주소 자동 복구 | ON/OFF 중복·역순·누락 상태표, start/end odometer·distance 분리. 주소 core 분리/timeout은 CHANGE-026 완료, 자동 worker는 미완료 |
| 4 | 수집 인증·발행 운영 안정성 | 장치 권한/credential 발급·회전/재전송/size·rate 정책 미완료. producer confirm/return·부분 실패는 CHANGE-028 완료, 전체 deadline/부하·자동 live 복구는 별도 |
| 5 | 성능·운영 증거 | 고정 데이터의 p95/처리량/오류·중복·lag, DB query 측정, 경고·배포 rollback 절차 |
| 6 | 사용자 업무 흐름과 제출 자료 | 차량 현황→운행 기록→회사 통계 데모, 실행 안내, 개인 기여·제약·면접 답변 |

### 결정이 필요한 통계 정의

현재 GPS 개수/(3600×차량 수)는 실제 가동 시간과 동일하지 않다. 사용자에게
완료 운행 시간 기준 가동률로 변경하고 GPS 수신 상태를 별도 지표로 분리할지 확인을 요청했다.
원 RFP 원문이 없으므로 이를 원래 요구사항이라고 추정해 구현하지 않는다. 답변 전 공식은 유지한다.
완료 운행만 사용하면 아직 종료 이벤트가 없는 운행은 제외되며 늦은 OFF에 재집계가 필요하다.
이 변경을 택하더라도 GPS 수신율 분모의 gpsCycle 이력/기대 관측 수 정책은 별도 정의해야 한다.

운영에 실제 노출하기 전에는 4번의 인증/발행 위험과 배포 보안이 반드시 다시 gate가 된다. 이 표는 로컬 포트폴리오 개발 순서이지 미완성 수집 API를 공개하라는 뜻이 아니다.

## 별도 정리 항목

- FE `codex/frontend-dependency-security`, BE 통계 작업 브랜치의 커밋·PR 검증·병합은 사용자 승인 후 진행한다.
- FE main bundle 약 975kB의 경고는 남아 있다. 보안 업데이트가 성능 개선이라는 표현은 사용하지 않는다.
- BE GitHub Actions의 setup-java/Node 런타임 경고를 별도 변경으로 정리한다.
- 운영 DB 전환, broker DLQ policy 적용, 배포/기존 인프라 생존 여부는 로컬 테스트와 별개다.
- AI 기능은 선택 과제다. 원천 데이터·고정 평가셋·rule baseline 없이는 예지정비/최적배차 성과를 주장하지 않는다.
- 차량 mileage는 신규 누적값 관측 시 max로 갱신하지만 이미 부풀린 legacy 값은 자동 정정하지 않는다. 근거 데이터와 정정 승인 필요.
- [제출/시연 체크리스트](submission-checklist.md)는 작성 완료. 실제 전체 업무 시연/사용자의 독립 설명 검증은 미완료.
- 사용자 요청의 '모든 작업' 전체가 완료된 것은 아니다. 위 표의 남은 작업을 그대로 추적한다.

## 포트폴리오 마무리 판단

제품 설명은 **기업용 차량 운영 관리 서비스**다. 기업 RFP를 바탕으로 진행했다는 참여자의 경험을 설명할 수 있지만, 원문 미보유 상태에서 원래 요구사항 목록·납품·실제 운영 성과를 새로 만들어 쓰지 않는다. 데모는 현재 구현 기반의 대표 업무 시나리오로 명시한다.

제출 가능한 소개 자료는 지금도 정리할 수 있다. 다만 운영 완성을 주장하려면 위 위험들을 더 해결해야 한다. 원래 개인 담당인 Vehicle/Statistics/Batch에서 대표 개선 사례를 본인이 재현·설명할 수 있는지가 기술 수나 테스트 개수보다 중요한 마무리 기준이다.
