# CHANGE-026: 운행 commit 이후 주소 보정

- 날짜: 2026-09-06
- 기준: `codex/statistics-batch-restart@bb04619`
- 상태: Verified

## 문제와 기여 경계

기존 팀의 TripLog 저장은 Kakao reverse geocoding을 core transaction 안에서 호출했다.
외부 장애가 power/vehicle/trip 저장을 rollback하고 기본 RestTemplate에 timeout도 없었다.
이번 변경은 개인 현대화이며 원 팀의 Trip 전체를 개인 기여로 주장하지 않는다.

## Acceptance criteria

- [x] core commit 후 주소 조회, 외부 I/O 중 transaction 없음
- [x] 외부 실패 시 좌표/운행 보존, 명시적 재시도로 주소 채움
- [x] core rollback이면 외부 조회 없음
- [x] 조회 도중 바뀐 좌표에 오래된 주소 쓰기 방지
- [x] 실제 무응답 로컬 HTTP 서버에서 read timeout 발생
- [x] MySQL 기반 최종 전체 회귀

## 선택과 흐름

- timeout만 추가: 지연 상한은 줄지만 core rollback 결합이 남아 거절.
- durable outbox/비동기 worker: 장애 복구/자동 재시도에 좋으나 별도 운영·retry lifecycle 필요.
- AFTER_COMMIT + nullable address: 작은 분리 단계로 채택. 외부 HTTP는 NOT_SUPPORTED,
  결과 update만 REQUIRES_NEW이며 core commit은 이미 확정됐다.

Trip 저장(주소 null, 좌표 보존) → ID/출도착 side 이벤트 → core commit →
트랜잭션 밖에서 좌표 조회/HTTP → 주소가 여전히 null이고 좌표가 같을 때만 새 transaction update.
실패는 payload/좌표/HTTP 예외 본문 없이 ID/side/exception type만 기록하고 false를 반환한다.
기존 상세 응답은 null 주소를 빈 문자열로 변환하고 FE `TripDetailViewPage`는 `-`로 표시한다.
이는 소스 확인이며 이번 단계에서 FE 브라우저 시연을 추가 실행한 것은 아니다.

## 검증 결과

- 좁은 테스트: Trip service 10 + enrichment 3, 13개 통과, 5초(H2).
- 1차 전체: 305개 통과, 실패/skipped 0, 1분 12초(H2 enrichment 포함).
- 최종: enrichment 3개를 실제 MySQL 8.0.40/Flyway validate로 강화. `./gradlew test --console=plain`, 305개 통과, 실패/오류/skipped 0, 1분 21초.
- timeout: connect 1000ms/read 1500ms. 로컬 ServerSocket 무응답에 SocketTimeoutException 검증.
  실제 Kakao 장애·DNS 지연·전체 요청 deadline/성능 수치는 측정하지 않았다.
- 환경 진단용 `ps`는 sandbox에서 거부됐으나 테스트 결과 파일/완료 결과로 검증했다.

## 남은 위험과 재처리

이벤트 자체는 durable queue가 아니다. commit 직후 JVM이 죽으면 자동 보정되지 않는다.
좌표와 null 주소는 DB에 남으므로 [주소 보정 절차](../../runbooks/trip-address-enrichment.md)를 따른다.
동기 after-commit 호출이므로 HTTP 응답 대기 시간은 남는다. background 성능 개선으로 주장하지 않는다.
자동 스케줄러, 최대 시도/백오프, distributed claim, 주소 상태/오류코드 노출은 후속이다.
Trip 중복·역순·누적 거리 오류는 이 변경으로 해결되지 않는다.

## 학습과 예상 면접 질문

- 공부: transaction synchronization, AFTER_COMMIT, NOT_SUPPORTED vs REQUIRES_NEW, conditional update, eventual consistency.
1. AFTER_COMMIT이면 자동 비동기인가요?
   - 아니다. 현재 동일 요청 thread에서 실행하며 HTTP 응답 지연은 남는다.
2. 왜 listener에서 바로 JPA save하지 않나요?
   - 완료된 transaction resource가 남아 있을 수 있다. 외부 I/O는 suspend하고 쓰기는 명시적 새 transaction.
3. 서버가 죽으면 이벤트는 유실되나요?
   - 이벤트는 유실 가능. 저장된 좌표/null 주소로 재처리하며 자동 복구 큐는 아직 없다.
4. 늦은 주소 응답이 새 좌표를 덮어쓰지 않나요?
   - ID/side뿐 아니라 조회 당시 좌표 및 null 조건으로 update를 제한한다.

## AI 활용

AI가 분리 코드/회귀/문서 초안과 실행을 수행했다. 과도한 비동기 인프라 추가는 보류하고
작은 core 경계 분리를 택했다. 사용자는 timeout 실패 fixture와 commit 후 callback 흐름을
직접 설명해야 한다. 운영 API 상태, 처리량, 사용자 이해는 AI가 검증하지 않았다.
