#!/bin/sh
set -e

# 设置 acme.sh 目录
ACME_DIR="/root/.acme.sh"
NGINX_SSL_DIR="/etc/nginx/ssl"
DOMAIN="my12306.tianminglalala.top"
ADMIN_DOMAIN="admin.tianminglalala.top"

# 如果证书不存在，则签发
if [ ! -f "$NGINX_SSL_DIR/fullchain.pem" ]; then
    echo "申请 SSL 证书..."
    export CF_Token="${CF_Token:?请设置 CF_Token 环境变量}"
    # 只有当 CF_Zone_ID 变量不为空时，才执行 export
    if [ -n "$CF_Zone_ID" ]; then
        export CF_Zone_ID
    fi
    
    $ACME_DIR/acme.sh --issue --debug -d $DOMAIN -d $ADMIN_DOMAIN \
        --dns dns_cf \
        --key-file $NGINX_SSL_DIR/key.pem \
        --fullchain-file $NGINX_SSL_DIR/fullchain.pem \
        --reloadcmd "if [ -f /run/nginx.pid ]; then nginx -s reload; else echo 'Nginx not running, skipping reload.'; fi"
else
    echo "证书已存在，跳过申请。"
fi

# 启动 Nginx
exec "$@"