# Thisway Backend 기준선 감사

## 감사 정보

- 감사일: 2026-09-05 KST
- 로컬 기준: `develop@98bff23`
- 원격 저장소: [Kernel360/KBE5-Thisway-BE](https://github.com/Kernel360/KBE5-Thisway-BE)
- 방법: README·코드·설정·Git/PR 이력 정적 분석, Gradle 테스트 1회 실행
- 실행하지 않은 항목: 애플리케이션 기동, fresh MySQL/RabbitMQ 구성, k6 부하 시험, 실제 AWS 배포

## 결론

포트폴리오로 살릴 가치가 충분하다. 다만 현재 상태 그대로 “대용량 트래픽, 데이터 정합성, 안정적 운영을 완성했다”고 말할 수는 없다. 좋은 재료는 차량 텔레메트리, RabbitMQ, SSE, 회사별 통계 Batch, 모니터링 구성이다. 부족한 것은 중복·유실·재시작·tenant 침범 같은 실패 조건에 대한 설계와 실행 증거다.

AI 기능이 없는 것은 핵심 약점이 아니다. 현재 우선순위는 신뢰 가능한 차량 이벤트 파이프라인과 배치, 보안, 재현 가능한 테스트다. AI는 그 기반과 평가 데이터가 준비된 뒤 이상 운행 후보 탐지·설명에 제한적으로 추가한다.

## 저장소 상태

- 공개 저장소 기본 브랜치는 `develop`이며 마지막 commit은 2025-08-02의 `98bff23`이다.
- `origin/main`은 `origin/develop`보다 707 commits 뒤다. 배포와 공개 기준 브랜치 정책을 개인 fork에서 다시 정해야 한다.
- 공개 이력은 717 commits, merged PR 96개다. 사용자 기여 범위는 [`original-contributions.md`](original-contributions.md)에 분리했다.
- `v1.0.0` tag는 있으나 현재 `develop`은 그 이후 변경을 포함한다.
- 확인 시점의 작업 트리는 clean이었다.

이 수치는 저장소 활동량을 설명할 뿐 품질이나 개인 성과를 증명하지 않는다.

## 실제 실행 결과

실행 명령:

```bash
./gradlew test --console=plain
```

결과:

| 항목 | 결과 |
| --- | ---: |
| 전체 | 178 |
| 성공 | 161 |
| 실패 | 4 |
| skipped | 13 |
| Build | FAILED |

실패 4건은 로컬 `localhost:6379` Redis가 실행되지 않아 발생했다.

- [`RedisComponentTest`](../../src/test/java/org/thisway/support/component/RedisComponentTest.java)
- [`PasswordServiceTest`](../../src/test/java/org/thisway/member/application/PasswordServiceTest.java)
- CI는 Redis service를 선언하지만 [README](../../README.md)에 로컬 테스트 사전조건이 없다.

skipped 13건 중 10건은 본문이 비어 있는 [`TripLogServiceTest`](../../src/test/java/org/thisway/vehicle/triplog/application/TripLogServiceTest.java)이고, 3건은 Security 허용·거부 테스트다. 따라서 “테스트 178개 보유”보다 “핵심 시나리오가 아직 비어 있고 fresh clone의 기본 테스트가 red”가 정확한 기준선이다.

## 실제 실행 흐름

### GPS

```text
POST /api/logs/gps
  -> GpsLogService
     -> direct mode: JDBC multi-row insert
     -> rabbitmq mode:
        -> DirectExchange -> durable queue -> gps_log 저장 consumer
        -> FanoutExchange -> instance-local anonymous queue -> SSE 전송
```

### Power와 TripLog

```text
POST /api/logs/power
  -> power_log 저장
  -> Vehicle power/mileage/location 변경
  -> Kakao reverse geocoding
  -> TripLog 시작 또는 종료
```

현재 이 흐름은 하나의 DB transaction 안에서 외부 Kakao API까지 호출한다.

### Statistics

```text
매일 02:00
  -> 전일 대상
  -> active company 전체 순회
  -> TripLog/GPS 집계
  -> company별 Statistics 저장
  -> API 조회 시 날짜 범위 재집계
```

## README와 구현 비교

| README 설명 | 코드에서 확인한 사실 | 판정 |
| --- | --- | --- |
| JWT와 역할 기반 인가 | 구현되어 있으나 refresh token도 access token 생성기를 그대로 사용하며 rotation/revocation이 없다. | 부분 일치 |
| 차량 CRUD | Controller, Service, JPA/QueryDSL, soft delete가 있다. | 일치 |
| 시동·위치 등을 RabbitMQ로 비동기 수신 | RabbitMQ 선택은 GPS에만 적용된다. power/geofence는 동기 처리하며 dev 기본은 `direct`다. | 부분 불일치 |
| 수신 데이터를 TripLog로 가공 | TripLog는 GPS consumer가 아니라 power HTTP 처리에서 생성·종료된다. | 표현 수정 필요 |
| 일별/월별 Spring Batch | 전일 일별 job 하나가 있고, 월별 전용 job은 없다. 조회 시 일 통계를 범위 집계한다. | 부분 일치 |
| 사용자별·차량별 거리·시간 통계 | 실제 핵심 통계는 회사 단위 시동 횟수·운전 시간·가동률·출발지다. | 불일치 |
| Redis 캐싱 | 일반 조회 cache가 아니라 password reset 인증 코드 저장에 사용한다. | 용어 수정 필요 |
| Prometheus/Grafana 모니터링 | 의존성과 scrape/datasource 설정은 있으나 dashboard, alert, SLO, runbook은 없다. | 기반만 일치 |
| 대용량·정합성·안정 운영 | bulk insert, consumer concurrency, k6 script는 있으나 결과와 장애·중복·유실 검증이 없다. | 미입증 |
| 디렉터리 구조 | 실제 feature-first package 구조와 README의 예시가 다르다. | 오래됨 |

## 현재 강점

- `@ConditionalOnProperty`를 이용해 direct/RabbitMQ GPS 수집 전략을 교체할 수 있다.
- Direct queue는 저장 consumer의 경쟁 소비, fanout queue는 각 인스턴스의 로컬 SSE 전달을 의도한다.
- GPS packet을 행별 insert하지 않고 한 SQL의 multi-row insert로 저장한다.
- 최신 좌표 조회는 vehicle별 N번 query 대신 window function 한 번으로 처리한다.
- 과거 SSE chunk 전송 중 live event를 buffer하려는 설계 의도가 있다.
- 일별 materialized aggregate를 저장한 뒤 범위 조회에 재사용하는 통계 방향은 타당하다.
- feature package 안에 `interfaces/application/domain/infrastructure`를 둔 modular monolith 형태다.

이 항목들은 좋은 설계 출발점이다. 성능·정합성 성과로 표현하려면 다음 위험을 해결하고 측정해야 한다.

## P0: 공개 전에 해결할 위험

### 1. fresh DB가 코드 계약과 일치하지 않는다

- [`00-init-schema.sql`](../../infra/dev/mysql/db/00-init-schema.sql)은 `geofence_log.occured_time`을 만들지만 [`LogRepository`](../../src/main/java/org/thisway/vehicle/log/infrastructure/LogRepository.java)는 `occurred_time`에 insert한다.
- `idx_trip_log_vehicle_id`라는 index를 `trip_log`가 아니라 `power_log`에 다시 생성한다.
- 초기 SQL에는 `statistics`와 Spring Batch metadata가 포함되지 않는다. 별도 `batch-schema.sql`은 있지만 prod만 `initialize-schema: never`를 명시하고, dev는 명시값이 없어 외부 MySQL이 자동 초기화 대상이 되는 설정이 아니다. migration 도구도 없다.
- dev/prod가 모두 `ddl-auto: update`라 환경마다 schema가 달라질 수 있다.

예상 결과는 fresh MySQL로 재현해야 확정한다. 해결 방향은 Flyway 단일 기준, `ddl-auto=validate`, MySQL Testcontainers다.

### 2. password와 JWT가 로그에 남을 수 있다

- [`LoggingFilter`](../../src/main/java/org/thisway/support/logging/filter/LoggingFilter.java)는 query string 전체를 INFO로 기록한다.
- SSE endpoint는 JWT를 `?token=`으로 받는다.
- request/response body advice는 거의 모든 body를 INFO로 기록하지만 `@MaskingData` 실제 사용처가 없다.
- 운영 설정도 Security debug가 켜져 있다.

마스킹만 추가하기보다 민감 body·query 수집 자체를 allowlist 방식으로 축소해야 한다.

### 3. Actuator 공개 범위가 과도하다

- [`ActuatorAuthorizationPolicy`](../../src/main/java/org/thisway/support/security/config/policy/ActuatorAuthorizationPolicy.java)는 모든 GET `/actuator/**`를 `permitAll`한다.
- 운영은 일부 endpoint만 제외하고 `exposure.include: "*"`, health detail은 `always`다.

health와 Prometheus처럼 필요한 endpoint만 내부 관리 경로에 제한해야 한다.

### 4. role과 tenant 경계가 일치하지 않는다

- TripLog current/detail/stream 일부는 ID만으로 조회하고 회사 소유권을 확인하지 않는다.
- Emulator CRUD/list는 tenant 조건 없는 전역 repository 접근이다.
- `POST /api/statistics/save`는 GET 전용 policy 밖으로 빠져 모든 인증 사용자에게 허용될 수 있다.
- SSE key 검색이 `startsWith("vehicle:1")` 방식이어서 `vehicle:10:*`도 함께 매칭된다. company key도 같은 문제를 가진다.
- Member DELETE policy와 실제 endpoint pattern이 다르며 차단 test는 disabled다.

repository 단계의 `tenantId + resourceId` 조건과 cross-tenant negative integration test가 필요하다.

### 5. 누구나 차량 로그를 위조할 수 있다

- GPS/power/geofence POST가 모두 `permitAll`이다.
- `tid/mid/pv/did` device 정보, `cCnt`와 실제 list 크기, 시간 범위, 좌표, packet 크기를 검증하지 않는다.
- public payload의 list 크기 상한이 없어 한 번의 요청이 거대한 SQL과 메모리 사용을 만들 수 있다.

device credential/HMAC 또는 mTLS, timestamp/nonce, replay 방지, idempotency key, validation, size/rate limit가 필요하다.

### 6. 핵심 테스트가 green이 아니다

현재 default test는 Redis 외부 상태에 의존하고 TripLog·Statistics/Batch·RabbitMQ·SSE의 핵심 실패 시나리오가 비어 있다. 코드 변경에 앞서 신뢰할 수 있는 회귀 안전망을 만들어야 한다.

## P1: 포트폴리오 핵심 개발 주제

### 메시징 신뢰성

- 3회 실패 후 `RejectAndDontRequeueRecoverer`로 버리지만 DLQ가 없다.
- publisher confirm/return과 dual publish 일부 성공 처리가 없다.
- event identity나 unique constraint가 없어 재전달 시 GPS가 중복 저장될 수 있다.
- retryable/non-retryable 분류, replay 절차, queue lag·중복·실패 업무 metric이 없다.

### Trip과 외부 API 경계

- power ON/OFF 중복·역순·유실 정책이 없다.
- Kakao reverse geocoding이 timeout 설정 없이 DB transaction 안에서 실행된다.
- `sum`이 누적 계기값인지 운행 거리인지 Emulator 계약으로 확인한 뒤 `startOdometer`, `endOdometer`, `distance`를 분리해야 한다.

### Batch 재시작성과 정확성

- 하나의 tasklet이 모든 회사를 순회하고 각 예외를 잡아 로그만 남긴다.
- 매번 timestamp로 새 JobInstance를 만들어 동일 대상일 restart 의미가 약하다.
- `(company_id, date)` unique constraint, 다중 인스턴스 실행 방지, timezone 명시가 없다.
- GPS 누락, 수집 주기, 정차, late event가 가동률 공식에 미치는 영향이 정의되지 않았다.

### SSE lifecycle

- `@Async`가 있으나 `@EnableAsync`가 없고 동일 bean 내부 호출이어서 실제 비동기 실행 근거가 없다.
- 전체 기록을 메모리에 올린 뒤 자르므로 DB streaming은 아니다.
- 동일 사용자 재연결 시 emitter overwrite, event name 유실, bounded buffer, heartbeat, Last-Event-ID 정책이 없다.

### 배포와 관측

- CD가 test를 실행하지 않고 immutable commit tag, 배포 안정화 대기, health 검증, rollback이 없다.
- generic JVM/HTTP/Rabbit metric은 수집할 수 있지만 ingest rate, consumer lag, invalid/replayed packet, DLQ, SSE connection 같은 업무 metric이 없다.
- k6 파일에는 결과가 없다. `stress_test.js`는 주석의 15,000 VU와 달리 실제 `vus`가 1,000이다.

## 확인된 사실과 아직 검증하지 않은 것

확인된 사실은 소스·설정·Git/PR 상태와 Gradle test 결과다. 다음 항목은 이후 실행 실험 전까지 “예상” 또는 “미검증”으로만 표현한다.

- fresh compose에서 geofence insert와 Batch job의 실제 실패
- SSE prefix collision의 실제 잘못된 수신 재현
- RabbitMQ 장애 시 중복·유실량
- Kakao 지연이 transaction과 처리량에 미치는 영향
- `sum` protocol 의미와 mileage 계산 오류 여부
- 현재 AWS 인프라의 생존 여부와 운영 traffic
- 1,000대 또는 15,000대 처리 성능

## 포트폴리오 판단

추천 서사는 다음과 같다.

> 차량 텔레메트리의 중복·지연·실패를 견디는 수집 파이프라인과 재처리 가능한 통계 배치를 구축하고, 이를 멀티테넌트 보안 테스트·부하 측정·운영 지표로 검증한 차량 관제 백엔드

이 문장은 목표다. 구현과 검증이 끝난 항목만 단계적으로 현재형으로 바꾼다. AI는 이 서사의 필수 조건이 아니며, 기반이 완성된 뒤 검증 가능한 anomaly 후보 랭킹과 근거 설명 기능으로 추가한다.
