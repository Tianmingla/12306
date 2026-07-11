# 12306 性能压测与高并发测试方案

## 一、压测目标与指标

### 1.1 核心性能指标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 查询接口 RT (avg) | < 500ms | 余票查询、车次搜索 |
| 购票接口 RT (P99) | < 1500ms | 含 Redis Lua 选座 + MQ 异步 |
| 持续 QPS | 100-200 | 混合场景下系统吞吐量 |
| 错误率 | < 0.1% | 含超时、5xx、业务异常 |
| 超卖 | 0 | 并发抢票下零超卖 |
| 消息丢失 | 0 | RocketMQ 端到端零丢失 |

### 1.2 简历表述模板

> "为验证高并发架构，使用 JMeter 进行全链路压测。准备 2000 个测试账号和真实线路数据，按 7:2:1 比例混合查询、下单和支付请求。在 100 QPS 持续压力下，查询接口平均 RT < 500ms，核心购票接口 P99 RT < 1.5s，错误率 < 0.1%。通过 Redis Lua 脚本 + MQ 削峰，实现 0 超卖和 0 消息丢失。"

---

## 二、压测工具选型

| 工具 | 适合场景 | 本项目用途 |
|------|---------|-----------|
| **JMeter** | GUI 操作、丰富插件、分布式压测 | ✅ 主力工具，完整购票场景 |
| **wrk/wrk2** | 轻量 HTTP 压测、延迟分布 | 快速单接口验证 |
| **Arthas** | 线上热更新、trace 方法耗时 | 定位瓶颈方法 |
| **async-profiler** | CPU/内存火焰图 | 定位热点代码 |

---

## 三、测试数据准备

### 3.1 数据文件清单

| 文件 | 说明 | 数量 |
|------|------|------|
| `test_users_2000.csv` | 测试用户手机号 + SMS 验证码 | 2000 |
| `test_routes.csv` | 车次搜索参数（出发站/到达站/日期/车次/座位类型） | 45 条（15 线路 × 3 日期） |
| `hot_route.csv` | 热点线路参数（用于缓存测试） | 1 条 |

### 3.2 用户池

- 手机号格式：`13800000001` ~ `13800002000`（共 2000 个）
- 登录方式：SMS 验证码（mock 模式，固定 `123456`）
- 每用户预添加 1-3 个乘车人

### 3.3 生成数据

```bash
# 方式1：仅生成 CSV（不登录，Token 在压测时获取）
python prepare_perf_data.py --count 2000 --skip-login

# 方式2：在线模式（登录获取 Token + 添加乘车人）
python prepare_perf_data.py --count 2000 --gateway http://localhost:8080

# 方式3：使用 shell 脚本（兼容老方式）
./prepare_test_data.sh 2000
```

### 3.4 路线数据

`test_routes.csv` 包含 15 条热门线路，每条线路 3 个出发日期（7/14/21 天后）：

| 线路 | 车次 | 座位类型 |
|------|------|---------|
| 北京南 → 上海虹桥 | G1, G3 | 二等座, 一等座 |
| 北京南 → 南京南 | G101, G103 | 二等座 |
| 上海虹桥 → 杭州东 | G7301, G7305 | 二等座, 一等座 |
| 广州南 → 长沙南 | G6002, G6006 | 二等座 |
| 深圳北 → 广州南 | G6202, G6210 | 一等座, 二等座 |
| 北京南 → 杭州东 | G39 | 二等座 |
| 北京南 → 济南西 | G107 | 二等座 |
| 成都东 → 重庆西 | G8605 | 二等座 |
| 武汉 → 长沙南 | G1021 | 二等座 |
| 西安北 → 郑州东 | G2002 | 二等座 |

---

## 四、压测场景设计

### 4.1 P0 — 混合场景（7:2:1）⭐ 核心场景

**测试计划**: `12306_mixed_721.jmx`

模拟真实用户行为分布：

| 操作类型 | 占比 | 线程数 (TOTAL=200) | 说明 |
|---------|------|-------------------|------|
| 余票查询 | 70% | 140 | 高频读接口，验证 Redis 缓存 |
| 购票下单 | 20% | 40 | 写接口，验证 Lua + MQ 链路 |
| 支付/取消 | 10% | 20 | 验证幂等性和状态流转 |

**流程**：
1. 每线程登录获取 Token（一次性）
2. 查询线程：循环搜索余票（200-800ms 思考时间）
3. 下单线程：搜索 → 购票 → (异步模式) 轮询结果
4. 支付线程：购票 → 支付 → 50% 概率取消

**运行命令**：
```bash
jmeter -n -t 12306_mixed_721.jmx \
  -l results/mixed_721.jtl \
  -JHOST=localhost -JPORT=8080 \
  -JTOTAL_THREADS=200 -JDURATION=300
```

### 4.2 P1 — 热点缓存测试

**测试计划**: `12306_special_hot_cache.jmx`

| 阶段 | 并发 | 时长 | 验证点 |
|------|------|------|--------|
| Phase 1: 冷启动 | 500 | 30s | 缓存击穿保护，首次查询走 DB |
| Phase 2: 热缓存 | 500 | 90s | 缓存命中，RT < 200ms |

所有 500 用户同时查询 **同一热门线路**（北京南→上海虹桥），验证：
- Redis 缓存命中率 > 95%
- 热缓存阶段 RT 显著低于冷启动
- 无缓存穿透（null 值保护）

### 4.3 P1 — 库存临界测试（超卖验证）⭐

**测试计划**: `12306_special_inventory_critical.jmx`

**前置条件**：某车次某日仅剩 **1 张票**

**操作**：50 个并发用户同时抢这张票

**验证项**：
1. ✅ 成功订单数 ≤ 1（不能超卖）
2. ✅ Redis 剩余余票 = 1 - 成功订单数
3. ✅ 无重复座位分配给不同用户
4. ✅ RocketMQ 仅产生 1 条有效购票消息

**超卖验证 SQL**：
```sql
-- 检查重复座位
SELECT seat_number, carriage_number, COUNT(*) as cnt
FROM t_order_item
WHERE order_sn IN (
  SELECT order_sn FROM t_order
  WHERE train_number = 'G1' AND run_date = '2026-07-20' AND status IN (0, 1)
)
GROUP BY seat_number, carriage_number
HAVING cnt > 1;

-- 验证订单数
SELECT COUNT(*) as order_count FROM t_order
WHERE train_number = 'G1' AND run_date = '2026-07-20' AND status IN (0, 1);
```

### 4.4 P1 — 候补队列压力测试

**测试计划**: `12306_special_waitlist.jmx`

| 阶段 | 操作 | 验证点 |
|------|------|--------|
| Phase 1 | 500 用户提交候补订单 | ZSet 队列写入性能 |
| Phase 2 | 查询候补队列状态 | 队列排序正确性 |

**验证项**：
1. ✅ 500 个候补订单全部入队
2. ✅ 队列按优先级排序（VIP > 普通用户）
3. ✅ 释放 1 张票后，仅 1 个候补兑现
4. ✅ 失败惩罚机制（-10 分）生效

### 4.5 P2 — 阶梯加压测试

使用混合场景计划，逐步增加并发：

| 并发数 | 持续时间 | 目的 |
|--------|---------|------|
| 50 | 120s | 基线测试 |
| 100 | 120s | 常规压力 |
| 200 | 120s | 中等压力 |
| 500 | 120s | 高压测试 |

---

## 五、压测执行流程

### 5.1 完整流程

```
1. 环境检查    → 确认服务启动、SMS mock 开启
2. 数据准备    → 生成 2000 用户 + 路线数据
3. 基线测试    → 单用户跑通全链路，记录 RT/TPS
4. 混合压测    → 7:2:1 场景，200 并发 × 5min
5. 专项测试    → 热缓存 / 超卖 / 候补
6. 阶梯加压    → 50→100→200→500，找到拐点
7. 定位瓶颈    → Arthas trace + async-profiler 火焰图
8. 结果分析    → analyze_results.py 生成报告
```

### 5.2 一键执行

```bash
# 运行混合场景（最常用）
./run_perf_test.sh mixed

# 运行超卖验证
./run_perf_test.sh oversell

# 运行所有场景
./run_perf_test.sh all

# 自定义参数
PERF_HOST=localhost PERF_PORT=8080 PERF_THREADS=300 PERF_DURATION=600 ./run_perf_test.sh mixed
```

### 5.3 手动执行

```bash
# 1. 准备数据
python prepare_perf_data.py --count 2000 --skip-login

# 2. 启动监控（后台）
./monitor.sh 300 &

# 3. 运行压测
jmeter -n -t 12306_mixed_721.jmx -l results/mixed.jtl -JHOST=localhost -JPORT=8080

# 4. 分析结果
python analyze_results.py results/mixed.jtl --output results/mixed_report.md
```

---

## 六、结果记录表

### 6.1 混合场景 (7:2:1) 结果

| 并发数 | TPS | 查询 RT(ms) | 购票 RT(ms) | P99 RT(ms) | 错误率 | CPU% | Redis 命中率 |
|--------|-----|-------------|-------------|------------|--------|------|-------------|
| 50     |     |             |             |            |        |      |             |
| 100    |     |             |             |            |        |      |             |
| 200    |     |             |             |            |        |      |             |
| 500    |     |             |             |            |        |      |             |

### 6.2 热点缓存测试结果

| 阶段 | 平均 RT(ms) | P99 RT(ms) | 缓存命中率 | DB 查询数 |
|------|-------------|------------|-----------|----------|
| 冷启动 |             |            |           |          |
| 热缓存 |             |            |           |          |

### 6.3 超卖验证结果

| 抢票并发数 | 可用票数 | 成功购票数 | 是否超卖 | 重复座位 |
|-----------|---------|-----------|---------|---------|
| 50        | 1       |           |         |         |

### 6.4 候补队列结果

| 候补订单数 | 入队耗时(ms) | 兑现数 | 优先级正确 |
|-----------|-------------|--------|-----------|
| 500       |             |        |           |

---

## 七、监控工具链

### 7.1 自动监控脚本

`monitor.sh` 在压测期间自动采集：

| 指标来源 | 采集项 | 间隔 |
|---------|--------|------|
| Redis | 连接数、内存、命中率、OPS、慢日志 | 5s |
| MySQL | 连接数、运行线程、QPS、慢查询、行锁等待 | 5s |
| JVM | YGC/FGC 次数和时间、老年代内存 | 10s |

```bash
# 启动监控（采集 300 秒）
./monitor.sh 300

# 输出文件
# monitor/redis_metrics_20260706_143000.log
# monitor/mysql_metrics_20260706_143000.log
# monitor/jvm_metrics_20260706_143000.log
# monitor/summary_20260706_143000.log
```

### 7.2 Arthas 快速定位

```bash
# 启动 Arthas
java -jar arthas-boot.jar

# 追踪购票核心方法耗时
trace com.lalal.modules.service.impl.TicketServiceImpl processCorePurchase -n 5

# 追踪 Redis Lua 选座耗时
trace com.lalal.modules.service.impl.SeatServiceImpl selectSeat -n 5

# 观察订单创建入参和返回值
watch com.lalal.modules.service.impl.OrderServiceImpl createOrder '{params, returnObj}' -n 5

# 查看线程阻塞
thread -b

# 查看最忙线程
thread -n 5

# 火焰图
profiler start
# ... 运行压测 ...
profiler stop --format html
```

### 7.3 Redis 手动检查

```bash
# 缓存命中率
redis-cli INFO stats | grep -E "keyspace_hits|keyspace_misses"

# 慢命令
redis-cli SLOWLOG GET 10

# 延迟检测
redis-cli --latency

# 内存使用
redis-cli INFO memory | grep used_memory_human

# 连接数
redis-cli INFO clients | grep connected_clients
```

### 7.4 MySQL 手动检查

```sql
-- 当前连接数
SHOW STATUS LIKE 'Threads_connected';

-- 慢查询
SHOW STATUS LIKE 'Slow_queries';

-- 行锁等待
SHOW STATUS LIKE 'Innodb_row_lock_waits';

-- 查看正在执行的 SQL
SHOW PROCESSLIST;
```

---

## 八、预判瓶颈点与优化方向

| 瓶颈 | 位置 | 原因 | 优化方向 |
|------|------|------|---------|
| 选座 Lua 脚本 | seat-service | bitmap 大时字符串拷贝开销 | 已改为掩码位运算；可考虑分车厢并发 |
| HikariCP 竞争 | 所有服务 | 默认 max-pool=20 太小 | 压测后调大；读写分离 |
| 查余票全表扫描 | ticket-service | train_route_pair 表 58 万行 | 确认索引命中；Redis 缓存预热 |
| RocketMQ 积压 | consumer | 选座/创建订单消费慢 | 增加 consumer 实例或线程 |
| Redis 连接池 | 所有服务 | lettuce 默认连接数 | 调 pool size；管道化 |
| Nacos 心跳 | 服务注册 | 高负载下心跳超时摘除 | 调大心跳间隔和超时阈值 |
| Gateway 线程 | gateway-service | Netty worker 线程不足 | 调 reactor.netty.workerCount |

---

## 九、文件清单

```
test/
├── test_procedures.md                    # 本文档
└── jmeter/
    ├── 12306_mixed_721.jmx               # ⭐ 混合场景 (7:2:1) 主测试计划
    ├── 12306_special_hot_cache.jmx       # 热点缓存测试
    ├── 12306_special_inventory_critical.jmx  # 超卖验证
    ├── 12306_special_waitlist.jmx        # 候补队列压力
    ├── 12306_stress_test.jmx             # 原始综合测试（含 TG1-TG6）
    ├── test_users_2000.csv               # 2000 测试用户
    ├── test_users.csv                    # 200 测试用户（兼容旧版）
    ├── test_routes.csv                   # 45 条测试路线
    ├── hot_route.csv                     # 热点线路
    ├── prepare_perf_data.py              # 数据准备脚本（Python）
    ├── prepare_test_data.sh              # 数据准备脚本（Shell）
    ├── prepare_test_data.bat             # 数据准备脚本（Windows）
    ├── run_perf_test.sh                  # ⭐ 一键压测脚本
    ├── monitor.sh                        # 监控采集脚本
    ├── analyze_results.py                # ⭐ 结果分析脚本
    └── results/                          # 压测结果输出目录
```

---

## 十、快速开始（5 分钟验证）

```bash
# 1. 确保服务启动 + SMS mock 开启
curl http://localhost:8080/api/user/sms/send -X POST \
  -H "Content-Type: application/json" -d '{"phone":"13800000001"}'

# 2. 准备数据
python prepare_perf_data.py --count 200 --skip-login

# 3. 快速压测（50 并发 × 60s）
jmeter -n -t 12306_mixed_721.jmx \
  -l results/quick_test.jtl \
  -JHOST=localhost -JPORT=8080 \
  -JTOTAL_THREADS=50 -JDURATION=60

# 4. 分析结果
python analyze_results.py results/quick_test.jtl
```

---

## 十一、潜在漏洞检查清单

| 类别 | 检查项 | 风险等级 | 测试方法 |
|------|--------|---------|---------|
| 超卖 | 并发选座是否原子 | 高 | `12306_special_inventory_critical.jmx` |
| 超卖 | 库存扣减与订单创建非原子 | 高 | 超卖验证 SQL |
| 幂等 | 支付回调重复处理 | 高 | 重复发送 Alipay notify |
| 幂等 | 购票接口重复提交 | 中 | @Idempotent 300s 窗口验证 |
| 越权 | 修改 orderSn 查看他人订单 | 中 | 手动构造请求 |
| 越权 | 退票/取消他人订单 | 中 | 手动构造请求 |
| 破刷 | 验证码无限制 | 中 | SMS 60s 频率限制验证 |
| 破刷 | 购票接口无速率限制 | 中 | Gateway TrafficMonitor 验证 |
| 数据泄露 | 身份证号未脱敏存储 | 低 | 代码审查 |
