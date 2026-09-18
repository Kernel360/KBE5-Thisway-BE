# 1) 공식 Prometheus 이미지를 베이스로
FROM prom/prometheus:v3.4.1

# 2) root 권한으로 설정 파일 복사
USER root
COPY prometheus.prod.yml /etc/prometheus/prometheus.yml

# 3) (선택) Alert 룰 파일이 있다면 복사
# COPY rules/ /etc/prometheus/rules/

# 4) 퍼미션 조정 및 EFS 마운트 포인트 준비
RUN chown -R nobody:nobody /etc/prometheus \
    && chmod -R 755 /etc/prometheus \
    && mkdir -p /prometheus \
    && chown -R nobody:nobody /prometheus

# 6) Prometheus 웹 UI 포트 노출
EXPOSE 9090

# 7) 기본 엔트리포인트와 커맨드 설정
ENTRYPOINT ["/bin/prometheus"]
CMD [\
    "--config.file=/etc/prometheus/prometheus.yml",\
    "--storage.tsdb.path=/prometheus",\
    "--web.listen-address=0.0.0.0:9090",\
    "--web.enable-lifecycle"\
    ]
