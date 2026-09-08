# ADR-006: 주소 보정은 핵심 운행 저장 이후

2026-09-06, 채택. 주소는 파생 정보이며 좌표/운행 원본의 commit 성공 조건이 아니다.

`TripLogServiceImpl`은 좌표와 null 주소를 저장한 후 ID와 출도착 구분 이벤트를 발행한다.
AFTER_COMMIT listener는 NOT_SUPPORTED로 기존 transaction resource를 suspend하고
외부 API를 호출한다. 주소 쓰기만 REQUIRES_NEW transaction으로 실행한다.
실패는 원본 commit을 되돌리지 않으며 주소는 비어 있다. core rollback에는 listener가 실행되지 않는다.

timeout만 추가하는 대안은 core 장애 결합을 해소하지 못한다. durable outbox/worker는
자동 복구에 더 적합하지만 이번 단계에서는 명시적 재시도 가능한 작은 경계 분리를 택했다.
동기 listener이므로 응답 지연은 남고, JVM crash에는 이벤트 전달이 보장되지 않는다.
좌표와 null 주소는 영속 상태이므로 후속 보정 대상으로 찾을 수 있다.

외부 응답이 오래 걸리는 사이 좌표가 바뀌거나 주소가 이미 채워졌다면 조건부 update는 0행이다.
이미 채워진 주소의 강제 수정/좌표 revision 기반 재보정은 별도 권한·정책으로 남긴다.
latency 향상이나 운영 자동 복구를 완료했다고 주장하지 않는다.

검증/학습/면접 및 제한은 [CHANGE-026](../portfolio/work-logs/2026-09-06-trip-address-enrichment.md),
내부 재시도는 [runbook](../runbooks/trip-address-enrichment.md)에 기록한다.
