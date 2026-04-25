#!/bin/bash

echo "========================================"
echo "  12306 RocketMQ Topic 创建脚本"
echo "  Linux / macOS 版本"
echo "========================================"
echo

# 设置 RocketMQ 地址
NAMESRV_ADDR="${1:-localhost:9876}"

echo "[信息] NameServer 地址: $NAMESRV_ADDR"
echo

# 检查 mqadmin 命令
if [ -n "$ROCKETMQ_HOME" ]; then
    MQADMIN="$ROCKETMQ_HOME/bin/mqadmin"
elif command -v mqadmin &> /dev/null; then
    MQADMIN="mqadmin"
else
    echo "[错误] 未找到 mqadmin 命令"
    echo "[提示] 请设置 ROCKETMQ_HOME 环境变量，例如:"
    echo "       export ROCKETMQ_HOME=/opt/rocketmq"
    echo "       或者将 RocketMQ bin 目录加入 PATH"
    exit 1
fi

echo "[信息] 使用命令: $MQADMIN"
echo

# Topic 列表
TOPICS=(
    "seat-selection-topic"
    "seat-selection-result-topic"
    "order-creation-topic"
    "order-creation-result-topic"
    "order-timeout-cancel-topic"
    "travel-reminder-topic"
    "seat-release-topic"
    "waitlist-fulfill-topic"
)

# Topic 描述
DESCS=(
    "选座请求 Topic"
    "选座结果返回 Topic"
    "订单创建请求 Topic"
    "订单创建结果回调 Topic"
    "订单超时取消 Topic（延迟消息）"
    "出行提醒 Topic（延迟消息）"
    "座位释放 Topic"
    "候补兑现触发 Topic"
)

echo "========================================"
echo "  开始创建 Topics..."
echo "========================================"
echo

SUCCESS_COUNT=0
FAIL_COUNT=0

for i in "${!TOPICS[@]}"; do
    TOPIC="${TOPICS[$i]}"
    DESC="${DESCS[$i]}"

    echo "[创建] $TOPIC - $DESC"

    if "$MQADMIN" updateTopic -n "$NAMESRV_ADDR" -t "$TOPIC" -c DefaultCluster -r 4 -w 4 > /dev/null 2>&1; then
        echo "[成功] $TOPIC 创建成功"
        ((SUCCESS_COUNT++))
    else
        echo "[失败] $TOPIC 创建失败或已存在"
        ((FAIL_COUNT++))
    fi
    echo
done

echo "========================================"
echo "  Topic 创建完成"
echo "========================================"
echo
echo "[统计] 成功: $SUCCESS_COUNT, 失败/已存在: $FAIL_COUNT"
echo

# 显示所有 Topic
echo "[信息] 显示当前所有 Topics:"
echo "----------------------------------------"
"$MQADMIN" topicList -n "$NAMESRV_ADDR" 2>/dev/null
echo "----------------------------------------"
