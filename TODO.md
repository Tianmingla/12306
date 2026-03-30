# 12306 项目 TODO 清单

> **注意**: 修改任务状态时，请同时更新此文档

## 一、前后端 API 对齐 [高优先级] ✅

### 1.1 后端分页策略
- **游标分页**（分库分表场景）：用户管理、订单管理
- **普通分页**（数据量小）：列车管理、车站管理

### 1.2 后端分页查询改造
- [x] 用户管理：保持游标分页（lastId/pageSize）
- [x] 订单管理：保持游标分页（lastId/pageSize）
- [x] 列车管理：改为普通分页（pageNum/pageSize）
- [x] 车站管理：改为普通分页（pageNum/pageSize）

### 1.3 用户管理修复
- [x] 数据库：t_user 表添加 status 字段
- [x] 后端：UserDO 添加 status 属性
- [x] 后端：AdminUserServiceImpl 修复 toggleUserStatus 实现

### 1.4 前端 API 对齐
- [x] 登录页面：API 方法(POST)、参数类型对齐
- [x] Dashboard：统计 API 对齐（移除 mock）
- [x] 用户管理：列表(游标分页)/状态切换(PUT)/乘车人 API 对齐
- [x] 列车管理：列表(普通分页)/售卖状态(PUT) API 对齐
- [x] 车站管理：列表(普通分页) API 对齐
- [x] 订单管理：列表(游标分页)/详情(GET)/退款(PUT)/取消(PUT) API 对齐

### 1.5 类型定义对齐
- [x] 前端 types 与后端 DTO 对齐
- [x] 分页请求/响应结构统一（游标分页 vs 普通分页）

---

## 二、线路管理功能 [P1] ✅

### 2.1 后端实现 ✅
- [x] 创建 TrainStationDO 实体（使用现有 t_train_station 表）
- [x] 创建 TrainStationMapper
- [x] 创建 AdminRouteController
- [x] 创建 AdminRouteService 接口和实现

### 2.2 前端实现 ✅
- [x] 创建 types/route.ts 类型定义
- [x] 创建 api/route.ts API 调用
- [x] 完善 RouteManage.vue 页面

---

## 三、座位配置功能 [P1] ✅

### 3.1 后端实现 ✅
- [x] AdminTrainController 添加车厢列表接口
- [x] AdminTrainController 添加座位布局接口

### 3.2 前端实现 ✅
- [x] TrainList.vue 添加「座位配置」按钮
- [x] 创建座位配置弹窗组件

---

## 四、角色管理功能 [P2] ✅

### 4.1 数据库 ✅
- [x] 创建 t_role 表
- [x] 创建 t_permission 表
- [x] 创建 t_role_permission 关联表
- [x] 创建 t_admin_user_role 关联表

### 4.2 后端实现 ✅
- [x] 创建 RoleDO、PermissionDO、RolePermissionDO 实体
- [x] 创建 AdminRoleController
- [x] 创建 AdminRoleService

### 4.3 前端实现 ✅
- [x] 创建 types/role.ts 类型定义
- [x] 创建 api/role.ts API 调用
- [x] 完善 RoleManage.vue 页面

---

## 五、操作日志功能 [P2] ✅

### 5.1 数据库 ✅
- [x] 创建 t_operation_log 表

### 5.2 后端实现 ✅
- [x] 创建 OperationLogDO 实体
- [x] 创建 OperationLogAspect 切面
- [x] 创建 AdminLogController
- [x] 创建 AdminLogService

### 5.3 前端实现 ✅
- [x] 创建 types/log.ts 类型定义
- [x] 创建 api/log.ts API 调用
- [x] 完善 OperationLog.vue 页面

---

## 二、MQ 消息队列模块 [低优先级]

### 1.1 通用接口设计 ✅
- [x] 设计通用的消息队列抽象接口，支持多种MQ实现
- [x] 定义消息体通用结构
- [x] 定义消息监听器通用接口
- [x] 定义消息序列化/反序列化接口

### 1.2 RocketMQ 实现 ✅
- [x] 添加 RocketMQ 依赖
- [x] 实现 RocketMQ 消息发送服务
- [x] 实现 RocketMQ 配置类
- [x] 实现延迟消息支持
- [x] 实现顺序消息支持
- [x] 实现 RocketMQ 消息消费服务（使用注解方式）

### 1.3 消息队列功能
- [x] 消息发送重试机制（RocketMQ内置）
- [x] 消息幂等性处理（结合Idempotent模块，`BaseMessageConsumer` 内置）
- [ ] 死信队列处理
- [ ] 消息追踪机制

### 1.4 使用示例

#### 1.4.1 发送消息

```java
@Autowired
private MessageQueueService messageQueueService;

// 简单发送
messageQueueService.send("order-topic", orderData);

// 带Tag发送
messageQueueService.send("order-topic", "create", orderData);

// 发送延迟消息（30秒后）
messageQueueService.sendDelay("order-topic", orderData, 30000);

// 发送顺序消息
messageQueueService.sendOrderly("order-topic", "orderId-123", orderData);

// 使用Message对象
Message message = new Message("order-topic", "create", orderData);
message.addHeader("trace-id", RequestIdGenerator.getRequestId());
messageQueueService.send(message);
```

#### 1.4.2 消费消息

**方式一：使用 `@MessageConsumer` 注解 + 继承 `RocketMQBaseConsumer`（推荐）**

```java
@Component
@MessageConsumer(
    topic = "order-topic",
    tag = "create",
    consumerGroup = "order-consumer-group",
    consumeMode = ConsumeMode.CONCURRENTLY
)
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "order-consumer-group",
    selectorExpression = "create"
)
public class OrderCreateConsumer extends RocketMQBaseConsumer<OrderDTO> {

    @Override
    protected void doProcess(OrderDTO order) {
        // 处理订单逻辑
        log.info("Received order: {}", order);
    }
}
```

**方式二：继承 `RocketMQMessageConsumer`（传统方式）**

```java
@Component
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "order-consumer-group",
    selectorExpression = "create"
)
public class OrderConsumer extends RocketMQMessageConsumer<OrderDTO> {

    @Override
    protected boolean handleMessage(Message message) {
        OrderDTO order = (OrderDTO) message.getBody();
        // 处理订单逻辑
        log.info("Received order: {}", order);
        return true;
    }

    @Override
    public String getTopic() {
        return "order-topic";
    }

    @Override
    public String getTag() {
        return "create";
    }
}
```

#### 1.4.3 配置文件 (application.yml)

```yaml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: default-producer-group
    send-message-timeout: 3000
    retry-times-when-send-failed: 2
  consumer:
    group: default-consumer-group
```

## 二、幂等性模块 Idempotent [低优先级]

### 2.1 幂等性注解完善 ✅
- [x] 完善 Idempotent 注解，支持多种幂等键来源
  - [x] SpEL 表达式支持
  - [x] Header 参数支持
  - [x] 请求体参数支持
- [x] 支持返回结果缓存

### 2.2 幂等性切面实现 ✅
- [x] 使用 Redisson 分布式锁实现
- [x] 完善幂等性校验逻辑
- [x] 添加自定义异常处理
- [x] 添加幂等性结果缓存

### 2.3 幂等性应用 ✅
- [x] 购票接口添加幂等性注解（`PurchaseTicketController.purchaseTicket`）
- [x] 订单创建接口添加幂等性注解（`OrderController.create`）
- [x] 支付回调接口添加幂等性注解（`OrderController.alipayNotify`）

### 2.4 使用示例

```java
@RestController
@RequestMapping("/order")
public class OrderController {

    // 使用默认key规则（类名:方法名:参数哈希）
    @Idempotent(expire = 300, message = "请勿重复提交订单")
    @PostMapping("/create")
    public Result<Order> createOrder(@RequestBody OrderDTO dto) {
        // ...
    }

    // 使用参数作为key
    @Idempotent(key = "${orderId}", expire = 600, message = "订单正在处理中")
    @PostMapping("/pay/{orderId}")
    public Result<String> payOrder(@PathVariable String orderId) {
        // ...
    }

    // 使用SpEL表达式
    @Idempotent(key = "${#dto.userId}-${#dto.productId}", cacheResult = true)
    @PostMapping("/purchase")
    public Result<PurchaseResult> purchase(@RequestBody PurchaseDTO dto) {
        // 相同的userId+productId组合会返回上次的计算结果
        return Result.success(calculatePrice(dto));
    }

    // 使用请求头
    @Idempotent(key = "${header.request-id}", cacheResult = true, expire = 3600)
    @GetMapping("/query")
    public Result<Order> queryOrder() {
        // 使用请求头中的request-id作为幂等键
    }
}
```

## 三、缓存模块 Cache
- [x] SafeCacheTemplate 基础实现完成
- [x] 分布式锁支持（Redisson 已在 Idempotent 模块中使用）
- [ ] 完善缓存序列化器配置（RawRedisSerializer）
- [ ] 添加观察者/拦截器扩展机制

## 四、其他任务
- [x] 整理 Frameworks 模块的 spring-boot-starter 自动配置
  - [x] common 模块自动配置
  - [x] database 模块自动配置
  - [x] cache 模块自动配置
  - [x] mq 模块自动配置
- [x] 更新各服务的 pom.xml 依赖

---

## 任务进度说明

### MQ 模块设计思路

```
MessageQueue (接口)
├── RocketMQMessageQueue (实现)
├── RabbitMQMessageQueue (预留)
└── KafkaMessageQueue (预留)

Message (消息体)
├── headers (Map<String, Object>)
├── body (Object)
└── topic (String)

MessageListener (监听器接口)
├── onMessage(Message message)
└── onError(Throwable error)
```

### 幂等性模块设计思路

使用 Redis SET NX EX 实现分布式锁：
- 如果 key 不存在，设置成功（首次请求）
- 如果 key 存在，说明重复请求，返回缓存的响应或抛出异常
- 使用 SpEL 表达式解析幂等键来源

---

## 六、前台功能补全 [进行中]

### 6.1 常用查询模块
- [ ] **车站大屏功能**
  - [ ] 后端：ticket-service 添加车站大屏接口（正晚点、检票状态）
  - [ ] 前端：车站大屏页面组件（玻璃效果 UI）
  - [ ] TODO(低优先级)：雨天水滴玻璃效果

### 6.2 候补购票模块
- [ ] 后端：order-service 添加候补购票接口
- [ ] 前端：候补购票页面组件

### 6.3 车站引导模块
- [ ] 后端：添加车站引导接口（放 ticket-service 或新建 info-service）
- [ ] 前端：车站引导页面

### 6.4 出行指南模块
- [ ] 前端：出行指南页面

### 6.5 Agent 客服
- [ ] TODO(低优先级)：等待用户提供详细需求
