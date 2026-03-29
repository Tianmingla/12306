# DataScript

数据脚本目录，用于数据导入和测试数据生成。

## 脚本说明

### 数据导入脚本（按顺序执行）

| 序号 | 脚本 | 功能 | 数据量 |
|------|------|------|--------|
| 1 | `1_import_stations.py` | 导入车站数据 | ~3000 条 |
| 2 | `2_import_trains.py` | 导入车次数据 | ~10000 条 |
| 3 | `3_import_train_stations.py` | 导入车次车站关系 | ~100000 条 |
| 4 | `4_generate_seats_and_carriages.py` | 生成车厢和座位数据 | ~500 万条 |

### 测试数据生成脚本

| 序号 | 脚本 | 功能 | 默认数量 |
|------|------|------|----------|
| 5 | `5_generate_users.py` | 生成用户数据 | 100 万 |
| 6 | `6_generate_passengers.py` | 生成乘客数据 | ~150 万 |
| 7 | `7_generate_orders.py` | 生成订单和订单明细 | 100 万 |

## 执行顺序

### 基础数据导入（首次部署）

```bash
# 1. 导入车站数据
python 1_import_stations.py

# 2. 导入车次数据
python 2_import_trains.py

# 3. 导入车次车站关系
python 3_import_train_stations.py

# 4. 生成车厢和座位
python 4_generate_seats_and_carriages.py
```

### 测试数据生成

```bash
# 5. 生成用户（可选指定数量）
python 5_generate_users.py --count 1000000

# 6. 生成乘客
python 6_generate_passengers.py --users 50000

# 7. 生成订单
python 7_generate_orders.py --count 1000000
```

## 依赖

```bash
pip install pymysql
```

## 数据源

- `station_name.txt`: 12306 车站数据
- `data/`: 车次 JSON 数据目录
