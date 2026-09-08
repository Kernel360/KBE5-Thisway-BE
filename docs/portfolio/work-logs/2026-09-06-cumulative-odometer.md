# CHANGE-027: 누적 주행거리 중복 가산 방지

- 날짜: 2026-09-06
- 기준: `bcfdd8e`, `codex/statistics-batch-restart`
- 상태: Verified

## 문제와 근거

Emulator `models/emulator_data.py`는 sum을 누적 m로 정의하고
`services/log_generators/power_log_generator.py`는 accumulated distance를 전송한다.
FE `CompanyCarDetailPage`는 mileage/1000을 km로 표시한다.
BE는 `Vehicle.updateMileage(sum)`에서 매 OFF마다 sum을 더했다.
예: 초기 1000m, OFF 1500m, 같은 OFF 재전송 → 기존 4000m, 기대 1500m.
원래 팀 코드에 대한 개인 현대화이며 원 기여는 별도 Git/PR 목록을 따른다.

## Acceptance criteria

- [x] 관측 누적값은 추가 거리가 아님을 domain test로 고정
- [x] 같은 값/작은 지연 값에 증가·감소 없음, null/음수 거부
- [x] 실제 OFF 4-thread에서 최댓값 보존
- [x] 전체 회귀/기록 완료

## 대안과 선택

- 단순 대입: 지연/역순 데이터가 거리를 감소시킬 수 있어 거절.
- 매 이벤트 차이 가산: 이벤트 순서·기준점 상태가 별도로 필요.
- max(기존, 누적값): 현행 차량 계기값의 단조성을 유지하는 작은 교정으로 채택.

Emulator MDN → VehicleReference projection → active Vehicle write lock →
power/vehicle/trip 저장 → commit → 주소 보정 흐름이다.
EAGER Emulator.vehicle을 먼저 읽으면 잠금 전의 오래된 managed entity를 사용할 수 있으므로
ID projection으로 교체했다. power use case는 READ_COMMITTED로 잠금 대기 후 후속 조회가
앞 요청의 commit을 볼 수 있게 한다. row lock을 빼고 max만 적용하면 동시성 보장이 아니다.

## 검증

- `./gradlew test --tests '*VehicleOdometerTest' --tests '*LogServiceTest' --console=plain`: 성공, 2초.
- `./gradlew test --console=plain`: 실제 MySQL 포함 308개 통과, 실패/오류/skipped 0, 1분 20초.
- Emulator/FE 계약은 소스 확인이다. 실제 장치 프로토콜/RFP 원문 보유 증거는 아니다.

## 실패와 제한

기존 누적 가산으로 이미 부풀린 mileage를 자동으로 낮추지 않는다. 원천 power log의 신뢰성,
차량/장치 교체 이력과 정정 승인이 필요하다. 계기판 초기화·rollover 정책도 미구현.
작은 값은 차량 계기값에만 무효이며 원본 power log는 여전히 저장한다.
Trip.totalTripMeter는 ON 때 기준 계기값, OFF 때 누적값을 담는 기존 혼합 의미가 남아 있다.
Trip 거리 필드 분리와 ON/OFF 중복·역순 상태 머신 완료라고 표현하지 않는다.
장치 인증도 아직 없어 높은 위조 값 자체를 막는 변경은 아니다.

## 공부와 예상 면접 질문

- 개념: cumulative vs delta, idempotent projection, lost update, pessimistic locking,
  first-level cache, repeatable-read snapshot vs read-committed.
1. max만 쓰면 동시성에 안전한가요?
   - 아니다. 동일 차량 lock으로 최신 상태를 읽고 commit까지 직렬화한다.
2. 장치 값이 작아지면 무조건 오류인가요?
   - 지연일 수도 초기화일 수도 있다. 현재 표시값은 유지하되 원천을 보존하고 별도 보정 정책이 필요하다.
3. 기존 데이터도 고쳐졌나요?
   - 아니다. 새 계산 경계만 교정했다. 이미 오염된 자료는 근거와 승인 후 보정해야 한다.

## AI 활용

AI가 세 저장소의 sum/mileage 단위 경로를 확인하고 코드·회귀·문서 초안을 작성했다.
자동 legacy mileage 재계산은 근거 부족으로 채택하지 않았다. 사용자는 1000→1500→1500→1200
fixture와 동시성에서 왜 lock이 필요한지 직접 설명해야 한다. 운영 효과/실장치 계약은 미검증이다.
