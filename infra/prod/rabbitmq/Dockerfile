# Dockerfile.rabbitmq
FROM rabbitmq:latest
# (만약 prometheus 플러그인도 같이 활성화하려면 아래처럼 추가)
RUN rabbitmq-plugins enable --offline rabbitmq_prometheus


# 포트 노출
EXPOSE 5672 15672 15692

# entrypoint & cmd는 공식 이미지가 이미 잘 설정해 두었으므로 건드릴 필요 없습니다.
