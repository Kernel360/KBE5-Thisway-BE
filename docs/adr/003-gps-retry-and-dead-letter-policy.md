# ADR-003: GPS 저장 retry와 policy 기반 dead lettering

- 상태: 로컬 검증용 채택, 운영 적용은 runbook gate 필요
- 기록: 2026-09-05~06
- 범위: GPS 저장 consumer만. SSE와 producer dual publish는 제외.

## 배경

원래 공통 listener는 오류 종류와 관계없이 3회 시도한 뒤 reject했다. 기존 source queue를 유지하면서 poison message를 격리하고 일시적 DB 오류만 재시도할 필요가 있다.

## 결정

1. 저장 전용 factory에서 DB 일시 오류 allowlist와 cause-chain 분류를 적용한다. 최대 3회, 200ms/400ms backoff, prefetch=1이다. 미분류 오류는 1회 후 격리한다.
2. source queue의 immutable arguments를 바꾸지 않는다. broker policy로 DLX routing을 연결하고 앱은 durable DLX/DLQ/binding만 선언한다.
3. 자동 DLQ→source loop는 만들지 않는다. 오류 수정 후 승인된 제한적 replay가 필요하다. publish confirm과 mandatory return 검증 후 원본 DLQ를 ack하는 protocol을 테스트한다.
4. DB 저장 멱등성은 ADR-002를 따른다. replay publish와 DLQ ack 사이 장애의 중복 전달 가능성을 유지하되 DB 효과를 제한한다.

## 대안과 결과

- queue arguments 변경은 fresh 환경에서 편하지만 기존 큐 재선언 충돌을 유발할 수 있어 선택하지 않았다.
- 모든 예외 retry는 poison message에 자원을 낭비한다. allowlist에 없는 일시 오류도 격리될 수 있으므로 분류 개선은 실제 증거에 따라 진행한다.
- policy 적용·권한·routing 확인이 별도 배포 의무가 된다. 앱만 배포하면 DLQ 보관이 보장되지 않는다.
- classic dead lettering을 무유실로 표현하지 않는다. quorum/HA 및 producer confirm/return은 별도 결정이다.
- 운영 replay 도구, 접근 감사, 보관 기간, 경고 자동화는 후속이며 이번에는 테스트 harness와 운영 조건을 확정했다.

검증·학습 기록: [CHANGE-022](../portfolio/work-logs/2026-09-05-gps-retry-dlq.md). 적용 순서·중단 조건: [runbook](../runbooks/gps-dlq-replay.md).

## 후속 결정: 제한적 도구 (2026-09-06)

CHANGE-023에서 별도 Java replay source set을 추가했다. 웹 관리 API·대량 자동 loop 대신 로컬/승인된 터널용 한 건 CLI를 선택했다. 승인 ID는 조직 승인 기록 참조이고 hash·marker·입력 확인, 신규 0600 audit와 confirm 전후 기록을 수행한다. 중앙 승인·감사·전역 rate limit을 구현했다고 표현하지 않는다. 웹 jar에 운영 main을 섞지 않으며 구체적 실패 경계는 [CHANGE-023](../portfolio/work-logs/2026-09-06-gps-replay-tool-and-pitch.md)에 기록한다.
