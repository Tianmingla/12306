FROM nginx:alpine

# 安装 acme.sh 和 curl（默认已有）
RUN apk add --no-cache openssl curl && \
    curl https://get.acme.sh | sh -s email=tianminglalala@gmail.com

# 创建证书目录
RUN mkdir -p /etc/nginx/ssl

# 复制启动脚本
COPY ./nginx/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# 复制 Nginx 配置
COPY ./nginx/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80 443
ENTRYPOINT ["/entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]