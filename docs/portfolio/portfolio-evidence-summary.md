# Thisway 취업 포트폴리오 제출 요약

2026-09-08. 추가 제품 기능 개발을 보류하고 저장 신뢰성·권한·측정 근거를 마무리한다. 원래 팀 프로젝트의 개인 담당은 Vehicle/VehicleModel·Statistics/Batch이며, 이후 작업은 AI 지원 개인 현대화다. [Git/PR 기여 근거](original-contributions.md)를 함께 제시한다.

## 핵심 주장과 실제 증거

1. **broker 접수와 DB 저장 완료를 구분했다.** 인증 후 publisher 진입→queue→consumer→transaction commit의 지연을 계측했다. 중복은 소비 시도로 따로 해석하며, 미측정을0ms로 처리하지 않는다.
2. **고정 synthetic workload를 세 번 재현했다.** 매회20/40/80RPS 각100초,8장치, fresh MySQL/RabbitMQ/Redis. 정상 요청42,000건 오류0이며, 매회14,096개 고유 DB행을 확인했다. 별도의 중복24건씩은 행을 추가하지 않았다.
3. **개선 후보를 실행계획과 같은 데이터로 비교했다.** 최근100개 query 형태에서 복합 인덱스가 Sort를 없앴으며,60쌍 교대 측정의 중앙값이58.9~63.3% 감소했다. 운영 migration·전체 API 개선율로 주장하지 않는다.
4. **관측 도구의 실제 연결을 검증했다.** 전용키 Prometheus 수집, Grafana 화면, 로컬 Alertmanager 발생·해제 수신, 실제 HTTP 로그의 Loki correlation 검색과 읽기/쓰기 거부를 재현했다.

## 반복 측정 결과

아래는 세 실행의 각 phase p95/p99 범위다. 모든 표본을 합친 하나의 percentile이 아니다. 측정 코드 기준선은 `69f34cd`다.

| 부하 | 정상 요청 합계 | 저장 p95 | 저장 p99 | 오류/미측정 |
| --- | ---: | ---: | ---: | ---: |
| 20RPS | 6,000 | 11~12ms | 14~16ms | 0/0 |
| 40RPS | 12,000 | 7~8ms | 9~10ms | 0/0 |
| 80RPS | 24,000 | 5~6ms | 7~8ms | 0/0 |

consumer 정지 후 복구 구간의 저장 p95는2.321~3.322초, 중복 소비 p95는4~7ms였다. 정지80건 동안 DB행은 증가하지 않았고, 재시작 후 매회 기대 행 수까지 수렴했다. 큐 재시작→전체 회복의 polling 측정은약182~183ms이며 개별 저장 지연과 다른 수치다. DLQ는 각0건이었다.

부하가 큰 뒤 phase의 수치가 낮은 것은 JVM·캐시 예열과 실행 순서 영향을 포함한다. 부하가 높아지면 빨라진다는 결론이나 최대 처리량을 말하지 않는다.5분씩3회는15분 연속 단일 DB soak와도 다르며 장기 메모리 누수·운영 SLA를 증명하지 않는다. 호스트 자원/백그라운드 활동은 독점하지 않았다.

[원시 측정과 요약](../experiments/2026-09-08-portfolio-completion/load-summary.json), [수신·검색·권한 증거](../experiments/2026-09-08-portfolio-completion/operations/result.json), [재현 절차](../runbooks/portfolio-completion-evidence.md), [실패·설계·면접 기록](work-logs/2026-09-08-portfolio-completion.md).

## CI와 리뷰

BE 전체 회귀475개·기존 fleet 통합1개·최종 관측성 통합1개 모두 성공(4분36초). Grafana20패널과 저장 지연/미측정 화면을 실제 Chromium에서 확인했다. [최종 검증 기록](../experiments/2026-09-08-portfolio-completion/final/validation.json), [실제 대시보드](../experiments/2026-09-08-portfolio-completion/final/dashboard-commit.png). 실제 DB rollback이 성공 지연에 섞이지 않는 negative test를 포함한다.

FE 로컬: npm ci→단위14→build→Chromium fixture26→production8 성공(세션/404/접근성 보강 포함). Emulator: Python3.11.16에서32개 성공. 새 workflow는 PR/read-only 권한/timeout을 사용한다. 로컬 commit은 FE `438f03a`, Emulator `fa7242f`다.

기존 공개 draft PR은 [BE235](https://github.com/Kernel360/KBE5-Thisway-BE/pull/235), [FE84](https://github.com/Kernel360/KBE5-Thisway-FE/pull/84), [Emulator22](https://github.com/Kernel360/KBE5-Thisway-Emulator/pull/22)이다. 이번 변경은 기존 PR에 fast-forward로 반영했고 BE cdd5493·FE438f03a·Emulatorfa7242f의 원격 CI 성공을 확인했다. [CI와 프론트 감사 기록](work-logs/2026-09-08-remote-ci-frontend-readiness.md). [BE PR 준비본](review/portfolio-observability-pr.md)과 공개 PR diff로 검토할 수 있다.

## 면접에서 보여 줄 흐름

회사별 차량 관제 문제와 기존 개인 담당을 소개한다. 이어 “HTTP200인데 consumer가 멈춰 DB에 저장되지 않는 경우”를 보여 준다. 저장 지연/큐/DB행을 함께 읽고, 복구 후 중복행이 생기지 않는 이유를 설명한다. 마지막으로 수치의 조건·한계와 선택하지 않은 설계를 말한다.

운영에서 별도인 항목: TLS/ingress/secret 회전, 지속 로그 collector와24시간 경과 후 물리 삭제, 실제 알림 수신자·HA·용량 검증, merge·배포. 사용자의 독립 이해·설명은 작업 로그의 질문을 직접 재현하며 확인해야 한다. 이 문서는 사용자에게 없는 운영 경험을 대신 주장하지 않는다.

프론트 범위: 회사 관리자 중심 시연과 전체 역할 제품 완성도를 구분한다. ADMIN 대시보드/통계·회사 설정 및 MEMBER6개 업무 화면은 placeholder다. 미구현 화면을 완료 기능으로 제출하지 않는다.
