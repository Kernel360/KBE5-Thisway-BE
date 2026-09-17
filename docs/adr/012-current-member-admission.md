# ADR-012: JWT 요청의 현재 회원·회사 상태 재검증

- 날짜: 2026-09-12
- 상태: 채택 — 로컬 구현·실제 MySQL 회귀·전체 및 교차 저장소 검증 완료. 운영 적용과 영구 토큰 폐기는 별도 범위다.
- 관련: [작업 기록](../portfolio/work-logs/2026-09-12-member-account-admission.md), [FE 세션 처리](../portfolio/work-logs/2026-09-12-auth-session.md)

## 맥락

JWT의 서명과 만료를 확인해도 발급 이후의 회원 비활성화, 회사 비활성화, 역할·소속 변경은
알 수 없다. 이번 결정은 보호 요청이 들어올 때 토큰에 담긴 회원과 현재 DB의 회원이 같은
계정인지, 그 계정과 회사가 현재 유효한지 확인하는 admission 경계다.

email은 재사용될 수 있으므로 subject email만으로 현재 회원을 다시 찾으면 이전 계정의
토큰이 같은 email을 사용하는 다른 계정에 결합할 수 있다. 불변 회원 PK를 토큰에 포함하고
현재 DB 행과 비교해야 계정 identity와 변경 가능한 속성을 구분할 수 있다.

## 결정

새 JWT에는 양수 `memberId` claim을 필수로 추가한다. 기존 `sub` email, `companyId`, 단일 원소 `roles`
계약은 유지한다. `memberId`가 없는 구토큰이나 유효한 양수 회원 ID를 담지 않은 토큰은
401로 거부하고 재로그인으로 새 토큰을 발급한다. email만으로 구토큰을 보정하지 않는다.
장치 API key와 telemetry 인증 계약은 바꾸지 않는다.

JWT 인증 요청마다 `MemberReader` port를 통해 DB에서 다음 조건을 한 번의 조회로 확인한다.

```text
member.id = token.memberId
AND member.email = token.subject
AND member.company.id = token.companyId
AND member.role = token.role
AND member.active = true
AND company.active = true
```

`MemberReader` 구현체가 repository를 사용하며 JWT 필터는 repository를 직접 호출하지
않는다. 일치하는 현재 회원이 없으면 인증하지 않고 401로 거부한다. role·company claim을
현재 값으로 몰래 교체하여 기존 토큰을 계속 허용하지 않는다.

로그인도 활성 회원과 활성 회사를 함께 확인하는 fetch join 조회를 사용한다. 인증 후
`MemberDetails`에 `memberId`를 전달하고 `SecurityService.getCurrentMember()`는 그 ID와
회사 ID로 현재 회원을 조회한다. email 기반 재조회로 다른 회원에게 결합하지 않는다.

DB 조회 오류는 인증 거부와 구분한다. 현재 상태를 확인할 수 없으므로 요청은 허용하지
않되 기존의 고정된 500 응답을 유지한다. FE는 500에서 token을 지우지 않는다. 401은 현재
요청의 token이 현재 세션과 일치할 때만 정리하는 기존 FE 계약을 사용한다. DB 장애를
계정 삭제로 오인해 전체 사용자를 로그아웃시키지 않는다.

## 대안과 비용

| 대안 | 장점 | 비용·한계 | 결정 |
| --- | --- | --- | --- |
| JWT 서명·만료만 확인 | 인증마다 DB 조회가 없다 | 발급 이후 상태 변경이 만료까지 반영되지 않는다 | 제외 |
| 현재 회원 상태를 TTL cache로 조회 | 반복 DB 접근을 줄일 수 있다 | TTL 동안 비활성화·역할 변경 반영이 늦고 invalidation 경계가 추가된다 | 이번 범위에서 제외 |
| 요청마다 현재 DB 상태를 단일 조회로 확인 | 다음 인증 요청에 commit된 현재 상태를 반영하고 email 재사용 계정을 구분한다 | JWT 인증 요청마다 DB 조회 1회와 DB 가용성 의존성이 추가된다 | 채택 |
| `authRevision`·토큰 denylist·영구 폐기 도입 | 재활성화 후 과거 토큰 재허용, 개별 세션 폐기까지 별도로 설계할 수 있다 | mutation 경로·schema·보관·배포 정책까지 확대된다 | 별도 후속 설계 |

현재 상태 반영 지연을 피하는 것이 이번 우선순위다. 조회 1회 추가는 예상 비용이며 실제
처리량·p95 개선이나 허용 부하를 측정한 결과는 아니다. `getCurrentMember()`가 필요한
업무는 기존 업무 조회도 수행하므로 요청 전체가 항상 query 1개라는 뜻이 아니다.

## 동시성과 보장 범위

조회 시점의 현재 상태를 확인한다. 이미 admission을 통과한 요청이 처리되는 동안
계정이 비활성화돼도 해당 요청을 즉시 취소하거나 모든 DB write와 상태 변경을 직렬화하지
않는다. 다음 요청은 그 요청의 admission 조회에서 보이는 commit된 상태로 판정한다.

SSE 신규 연결과 재연결은 같은 인증 검사를 거친다. 이미 열린 SSE 연결을 회원·회사 상태
변경 순간 즉시 닫는 기능은 포함하지 않는다. 열린 연결의 강제 종료에는 별도 lifecycle
전파와 다중 인스턴스 경계가 필요하다.

이 결정은 영구 revocation이 아니다. 회원·회사·역할·소속을 변경했다가 원래 값으로
되돌리면, 만료 전 기존 토큰이 다시 모든 조건과 일치하여 허용될 수 있다. 이 ABA 한계는
`memberId`가 해결하는 email 재사용의 다른 계정 결합 문제와 다르다. 이를 막는
`authRevision`, 비밀번호 재설정 시 기존 토큰 폐기, migration은 이번 범위에 넣지 않는다.

## 검증과 전환

실제 MySQL 테스트로 허용 조합, 회원·회사 비활성화, role·company 불일치, email 재사용
계정의 PK 차이, `memberId` 누락·잘못된 값, 로그인 활성 회사 조건을 검증한다. DB 오류의
고정 500과 권한 없는 요청이 인증 상태를 만들지 않는 경계도 회귀에 포함한다.

구토큰은 배포 후 다음 보호 요청에서 401이므로 재로그인 전환이 필요하다. DB schema
변경과 장치 API key 교체는 없다. 구토큰 fallback을 추가해 계정 identity를 추정하지 않는다.
최종 테스트 명령·개수·실패·한계는 연결한 작업 기록에 확정하며, 검증 전 완료로 표시하지 않는다.
