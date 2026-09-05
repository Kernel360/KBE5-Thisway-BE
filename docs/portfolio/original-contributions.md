# 원 팀 프로젝트 기여 범위

## 기록 목적

Thisway는 5인 팀 프로젝트다. 포트폴리오와 면접에서는 “프로젝트가 가진 기능”과 “내가 직접 설계·구현한 기능”을 구분해야 한다. 이 문서는 2026-09-05에 공개 GitHub PR과 로컬 Git author/blame을 대조한 결과다.

작성자 기준 merged PR 검색: [Shin-Dong-Jun의 merged PR](https://github.com/Kernel360/KBE5-Thisway-BE/pulls?q=is%3Apr+is%3Amerged+author%3AShin-Dong-Jun)

## 요약

- merged PR: 17개
- Vehicle/VehicleModel 관련: 14개
- Statistics/Spring Batch 관련: 3개
- PR diff 단순 합계: `+3,581/-627`, 변경 파일 합계 124개
- 로컬 email author 기준 commit: 77개, 그중 non-merge 60개
- 현재 `src/main`·`src/test`의 Java 파일 232개, 물리 줄 수 13,443줄(`wc -l`, 빈 줄·주석 포함) 중 동일 email blame: 1,778줄, 약 13.2%

줄 수와 commit 수는 소유권을 보조하는 자료이지 품질·성과 지표가 아니다. 이력과 설계 설명, 검증 결과를 함께 제시한다.

## Vehicle 및 VehicleModel

| PR | 범위 |
| --- | --- |
| [#27](https://github.com/Kernel360/KBE5-Thisway-BE/pull/27) | 차량 등록, 차량 번호 검증, 테스트 |
| [#35](https://github.com/Kernel360/KBE5-Thisway-BE/pull/35) | 차량 상세 조회 |
| [#39](https://github.com/Kernel360/KBE5-Thisway-BE/pull/39) | 등록 field와 Company 검증 |
| [#40](https://github.com/Kernel360/KBE5-Thisway-BE/pull/40) | 차량 삭제 |
| [#58](https://github.com/Kernel360/KBE5-Thisway-BE/pull/58) | 차량 목록 조회 |
| [#59](https://github.com/Kernel360/KBE5-Thisway-BE/pull/59) | 차량 부분 수정 |
| [#95](https://github.com/Kernel360/KBE5-Thisway-BE/pull/95) | Vehicle 인가와 DTO 정리 |
| [#120](https://github.com/Kernel360/KBE5-Thisway-BE/pull/120) | 응답과 mileage 정책 조정 |
| [#124](https://github.com/Kernel360/KBE5-Thisway-BE/pull/124) | VehicleModel 등록 |
| [#132](https://github.com/Kernel360/KBE5-Thisway-BE/pull/132) | VehicleModel 목록 |
| [#134](https://github.com/Kernel360/KBE5-Thisway-BE/pull/134) | 차량 등록 형식 변경 |
| [#148](https://github.com/Kernel360/KBE5-Thisway-BE/pull/148) | 차량 번호 예외 처리 |
| [#153](https://github.com/Kernel360/KBE5-Thisway-BE/pull/153) | 수정 시 VehicleModel 변경 |
| [#227](https://github.com/Kernel360/KBE5-Thisway-BE/pull/227) | 최신 차량 우선 정렬 |

대표 commit은 `dc04a74`(Vehicle entity/repository), `37148d0`(등록·검증·테스트), `cb44938`(Vehicle 인가), `628c7b1`(정렬)이다. 현재 [`VehicleService`](../../src/main/java/org/thisway/vehicle/application/VehicleService.java) 206 lines 중 132 lines가 동일 사용자 blame으로 남아 있다.

## Statistics 및 Spring Batch

| PR | 범위 |
| --- | --- |
| [#172](https://github.com/Kernel360/KBE5-Thisway-BE/pull/172) | 회사별 통계 집계·저장·조회 API |
| [#192](https://github.com/Kernel360/KBE5-Thisway-BE/pull/192) | 0% 시간대를 제외한 최소 시간 정책 |
| [#213](https://github.com/Kernel360/KBE5-Thisway-BE/pull/213) | Spring Batch 적용 |

대표 commit은 `6e875ca`(통계 API 시작), `159211e`(계산·저장·조회 책임 분리), `9aa35ff`(응답 구조), `6c48a3c`(review 반영), `9def764`(Batch 적용)이다.

특히 [#213](https://github.com/Kernel360/KBE5-Thisway-BE/pull/213)에는 Batch metadata와 테스트가 미완성이라는 당시 한계가 기록되어 있다. 이 영역을 restart/backfill/concurrency까지 직접 현대화하면 “기존 구현의 한계를 발견하고 운영 가능한 설계로 발전시킨 과정”을 정직하게 보여 줄 수 있다.

## 팀 시스템으로 표현할 영역

다음 기능은 프로젝트 경험과 연동 경험으로 설명할 수 있지만 원래 개인 구현으로 표현하지 않는다.

| 영역 | 주된 기존 기여 | 안전한 표현 |
| --- | --- | --- |
| Spring Security/JWT | 다른 팀원, [#65](https://github.com/Kernel360/KBE5-Thisway-BE/pull/65) | JWT/Security가 적용된 팀 시스템에서 Vehicle 인가를 연동했다. |
| RabbitMQ 수집 | 다른 팀원, [#160](https://github.com/Kernel360/KBE5-Thisway-BE/pull/160) | 기존 MQ pipeline을 분석하고 이후 개인 현대화에서 신뢰성을 강화했다. |
| GPS log와 bulk insert | 다른 팀원, [#36](https://github.com/Kernel360/KBE5-Thisway-BE/pull/36), [#215](https://github.com/Kernel360/KBE5-Thisway-BE/pull/215) | 팀 구현을 사용·연동했으며 새 개선만 개인 기여로 구분한다. |
| TripLog/SSE | 다른 팀원, [#180](https://github.com/Kernel360/KBE5-Thisway-BE/pull/180), [#212](https://github.com/Kernel360/KBE5-Thisway-BE/pull/212) | 팀 기능의 실행 흐름과 한계를 이해하고 개인 수정 diff를 별도로 제시한다. |
| CI/CD/Monitoring | 다른 팀원, [#118](https://github.com/Kernel360/KBE5-Thisway-BE/pull/118), [#171](https://github.com/Kernel360/KBE5-Thisway-BE/pull/171), [#202](https://github.com/Kernel360/KBE5-Thisway-BE/pull/202) | 팀 인프라 구성을 이해하고 이후 직접 검증·개선한 부분만 주장한다. |

## 현재 사용할 수 있는 소개 문장

> 5인 팀 차량 관제 프로젝트에서 차량·차종 관리 도메인과 회사별 운행 통계 및 일일 배치를 담당했습니다. 차량 등록·조회·수정·삭제, 멀티테넌트 접근 검증, 통계 집계와 사전 계산 구조를 17개 PR로 개발하고 리뷰를 반영했습니다.

다음 문장은 목표 작업이 실제 완료된 후에만 덧붙인다.

> 이후 개인 현대화에서 텔레메트리 중복·실패 처리와 Batch 재시작성을 강화하고 통합 테스트와 성능 지표로 검증했습니다.

## 금지할 과장

- “전체 백엔드를 설계·구현했다.”
- “RabbitMQ/SSE/CI/CD/모니터링을 내가 처음 구축했다.”
- 결과 파일 없이 “15,000대 처리”라고 주장한다.
- migration과 fresh DB 검증 없이 “데이터 정합성을 보장했다”고 쓴다.
- 실패·재시작 test 없이 “무중단·무유실”이라고 표현한다.
- AI API를 연결한 것만으로 “AI 기반 관제 시스템”이라고 부른다.
