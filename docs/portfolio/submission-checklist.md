# 포트폴리오 제출/시연 체크리스트

기준: 2026-09-07. 기업용 차량 운영 관리 서비스. 현행 코드의 시연 절차이며 원 RFP 요구사항을 복원한 문서가 아니다.

## 기술 시연: 원래 개인 담당과 연결하기

1. 서비스 소개: 기업 담당자의 차량 현황 → 운행 기록 → 회사 통계.
2. 기존 개인 Vehicle/Statistics·Batch 역할과 원 팀의 RabbitMQ/SSE 구현을 분리한다.
3. 대표 개선: 첫 회사 통계 저장 후 두 번째 회사 실패 → Job FAILED지만 첫 회사는 보존
   → 동일 날짜 재시작 → 첫 회사 계산 생략 → 두 번째 회사 완료.
4. 보여 줄 핵심 파일: StatisticsCompanyWorker, V4 migration, StatisticsBatchIntegrationTest.
5. 설계 비용: REQUIRES_NEW connection 여유, 회사 row lock 범위, 최초 회사 목록 미동결. 차량별 집계 범위는 CHANGE-040의 최초 fleet ID snapshot을 별도로 사용한다.

```bash
./gradlew test --tests '*StatisticsBatchIntegrationTest' --console=plain
```

추가 사례 중 하나를 선택한다.

- 외부 API 실패: 좌표/운행은 commit, 주소는 보류 → 내부 retry로 보정.
- 누적 거리: 1000 → 1500 → 중복 1500 → 지연 1200에서 표시값 1500 유지.
- publisher: broker ack라도 mandatory return이 있으면 실패. broker 접수와 DB 저장 완료는 다름.

```bash
./gradlew test --tests '*TripAddressEnrichmentIntegrationTest' --console=plain
./gradlew test --tests '*GpsLogProducer*' --console=plain
```

Gradle 검증은 한 저장소에서 순서대로 실행한다. 전체 test와 별도 sseBrowserTest를 동시에
실행하다 artifact cache lock timeout을 경험했으므로 겹치지 않는다.

## 실제 브라우저 fixture로 검증한 화면 흐름

- [x] 격리 MySQL/Redis/RabbitMQ 및 회사2개·차량·회원 fixture. 운영/개인 위치 데이터 미사용.
- [x] 실제 App에서 회사 A 로그인 → 차량 목록/상세 → 운행 상세2시간/500m → 통계120분/GPS2의 실제 API 연결.
- [x] 회사 B 로그인 후 A의 차량/운행 HTTP와 SSE 모두404. 외부 API 호출·page error·예상외5xx0. 캡처의 계정영역은 마스킹.
- [x] 화면에서 거리·통계 날짜·GPS 관측 수·fleet 기준 안내를 검증. 지도 SDK는 fixture에서 격리하며 실제 지도 API 결과는 미검증.
- [ ] 사용자가 주소 미확정 `-`, 통계 날짜와 GPS 미수신의 의미를 직접 설명한다.
- [x] CHANGE-045 원시 API 응답 요약과 캡처5장 보존. source는 HEAD+dirty 상태이며 CHANGE-049 통합 기록으로 구분.

[CHANGE-045](work-logs/2026-09-07-fleet-browser-workflow.md)는 실제 FE 업무 요청을 격리 Boot에 연결한다. 일반 routing/SSE fixture 성공과 구분한다. 별도 Python client 계약도 실제 HTTP→MySQL로 확인하며, 외부서비스/파일 부작용이 있는 Emulator 수동 main/test_emulator 전체 프로그램을 실행한 결과로 대신 말하지 않는다.

## 제출 전 확인

- [ ] 프로젝트 최종 정리 때 대표 어필 항목마다 코드·실행 증거·개인 기여·한계를 대조한다.
- [ ] [코드 읽기와 최종 면접 검토 계획](learning-review-plan.md)에 따라 사용자가 핵심 흐름을 읽고 구두 설명한다.
- [ ] 사용자가 핵심 테스트 하나를 직접 실행하고 질문 3개 이상을 AI 없이 설명.
- [ ] 원래 팀 기여/개인 기여/AI 지원 이후 개선을 분리.
- [ ] Git 원격 push/PR/CI/merge 상태 확인. 로컬 commit은 원격 반영이 아니다.
- [ ] 실측하지 않은 p95·처리량·개선율·무유실·실제 기업 운영 성과 문구 제거.
- [ ] 장치 인증 연결(CHANGE-037) 이후에도 운영 전환·배포 보안 gate 통과 전에는 수집 API를 외부에 공개하지 않는다.

## 학습 완료 확인 질문

1. 첫 회사가 commit한 뒤 프로세스가 죽으면 무엇이 남는가? STARTED Job 복구는 무엇이 추가로 필요한가?
2. AFTER_COMMIT은 왜 자동 비동기가 아닌가? API 실패와 transaction rollback을 어떻게 분리했는가?
3. max 누적값만으로 왜 동시성 안전하지 않은가? 이미 잘못된 과거 값을 자동 정정하지 않은 이유는?
4. publisher confirm이 왔는데 DB가 장애라면 어떤 counter와 DLQ를 확인해야 하는가?
5. AI가 만든 코드를 본인 구현으로 설명할 때 무엇을 직접 검증했다고 말할 수 있는가?


## 최신 통합 증거

[CHANGE-049 통합 검토](work-logs/2026-09-07-local-completion-review.md)와 [현재 남은 항목](remaining-work.md)을 최종 제출 시점에 대조한다. 초기 작업 로그의 과거 테스트 수를 최신 결과로 소급 수정하지 않는다.
