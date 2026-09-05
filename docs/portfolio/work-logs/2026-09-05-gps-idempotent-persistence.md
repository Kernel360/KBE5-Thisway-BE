# CHANGE-020: GPS 관측값 중복·동시 저장 방지

- 날짜: 2026-09-05, Shin Dong Jun + AI assistant
- 기준: `codex/portfolio-foundation@c07275c`
- 범위: 신규 writer의 normalized GPS 관측값 DB 중복 방지. 기존 행 정리·device identity·broker ack 장애 검증은 미수행.

## 문제·출처

기존 팀 LogRepository는 전달받은 모든 GPS를 bulk insert했다. HTTP 재전송과 Rabbit 재전달 모두 같은 데이터가 누적될 수 있었다. 이번 개인 기여는 fingerprint, V3 unique constraint, no-op duplicate insert 및 MySQL 실행 증거다. 기존 telemetry 기능 전체와 구분한다.

## Acceptance criteria

- [x] 같은 packet 안의 같은 관측값 및 재전송은 신규 row 한 개.
- [x] 같은 시각이라도 다른 측정값은 별도로 저장.
- [x] 순서가 반대인 겹치는 batch 4개가 동시 저장되어도 각 관측값은 한 개.
- [x] 잘못된 FK가 포함된 batch는 실패하며 부분 row가 남지 않고 정상 재시도 가능.
- [x] V2 기존 중복 row는 V3 적용 후 NULL key로 보존.
- [x] 정규화·identity 필드 변경 테스트와 전체 회귀 통과.

## 선택과 실행 흐름

[ADR-002](../../adr/002-gps-observation-identity.md)에 alternatives와 identity 경계를 기록했다. input validation → MDN의 vehicle 조회 → 시간/측정값 변환 → 관측값별 v1 hash → key 순서 정렬 → unique index가 있는 MySQL bulk insert → duplicate는 기존 값 유지 순서다. 선조회 후 insert로 중복 여부를 결정하지 않는다.

unique index는 프로세스 간 동시 insert의 최종 중복 방지 수단이다. 기존 repository transaction을 유지하고 INSERT IGNORE 대신 no-op ON DUPLICATE KEY를 사용해 FK 오류를 감추지 않는다. key와 GPS row가 같은 insert에 저장되므로 별도 receipt와 business row 사이 불일치가 없다.

## 검증과 증거

- `./gradlew test --tests '*MySqlMigrationIntegrationTest' --console=plain`: 9/9 통과, Gradle 14초.
- 실제 MySQL 8.0.40에서 동일 packet 내부 중복·반복 호출, 4-thread 역순 overlap, FK 실패/rollback/재시도를 확인했다.
- V3 기존 row 보존 및 fingerprint unit test 추가 후 `./gradlew test --console=plain`: 277/277 통과, failures/errors/skipped 0, Gradle 42초. XML 집계와 `git diff --check` 통과.
- 적용 전 일반 insert는 중복을 모두 기록했다. 적용 후 새 key 경로의 동일 관측값 row 수가 1임을 SQL count로 확인한다. 시간/처리량 개선은 측정하지 않았다.

## 배포와 한계

DB V3를 먼저 적용하고 새 writer를 배포한다. V1/V2 파일은 수정하지 않았다. 기존 key=NULL 행은 보호 범위 밖이며 자동 backfill·삭제하지 않는다. V3는 기존 MySQL 데이터 복원본/DDL 비용을 검증한 운영 배포 계획이 아니다.

기존 device ID/sequence가 없으므로 동일 초에 모든 값이 같은 별개 관측을 구분하지 못한다. 같은 timestamp의 변경 payload는 둘 다 보존하고 conflict 알림은 없다. MDN 차량 재매핑·지연 도착, base hour 복원, SSE 중복 broadcast, broker confirm/ack 장애, poison message/DLQ는 별도다.

## 학습·면접

1. 왜 DB unique가 필요한가? 애플리케이션 선조회는 동시 요청 둘이 모두 없는 상태를 볼 수 있다. unique constraint가 충돌을 원자적으로 해결한다.
2. 왜 timestamp만 key로 쓰지 않았는가? 프로토콜 sequence가 없어 같은 시각의 다른 측정값을 잃을 수 있다. 현재는 전체 관측값의 정확 중복만 제거한다.
3. INSERT IGNORE와 차이는? duplicate 외 오류까지 묵살하지 않기 위해 no-op upsert를 택했다. FK 실패는 batch 전체 실패로 확인했다.
4. 왜 정렬하는가? 겹치는 batch가 같은 순서로 key 잠금을 획득하도록 돕는다. 모든 deadlock이나 장애를 해결한 것은 아니다.
5. 기존 데이터도 중복이 제거되는가? 아니다. NULL key를 보존했고 backfill은 identity·데이터 검토 후 별도로 해야 한다.

실습: 같은 관측값의 속도만 바꾸면 왜 두 row가 남는지, 이것이 device event ID 기반 멱등성과 어떻게 다른지 설명한다. 사용자 수행은 미확인이다.

## AI 활용

AI가 설계 대안, fingerprint·migration·SQL·동시성 테스트·문서를 작성하고 실행했다. timestamp-only unique와 자동 legacy 삭제는 채택하지 않았다. 사람의 event 의미 확인 및 실제 broker 장애 실험은 수행하지 않았다. 포트폴리오 주장은 위 DB 경계에 한정한다.
