# 공식 Grafana 이미지 사용
FROM grafana/grafana:10.2.3

# root 권한으로 전환
USER root

# Prometheus 데이터소스 자동 설정 파일 복사
COPY ./provisioning/datasources/ /etc/grafana/provisioning/datasources/

# 권한 설정
RUN chown -R grafana:root /etc/grafana/provisioning

# Grafana 포트 노출
EXPOSE 3000

# 기본 엔트리포인트/CMD 사용 (grafana 이미지 기본값)
