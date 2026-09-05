# CHANGE-011: SSE 헤더 인증과 resource ownership

## 메타데이터

- 날짜: 2026-09-05
- 작업자: Shin Dong Jun + AI assistant
- BE 기준: codex/portfolio-foundation@60736e7
- FE 작업 브랜치: codex/sse-header-auth (기존 develop에서 분기)
- 상태: P0-02D-2 Verified. 전체 P0-02D는 미완료.

## 문제와 출처

기존 팀 FE의 TripDetailViewPage와 CompanyCarDetailPage는 EventSource URL에 JWT를 붙였다. BE는 query token을 controller에서 검증했고 차량 및 운행 SSE의 tenant ownership은 확인하지 않았다. 회사 SSE도 별도의 query token을 파싱했다. 이번 작업은 기존 팀 기능에 대한 개인 현대화이며 원래 팀 기여는 original-contributions.md와 구분한다.

## Acceptance criteria

- [x] FE의 두 SSE 호출 URL에 JWT를 넣지 않는다.
- [x] 세 SSE route 모두 Bearer 헤더 인증과 MEMBER/COMPANY_ADMIN/COMPANY_CHEF role을 요구한다.
- [x] query token만 있거나 변조된 헤더 token이면 401, ADMIN role은 403.
- [x] 다른 회사 차량/운행 SSE는 404이며 GPS 조회와 live 연결 등록을 하지 않는다.
- [x] 자기 회사 차량·완료 운행 구독 성공 및 회사 stream의 principal companyId 사용 확인.
- [x] FE fragmented UTF-8, event 이름/여러 줄 data, 오류와 abort 검증.

## 선택지와 판단

EventSource + 일회용 ticket은 브라우저 자동 재연결을 활용하지만 ticket 저장소/만료/소비 원자성/재발급이 추가된다. Cookie 인증은 현재 localStorage JWT 구조에서 CSRF와 credential 정책을 함께 바꿔야 한다. 이번에는 기존 Bearer 필터를 재사용할 수 있는 fetch 스트림을 채택했다. FE는 원래 onerror에서 연결을 닫았으므로 이번 adapter도 자동 재시도를 하지 않는다. Last-Event-ID/replay는 구현하지 않았다.

## 실행 흐름과 변경

FE localStorage token → fetch Authorization header → JwtAuthenticationFilter → route role 검사 → 인증 principal의 companyId로 기존 scoped repository 조회 → 허용된 데이터 조회 → emitter 생성 → 이름을 보존한 SSE 수신.

차량 stream은 `findByIdAndCompanyIdAndActiveTrue`를 직접 사용해 MEMBER에게 조회를 허용한다. 기존 VehicleService의 관리자 전용 detail helper를 재사용하지 않았다. 운행 좌표 조회는 기존 `findByIdAndVehicleCompanyIdAndActiveTrue`로 제한한다. 회사 stream은 principal companyId와 service 인자를 대조한다. 초기 조회 오류로 orphan 연결이 생기지 않게 초기 DB/GPS 조회를 registry 등록보다 앞에 배치했다. 이 순서의 snapshot/live 사이 이벤트 누락 가능성은 후속 항목이다.

기존 query-token controller parsing은 제거했다. FE는 `openAuthenticatedEventStream` adapter로 두 화면의 listener/close 형태를 유지한다. 상대 `/api/` URL만 허용하고 query 및 redirect를 차단한다. parser 입력은 event 기준 1 MiB 문자 상한을 둔다. 이는 서버의 live queue 상한을 보장하는 것은 아니다.

## 검증과 실패

- BE 좁은 `./gradlew test --tests '*TripLogTenantIntegrationTest' --tests '*SseConnectionTest' --console=plain`: 22건 통과. 이후 변조 token 3건 추가.
- BE `./gradlew test --console=plain`: 238건 통과, 실패/오류/skip 0, 20초. 기존 224건에서 14건 추가.
- FE `node --test src/utils/authenticatedEventStream.test.mjs`: 6/6 통과.
- FE 첫 `npm run build`: vite 미설치로 실패. `npm ci --ignore-scripts --no-audit --no-fund`로 기존 lockfile의 209개 package 설치 후 재실행 성공(5.04초).
- FE build에는 500 kB 초과 chunk 경고가 있다. 이번 성과로 bundle 최적화를 주장하지 않는다.
- 양쪽 `git diff --check` 통과. FE 제품 코드에서 `new EventSource`, `?token=` 사용 없음(거부 테스트 문자열 제외).

검증은 실제 JWT/MockMvc + H2 및 mock GPS service 조합이다. MySQL 실행 계획, 실서버 브라우저/지도/프록시 SSE, remote CI는 미검증이다. 기존 scoped query를 재사용했으며 schema 변경은 없다.

## 배포 계약과 남은 위험

BE와 FE를 함께 반영해야 한다. 구 FE query-token 호출은 새 BE에서 401이다. 구 controller를 되살리는 호환 fallback은 두지 않았다. 배포 시 FE/BE 버전 조합을 확인한다. Emulator 저장소의 telemetry 계약은 이번 작업 대상이 아니다.

서버 initial/live buffer 무제한, flush event 이름 `live_gps`, flush/live 순서 경쟁, stream ticket/자동 재연결 미구현, 여러 탭의 동일 username key 교체, process-local registry의 다중 instance 전달, 기존 FE 콘솔의 원시 좌표 출력과 localStorage XSS 위험은 남아 있다. 연결 후 JWT 만료 즉시 종료/권한 회수도 구현하지 않았다. 과거 운행 stream은 전체 좌표를 먼저 메모리에 읽고 chunking하며 `@Async` self-invocation도 남아 있다. P0-02D 전체 안전성이나 운영 안정성 완료를 주장하지 않는다.

## 학습과 면접

1. JWT를 헤더로 옮기면 모든 보안 문제가 해결되는가?
   - URL 노출 경로는 줄지만 localStorage/XSS, 로그의 header 수집, token 만료와 권한 회수는 별도 문제다.
2. role 검사가 있는데 왜 repository ownership을 확인하는가?
   - MEMBER는 구독 종류를 허용할 뿐 임의 vehicle/tripId를 소유하지 않는다. companyId와 resourceId를 함께 제한한다.
3. EventSource 대신 fetch를 선택한 비용은?
   - custom parser, AbortController, 응답 status/Content-Type 처리, 명시적인 reconnect/replay 정책이 필요하다.
4. 왜 성공 및 거부 테스트를 모두 두는가?
   - 전부 차단하는 잘못된 구현도 negative test만 통과한다. 자기 회사 MEMBER 구독과 foreign tenant 차단을 함께 확인한다.

실습: 헤더를 제거한 요청, ADMIN token, 다른 회사 ID를 각각 보내 실패가 filter/role/repository 중 어디서 발생하는지 설명한다. 사용자의 실습 완료는 아직 확인하지 않았다.

## AI 활용

AI가 FE/BE 호출 경로 분석, 대안 비교, adapter·인가·테스트·문서 초안을 작성하고 검증했다. 선택 이유는 기존 JWT 필터 재사용과 현재 FE 오류 시 close 동작의 유지다. 사람의 학습·브라우저 검증을 수행했다고 기록하지 않는다. 자동화 테스트 범위 밖 운영 조건과 남은 설계 문제를 위에 명시했다.
