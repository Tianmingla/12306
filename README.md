# 12306 铁路票务系统

基于 Spring Cloud + React 的高性能铁路票务系统，支持高并发购票、座位选择、订单管理等核心功能。

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                      前端 (React + Vue3)                     │
│                  12306 用户端  |  admin 管理端                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Gateway (8080)                             │
│         流量监控 | JWT认证 | 请求追踪 | 路由分发              │
└─────────────────────────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         ▼                    ▼                    ▼
   ┌───────────┐      ┌───────────┐      ┌───────────┐
   │  Ticket   │◄────│   Seat   │      │   Order   │
   │  8081     │ Feign │   8082   │      │   8083    │
   └─────┬─────┘      └───────────┘      └─────┬─────┘
         │                                      │
         │              ┌───────────┐            │
         └─────────────►│   User   │◄───────────┘
                        │   8084   │
                        └───────────┘
                              │
                              ▼
                     ┌─────────────────┐
                     │  RocketMQ      │
                     │  异步消息队列   │
                     └─────────────────┘
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.0.7 + Spring Cloud |
| 服务调用 | OpenFeign |
| 数据库 | MySQL 8.0 + MyBatis-Plus |
| 缓存 | Redis + Redisson |
| 消息队列 | RocketMQ |
| 前端用户端 | React 18 + TypeScript + Vite |
| 前端管理端 | Vue 3 + Arco Design + TypeScript |

## 核心功能

### 票务核心
- [x] 车票搜索（支持中转换乘）
- [x] 座位选择（自动/手动选座）
- [x] 票价计算（多维度计价）
- [x] 订单管理（创建/支付/退款/取消）

### 高并发处理
- [x] 流量监控（QPS > 100 自动标记高峰）
- [x] 异步购票（Redis + MQ 削峰）
- [x] 前端轮询（状态实时反馈）
- [x] 幂等性保护（分布式锁）

### 用户系统
- [x] 短信验证码登录
- [x] 乘车人管理
- [x] 订单历史

### 后台管理
- [x] 数据统计 Dashboard
- [x] 用户/车次/订单管理
- [x] 退款处理

## 快速开始

### 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8.0
- Redis 6.0+
- RocketMQ 4.9+

### 1. 初始化数据库

```bash
mysql -u root -p < createTable.sql
```

### 2. 导入测试数据

```bash
cd DataScript
pip install -r requirements.txt
python 1_import_stations.py
python 2_import_trains.py
# ... 其他脚本
```

### 3. 启动后端服务

```bash
# 编译
mvn clean install -DskipTests

# 按顺序启动（或使用 IDE）
java -jar Services/gateway-service/target/gateway-service.jar
java -jar Services/user-service/target/user-service.jar
java -jar Services/seat-service/target/seat-service.jar
java -jar Services/ticket-service/target/ticket-service.jar
java -jar Services/order-service/target/order-service.jar
java -jar Services/admin-service/target/admin-service.jar
```

### 4. 启动前端

```bash
# 用户端
cd 12306
npm install
npm run dev      # http://localhost:5173

# 管理端
cd admin
npm install
npm run dev      # http://localhost:5174
```

## 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| Gateway | 8080 | API 网关 |
| Ticket | 8081 | 车票服务 |
| Seat | 8082 | 座位服务 |
| Order | 8083 | 订单服务 |
| User | 8084 | 用户服务 |
| Admin | 8085 | 后台管理 |

## 默认账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | 123456 |
| 测试用户 | 使用短信验证码登录 |

## 项目结构

```
12306/
├── Services/              # 后端微服务
│   ├── gateway-service/  # API 网关
│   ├── ticket-service/   # 车票服务
│   ├── seat-service/    # 座位服务
│   ├── order-service/    # 订单服务
│   ├── user-service/     # 用户服务
│   └── admin-service/   # 后台管理服务
├── Frameworks/           # 公共框架
│   ├── common/          # 通用 DTO、枚举
│   ├── cache/          # Redis 封装
│   ├── Idempotent/      # 幂等性模块
│   └── mq/             # 消息队列封装
├── 12306/               # 前端用户端 (React)
├── admin/               # 前端管理端 (Vue3)
├── DataScript/          # 数据导入脚本
└── createTable.sql      # 数据库建表脚本
```

## 高峰购票流程

```
用户发起购票请求
       │
       ▼
  Gateway 流量监控
  检测 QPS > 100
       │
       ▼
  设置 Redis key
  traffic:peak:status = true
       │
       ▼
  TicketService 判断
       │
       ├── 低峰: 同步 OpenFeign 调用
       │         返回订单号
       │
       └── 高峰: 异步 MQ 处理
                 返回 requestId
                         │
                         ▼
               前端每 2s 轮询状态
                         │
                         ▼
               Consumer 处理完成
               更新 Redis + MQ ACK
                         │
                         ▼
               前端跳转支付页面
```

## License

MIT License
