# Vehicle Power watermark V6 적용 점검

실제 운영 DB에는 적용하지 않았다. 아래는 승인된 대상의 복원본에서 먼저 수행할 절차다.

1. 대상·백업·복원본과 Flyway 이력/checksum을 확인한다. history 없는 legacy DB는
   [기존 schema preflight](legacy-schema-preflight.md)를 따르고 자동 baseline/repair하지 않는다.
2. V5 복원본에서 차량 수와 상태별 수를 저장한다. 원시 좌표·장치 식별자를 공개 기록에 남기지 않는다.
3. V6 적용 후 기존 차량 수/상태가 동일하고 추가 컬럼은 `DATETIME(6) NULL`인지 확인한다.
   기존 `updated_at`이나 MAX 원본 시간으로 자동 채우지 않는다.
4. 신규/기존 Power writer를 동시에 운용하지 않는다. 구버전 writer는 watermark 없이
   상태를 덮으므로 수집 정지·요청 재시도 계획을 포함한 승인된 전환이 필요하다.
5. 격리된 검증 차량에 12시 ON → 11시 OFF를 보내 현재 ON/12시 기준이 유지되는지 확인한다.
   실제 차량 데이터에 테스트 이벤트를 삽입하지 않는다.
6. 구버전 애플리케이션으로 rollback하면 컬럼은 남겨도 되지만 순서 보장은 사라진다.
   컬럼 DROP이나 watermark NULL 초기화로 자동 복구하지 않는다. 오염 의심 차량은
   수집을 격리하고 신뢰 가능한 원본/장치 시계부터 조사한 후 별도 승인된 보정을 설계한다.

읽기 전용 집계 예시(대상 schema 선택 후):

```sql
SELECT power_on, COUNT(*) FROM vehicle GROUP BY power_on;
SELECT COUNT(*) AS total, COUNT(last_power_event_time) AS initialized FROM vehicle;
SELECT column_type, is_nullable FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='vehicle' AND column_name='last_power_event_time';
```

두 번째·세 번째 조회는 V6 적용 후 실행한다. 테스트는 폐기 가능한 MySQL 8.0.40
컨테이너에서 legacy 행 보존·동시 요청·rollback을 검증하며, 운영 DDL 잠금 시간이나
배포 무중단을 검증한 것은 아니다.
