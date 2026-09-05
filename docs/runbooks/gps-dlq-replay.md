# GPS 저장 실패: DLQ 적용과 제한적 수동 재처리

## 보장 범위와 배포 전제

GPS 저장 전용 `gpsSaveListenerContainerFactory`는 DB 일시 오류만 최대 3회(최초 포함), 200ms → 400ms 간격으로 시도한다. 나머지는 1회 후 reject/no-requeue한다. 실패는 source queue의 broker policy를 통해 DLQ로 이동한다. **policy 없이 애플리케이션만 배포하면 실패 메시지가 폐기될 수 있다.**

기존 `gps_log.queue`의 declaration arguments는 바꾸지 않는다. 기존 큐 삭제·purge·이름 교체 없이 policy를 적용한다. 실제 운영 broker 변경은 이번 개발 작업에서 실행하지 않았다.

DLQ는 영구 보관소나 무유실 보장이 아니다. 현재 classic queue의 dead lettering은 target 장애 등에 대해 무유실을 보장하지 않는다. quorum queue의 at-least-once dead lettering, broker HA·복구 실험은 별도 단계다. durable queue만으로 message persistence와 디스크 장애까지 해결되지 않는다.

## 적용 순서: 승인된 broker에서만

1. 운영자 권한과 정확한 vhost를 확인한다. 수집 producer와 저장 consumer를 중지할 유지보수 구간을 확보한다. 임의로 live consumer를 종료하지 않는다.
2. 아래 read-only 점검으로 source/DLQ의 메시지 수, arguments, 기존 policy와 binding을 기록한다. 명령 예시는 vhost `/`이며 다른 환경에 그대로 적용하지 않는다.

```bash
rabbitmqctl list_queues -p / name messages_ready messages_unacknowledged arguments policy
rabbitmqctl list_policies -p /
rabbitmqctl list_bindings -p / source_name destination_name routing_key
```

3. 다음 topology가 먼저 존재하도록 관리 도구에서 선언하고 확인한다. 앱의 `RabbitAdmin`도 같은 선언을 사용한다. 저장 listener를 시작하기 전에 생성 여부를 확인해야 한다.

| 리소스 | 값 |
| --- | --- |
| DLX | `gps_log.dead.exchange`, direct, durable, auto-delete=false |
| DLQ | `gps_log.dead.queue`, durable, exclusive=false, auto-delete=false, DLX/TTL 없음 |
| binding | 위 DLX → DLQ, routing key=`gps_log.dead` |

4. 기존 policy와 충돌하지 않는 priority를 결정한다. 아래 50은 테스트 예시다. RabbitMQ의 policy 우선순위 때문에 기존 TTL/length 등의 설정이 대체될 수 있으므로 기존 정의를 검토·병합한 후 승인받는다. source에 `x-dead-letter-*` argument가 이미 있으면 policy보다 우선하므로 작업을 중단하고 별도 전환안을 만든다.

```bash
rabbitmqctl set_policy --vhost / --apply-to queues --priority 50 gps-save-dlx '^gps_log\.queue$' '{"dead-letter-exchange":"gps_log.dead.exchange","dead-letter-routing-key":"gps_log.dead"}'
```

5. exact source queue에 policy가 적용되고 DLQ에 TTL/자동 원복 routing이 없는지 확인한다. 쓰기·읽기·configure 및 DLX 관련 권한도 확인한다.
6. 격리된 환경에서 아래 테스트를 먼저 통과시킨다. 운영 적용 후에는 승인된 synthetic canary로 실제 DLQ routing을 확인한 뒤 producer/consumer를 재개한다. 선언만으로 정상 routing을 단정하지 않는다.

```bash
./gradlew test --tests org.thisway.support.config.MySqlMigrationIntegrationTest --console=plain
```

rollback 때 source/DLQ를 삭제하거나 policy를 무조건 제거하지 않는다. 소비를 중지하고 메시지를 유지한 채 기존 설정과 차이를 검토한다. old consumer로 돌아가더라도 DLQ policy를 유지하면 기존 reject도 보관할 수 있다.

## 오류 분류

| 실패 | 처리 | 복구 전 확인 |
| --- | --- | --- |
| `TransientDataAccessException`, `RecoverableDataAccessException`, `CannotCreateTransactionException` | cause chain 검사, 최대 3회 | DB lock, 연결, 자원 상태 |
| validation/Emulator 미등록/JSON conversion | 1회 후 DLQ | 입력 계약, 실제 장치 등록 및 소유권 |
| FK·기타 무결성 오류 | 1회 후 DLQ | 참조 데이터, migration 상태 |
| 미분류 오류·코드 버그 | 1회 후 DLQ | 재현 및 수정 전 자동 재처리 금지 |

재시도는 repository transaction 바깥의 listener advice가 담당한다. 재시도마다 새로운 저장 transaction이 실행된다. consumer thread에서 backoff하므로 prefetch=1로 미처리 메시지 선점을 줄였으며 처리량 영향은 아직 측정하지 않았다. SSE 전용/기존 공통 factory 정책은 변경하지 않았다.

## 관측과 민감 데이터

- `gps.consumer.rejected`는 애플리케이션에서 최종 거부를 결정한 횟수다. DLQ 도착 확인이나 고유 메시지 수가 아니며 프로세스 재시작 시 counter가 초기화된다.
- DLQ `messages_ready`, `messages_unacknowledged`와 source backlog를 관리 도구로 함께 확인한다. counter 증가 또는 DLQ 유입 시 조사하고 장기 체류·용량 증가에 대응한다. 자동 alert/dashboard는 아직 구현하지 않았다.
- `x-death`의 source queue, reason, count, time은 broker 이력이다. 개별 SQL 예외 원인은 저장하지 않는다. 승인된 trace 상관관계와 재현 환경으로 원인을 조사한다.
- DLQ body에는 실제 GPS 위치가 들어간다. 접근을 운영자에게 제한하고 원문을 터미널·일반 로그·포트폴리오에 복사하지 않는다. 보관 기간·접근 감사·폐기 절차는 운영 적용 전에 별도 승인해야 한다. 이번 변경에 자동 삭제는 없다.

## Replay 절차와 중단 조건

CHANGE-023에서 제한적 CLI를 추가했다. 아래 절차와 다음 CLI 제약을 함께 따른다. 웹 API·조직 승인 시스템·중앙 감사 저장소는 제공하지 않으며 승인 없이 메시지 삭제·대량 replay를 해서는 안 된다.

1. 오류를 먼저 수정하고 재처리 대상·건수·담당자·사유를 기록한다. 장치의 현재 MDN→vehicle 매핑과 원래 소유권이 달라졌으면 자동 replay하지 않는다.
2. DLQ에서 `autoAck=false`, 한 건씩 가져온다. Management UI의 ack-and-remove로 조회하지 않는다.
3. 원본 payload 및 필요한 protocol properties를 그대로 유지하고 **저장용 direct exchange에만** publish한다. fanout 재발행은 금지한다. 신규 운영 도구에서는 재처리 횟수·audit ID를 관리하고 내부 broker header 처리도 대상 broker 버전에 맞춰 검증해야 한다.
4. `mandatory=true`와 publisher confirm을 모두 사용한다. return/unroutable, nack, timeout, 연결 실패 중 하나라도 발생하면 **DLQ ack를 보내지 않는다**. 연결을 닫으면 원본은 다시 받을 수 있다. 성공 여부가 불확실한 경우에도 원본을 삭제하지 않는다.
5. routing 성공 및 confirm 이후에만 현재 DLQ channel의 delivery tag로 ack한다. confirm은 consumer의 DB commit 보장이 아니다. 소비 결과와 DLQ 재유입을 별도로 확인한다.
6. publish 성공 후 DLQ ack 전 장애는 중복 publish를 만들 수 있다. CHANGE-020의 신규 exact observation unique key가 DB 중복 효과를 제한한다. legacy NULL key나 장치 재할당까지 보장하는 것은 아니다.
7. 동일 메시지가 다시 DLQ에 들어오면 자동 반복하지 말고 중단·조사한다. 자동 DLQ→source loop는 구성하지 않는다.

CHANGE-022의 harness는 원본 properties를 보존했다. 새 CLI는 원본 body만 그대로 유지하고 properties는 `application/json`, UTF-8, persistent 및 고정 DTO type으로 재구성한다. 임의 expiry/user/type header와 broker x-death 이력은 복사하지 않는다. 대신 `thisway-replay-count=1`, 승인 ID를 추가한다. 이미 replay marker가 있으면 값과 무관하게 거부한다. RabbitMQ 3.13.7 fixture 검증이며 새 버전까지 검증된 범용 도구가 아니다.

## 제한적 CLI 사용법

별도 `src/replay` source set이며 웹 애플리케이션 jar에 포함되지 않는다. Java 21·Gradle 환경에서 실행한다. 운영 배포나 실제 메시지 replay는 이번 개발 작업에서 수행하지 않았다.

사전 조건:

- 승인된 관리자 계정과 로컬 broker 또는 승인된 SSH/port-forward tunnel을 준비한다. 직접 원격 host는 CLI가 거부한다. tunnel이 어느 broker/vhost인지 별도 승인 기록에 남긴다.
- `GPS_REPLAY_URI` 환경 변수에 AMQP 연결 정보를 안전하게 주입한다. URI/password를 인자·Git·로그·터미널 출력으로 남기지 않는다. 환경 변수도 같은 사용자/프로세스 권한 관점에서 완전한 비밀 저장소가 아니다.
- DLQ와 source topology/policy를 확인하고 장치 소유권·MDN mapping을 사람이 확인한다. 도구는 DB나 조직 승인 시스템에 접근하지 않는다.
- 다른 replay 작업과 동시에 실행하지 않는다. 이 도구에 전역 lock/승인 검증/중앙 rate limit은 없다.

```bash
./gradlew gpsDlqReplay --args=--help --console=plain
./gradlew gpsDlqReplay --args=preview --console=plain
```

preview는 큐 선두 한 건을 manual get하고 hash/byte 수만 보여 준 뒤 연결을 닫는다. 원본 body를 출력하거나 publish/ack하지 않는다. **완전한 read-only peek가 아니며**, 일시적인 unacked 상태와 redelivery·순서 변화가 생길 수 있다.

확인한 hash와 승인 번호, 신뢰할 수 있는 운영자 전용 디렉터리의 **새 절대 파일 경로**를 지정한다. 다음 값들은 반드시 실제 승인 값으로 바꾼다.

```bash
./gradlew gpsDlqReplay --args='execute APPROVAL_ID SHA256 ABSOLUTE_NEW_AUDIT_FILE' --console=plain
```

- 한 호출은 한 건만 처리한다. 선두 메시지 hash가 달라지면 중단하고 다시 확인한다. payload 편집·검색·대량 drain 기능은 없다.
- 256KiB 이하의 유효한 GPS request만 재발행한다. 잘못된 JSON/입력은 이 도구로 수정·강제 replay할 수 없다.
- 신규 audit 파일은 POSIX 0600 + CREATE_NEW로 만들며 기존 파일을 덮어쓰지 않는다. 각 이벤트는 `force(true)` 후 다음 단계로 넘어간다. 로컬 파일이므로 변조 방지·중앙 보존은 별도다.
- `RUN_STARTED → INTENT → PUBLISH_CONFIRMED → ACK_COMPLETED`를 기록한다. 실패하면 가능한 경우 `FAILED_OR_UNCERTAIN`을 기록한다. 위치·MDN·credential·임의 stacktrace는 기록하지 않는다.
- audit 실패가 publish 전이면 전송하지 않는다. confirm 기록 실패면 원본 ack를 하지 않는다. ack 이후 최종 audit 실패면 이미 원본이 제거됐을 수도 있으므로 무조건 재실행하지 말고 확인한다.
- marker는 정상 도구 경로의 반복을 제한할 뿐 인증 장치가 아니다. 원본 DLQ ack 누락 시 원본에는 marker가 없어 다시 replay될 수 있다. 이 경우 DB 멱등성과 audit 확인이 필요하다.
- `REPLAYED`는 routing/confirm과 DLQ ack 절차 완료를 뜻하며 DB 저장 성공을 뜻하지 않는다. source 소비 결과·DLQ 재유입을 따로 확인한다.

검증: CLI help·컴파일, audit 파일 권한/기존 파일 보존, 실제 처리 엔진을 RabbitMQ/MySQL에 연결한 preview·unroutable·audit 실패 후 중복 복구. 프로세스 kill/조직 권한/운영 터널·실제 URI 연결 전체는 미검증이다.

## 참고 자료

- [Spring AMQP: recovery와 cause-chain classification](https://docs.spring.io/spring-amqp/reference/amqp/resilience-recovering-from-errors-and-broker-failures.html)
- [RabbitMQ: DLX policy와 safety](https://www.rabbitmq.com/docs/dlx)

공식 현재 문서는 프로젝트 버전보다 최신일 수 있다. 구현 API와 실제 동작은 로컬 Spring AMQP 3.2.5 / Spring Retry 2.0.11 및 컨테이너 테스트로 검증한다.
