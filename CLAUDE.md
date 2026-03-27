# CLAUDE.md

This file provides guidance to Cluade Code (cludae.ai/code) when working with code in this repository.

## Project Overview

This is a 12306 railway ticketing system implementation with a microservices architecture. The project consists of:
- **Backend**: Spring Cloud microservices (Java 17, Spring Boot 3.0.7)
- **Frontend**: React + TypeScript + Vite application
- **Admin**: Vue3 + Arco Design backend management system
- **Data Scripts**: Python scripts for data processing and import

## Build Commands

### Backend (Maven)
```bash
# Build all modules
mvn clean install

# Build a specific service
mvn clean install -pl Services/ticket-service -am

# Skip tests
mvn clean install -DskipTests
```

### Frontend
```bash
cd 12306
npm install
npm run dev       # Development server (default Vite port)
npm run build     # Production build
```

### Admin Backend Management
```bash
cd admin
npm install
npm run dev       # Development server at http://localhost:5174
npm run build     # Production build
```

Default login: `admin / 123456`

## Service Ports

| Service | Port |
|---------|------|
| gateway-service | 8080 |
| ticket-service | 8081 |
| seat-service | 8082 |
| order-service | 8083 |
| user-service | 8084 |

## Infrastructure Dependencies

- **MySQL**: localhost:3306, database `my12306`, user: root
- **Redis**: localhost:6379
- **Nacos**: localhost:8848 (service discovery)

## Architecture

### Microservices Structure

```
Services/
├── gateway-service/    # API Gateway, routes requests, injects X-User-Id header
├── ticket-service/     # Train search, ticket purchase, orchestrates other services
├── seat-service/       # Seat selection and management
├── order-service/      # Order creation, payment (Alipay sandbox integration)
└── user-service/       # User auth (JWT), passenger management, SMS login
```

### Framework Modules

```
Frameworks/
├── common/           # Shared DTOs, Result wrapper, enums, utils
├── database/         # MyBatis-Plus config, base entities
├── cache/            # Redis config, SafeCacheTemplate
├── log/              # Log monitoring aspects
├── Idempotent/       # @Idempotent annotation for deduplication
└── mq/               # RocketMQ abstraction layer
```

### Inter-Service Communication

- ticket-service uses OpenFeign clients to call:
  - `user-service`: `/api/user/internal/passengers/batch`
  - `seat-service`: Seat selection APIs
  - `order-service`: Order creation

- Gateway routes by path prefix:
  - `/api/user/**` -> user-service
  - `/api/ticket/**`, `/api/trainDetail/**` -> ticket-service
  - `/api/seat/**` -> seat-service
  - `/api/order/**` -> order-service

### Key Patterns

1. **Idempotency**: Use `@Idempotent` annotation on methods that need duplicate request protection. Supports SpEL expressions for key generation.

2. **Result Wrapper**: All API responses use `Result<T>` from `common` module.

3. **User Context**: Gateway injects `X-User-Id` header for authenticated requests. Controllers extract it with `@RequestHeader("X-User-Id")`.

## Data Scripts (Python)

Located in `DataScript/` for importing station data, train routes, and generating seat/carriage data. Requires `pandas`, `pymysql`, `sqlalchemy`.

## Frontend API

Frontend services in `12306/services/` communicate via gateway at `http://localhost:8080/api`. Auth token stored in localStorage, sent as `Authorization: Bearer <token>`.

## Payment Integration

Order service integrates Alipay sandbox. Configuration in `Services/order-service/src/main/resources/application.yml`:
- Sandbox gateway URL
- App ID and keys (RSA2 signing)
- Notify/return URLs

## Framework Modules Details

### Idempotent Module (Frameworks/Idempotent/)

幂等性模块用于防止重复请求，基于 Redis + Redisson 分布式锁实现。

**核心注解 `@Idempotent`**:
- `key`: 幂等键，支持 SpEL 表达式（如 `${#dto.userId}`）
- `expire`: 过期时间（秒），默认 60
- `message`: 重复请求时的提示信息
- `cacheResult`: 是否缓存返回结果（相同请求直接返回缓存结果）

**使用示例**:
```java
// 默认 key（类名:方法名:参数哈希）
@Idempotent(expire = 300, message = "请勿重复提交")
@PostMapping("/create")
public Result<Order> createOrder(@RequestBody OrderDTO dto) { ... }

// SpEL 表达式取参数
@Idempotent(key = "${#dto.userId}-${#dto.productId}", cacheResult = true)
@PostMapping("/purchase")
public Result<PurchaseResult> purchase(@RequestBody PurchaseDTO dto) { ... }

// 请求头作为 key
@Idempotent(key = "${header.request-id}", cacheResult = true, expire = 3600)
@GetMapping("/query")
public Result<Order> queryOrder() { ... }
```

**已应用接口**:
- `PurchaseTicketController.purchaseTicket` - 购票接口
- `OrderController.create` - 订单创建接口
- `OrderController.alipayNotify` - 支付宝回调接口

### MQ Module (Frameworks/mq/)

消息队列抽象层，支持多种 MQ 实现。当前已实现 RocketMQ。

**核心接口**:
- `MessageQueueService`: 消息发送服务
- `Message`: 消息体封装（topic, tag, headers, body）
- `BaseMessageConsumer`: 消费者基类（内置幂等性处理）

**发送消息示例**:
```java
@Autowired
private MessageQueueService messageQueueService;

// 简单发送
messageQueueService.send("order-topic", orderData);

// 带 Tag 发送
messageQueueService.send("order-topic", "create", orderData);

// 延迟消息（30秒后）
messageQueueService.sendDelay("order-topic", orderData, 30000);

// 顺序消息（按 orderId 分区）
messageQueueService.sendOrderly("order-topic", "orderId-123", orderData);
```

**消费消息示例**:
```java
@Component
@MessageConsumer(topic = "order-topic", tag = "create", consumerGroup = "order-group")
@RocketMQMessageListener(topic = "order-topic", consumerGroup = "order-group", selectorExpression = "create")
public class OrderCreateConsumer extends RocketMQBaseConsumer<OrderDTO> {
    @Override
    protected void doProcess(OrderDTO order) {
        // 处理订单逻辑
    }
}
```

**配置** (`application.yml`):
```yaml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: default-producer-group
    send-message-timeout: 3000
    retry-times-when-send-failed: 2
```

**待完善**:
- 死信队列处理
- 消息追踪机制

### Cache Module (Frameworks/cache/)

Redis 缓存封装，提供 `SafeCacheTemplate` 安全操作模板。

**待完善**:
- RawRedisSerializer 序列化器配置
- 观察者/拦截器扩展机制

## Admin Backend Management System (admin/)

Vue3 + Arco Design 后台运营管理系统，用于管理 12306 铁路票务系统的核心数据。

### 技术栈

- **框架**: Vue 3.4+ (Composition API)
- **UI 库**: Arco Design Vue
- **构建工具**: Vite 5
- **状态管理**: Pinia
- **路由**: Vue Router 4
- **HTTP 客户端**: Axios
- **图表**: ECharts
- **样式**: SCSS

### 目录结构

```
admin/
├── src/
│   ├── api/           # API 调用层（与后端对接）
│   │   ├── user.ts    # 用户相关 API
│   │   ├── train.ts   # 列车相关 API
│   │   ├── station.ts # 车站相关 API
│   │   ├── order.ts   # 订单相关 API
│   │   └── stats.ts   # 统计数据 API
│   ├── types/         # TypeScript 类型定义
│   │   ├── user.ts    # 用户类型
│   │   ├── train.ts   # 列车类型
│   │   ├── station.ts # 车站类型
│   │   ├── order.ts   # 订单类型
│   │   └── stats.ts   # 统计类型
│   ├── mock/          # Mock 数据（开发阶段）
│   ├── views/         # 页面组件
│   │   ├── login/     # 登录页
│   │   ├── dashboard/ # 首页统计
│   │   ├── train/     # 车票管理（车次、站点、线路）
│   │   ├── order/     # 订单管理（订单、退款）
│   │   └── system/    # 系统管理（用户、角色、日志）
│   ├── layouts/       # 布局组件
│   ├── store/         # Pinia 状态管理
│   ├── router/        # 路由配置
│   ├── styles/        # SCSS 样式（深色铁路主题）
│   └── utils/         # 工具函数
└── vite.config.ts
```

### 功能模块

| 模块 | 路由 | 功能 |
|------|------|------|
| 登录 | `/login` | 角色区分登录（管理员/运营人员） |
| Dashboard | `/dashboard` | 统计卡片、订单趋势图表、快捷入口 |
| 车次管理 | `/train/list` | 列车 CRUD、状态切换、经停站查看 |
| 站点管理 | `/train/station` | 车站 CRUD |
| 线路管理 | `/train/route` | 线路管理（开发中） |
| 订单管理 | `/order/list` | 订单列表、详情、退款、取消 |
| 退款管理 | `/order/refund` | 退款审核 |
| 用户管理 | `/system/user` | 用户列表、状态管理、重置密码 |
| 角色管理 | `/system/role` | 角色权限（开发中） |
| 操作日志 | `/system/log` | 操作记录（开发中） |

### UI 主题

深色铁路风格主题，配色方案：
- **主色调**: `#C41E3A` (铁路红)
- **辅助色**: `#1E3A5F` (沉稳蓝)
- **成功色**: `#10B981`
- **警告色**: `#F59E0B`
- **危险色**: `#EF4444`
- **背景色**: `#0F172A` (主背景), `#1E293B` (卡片背景)

### API 设计原则

1. **类型与 API 分离**: 类型定义在 `types/` 目录，API 调用在 `api/` 目录
2. **统一响应结构**: 所有 API 返回 `Result<T>` 类型
3. **分页请求**: 使用 `PageParams` 和 `PageResult<T>`
4. **Mock 数据优先**: 开发阶段使用 `mock/data.ts` 模拟数据

### 与后端对接

Admin 前端通过 `/api/admin/*` 路径与后端通信，需要在各微服务中新增 Admin Controller。

**待实现的后端 API**:
- `GET /api/admin/user/list` - 用户分页列表
- `PUT /api/admin/user/{id}/status` - 切换用户状态
- `GET /api/admin/train/list` - 列车分页列表
- `POST /api/admin/train` - 创建列车
- `PUT /api/admin/train/{id}` - 更新列车
- `DELETE /api/admin/train/{id}` - 删除列车
- `GET /api/admin/station/list` - 车站分页列表
- `POST /api/admin/station` - 创建车站
- `PUT /api/admin/station/{id}` - 更新车站
- `DELETE /api/admin/station/{id}` - 删除车站
- `GET /api/admin/order/list` - 订单分页列表
- `GET /api/admin/refund/list` - 退款申请列表
- `PUT /api/admin/refund/{id}/audit` - 审核退款
