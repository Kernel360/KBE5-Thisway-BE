# 장치 credential 관리 기반 실행 안내

2026-09-06 / CHANGE-034. **GPS·Power·Geofence 인증 강제는 아직 적용하지 않았다.**
현재 공개 수집 API를 운영 인터넷에 노출해도 된다는 안내가 아니다.

## 준비와 API

기존 운영 DB에는 백업·복구 리허설과 기존 Flyway 전환 절차를 먼저 적용한다.
V8은 기존 emulator 정보 변경이나 자동 키 발급 없이 새 테이블을 생성한다.
MySQL에서 마이그레이션을 적용해야 하며 H2 자동 JPA 스키마만으로 이 API를 사용할 수 없다.

현재 회사의 COMPANY_ADMIN 회원 JWT로 `/api/emulators/{id}/device-key`를 호출한다.
차량과 회사가 active이고 같은 회사 소유여야 한다.

| 방법 | 성공 응답 | 의미 |
| --- | --- | --- |
| POST | 200, key·expiresAt | 최초 발급 또는 기존 키 즉시 교체 |
| GET | 200, state·issuedAt·expiresAt | 비밀 없는 상태 조회 |
| DELETE | 204 | 해시 폐기, 반복 호출은 추가 감사 없이 성공 |

성공 응답은 Cache-Control: no-store다. 키는 원문 조회/복구 API가 없으며 POST 응답에만 포함된다.
JWT와 키를 URL, 로그, 터미널 기록, 저장소, 스크린샷에 남기지 않는다. HTTPS와 보호된 비밀 저장/주입 경로가 필요하다.
no-store와 해시 저장은 클라이언트 탈취나 네트워크 노출 자체를 해결하지 않는다.
발급 응답을 잃었다면 재발급한다. 동시 발급은 마지막 commit만 유효한 저장 상태가 되므로 한 작업자가 순차 관리한다.
30일 만료이며 자동 갱신·교체 유예는 없다.

## 상태와 장애

- NOT_ISSUED: 발급 이력 행 없음.
- REVOKED: 현재 해시 폐기됨.
- BINDING_CHANGED: 차량/회사/MDN snapshot 불일치. 재발급 전에 연결을 확인한다.
- EXPIRED: 만료 시각 도달. 재발급 필요.
- ACTIVE: 관리 메타데이터 기준이며 수집 인증 보장은 아님.
- 401/403: 로그인 또는 현재 관리자 권한 확인. 404: 소유권·active 차량·존재 여부 확인.
- 500: 원문이나 JWT를 첨부하지 말고 요청 시각/장치 ID와 서버 오류를 조사한다.
  감사 실패 시 키 교체는 rollback한다. 네트워크 응답 유실은 이와 달리 commit됐을 수 있다.

감사 확인은 device_credential_event의 장치/회사/행위자 ID·event_type·occurred_at만 사용한다.
키/해시를 덤프해 공유하지 않는다. 장치 삭제 후 감사는 남지만 법적 보존·변조 방지 수준은 미검증이다.

## 다음 단계 전환 gate

현재 Emulator는 use_auth=False로 전송하며 이 변경에서 수정하지 않았다. 임의의 인증 header를 추가해
보호가 시작됐다고 판단하지 않는다. FE 관리 화면도 아직 없다.
재연결 revision/폐기, inactive 폐기 정책, 비동기 GPS identity 전달, 세 수집 API 거부 테스트,
Emulator 비밀 주입·로그 차단, 기존 장치 provisioning과 rollback 순서를 함께 마련한 다음 인증을 강제한다.
자세한 한계는 [ADR-011](../adr/011-device-credential-lifecycle.md)을 따른다.
