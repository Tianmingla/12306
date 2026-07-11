#!/bin/bash
# ============================================================
# 12306 一键压测脚本
# 用法：
#   ./run_perf_test.sh mixed       # 运行 7:2:1 混合场景
#   ./run_perf_test.sh hot-cache   # 运行热点缓存测试
#   ./run_perf_test.sh oversell    # 运行超卖验证
#   ./run_perf_test.sh waitlist    # 运行候补队列压力测试
#   ./run_perf_test.sh staircase   # 运行阶梯加压
#   ./run_perf_test.sh all         # 运行所有场景
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESULT_DIR="$SCRIPT_DIR/results"
mkdir -p "$RESULT_DIR"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# 默认参数
HOST=${PERF_HOST:-localhost}
PORT=${PERF_PORT:-8080}
THREADS=${PERF_THREADS:-200}
DURATION=${PERF_DURATION:-300}

# 检查 JMeter
JMETER=${JMETER_CMD:-jmeter}
if ! command -v "$JMETER" &> /dev/null; then
    echo "❌ JMeter 未找到，请安装或设置 JMETER_CMD 环境变量"
    echo "   下载: https://jmeter.apache.org/download_jmeter.cgi"
    exit 1
fi

echo "===== 12306 压测 ====="
echo "网关: http://${HOST}:${PORT}"
echo "线程数: ${THREADS}"
echo "持续时间: ${DURATION}s"
echo ""

# ---- 数据准备 ----
prepare_data() {
    echo "--- 数据准备 ---"
    if [ ! -f "$SCRIPT_DIR/test_users_2000.csv" ]; then
        echo "生成 2000 用户测试数据..."
        python3 "$SCRIPT_DIR/prepare_perf_data.py" --count 2000 --skip-login --output-dir "$SCRIPT_DIR"
    else
        echo "测试用户数据已存在，跳过"
    fi
}

# ---- 运行 JMeter 测试 ----
run_jmeter() {
    local test_name=$1
    local jmx_file=$2
    local jtl_output="$RESULT_DIR/${test_name}_${TIMESTAMP}.jtl"
    local log_output="$RESULT_DIR/${test_name}_${TIMESTAMP}.log"

    echo ""
    echo "===== 运行: ${test_name} ====="
    echo "JMX: ${jmx_file}"
    echo "结果: ${jtl_output}"

    "$JMETER" -n -t "$SCRIPT_DIR/$jmx_file" \
        -l "$jtl_output" \
        -j "$log_output" \
        -JHOST="$HOST" \
        -JPORT="$PORT" \
        -JTHREADS="$THREADS" \
        -JDURATION="$DURATION" \
        -JRAMP_UP=30

    echo "✅ ${test_name} 完成"

    # 自动分析结果
    if [ -f "$jtl_output" ]; then
        python3 "$SCRIPT_DIR/analyze_results.py" "$jtl_output" \
            --output "$RESULT_DIR/${test_name}_${TIMESTAMP}_report.md"
        echo "📊 报告: $RESULT_DIR/${test_name}_${TIMESTAMP}_report.md"
    fi
}

# ---- 场景选择 ----
case "${1:-mixed}" in
    mixed)
        prepare_data
        echo "启动监控..."
        "$SCRIPT_DIR/monitor.sh" "$DURATION" &
        MONITOR_PID=$!
        run_jmeter "mixed_721" "12306_mixed_721.jmx"
        kill $MONITOR_PID 2>/dev/null || true
        ;;
    hot-cache)
        prepare_data
        run_jmeter "hot_cache" "12306_special_hot_cache.jmx"
        ;;
    oversell)
        prepare_data
        run_jmeter "oversell" "12306_special_inventory_critical.jmx"
        # 额外分析超卖
        JTL="$RESULT_DIR/oversell_${TIMESTAMP}.jtl"
        if [ -f "$JTL" ]; then
            python3 "$SCRIPT_DIR/analyze_results.py" "$JTL" --oversell \
                --output "$RESULT_DIR/oversell_${TIMESTAMP}_report.md"
        fi
        ;;
    waitlist)
        prepare_data
        run_jmeter "waitlist" "12306_special_waitlist.jmx"
        ;;
    staircase)
        prepare_data
        echo ""
        echo "===== 阶梯加压测试 ====="
        for t in 50 100 200 500; do
            echo "--- 并发数: ${t} ---"
            THREADS=$t DURATION=120 run_jmeter "staircase_${t}" "12306_mixed_721.jmx"
        done
        ;;
    all)
        prepare_data
        echo "启动全局监控..."
        TOTAL_DURATION=$((DURATION * 4 + 600))
        "$SCRIPT_DIR/monitor.sh" "$TOTAL_DURATION" &
        MONITOR_PID=$!

        run_jmeter "mixed_721" "12306_mixed_721.jmx"
        run_jmeter "hot_cache" "12306_special_hot_cache.jmx"
        run_jmeter "oversell" "12306_special_inventory_critical.jmx"
        run_jmeter "waitlist" "12306_special_waitlist.jmx"

        kill $MONITOR_PID 2>/dev/null || true
        ;;
    *)
        echo "未知场景: $1"
        echo "可用场景: mixed, hot-cache, oversell, waitlist, staircase, all"
        exit 1
        ;;
esac

echo ""
echo "===== 压测完成 ====="
echo "结果目录: $RESULT_DIR"
