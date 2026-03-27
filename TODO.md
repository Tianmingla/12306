# 12306 项目 TODO 清单

> **注意**: 修改任务状态时，请同时更新此文档

## 一、MQ 消息队列模块 [高优先级]

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
