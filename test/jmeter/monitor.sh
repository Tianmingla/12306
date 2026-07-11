#!/bin/bash
# ============================================================
# 12306 压测监控脚本
# 用途：在压测期间采集 Redis、MySQL、RocketMQ、JVM 指标
# 用法：./monitor.sh [duration_seconds]
# 输出：test/jmeter/monitor/ 目录下的指标文件
# ============================================================

DURATION=${1:-300}
OUTPUT_DIR="$(dirname "$0")/monitor"
mkdir -p "$OUTPUT_DIR"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REDIS_LOG="$OUTPUT_DIR/redis_metrics_${TIMESTAMP}.log"
MYSQL_LOG="$OUTPUT_DIR/mysql_metrics_${TIMESTAMP}.log"
JVM_LOG="$OUTPUT_DIR/jvm_metrics_${TIMESTAMP}.log"
SUMMARY_LOG="$OUTPUT_DIR/summary_${TIMESTAMP}.log"

echo "===== 12306 压测监控启动 ====="
echo "采集时长: ${DURATION}s"
echo "输出目录: ${OUTPUT_DIR}"
echo ""

# ---- Redis 监控 ----
monitor_redis() {
    echo "[Redis] 开始监控..." >&2
    echo "timestamp,connected_clients,used_memory_mb,keyspace_hits,keyspace_misses,hit_rate,instantaneous_ops_per_sec,slowlog_len" > "$REDIS_LOG"

    END_TIME=$(($(date +%s) + DURATION))
    while [ $(date +%s) -lt $END_TIME ]; do
        INFO=$(redis-cli INFO stats 2>/dev/null)
        MEMORY=$(redis-cli INFO memory 2>/dev/null)
        CLIENTS=$(redis-cli INFO clients 2>/dev/null)
        SLOWLOG=$(redis-cli SLOWLOG LEN 2>/dev/null)

        TS=$(date +%s)
        CONN=$(echo "$CLIENTS" | grep "^connected_clients:" | cut -d: -f2 | tr -d '\r')
        MEM=$(echo "$MEMORY" | grep "^used_memory:" | cut -d: -f2 | tr -d '\r\n' | awk '{printf "%.1f", $1/1024/1024}')
        HITS=$(echo "$INFO" | grep "^keyspace_hits:" | cut -d: -f2 | tr -d '\r')
        MISSES=$(echo "$INFO" | grep "^keyspace_misses:" | cut -d: -f2 | tr -d '\r')
        OPS=$(echo "$INFO" | grep "^instantaneous_ops_per_sec:" | cut -d: -f2 | tr -d '\r')
        SLOW=$(echo "$SLOWLOG" | tr -d '\r\n' | awk '{print $1}')

        # 计算命中率
        TOTAL=$((HITS + MISSES))
        if [ $TOTAL -gt 0 ]; then
            HIT_RATE=$(awk "BEGIN {printf \"%.2f\", $HITS/$TOTAL*100}")
        else
            HIT_RATE="0.00"
        fi

        echo "${TS},${CONN:-0},${MEM:-0},${HITS:-0},${MISSES:-0},${HIT_RATE},${OPS:-0},${SLOW:-0}" >> "$REDIS_LOG"
        sleep 5
    done
    echo "[Redis] 监控结束" >&2
}

# ---- MySQL 监控 ----
monitor_mysql() {
    echo "[MySQL] 开始监控..." >&2
    echo "timestamp,threads_connected,threads_running,queries_per_sec,slow_queries,innodb_row_lock_waits" > "$MYSQL_LOG"

    END_TIME=$(($(date +%s) + DURATION))
    PREV_QUERIES=0
    while [ $(date +%s) -lt $END_TIME ]; do
        TS=$(date +%s)
        METRICS=$(mysql -u root -p123456 -N -e "
            SELECT
                (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Threads_connected'),
                (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Threads_running'),
                (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Queries'),
                (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Slow_queries'),
                (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Innodb_row_lock_waits')
        " my12306 2>/dev/null)

        if [ -n "$METRICS" ]; then
            CONN=$(echo "$METRICS" | awk '{print $1}')
            RUNNING=$(echo "$METRICS" | awk '{print $2}')
            QUERIES=$(echo "$METRICS" | awk '{print $3}')
            SLOW=$(echo "$METRICS" | awk '{print $4}')
            LOCK_WAITS=$(echo "$METRICS" | awk '{print $5}')

            if [ $PREV_QUERIES -gt 0 ]; then
                QPS=$((QUERIES - PREV_QUERIES))
            else
                QPS=0
            fi
            PREV_QUERIES=$QUERIES

            echo "${TS},${CONN},${RUNNING},${QPS},${SLOW},${LOCK_WAITS}" >> "$MYSQL_LOG"
        fi
        sleep 5
    done
    echo "[MySQL] 监控结束" >&2
}

# ---- JVM 监控 (通过 jstat) ----
monitor_jvm() {
    echo "[JVM] 开始监控..." >&2
    echo "timestamp,service,ygc_count,ygc_time_ms,fgc_count,fgc_time_ms,old_used_mb,old_max_mb" > "$JVM_LOG"

    # 找到 Java 进程
    PIDS=$(jps -l 2>/dev/null | grep -E "(ticket|seat|order|user|gateway|admin)-service" | awk '{print $1}')

    if [ -z "$PIDS" ]; then
        echo "[JVM] 未找到 Java 进程，跳过 JVM 监控" >&2
        return
    fi

    END_TIME=$(($(date +%s) + DURATION))
    while [ $(date +%s) -lt $END_TIME ]; do
        TS=$(date +%s)
        for PID_LINE in $(jps -l 2>/dev/null | grep -E "(ticket|seat|order|user|gateway|admin)-service"); do
            PID=$(echo "$PID_LINE" | awk '{print $1}')
            SERVICE=$(echo "$PID_LINE" | awk '{print $2}' | sed 's/.*\///' | sed 's/-service.*//')

            GC_INFO=$(jstat -gc "$PID" 2>/dev/null | tail -1)
            if [ -n "$GC_INFO" ]; then
                YGC=$(echo "$GC_INFO" | awk '{print $13}')
                YGCT=$(echo "$GC_INFO" | awk '{printf "%.0f", $14*1000}')
                FGC=$(echo "$GC_INFO" | awk '{print $15}')
                FGCT=$(echo "$GC_INFO" | awk '{printf "%.0f", $16*1000}')
                OLD_USED=$(echo "$GC_INFO" | awk '{printf "%.0f", $7/1024}')
                OLD_MAX=$(echo "$GC_INFO" | awk '{printf "%.0f", $8/1024}')
                echo "${TS},${SERVICE},${YGC},${YGCT},${FGC},${FGCT},${OLD_USED},${OLD_MAX}" >> "$JVM_LOG"
            fi
        done
        sleep 10
    done
    echo "[JVM] 监控结束" >&2
}

# ---- 汇总 ----
print_summary() {
    echo "" > "$SUMMARY_LOG"
    echo "===== 12306 压测监控汇总 =====" >> "$SUMMARY_LOG"
    echo "采集时间: $(date '+%Y-%m-%d %H:%M:%S')" >> "$SUMMARY_LOG"
    echo "采集时长: ${DURATION}s" >> "$SUMMARY_LOG"
    echo "" >> "$SUMMARY_LOG"

    if [ -f "$REDIS_LOG" ] && [ $(wc -l < "$REDIS_LOG") -gt 1 ]; then
        echo "--- Redis ---" >> "$SUMMARY_LOG"
        echo "峰值连接数: $(tail -n +2 "$REDIS_LOG" | awk -F, 'NR==1{max=$2} $2>max{max=$2} END{print max}')" >> "$SUMMARY_LOG"
        echo "峰值内存: $(tail -n +2 "$REDIS_LOG" | awk -F, 'NR==1{max=$3} $3>max{max=$3} END{printf "%.1f MB", max}')" >> "$SUMMARY_LOG"
        echo "平均命中率: $(tail -n +2 "$REDIS_LOG" | awk -F, '{sum+=$6; n++} END{printf "%.2f%%", sum/n}')" >> "$SUMMARY_LOG"
        echo "峰值 OPS: $(tail -n +2 "$REDIS_LOG" | awk -F, 'NR==1{max=$7} $7>max{max=$7} END{print max}')" >> "$SUMMARY_LOG"
        echo "" >> "$SUMMARY_LOG"
    fi

    if [ -f "$MYSQL_LOG" ] && [ $(wc -l < "$MYSQL_LOG") -gt 1 ]; then
        echo "--- MySQL ---" >> "$SUMMARY_LOG"
        echo "峰值连接数: $(tail -n +2 "$MYSQL_LOG" | awk -F, 'NR==1{max=$2} $2>max{max=$2} END{print max}')" >> "$SUMMARY_LOG"
        echo "峰值 QPS: $(tail -n +2 "$MYSQL_LOG" | awk -F, 'NR==1{max=$4} $4>max{max=$4} END{print max}')" >> "$SUMMARY_LOG"
        echo "慢查询数: $(tail -n +2 "$MYSQL_LOG" | awk -F, '{sum+=$5} END{print sum}')" >> "$SUMMARY_LOG"
        echo "行锁等待: $(tail -n +2 "$MYSQL_LOG" | awk -F, '{sum+=$6} END{print sum}')" >> "$SUMMARY_LOG"
        echo "" >> "$SUMMARY_LOG"
    fi

    cat "$SUMMARY_LOG"
}

# ---- 启动所有监控 ----
monitor_redis &
REDIS_PID=$!
monitor_mysql &
MYSQL_PID=$!
monitor_jvm &
JVM_PID=$!

echo "监控进程: Redis=$REDIS_PID, MySQL=$MYSQL_PID, JVM=$JVM_PID"
echo "按 Ctrl+C 提前结束，或等待 ${DURATION}s 后自动结束"
echo ""

# 等待指定时长
sleep "$DURATION"

# 打印汇总
print_summary

echo ""
echo "===== 监控数据文件 ====="
echo "Redis:  $REDIS_LOG"
echo "MySQL:  $MYSQL_LOG"
echo "JVM:    $JVM_LOG"
echo "Summary: $SUMMARY_LOG"
