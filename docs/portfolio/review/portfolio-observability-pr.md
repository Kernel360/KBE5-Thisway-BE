# PR 준비본: 저장 완료 지연과 재현 가능한 운영 증거

기존 HTTP 응답 시간은 broker 접수까지만 보여 주어 DB 저장 적체를 구분하기 어려웠다. 인증 후 publisher 진입부터 transactional service 반환까지의 지연을 별도 계측하고, 정상·consumer 정지·중복 소비의 원시 표본을 보존한다. metrics는 사람 권한과 분리된 전용 수집키를 요구한다.

고정 환경의 반복 부하와 같은 데이터에서의 조회 인덱스 후보 비교를 추가했다. 실제 Prometheus/Alertmanager의 로컬 수신·해제, Spring HTTP 로그의 Loki correlation 검색·읽기/쓰기 권한 분리를 재현할 수 있다. 실제 운영 SLA, 전체 API 개선율, 운영24시간 보존 삭제를 주장하지 않는다.

검증 명령과 최종 결과는 `docs/portfolio/portfolio-evidence-summary.md` 및 `docs/portfolio/work-logs/2026-09-08-portfolio-completion.md`를 참조한다. 원 팀 기여와 AI 지원 개인 현대화를 구분했다.

리뷰 순서: MetricsSecurityConfig의 chain/filter 경계 → GpsCommitLatency와 consumer transaction 경계 → FleetEvidenceIntegrationTest의 raw sample/동일 조건 → operations-evidence.py의 임시 환경과 접근 분리.

원격 반영 대상은 현재 로컬 `codex/observability-evidence`다. 기존 BE PR235는 다른 head인 `codex/statistics-batch-restart`이므로 새 변경이 포함돼 있다고 표시하지 않는다. 공개 push·PR 생성은 이 준비본과 로컬 commit을 검토한 뒤 별도 반영한다. FE84/Emulator22도 현재 원격 head에는 새 CI workflow가 없다.
