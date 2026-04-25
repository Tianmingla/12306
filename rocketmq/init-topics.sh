#!/bin/bash

# RocketMQ Topic 初始化脚本
# 在 Broker 启动后自动创建所需的 Topic

echo "========================================"
echo "  12306 RocketMQ Topic 初始化"
echo "========================================"
echo

# 等待 Broker 启动
echo "[等待] Broker 启动中..."
sleep 10

# NameServer 地址
NAMESRV_ADDR="rocketmq-namesrv:9876"

# Topic 列表（格式：topic_name:read_queue_num:write_queue_num）
TOPICS=(
    "seat-selection-topic:4:4"
    "seat-selection-result-topic:4:4"
    "order-creation-topic:4:4"
    "order-creation-result-topic:4:4"
    "order-timeout-cancel-topic:4:4"
    "travel-reminder-topic:4:4"
    "seat-release-topic:4:4"
    "waitlist-fulfill-topic:4:4"
)

echo "[信息] NameServer: $NAMESRV_ADDR"
echo "[信息] 开始创建 Topics..."
echo

for TOPIC_CONFIG in "${TOPICS[@]}"; do
    IFS=':' read -r TOPIC READ_QUEUE WRITE_QUEUE <<< "$TOPIC_CONFIG"

    echo -n "[创建] $TOPIC ... "

    if ./mqadmin updateTopic \
        -n "$NAMESRV_ADDR" \
        -t "$TOPIC" \
        -c DefaultCluster \
        -r "$READ_QUEUE" \
        -w "$WRITE_QUEUE" \
        > /dev/null 2>&1; then
        echo "成功"
    else
        echo "已存在或失败"
    fi
done

echo
echo "[完成] Topic 初始化完成"
echo

# 显示 Topic 列表
echo "[信息] 当前 Topic 列表:"
./mqadmin topicList -n "$NAMESRV_ADDR" 2>/dev/null | grep -E "seat|order|travel|waitlist"
