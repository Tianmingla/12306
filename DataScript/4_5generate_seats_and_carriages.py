#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
座位和车厢数据生成脚本（重构版）

功能：
    为 t_train 表中的每个车次生成符合其类型的车厢和座位数据。
    严格按照中国铁路实际配置标准生成，不同车次类型采用不同规格。

车次类型与配置规则：
    G/C (高铁/城际):
        - CRH380/CR400 系列：8 节编组（2+3 座布局）或 16 节长编组
        - 商务座：1-2 节，3 座排（A-C-F），约 60 座/节
        - 一等座：1-2 节，4 座排（A-C-D-F），约 28-56 座/节
        - 二等座：其余车厢，5 座排（A-B-C-D-F），约 72-90 座/节

    D (动车):
        - CRH1/2/5 系列：8 节编组
        - 一等座：1 节，4 座排，约 40-50 座
        - 二等座：6-7 节，5 座排，约 80-90 座/节

    Z (直达特快):
        - 25T 型客车
        - 软卧：1 节，32 铺位（8 包厢*4 铺）
        - 硬卧：4-5 节，66 铺位（11 包厢*6 铺）
        - 硬座：1 节，118 座

    T (特快):
        - 25K 型客车
        - 软卧：1 节，32 铺位
        - 硬卧：3-4 节，66 铺位
        - 硬座：1 节，118 座

    K (快速):
        - 25G 型客车
        - 硬卧：3-4 节
        - 硬座：1-2 节

用法：
    python 4_generate_seats_and_carriages.py


"""

import sys
import logging
from datetime import datetime
from typing import Dict, List, Tuple, Any, Optional
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

BATCH_SIZE = 200


# =============================================================================
# 座位类型枚举（与数据库 t_seat.seat_type 对应）
# =============================================================================

SEAT_TYPE_BUSINESS = 3      # 商务座
SEAT_TYPE_FIRST_CLASS = 2   # 一等座
SEAT_TYPE_SECOND_CLASS = 1  # 二等座
SEAT_TYPE_HARD_SEAT = 0     # 硬座
SEAT_TYPE_SOFT_SLEEPER = 6  # 软卧
SEAT_TYPE_HARD_SLEEPER = 5  # 硬卧

# 车厢类型枚举
CARRIAGE_TYPE_NORMAL = 0    # 普通车厢
CARRIAGE_TYPE_BED = 1       # 卧铺车厢
CARRIAGE_TYPE_BUSINESS = 2  # 商务车厢


# =============================================================================
# 座位号生成器
# =============================================================================

def generate_second_class_seat_numbers(total_seats: int) -> List[str]:
    """
    生成二等座座位号（5 座排：A-B-C-D-F）。

    二等座车厢通常有 90 个座位，排布为 2+3（过道两侧）。
    A/F 靠窗，C/D 靠过道，B 中间。
    """
    seats_per_row = 5
    letter_order = ['A', 'B', 'C', 'D', 'F']

    seat_numbers = []
    row = 1

    while len(seat_numbers) < total_seats:
        for letter in letter_order:
            if len(seat_numbers) >= total_seats:
                break
            seat_numbers.append(f"{row:02d}{letter}")
        row += 1

    return seat_numbers[:total_seats]


def generate_first_class_seat_numbers(total_seats: int) -> List[str]:
    """
    生成一等座座位号（4 座排：A-C-D-F）。

    一等座车厢通常有 28-56 个座位，排布为 2+2（更宽敞）。
    A/F 靠窗，C/D 靠过道。
    """
    seats_per_row = 4
    letter_order = ['A', 'C', 'D', 'F']

    seat_numbers = []
    row = 1

    while len(seat_numbers) < total_seats:
        for letter in letter_order:
            if len(seat_numbers) >= total_seats:
                break
            seat_numbers.append(f"{row:02d}{letter}")
        row += 1

    return seat_numbers[:total_seats]


def generate_business_seat_numbers(total_seats: int) -> List[str]:
    """
    生成商务座座位号（3 座排：A-C-F）。

    商务座车厢非常豪华，每节约 60 个座位，排布为 1+2 或 2+1。
    A/F 靠窗，C 中间。
    """
    seats_per_row = 3
    letter_order = ['A', 'C', 'F']

    seat_numbers = []
    row = 1

    while len(seat_numbers) < total_seats:
        for letter in letter_order:
            if len(seat_numbers) >= total_seats:
                break
            seat_numbers.append(f"{row:02d}{letter}")
        row += 1

    return seat_numbers[:total_seats]


def generate_hard_seat_numbers(total_seats: int) -> List[str]:
    """
    生成硬座座位号（数字序列）。

    普速列车硬座车通常为 118 个座位，使用纯数字编号 01-118。
    """
    return [f"{i:03d}" for i in range(1, total_seats + 1)]


def generate_sleeper_numbers(total_seats: int, is_soft: bool) -> List[str]:
    """
    生成卧铺座位号。

    软卧：包厢式，4 铺位/包厢（上铺下铺）。如 01 02 03 04（1 号包厢）。
    硬卧：开放式，6 铺位/包厢（上中下铺）。如 01 02 03 04 05 06（1 号包厢）。
    """
    if is_soft:
        # 软卧 32 铺位（8 包厢×4 铺）
        return [f"{i:02d}" for i in range(1, total_seats + 1)]
    else:
        # 硬卧 66 铺位（11 包厢×6 铺）
        return [f"{i:02d}" for i in range(1, total_seats + 1)]


# =============================================================================
# 列车配置规则（按车次类型定义车厢布局）
# =============================================================================

# 高铁 8 节编组（复兴号 CR400）
G_HIGH_SPEED_8 = [
    {"carriage_number": "01", "carriage_type": CARRIAGE_TYPE_BUSINESS, "seat_type": SEAT_TYPE_BUSINESS, "seat_count": 55},  # 1 节商务座
    {"carriage_number": "02", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_FIRST_CLASS, "seat_count": 28},  # 1 节一等座
    {"carriage_number": "03", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},  # 二等座
    {"carriage_number": "04", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},  # 二等座
    {"carriage_number": "05", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},  # 二等座
    {"carriage_number": "06", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},  # 二等座
    {"carriage_number": "07", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},  # 二等座
    {"carriage_number": "08", "carriage_type": CARRIAGE_TYPE_BUSINESS, "seat_type": SEAT_TYPE_BUSINESS, "seat_count": 64},  # 1 节商务座/二等座合造
]

# 高铁 16 节长编组（和谐号 CRH380）
G_HIGH_SPEED_16 = [
    {"carriage_number": "01", "carriage_type": CARRIAGE_TYPE_BUSINESS, "seat_type": SEAT_TYPE_BUSINESS, "seat_count": 55},
    {"carriage_number": "02", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_FIRST_CLASS, "seat_count": 28},
    {"carriage_number": "03", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},
    {"carriage_number": "04", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},
    {"carriage_number": "05", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},
    {"carriage_number": "06", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},
    {"carriage_number": "07", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},
    {"carriage_number": "08", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},
    {"carriage_number": "09", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},
    {"carriage_number": "10", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},
    {"carriage_number": "11", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},
    {"carriage_number": "12", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},
    {"carriage_number": "13", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},
    {"carriage_number": "14", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},
    {"carriage_number": "15", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_FIRST_CLASS, "seat_count": 56},  # 长编组增加一等座
    {"carriage_number": "16", "carriage_type": CARRIAGE_TYPE_BUSINESS, "seat_type": SEAT_TYPE_BUSINESS, "seat_count": 55},
]

# 城际 C 型（短编全二等座）
C_INTERCITY_8 = [
    {"carriage_number": "01", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 100},
    {"carriage_number": "02", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 100},
    {"carriage_number": "03", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 100},
    {"carriage_number": "04", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 100},
    {"carriage_number": "05", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 100},
    {"carriage_number": "06", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 100},
    {"carriage_number": "07", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 100},
    {"carriage_number": "08", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 100},
]

# 动车 D 型（8 节编组）
D_EMU_8 = [
    {"carriage_number": "01", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_FIRST_CLASS, "seat_count": 40},  # 一等座
    {"carriage_number": "02", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 86},  # 二等座
    {"carriage_number": "03", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 86},
    {"carriage_number": "04", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 86},
    {"carriage_number": "05", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 86},
    {"carriage_number": "06", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 86},
    {"carriage_number": "07", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 86},
    {"carriage_number": "08", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 86},
]

# 直达特快 Z 型（25T 型）
Z_DIRECT_EXPRESS = [
    {"carriage_number": "01", "carriage_type": CARRIAGE_TYPE_BED, "seat_type": SEAT_TYPE_SOFT_SLEEPER, "seat_count": 32},   # 软卧 1 节
    {"carriage_number": "02", "carriage_type": CARRIAGE_TYPE_BED, "seat_type": SEAT_TYPE_HARD_SLEEPER, "seat_count": 66},   # 硬卧 1 节
    {"carriage_number": "03", "carriage_type": CARRIAGE_TYPE_BED, "seat_type": SEAT_TYPE_HARD_SLEEPER, "seat_count": 66},
    {"carriage_number": "04", "carriage_type": CARRIAGE_TYPE_BED, "seat_type": SEAT_TYPE_HARD_SLEEPER, "seat_count": 66},
    {"carriage_number": "05", "carriage_type": CARRIAGE_TYPE_BED, "seat_type": SEAT_TYPE_HARD_SLEEPER, "seat_count": 66},
    {"carriage_number": "06", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_HARD_SEAT, "seat_count": 118},   # 硬座 1 节
]

# 特快 T 型（25K 型）
T_EXPRESS = [
    {"carriage_number": "01", "carriage_type": CARRIAGE_TYPE_BED, "seat_type": SEAT_TYPE_SOFT_SLEEPER, "seat_count": 32},
    {"carriage_number": "02", "carriage_type": CARRIAGE_TYPE_BED, "seat_type": SEAT_TYPE_HARD_SLEEPER, "seat_count": 66},
    {"carriage_number": "03", "carriage_type": CARRIAGE_TYPE_BED, "seat_type": SEAT_TYPE_HARD_SLEEPER, "seat_count": 66},
    {"carriage_number": "04", "carriage_type": CARRIAGE_TYPE_BED, "seat_type": SEAT_TYPE_HARD_SLEEPER, "seat_count": 66},
    {"carriage_number": "05", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_HARD_SEAT, "seat_count": 118},
]

# 快速 K 型（25G 型）
K_EXPRESS = [
    {"carriage_number": "01", "carriage_type": CARRIAGE_TYPE_BED, "seat_type": SEAT_TYPE_HARD_SLEEPER, "seat_count": 66},
    {"carriage_number": "02", "carriage_type": CARRIAGE_TYPE_BED, "seat_type": SEAT_TYPE_HARD_SLEEPER, "seat_count": 66},
    {"carriage_number": "03", "carriage_type": CARRIAGE_TYPE_BED, "seat_type": SEAT_TYPE_HARD_SLEEPER, "seat_count": 66},
    {"carriage_number": "04", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_HARD_SEAT, "seat_count": 118},
]

# 默认配置（未知车次类型）
DEFAULT_LAYOUT = [
    {"carriage_number": "01", "carriage_type": CARRIAGE_TYPE_NORMAL, "seat_type": SEAT_TYPE_SECOND_CLASS, "seat_count": 90},
]

# 车次品牌到配置规则的映射
TRAIN_BRAND_TO_LAYOUT = {
    'G': G_HIGH_SPEED_16,  # 高铁默认使用 16 节长编组
    'C': C_INTERCITY_8,
    'D': D_EMU_8,
    'Z': Z_DIRECT_EXPRESS,
    'T': T_EXPRESS,
    'K': K_EXPRESS,
}


# =============================================================================
# 数据处理函数
# =============================================================================

def generate_seats_for_layout(layout_config: Dict[str, Any]) -> List[Tuple[str, str]]:
    """
    根据车厢配置生成座位号列表。

    Args:
        layout_config: 车厢配置字典

    Returns:
        [(carriage_number, seat_number), ...] 元组列表
    """
    carriage_number = layout_config['carriage_number']
    seat_type = layout_config['seat_type']
    seat_count = layout_config['seat_count']

    seat_numbers = []

    if seat_type == SEAT_TYPE_SECOND_CLASS:
        seat_numbers = generate_second_class_seat_numbers(seat_count)
    elif seat_type == SEAT_TYPE_FIRST_CLASS:
        seat_numbers = generate_first_class_seat_numbers(seat_count)
    elif seat_type == SEAT_TYPE_BUSINESS:
        seat_numbers = generate_business_seat_numbers(seat_count)
    elif seat_type == SEAT_TYPE_HARD_SEAT:
        seat_numbers = generate_hard_seat_numbers(seat_count)
    elif seat_type == SEAT_TYPE_SOFT_SLEEPER:
        seat_numbers = generate_sleeper_numbers(seat_count, is_soft=True)
    elif seat_type == SEAT_TYPE_HARD_SLEEPER:
        seat_numbers = generate_sleeper_numbers(seat_count, is_soft=False)

    return [(carriage_number, seat_num) for seat_num in seat_numbers]


def build_records_for_train(train_id: int, train_brand: str) -> Tuple[List[Dict], List[Dict]]:
    """
    为单个车次构建车厢和座位记录。

    Args:
        train_id: 车次 ID
        train_brand: 车次品牌（G/D/Z/T/K 等）

    Returns:
        (carriage_records, seat_records) 元组
    """
    layout = TRAIN_BRAND_TO_LAYOUT.get(train_brand, DEFAULT_LAYOUT)

    carriage_records = []
    seat_records = []

    for layout_config in layout:
        carriage_number = layout_config['carriage_number']
        carriage_type = layout_config['carriage_type']
        seat_type = layout_config['seat_type']
        seat_count = layout_config['seat_count']

        now = datetime.now()

        # 构建车厢记录
        carriage_records.append({
            'train_id': train_id,
            'carriage_number': carriage_number,
            'carriage_type': carriage_type,
            'seat_count': seat_count,
            'create_time': now,
            'update_time': now,
            'del_flag': 0
        })

        # 构建座位记录
        for carriage_num, seat_num in generate_seats_for_layout(layout_config):
            seat_records.append({
                'train_id': train_id,
                'carriage_number': carriage_num,
                'seat_number': seat_num,
                'seat_type': seat_type,
                'create_time': now,
                'update_time': now,
                'del_flag': 0
            })

    return carriage_records, seat_records


# =============================================================================
# 数据库操作
# =============================================================================

def get_trains_without_carriages() -> List[Dict]:
    """
    获取所有还没有车厢数据的车次。

    使用左连接查询，找出在 t_train 中存在但在 t_carriage 中不存在的车次。
    """
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        cursor.execute("""
            SELECT DISTINCT t.id, t.train_number, t.train_brand
            FROM t_train t
            LEFT JOIN t_carriage c ON t.id = c.train_id AND c.del_flag = 0
            WHERE t.del_flag = 0 AND c.id IS NULL
        """)
        trains = cursor.fetchall()
        logger.info(f"找到 {len(trains)} 个需要生成车厢数据的车次")
        return trains
    finally:
        cursor.close()
        conn.close()


def save_batch(
    carriage_records: List[Dict],
    seat_records: List[Dict]
) -> bool:
    """
    批量插入车厢和座位数据。

    使用 INSERT IGNORE 忽略已存在的数据（避免重复插入）。
    每批插入后立即提交并释放内存。
    """
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        # 批量插入车厢
        if carriage_records:
            sql_carriage = """
                INSERT IGNORE INTO t_carriage (
                    train_id, carriage_number, carriage_type, seat_count,
                    create_time, update_time, del_flag
                ) VALUES (%(train_id)s, %(carriage_number)s, %(carriage_type)s, %(seat_count)s,
                          %(create_time)s, %(update_time)s, %(del_flag)s)
            """

            for i in range(0, len(carriage_records), BATCH_SIZE):
                batch = carriage_records[i:i + BATCH_SIZE]
                cursor.executemany(sql_carriage, batch)

            conn.commit()

        # 批量插入座位（分批提交，避免单次事务过大）
        if seat_records:
            sql_seat = """
                INSERT IGNORE INTO t_seat (
                    train_id, carriage_number, seat_number, seat_type,
                    create_time, update_time, del_flag
                ) VALUES (%(train_id)s, %(carriage_number)s, %(seat_number)s, %(seat_type)s,
                          %(create_time)s, %(update_time)s, %(del_flag)s)
            """

            for i in range(0, len(seat_records), BATCH_SIZE):
                batch = seat_records[i:i + BATCH_SIZE]
                cursor.executemany(sql_seat, batch)
                conn.commit()

        return True

    except Exception as e:
        conn.rollback()
        logger.error(f"数据库操作失败：{e}")
        raise
    finally:
        cursor.close()
        conn.close()


def verify_data():
    """验证生成的数据是否完整。"""
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        # 统计各车次类型的车厢数和座位数
        cursor.execute("""
            SELECT t.train_brand, COUNT(DISTINCT c.id) as carriage_count,
                   COUNT(s.id) as seat_count
            FROM t_train t
            LEFT JOIN t_carriage c ON t.id = c.train_id AND c.del_flag = 0
            LEFT JOIN t_seat s ON t.id = s.train_id AND c.carriage_number = s.carriage_number AND s.del_flag = 0
            WHERE t.del_flag = 0
            GROUP BY t.train_brand
            ORDER BY t.train_brand
        """)

        stats = cursor.fetchall()

        logger.info("\n=== 车厢座位数据统计 ===")
        logger.info(f"{'车次类型':<10} {'车厢数':<10} {'座位数':<15}")
        logger.info("-" * 40)

        total_carriages = 0
        total_seats = 0

        for row in stats:
            brand = row['train_brand'] or '未知'
            carriage_count = row['carriage_count'] or 0
            seat_count = row['seat_count'] or 0

            total_carriages += carriage_count
            total_seats += seat_count

            logger.info(f"{brand:<10} {carriage_count:<10} {seat_count:<15}")

        logger.info("-" * 40)
        logger.info(f"{'总计':<10} {total_carriages:<10} {total_seats:<15}\n")

    finally:
        cursor.close()
        conn.close()


# =============================================================================
# 主程序
# =============================================================================

# 每处理多少个车次就写入数据库并清空内存（内存优化）
FLUSH_INTERVAL = 20


def main():
    """主函数：获取车次 -> 分批生成数据 -> 分批导入 -> 验证（内存优化版）"""
    logger.info("=" * 60)
    logger.info("座位和车厢数据生成脚本启动（低内存模式）")
    logger.info("=" * 60)

    start_time = datetime.now()

    try:
        # Step 1: 获取需要生成数据的车次
        trains = get_trains_without_carriages()

        if not trains:
            logger.info("所有车次已有车厢数据，无需生成")
            return 0

        total_trains = len(trains)
        logger.info(f"共需处理 {total_trains} 个车次，每 {FLUSH_INTERVAL} 个车次写入一次数据库")

        # Step 2: 分批处理车次，避免内存堆积
        processed_count = 0
        total_carriages = 0
        total_seats = 0

        batch_carriages = []
        batch_seats = []

        for train in trains:
            train_id = train['id']
            train_number = train['train_number']
            train_brand = train['train_brand']

            carriage_records, seat_records = build_records_for_train(train_id, train_brand)

            batch_carriages.extend(carriage_records)
            batch_seats.extend(seat_records)
            processed_count += 1

            # 每 FLUSH_INTERVAL 个车次写入一次数据库并清空内存
            if processed_count % FLUSH_INTERVAL == 0:
                save_batch(batch_carriages, batch_seats)
                total_carriages += len(batch_carriages)
                total_seats += len(batch_seats)
                logger.info(f"  进度：{processed_count}/{total_trains} 车次，"
                           f"本轮 {len(batch_carriages)} 车厢，{len(batch_seats)} 座位，"
                           f"累计 {total_carriages} 车厢，{total_seats} 座位")
                # 清空内存
                batch_carriages = []
                batch_seats = []

        # Step 3: 写入剩余数据
        if batch_carriages or batch_seats:
            save_batch(batch_carriages, batch_seats)
            total_carriages += len(batch_carriages)
            total_seats += len(batch_seats)
            logger.info(f"  最终批次：{len(batch_carriages)} 车厢，{len(batch_seats)} 座位")

        logger.info(f"成功插入 {total_carriages} 条车厢记录和 {total_seats} 条座位记录")

        # Step 4: 验证结果
        verify_data()

        elapsed = (datetime.now() - start_time).total_seconds()
        logger.info(f"脚本执行完成，耗时 {elapsed:.2f} 秒")

        return 0

    except Exception as e:
        logger.error(f"脚本执行失败：{e}", exc_info=True)
        return 1


if __name__ == '__main__':
    sys.exit(main())
