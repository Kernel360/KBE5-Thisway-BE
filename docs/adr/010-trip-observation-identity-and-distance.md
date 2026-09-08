# ADR-010: Trip 관측 식별과 거리의 분리

- 날짜: 2026-09-06
- 결정: 신규 운행 처리에 채택. 과거 기록 자동 변환은 하지 않음.

## 식별과 상태표

현재 Emulator가 OFF에 담아 보내는 ON 시각을 이용해 `vehicleId + onTime`을 운행 키로 삼는다.
장치 세션/sequence가 없어 같은 초의 별개 운행은 구분하지 못한다. 원 RFP 규칙을 복원한 것은 아니다.
새 기록에만 `identityStartTime=startTime`을 저장하고 `(vehicle_id, identity_start_time)` unique를 둔다.
기존 행은 NULL identity로 보존한다. 같은 vehicle/startTime의 legacy 행이나 여러 행이 발견되면
409로 중단하며 임의 병합/삭제/승격하지 않는다. 이것은 운영 기존 운행에 대한 수동 검토 gate다.

| 기존 상태 | 입력 | 결과 |
| --- | --- | --- |
| 없음 | ON | 시작 계기값/좌표를 가진 미종료 운행 |
| 없음 | OFF | 종료 관측만 가진 종료 운행, 거리 확인 불가 |
| ON만 | 동일 ON | no-op |
| ON만 | OFF | 종료 정보 추가, 두 계기값으로 거리 계산 |
| OFF만 | ON | 시작 정보 보충, 종료 유지, 거리 계산 |
| 종료 | 동일 ON/OFF | no-op, 주소 보정 재발행 없음 |
| 관측된 같은 종류 | 시각/계기값/좌표가 다른 재수신 | 409, 원래 관측 유지 |
| legacy/모호한 중복 | ON/OFF | 409, 검토 전 변경 금지 |

동일성은 Trip에 저장하는 관측 필드(ON 시각, OFF 시각, 해당 계기값/좌표)에 한한다.
gcd 등 저장하지 않는 Power 메타데이터 전체의 멱등성은 아니다. raw power_log 재전송 행은 보존된다.
충돌 응답 시 core transaction 전체가 rollback되어 그 충돌 원본도 DB에 남지 않는다.

## 거리

`startOdometer`, `endOdometer`, `distanceMeters`를 분리한다. 둘 다 관측했고 end>=start일 때만
distance=end-start다. ON 없음/미종료/계기값 감소/legacy는 distance NULL과 이유를 반환한다.
기존 `total_trip_meter`는 혼합 의미의 과거 증거로 보존하며 새 모델의 읽기/계산에는 사용하지 않는다.
신규 행은 그 legacy 필드에 호환용 0을 저장한다. 이는 실제 거리 0이라는 의미가 아니다.
실시간 GPS도 `gps.sum - startOdometer`이며 시작값 없음/역전/GPS 없음에는 확인 불가다.

## 트랜잭션과 제약

Power orchestrator와 직접 Trip service 진입 모두 active Vehicle의 write lock을 획득하고
READ_COMMITTED에서 같은 운행을 조회한다. 신규 insert의 unique는 잠금을 우회하는 중복 쓰기의
최종 방어선이다. 상태 전이는 domain, 400/409 변환은 application service에 둔다.
주소는 실제로 새 관측을 채운 경우만 AFTER_COMMIT 보정한다. 외부 HTTP는 잠금 transaction 밖이다.

전체 vehicle/startTime unique는 legacy 중복을 보존할 수 없으므로 선택하지 않았다.
선조회만으로 방어하는 안은 동시성 불변식에 부족하다. legacy 값을 계기값으로 추정하는 안은
OFF-only의 0과 이미 혼합 저장한 숫자를 구별할 근거가 없어 거절했다.

## API/배포 비용과 한계

거리 응답 `tripMeter`는 nullable이며 완료/legacy 이유인 `distanceStatus`를 추가한다.
CurrentDrivingInfo에는 startOdometer를 추가해 FE가 SSE 누적값을 거리로 착각하지 않게 한다.
FE는 null을 0.0km로 표시하지 않는다. 서버/FE 호환 배포가 필요하다.
장치 인증, 미래 시각/clock skew, sequence, 과거 기록의 승인된 보정, 통계 자동 backfill은 별도다.
OFF의 onTime이 Emulator의 임의 fallback이면 다른 운행으로 식별될 수 있다.
