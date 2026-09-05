# CHANGE-010: SSE 구독 ID 격리와 재접속 정리

## 메타데이터

- 작업자: Shin Dong Jun + AI assistant
- 기준: codex/portfolio-foundation@efb7145
- 상태: P0-02D-1 구현, 검증 결과 아래 기록. P0-02D 전체는 미완료.

## 문제와 근거

기존 팀 코드 `SseConnection.findKeysByPrefix`는 `vehicle:1`로 `vehicle:10:alice`도 선택했다. company key도 같은 문제다. 또한 같은 key의 새 연결이 map을 대체한 뒤 이전 emitter callback이 `remove(key)`를 실행하면 새 연결까지 지워졌다. 이번 개인 현대화는 이 두 동작을 교정하며 원 팀 구현 전체를 개인 기여로 주장하지 않는다.

## Acceptance criteria

- [x] vehicle/company ID 1 전송 대상에 ID 10, 100은 포함되지 않는다.
- [x] 같은 resource의 서로 다른 사용자 구독은 함께 선택된다.
- [x] 이전 연결 completion/timeout/error가 새 연결을 제거하지 않는다.
- [x] 현재 연결 callback은 자기 연결을 정리한다.
- [x] 재접속으로 대체된 emitter는 complete된다.

## 선택지와 결정

호출자마다 prefix에 콜론을 붙이는 방법은 새 호출자가 빠뜨릴 수 있어, 검색 경계에서 끝 구분자를 강제했다. typed subscription key는 장기적으로 유용하지만 인증 resource binding 작업에서 함께 다룬다. cleanup은 단순 remove 대신 ConcurrentHashMap의 `remove(key, context)`를 사용했다. 이전 연결 callback이 늦게 도착해도 새 context와 일치하지 않아 삭제되지 않는다.

## 실행 흐름

새 emitter 생성 → 자기 context를 캡처한 callback 등록 → map 교체 → 이전 emitter 종료. 이후 이전 callback은 compare-and-remove 실패로 새 연결을 보존한다. 현재 callback은 일치하는 entry만 삭제한다. DB transaction은 관여하지 않는다.

## 검증

- `./gradlew test --tests '*SseConnectionTest' --console=plain`: 6건 통과.
- 테스트는 mock emitter callback을 직접 호출해 종료 순서를 결정적으로 재현한다. 실제 servlet/network disconnect 검증은 아니다.
- `./gradlew test --console=plain`: 전체 224/224 통과, 실패·오류·skip 0, Gradle 18초. XML 집계로 확인했다.
- `git diff --check`: 통과. P0-02D-1 검증 완료.

## 남은 위험과 후속 작업

FE의 `TripDetailViewPage.jsx`, `CompanyCarDetailPage.jsx`가 EventSource query token을 사용함을 확인했다. 이 계약과 tenant ownership은 다음 단위에서 함께 수정한다. 현재 무제한 buffer, flush 시 event name 변경, initial chunk와 live event 순서 경쟁, 같은 사용자 여러 탭 정책, process-local registry의 다중 instance 전달은 해결되지 않았다. 따라서 SSE 보안 완료나 운영 안정성을 주장하지 않는다. 이번 변경은 FE URL/event 계약을 바꾸지 않았다.

## 학습과 면접

1. ConcurrentHashMap을 쓰는데도 왜 재접속 버그가 생기는가?
   - 개별 map 연산의 thread safety와 연결 세대 간 소유권은 다르다. 이전 callback은 새로운 entry를 삭제하면 안 된다.
2. prefix에 구분자가 왜 필요한가?
   - 문자열 prefix와 resource ID 동등성은 다르다. `1:`과 `10:`은 구분된다.
3. callback을 직접 호출하는 테스트의 장단점은?
   - 시간 지연 없이 순서를 재현한다. 실제 servlet timeout과 proxy disconnect는 별도 통합 검증해야 한다.

실습: 이전 callback에서 조건부 remove를 단순 remove로 바꾸면 재접속 테스트가 어떤 순서에서 실패하는지 설명한다. 사용자 본인의 실습 수행은 아직 확인하지 않았다.

## AI 사용과 검증 책임

AI가 코드 감사, 테스트와 문서 초안, 검증 실행을 수행했다. prefix 정규화와 조건부 삭제를 채택한 이유는 변경 범위와 소유권 보장을 명확히 하기 위해서다. 사람의 코드 이해·면접 설명은 자동 테스트로 대체되지 않으며, 브라우저·proxy·다중 instance 검증은 미수행이다.
