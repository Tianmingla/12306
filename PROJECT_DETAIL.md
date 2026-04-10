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
> 技术栈: Vue 3 + Arco Design + Vite + TypeScript

*待分析...*

---

## 3. 12306 前端前台界面

> 位置: `12306/`
> 技术栈: React + TypeScript + Vite

*待分析...*

---

## 4. Frameworks 后端框架模块

> 位置: `Frameworks/`
> 语言: Java (Spring Boot)

*待分析...*

---

## 5. Services 微服务模块

> 位置: `Services/`
> 架构: Spring Cloud 微服务

*待分析...*

---

## 6. 项目整体总结

*待完成...*
