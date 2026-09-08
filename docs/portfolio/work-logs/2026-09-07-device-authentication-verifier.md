# CHANGE-036: 장치 키 인증 검증기와 소속 snapshot

## 메타데이터

- 날짜: 2026-09-07
- 작업자: 사용자 + AI 구현/검증 보조
- 브랜치/기준 커밋: codex/statistics-batch-restart / ef74fcf
- 관련: P1-01C, ADR-011 / 원격 PR 없음
- 상태: Verified — 로컬 인증 검증기, HTTP 수집 인증 연결은 미완료

## 1. 문제와 근거

CHANGE-034/035는 관리자용 키 발급·조회·폐기와 연결 revision을 제공한다.
실제 원문 키를 검증하는 경계가 없고, DeviceCredentialService.status는 소속과 키를
두 query로 읽으므로 그대로 수집 인가에 재사용할 수 없다.
원 팀의 Emulator/수집 구현에 추가하는 개인 현대화다. 원래 개인 담당 Vehicle/Statistics/Batch와 구분한다.

## 2. Acceptance criteria

- [x] 현재 키·활성 회사/차량·발급 당시 소속·revision·MDN을 함께 확인하고 DB identity를 반환한다.
- [x] 교체 전 키, 폐기, 만료, 미래 발급, 미발급/삭제, 다른 회사 키, 재연결 원복을 거부한다.
- [x] MDN은 MySQL 기본 collation과 무관하게 대소문자와 후행공백을 구분한다.
- [x] 형식/길이 오류는 DB 조회 전에 거부하고 인증 실패 사유를 동일한 오류로 제한한다.
- [x] 실제 MySQL 통합 테스트와 전체 테스트 결과를 기록한다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·위험 | 결정 |
| --- | --- | --- | --- |
| 기존 status의 ACTIVE 재사용 | 구현량이 적음 | 키 검증이 없고 두 query 사이 소속 변경 가능 | 제외 |
| 장치 ID로 단일 JOIN 조회 후 해시 비교 | 키와 현재 소속을 같은 statement snapshot으로 확인 | 조회 직후 변경까지 막지는 못함 | 채택 |
| 인증부터 broker 소비까지 DB lock 유지 | 동시 변경 제한 의도 | 비동기·분산 경계를 넘어 transaction을 유지할 수 없음 | 제외 |

장치 ID는 조회 locator일 뿐 신뢰하지 않는다. companyId/vehicleId는 요청에서 받지 않는다.
SHA-256은 기존 SecureRandom 256-bit opaque key 정책을 유지한다. 사람의 비밀번호 저장 방식으로 일반화하지 않는다.
MessageDigest.isEqual로 고정 길이 해시를 비교하며 후보가 없을 때도 dummy 해시를 비교한다.
DB 조회/형식 검사/응답을 포함한 전체 요청의 일정한 처리 시간을 보장한다는 의미는 아니다.

## 4. 구현과 실행 흐름

DeviceAuthenticationService.authenticate(emulatorId, key, payloadMdn)
→ 키 49자/ASCII 형식·MDN 최대 20자 검사
→ DeviceCredentialRepository.findAuthenticationCandidate 단일 JOIN
→ 활성 소속·발급/만료 시각·폐기 여부·bound snapshot/revision 확인
→ SHA-256 해시 비교와 payload MDN exact 비교
→ DeviceIdentity(emulatorId, vehicleId, companyId, mdn, assignmentRevision) 반환.

읽기 전용이며 키/감사/운행을 변경하지 않는다. 후보와 identity의 toString은 해시/MDN을 출력하지 않는다.
조회 시각은 기존 발급과 동일한 UTC Instant→DATETIME 변환을 사용한다.
허용 구간은 issuedAt <= now < expiresAt다. SQL BINARY 비교로 collation의 대소문자/공백 동등 처리를 피한다.
인증 실패는 DEVICE_AUTHENTICATION_FAILED(15004, HTTP 401 매핑) 하나로 표현하고 입력값을 오류에 포함하지 않는다.
DB 장애는 401로 위장하지 않고 기존 서버 오류 처리로 전파한다.

## 5. 검증 결과

| 명령/실험 | 결과 | 증거 |
| --- | --- | --- |
| ./gradlew test --tests '*DeviceCredentialIntegrationTest' --tests '*DeviceAuthenticationServiceTest' --console=plain (최초) | unit 3개 통과, MySQL suite 초기화 실패: Docker daemon 미실행 | 최초 Gradle 출력 |
| 동일 명령 (Docker Desktop 실행 후) | 25개 통과, 실패·오류·skipped 0 / 47초 | build/test-results/test/TEST-*.xml |
| ./gradlew test --console=plain | 406개 통과, 실패·오류·skipped 0 / 2분 2초 | build/test-results/test/TEST-*.xml |

`git diff --check`도 통과했다. 기존 JVM class-sharing 경고는 남아 있다.
After: 신규 unit 3개와 MySQL 통합 8개를 추가해 기존 395개에서 406개로 증가했다.

Before는 코드 확인이며 기존 공격 성공 실험이 아니다. HTTP 수집 endpoint와 FE/Emulator를 변경하지 않으므로
해당 브라우저/클라이언트 suite는 이번 검증에 포함하지 않는다.

## 6. 실패 사례와 남은 위험

Docker 미실행으로 첫 integration 초기화가 실패했다. Docker Desktop을 실행해 같은 검증을 재시도해 통과했다.
이 검증기는 아직 LogController/Spring Security에 연결되지 않았다. GPS/Power/Geofence 수집 API는 여전히 인증을 강제하지 않는다.
identity는 조회 순간의 snapshot이며 재사용 가능한 인증 token이나 서명된 메시지가 아니다.
다음 단계는 HTTP에서 이 경계를 호출하고 GPS 메시지에 서버 identity를 보존하며 소비 transaction에서
현재 연결 세대를 재검증하는 것이다. 동기 Power/Geofence도 인증 후 변경 경쟁과 쓰기 경계를 함께 정해야 한다.
키 교체/폐기 후 이미 접수된 메시지를 처리할지에 대한 정책은 아직 결정하지 않았다.
활성 상태 복원·직접 SQL을 통한 ABA·과거 소속 이력은 CHANGE-035의 한계가 유지된다.
HTTPS, Emulator 비밀 주입, replay/nonce, request size/rate 제한, 배포 전환은 미완료다.
인증 조회 부하, 동시 변경 부하, 운영/실장치 결과를 측정하지 않았다.

## 7. 학습 기록

DeviceAuthenticationService → repository 단일 JOIN → DeviceIdentity → DeviceCredentialIntegrationTest 순서로 읽는다.
학습할 개념: 인증과 인가, locator와 신뢰된 identity, statement snapshot과 TOCTOU,
상수 시간 해시 비교의 한계, DB collation, 이벤트에 소속 세대를 담는 이유.
실습: 발급한 키로 authenticate를 호출하고 차량 A→B→A 후 실패, 재발급 후 revision=2 반환을 재현한다.
그 다음 인증 성공 직후 소속이 바뀌면 비동기 소비가 왜 별도 확인을 해야 하는지 설명한다.

## 8. 예상 면접 질문

1. ACTIVE 상태인데 왜 별도 인증 검증기가 필요한가요?
   - 메타데이터 상태는 원문 키 보유의 증거가 아니다. 소속과 credential도 같은 DB snapshot에서 읽어야 한다.
2. 단일 query면 이후 저장까지 안전한가요?
   - 아니다. 조회 뒤 재연결될 수 있다. 비동기 메시지에는 당시 identity를 담고 저장 시 현재 세대와 비교해야 한다.
3. 왜 MDN을 SQL 기본 문자열 비교에 맡기지 않나요?
   - collation이 대소문자/후행공백을 같게 볼 수 있다. 인증 식별자는 Java와 DB의 exact 비교 의미를 맞춘다.
4. 잘못된 키와 DB 장애를 왜 구분하나요?
   - 전자는 인증 거부이고 후자는 서버 가용성 실패다. DB 장애를 401로 바꾸면 클라이언트와 운영 진단을 오도한다.

## 9. AI 활용과 사람의 검증

사용자가 남은 작업 확인과 다음 단계 구현을 위임했다. AI가 저장소 상태 확인, 단일 snapshot 설계,
코드/테스트/문서 초안을 작성하고 검증했다. 상태 조회 재사용과 비동기 구간 lock 유지 제안을 배제했다.
사람의 독립 실습·코드 이해·면접 설명은 확인하지 않았다. 자동 검증과 사용자 학습 완료를 구분한다.
