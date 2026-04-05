#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
车次数据导入脚本

功能：
    从 data/ 目录下的 JSON 文件中提取车次信息并导入到 t_train 表。
    自动识别车次类型（高铁/动车/普速）、去重、增量导入。

数据源：
    data/G/*.json  - 高铁数据
    data/D/*.json  - 动车数据
    data/Z/*.json  - 直达特快数据
    data/T/*.json  - 特快数据
    data/K/*.json  - 快速数据
    ...

用法：
    python 2_import_trains.py


"""

import os
import json
import sys
import logging
from typing import Set, Tuple, List, Dict, Any
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

# 数据根目录
DATA_ROOT = 'data'

# 日志配置
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger(__name__)

# 批量插入大小
BATCH_SIZE = 500


# =============================================================================
# 车次类型识别
# =============================================================================

# 车次类型枚举（与数据库 t_train.train_type 对应）
TRAIN_TYPE_HIGH_SPEED = 0   # 高铁（G/C）
TRAIN_TYPE_EMU = 1          # 动车（D）
TRAIN_TYPE_NORMAL = 2       # 普速（Z/T/K/Y/S/L 等）

# 车次类型映射表
TRAIN_TYPE_MAP = {
    'G': TRAIN_TYPE_HIGH_SPEED,  # 高铁
    'C': TRAIN_TYPE_HIGH_SPEED,  # 城际高铁
    'D': TRAIN_TYPE_EMU,         # 动车
    'Z': TRAIN_TYPE_NORMAL,      # 直达特快
    'T': TRAIN_TYPE_NORMAL,      # 特快
    'K': TRAIN_TYPE_NORMAL,      # 快速
    'Y': TRAIN_TYPE_NORMAL,      # 旅游列车
    'S': TRAIN_TYPE_NORMAL,      # 市郊列车
    'L': TRAIN_TYPE_NORMAL,      # 临客
}


def parse_train_type(train_number: str) -> Tuple[int, str]:
    """
    根据车次号识别车次类型和品牌。

    规则：
        - G/C 开头：高铁（train_type=0）
        - D 开头：动车（train_type=1）
        - 其他：普速（train_type=2）
        - train_brand 取首字母大写

    Args:
        train_number: 车次号（如 G1234, D5678, K123）

    Returns:
        (train_type, train_brand) 元组
        - train_type: 0=高铁, 1=动车, 2=普速
        - train_brand: 首字母（G/D/Z/T/K 等）
    """
    if not train_number or len(train_number) < 1:
        return (TRAIN_TYPE_NORMAL, 'OTHER')

    prefix = train_number[0].upper()
    train_type = TRAIN_TYPE_MAP.get(prefix, TRAIN_TYPE_NORMAL)

    return (train_type, prefix)


# =============================================================================
# 数据收集
# =============================================================================

def collect_unique_trains() -> Set[str]:
    """
    遍历 data/ 目录下所有 JSON 文件，提取唯一车次号。

    目录结构：
        data/
        ├── G/           # 高铁数据目录
        │   ├── xxx.json
        │   └── ...
        ├── D/           # 动车数据目录
        ├── index/       # 索引文件（跳过）
        └── ...

    JSON 文件格式：
        {
            "data": {
                "data": [
                    {
                        "station_train_code": "G1234",
                        ...
                    },
                    ...
                ]
            }
        }

    Returns:
        唯一车次号集合
    """
    logger.info(f"开始扫描数据目录: {DATA_ROOT}")

    train_set = set()
    file_count = 0
    error_count = 0

    # 遍历 data/ 下的子目录
    for train_type_dir in os.listdir(DATA_ROOT):
        dir_path = os.path.join(DATA_ROOT, train_type_dir)

        # 跳过非目录文件和 index 目录
        if not os.path.isdir(dir_path) or train_type_dir == 'index':
            continue

        logger.info(f"扫描目录: {train_type_dir}")

        # 遍历目录下的 JSON 文件
        for filename in os.listdir(dir_path):
            if not filename.endswith('.json'):
                continue

            json_path = os.path.join(dir_path, filename)
            file_count += 1

            try:
                with open(json_path, 'r', encoding='utf-8') as f:
                    data = json.load(f)

                # 提取 station_train_code（车次号）
                stations = data.get('data', {}).get('data', [])
                if stations and len(stations) > 0:
                    train_number = stations[0].get('station_train_code', '').strip()
                    if train_number:
                        train_set.add(train_number)

            except json.JSONDecodeError as e:
                logger.warning(f"JSON 解析失败: {json_path}, 错误: {e}")
                error_count += 1
            except Exception as e:
                logger.warning(f"文件读取失败: {json_path}, 错误: {e}")
                error_count += 1

    logger.info(f"扫描完成: 共 {file_count} 个文件，发现 {len(train_set)} 个唯一车次，{error_count} 个文件错误")

    return train_set


# =============================================================================
# 数据导入
# =============================================================================

def get_existing_trains() -> Set[str]:
    """
    查询数据库中已存在的车次号。

    Returns:
        已存在的车次号集合
    """
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        cursor.execute("SELECT train_number FROM t_train WHERE del_flag = 0")
        return {row['train_number'] for row in cursor.fetchall()}
    finally:
        cursor.close()
        conn.close()


def import_trains_to_database(train_numbers: Set[str]) -> int:
    """
    将车次数据批量导入到 t_train 表。

    使用 INSERT IGNORE 忽略已存在的车次。

    Args:
        train_numbers: 车次号集合

    Returns:
        成功导入的记录数
    """
    if not train_numbers:
        logger.warning("没有需要导入的车次数据")
        return 0

    # 过滤已存在的车次
    existing = get_existing_trains()
    new_trains = train_numbers - existing

    if not new_trains:
        logger.info("所有车次已存在于数据库，无需导入")
        return 0

    logger.info(f"发现 {len(new_trains)} 个新车次，开始导入...")

    # 准备数据
    records = []
    for train_number in sorted(new_trains):
        train_type, train_brand = parse_train_type(train_number)
        records.append({
            'train_number': train_number,
            'train_type': train_type,
            'train_brand': train_brand,
            'sale_status': 0  # 默认可售
        })

    # 批量插入
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    sql = """
        INSERT IGNORE INTO t_train (train_number, train_type, train_brand, sale_status, del_flag)
        VALUES (%(train_number)s, %(train_type)s, %(train_brand)s, %(sale_status)s, 0)
    """

    success_count = 0

    try:
        for i in range(0, len(records), BATCH_SIZE):
            batch = records[i:i + BATCH_SIZE]
            cursor.executemany(sql, batch)
            success_count += len(batch)
            logger.debug(f"已导入 {success_count}/{len(records)} 条")

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
    校验导入结果，统计数据库中的车次数据。

    Returns:
        统计信息字典
    """
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        # 统计总数
        cursor.execute("SELECT COUNT(*) as total FROM t_train WHERE del_flag = 0")
        total = cursor.fetchone()['total']

        # 按类型统计
        cursor.execute("""
            SELECT train_type, COUNT(*) as count
            FROM t_train
            WHERE del_flag = 0
            GROUP BY train_type
            ORDER BY train_type
        """)
        by_type = cursor.fetchall()

        # 按品牌统计
        cursor.execute("""
            SELECT train_brand, COUNT(*) as count
            FROM t_train
            WHERE del_flag = 0
            GROUP BY train_brand
            ORDER BY count DESC
        """)
        by_brand = cursor.fetchall()

        type_names = {0: '高铁', 1: '动车', 2: '普速'}

        stats = {
            'total': total,
            'by_type': [(type_names.get(r['train_type'], '未知'), r['count']) for r in by_type],
            'by_brand': [(r['train_brand'], r['count']) for r in by_brand]
        }

        logger.info(f"数据校验完成: 共 {total} 个车次")
        for type_name, count in stats['by_type']:
            logger.info(f"  - {type_name}: {count} 个")

        return stats

    finally:
        cursor.close()
        conn.close()


# =============================================================================
# 主程序
# =============================================================================

def main():
    """主函数：收集 -> 导入 -> 校验"""
    logger.info("=" * 60)
    logger.info("车次数据导入脚本启动")
    logger.info("=" * 60)

    start_time = datetime.now()

    try:
        # Step 1: 收集唯一车次
        train_numbers = collect_unique_trains()

        # Step 2: 导入数据库
        imported = import_trains_to_database(train_numbers)

        # Step 3: 校验结果
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
