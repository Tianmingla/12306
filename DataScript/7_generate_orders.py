#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
订单数据生成脚本

功能：
    生成 100 万条测试订单数据到 t_order 和 t_order_item 表。
    模拟真实订单流程：创建 - 支付 - 完成/取消。

订单特征：
    - 订单号：唯一 UUID 格式
    - 状态分布：待支付 (5%)、已支付 (70%)、已完成 (20%)、已取消/已退票 (5%)
    - 金额：根据车次类型、距离、座位类型计算
    - 乘车日期：未来 90 天内随机
    - 时间分布：过去 6 个月内随机创建

性能优化：
    - 批量插入（每批 5000 条主表，15000 条明细）
    - 进度显示
    - 数据库级去重（唯一索引：order_sn）

用法：
    python 7_generate_orders.py [--count 1000000]


"""

import sys
import random
import logging
import uuid
import argparse
from datetime import datetime, timedelta
from typing import List, Dict, Any, Tuple
import pymysql
from pymysql.cursors import DictCursor

# =============================================================================
# 配置区域
# =============================================================================

DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'my12306',
    'charset': 'utf8mb4',
    'cursorclass': DictCursor
}

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger(__name__)

# 默认生成数量
DEFAULT_COUNT = 1_000_000

# 批量插入大小
ORDER_BATCH_SIZE = 5_000
ITEM_BATCH_SIZE = 15_000

# 订单状态
ORDER_STATUS_WAIT_PAYMENT = 0   # 待支付
ORDER_STATUS_PAID = 1           # 已支付
ORDER_STATUS_COMPLETED = 2      # 已完成
ORDER_STATUS_CANCELLED = 3      # 已取消
ORDER_STATUS_REFUNDED = 4       # 已退票

# 状态名称映射
STATUS_NAMES = {
    ORDER_STATUS_WAIT_PAYMENT: '待支付',
    ORDER_STATUS_PAID: '已支付',
    ORDER_STATUS_COMPLETED: '已完成',
    ORDER_STATUS_CANCELLED: '已取消',
    ORDER_STATUS_REFUNDED: '已退票'
}

# 座位类型价格基准（元）
SEAT_TYPE_PRICES = {
    0: {'base': 50, 'type_multiplier': 1.0},     # 硬座
    1: {'base': 30, 'type_multiplier': 1.0},     # 二等座
    2: {'base': 50, 'type_multiplier': 1.6},     # 一等座
    3: {'base': 100, 'type_multiplier': 3.0},    # 商务座
    5: {'base': 150, 'type_multiplier': 1.0},    # 硬卧
    6: {'base': 300, 'type_multiplier': 1.0},    # 软卧
}


# =============================================================================
# 价格计算函数
# =============================================================================

def calculate_ticket_price(train_type: int, seat_type: int, distance_days: int) -> float:
    """
    计算单张车票价格。

    公式：
        base_price * type_multiplier * distance_factor

    其中 distance_factor 基于乘车日期的远近（越远可能略贵）。

    Args:
        train_type: 列车类型（0=高铁，1=动车，2=普速）
        seat_type: 座位类型
        distance_days: 距离乘车日的天数

    Returns:
        票价（保留 2 位小数）
    """
    base_info = SEAT_TYPE_PRICES.get(seat_type, SEAT_TYPE_PRICES[0])
    base_price = base_info['base']
    multiplier = base_info['type_multiplier']

    # 根据车次类型微调基准价
    if train_type == 0:  # 高铁
        base_price *= 1.2
    elif train_type == 1:  # 动车
        base_price *= 1.0

    # 距离因子（提前购票可能有折扣）
    if distance_days > 30:
        distance_factor = 1.1  # 远期稍贵
    elif distance_days < 3:
        distance_factor = 1.2  # 临期较贵
    else:
        distance_factor = 1.0  # 正常

    price = base_price * multiplier * distance_factor

    return round(price, 2)


# =============================================================================
# 数据获取函数
# =============================================================================

def get_trains_with_routes(limit: int = 1000) -> List[Dict]:
    """
    获取列车及其路线信息。

    Args:
        limit: 返回数量限制

    Returns:
        列车路线列表
    """
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        # 随机获取一些列车和它的路线
        cursor.execute("""
            SELECT DISTINCT t.id, t.train_number, t.train_type, t.train_brand,
                   trp.departure_station, trp.arrival_station,
                   TIMESTAMPDIFF(DAY, NOW(), trp.end_time) as days_diff
            FROM t_train t
            JOIN t_train_route_pair trp ON t.id = trp.train_id
            WHERE t.del_flag = 0 AND trp.del_flag = 0
            ORDER BY RAND()
            LIMIT %s
        """, (limit,))

        return cursor.fetchall()
    finally:
        cursor.close()
        conn.close()


def get_users_and_passengers(user_count: int = 50000) -> List[Dict]:
    """
    获取用户及其乘车人信息。

    Args:
        user_count: 用户数

    Returns:
        [(user_id, real_name, id_card), ...] 列表
    """
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        # 随机获取用户及其乘客
        cursor.execute("""
            SELECT p.user_id, p.real_name, p.id_card_number , u.phone
            FROM t_passenger p
            JOIN t_user u ON p.user_id = u.id
            WHERE p.del_flag = 0 AND u.del_flag = 0
            ORDER BY RAND()
            LIMIT %s
        """, (user_count,))

        return cursor.fetchall()
    finally:
        cursor.close()
        conn.close()


def generate_order_status() -> Tuple[int, datetime]:
    """
    生成订单状态及对应的时间。

    概率分布：
        - 待支付：5%
        - 已支付：70%
        - 已完成：20%
        - 已取消/已退票：5%

    Returns:
        (status, pay_time) 元组
    """
    rand = random.random() * 100

    if rand < 5:
        status = ORDER_STATUS_WAIT_PAYMENT
        pay_time = None
    elif rand < 75:
        status = ORDER_STATUS_PAID
        # 创建后 0-2 小时内支付
        hours_later = random.uniform(0, 2)
        pay_time = datetime.now() + timedelta(hours=hours_later)
    elif rand < 95:
        status = ORDER_STATUS_COMPLETED
        # 已支付后 1-3 天完成
        hours_later = random.uniform(24, 72)
        pay_time = datetime.now() + timedelta(hours=hours_later)
    else:
        # 取消或退票
        if random.random() < 0.5:
            status = ORDER_STATUS_CANCELLED
        else:
            status = ORDER_STATUS_REFUNDED
        # 取消时间在创建后 0-24 小时内
        hours_later = random.uniform(0, 24)
        pay_time = datetime.now() + timedelta(hours=hours_later)

    return status, pay_time


# =============================================================================
# 订单数据生成函数
# =============================================================================

def generate_order_records(
    routes: List[Dict],
    passengers: List[Tuple[int, str, str]],
    count: int
) -> Tuple[List[Dict], List[Dict]]:
    """
    批量生成订单记录。

    Args:
        routes: 列车路线列表
        passengers: 用户乘车人列表
        count: 生成数量

    Returns:
        (orders, order_items) 元组
    """
    orders = []
    order_items = []

    used_order_sns = set()

    for i in range(count):
        # 选择随机路线
        route = random.choice(routes)
        train_id = route['id']
        train_number = route['train_number']
        train_type = route['train_type'] or 2
        departure_station = route['departure_station']
        arrival_station = route['arrival_station']
        travel_date = (datetime.now() + timedelta(days=random.randint(1, 90)))

        # 选择随机乘客
        passenger = random.choice(passengers)
        user_id = passenger["user_id"]
        real_name = passenger["real_name"]
        id_card = passenger["id_card_number"]
        phone = passenger["phone"]

        # 随机座位类型（优先二等座、一等座）
        if random.random() < 0.6:
            seat_type = 1  # 二等座
        elif random.random() < 0.85:
            seat_type = 2  # 一等座
        elif random.random() < 0.92:
            seat_type = 3  # 商务座
        elif random.random() < 0.96:
            seat_type = 5  # 硬卧
        elif random.random() < 0.98:
            seat_type = 6  # 软卧
        else:
            seat_type = 0  # 硬座

        # 计算价格
        distance_days = (travel_date - datetime.now()).days if travel_date else 7
        ticket_price = calculate_ticket_price(train_type, seat_type, max(1, distance_days))

        # 生成订单号
        while True:
            order_sn = f"ORD{uuid.uuid4().hex[:16].upper()}"
            if order_sn not in used_order_sns:
                used_order_sns.add(order_sn)
                break

        # 确定订单状态和支付时间
        status, pay_time = generate_order_status()

        now = datetime.now()
        create_time = now - timedelta(minutes=random.randint(1, 10080))  # 过去 1-7 天

        order = {
            'order_sn': order_sn,
            'username': phone,
            'train_number': train_number,
            'start_station': departure_station,
            'end_station': arrival_station,
            'run_date': travel_date.date(),
            'total_amount': ticket_price,
            'status': status,
            'pay_time': pay_time,
            'create_time': create_time,
            'update_time': now,
            'del_flag': 0
        }

        order_item = {
            'order_id': 0,  # 占位符，后续填充
            'order_sn': order_sn,
            'passenger_id': 0,  # 占位符
            'passenger_name': real_name,
            'id_card': id_card,
            'carriage_number': f"{random.randint(1, 16):02d}",
            'seat_number': generate_seat_number(seat_type),
            'seat_type': seat_type,
            'amount': ticket_price,
            'create_time': now,
            'update_time': now,
            'del_flag': 0
        }

        orders.append(order)
        order_items.append(order_item)

        if (i + 1) % 10000 == 0:
            logger.info(f"生成进度：{i+1:,}/{count:,}")

    return orders, order_items


def generate_seat_number(seat_type: int) -> str:
    """生成随机座位号。"""
    if seat_type == 1:  # 二等座
        row = random.randint(1, 18)
        letter = random.choice(['A', 'B', 'C', 'D', 'F'])
        return f"{row:02d}{letter}"
    elif seat_type == 2:  # 一等座
        row = random.randint(1, 14)
        letter = random.choice(['A', 'C', 'D', 'F'])
        return f"{row:02d}{letter}"
    elif seat_type == 3:  # 商务座
        row = random.randint(1, 20)
        letter = random.choice(['A', 'C', 'F'])
        return f"{row:02d}{letter}"
    else:  # 其他
        return f"{random.randint(1, 118):03d}"


# =============================================================================
# 数据库操作
# =============================================================================

def insert_orders_batch(orders: List[Dict]) -> List[str]:
    """
    批量插入订单主表数据。

    Args:
        orders: 订单数据列表

    Returns:
        已插入的 order_sn 列表
    """
    if not orders:
        return []

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    sql = """
        INSERT IGNORE INTO t_order (
            order_sn, username, train_number, start_station, end_station,
            run_date, total_amount, status, pay_time, create_time, update_time, del_flag
        ) VALUES (
            %(order_sn)s, %(username)s, %(train_number)s, %(start_station)s,
            %(end_station)s, %(run_date)s, %(total_amount)s, %(status)s, %(pay_time)s,
            %(create_time)s, %(update_time)s, %(del_flag)s
        )
    """

    try:
        cursor.executemany(sql, orders)
        conn.commit()

        # 查询刚插入的订单 ID
        order_sns = [o['order_sn'] for o in orders]
        placeholders = ','.join(['%s'] * len(order_sns))
        cursor.execute(f"""
            SELECT id, order_sn FROM t_order WHERE order_sn IN ({placeholders})
        """, order_sns)

        id_map = {row['order_sn']: row['id'] for row in cursor.fetchall()}
        return list(id_map.values())

    except Exception as e:
        conn.rollback()
        logger.error(f"插入订单失败：{e}")
        raise
    finally:
        cursor.close()
        conn.close()


def insert_order_items_batch(items: List[Dict], id_map: Dict[str, int]) -> int:
    """
    批量插入订单明细数据。

    Args:
        items: 订单明细列表
        id_map: order_sn -> order_id 映射

    Returns:
        成功插入的记录数
    """
    if not items:
        return 0

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    sql = """
        INSERT IGNORE INTO t_order_item (
            order_id, order_sn, passenger_id, passenger_name, id_card,
            carriage_number, seat_number, seat_type, amount,
            create_time, update_time, del_flag
        ) VALUES (
            %(order_id)s, %(order_sn)s, %(passenger_id)s, %(passenger_name)s, %(id_card)s,
            %(carriage_number)s, %(seat_number)s, %(seat_type)s, %(amount)s,
            %(create_time)s, %(update_time)s, %(del_flag)s
        )
    """

    try:
        # 更新 order_id
        for item in items:
            item['order_id'] = id_map[item['order_sn']]
            item['passenger_id'] = random.randint(1, 1000000)  # 随机乘客 ID

        cursor.executemany(sql, items)
        conn.commit()
        return cursor.rowcount

    except Exception as e:
        conn.rollback()
        logger.error(f"插入订单明细失败：{e}")
        raise
    finally:
        cursor.close()
        conn.close()


def verify_data(total_orders: int) -> Dict[str, Any]:
    """验证生成的数据。"""
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        # 总订单数
        cursor.execute("SELECT COUNT(*) as count FROM t_order WHERE del_flag = 0")
        total = cursor.fetchone()['count']

        # 状态分布
        cursor.execute("""
            SELECT status, COUNT(*) as count
            FROM t_order
            WHERE del_flag = 0
            GROUP BY status
        """)
        status_dist = cursor.fetchall()

        # 金额统计
        cursor.execute("""
            SELECT AVG(total_amount) as avg_amount,
                   MIN(total_amount) as min_amount,
                   MAX(total_amount) as max_amount
            FROM t_order
            WHERE del_flag = 0
        """)
        amount_stats = cursor.fetchone()

        # 时间分布
        cursor.execute("""
            SELECT DATE(create_time) as day, COUNT(*) as count
            FROM t_order
            WHERE del_flag = 0
            GROUP BY DATE(create_time)
            ORDER BY day DESC
            LIMIT 7
        """)
        recent_days = cursor.fetchall()

        stats = {
            'total': total,
            'status_distribution': {STATUS_NAMES.get(r['status'], '未知'): r['count'] for r in status_dist},
            'amount': {
                'avg': round(amount_stats['avg_amount'], 2),
                'min': amount_stats['min_amount'],
                'max': amount_stats['max_amount']
            },
            'recent_days': recent_days
        }

        logger.info("\n=== 订单数据统计 ===")
        logger.info(f"总订单数：{total:,}")
        logger.info(f"状态分布：")
        for name, count in stats['status_distribution'].items():
            logger.info(f"  - {name}: {count:,} ({count/total*100:.1f}%)")
        logger.info(f"金额统计:")
        logger.info(f"  - 平均：¥{stats['amount']['avg']}")
        logger.info(f"  - 最低：¥{stats['amount']['min']}")
        logger.info(f"  - 最高：¥{stats['amount']['max']}")
        logger.info(f"最近 7 天订单:")
        for day in recent_days:
            logger.info(f"  - {day['day']}: {day['count']:,} 单")
        logger.info("")

        return stats

    finally:
        cursor.close()
        conn.close()


# =============================================================================
# 主程序
# =============================================================================

def main():
    parser = argparse.ArgumentParser(description='生成订单测试数据')
    parser.add_argument('--count', type=int, default=DEFAULT_COUNT, help=f'生成订单数量（默认 {DEFAULT_COUNT:,}）')
    args = parser.parse_args()

    target_count = args.count

    logger.info("=" * 60)
    logger.info("订单数据生成脚本启动")
    logger.info("=" * 60)

    start_time = datetime.now()

    try:
        # Step 1: 准备基础数据
        logger.info("准备基础数据...")
        routes = get_trains_with_routes(limit=500)
        passengers = get_users_and_passengers(user_count=50000)

        logger.info(f"加载了 {len(routes):,} 条路线，{len(passengers):,} 个乘客")

        if not routes or not passengers:
            logger.error("基础数据不足，无法生成订单")
            return 1

        # Step 2: 生成订单数据
        logger.info(f"开始生成 {target_count:,} 条订单记录...")
        orders, order_items = generate_order_records(routes, passengers, target_count)

        # Step 3: 分批插入订单主表
        inserted_orders = 0
        all_order_ids = {}

        for i in range(0, len(orders), ORDER_BATCH_SIZE):
            batch = orders[i:i + ORDER_BATCH_SIZE]
            ids = insert_orders_batch(batch)

            # 构建 order_sn -> order_id 映射
            batch_sns = [o['order_sn'] for o in batch]
            for j, sn in enumerate(batch_sns):
                if j < len(ids):
                    all_order_ids[sn] = ids[j]

            inserted_orders += len(ids)
            logger.info(f"订单主表插入：{inserted_orders:,}/{target_count:,}")

        # Step 4: 插入订单明细
        if all_order_ids:
            # 更新 order_id
            for item in order_items:
                item['order_id'] = all_order_ids.get(item['order_sn'], 0)
                item['passenger_id'] = random.randint(1, 1000000)

            items_inserted = 0
            for i in range(0, len(order_items), ITEM_BATCH_SIZE):
                batch = order_items[i:i + ITEM_BATCH_SIZE]
                inserted = insert_order_items_batch(batch, all_order_ids)
                items_inserted += inserted
                logger.info(f"订单明细插入：{items_inserted:,}/{len(order_items):,}")

        # Step 5: 验证结果
        verify_data(target_count)

        elapsed = (datetime.now() - start_time).total_seconds()
        logger.info(f"脚本执行完成，耗时 {elapsed:.2f} 秒，生成 {inserted_orders:,} 条订单记录")

        return 0

    except Exception as e:
        logger.error(f"脚本执行失败：{e}", exc_info=True)
        return 1


if __name__ == '__main__':
    sys.exit(main())
