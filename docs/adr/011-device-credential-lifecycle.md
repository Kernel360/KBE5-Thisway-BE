# ADR-011: 회사 범위 장치 credential 관리 기반

- 날짜: 2026-09-06
- 상태: 채택 — CHANGE-037에서 수집 인증/비동기 identity/Emulator 연결, 운영 전환은 후속
- 관련: CHANGE-034, P1-01C

## 맥락과 결정

CHANGE-034 시점에는 GPS/Power/Geofence 수집 API가 공개 요청을 허용했다. 먼저 키 발급·교체·폐기와
원문 보관 경계를 정의했고, 아래 CHANGE-037 보완 결정에서 수집 인증을 연결했다.

사람의 JWT를 장치에 복사하지 않고 SecureRandom 32바이트로 독립적인 opaque key를 발급한다.
DB에는 SHA-256 해시만 저장한다. 사람이 고르는 저엔트로피 비밀번호에 이 방식을 적용하는 것은 다른 문제다.
HMAC 요청 서명·mTLS는 메시지 무결성/상호 인증에 도움이 되지만 provisioning과 운영 복잡도가 커 이번 기반에서는 제외한다.

JWT의 COMPANY_ADMIN endpoint 정책에 더해 현재 DB 회원의 역할·active 회사와
장치→차량→회사 소유권을 확인한다. COMPANY_CHEF의 기존 장치 CRUD 권한을 키 발급 권한으로 확대하지 않는다.
요청의 companyId는 받지 않는다. 다른 회사 자원은 404로 처리한다.

장치당 한 행을 두고 발급 시 기존 해시를 교체한다. 유효기간은 30일이며 새로 정한 포트폴리오 정책이지
원 RFP의 복원이나 실장치 운영 경험에 근거한 수치가 아니다. 교체 유예기간과 자동 갱신 worker는 없다.
원문은 발급 응답에만 포함하고 조회 API에는 노출하지 않는다. 응답 유실 시 원문을 복원할 수 없어 재발급해야 한다.

## 저장·동시성·감사

V8의 두 테이블은 JdbcTemplate으로 관리한다. Flyway가 스키마 원천이며 실제 MySQL 테스트로 검증한다.
JPA validate만으로 이 JDBC 전용 테이블의 계약을 검증했다고 주장하지 않는다.
소유권 조회의 FOR UPDATE 이후 교체와 감사 이벤트를 같은 READ_COMMITTED transaction에 저장한다.
감사 저장 실패 시 교체도 rollback한다. 동시에 발급하면 마지막 commit의 키만 남는다.
JOIN 잠금이 차량/회사 행까지 확대될 수 있으므로 장치별로만 좁게 잠긴다고 주장하지 않는다. 처리량 측정은 하지 않았다.

hash unique 충돌은 실패시킨다. 다른 장치 행까지 갱신할 수 있는 일반적인 ON DUPLICATE KEY UPDATE 대신
emulator_id 지정 UPDATE/INSERT를 쓴다. 감사에는 행위자·장치·회사 ID, 행위, 시각만 저장한다.
장치 삭제 시 credential은 cascade 삭제하되 감사 ID에는 cascade FK를 두지 않아 기록을 남긴다.
변조 방지 저장소나 보존 기간 정책까지 구현한 것은 아니다.

## 후속 인증 적용의 필수 조건

- 현재 ACTIVE는 메타데이터 상태이지 수집 요청의 인증 성공이 아니다.
- CHANGE-035에서 차량/MDN 변경마다 assignmentRevision을 증가시키고 발급 당시 revision도 비교한다.
  관리 API로 변경했다 원상 복구해도 이전 키는 BINDING_CHANGED를 유지한다. 아래 보완 결정의 범위에 한정한다.
- 상태 조회의 두 query 사이 변경 가능성이 있어 조회 결과를 인가 판정으로 재사용하지 않는다.
- GPS는 비동기로 소비된다. HTTP 인증 후 소비 시 MDN만 다시 조회하면 다른 차량/회사에 저장될 수 있다.
  인증 identity와 할당 revision을 메시지 및 저장 경계까지 전달·검증해야 한다.
- CHANGE-035에서 비활성 차량 발급은 거부하되 소유 관리자의 상태 조회(INACTIVE)와 폐기는 허용한다.
  비활성화만으로 키를 영구 폐기하지 않으므로 다시 active가 되면 상태가 복원될 수 있다.
- 권한 검증 후 진행 중인 요청에 대한 동시 역할 변경의 즉시 취소는 보장하지 않는다.
- HTTPS, Emulator 비밀 주입, 상수 시간 검증, 재전송·크기·rate 제한, 배포 전환 및 복구를 별도로 검증한다.

## CHANGE-035 보완 결정: 연결 세대

단순 snapshot 비교에는 A→B→A 변경을 감지할 수 없는 문제가 있다. 변경 시 해시를 지우는 방식 대신
장치의 assignmentRevision과 발급 당시 boundAssignmentRevision을 비교하도록 선택했다.
이 값은 이후 비동기 메시지의 연결 identity에도 활용할 수 있지만 현재 메시지에는 아직 전달하지 않는다.
키 행/해시는 남고 상태가 BINDING_CHANGED가 되는 **논리적 무효화**이며 REVOKED 감사 이벤트를 생성하는 폐기와 다르다.

V9에서 기존 장치와 credential의 revision을 0으로 초기화한다. V8 이전 연결 이력은 알 수 없으므로
과거 A→B→A까지 복원·탐지하는 것은 아니다. 관리 API가 MDN/차량을 실제 변경할 때만 한 번 증가하며
동일값·펌웨어 변경은 유지한다. 재발급은 현재 revision을 저장한다.
EmulatorService의 변경 조회에 PESSIMISTIC_WRITE를 적용해 키 발급과 동일 장치 행을 잠그고,
동시 변경이 증가 값을 덮어쓰지 않게 한다. 잠금 대기/교착 가능성과 성능은 운영 검증 대상이다.

이 revision은 JPA @Version(모든 수정의 낙관적 잠금)이 아니라 연결 의미를 표현하는 도메인 값이다.
직접 SQL/bulk update는 이 규칙을 우회하므로 허용된 운영 변경 경로가 아니다. 부득이한 데이터 복구는
해당 키 폐기와 revision 증가를 함께 수행하는 별도 승인 절차가 필요하다.
차량 회사 이동과 active 전환은 이 revision이 기록하는 이벤트가 아니며 해당 정책은 후속 설계다.

## CHANGE-036 보완 결정: 인증 검증기

키 검증은 관리용 status와 분리한 DeviceAuthenticationService에서 담당한다.
장치 ID를 locator로 사용하고 credential과 현재 emulator/vehicle/company를 단일 JOIN으로 읽는다.
발급 당시 소속·revision·MDN, 현재 활성 회사/차량, 발급/만료/폐기를 확인한 후보만 해시 비교한다.
payload MDN도 exact 비교하며 반환 identity의 회사/차량은 DB에서 얻는다.
SQL의 MDN 비교는 BINARY로 지정해 기본 collation의 대소문자/후행공백 동등 처리를 허용하지 않는다.

MessageDigest.isEqual로 고정 길이 SHA-256 해시를 비교한다. 전체 요청의 일정한 응답 시간을
보장하지 않는다. 실패 원인은 단일 인증 오류로 반환하되 DB 장애는 서버 오류로 남긴다.
검증은 read-only snapshot으로, 이후 비동기 저장까지 소속을 고정하지 않는다.
HTTP 수집 경로 연결, 서버 identity 메시지 전달, consumer 재검증과 Emulator 연결은 여전히 후속이다.
자세한 검증과 한계는 [CHANGE-036](../portfolio/work-logs/2026-09-07-device-authentication-verifier.md)에 기록한다.

## CHANGE-037 보완 결정: 수집과 소비의 소속 검증

세 수집 POST는 X-Device-Id/X-Device-Key를 검증한다. device locator는 emulator DB ID이며 payload did와 다르다.
GPS body는 유지하고 서버에서 확인한 version/emulatorId/vehicleId/companyId/assignmentRevision을 AMQP header에 담는다.
저장·방송은 현재 active 소속/revision을 FOR UPDATE로 재검증한다. guard와 쓰기는 같은 transaction이다.
Power/Geofence의 기존 MDN 재조회도 해당 잠금 안에서 수행한다. GPS 쓰기와 방송 대상은 identity ID를 직접 사용한다.

키 폐기/만료/교체 이후 새 인증은 거부하지만 이미 인증한 같은 연결의 메시지는 처리한다.
연결 변경은 이전 세대를 거부하며, identity 없는 legacy 메시지는 자동 MDN fallback 없이 검토 대상으로 남긴다.
저장 오류의 기존 retry/DLQ 분류를 유지하고 live는 실패 시 재큐잉하지 않는 best effort로 분리한다.
replay는 검증한 identity allowlist만 복사한다. broker publish ACL은 필수 신뢰 경계이며 identity 자체가 서명은 아니다.

Python Emulator는 private MDN별 credential 파일을 읽고, 브라우저 Emulator는 실행 중 메모리에서만 입력 키를 사용한다.
두 client 모두 인증 실패를 성공으로 무시하지 않는다. 브라우저 저장소에 키를 저장하지 않으며 JavaScript heap의
암호학적 메모리 삭제나 브라우저 확장 프로그램까지 보호하는 방식은 아니다.
잠금 확대·SSE 지연·운영 처리량, nonce/replay·size/rate 정책, legacy backlog와 실제 배포는 미완료다.
[CHANGE-037](../portfolio/work-logs/2026-09-07-device-ingestion-authentication.md),
[운영 전환 gate](../runbooks/device-ingestion-authentication.md)에 검증 범위를 기록한다.
