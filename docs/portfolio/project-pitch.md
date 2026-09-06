# Thisway를 어떻게 소개할 것인가

작성: 2026-09-06. 취업 제출 전 실제 본인 설명·재현 여부를 점검한다.

## 프로젝트 이름과 한 문장

**Thisway — 기업용 차량 운영 관리 서비스**

개인 개선 주제는 **차량 데이터 정합성·장애 복구**다. 기업 RFP를 기반으로 진행했다는 배경은 참여자의 경험에 근거하며 원문은 현재 미보유다. 현재 기능으로 정리한 데모 흐름을 당시 RFP 원문이나 기업 납품·운영 실적으로 표현하지 않는다.

> 차량 위치와 운행 정보를 수집하고, 회사별 차량 조회·실시간 위치 확인·운행 통계를 제공하는 차량 관제 서비스입니다. 팀 프로젝트 이후 개인 개선 단계에서 GPS 중복 저장과 메시지 소비 실패, 회사 간 접근 권한을 테스트하고 개선하고 있습니다.

서비스 분류는 **차량 관제/Fleet Management**, 데이터 특성은 **차량 텔레메트리**, 개인 개선 주제는 **백엔드 정합성·복구·권한 경계**다. AI 서비스나 MSA, 자율주행 제어 시스템으로 소개하지 않는다. 현재 형태는 Java·Spring 기반 modular monolith이며 RabbitMQ 연동이 있다고 MSA가 되는 것은 아니다.

## 30초 소개 예시

> 5인 팀으로 개발한 차량 관제 서비스 Thisway입니다. 저는 당시 차량·차종 관리와 회사별 운행 통계, 일일 배치를 담당했습니다. 이후 기존 구현의 실패 경계를 점검하면서 개인 개선을 진행하고 있습니다. 대표적으로 GPS 저장 이후 RabbitMQ ack가 누락돼 재전달되는 상황을 재현했고, 동일 관측값의 DB 중복 저장을 방지했습니다. 일시적 DB 오류와 잘못된 메시지를 구분해 재시도·DLQ로 처리하는 흐름도 실제 RabbitMQ와 MySQL에서 검증했습니다.

이 문장은 **본인이 코드와 테스트를 이해하고 직접 재현한 뒤** 본인 경험 문장으로 사용한다. AI가 만든 코드를 아직 설명할 수 없다면 “AI와 개선을 진행하며 검증 결과를 학습 중”이라고 표현한다.

## 이력서에 쓸 대표 3개 항목

1. **재전달·동시 요청의 중복 저장 제한:** normalized GPS 관측값의 SHA-256 key와 MySQL unique constraint를 적용하고, 4-thread overlap 및 저장 후 ack 누락 재전달 시 동일 관측값 1행을 검증.
2. **소비 실패 격리·복구 경계:** 일시적 DB 오류만 최대 3회 재시도하고, 입력·JSON·무결성 오류는 1회 후 DLQ로 격리. 실제 Spring container·RabbitMQ·MySQL으로 실패 분류 및 재처리 중 중복 효과를 검증.
3. **회사별 데이터 접근 검증:** Vehicle·TripLog·Emulator 등 개선한 API에서 다른 회사 ID 접근을 거부하는 회귀 테스트를 추가. 인증 여부와 자원 소유권 검사를 구분.

지원 직무에 따라 한 항목을 Flyway fresh MySQL migration/validate나 SSE 다중 탭·버퍼·재연결 검증으로 교체할 수 있다. “기술을 많이 썼다”보다 하나의 문제를 끝까지 설명하는 항목을 고른다.

## 주장과 근거의 대응

| 어필할 역량 | 보여 줄 증거 | 함께 말할 한계 |
| --- | --- | --- |
| 동시성·트랜잭션 이해 | [CHANGE-020](work-logs/2026-09-05-gps-idempotent-persistence.md), MySQL integration test | full observation identity이며 legacy NULL key/장치 재할당은 별도 |
| 메시징 실패 설계 | [CHANGE-021](work-logs/2026-09-05-gps-broker-redelivery.md), [CHANGE-022](work-logs/2026-09-05-gps-retry-dlq.md) | exactly-once·무유실·실제 운영 장애 복구를 주장하지 않음 |
| tenant 경계 | [Vehicle](work-logs/2026-09-05-vehicle-tenant-boundary.md), [TripLog](work-logs/2026-09-05-triplog-tenant-boundary.md), [Emulator](work-logs/2026-09-05-emulator-tenant-boundary.md) | 테스트한 API 범위이며 device 인증은 미완료 |
| 운영 작업의 안전성 | [DLQ runbook](../runbooks/gps-dlq-replay.md), [CHANGE-023](work-logs/2026-09-06-gps-replay-tool-and-pitch.md) | 제한적 로컬/터널 CLI, 조직 승인 검증·중앙 감사 시스템은 아님 |
| 기존 코드 개선 | [기준선](baseline-audit.md) → 개별 work log·commit·회귀 테스트 | 테스트 개수 증가 자체를 성능/신뢰성 개선율로 바꾸지 않음 |
| 원래 개인 Batch의 현대화 | [CHANGE-025](work-logs/2026-09-06-company-statistics-checkpoint.md): 회사별 commit/checkpoint·직접 저장 동시성 | 회사 목록 snapshot·통계 공식 완성·JVM kill 복구는 별도 |
| 외부 API 장애 경계 | [CHANGE-026](work-logs/2026-09-06-trip-address-enrichment.md): commit 이후 주소 보정 | 자동 background retry/성능 향상으로 주장하지 않음 |
| 차량 누적값 계약 검토 | [CHANGE-027](work-logs/2026-09-06-cumulative-odometer.md): 누적 m 중복 가산 교정 | 기존 오염 데이터 자동 보정·Trip 거리 분리는 별도 |

## 원래 역할과 이후 개선을 나누기

기존 PR·Git 대조 기록상 원래 개인 역할은 Vehicle/VehicleModel과 Statistics/Batch다. RabbitMQ·GPS bulk insert·SSE·Security 전체를 최초 개인 구현으로 설명하지 않는다. [원래 기여 근거](original-contributions.md)를 함께 제시한다.

- 팀 개발: 기능 분담, 리뷰, 차량·통계 구현 경험.
- 개인 개선: 기존 한계 발견 → 설계 대안 비교 → 변경 diff → 실패 주입 → 결과·한계 기록.
- AI 활용: 분석·초안·테스트 작성 보조. 결과에 대한 판단·이해·검증은 별개이며 본인이 맡아야 한다.

## AI를 어떻게 어필할 것인가

“AI가 만들어 줬다” 또는 “AI를 써서 빠르게 완성했다”만으로 설명하지 않는다. 다음과 같이 실제 작업 방식을 말한다.

> AI를 코드 분석과 구현·테스트 초안에 활용했습니다. 대신 기존 팀 기여와 새 변경을 분리하고, 모든 변경에 실패 조건과 실행 근거를 남기는 규칙을 적용했습니다. 예를 들어 DLQ를 추가했다고 무유실이라고 표현하지 않고, broker policy 누락이나 publish와 ack 사이의 실패를 따로 검증하고 미검증 범위를 기록했습니다.

이 문장도 사용자가 실제로 해당 판단을 검토한 뒤 사용해야 한다. 현재 작업 로그의 “AI가 실행했다”를 “내가 모두 독립 수행했다”로 바꾸지 않는다.

AI 기능이 없다는 이유로 임의 챗봇을 붙이지 않는다. 백엔드 지원용 서사에서는 데이터·권한·복구 문제를 명확히 다루는 편이 이 프로젝트의 현재 구현과 잘 맞는다. 향후 AI는 고정 평가 데이터와 rule baseline을 갖춘 이상 운행 후보 탐지 정도로 따로 검토한다. 채용 합격이나 특정 회사의 선호를 보장하는 주장은 아니다.

## 면접에서 5분 동안 보여 줄 순서

1. 서비스 사용자와 문제: 여러 회사의 차량 상태·위치·운행 기록 관리.
2. 원래 본인 담당: 차량 도메인과 통계/배치. 팀 MQ 흐름과 내 후속 변경을 구분.
3. 대표 장애 하나: DB commit 뒤 ack 누락 → 재전달 → unique key로 동일 관측값 1행.
4. 설계 대안: 선조회만 하는 방식의 race와 DB constraint의 역할, DLQ 무한 loop를 만들지 않은 이유.
5. 실패 테스트 실행 결과와 남은 한계: 원자적 dual-publish/장치 인증/운영 HA·성능은 아직 미완료. producer confirm/return의 보장 범위는 CHANGE-028로 확인한다.

데모는 처음부터 전체 테스트를 오래 실행하기보다 핵심 test method와 준비된 실행 결과를 보여 주고 재현 명령을 제공한다. 테스트 숫자는 제출 시점 결과로 갱신한다.

## 반드시 스스로 답해 볼 질문

- DB commit과 ack를 하나로 묶지 않았을 때 중복과 유실은 어느 순서에서 발생하는가?
- 같은 시각의 속도가 다르면 왜 별도 관측값으로 남기는가?
- FK 오류를 일시 오류와 똑같이 retry하면 어떤 문제가 생기는가?
- 로그인한 사용자가 다른 회사의 vehicleId를 보내면 어느 계층에서 막는가?
- 실제 DB 장애를 유발한 테스트와 예외를 주입한 테스트의 차이는 무엇인가?
- 본인이 맡았던 Batch에는 restart·동시 실행 관점에서 어떤 과제가 남아 있는가?

## 다음 개발 우선순위에 대한 판단

원래 개인 담당인 Statistics/Batch의 재시작·회사별 부분 성공은 CHANGE-024/025로 개선했다.
다음은 통계 공식/보정 정책과 Trip 상태의 완성이다. 실제 부하 수치는 이후 동일 조건 before/after
실험으로 확보한다. 검증되지 않은 “대규모 실서비스”, “15,000대 처리”, “성능 N% 개선”은 사용하지 않는다.
제출/시연 전에는 [체크리스트](submission-checklist.md)를 따르며, 사용자 본인의 재현·설명은 별도로 확인한다.
