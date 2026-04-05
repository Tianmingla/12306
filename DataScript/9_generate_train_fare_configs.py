#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
列车票价配置数据生成脚本

功能：
    根据车次类型（G/C/D/Z/T/K等）自动生成票价上浮配置。
    生成的数据导入到 t_train_fare_config 表。

上浮类型说明：
    0 - 普通车 (1.0)
    1 - 新型空调车 50% 上浮 (1.5)
    2 - 新型空调车一档折扣 40% 上浮 (1.4)
    3 - 新型空调车二档折扣 30% 上浮 (1.3)
    4 - 高级软卧 180% 上浮 (2.8)
    5 - 高级软卧 208% 上浮 (3.08)

用法：
    python 9_generate_train_fare_configs.py

"""

import sys
import logging
from typing import Dict, List, Set, Any
from datetime import datetime
import pymysql
from pymysql.cursors import DictCursor

# =============================================================================
# 配置区域
# =============================================================================

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'my12306',
    'charset': 'utf8mb4',
    'cursorclass': DictCursor
}

# 日志配置
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger(__name__)

# 批量插入大小
BATCH_SIZE = 1000

# 车次品牌 -> 上浮类型映射
# 根据车次品牌推断上浮类型
TRAIN_BRAND_SURCHARGE_MAP = {
    'G': 1,   # 高铁 - 新型空调车 50% 上浮
    'C': 1,   # 城际高速 - 新型空调车 50% 上浮
    'D': 1,   # 动车 - 新型空调车 50% 上浮
    'Z': 1,   # 直达特快 - 新型空调车 50% 上浮
    'T': 1,   # 特快 - 新型空调车 50% 上浮
    'K': 1,   # 快速 - 新型空调车 50% 上浮
    'Y': 0,   # 旅游列车 - 普通车
    'L': 0,   # 临客 - 普通车
}


# =============================================================================
# 数据加载
# =============================================================================

def load_train_data() -> List[Dict[str, Any]]:
    """
    从数据库加载列车数据。

    Returns:
        列车记录列表
    """
    logger.info("加载列车数据...")

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        cursor.execute("""
            SELECT id, train_number, train_brand
            FROM t_train
            WHERE del_flag = 0
        """)

        trains = cursor.fetchall()
        logger.info(f"加载完成: {len(trains)} 个列车")
        return trains

    finally:
        cursor.close()
        conn.close()


def load_existing_configs() -> Set[int]:
    """
    加载已存在的票价配置记录，避免重复插入。

    Returns:
        {train_id} 集合
    """
    logger.info("加载已存在的票价配置数据...")

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        cursor.execute("""
            SELECT train_id FROM t_train_fare_config WHERE del_flag = 0
        """)

        existing = {row['train_id'] for row in cursor.fetchall()}
        logger.info(f"已有 {len(existing)} 条票价配置记录")
        return existing

    finally:
        cursor.close()
        conn.close()


# =============================================================================
# 配置生成
# =============================================================================

def get_surcharge_type(train_number: str, train_brand: str = None) -> int:
    """
    根据车次号和品牌推断上浮类型。

    Args:
        train_number: 车次号（如 G1234）
        train_brand: 车次品牌（G/C/D/Z/T/K 等）

    Returns:
        上浮类型代码
    """
    # 优先使用品牌映射
    if train_brand and train_brand in TRAIN_BRAND_SURCHARGE_MAP:
        return TRAIN_BRAND_SURCHARGE_MAP[train_brand]

    # 从车次号推断
    if train_number:
        first_char = train_number[0].upper()
        if first_char in TRAIN_BRAND_SURCHARGE_MAP:
            return TRAIN_BRAND_SURCHARGE_MAP[first_char]

    # 默认：新型空调车
    return 1


def generate_fare_configs(
    trains: List[Dict[str, Any]],
    existing: Set[int]
) -> List[Dict[str, Any]]:
    """
    生成票价配置记录。

    Args:
        trains: 列车数据列表
        existing: 已存在的 train_id 集合

    Returns:
        票价配置记录列表
    """
    logger.info("生成票价配置...")

    configs = []

    for train in trains:
        train_id = train['id']
        train_number = train['train_number']
        train_brand = train['train_brand']

        # 跳过已存在的
        if train_id in existing:
            continue

        # 推断上浮类型
        surcharge_type = get_surcharge_type(train_number, train_brand)

        configs.append({
            'train_id': train_id,
            'train_number': train_number,
            'surcharge_type': surcharge_type,
            'is_peak_season': 0,  # 默认非春运期间
            'effective_date': None,
            'expire_date': None
        })

    logger.info(f"生成完成: {len(configs)} 条新配置")
    return configs


# =============================================================================
# 数据导入
# =============================================================================

def import_to_database(records: List[Dict[str, Any]]) -> int:
    """
    将票价配置数据批量导入到 t_train_fare_config 表。

    Args:
        records: 票价配置记录列表

    Returns:
        成功导入的记录数
    """
    if not records:
        logger.warning("没有需要导入的数据")
        return 0

    logger.info(f"开始导入 {len(records)} 条票价配置数据...")

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    sql = """
        INSERT IGNORE INTO t_train_fare_config (
            train_id, train_number, surcharge_type, is_peak_season,
            effective_date, expire_date, del_flag
        ) VALUES (
            %(train_id)s, %(train_number)s, %(surcharge_type)s, %(is_peak_season)s,
            %(effective_date)s, %(expire_date)s, 0
        )
    """

    success_count = 0

    try:
        for i in range(0, len(records), BATCH_SIZE):
            batch = records[i:i + BATCH_SIZE]
            cursor.executemany(sql, batch)
            success_count += len(batch)
            logger.info(f"已导入 {success_count}/{len(records)} 条")

        conn.commit()
        logger.info(f"导入完成: 成功 {success_count} 条")

    except Exception as e:
        conn.rollback()
        logger.error(f"数据库操作失败，已回滚: {e}")
        raise
    finally:
        cursor.close()
        conn.close()

    return success_count


# =============================================================================
# 数据校验
# =============================================================================

def verify_import() -> Dict[str, Any]:
    """
    校验导入结果。

    Returns:
        统计信息字典
    """
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        # 统计总数
        cursor.execute("SELECT COUNT(*) as total FROM t_train_fare_config WHERE del_flag = 0")
        total = cursor.fetchone()['total']

        # 按上浮类型统计
        cursor.execute("""
            SELECT surcharge_type, COUNT(*) as count
            FROM t_train_fare_config
            WHERE del_flag = 0
            GROUP BY surcharge_type
            ORDER BY surcharge_type
        """)

        type_stats = cursor.fetchall()

        stats = {
            'total': total,
            'by_type': type_stats
        }

        logger.info(f"数据校验完成:")
        logger.info(f"  - 总配置记录: {total}")
        for row in type_stats:
            logger.info(f"  - 上浮类型 {row['surcharge_type']}: {row['count']} 条")

        return stats

    finally:
        cursor.close()
        conn.close()


# =============================================================================
# 主程序
# =============================================================================

def main():
    """主函数：加载数据 -> 生成配置 -> 导入 -> 校验"""
    logger.info("=" * 60)
    logger.info("列车票价配置数据生成脚本启动")
    logger.info("=" * 60)

    start_time = datetime.now()

    try:
        # Step 1: 加载列车数据
        trains = load_train_data()

        # Step 2: 加载已存在的记录
        existing = load_existing_configs()

        # Step 3: 生成票价配置
        configs = generate_fare_configs(trains, existing)

        # Step 4: 导入数据库
        imported = import_to_database(configs)

        # Step 5: 校验结果
        if imported > 0:
            verify_import()

        elapsed = (datetime.now() - start_time).total_seconds()
        logger.info(f"脚本执行完成，耗时 {elapsed:.2f} 秒")

        return 0

    except Exception as e:
        logger.error(f"脚本执行失败: {e}", exc_info=True)
        return 1


if __name__ == '__main__':
    sys.exit(main())
