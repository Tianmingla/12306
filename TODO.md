# 12306 铁路票务系统 - TODO

> 分支: `master` | 最后更新: 2026-06-27

---

## 一、功能开发

### 核心票务
- [ ] **改签功能** — 前端已有改签界面(dda1107)，后端逻辑待实现:
  - [ ] 改签差价计算（多退少补）
  - [ ] 原票释放 + 新票锁定（事务一致性）
  - [ ] 改签次数限制（一次）
  - [ ] 改签截止时间（发车前24h/30min）
- [ ] **电子客票** — 生成乘车二维码，扫码核销
- [ ] **列车时刻表** — 按车次查询完整经停站+时刻

### 后台管理
- [ ] **角色权限** — 前端已有角色管理页面，后端 RBAC 未完整实现
- [ ] **操作日志** — 前端已有日志页面，后端查询/导出未完整实现
- [ ] **数据导出** — 订单/用户数据导出 Excel

### 前端体验
- [ ] **Shader UI 美化** (论文亮点):
  - [ ] CSS 玻璃拟态 + 座位图特效 (快速见效)
  - [ ] 首页粒子动画/铁路线路背景 (Three.js)
  - [ ] 支付成功粒子爆发效果 (Canvas)

---

## 二、性能与架构

### 分库分表 (ShardingSphere)
> 目标: t_order、t_order_item、t_ticket 按 user_id 或日期分片，支撑亿级数据
- [ ] **ShardingSphere 集成** — 依赖已声明(shardingsphere-jdbc-core 5.3.2)，需配置:
  - [ ] 分片策略设计（按 user_id 取模 vs 按日期范围）
  - [ ] 分片键选择（t_order.user_id / t_ticket.date）
  - [ ] 广播表配置（t_station、t_train 等基础表）
  - [ ] 绑定表配置（t_order + t_order_item 避免笛卡尔积）
- [ ] **跨分片查询改造** — 订单列表、管理后台统计需要聚合多分片结果
- [ ] **分布式 ID 生成** — Snowflake 替换自增 ID
- [ ] **分片后的数据迁移** — 存量数据重新分布
- [ ] **分片路由测试** — 验证数据均匀分布、查询正确路由

### RocketMQ 分布式事务一致性
> 目标: 购票链路 (扣库存→建订单→扣款) 跨多个服务，需要最终一致性保证
- [ ] **购票事务消息** — ticket-service 作为事务发起方:
  - [ ] half message 预发送 → 执行本地事务(扣库存) → commit/rollback
  - [ ] seat-service 消费 commit 消息执行真实扣库存
  - [ ] order-service 消费 commit 消息创建订单
- [ ] **事务回查机制** — ticket-service 提供 check 接口，MQ 回查本地事务状态
- [ ] **幂等性保证** — 消费者已通过 @Idempotent 处理，需验证分布式事务场景
- [ ] **死信队列处理** — 消费失败后的重试→死信→告警→人工处理链路
- [ ] **订单超时取消** — 现有延迟队列方案 + 分布式事务补偿（释放库存）

### 缓存数据库一致性
> 目标: Redis 缓存与 MySQL 数据同步，避免读到脏数据
- [ ] **缓存更新策略制定** — 评估每个缓存场景的最佳策略:
  - [ ] Cache-Aside（旁路缓存）— 读: 缓存未命中查DB写缓存, 写: 先写DB再删缓存
  - [ ] Write-Through（写穿）— 写DB同时更新缓存
  - [ ] Write-Behind（写回）— 异步批量写DB
- [ ] **余票缓存一致性** — 读多写少场景，需处理:
  - [ ] 缓存更新时机（先删缓存还是先更新DB）
  - [ ] 延迟双删策略（写入后延迟再删一次，防止并发读的旧数据回写）
  - [ ] Redis Lua 脚本保证查询+扣减原子性
- [ ] **缓存最终一致性兜底** — Canal/binlog 监听 MySQL 变更，异步刷新 Redis
- [ ] **缓存预热** — 服务启动时预加载热点数据（热门车次、车站列表）
- [ ] **缓存雪崩防护** — 过期时间加随机值，避免同时大面积失效
- [ ] **热点缓存检测** — 监控哪些 key 被高频访问，针对性优化

### 其他
- [ ] **数据库读写分离** — 查询走从库，写入走主库
- [ ] **Nacos 配置中心** — 动态配置刷新（目前只用服务发现）
- [ ] **链路追踪** — SkyWalking / Jaeger
- [ ] **熔断降级** — Resilience4j（Feign 调用容错）
- [ ] **接口限流** — Sentinel / Guava RateLimiter（网关层或接口层）

---

## 三、测试

### 单元测试
- [ ] **ticket-service 核心逻辑单测** — 票价计算、中转换乘算法、余票查询
- [ ] **seat-service 选座逻辑单测** — 各座位类型选择策略、连续座位分配
- [ ] **order-service 订单状态机单测** — 创建→支付→取消/退款 全链路
- [ ] **user-service 认证逻辑单测** — JWT 签发/验证、验证码

### 集成测试
- [ ] **API 端到端测试** — Spring Boot Test + Testcontainers (MySQL/Redis)
- [ ] **Feign 调用测试** — ticket→user、ticket→seat、ticket→order

### 压力测试
- [ ] **JMeter 单接口压测** — search, purchase, login 各接口极限 TPS
- [ ] **全链路压测** — 模拟真实用户行为: 登录→搜索→选座→下单→支付
- [ ] **超卖验证** — 同车次 500 并发购票，验证不存在超卖
- [ ] **阶梯增压** — 100→300→800 线程，观察系统瓶颈点

### 性能测试
- [ ] **Redis 命中率监控** — SafeCacheTemplate 缓存效果评估
- [ ] **数据库慢查询分析** — 已有拦截器，需整理 Top N 问题 SQL
- [ ] **RocketMQ 消息积压监控** — 高峰时段消息处理延迟

---

## 四、运维与部署

- [ ] **Kubernetes 部署** — Helm Chart / K8s YAML（目前仅 docker-compose）
- [ ] **CI/CD Pipeline** — GitHub Actions 自动构建+测试+部署
- [ ] **日志收集** — ELK / Loki + 业务日志规范
- [ ] **数据库备份策略** — 定时备份 + 异地存储

---

## 五、论文相关

### 票额智能分配 (ML)
- [ ] Phase 1: 历史订单数据清洗 + 特征工程脚本
- [ ] Phase 2: XGBoost baseline → LSTM 模型训练
- [ ] Phase 3: 模型导出 ONNX + Flask/FastAPI 推理服务
- [ ] Phase 4: ticket-service 调用预测服务

### Shader UI 美化
- [ ] Phase 1: CSS 效果（玻璃拟态、渐变、动画）
- [ ] Phase 2: Canvas 效果（粒子、波纹、座位）
- [ ] Phase 3: Three.js 效果（复杂粒子、Shader）

### 文档产出
- [ ] 系统接口文档 (Swagger/OpenAPI 生成)
- [ ] 部署文档 (Docker Compose + K8s)
- [ ] 数据清洗报告
- [ ] 模型训练报告

---

## 六、AOT / GraalVM 原生编译

> 详见 `AOT-TODO.md` (aot 分支)
- [ ] user-service 原生编译启动问题（SLF4J/Logback）
- [ ] 其余 5 个服务原生编译验证
- [ ] tracing agent 生成完整 metadata

---

## 历史已完成

- [x] 用户注册登录（手机号+验证码）
- [x] 车票查询（按区间/日期搜索、中转搜索、A*智能换乘）
- [x] 座位选择（手动/自动，6种座位类型，位运算优化）
- [x] 订单管理（创建、支付宝沙箱支付、取消、退款、延迟队列超时取消）
- [x] 候补购票（Redis ZSet 优先级队列 + RocketMQ 回调）
- [x] 购物车异步购票（Redis + RocketMQ 削峰填谷）
- [x] 后台管理（Dashboard统计、用户/订单/车次/车站 CRUD、游标分页）
- [x] 出行提醒（发车前短信/推送，含提前1h/30min两段提醒）
- [x] 分布式锁与幂等性保护（@Idempotent 注解 + Redisson）
- [x] 网关流量统计（本地缓存定时同步）
- [x] Docker 一键部署（docker-compose: MySQL + Redis + Nacos + RocketMQ + 6服务 + nginx）
- [x] Prometheus + Grafana 监控（JVM指标、业务指标、自定义Dashboard）
- [x] MySQL 慢查询拦截器
- [x] 缓存框架 SafeCacheTemplate（批量查询、null保护、序列化统一）
- [x] 位运算优化座位分配（连续区间掩码快速构造）
- [x] 前端 nginx 部署 + React/Vue 双前端
- [x] 数据生成脚本（车站、车次、座位、用户、订单、距离）
- [x] JMeter 压测脚本（登录/搜索/购票/超卖/阶梯增压 6个线程组）
