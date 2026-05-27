# 12306 性能压测与高并发测试方案

## 一、压测工具选型

| 工具 | 适合场景 | 推荐度 |
|------|---------|--------|
| **JMeter** | GUI 操作、分布式压测、丰富插件 | 通用首选 |
| **wrk/wrk2** | 轻量 HTTP 压测、延迟分布测量 | 快速验证 |
| **Gatling** | 代码编写场景、CI 集成 | 自动化压测 |
| **hey** (Go) | 一行命令快速压 | 临时验证 |

策略：**JMeter** 编写完整购票场景 + **wrk** 做快速单接口验证。

---

## 二、核心压测场景

### P0 — 必须压测（系统命脉）

| # | 场景 | 并发梯度 | 验证点 |
|---|------|---------|--------|
| 1 | 购票全链路（搜车→选座→创建订单） | 100→500→1000→2000 | RT/TPS/错误率 |
| 2 | 同车次同日抢票超卖验证 | 1000 用户抢 50 张票 | 订单数≤50，无超卖 |
| 3 | 选座原子性 | 同车厢同座位并发选 | bitmap 一致性，无重复分配 |

### P1 — 重要场景

| # | 场景 | 并发梯度 | 验证点 |
|---|------|---------|--------|
| 4 | 查余票（高频读接口） | 500→2000 | 缓存穿透/击穿，RT |
| 5 | 支付宝回调并发 | 100→500 | 幂等性（@Idempotent） |
| 6 | 候补兑现链路 | 100→500 | 队列弹出+选座+创建订单 |

### P2 — 极端场景

| # | 场景 | 验证点 |
|---|------|--------|
| 7 | RocketMQ 消费延迟 | 生产者灌入大量消息，观察消费 TPS |
| 8 | Redis 连接池耗尽 | 连接数打满后系统行为 |
| 9 | MySQL 连接池 | 慢 SQL 打满 HikariCP |

---

## 三、性能定位工具链

### 应用层 Profiling

| 工具 | 用途 |
|------|------|
| **Arthas**（阿里） | 线上热更新、trace 方法耗时、watch 入参返回值 |
| **async-profiler** | CPU/内存火焰图，定位热点方法 |
| **Spring Actuator** | /actuator/metrics 看连接池、线程池 |

### 数据库层

| 工具 | 用途 |
|------|------|
| **MySQL slow_query_log** | 慢 SQL 抓取 |
| **EXPLAIN + SHOW PROFILE** | 执行计划分析 |
| **performance_schema** | 锁等待、临时表、全表扫描 |

### JVM 层

| 工具 | 用途 |
|------|------|
| **jstat -gc** | GC 频率和停顿 |
| **GC 日志 + GCEasy** | 分析 GC 瓶颈 |
| **jmap + MAT** | 内存泄漏分析 |

### Redis 层

| 工具 | 用途 |
|------|------|
| **redis-cli --latency** | 延迟检测 |
| **SLOWLOG** | 慢命令 |
| **INFO + MEMORY** | 内存和连接数 |

---

## 四、压测执行流程

```
1. 基线测试  → 单用户跑通全链路，记录 RT/TPS
2. 阶梯加压  → 50→100→200→500→1000 并发，找到拐点
3. 定位瓶颈  → 拐点处用 Arthas trace + async-profiler 出火焰图
4. 优化      → 根据瓶颈类型优化（见第五节）
5. 回归验证  → 重新压测确认提升
```

### 阶梯加压记录表

| 并发数 | TPS | 平均 RT(ms) | P99 RT(ms) | 错误率 | CPU% | 备注 |
|--------|-----|-------------|------------|--------|------|------|
| 50     |     |             |            |        |      |      |
| 100    |     |             |            |        |      |      |
| 200    |     |             |            |        |      |      |
| 500    |     |             |            |        |      |      |
| 1000   |     |             |            |        |      |      |
| 2000   |     |             |            |        |      |      |

---

## 五、预判瓶颈点与优化方向

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

## 六、超卖验证方案

最关键的测试——并发购票是否超卖：

```
前置条件：
  某车次某日余票 10 张

操作：
  100 并发用户同时购买该车次

验证项：
  1. 成功订单数 ≤ 10（不能超卖）
  2. Redis 剩余余票 = 10 - 成功订单数（数据一致性）
  3. bitmap 中被占位数 = 成功订单的座位总数
  4. 无重复座位分配给不同用户
  5. 座位释放后余票正确恢复
```

### 超卖测试 SQL 验证

```sql
-- 检查是否有重复座位分配
SELECT seat_number, carriage_number, COUNT(*) as cnt
FROM t_order_item
WHERE order_sn IN (SELECT order_sn FROM t_order WHERE train_number = 'G1' AND run_date = '2026-06-01')
GROUP BY seat_number, carriage_number
HAVING cnt > 1;

-- 检查订单数与余票是否一致
SELECT COUNT(*) as order_count FROM t_order
WHERE train_number = 'G1' AND run_date = '2026-06-01' AND status IN (0, 1);
```

---

## 七、Arthas 快速定位命令

```bash
# 启动 Arthas
java -jar arthas-boot.jar

# 追踪方法耗时
trace com.lalal.modules.service.impl.TicketServiceImpl processCorePurchase -n 5

# 观察方法入参和返回值
watch com.lalal.modules.service.impl.OrderServiceImpl createOrder '{params, returnObj}' -n 5

# 查看线程阻塞
thread -b

# 查看最忙线程
thread -n 5

# 火焰图（配合 async-profiler）
profiler start
# ... 运行压测 ...
profiler stop --format html
```

---

## 八、快速开始（5 分钟验证）

```bash
# 安装 wrk
# Ubuntu: apt install wrk
# Mac: brew install wrk

# 单接口压测 - 查余票
wrk -t4 -c100 -d30s http://localhost:8080/api/ticket/search?from=北京&to=上海&date=2026-06-01

# 单接口压测 - 登录
wrk -t4 -c50 -d30s -s login.lua http://localhost:8080/api/user/login
```

---

## 九、JMeter 测试计划要点

### 线程组配置

- **线程数**：按梯度递增（100/500/1000）
- **Ramp-Up**：10s（避免瞬间打满）
- **循环次数**：永远 / 持续 300s
- **思考时间**：100-500ms 随机（模拟真实用户）

### 关键断言

1. 购票响应码 = 200
2. 返回 orderSn 非空
3. 超卖场景：成功数 ≤ 余票总量

### 数据准备

- 预注册 N 个测试账号（手机号 13800000001-13800001000）
- 每账号预添加 1-3 个乘车人
- 确保目标车次有足够的测试余票

---

## 十、潜在漏洞检查清单

| 类别 | 检查项 | 风险等级 |
|------|--------|---------|
| 超卖 | 并发选座是否原子 | 高 |
| 超卖 | 库存扣减与订单创建非原子 | 高 |
| 幂等 | 支付回调重复处理 | 高 |
| 幂等 | 购票接口重复提交 | 中 |
| 越权 | 修改 orderSn 查看他人订单 | 中 |
| 越权 | 退票/取消他人订单 | 中 |
| 注入 | SQL 拼接（MyBatis-Plus 已防） | 低 |
| 破刷 | 验证码无限制 | 中 |
| 破刷 | 购票接口无速率限制 | 中 |
| 数据泄露 | 身份证号未脱敏存储 | 低 |
