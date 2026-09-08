# CHANGE-035: 장치 재연결 revision과 비활성 차량 키 폐기

## 메타데이터

- 날짜: 2026-09-06
- 작업자: 사용자 + AI 구현/검증 보조
- 브랜치/기준: codex/statistics-batch-restart / a9f103a
- 관련: P1-01C, ADR-011 / 원격 PR 없음
- 상태: Verified — 로컬 관리 API 경계, 수집 인증은 미완료

## 1. 문제와 근거

CHANGE-034는 발급 당시 차량/회사/MDN만 비교하므로 A→B→A 변경 후 이전 키가 ACTIVE로 복원됐다.
또한 findOwned의 active 차량 조건 때문에 비활성 차량의 키를 관리자도 폐기하지 못했다.
GpsLogSaveService는 소비 시 MDN을 다시 조회하므로 비동기 identity 문제는 여전히 남아 있다.
이번 변경은 원 팀의 Emulator CRUD에 추가하는 개인 현대화이며 원래 개인 Vehicle/Statistics/Batch 기여와 구분한다.

## 2. Acceptance criteria

- [x] 차량/MDN 변경 후 원복해도 기존 키가 ACTIVE로 복원되지 않는다.
- [x] 동일값·펌웨어 수정은 revision을 바꾸지 않고 재발급은 현재 revision을 반영한다.
- [x] 동시에 재연결 두 건과 발급을 실행해도 revision 증가를 잃지 않는다.
- [x] 비활성 차량의 발급 거부, 소유 관리자 조회/폐기 허용, 타 회사 거부를 검증한다.
- [x] V8 데이터/해시 보존과 V9 revision 0 초기화를 실제 MySQL로 검증한다.

## 3. 선택지와 결정

변경 시 해시 삭제는 간단하지만 이후 비동기 메시지에 할당 세대를 표현하지 못한다.
assignmentRevision을 선택해 장치 연결 세대와 발급 당시 세대를 비교한다.
이는 해시를 제거하는 물리적 폐기가 아니라 논리적 무효화다. 수집 인증은 아직 적용하지 않았다.
JPA @Version은 일반 수정 충돌용이므로 연결에만 의미를 갖는 revision과 구분한다.
상세 선택·한계는 [ADR-011](../../adr/011-device-credential-lifecycle.md).

## 4. 구현과 실행 흐름

PATCH → 회사 범위 PESSIMISTIC_WRITE 조회 → 차량/MDN 실제 변경 시 Emulator.update에서 revision+1 → commit.
POST 키 발급 → 소유권 잠금 → 현재 revision을 키와 함께 저장 → 감사 → commit.
GET은 revision 또는 snapshot 불일치 시 BINDING_CHANGED이며 현재 inactive 차량은 INACTIVE다.
DELETE는 active 차량 조건 없이 소유권을 확인하고 기존 폐기/감사 transaction을 사용한다.
V9는 두 revision 컬럼만 추가한다. 원문 키나 기존 장치 데이터를 재작성하지 않는다.

## 5. 검증 결과

| 명령 | 결과 |
| --- | --- |
| ./gradlew test --tests '*DeviceCredentialIntegrationTest' --tests '*LegacySchemaPreflightIntegrationTest' --tests '*EmulatorTenantIntegrationTest' --console=plain | 29개 통과 / 실패·오류·skipped 0, 32초 |
| ./gradlew test --console=plain | 395개 통과 / 실패·오류·skipped 0, 1분 41초 |
| git diff --check | 통과 |

증거: build/test-results/test/TEST-*.xml. Before는 snapshot 원복 취약점의 코드 확인이며 공격 실험 결과가 아니다.
After: 실제 MySQL에서 원복 후 키 무효 상태, 재발급 후 ACTIVE, 3-thread 재연결/발급 시 revision=2를 검증했다.
FE/Emulator는 작업 트리가 깨끗하며 변경하지 않았다. 해당 suite와 별도 SSE 브라우저 테스트는 이번에 실행하지 않았다.

## 6. 실패·남은 위험

이번 대상/전체 테스트에서 실패는 없었다. 컴파일의 기존 unchecked 경고와 JVM class-sharing 경고는 남아 있다.
직접 SQL/bulk update는 domain revision을 우회한다. 과거 이력 복원, 차량 회사 이동, active 복원 시 영구 폐기,
재연결 감사 이력, 운영 부하와 교착 복구는 미완료다. 키 상태 조회를 실제 인가 결정으로 재사용하면 안 된다.
비동기 메시지에 emulatorId/vehicleId/companyId/assignmentRevision을 담고 소비 시 검증하는 것은 다음 단계다.
수집 요청 인증·Emulator 비밀 주입·재전송/size/rate 제한은 여전히 미완료다.

## 7. 학습 기록

EmulatorService.updateEmulator → Emulator.update → DeviceCredentialService.status/issue → V9 → 통합 테스트 순서로 읽는다.
학습할 개념은 ABA 문제(A→B→A), 도메인 revision과 @Version 차이, pessimistic lock,
발급과 변경의 직렬화, 논리적 무효화와 물리적 폐기의 차이다.
직접 실습: 차량을 두 번 바꿔 revision=2와 BINDING_CHANGED를 확인하고 재발급 후 ACTIVE를 설명한다.

## 8. 예상 면접 질문

1. 현재 차량 ID가 같으면 왜 이전 키를 허용하지 않나요?
   - 연결이 중간에 바뀌었을 수 있다. 현재값 비교만으로 이력을 구분하지 못해 단조 증가 revision을 비교한다.
2. 증가 연산만 넣으면 동시성이 안전한가요?
   - 아니다. 두 transaction이 같은 값을 읽을 수 있어 변경 전에 같은 장치 행을 잠근다. 두 변경과 발급을 함께 테스트한다.
3. 왜 비활성 차량에 폐기를 허용하나요?
   - 새 권한 발급과 기존 권한 제거는 다르다. 소유권은 유지하면서 제거 경로를 열어 둔다.
4. 이것으로 비동기 GPS 소유권도 해결됐나요?
   - 아니다. 현재 메시지는 연결 세대를 전달하지 않아 HTTP부터 저장까지 identity를 별도로 보존·검증해야 한다.

## 9. AI 활용과 사람의 검증

사용자는 다음 작업 진행을 위임했다. AI는 위험 분석, 대안 선택, 코드·migration·테스트·문서를 작성한다.
AI가 전체 장치 인증 완료 주장을 배제하고 이번 단위를 재연결 관리 경계로 제한했다.
사용자의 독립 코드 이해·실습은 아직 확인하지 않았다. 자동 테스트와 수동 학습 확인을 구분한다.
운영 시스템·실장치·실부하 결과는 확인하지 못했다.
