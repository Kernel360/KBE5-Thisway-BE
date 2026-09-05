# AI 협업 및 기능 개발 정책

## 목적

AI 사용을 숨기거나 “AI가 대신 만들었다”로 표현하지 않는다. AI를 활용해 탐색 범위를 넓히되, 사람이 문제 정의·권한 범위·설계 선택·코드 이해·검증을 소유했다는 증거를 남긴다.

## 개발 과정에서의 AI 역할

허용하고 권장하는 역할:

- 관련 코드와 실패 경로 탐색 보조
- 설계 대안과 trade-off 초안
- 경계값·동시성·장애 test scenario 제안
- 문서 초안과 질문 생성
- diff review와 누락 가능성 점검

사람이 직접 책임지는 역할:

- 요구사항과 acceptance criteria 확정
- tenant, 개인정보, 운영 권한 결정
- domain invariant와 transaction 경계 선택
- 생성 코드 전체 diff와 실행 흐름 이해
- 실제 test, migration, 성능·장애 실험 실행
- 포트폴리오 성과 문구 승인

금지:

- AI 답변만으로 동작·보안·성능이 검증됐다고 기록
- 이해하지 못한 코드를 병합
- production secret·개인정보·원시 위치 이력을 외부 model prompt에 전달
- 실패한 test나 반대 근거를 문서에서 제외
- 팀원의 기존 구현을 AI와 함께 고쳤다는 이유로 개인 원구현처럼 표현

## 변경별 기록

각 work log의 `AI 활용과 사람의 검증`에 다음을 적는다.

- AI에게 맡긴 범위
- 제안받은 중요한 대안
- 채택/거절한 결정과 이유
- 사람이 직접 추적한 request/event 실행 흐름
- 실제 자동화 검증
- 확인하지 못한 사항과 남은 위험

raw prompt 전체를 무조건 보존할 필요는 없다. 재현과 판단에 필요한 요약을 남기고 secret·개인정보는 저장하지 않는다.

## 포트폴리오 표현

권장:

> AI를 설계 대안·실패 시나리오·테스트 후보를 넓히는 검토자로 사용했습니다. 제안은 명시적인 acceptance criteria와 코드 리뷰를 거쳤고, 해당 변경에서 실제 수행한 자동화 테스트·실험 결과와 실행하지 못한 검증도 함께 기록했습니다.

피할 표현:

- “AI로 개발 속도를 몇 배 높였다.” — 측정하지 않았다면 근거가 없다.
- “AI가 전체 시스템을 구현했다.” — 본인의 역량과 책임을 약화한다.
- “AI가 보안을 검증했다.” — 정적 제안과 실행 검증을 혼동한다.

## 제품 AI 기능의 go/no-go gate

AI 기능은 다음 조건을 모두 만족할 때만 시작한다.

- 사용자가 해결할 문제와 잘못된 출력의 피해가 정의됐다.
- 입력 데이터의 출처, 품질, tenant 격리, 보존 정책이 있다.
- rule/query 같은 non-AI baseline이 있다.
- 고정 evaluation set과 metric이 있다.
- timeout, rate/cost limit, fallback, 관측과 중단 방법이 있다.
- irreversible action은 human approval 없이 실행하지 않는다.

Thisway의 1순위 후보는 “운영자 검토용 이상 운행 후보 랭킹”이다. “고장 예측”은 정비 이력과 고장 label이 준비되기 전까지 사용하지 않는다.

## AI 기능 평가 최소 항목

- 데이터 split과 leakage 방지
- baseline 대비 quality
- Precision@K 또는 task에 맞는 metric
- 차량 100대당 false alert
- tenant 격리 100% negative test
- 결과 근거와 version 정보
- abstention과 fallback
- p95 latency와 요청당 비용
- prompt injection/adversarial input
- drift와 score distribution monitoring
