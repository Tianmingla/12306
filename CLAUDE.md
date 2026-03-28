# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a 12306 railway ticketing system implementation with a microservices architecture. The project consists of:
- **Backend**: Spring Cloud microservices (Java 17, Spring Boot 3.0.7)
- **Frontend**: React + TypeScript + Vite application
- **Admin**: Vue3 + Arco Design backend management system
- **Data Scripts**: Python scripts for data processing and import

## Development Workflow

### TODO 工作流

每次开始工作前，请遵循以下工作流：

1. **阅读任务**: 查看 `TODO.md` 文件，了解待办任务
2. **理解需求**: 仔细阅读用户需求，明确任务目标
3. **执行任务**:
   - 标记任务为 `[x]` 表示进行中
   - 按步骤实现功能
   - 完成后将 `[x]` 改为 `[✓]`
4. **记录重要改动**: 如果是较为重要的改动，将关键信息添加到 `CLAUDE.md`
5. **清理任务**: 任务完成后，从 `TODO.md` 中删除该任务条目

**TODO.md 格式**:
```markdown
# TODO

- [ ] 任务描述1
- [ ] 任务描述2
- [x] 进行中的任务
- [✓] 已完成的任务（删除前可保留片刻供确认）
```

### 代码规范

1. **依赖管理**: 所有新依赖必须添加到根目录 `pom.xml` 的 `dependencyManagement` 中统一管理版本
2. **缓存常量**: 所有缓存 Key 常量统一放在 `Frameworks/common/src/main/java/com/lalal/modules/constant/cache/CacheConstant.java`
3. **枚举统一**: 类别、状态等应使用枚举定义，放在 `Frameworks/common/src/main/java/com/lalal/modules/constant/` 目录
4. **API 分离**: 前端 API 调用与类型定义分离（`api/` 和 `types/` 目录）

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
| admin-service | 8085 |

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
├── user-service/       # User auth (JWT), passenger management, SMS login
└── admin-service/      # Admin backend management (CRUD, statistics)
```

### Admin Service Architecture

admin-service 是独立的后台运营管理微服务，具有以下特点：

**核心设计**：
- **独立认证**: 使用 `t_admin_user` 表存储管理员账户，与普通用户 `t_user` 分离
- **Token 区分**: JWT token 中包含 `type: "ADMIN"` 标识，网关据此区分管理员和普通用户
- **游标分页**: 使用 `lastId + pageSize` 游标分页，适配未来分库分表
- **直连数据库**: 管理后台流量低，直接访问数据库，不通过 Feign 调用其他服务
- **统计缓存**: 统计类数据使用 `SafeCacheTemplate` 进行 Redis 缓存，分页查询直连数据库

**API 端点**：
| 模块 | 端点 | 说明 |
|------|------|------|
| 认证 | `/api/admin/auth/login` | 管理员登录 |
| 认证 | `/api/admin/auth/info` | 获取当前管理员信息 |
| 用户 | `/api/admin/user/list` | 用户列表（游标分页） |
| 用户 | `/api/admin/user/{id}/status` | 切换用户状态 |
| 用户 | `/api/admin/user/{id}/passengers` | 用户乘车人列表 |
| 列车 | `/api/admin/train/list` | 列车列表 |
| 列车 | `/api/admin/train/{id}/sale-status` | 更新售卖状态 |
| 车站 | `/api/admin/station/list` | 车站列表 |
| 车站 | `/api/admin/station/all` | 所有车站（下拉用） |
| 订单 | `/api/admin/order/list` | 订单列表 |
| 订单 | `/api/admin/order/{orderSn}` | 订单详情 |
| 统计 | `/api/admin/stats/dashboard` | Dashboard 统计 |
| 统计 | `/api/admin/stats/order-trend` | 订单趋势 |
| 统计 | `/api/admin/stats/train-distribution` | 列车类型分布 |
| 统计 | `/api/admin/stats/hot-routes` | 热门线路 |

**数据表**：
- `t_admin_user`: 管理员账户（username, password[BCrypt], role, status）
- `t_user`: 普通用户（phone, email, status）
- `t_passenger`: 乘车人信息
- `t_train`: 列车信息
- `t_station`: 车站信息
- `t_order`: 订单信息

**网关路由**：
- `/api/admin/**` -> admin-service
- 网关验证 Admin Token，注入 `X-Admin-Id`, `X-Admin-Name`, `X-User-Type` 请求头

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
  - `/api/admin/**` -> admin-service (requires Admin token with `type: "ADMIN"`)

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

**缓存 Key 常量管理**:
所有缓存 Key 统一在 `Frameworks/common/src/main/java/com/lalal/modules/constant/cache/CacheConstant.java` 中定义：

```java
// 使用示例
String key = CacheConstant.trainTicketRemainingKey(trainId, date, seatType);
String userKey = CacheConstant.userDetailById(userId);
```

**已有缓存 Key**:
| 方法 | Key 格式 | 用途 |
|------|----------|------|
| `requestIdKey(requestId)` | `REQUEST::{requestId}` | 请求幂等性 |
| `trainTicketRemainingKey(trainId, date, seatType)` | `TICKET::REMAINING::{trainId}::{date}::{seatType}` | 火车余票 |
| `trainTicketDetailKey(trainId, date, carriageNumber)` | `TICKET::DETAIL::{trainId}::{date}::{carriageNumber}` | 余票详情 |
| `trainRouteKey(startRegion, endRegion)` | `TRAIN::ROUTE::{startRegion}::{endRegion}` | 火车路线 |
| `trainSeatType(trainId)` | `TRAIN::SEAT_TYPE::{trainId}` | 座位类型 |
| `trainStation(trainId)` | `TRAIN::STATION::{trainId}` | 火车站台 |
| `trainCarriage(trainId)` | `TRAIN::CARRIAGE::{trainId}` | 车厢信息 |
| `trainCodeToDetail(trainNum)` | `TRAIN::CODE::{trainNum}` | 车次号映射 |
| `userDetailById(id)` | `USER::DETAIL::{id}` | 用户详情 |
| `userDetailByPhone(phone)` | `USER::DETAIL::PHONE::{phone}` | 用户手机号 |
| `smsLoginCodeKey(phone)` | `SMS::LOGIN::CODE::{phone}` | 短信验证码 |

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

## Enums and Constants

### 枚举类定义 (Frameworks/common/src/main/java/com/lalal/modules/constant/)

| 枚举类 | 说明 |
|--------|------|
| `SeatType` | 座位类型：硬座(0)、二等座(1)、一等座(2)、商务座(3)、软座(4)、硬卧(5)、软卧(6) |

**新增枚举规范**:
- 枚举类放在 `Frameworks/common/src/main/java/com/lalal/modules/constant/` 目录
- 必须包含 `code` (int) 和 `description` (String) 字段
- 提供 `fromCode(int code)` 静态方法用于反向查找


