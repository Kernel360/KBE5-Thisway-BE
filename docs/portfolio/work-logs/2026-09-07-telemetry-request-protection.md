# CHANGE-038 — 수집 요청 freshness·중복 접수·크기·빈도 경계

상태: 구현·전용 검증·최종 통합 회귀 완료. 2026-09-07.

## 문제와 출처

원 팀의 Power/GPS/Geofence 수집 기능에 CHANGE-037로 장치 인증을 연결했다. 인증 키만으로는 동일 HTTP 시도의 재사용, 과다 요청, Jackson 역직렬화 전 큰 본문을 제한하지 못했다. 이 변경은 이후 개인 현대화이며 원 팀 전체 구현을 개인 기여로 주장하지 않는다.

## 계약과 선택

- 세 POST는 장치 헤더 외에 `X-Request-Id`(소문자 UUID v4)와 `X-Request-Timestamp`(epoch seconds)를 요구한다. 전송 시각 ±300초, nonce TTL 601초. 미래 300초에 최초 수락된 요청도 마지막 허용 시점까지 nonce가 유지된다.
- 동일 장치의 nonce 확인·기록과 첫 접수부터 60초간 기본 120회 제한을 Redis Lua 한 번으로 수행한다. Redis Cluster hash slot도 장치별로 일치한다. 한도 초과 요청은 nonce를 소비하지 않으며 429 + `Retry-After: 60`을 반환한다.
- 같은 nonce는 endpoint와 key rotation을 넘어서 409. Redis 오류는 503으로 접수를 닫는다. 잘못된 key는 이 Redis 상태를 만들기 전에 거부한다.
- POST 실제 본문 256 KiB 상한을 Jackson 앞에서 검사한다. Content-Length가 없거나 chunked여도 MAX+1 bytes만 읽고 413을 반환한다. decoded servlet path도 검사한다.
- Power의 기존 KST+5분 정책에 GPS/Geofence를 맞춘다. GPS는 packet 기준시각뿐 아니라 min/sec를 반영한 실제 observation 시각도 검사한다. 지연 도착한 과거 이벤트는 허용한다. Geofence는 strict 날짜·숫자·좌표·이벤트값을 검사한다.
- Python과 FE Emulator는 전송 시도마다 새 UUID와 현재 전송 시각을 만든다. 원본 이벤트 시각은 변경하지 않는다. 브라우저 CORS에 네 헤더를 허용하고 malformed JSON 응답/로그에서 Jackson 원문을 제외한다.

메모리 Map보다 공유 Redis를 선택해 다중 인스턴스에서 접수 정책을 공유한다. 분산 상태 의존과 장애 시 수집 불가 비용을 지불한다. fixed window는 단순하고 검사 한 번으로 원자적이지만 경계 근처 burst를 평탄화하지는 못한다. 요청 nonce는 observation의 영구 식별자가 아니므로 DB unique/idempotency는 유지한다.

## 실행 흐름과 acceptance

본문 byte 제한 → strict payload 검증 → device identity 인증 → freshness/Redis atomic admission → 회사/차량 binding 재검증 → 직접 저장 또는 broker 접수. 재전송 nonce가 새로워도 동일 GPS observation은 기존 DB 제약으로 추가 저장되지 않아야 한다.

- [x] 8개 동시 Redis 클라이언트에서 동일 시도 1개만 허용, 나머지 409.
- [x] 장치별 공유 한도/격리, 유한 TTL, 거부 nonce 재시도, strict timestamp 경계.
- [x] Redis 오류에서 실패를 노출하되 명령/키 원문을 노출하지 않음.
- [x] 실제 MySQL HTTP에서 동일 nonce endpoint 간 재사용 거부, 새 nonce GPS 재시도 DB 1건 유지, missing freshness/429에서 저장 없음.
- [x] 실제 byte 상한, chunked, 정확한 경계, UTF-8 보존, decoded path 검사.
- [x] Geofence/GPS 날짜·정규화 관측 시각 경계.
- [x] 최종 BE 전체459/SSE2/실제Python client2, Python전체32, FEunit14/browser26/production3/build 통과. [통합 기록](2026-09-07-local-completion-review.md).

## 검증

`./gradlew test --tests '*TelemetryRequestGuardTest' --tests '*TelemetryBodyLimitFilterTest' --tests '*TelemetryTimeValidationTest' --tests '*DeviceCredentialIntegrationTest' --tests '*TripLogServiceTest' --tests '*GpsLogValidationTest' --console=plain`

MySQL 8.0.40 + Redis 7.4.2 + RabbitMQ 3.13.7: 68 tests, 실패/오류/skipped 0, Gradle 25초. 이후 CORS/JSON 안전 응답 테스트를 추가했으므로 최종 회귀에서 별도 확인한다. 처리량 개선을 측정한 변경이 아니다.

## 한계와 운영 전환

키를 탈취한 공격자는 새 nonce를 만들 수 있다. 이는 body signature/하드웨어 장치 attestation이 아니다. Redis 재시작·eviction·데이터 유실은 TTL 내 replay 기억도 없애므로 운영 Redis persistence/HA/eviction과 time sync를 별도로 결정해야 한다. 한도는 성공·실패와 무관한 접수 예산이며 Power/GPS/Geofence가 공유한다. 강한 IP/global connection/slow-body 방어는 ingress 계층의 별도 정책이다. 기존 client는 새 헤더 없이 400이므로 BE/두 client 전환을 함께 해야 한다. 거부된 요청을 무한 자동 재전송하지 않는다.

## 학습과 면접

1. 왜 timestamp와 nonce가 둘 다 필요한가? timestamp는 허용 시간창, nonce는 그 시간창 안의 중복 시도를 구분한다.
2. 왜 SETNX 후 INCR 두 호출이 아닌 Lua인가? 동시 요청의 check-then-act와 예산 변경을 하나의 원자적 연산으로 묶는다.
3. 왜 nonce가 있어도 DB unique가 필요한가? 통신 실패 후 새 nonce로 보낸 동일 observation과 broker 재전달은 HTTP 시도 중복과 다르다.
4. 503이면 저장이 0건인가? admission Redis 실패는 쓰기 전이지만 publisher timeout은 broker 수락 여부가 불확실할 수 있다. 오류 출처별 보장이 다르다.

AI가 경계/코드/테스트 초안을 작성했고 원자성, TTL의 최대 수명, 요청 시각과 이벤트 시각의 분리, 실제 DB 중복 결과로 검증했다. 사용자는 위 흐름과 비용을 직접 설명하는 검토가 남는다.


### 통합 중 발견·수정

브라우저와 실제 연결해 검사하자 수집용 header의 기존 CORS allowlist 누락과, 본문 한도 filter가 CORS보다 앞에서 413을 반환하는 경계를 발견했다. header를 허용하고 filter를 security/CORS 뒤·Jackson 앞에 배치했다. 실제 Origin의 초과 POST 413+ACAO, 허용하지 않은 Origin403을 검사한다. metrics 의존성 추가 후 기존 PasswordController MVC slice 4개가 MeterRegistry 빈 누락으로 실패해 test slice에 SimpleMeterRegistry를 추가했다. production 경계를 완화하지 않았다. JSON body 파싱 실패는 원문을 로그/응답에 노출하지 않고 고정400으로 반환한다.
