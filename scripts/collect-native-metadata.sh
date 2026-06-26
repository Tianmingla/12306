#!/bin/bash
# ================================================================
# GraalVM Native Image 元数据收集脚本（Tracing Agent）
#
# 使用方式:
#   1. 确保 MySQL (3306)、Redis (6379)、Nacos (8848) 已启动
#   2. 运行: bash scripts/collect-native-metadata.sh user-service 8084
#   3. 手动调用该服务的所有 API 接口
#   4. Ctrl+C 停止后，检查生成的文件
#   5. 确认无误后提交到 git
# ================================================================

set -e

SERVICE=${1:-user-service}
PORT=${2:-8084}
AGENT_DIR="Services/${SERVICE}/src/main/resources/META-INF/native-image"

if [ ! -d "Services/${SERVICE}" ]; then
    echo "Error: Service '${SERVICE}' not found"
    exit 1
fi

echo "=== Step 1: Build JAR ==="
mvn clean package -pl Services/${SERVICE} -am -DskipTests -q

JAR=$(ls Services/${SERVICE}/target/${SERVICE}*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
    echo "Error: JAR not found for ${SERVICE}"
    exit 1
fi
echo "Built: $JAR"

echo ""
echo "=== Step 2: Clean old agent configs ==="
rm -rf "${AGENT_DIR}"/*.json 2>/dev/null || true
mkdir -p "${AGENT_DIR}"
echo "Cleaned: ${AGENT_DIR}"

echo ""
echo "=== Step 3: Start app with native-image-agent ==="
echo "Agent output dir: ${AGENT_DIR}"
echo "App port: ${PORT}"
echo ""
echo ">>> IMPORTANT: Now manually call ALL API endpoints to exercise code paths <<<"
echo ">>> Then press Ctrl+C to stop the app <<<"
echo ""

# Run with native-image-agent
# config-merge-dir: merge all configs into this directory
java \
  -agentlib:native-image-agent=config-merge-dir="${AGENT_DIR}" \
  -jar "$JAR" \
  --server.port="${PORT}"

echo ""
echo "=== Step 4: Agent configs generated ==="
echo "Files in ${AGENT_DIR}:"
ls -la "${AGENT_DIR}/"
echo ""
echo "Done! Review the generated JSON files, then:"
echo "  1. Commit them: git add ${AGENT_DIR} && git commit"
echo "  2. Build native image: docker build -f Services/${SERVICE}/Dockerfile.native -t 12306-${SERVICE}:native ."
