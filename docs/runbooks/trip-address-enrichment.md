# 운행 주소 보정: 내부 개발/운영 절차

2026-09-06. 자동 운영 복구 도구가 아니라 현재 코드의 제한된 재시도 절차다.

1. 읽기 전용 권한으로 아래 ID 목록을 조회한다. 원시 좌표를 티켓/일반 로그에 복사하지 않는다.

```sql
SELECT id,
       on_addr IS NULL AND on_latitude IS NOT NULL AND on_longitude IS NOT NULL AS needs_on,
       off_addr IS NULL AND off_latitude IS NOT NULL AND off_longitude IS NOT NULL AS needs_off
FROM trip_log
WHERE (on_addr IS NULL AND on_latitude IS NOT NULL AND on_longitude IS NOT NULL)
   OR (off_addr IS NULL AND off_latitude IS NOT NULL AND off_longitude IS NOT NULL)
ORDER BY id LIMIT 20;
```

2. Kakao credential/할당량/장애 상태를 담당자가 확인한다. 키나 좌표가 포함된 예외를 공개하지 않는다.
3. 로컬 재현/승인된 내부 실행에서 Spring proxy `TripAddressEnrichment.enrich(tripId, off)`를 호출한다.
   신규 HTTP 관리 API나 CLI는 제공하지 않는다. 임의 원격 실행/대량 재처리는 승인 없이 금지.
4. true는 조건부 주소 update 1행, false는 대상 없음/이미 처리/경쟁/실패 중 하나다.
   false를 일괄 성공으로 집계하지 않는다. ID 목록 재조회로 확인한다.
5. 대량 자동화 전에는 durable work queue, 시도 횟수, backoff, 실패 분류와 API quota를 추가한다.

주소 보정은 저장된 일별 통계를 자동 재계산하지 않는다. 주소별 조회는 현행 Trip 조회 기반이다.
위 절차는 운영 실행 완료 증거가 아니며 실제 배포 DB에는 이번에 접근하지 않았다.
