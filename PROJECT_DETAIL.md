# 12306 铁路票务系统 - 项目详细介绍

> 本文档详细介绍 12306 铁路票务系统的各个模块，用于毕业论文写作参考。

---

## 目录

1. [DataScript 数据脚本](#1-datascript-数据脚本)
2. [Admin 前端后台界面](#2-admin-前端后台界面)
3. [12306 前端前台界面](#3-12306-前端前台界面)
4. [Frameworks 后端框架模块](#4-frameworks-后端框架模块)
5. [Services 微服务模块](#5-services-微服务模块)
6. [项目整体总结](#6-项目整体总结)

---

## 1. DataScript 数据脚本

> 位置: `DataScript/`
> 语言: Python
> 用途: 数据处理、批量导入、模拟数据生成

### 1.1 项目概述

DataScript 目录包含一系列 Python 脚本，用于：
- 从原始数据文件导入车站信息
- 导入列车时刻表数据
- 生成车厢配置数据
- 生成座位布局数据
- 导入列车经停站点数据

### 1.2 脚本详解

#### 1.2.1 `1_import_stations.py` - 车站数据导入

**功能概述**：从 12306 官方 `station_name.txt` 文件解析车站数据并导入到 `t_station` 表。

**数据源格式**：
```
station_names = '@bjb|北京北|VAP|beijingbei|bjb|0|0501|北京市|...'
```
解析规则：`@电报码|车站名|电报码|拼音全拼|拼音简写|排序|地区编码|地区名称|...`

**核心流程**：
1. 正则提取 `station_names` 变量内容
2. 按 `@` 分割，解析各字段
3. 数据清洗：过滤无效/空字段，按车站名去重
4. 批量插入：`INSERT ... ON DUPLICATE KEY UPDATE` 实现增量导入
5. 校验结果：统计总数、地区分布

**输出表**：`t_station`（code, name, spell, region, region_name）

---

#### 1.2.2 `2_import_trains.py` - 列车数据导入

**功能概述**：从 `data/` 目录下的 JSON 文件中提取车次信息并导入到 `t_train` 表。

**目录结构**：
```
data/
├── G/    # 高铁数据
├── D/    # 动车数据
├── Z/    # 直达特快
├── T/    # 特快
├── K/    # 快速
└── index/ # 索引（跳过）
```

**车次类型识别**：
| 首字母 | train_type | train_brand | 说明 |
|--------|------------|-------------|------|
| G/C | 0 | G | 高铁/城际高铁 |
| D | 1 | D | 动车 |
| Z/T/K/Y/S/L | 2 | 首字母 | 普速列车 |

**核心流程**：
1. 遍历目录，收集唯一车次号
2. 识别车次类型和品牌
3. 过滤已存在的车次
4. 批量插入：`INSERT IGNORE`

**输出表**：`t_train`（train_number, train_type, train_brand, sale_status）

---

#### 1.2.3 `3_import_train_stations.py` - 列车站点数据导入

**功能概述**：从 JSON 文件提取车次经停站信息，导入到 `t_train_station` 表。

**JSON 数据格式**：
```json
{
  "data": {
    "data": [
      {"station_no": "1", "station_name": "北京南", "arrive_time": "--", "start_time": "08:00", ...},
      ...
    ]
  }
}
```

**核心处理**：
- 时间解析：`--` 转为 `None`，首站到站时间为空，末站离站时间为空
- 停留时间计算：`stopover = departure_time - arrival_time`（处理跨天）
- 去重逻辑：同一车次同一序号只保留一条

**输出表**：`t_train_station`（train_id, train_number, station_id, station_name, sequence, arrival_time, departure_time, stopover_time, arrive_day_diff）

---

#### 1.2.4 `4_generate_seats_and_carriages.py` - 车厢座位数据生成

**功能概述**：为每个车次生成符合其实际类型的车厢和座位配置数据。

**列车配置规则**：

| 车次类型 | 编组 | 车厢配置 |
|----------|------|----------|
| G（高铁） | 16节长编组 | 商务座(2节) + 一等座(2节) + 二等座(12节) |
| C（城际） | 8节编组 | 全部二等座(8节)，每节100座 |
| D（动车） | 8节编组 | 一等座(1节,40座) + 二等座(7节,86座/节) |
| Z（直达特快） | 6节 | 软卧(1节,32铺) + 硬卧(4节,66铺/节) + 硬座(1节,118座) |
| T（特快） | 5节 | 软卧(1节) + 硬卧(3节) + 硬座(1节) |
| K（快速） | 4节 | 硬卧(3节) + 硬座(1节) |

**座位号生成规则**：
- 二等座：5座排（A-B-C-D-F），如 `01A`, `01B`, ...
- 一等座：4座排（A-C-D-F）
- 商务座：3座排（A-C-F）
- 硬座：纯数字编号 `001`-`118`
- 卧铺：按铺位编号

**输出表**：`t_carriage`, `t_seat`

---

#### 1.2.5 `5_generate_users.py` - 用户数据生成

**功能概述**：生成 100 万条测试用户数据到 `t_user` 表。

**用户特征**：
- **手机号**：11位，符合中国号段规则（13/15/18/19开头）
- **邮箱**：30% 用户有邮箱，格式为 `{phone}@{domain}`
- **状态分布**：99% 正常(0)，1% 禁用(1)
- **创建时间**：过去 3 年内随机分布，偏向近期

**性能优化**：
- 批量插入：每批 10,000 条
- 去重：内存维护 `used_phones` 集合
- 进度显示

**输出表**：`t_user`（phone, email, status, create_time, update_time）

---

#### 1.2.6 `6_generate_passengers.py` - 乘客数据生成

**功能概述**：为用户生成乘车人数据，每个用户平均 1.5-2.5 个乘客。

**乘客特征**：
- **真实姓名**：使用常见中文姓名库（50 个姓 + 100 个名）
- **身份证号**：符合校验规则（前 17 位随机 + 第 18 位校验码）
- **证件类型**：95% 身份证，其余护照/港澳台通行证
- **乘客类型**：根据年龄判断（儿童/学生/成人）

**身份证校验码算法**：
```python
weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
check_codes = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2']
```

**输出表**：`t_passenger`（user_id, real_name, id_card_type, id_card_number, passenger_type, phone）

---

#### 1.2.7 `7_generate_orders.py` - 订单数据生成

**功能概述**：生成 100 万条测试订单数据，模拟真实订单流程。

**订单状态分布**：
| 状态 | 占比 | 说明 |
|------|------|------|
| 待支付 | 5% | order_status = 0 |
| 已支付 | 70% | order_status = 1 |
| 已完成 | 20% | order_status = 2 |
| 已取消/已退票 | 5% | order_status = 3/4 |

**票价计算公式**：
```
price = base_price × type_multiplier × distance_factor
```
- **base_price**：根据座位类型（硬座50/二等座30/一等座50/商务座100/硬卧150/软卧300）
- **type_multiplier**：高铁1.2，动车1.0
- **distance_factor**：远期(>30天)1.1，临期(<3天)1.2，正常1.0

**座位号生成**：根据座位类型生成对应格式
- 二等座：`{row:02d}{A-F}`，如 `05A`
- 一等座：`{row:02d}{A/C/D/F}`
- 商务座：`{row:02d}{A/C/F}`

**输出表**：`t_order`, `t_order_item`

---

#### 1.2.8 `8_generate_station_distances.py` - 站间距离数据生成

**功能概述**：计算车次任意两站之间的距离，导入到 `t_station_distance` 表。

**距离估算**：
- 假设相邻站点平均间距 30 公里
- 实际生产环境应从铁路数据获取真实里程

**计算逻辑**：
```python
estimated_distance = (arrival_seq - departure_seq) × 30
```

**输出表**：`t_station_distance`（train_id, departure_station_name, arrival_station_name, distance）

---

#### 1.2.9 `9_generate_train_fare_configs.py` - 票价配置数据生成

**功能概述**：根据车次类型生成票价上浮配置。

**上浮类型说明**：
| 类型 | 上浮比例 | 适用车次 |
|------|----------|----------|
| 0 | 1.0 | 普通车（Y/L 临客） |
| 1 | 1.5 | 新型空调车 50% 上浮（G/C/D/Z/T/K） |
| 2 | 1.4 | 新型空调车一档折扣 |
| 3 | 1.3 | 新型空调车二档折扣 |
| 4 | 2.8 | 高级软卧 180% 上浮 |
| 5 | 3.08 | 高级软卧 208% 上浮 |

**输出表**：`t_train_fare_config`（train_id, train_number, surcharge_type, is_peak_season）

---

## 2. Admin 前端后台界面

> 位置: `admin/`
> 技术栈: Vue 3 + Arco Design + Vite + TypeScript + Pinia

### 2.1 项目概述

Admin 是铁路票务系统的后台运营管理界面，为管理员提供数据管理、订单处理、用户管理等核心功能。

### 2.2 技术架构

#### 2.2.1 技术栈
| 技术 | 说明 |
|------|------|
| Vue 3.4+ | 渐进式前端框架，使用 Composition API |
| Arco Design Vue | 企业级 UI 组件库（深色铁路主题） |
| Vite 5 | 新一代构建工具，快速热更新 |
| TypeScript | 类型安全 |
| Pinia | 状态管理 |
| Axios | HTTP 客户端 |
| ECharts | 数据可视化图表 |

#### 2.2.2 项目目录结构
```
admin/
├── src/
│   ├── api/           # API 调用层
│   │   ├── user.ts    # 用户管理 API
│   │   ├── train.ts   # 车次管理 API
│   │   ├── station.ts # 站点管理 API
│   │   ├── order.ts   # 订单管理 API
│   │   ├── stats.ts   # 统计 API
│   │   └── index.ts   # API 导出汇总
│   ├── types/         # TypeScript 类型定义
│   │   ├── user.ts    # 用户类型
│   │   ├── train.ts   # 车次类型
│   │   ├── station.ts # 站点类型
│   │   ├── order.ts   # 订单类型
│   │   └── stats.ts   # 统计类型
│   ├── views/         # 页面组件
│   │   ├── login/     # 登录页
│   │   ├── dashboard/ # 数据统计首页
│   │   ├── train/     # 车票管理
│   │   │   ├── TrainList.vue     # 车次列表
│   │   │   ├── StationManage.vue  # 站点管理
│   │   │   └── RouteManage.vue    # 线路管理
│   │   ├── order/     # 订单管理
│   │   │   ├── OrderList.vue      # 订单列表
│   │   │   └── RefundManage.vue   # 退款管理
│   │   └── system/     # 系统管理
│   │       ├── UserManage.vue     # 用户管理
│   │       ├── RoleManage.vue     # 角色管理
│   │       └── OperationLog.vue   # 操作日志
│   ├── components/    # 公共组件
│   ├── layouts/       # 布局组件
│   │   └── BasicLayout.vue  # 主布局（侧边栏+头部）
│   ├── store/         # Pinia 状态管理
│   │   ├── user.ts    # 用户状态
│   │   └── app.ts     # 应用状态
│   ├── router/        # 路由配置
│   │   └── index.ts   # 路由定义+守卫
│   ├── utils/         # 工具函数
│   │   └── request.ts # Axios 封装
│   └── main.ts        # 入口文件
├── vite.config.ts     # Vite 配置
└── package.json      # 依赖配置
```

### 2.3 核心功能模块

#### 2.3.1 登录认证
**文件**: `views/login/index.vue`

**功能**:
- 管理员用户名密码登录
- JWT Token 存储到 localStorage
- 路由守卫自动跳转未登录用户

**流程**:
```
用户输入账号密码 → POST /api/admin/auth/login
    ↓
验证成功 → 存储 Token → 跳转 Dashboard
验证失败 → 显示错误信息
```

#### 2.3.2 数据统计 Dashboard
**文件**: `views/dashboard/index.vue`

**功能**:
- 今日订单量统计卡片
- 订单状态分布饼图（ECharts）
- 订单趋势折线图
- 热门线路 TOP 10
- 列车类型分布

**核心接口**:
```typescript
// 获取统计数据
GET /api/admin/stats/dashboard

// 获取订单趋势
GET /api/admin/stats/order-trend?startDate=&endDate=

// 获取热门线路
GET /api/admin/stats/hot-routes?limit=10
```

#### 2.3.3 用户管理
**文件**: `views/system/UserManage.vue`

**功能**:
- 用户列表（游标分页）
- 用户状态切换（禁用/启用）
- 查看用户详情和乘车人列表
- 重置用户密码

**类型定义** (`types/user.ts`):
```typescript
interface User {
  id: number
  username: string
  phone: string
  email?: string
  status: UserStatus  // 0-正常, 1-禁用
  createTime: string
  updateTime: string
}

interface Passenger {
  id: number
  userId: number
  realName: string
  idCardType: IdCardType  // 0-身份证, 1-护照, 2-港澳通行证, 3-台湾通行证
  idCardNumber: string
  passengerType: PassengerType  // 0-成人, 1-儿童, 2-学生, 3-残疾军人
  phone?: string
}
```

#### 2.3.4 订单管理
**文件**: `views/order/OrderList.vue`

**功能**:
- 订单列表查询
- 订单状态筛选
- 订单详情弹窗
- 取消订单
- 退款处理

**订单状态**:
| 状态码 | 说明 |
|--------|------|
| 0 | 待支付 |
| 1 | 已支付 |
| 2 | 已完成 |
| 3 | 已取消 |
| 4 | 已退款 |

#### 2.3.5 车次管理
**文件**: `views/train/TrainList.vue`

**功能**:
- 车次列表展示
- 售卖状态切换
- 座位配置管理
- 站点配置管理

**座位配置** (`components/SeatConfigModal.vue`):
- 查看车厢详情
- 编辑座位数量
- 保存配置

#### 2.3.6 站点管理
**文件**: `views/train/StationManage.vue`

**功能**:
- 站点列表（支持名称搜索）
- 新增站点
- 编辑站点信息
- 删除站点

### 2.4 API 层设计

#### 2.4.1 API 封装
**文件**: `utils/request.ts`

**特性**:
- 统一请求拦截器（添加 Token）
- 统一响应拦截器（错误处理）
- TypeScript 类型支持
- 自动重试机制

#### 2.4.2 统一响应格式
```typescript
interface Result<T> {
  code: number      // 200-成功，其他-失败
  message?: string
  data?: T
}

interface PageResult<T> {
  list: T[]         // 数据列表
  total: number     // 总数
  pageSize: number  // 每页大小
  pageNum: number   // 当前页
}
```

#### 2.4.3 API 分类

| 模块 | 文件 | 说明 |
|------|------|------|
| 用户 | `api/user.ts` | 登录、信息、状态管理 |
| 车次 | `api/train.ts` | 车次 CRUD、状态管理 |
| 站点 | `api/station.ts` | 站点 CRUD |
| 订单 | `api/order.ts` | 订单查询、退款 |
| 统计 | `api/stats.ts` | Dashboard 数据 |

### 2.5 状态管理

#### 2.5.1 用户状态 (`store/user.ts`)
```typescript
interface UserState {
  token: string | null
  userInfo: User | null
  isLoggedIn: boolean
}
```

#### 2.5.2 应用状态 (`store/app.ts`)
```typescript
interface AppState {
  collapsed: boolean      // 侧边栏折叠状态
  theme: 'dark' | 'light'
}
```

### 2.6 路由设计

**路由结构**:
```
/login           → 登录页
/                → 主布局
  /dashboard     → 数据统计
  /train
    /list       → 车次管理
    /station    → 站点管理
    /route      → 线路管理
  /order
    /list       → 订单列表
    /refund     → 退款管理
  /system
    /user       → 用户管理
    /role       → 角色管理
    /log        → 操作日志
```

**路由守卫**:
- 未登录自动跳转 `/login`
- 已登录访问 `/login` 跳转首页

### 2.7 UI 主题

**配色方案**:
| 用途 | 颜色 | 说明 |
|------|------|------|
| 主色 | `#C41E3A` | 铁路红 |
| 辅助色 | `#1E3A5F` | 沉稳蓝 |
| 成功色 | `#10B981` | 绿色 |
| 警告色 | `#F59E0B` | 橙黄色 |
| 危险色 | `#EF4444` | 红色 |
| 背景色 | `#0F172A` | 深色背景 |
| 卡片色 | `#1E293B` | 卡片背景 |

---

---

## 3. 12306 前端前台界面

> 位置: `12306/`
> 技术栈: React 18 + TypeScript + Vite + Tailwind CSS

### 3.1 项目概述

12306 是铁路票务系统的前台用户界面，为用户提供车票查询、预订、订单管理等核心功能。

### 3.2 技术架构

#### 3.2.1 技术栈
| 技术 | 说明 |
|------|------|
| React 18 | UI 库，支持 Hooks |
| TypeScript | 类型安全 |
| Vite | 快速构建工具 |
| Tailwind CSS | 原子化 CSS 框架 |
| Lucide React | 图标库 |
| React Router | 路由管理（本项目中使用状态驱动） |

#### 3.2.2 项目目录结构
```
12306/
├── components/           # React 组件
│   ├── Navbar.tsx              # 顶部导航栏
│   ├── SearchWidget.tsx         # 搜索组件
│   ├── TrainList.tsx            # 车次列表
│   ├── BookingModal.tsx         # 购票弹窗
│   ├── LoginModal.tsx           # 登录弹窗
│   ├── PassengerManageModal.tsx # 乘车人管理
│   ├── OrderDetailPage.tsx     # 订单详情页
│   ├── OrderHistoryPage.tsx     # 订单历史页
│   ├── StationScreenPage.tsx    # 车站大屏
│   ├── WaitlistPage.tsx         # 候补购票
│   ├── StationGuidePage.tsx     # 车站指南
│   ├── TravelGuidePage.tsx      # 出行指南
│   ├── AIAssistant.tsx          # AI 助手
│   ├── CitySelector.tsx         # 城市选择器
│   ├── Features.tsx             # 首页功能展示
│   └── FilterPanel.tsx          # 筛选面板
├── services/             # API 服务层
│   ├── http.ts                 # HTTP 封装
│   ├── ticketService.ts        # 车票 API
│   ├── orderService.ts         # 订单 API
│   ├── userService.ts          # 用户 API
│   ├── passengerService.ts     # 乘车人 API
│   ├── stationService.ts       # 车站 API
│   └── geminiService.ts        # AI 服务
├── types.ts              # 全局类型定义
├── App.tsx               # 根组件
├── index.tsx             # 入口文件
└── vite.config.ts        # Vite 配置
```

### 3.3 核心功能模块

#### 3.3.1 首页 (App.tsx)
**组件**: `SearchWidget`, `Features`

**功能**:
- Hero 背景展示
- 出发地/目的地选择
- 日期选择
- 单程/往返/中转切换
- 搜索执行

**视图状态机** (`AppView`):
```typescript
enum AppView {
  HOME = 'HOME',              // 首页
  SEARCH_RESULTS = 'SEARCH_RESULTS',  // 搜索结果
  ORDER_DETAIL = 'ORDER_DETAIL',      // 订单详情
  ORDER_HISTORY = 'ORDER_HISTORY',     // 历史订单
  STATION_SCREEN = 'STATION_SCREEN',   // 车站大屏
  WAITLIST = 'WAITLIST',              // 候补购票
  STATION_GUIDE = 'STATION_GUIDE',    // 车站指南
  TRAVEL_GUIDE = 'TRAVEL_GUIDE'       // 出行指南
}
```

#### 3.3.2 车票搜索
**组件**: `TrainList.tsx`

**功能**:
- 显示搜索结果列表
- 高铁/动车筛选
- 座位类型筛选
- 出发时间筛选
- 中转/直达切换
- 点击进入购票

**搜索参数**:
```typescript
interface SearchParams {
  from: string           // 出发地
  to: string             // 目的地
  date: string           // 日期 yyyy-MM-dd
  onlyHighSpeed: boolean // 仅高铁动车
  searchType: 'oneWay' | 'roundTrip' | 'transfer'
  returnDate?: string    // 返程日期
  midStation?: string    // 中转站
}
```

#### 3.3.3 购票流程
**组件**: `BookingModal.tsx`

**流程**:
```
选择座位类型 → 选择乘车人 → 选座（可选）→ 提交订单
    ↓
同步模式: 等待结果 → 跳转支付
高峰模式: PROCESSING → 轮询状态 → 成功跳转/失败提示
```

**购票请求**:
```typescript
interface PurchaseTicketRequest {
  account: string          // 登录手机号
  IDCardCodelist: number[] // 乘车人 ID 列表
  seatTypelist: string[]   // 座位类型列表
  chooseSeats?: string[]    // 偏好座位
  trainNum: string         // 车次号
  startStation: string     // 出发站
  endStation: string       // 到达站
  date: string            // 乘车日期
}
```

**响应处理**:
```typescript
// 同步模式成功
{ status: "SUCCESS", orderSn: "xxx" }

// 高峰模式（异步）
{ status: "PROCESSING", requestId: "xxx" }
```

#### 3.3.4 高峰异步购票
**组件**: `BookingModal.tsx`

**轮询机制**:
```typescript
const startPolling = (reqId: string) => {
  const poll = async () => {
    const json = await checkTicketPurchaseStatus(reqId);
    if (json.data?.status === 'SUCCESS') {
      onPurchaseSuccess(json.data.orderSn);
    } else if (json.data?.status === 'FAILED') {
      showError(json.data.errorMessage);
    } else {
      // 每 2 秒轮询
      setTimeout(poll, 2000);
    }
  };
  poll();
};
```

**状态查询响应**:
```typescript
interface AsyncTicketCheckResponse {
  data: {
    requestId: string
    status: 'PROCESSING' | 'SUCCESS' | 'FAILED'
    orderSn?: string
    errorMessage?: string
  }
}
```

#### 3.3.5 订单管理
**组件**: `OrderDetailPage.tsx`, `OrderHistoryPage.tsx`

**功能**:
- 订单详情展示
- 支付宝支付
- 取消订单
- 退款申请

**订单状态**:
| 状态 | 说明 |
|------|------|
| 0 | 待支付 |
| 1 | 已支付 |
| 2 | 已完成 |
| 3 | 已取消 |
| 4 | 已退款 |

#### 3.3.6 乘车人管理
**组件**: `PassengerManageModal.tsx`

**功能**:
- 查看乘车人列表
- 新增乘车人
- 编辑乘车人信息
- 删除乘车人
- 设置默认乘车人

#### 3.3.7 其他功能页面
| 页面 | 组件 | 功能 |
|------|------|------|
| 车站大屏 | `StationScreenPage.tsx` | 显示车站列车信息 |
| 候补购票 | `WaitlistPage.tsx` | 候补车票申请 |
| 车站指南 | `StationGuidePage.tsx` | 车站楼层/设施导览 |
| 出行指南 | `TravelGuidePage.tsx` | 乘车须知/规定 |
| AI 助手 | `AIAssistant.tsx` | 智能客服问答 |

### 3.4 API 层设计

#### 3.4.1 HTTP 封装 (`services/http.ts`)
```typescript
const API_BASE = 'http://localhost:8080/api';

function authHeaders(): HeadersInit {
  const token = localStorage.getItem('token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}
```

#### 3.4.2 服务分类

| 服务 | 文件 | 功能 |
|------|------|------|
| 车票 | `ticketService.ts` | 搜索、购票、状态查询 |
| 订单 | `orderService.ts` | 详情、支付、退款、取消 |
| 用户 | `userService.ts` | 登录、信息 |
| 乘车人 | `passengerService.ts` | CRUD |
| 车站 | `stationService.ts` | 车站搜索 |

### 3.5 类型定义 (`types.ts`)

#### 3.5.1 车票相关
```typescript
interface TrainTicket {
  id: string
  trainNumber: string
  fromStation: string
  toStation: string
  departureTime: string
  arrivalTime: string
  duration: string
  price: number
  seatsAvailable: Record<string, number>  // { '商务座': 10, '二等座': 50 }
  prices?: Record<string, number>
  type: 'G' | 'D' | 'K' | 'Z'
  transferCount?: number
  segments?: TicketSegment[]
}
```

#### 3.5.2 用户相关
```typescript
interface UserInfoResponse {
  phone: string
  userId: string
  role: string
}

interface PassengerApi {
  id: number
  realName: string
  idCardType: number
  idCardNumber: string
  passengerType: number
  phone?: string | null
}
```

### 3.6 状态管理

本项目采用 **React Context + localStorage** 模式：

| 状态 | 存储位置 | 说明 |
|------|----------|------|
| Token | localStorage | JWT 认证令牌 |
| 用户手机号 | localStorage | 显示用户名 |
| 当前视图 | React State | AppView 枚举 |
| 搜索参数 | React State | 页面间传递 |

---

---

## 4. Frameworks 后端框架模块

> 位置: `Frameworks/`
> 架构: Spring Boot Starter + 自定义框架

### 4.1 模块概述

Frameworks 目录包含项目的公共框架模块，为各个微服务提供基础设施支持。

### 4.2 模块结构

```
Frameworks/
├── common/        # 公共模块（DTO、枚举、工具类、Result封装）
├── database/      # 数据库模块（MyBatis-Plus配置、实体基类）
├── cache/        # 缓存模块（Redis封装、防缓存击穿）
├── log/          # 日志模块（切面监控）
├── Idempotent/   # 幂等性模块（分布式幂等控制）
└── mq/           # 消息队列模块（MQ封装）
```

---

### 4.3 common 公共模块

#### 4.3.1 统一响应封装 (`result/Result.java`)

**功能**: 所有 API 响应统一封装为 `Result<T>` 结构

```java
@Data
public class Result<T> implements Serializable {
    private String message;
    private T data;
    private String requestId;  // 分布式追踪ID
    private Integer code;

    public static <T> Result<T> success(T data) { ... }
    public static <T> Result<T> fail(String message, Integer code) { ... }
}
```

**响应码定义** (`enumType/ReturnCode.java`):
```java
public enum ReturnCode {
    success(200, "success"),
    fail(500, "系统异常"),
    param_error(400, "参数错误"),
    unauthorized(401, "未授权"),
    forbidden(403, "禁止访问"),
    not_found(404, "资源不存在"),
    // ... 更多状态码
}
```

#### 4.3.2 请求上下文 (`context/RequestContext.java`)

**功能**: ThreadLocal 存储当前请求上下文信息

```java
public class RequestContext {
    public static String getRequestId()  // 获取请求追踪ID
    public static Long getUserId()        // 获取当前用户ID
    public static void setUserId(Long userId)  // 设置用户ID
}
```

#### 4.3.3 缓存 Key 常量 (`constant/cache/CacheConstant.java`)

**功能**: 统一管理所有 Redis 缓存 Key 命名规范

| Key 模板 | 用途 |
|----------|------|
| `TICKET::REMAINING::{trainId}::{date}::{seatType}` | 火车余票 |
| `TICKET::DETAIL::{trainId}::{date}::{carriageNumber}` | 余票详情 |
| `TRAIN::ROUTE::{start}::{end}` | 火车路线 |
| `USER::DETAIL::{id}` | 用户详情 |
| `FARE::DISTANCE::{trainId}::{dep}::{arr}` | 站间距离 |

#### 4.3.4 座位类型枚举 (`enumType/train/SeatType.java`)

```java
public enum SeatType {
    HARD_SEAT(0, "硬座"),
    SECOND_CLASS(1, "二等座"),
    FIRST_CLASS(2, "一等座"),
    BUSINESS(3, "商务座"),
    SOFT_SEAT(4, "软座"),
    HARD_SLEEPER(5, "硬卧"),
    SOFT_SLEEPER(6, "软卧"),
    NO_SEAT(7, "无座");

    public static SeatType findByCode(int code) { ... }
}
```

#### 4.3.5 工具类

| 类 | 功能 |
|----|------|
| `DateUtils` | 日期时间工具 |
| `RequestUtil` | 请求工具 |
| `UUIDGenerator` | UUID 生成器 |
| `RequestIdGenerator` | 请求ID生成器 |

---

### 4.4 cache 缓存模块

#### 4.4.1 SafeCacheTemplate (`cache/SafeCacheTemplate.java`)

**设计目标**:
1. **防缓存击穿**: 分布式锁保证只有一个线程加载数据
2. **防缓存雪崩**: 支持 TTL 随机化
3. **自定义序列化**: 关闭 `@class` 类型信息，避免缓存失效

#### 4.4.2 核心方法

```java
public class SafeCacheTemplate {
    // 基础操作
    void set(String key, Object value, long timeout, TimeUnit unit)
    <T> T get(String key, TypeReference<T> typeReference)

    // 安全加载（防击穿）
    <T> T safeGet(String key, Supplier<T> loader,
                   TypeReference<T> typeReference,
                   long cacheTtl, TimeUnit timeUnit)

    // 批量操作
    <T> List<T> multiGet(List<String> keys, TypeReference<T> typeReference)
    <T> List<List<T>> multiLGet(List<String> keys, TypeReference<T> typeReference)

    // 分布式锁
    Boolean setIfAbsent(String key, Object value, long expireHours, TimeUnit timeUnit)
}
```

#### 4.4.3 使用示例

```java
// 普通缓存
safeCacheTemplate.set("user:1", user, 30, TimeUnit.MINUTES);
User user = safeCacheTemplate.get("user:1", new TypeReference<User>() {});

// 安全加载（防止缓存击穿）
User user = safeCacheTemplate.safeGet(
    "user:1",
    () -> userMapper.selectById(1),
    new TypeReference<User>() {},
    30,
    TimeUnit.MINUTES
);

// 分布式锁（幂等性）
Boolean locked = safeCacheTemplate.setIfAbsent(
    "lock:purchase:" + requestId,
    "1",
    30,
    TimeUnit.SECONDS
);
```

#### 4.4.4 Redis 序列化器

| 序列化器 | 说明 |
|----------|------|
| `DefaultValueRedisSerializer` | 默认 JSON 序列化 |
| `RawRedisSerializer` | 原始字节序列化 |

---

### 4.5 Idempotent 幂等性模块

#### 4.5.1 幂等性注解 (`Idempotent.java`)

**功能**: 基于 Redis + Redisson 分布式锁实现接口幂等性

```java
@Idempotent(
    key = "${header.X-User-Id}-${#dto.trainNum}-${#dto.date}",
    expire = 300,
    message = "购票请求正在处理中，请勿重复提交",
    cacheResult = true
)
@PostMapping("/purchase")
public Result<PurchaseTicketVO> purchaseTicket(...) { ... }
```

**注解参数**:
| 参数 | 说明 | 默认值 |
|------|------|--------|
| `key` | 幂等键（SpEL 表达式） | 类名:方法名:参数哈希 |
| `expire` | 过期时间（秒） | 300 |
| `cacheResult` | 是否缓存返回结果 | false |
| `message` | 重复请求提示 | "请勿重复提交" |

#### 4.5.2 幂等性切面 (`IdempotentAspect.java`)

**执行流程**:
1. 解析 SpEL 表达式生成 Key
2. Redis SETNX 尝试获取锁
3. 执行目标方法
4. 可选缓存返回结果
5. 释放锁（可选）

---

### 4.6 mq 消息队列模块

#### 4.6.1 MQ 服务接口 (`MessageQueueService.java`)

**功能**: 抽象 MQ 操作，支持多种实现（当前为 RocketMQ）

```java
public interface MessageQueueService {
    // 同步发送
    void send(String topic, Object message)
    void send(String topic, String tag, Object message)

    // 异步发送
    void sendAsync(String topic, Object message, SendCallback callback)

    // 延迟消息
    void sendDelay(String topic, Object message, long delayTime)

    // 顺序消息
    void sendOrderly(String topic, String key, Object message)
}
```

#### 4.6.2 消息体封装 (`Message.java`)

```java
@Data
public class Message implements Serializable {
    private String topic;      // 主题
    private String tag;        // 标签
    private String keys;       // 消息键
    private Object body;       // 消息体
    private Map<String, Object> headers;  // 扩展头
    private Long timestamp;    // 时间戳
}
```

#### 4.6.3 消费者基类 (`BaseMessageConsumer.java`)

**功能**: 提供幂等性处理基础能力

```java
public abstract class BaseMessageConsumer {
    // 处理消息，返回是否成功
    protected boolean process(Message message) {
        // 1. 幂等性检查
        // 2. 调用子类处理逻辑
        // 3. 返回处理结果
    }

    // 子类实现具体处理逻辑
    protected abstract void doProcess(Object msg);
}
```

#### 4.6.4 RocketMQ 实现

**配置** (`RocketMQProperties.java`):
```yaml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: default-producer-group
    send-message-timeout: 3000
    retry-times-when-send-failed: 2
```

**消费者注解**:
```java
@MessageConsumer(
    topic = "ticket-purchase-topic",
    tag = "purchase",
    consumerGroup = "ticket-purchase-consumer"
)
public class TicketPurchaseConsumer extends RocketMQBaseConsumer {
    @Override
    protected void doProcess(Object msg) {
        // 处理购票逻辑
    }
}
```

---

### 4.7 log 日志模块

#### 4.7.1 日志监控注解 (`LogMonitor.java`)

**功能**: 切面记录方法调用日志

```java
@LogMonitor
public void processOrder(Order order) { ... }
```

#### 4.7.2 日志切面 (`LogMonitorAspect.java`)

**输出格式**:
```
[LogMonitor] com.xxx.OrderService.processOrder | args={...} | result={...} | cost=125ms
```

---

### 4.8 database 数据库模块

#### 4.8.1 实体基类 (`BaseDO.java`)

**功能**: 所有实体继承基类，自动管理创建/更新时间

```java
@Data
public class BaseDO {
    private Long id;
    private Integer delFlag;  // 删除标记
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

#### 4.8.2 MyBatis-Plus 配置

**功能**:
- 自动填充创建/更新时间
- 逻辑删除支持
- 分页插件

---

---

## 5. Services 微服务模块

> 位置: `Services/`
> 架构: Spring Cloud 微服务 + OpenFeign

### 5.1 服务架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend (React)                         │
│                    12306/  admin/  (Browser)                   │
└─────────────────────────────────┬───────────────────────────────┘
                                  │ HTTP
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Gateway Service (8080)                        │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐ │
│  │ TrafficMonitorFilter │ │ JwtAuthFilter  │  │ RequestIdFilter│ │
│  │ QPS > 100 时标记   │  │ 令牌验证      │  │ 请求ID注入     │ │
│  │ traffic:peak:status │  │               │  │                │ │
│  └──────────────────┘  └──────────────────┘  └────────────────┘ │
└─────────────────────────────────┬───────────────────────────────┘
                                  │ Route
     ┌────────────────────────────┼────────────────────────────┐
     │                            │                            │
     ▼                            ▼                            ▼
┌─────────┐               ┌──────────────┐              ┌──────────┐
│  User   │               │   Ticket    │              │  Seat    │
│ Service │               │   Service   │              │  Service │
│ (8084)  │               │   (8081)   │              │  (8082)  │
└─────────┘               └──────┬───────┘              └──────────┘
     ▲                          │
     │ OpenFeign                 │ OpenFeign
     │                   ┌───────┴───────┐
     │                   ▼               ▼
     │            ┌──────────┐     ┌──────────┐
     │            │  Order   │     │   MQ    │
     │            │ Service  │     │ (Rocket) │
     │            │ (8083)   │     └──────────┘
     │            └──────────┘
     │                 ▲
     │                 │
     └─────────────────┘
          OpenFeign
```

### 5.2 服务端口映射

| 服务 | 端口 | 说明 |
|------|------|------|
| gateway-service | 8080 | API 网关，统一入口 |
| ticket-service | 8081 | 车票服务（核心） |
| seat-service | 8082 | 座位服务 |
| order-service | 8083 | 订单服务 |
| user-service | 8084 | 用户服务 |
| admin-service | 8085 | 后台管理服务 |

---

### 5.3 Gateway Service (网关服务)

**入口类**: `GatewayApplication.java`

#### 5.3.1 流量监控过滤器 (`TrafficMonitorFilter`)

**功能**: 实时统计 QPS，超过阈值时标记高峰状态

**工作流程**:
```
1. 每秒计数器 +1，Key: traffic:count:{timestamp}
2. 若 count > 100，设置 traffic:peak:status = true (TTL=5s)
3. 购票接口读取该状态决定走同步/异步路径
```

**配置** (`application.yml`):
```yaml
traffic:
  peak-threshold: 100  # QPS 阈值
  monitor-interval: 1s
```

#### 5.3.2 路由配置

| 路径 | 目标服务 |
|------|----------|
| `/api/user/**` | user-service |
| `/api/ticket/**` | ticket-service |
| `/api/seat/**` | seat-service |
| `/api/order/**` | order-service |
| `/api/admin/**` | admin-service |

#### 5.3.3 JWT 认证过滤器 (`JwtAuthenticationFilter`)

**功能**: 验证用户 Token，注入 `X-User-Id`、`X-User-Name` 请求头

---

### 5.4 Ticket Service (车票服务)

**入口类**: `TicketApplication.java`

#### 5.4.1 核心功能

| 接口 | 说明 |
|------|------|
| `GET /api/ticket/search` | 车票搜索 |
| `POST /api/ticket/purchase` | 购票 |
| `GET /api/ticket/check/{requestId}` | 查询异步购票状态 |

#### 5.4.2 购票流程（MQ 消息驱动架构）

系统采用纯 MQ 消息驱动架构，根据流量状态选择同步或异步路径：

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          用户购票请求                                         │
│                    POST /api/ticket/purchase                                 │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
                     ┌────────────────────────────────┐
                     │    TicketServiceImpl.purchase() │
                     │   检查 Redis: traffic:peak:status│
                     └────────────────┬───────────────┘
                                      │
               ┌──────────────────────┴──────────────────────┐
               │                                              │
               ▼ (非高峰)                                     ▼ (高峰)
    ┌──────────────────────┐                    ┌──────────────────────┐
    │ 同步处理 (OpenFeign) │                    │ 异步处理 (MQ)         │
    │                      │                    │                      │
    │ 1. 调用座位服务选座  │                    │ 1. 保存请求到 Redis  │
    │ 2. 计算票价         │                    │ 2. 发送到 seat-selection-topic│
    │ 3. 调用订单服务创建 │                    │ 3. 返回 PROCESSING   │
    │ 4. 返回订单号       │                    │    + requestId       │
    └──────────────────────┘                    └──────────────────────┘
```

##### 异步购票消息流

```
[前端请求] ──► ticket-service
                    │
                    ▼ 发送到: seat-selection-topic (tag: select)
             ┌─────────────────────────────────────────────┐
             │ SeatSelectionConsumer (seat-service)         │
             │ - 执行座位选择逻辑                            │
             │ - 锁定座位（Redis + Lua 脚本）                │
             │ - 填充计划发车/到达时间（从 t_train_station）  │
             └─────────────────────┬───────────────────────┘
                                   │
                                   ▼ 发送到: seat-selection-result-topic (tag: result)
             ┌─────────────────────────────────────────────┐
             │ SeatResultProcessorConsumer (ticket-service) │
             │ - 获取乘客信息（调用 user-service）            │
             │ - 计算票价                                    │
             │ - 构建订单创建请求                            │
             └─────────────────────┬───────────────────────┘
                                   │
                                   ▼ 发送到: order-creation-topic (tag: create)
             ┌─────────────────────────────────────────────┐
             │ OrderCreationConsumer (order-service)        │
             │ - 创建订单（status=0 待支付）                  │
             │ - 发送超时取消延迟消息（30分钟）                │
             │ - 初始化出行提醒                              │
             └─────────────────────┬───────────────────────┘
                                   │
                                   ▼ 发送到: order-creation-result-topic (tag: result)
             ┌─────────────────────────────────────────────┐
             │ OrderResultProcessorConsumer (ticket-service)│
             │ - 成功：更新缓存，返回订单号给前端             │
             │ - 失败：发送座位释放消息                      │
             └─────────────────────────────────────────────┘
```

#### 5.4.3 购票核心流程 (`processCorePurchase`)

```java
public PurchaseTicketVO processCorePurchase(...) {
    // 1. 查询乘客信息
    List<Passenger> passengers = userServiceClient.batchPassengers(...);

    // 2. 座位选择
    TicketDTO selectedSeats = seatServiceClient.select(seatRequest);

    // 3. 获取列车信息 + 查询发车/到达时刻
    TrainDO train = trainMapper.selectOne(...);
    fillPlanTimes(orderRequest, train, date); // 从 t_train_station 获取时刻

    // 4. 计算票价
    List<FareCalculationResultDTO> fares =
        fareCalculationService.batchCalculateFare(...);

    // 5. 创建订单（通过 Feign 调用）
    String orderSn = orderServiceClient.create(orderRequest);

    return PurchaseTicketVO.success(orderSn);
}
```

#### 5.4.4 MQ 消费者汇总

| 消费者 | Topic | Tag | 功能 |
|--------|-------|-----|------|
| SeatResultProcessorConsumer | seat-selection-result-topic | * | 处理选座结果，计算票价，发送订单创建请求 |
| OrderResultProcessorConsumer | order-creation-result-topic | * | 处理订单创建结果，更新缓存或发送座位释放 |

---

### 5.5 Seat Service (座位服务)

**入口类**: `SeatApplication.java`

#### 5.5.1 核心接口

| 接口 | 说明 |
|------|------|
| `POST /api/seat/select` | 座位选择 |

#### 5.5.2 座位选择服务 (`SeatSelectionService`)

**输入**:
```java
SeatSelectionRequestDTO {
    String trainNum;
    String startStation;
    String endStation;
    LocalDate date;
    List<PassengerDTO> passengers;  // {id, seatType, seatPreference}
}
```

**输出**:
```java
TicketDTO {
    List<TicketItem> items;  // {passengerId, carriageNum, seatNum, seatType}
}
```

#### 5.5.3 MQ 消费者

| 消费者 | Topic | Tag | 功能 |
|--------|-------|-----|------|
| SeatSelectionConsumer | seat-selection-topic | select | 处理选座请求（普通购票+候补），锁定座位，查询时刻信息，发送选座结果 |
| SeatReleaseConsumer | seat-release-topic | * | 释放座位（退票/取消/超时），触发候补兑现 |

#### 5.5.4 座位选择消息处理 (`SeatSelectionConsumer`)

**监听 Topic**: `seat-selection-topic`（Tag: `select`）

**处理流程**:
1. 构建选座请求，调用 `SeatSelectionService` 执行选座
2. 支持两种来源：`NORMAL`（普通购票）和 `WAITLIST`（候补订单）
3. 选座成功后，从 `t_train_station` 查询出发站和到达站的时刻信息
4. 将计划发车时间（`planDepartTime`）和到达时间（`planArrivalTime`）附加到结果消息中
5. 发送结果到 `seat-selection-result-topic`

**时刻查询逻辑**:
```java
// 查询出发站发车时间
TrainStationDO departStation = trainStationMapper.selectOne(...);
planDepartTime = runDate.atTime(departStation.getDepartureTime())
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

// 查询到达站到达时间（考虑跨天 arriveDayDiff）
TrainStationDO arriveStation = trainStationMapper.selectOne(...);
LocalDate arriveDate = runDate.plusDays(arriveStation.getArriveDayDiff());
planArrivalTime = arriveDate.atTime(arriveStation.getArrivalTime())
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
```

#### 5.5.5 座位释放消费者 (`SeatReleaseConsumer`)

**监听 Topic**: `seat-release-topic`

**触发时机**：
- 用户取消订单（`releaseType=CANCEL`）
- 用户退票（`releaseType=REFUND`）
- 订单超时自动取消（`releaseType=TIMEOUT`）

**处理流程**:
1. 使用 Lua 脚本原子性释放座位到可用池
2. 对每种释放的座位类型，发送候补兑现消息到 `waitlist-fulfill-topic`

---

### 5.6 Order Service (订单服务)

**入口类**: `OrderApplication.java`

#### 5.6.1 核心接口

| 接口 | 说明 |
|------|------|
| `POST /api/order/create` | 创建订单 |
| `GET /api/order/detail/{orderSn}` | 订单详情 |
| `GET /api/order/list` | 订单列表 |
| `POST /api/order/pay` | 发起支付 |
| `POST /api/order/refund/{orderSn}` | 退款 |
| `POST /api/order/cancel/{orderSn}` | 取消订单 |
| `POST /api/waitlist/create` | 创建候补订单 |
| `GET /api/waitlist/list` | 获取候补订单列表 |
| `GET /api/waitlist/detail/{waitlistSn}` | 候补订单详情 |
| `DELETE /api/waitlist/cancel/{waitlistSn}` | 取消候补订单 |

#### 5.6.2 订单状态

| 状态 | 说明 |
|------|------|
| 0 | 待支付 |
| 1 | 已支付 |
| 2 | 已取消 |
| 3 | 已退票 |

#### 5.6.3 MQ 消费者汇总

| 消费者 | Topic | Tag | 功能 |
|--------|-------|-----|------|
| OrderCreationConsumer | order-creation-topic | create | 创建订单，发送结果回调，初始化出行提醒，发送超时取消延迟消息 |
| OrderTimeoutCancelConsumer | order-timeout-cancel-topic | cancel | 处理订单超时取消，释放座位，取消出行提醒 |
| ReminderConsumer | travel-reminder-topic | * | 处理出行提醒消息，发送短信通知 |
| WaitlistFulfillConsumer | waitlist-fulfill-topic | fulfill | 候补兑现触发，从队列取出最高优先级订单 |
| WaitlistSeatResultConsumer | seat-selection-result-topic | * | 处理候补选座结果，成功创建订单，失败重新排队 |

#### 5.6.4 订单创建消费者 (`OrderCreationConsumer`)

**监听 Topic**: `order-creation-topic`（Tag: `create`）

**处理流程**:
1. 转换消息为订单创建请求 DTO
2. 调用 `OrderService.createOrder()` 创建订单（status=0 待支付）
3. 如果是候补订单（`waitlistSn` 不为空），更新候补状态为已兑现
4. 发送成功结果到 `order-creation-result-topic`
5. 初始化出行提醒状态（调用 `ReminderService.initReminderState()`）
6. 发送超时取消延迟消息到 `order-timeout-cancel-topic`（30分钟后触发）

**消息数据传递**:
```java
// 计划发车/到达时间从消息中获取（由 seat-service 查询填充）
Long planDepartTime = message.getPlanDepartTime();
Long planArrivalTime = message.getPlanArrivalTime();
// 若消息中没有，使用兜底值（当前时间）
```

#### 5.6.5 订单超时取消消费者 (`OrderTimeoutCancelConsumer`)

**监听 Topic**: `order-timeout-cancel-topic`（Tag: `cancel`）

**触发时机**：订单创建 30 分钟后仍未支付

**处理流程**:
1. 查询订单，检查状态是否为待支付（status=0）
2. 调用 `OrderService.cancelOrder()` 取消订单
3. 发送座位释放消息到 `seat-release-topic`（触发候补兑现）
4. 调用 `ReminderService.handleOrderCancel()` 取消出行提醒

**延迟消息机制**:
```java
// OrderCreationConsumer 发送延迟消息
messageQueueService.sendDelay(ORDER_TIMEOUT_CANCEL_TOPIC, resultMsg, 30 * 60 * 1000);
// 30分钟后，RocketMQ 触发消费者处理
```

#### 5.6.6 出行提醒服务 (`ReminderService`)

**功能**: 为已支付订单发送出行提醒短信

**提醒时机**:
- 发车前 1 小时
- 发车前 30 分钟
- 到达后 10 分钟（行程结束提醒）

**版本控制机制**:
```java
// 消息携带版本号，Redis 存储当前版本
// 版本不匹配时，重新计算触发时间并发送新延迟消息
// 防止订单取消/改签后发送错误提醒
```

**状态标识**:
| 状态 | 值 | 说明 |
|------|-----|------|
| STATUS_NORMAL | 0 | 正常状态 |
| STATUS_DELAY | 1 | 列车晚点 |
| STATUS_CANCEL | 2 | 列车停运 |
| STATUS_ORDER_CANCEL | 4 | 订单已取消 |

**已发送标识（位掩码）**:
| 标识 | 值 | 说明 |
|------|-----|------|
| FLAG_1H_SENT | 1 | 1小时提醒已发送 |
| FLAG_30M_SENT | 2 | 30分钟提醒已发送 |
| FLAG_ARRIVAL_SENT | 4 | 到达提醒已发送 |

#### 5.6.7 候补订单状态

| 状态 | 说明 |
|------|------|
| 0 | 待兑现（排队中） |
| 1 | 兑现中（已锁定座位，创建订单中） |
| 2 | 已兑现（成功出票） |
| 3 | 已取消（用户主动取消） |
| 4 | 已过期（超过截止时间） |

#### 5.6.8 候补优先级计算规则

候补订单采用 Redis ZSet 实现优先级队列，分数越高越优先：

```
总分 = 时间因子 - 队列拥堵惩罚

时间因子 = -创建时间戳 / 1,000,000,000（越早候补分数越高）
队列拥堵惩罚 = MIN(当前队列人数 × 0.1, 20)（最多扣20分）
失败惩罚 = 每次失败固定扣10分
```

**简化策略**（当前版本）：
- VIP加成：暂不启用（固定0分）
- 历史购票加成：暂不启用（固定0分）
- 乘客类型加成：暂不启用（固定成人0分）

#### 5.6.9 候补订单处理流程

候补订单采用**事件驱动架构**，仅在退票或新增座位时触发兑现流程：

```
用户提交候补
    ↓
[Redis ZSet 入队] ← 计算优先级
    ↓
    等待事件触发（退票/新增座位）
    ↓
[waitlist-fulfill-topic] ──→ WaitlistFulfillConsumer (order-service)
    ↓ (ZPOPMAX 取出最高优先级)
[seat-selection-topic] ──→ SeatSelectionConsumer (seat-service)
    ↓ (选座成功/失败，携带 planDepartTime/planArrivalTime)
[seat-selection-result-topic] ──→ WaitlistSeatResultConsumer (order-service)
    ↓ (成功：创建订单，传递时刻信息)
[order-creation-topic] ──→ OrderCreationConsumer (order-service)
    ↓
┌──────────────┴──────────────┐
│ 成功：状态=已兑现，移除队列   │
│ 失败：状态=待兑现，惩罚-10分  │
└─────────────────────────────┘
```

**关键特性**：
1. **事件驱动**：仅在退票/新增座位时触发，不使用轮询
2. **ZPOPMAX 原子出队**：Redis ZSet 保证最高优先级订单优先兑现
3. **幂等性保护**：每个请求携带 `requestId`，Redis SETNX 防止重复处理
4. **状态持久化**：候补订单状态流转实时同步数据库
5. **优先级惩罚**：每次失败降低 10 分，重新排队
6. **来源标识**：通过 `source=WAITLIST` 标记，区别于普通购票
7. **时刻传递**：选座成功后，时刻信息随消息传递到订单创建，用于出行提醒

#### 5.6.10 候补兑现消费者 (`WaitlistFulfillConsumer`)

**监听 Topic**: `waitlist-fulfill-topic`（Tag: `fulfill`）

**触发时机**：
- 用户退票时 → `SeatReleaseConsumer` 发送兑现消息
- 新增车次/座位时 → 发送兑现消息

**处理流程**：
1. 从 Redis ZSet 队列出队（ZPOPMAX 原子弹出最高优先级订单）
2. 查询候补订单，状态检查（仅处理"待兑现"状态）
3. 检查截止时间，过期订单标记为已过期
4. 更新状态为"兑现中"
5. 发送选座请求到 `seat-selection-topic`（标记 `source=WAITLIST`）

#### 5.6.11 候补选座结果消费者 (`WaitlistSeatResultConsumer`)

**监听 Topic**: `seat-selection-result-topic`（Tag: `*`）

**处理流程**：
1. 仅处理候补订单（`waitlistSn` 不为空）
2. 查询候补订单，校验状态（仅处理"兑现中"状态）
3. **选座成功**：
   - 构建订单创建消息，传递时刻信息（`planDepartTime`、`planArrivalTime`）
   - 发送到 `order-creation-topic`
4. **选座失败**：
   - 状态回滚为"待兑现"
   - 优先级惩罚 -10 分，重新入队

#### 5.6.12 Redis 数据结构

**候补优先级队列（ZSet）**：
```
Key: WAITLIST:QUEUE::{trainNumber}::{travelDate}
Member: waitlistSn
Score: priority（ BigDecimal 转换为 double，分数越高越优先）
```

**候补订单详情（Hash）**：
```
Key: WAITLIST:DETAIL::{waitlistSn}
Fields: status, trainNumber, startStation, endStation, travelDate,
        seatTypes, passengerIds, priority, deadline, fulfilledOrderSn
```

**幂等性锁（String）**：
```
Key: WAITLIST:MSGID::{requestId}
Value: PROCESSING
TTL: 30分钟
```

**异步请求跟踪（Hash）**：
```
Key: ticket:async:req:{requestId}
Fields: requestId, userId, trainNum, date, status,
        waitlistSn（候补订单号）, source（NORMAL/WAITLIST）
```

**出行提醒状态（Hash）**：
```
Key: REMINDER::STATE::{orderSn}
Fields: version, status, trainNumber, startStation, endStation,
        planDepartTime, planArrivalTime, sentFlags
```

#### 5.6.14 候补订单实体

**数据表**: `t_waitlist_order`

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `waitlist_sn` | 候补订单号（WL开头） |
| `username` | 用户名 |
| `train_number` | 车次号 |
| `start_station` | 出发站 |
| `end_station` | 到达站 |
| `travel_date` | 乘车日期 |
| `seat_types` | 座位类型列表（逗号分隔） |
| `passenger_ids` | 乘车人ID列表（逗号分隔） |
| `prepay_amount` | 预付款金额 |
| `deadline` | 候补截止时间 |
| `status` | 状态（0-待兑现，1-兑现中，2-已兑现，3-已取消，4-已过期） |
| `fulfilled_order_sn` | 兑现后的订单号 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

#### 5.6.15 MQ Topic 汇总

| Topic | Tag | 生产者 | 消费者 | 用途 |
|-------|-----|--------|--------|------|
| `seat-selection-topic` | `select` | ticket-service (异步购票)、order-service (候补兑现) | seat-service | 选座请求 |
| `seat-selection-result-topic` | `result` | seat-service | ticket-service (普通购票)、order-service (候补) | 选座结果返回 |
| `order-creation-topic` | `create` | ticket-service (异步)、order-service (候补) | order-service | 订单创建请求 |
| `order-creation-result-topic` | `result` | order-service | ticket-service | 订单创建结果回调 |
| `order-timeout-cancel-topic` | `cancel` | order-service (订单创建/支付) | order-service | 订单超时取消（延迟30分钟） |
| `travel-reminder-topic` | `*` | order-service (ReminderService) | order-service | 出行提醒（延迟消息） |
| `seat-release-topic` | `*` | order-service (取消/退票/超时)、ticket-service (失败) | seat-service | 座位释放，触发候补兑现 |
| `waitlist-fulfill-topic` | `fulfill` | seat-service (座位释放) | order-service | 候补兑现触发 |

#### 5.6.16 候补订单 API 示例

**创建候补订单**：
```http
POST /api/waitlist/create
Content-Type: application/json

{
  "account": "13800138000",
  "trainNumber": "G1234",
  "startStation": "北京南",
  "endStation": "上海虹桥",
  "travelDate": "2026-04-20",
  "seatTypes": ["1", "2"],
  "passengerIds": [1001, 1002],
  "prepayAmount": 200.00,
  "deadline": "2026-04-20T10:00:00"
}
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "waitlistSn": "WLABCDEF12345678"
  }
}
```

**查询候补列表**：
```http
GET /api/waitlist/list?username=13800138000
```

**取消候补**：
```http
DELETE /api/waitlist/cancel/WLABCDEF12345678?username=13800138000
```

#### 5.6.17 候补订单审计日志

**数据表**: `t_waitlist_log`

记录候补订单的关键状态变更：

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `waitlist_sn` | 候补订单号 |
| `action` | 操作类型（CREATE/CANCEL/EXPIRE/SUCCESS/FAIL） |
| `from_status` | 原状态 |
| `to_status` | 新状态 |
| `reason` | 变更原因 |
| `operator` | 操作人（SYSTEM/用户账号） |
| `create_time` | 记录时间 |

#### 5.6.18 与普通购票的差异

| 维度 | 普通购票（异步） | 候补订单 |
|------|------------------|----------|
| 处理模式 | MQ 异步处理 | 事件驱动 MQ（退票/新增座位触发） |
| 触发方式 | 用户主动下单 | 自动兑现 |
| 座位锁定 | 30分钟 | 10分钟（更快释放） |
| 无票处理 | 失败返回 | 继续排队，等待下次触发 |
| 失败惩罚 | 无 | 优先级 -10 分 |
| 截止时间 | 无 | 有（发车前一定时间） |
| 队列管理 | 无 | Redis ZSet 优先级队列（ZPOPMAX 出队） |
| 来源标识 | `NORMAL` | `WAITLIST` |
| 时刻信息获取 | 从消息获取，无则查询 DB | 随消息传递 |

#### 5.6.19 待优化方向

1. **VIP 等级加成**：接入用户服务，查询 VIP 等级并计算加成
2. **历史购票加成**：统计用户历史订单数量，给予忠诚度加分
3. **乘客类型加成**：查询 `t_passenger` 表，学生/儿童/残疾军人加分
4. **动态惩罚系数**：根据失败次数调整惩罚力度
5. **智能预测**：基于退票率、历史数据预测兑现成功率
6. **死信队列**：多次重试失败的消息转入死信队列人工处理
7. **消息追踪**：集成 SkyWalking 或自建消息追踪系统
8. **新增车次触发**：新增加车次/座位时，自动发送候补兑现消息

---

### 5.7 User Service (用户服务)

**入口类**: `UserServiceApplication.java`

#### 5.7.1 核心接口

| 接口 | 说明 |
|------|------|
| `POST /api/user/sms/send` | 发送短信验证码 |
| `POST /api/user/sms/login` | 短信登录 |
| `GET /api/user/passengers` | 获取乘车人列表 |
| `POST /api/user/passengers` | 添加乘车人 |
| `PUT /api/user/passengers/{id}` | 更新乘车人 |
| `DELETE /api/user/passengers/{id}` | 删除乘车人 |

---

### 5.8 Admin Service (后台管理服务)

**入口类**: `AdminServiceApplication.java`

#### 5.8.1 核心接口

| 模块 | 接口 | 说明 |
|------|------|------|
| 认证 | `POST /api/admin/auth/login` | 管理员登录 |
| 认证 | `GET /api/admin/auth/info` | 管理员信息 |
| 用户 | `GET /api/admin/user/list` | 用户列表 |
| 用户 | `PUT /api/admin/user/{id}/status` | 切换用户状态 |
| 车次 | `GET /api/admin/train/list` | 车次列表 |
| 车次 | `PUT /api/admin/train/{id}/sale-status` | 更新售卖状态 |
| 订单 | `GET /api/admin/order/list` | 订单列表 |
| 统计 | `GET /api/admin/stats/dashboard` | Dashboard 统计 |

#### 5.8.2 技术特点

- **独立认证**: 使用 `t_admin_user` 表，与普通用户分离
- **直连数据库**: 低流量场景，直接访问 DB，不通过 Feign 调用
- **游标分页**: `lastId + pageSize` 方式

---

---

## 6. 项目整体总结

### 6.1 系统架构图（MQ 消息驱动）

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              客户端层                                        │
│  ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐        │
│  │   Web 用户端    │     │   Web 管理端    │     │   移动端(预留)   │        │
│  │  12306 (React) │     │  admin (Vue3)   │     │   (H5/小程序)   │        │
│  └────────┬────────┘     └────────┬────────┘     └────────┬────────┘        │
└───────────┼───────────────────────┼───────────────────────┼──────────────────┘
            │                       │                       │
            ▼                       ▼                       ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                              网关层 (8080)                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │流量监控过滤器 │  │JWT认证过滤器 │  │请求ID过滤器 │  │ 路由分发器   │    │
│  │QPS>100标记   │  │Token验证    │  │追踪ID注入   │  │ 路由到后端   │    │
│  │peak=true     │  │用户ID注入    │  │             │  │              │    │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘    │
└───────────────────────────────────────────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        │                           │                           │
        ▼                           ▼                           ▼
┌───────────────┐         ┌───────────────┐           ┌───────────────┐
│ Ticket Service│◄────────│  Seat Service │           │ Order Service │
│    (8081)     │ Feign   │    (8082)     │           │    (8083)     │
│               │────────►│               │           │               │
│ - 购票搜索    │         │ - 座位选择    │           │ - 订单创建    │
│ - 购票处理    │         │ - 座位锁定    │           │ - 支付集成    │
│ - 选座结果处理│         │ - 座位释放    │           │ - 退款处理    │
│ - 订单结果处理│         │ - 候补触发    │           │ - 候补兑现    │
└───────┬───────┘         └───────────────┘           │ - 出行提醒    │
        │                                               │ - 超时取消    │
        │              ┌───────────────┐               └───────┬───────┘
        │              │   User Service│                       │
        └─────────────►│    (8084)    │◄───────────────────────┘
                       │               │       Feign
                       │ - 用户认证    │
                       │ - 乘车人管理 │
                       └───────────────┘

┌───────────────────────────────────────────────────────────────────────────────┐
│                           MQ 消息流 (RocketMQ)                               │
│                                                                               │
│  购票流程:                                                                    │
│  seat-selection-topic ──► seat-selection-result-topic ──► order-creation-topic│
│                              ──► order-creation-result-topic                  │
│                                                                               │
│  候补流程:                                                                    │
│  seat-release-topic ──► waitlist-fulfill-topic ──► seat-selection-topic      │
│                              ──► seat-selection-result-topic ──► order-creation│
│                                                                               │
│  超时取消:                                                                    │
│  order-timeout-cancel-topic (延迟30分钟) ──► seat-release-topic              │
│                                                                               │
│  出行提醒:                                                                    │
│  travel-reminder-topic (延迟消息: 1h前/30m前/到达后)                          │
└───────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 技术栈总结

#### 6.2.1 后端技术栈

| 类别 | 技术 | 说明 |
|------|------|------|
| 框架 | Spring Boot 3.0.7 | 基础框架 |
| 微服务 | Spring Cloud | 服务治理 |
| 服务调用 | OpenFeign | 声明式 HTTP 客户端 |
| 数据库 | MySQL 8.0 | 关系型数据库 |
| ORM | MyBatis-Plus | 数据库访问层 |
| 缓存 | Redis | 高性能缓存 |
| 分布式锁 | Redisson | Redis 客户端 |
| 消息队列 | RocketMQ | 削峰填谷 |
| 认证 | JWT | 无状态认证 |
| 构建 | Maven | 依赖管理 |

#### 6.2.2 前端技术栈

| 类别 | 用户端 (12306) | 管理端 (admin) |
|------|----------------|----------------|
| 框架 | React 18 | Vue 3 |
| 语言 | TypeScript | TypeScript |
| 构建 | Vite | Vite |
| UI库 | Tailwind CSS | Arco Design |
| 图标 | Lucide React | Element Plus Icons |
| 图表 | - | ECharts |
| 状态管理 | React Context | Pinia |

### 6.3 功能模块总结

| 模块 | 功能 | 技术亮点 |
|------|------|----------|
| 车票搜索 | 出发地/目的地/日期查询 | 中转方案计算 |
| 座位选择 | 自动/手动选座 | Redis 位图存储 + Lua 脚本原子操作 |
| 票价计算 | 多维度计价 | 距离×单价×折扣 |
| 订单管理 | 创建/支付/退款/取消 | 支付宝沙箱集成 |
| 异步购票 | MQ 消息驱动 | 流量削峰 + Redis 状态追踪 |
| 候补购票 | 事件驱动兑现 | Redis ZSet 优先级队列 + ZPOPMAX |
| 出行提醒 | 延迟消息提醒 | 版本控制 + 状态机 |
| 订单超时 | 自动取消 | 延迟队列（30分钟） |
| 用户管理 | 短信登录/乘车人 | JWT Token |
| 后台管理 | 数据统计/用户管理 | 游标分页 |

### 6.4 数据库设计

#### 6.4.1 核心表结构

| 表名 | 说明 | 记录量(示例) |
|------|------|-------------|
| `t_station` | 车站信息 | ~3,300 |
| `t_train` | 列车信息 | ~10,000 |
| `t_train_station` | 列车经停站 | ~95,000 |
| `t_carriage` | 车厢信息 | ~64,000 |
| `t_seat` | 座位信息 | ~4,900,000 |
| `t_user` | 用户账号 | ~1,000,000 |
| `t_passenger` | 乘车人 | ~2,000,000 |
| `t_order` | 订单主表 | ~1,000,000 |
| `t_order_item` | 订单明细 | ~2,500,000 |

#### 6.4.2 索引设计

**复合索引示例**:
```sql
-- 车票查询
KEY `t_ticket_detail` (id, train_id, travel_date, carriage_number, seat_type, del_flag)

-- 订单查询
KEY `idx_username` (username)
KEY `idx_run_date` (run_date)

-- 座位查询
KEY `idx_count` (train_id, seat_type, del_flag)
```

### 6.5 缓存设计

#### 6.5.1 缓存 Key 规范

```
TICKET::REMAINING::{trainId}::{date}::{seatType}   # 余票数量
TICKET::DETAIL::{trainId}::{date}::{carriageNum}  # 余票详情
TRAIN::ROUTE::{startRegion}::{endRegion}           # 路线缓存
USER::DETAIL::{userId}                            # 用户信息
REQUEST::{requestId}                               # 请求追踪
traffic:peak:status                               # 高峰状态
ticket:async:req:{requestId}                       # 异步购票请求状态
REMINDER::STATE::{orderSn}                        # 出行提醒状态（版本控制）
WAITLIST:QUEUE::{trainNumber}::{date}              # 候补队列（ZSet）
WAITLIST:DETAIL::{waitlistSn}                      # 候补订单详情（Hash）
```

#### 6.5.2 缓存策略

| 场景 | TTL | 更新策略 |
|------|-----|----------|
| 余票数量 | 5分钟 | 主动更新 |
| 路线缓存 | 1天 | 主动更新 |
| 用户信息 | 30分钟 | LRU |
| 高峰状态 | 5秒 | 自动过期 |
| 异步请求 | 30分钟 | 完成后删除 |
| 候补队列 | 永久 | 订单失效后删除 |
| 提醒状态 | 24小时 | 出行完成后删除 |

### 6.6 项目启动指南

#### 6.6.1 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8.0
- Redis 6.0+
- RocketMQ 4.9+
- Nacos (可选，服务注册)

#### 6.6.2 启动顺序

1. **启动基础设施**: MySQL → Redis → RocketMQ
2. **编译后端**: `mvn clean install -DskipTests`
3. **启动服务**: 按端口顺序 gateway → user → seat → ticket → order → admin
4. **启动前端**: `cd 12306 && npm install && npm run dev`

#### 6.6.3 默认端口

| 服务 | 端口 | 访问地址 |
|------|------|----------|
| Gateway | 8080 | http://localhost:8080 |
| Ticket | 8081 | - |
| Seat | 8082 | - |
| Order | 8083 | - |
| User | 8084 | - |
| Admin | 8085 | http://localhost:5174 |
| 12306前端 | 5173 | http://localhost:5173 |

### 6.7 项目亮点

1. **高性能**: Redis + MQ 异步化应对高并发，Lua 脚本保证座位操作原子性
2. **可扩展**: 微服务架构便于水平扩展
3. **高可用**: MQ 削峰保证系统稳定，延迟消息实现超时自动取消
4. **安全**: JWT + 幂等性防止重复提交，版本控制防止过期提醒
5. **可观测**: 请求追踪 ID 贯穿全链路
6. **规范化**: 统一响应格式、统一异常处理
7. **事件驱动**: 候补兑现由退票/释放座位事件触发，不使用轮询
8. **数据传递**: 时刻信息从源头服务查询后随消息传递，避免跨服务耦合

---
