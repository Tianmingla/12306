FROM python:3.11-slim

WORKDIR /app

# 安装依赖
RUN pip install --no-cache-dir pymysql pyyaml

# 复制所有脚本和数据文件
COPY DataScript/ /app/

# 构建时解压 data.zip（如果 data/ 目录不存在）
RUN if [ ! -d /app/data ] && [ -f /app/data.zip ]; then \
      cd /app && unzip -q data.zip && rm data.zip; \
    fi

# 默认环境变量（可通过 docker-compose 环境变量覆盖）
ENV MYSQL_HOST=mysql \
    MYSQL_PORT=3306 \
    MYSQL_USER=root \
    MYSQL_PASSWORD=123456 \
    MYSQL_DATABASE=my12306

ENTRYPOINT ["python", "run_all.py", "--config", "import.yml"]
