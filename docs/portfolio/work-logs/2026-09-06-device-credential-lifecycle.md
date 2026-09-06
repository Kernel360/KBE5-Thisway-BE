# CHANGE-034: 회사 범위 장치 credential 관리 기반

## 메타데이터

- 날짜: 2026-09-06
- 작업자: 사용자 + AI 구현/검증 보조
- 브랜치/기준 커밋: codex/statistics-batch-restart / 5a61a59
- 관련: P1-01C, ADR-011 / 원격 PR 없음
- 상태: Verified — 로컬 키 관리 기반, 수집 인증 적용은 미완료

## 1. 문제와 근거

AuthAuthorizationPolicy의 GPS/Power/Geofence는 permitAll이고 장치별 키 관리 경로가 없었다.
Emulator BaseLogHandler도 use_auth=False다. 입력 검증이나 중복 방지가 발신자의 신원을 보장하지 않는다.
이번에는 키 관리 기반만 추가하며 공개 수집 API의 인증은 아직 바꾸지 않는다.
원 팀 구현 및 개인 Vehicle/Statistics/Batch 기여와 별개의 개인 현대화다. 원 RFP는 미보유 상태다.

## 2. Acceptance criteria

- [x] 회사 관리자만 현재 소유 장치 키를 발급·교체·폐기하고 비밀 없는 상태를 조회한다.
- [x] 원문은 발급 응답에만 제공하고 DB는 해시·만료·연결 snapshot만 저장한다.
- [x] role/tenant 거부, 동시 교체, 감사 실패 rollback, hash 충돌을 실제 MySQL에서 검증한다.
- [x] V7 기존 장치를 변경/자동 발급하지 않고 V8로 전환한다.
- [x] 한계·운영 절차·학습·면접 기록을 남긴다.

## 3. 선택지와 결정

| 선택지 | 장점 | 단점·결정 |
| --- | --- | --- |
| 사람 JWT 재사용 | 기존 경로 활용 | 사람/장치 권한과 수명 결합, 제외 |
| 난수 opaque key + 해시 | 장치별 교체·폐기와 원문 비저장 | provisioning 필요, 이번 기반에 채택 |
| HMAC/mTLS | 서명 또는 상호 인증 | 키/인증서 운영·클라이언트 변경 범위 커 후속 검토 |

32바이트 난수, 30일 만료, 장치당 하나의 현재 키, 관리자 전용 정책을 선택했다.
30일은 새 설계 선택이며 운영 측정값이 아니다. 상세 판단은 [ADR-011](../../adr/011-device-credential-lifecycle.md).

## 4. 구현과 실행 흐름

DeviceCredentialController → Service의 현재 DB 회원 권한 검사 → tenant 포함 소유권 잠금 조회 →
난수 생성/해시 교체 → 감사 저장 → transaction commit → 원문 응답 순서다.
V8의 credential/event 테이블은 JdbcTemplate으로 관리하며 JPA validate만으로 보장하지 않는다.
DELETE는 해시를 지우고 실제 변경 시에만 감사한다. GET은 비밀 없는 메타데이터만 반환한다.
키 DTO의 toString은 REDACTED이며 성공 HTTP 응답은 no-store다.
실행 안내: [runbook](../../runbooks/device-credential-lifecycle.md).

## 5. 검증 결과

| 명령/실험 | 결과 | 증거 |
| --- | --- | --- |
| ./gradlew test --tests '*DeviceCredentialIntegrationTest' --console=plain | 최초 10개 실패, 15초 | Member fixture phone='000'이 기존 도메인 검증 위반 |
| ./gradlew test --tests '*DeviceCredentialIntegrationTest' --tests '*LegacySchemaPreflightIntegrationTest' --console=plain | 통과, 22초 | credential 10개 및 V8 기존 데이터 보존 |
| ./gradlew test --console=plain | 391개 통과 / 실패 0 / 오류 0 / skipped 0, 1분 37초 | build/test-results/test/TEST-*.xml |
| git diff --check | 통과 | 로컬 변경 공백 검사 |

Before: 관리 경로 없음. After: 실제 MySQL의 키 교체·4개 동시 발급·감사 rollback·역할/회사 경계 검증.
추측 저항성이나 운영 성능을 부하 측정으로 입증한 것은 아니다.
SSE 별도 브라우저 task, FE/Emulator suite와 실제 배포는 이번에 실행하지 않았다. 해당 저장소 코드는 변경하지 않았다.

## 6. 실패와 남은 위험

최초 테스트의 잘못된 phone fixture만 정상 형식으로 고쳤으며 도메인 검증을 완화하지 않았다.
발급 응답 유실 시 DB commit 여부를 응답만으로 알 수 없어 재발급해야 한다.
마지막 동시 발급만 남고 감사 실패 시 원래 키가 유지됨을 테스트했다.
JOIN 잠금의 회사 행 경합, inactive 장치의 폐기 불가, 연결 변경 후 복원 시 ACTIVE 재등장,
비동기 GPS 소비까지의 identity 전달은 해결되지 않았다. 상세 한계는 ADR에 명시했다.
수집 API 인증·재전송 정책·size/rate 제한·Emulator 비밀 주입이 후속 작업이다.

## 7. 학습 기록

- 읽기 순서: Controller → Service.administrator/issue → Repository.findOwned/replace/audit → V8 → IntegrationTest.
- 공부: authentication과 authorization, tenant predicate, JWT claim과 현재 권한 차이,
  난수 토큰과 비밀번호 해시 차이, transaction 원자성, 행 잠금, unique 충돌, 응답 유실.
- 직접 실습: 감사 insert를 실패시키고 원래 해시/감사 개수가 유지되는 이유를 설명한다.
- 경계 질문: HTTP 인증 직후 차량 연결이 바뀌면 비동기 consumer는 어디에 저장해야 하는가?

## 8. 예상 면접 질문

1. 왜 관리자 role만 검사하면 안 되나요?
   - 다른 회사 장치 ID 접근을 막아야 한다. 현재 회원 회사와 실제 자원 소유권을 SQL 조건에 포함한다.
2. 왜 원문 대신 SHA-256인가요? 비밀번호에도 같은 방식을 쓰나요?
   - 256bit 난수 토큰의 원문 복구를 피하려는 선택이다. 사람이 고르는 비밀번호에는 별도의 느린 password hash가 필요하다.
3. 키 교체와 감사 저장을 왜 같은 transaction에 묶었나요?
   - 교체됐는데 행위 기록이 없는 부분 성공을 막는다. 감사 실패 후 이전 키 유지 테스트로 확인했다.
4. 동시에 발급하거나 응답을 잃으면 어떻게 되나요?
   - 잠금으로 직렬화하되 마지막 commit만 남는다. 원문은 복구할 수 없으므로 응답 유실 시 재발급한다.
5. 이제 위조 GPS 요청을 막나요?
   - 아니다. 키 관리만 구현했다. 세 수집 API와 비동기 저장의 identity/revision 검증까지 연결해야 한다.

## 9. AI 활용과 사람의 검증

AI가 대안·구현·migration·실패/동시성 테스트·문서 초안을 작성하고 자동 검증했다.
사용자는 다음 단계 진행을 위임했으며 세부 설계의 독립 이해/승인까지 확인된 것은 아니다.
AI는 사람 JWT 재사용과 수집 인증 전체 완료 주장을 배제했다.
사용자가 직접 확인할 항목은 위 코드 흐름, 실패 재현, 실제 관리 정책 선택과 면접 답변이다.
운영 장치 provisioning, HTTPS 배포, 실부하, 사용자의 독립 설명은 확인하지 못했다.
