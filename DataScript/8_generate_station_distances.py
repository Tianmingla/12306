#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
站间距离数据生成脚本

功能：
    从 t_train_station 表中读取车次站点数据，计算任意两站之间的距离。
    基于站点顺序估算站间距离（假设相邻站点平均间距 30km）。
    生成的数据导入到 t_station_distance 表。

用法：
    python 8_generate_station_distances.py

"""

import os
import sys
import logging
from typing import Dict, List, Set, Any, Tuple
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

# 相邻站点平均间距（公里）
# 用于估算距离，实际距离应从铁路数据获取
AVG_ADJACENT_DISTANCE = 30

# 日志配置
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger(__name__)

# 批量插入大小
BATCH_SIZE = 1000


# =============================================================================
# 数据加载
# =============================================================================

def load_train_station_data() -> Dict[str, List[Dict[str, Any]]]:
    """
    从数据库加载车次站点数据。

    Returns:
        {train_number: [station_records]} 字典，每个车次的站点按序号排序
    """
    logger.info("加载车次站点数据...")

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        cursor.execute("""
            SELECT train_id, train_number, station_id, station_name, sequence
            FROM t_train_station
            WHERE del_flag = 0
            ORDER BY train_number, sequence
        """)

        train_data: Dict[str, List[Dict[str, Any]]] = {}

        for row in cursor.fetchall():
            train_number = row['train_number']
            if train_number not in train_data:
                train_data[train_number] = []
            train_data[train_number].append({
                'train_id': row['train_id'],
                'station_id': row['station_id'],
                'station_name': row['station_name'],
                'sequence': row['sequence']
            })

        logger.info(f"加载完成: {len(train_data)} 个车次")
        return train_data

    finally:
        cursor.close()
        conn.close()


def load_existing_distances() -> Set[Tuple[int, str, str]]:
    """
    加载已存在的站间距离记录，避免重复插入。

    Returns:
        {(train_id, dep_station, arr_station)} 集合
    """
    logger.info("加载已存在的站间距离数据...")

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        cursor.execute("""
            SELECT train_id, departure_station_name, arrival_station_name
            FROM t_station_distance
            WHERE del_flag = 0
        """)

        existing = set()
        for row in cursor.fetchall():
            existing.add((row['train_id'], row['departure_station_name'], row['arrival_station_name']))

        logger.info(f"已有 {len(existing)} 条站间距离记录")
        return existing

    finally:
        cursor.close()
        conn.close()


# =============================================================================
# 距离计算
# =============================================================================

def calculate_distances(train_data: Dict[str, List[Dict[str, Any]]]) -> List[Dict[str, Any]]:
    """
    计算每列车任意两站之间的距离。

    距离计算方式：
    - 假设相邻站点间距为 AVG_ADJACENT_DISTANCE 公里
    - 实际生产环境应从铁路数据获取真实里程

    Args:
        train_data: 车次站点数据

    Returns:
        站间距离记录列表
    """
    logger.info("计算站间距离...")

    distance_records = []
    total_pairs = 0

    for train_number, stations in train_data.items():
        if len(stations) < 2:
            continue

        train_id = stations[0]['train_id']

        # 计算每对站点之间的距离
        for i in range(len(stations)):
            for j in range(i + 1, len(stations)):
                dep_station = stations[i]
                arr_station = stations[j]

                # 计算站序差，估算距离
                seq_diff = arr_station['sequence'] - dep_station['sequence']
                estimated_distance = seq_diff * AVG_ADJACENT_DISTANCE

                distance_records.append({
                    'train_id': train_id,
                    'train_number': train_number,
                    'departure_station_id': dep_station['station_id'],
                    'departure_station_name': dep_station['station_name'],
                    'arrival_station_id': arr_station['station_id'],
                    'arrival_station_name': arr_station['station_name'],
                    'distance': estimated_distance,
                    'line_name': None  # 线路名称需要后续补充
                })
                total_pairs += 1

        # 进度日志
        if len(distance_records) % 100000 == 0:
            logger.info(f"已计算 {len(distance_records)} 条站间距离...")

    logger.info(f"计算完成: {len(distance_records)} 条站间距离记录，来自 {len(train_data)} 个车次")
    return distance_records


def filter_new_records(
    distance_records: List[Dict[str, Any]],
    existing: Set[Tuple[int, str, str]]
) -> List[Dict[str, Any]]:
    """
    过滤掉已存在的记录。

    Args:
        distance_records: 新计算的站间距离记录
        existing: 已存在的记录集合

    Returns:
        需要插入的新记录
    """
    new_records = []

    for record in distance_records:
        key = (record['train_id'], record['departure_station_name'], record['arrival_station_name'])
        if key not in existing:
            new_records.append(record)

    logger.info(f"过滤完成: {len(distance_records)} -> {len(new_records)} 条新记录")
    return new_records


# =============================================================================
# 数据导入
# =============================================================================

def import_to_database(records: List[Dict[str, Any]]) -> int:
    """
    将站间距离数据批量导入到 t_station_distance 表。

    Args:
        records: 站间距离记录列表

    Returns:
        成功导入的记录数
    """
    if not records:
        logger.warning("没有需要导入的数据")
        return 0

    logger.info(f"开始导入 {len(records)} 条站间距离数据...")

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    sql = """
        INSERT IGNORE INTO t_station_distance (
            train_id, train_number,
            departure_station_id, departure_station_name,
            arrival_station_id, arrival_station_name,
            distance, line_name, del_flag
        ) VALUES (
            %(train_id)s, %(train_number)s,
            %(departure_station_id)s, %(departure_station_name)s,
            %(arrival_station_id)s, %(arrival_station_name)s,
            %(distance)s, %(line_name)s, 0
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
        cursor.execute("SELECT COUNT(*) as total FROM t_station_distance WHERE del_flag = 0")
        total = cursor.fetchone()['total']

        # 统计车次数
        cursor.execute("""
            SELECT COUNT(DISTINCT train_number) as count
            FROM t_station_distance
            WHERE del_flag = 0
        """)
        train_count = cursor.fetchone()['count']

        # 统计平均距离
        cursor.execute("""
            SELECT AVG(distance) as avg_distance
            FROM t_station_distance
            WHERE del_flag = 0
        """)
        avg_distance = cursor.fetchone()['avg_distance'] or 0

        # 最大距离
        cursor.execute("""
            SELECT MAX(distance) as max_distance
            FROM t_station_distance
            WHERE del_flag = 0
        """)
        max_distance = cursor.fetchone()['max_distance'] or 0

        stats = {
            'total': total,
            'train_count': train_count,
            'avg_distance': round(avg_distance, 2),
            'max_distance': max_distance
        }

        logger.info(f"数据校验完成:")
        logger.info(f"  - 总站间距离记录: {total}")
        logger.info(f"  - 覆盖车次: {train_count}")
        logger.info(f"  - 平均距离: {avg_distance:.2f} km")
        logger.info(f"  - 最大距离: {max_distance} km")

        return stats

    finally:
        cursor.close()
        conn.close()


# =============================================================================
# 主程序
# =============================================================================

def main():
    """主函数：加载数据 -> 计算距离 -> 过滤 -> 导入 -> 校验"""
    logger.info("=" * 60)
    logger.info("站间距离数据生成脚本启动")
    logger.info("=" * 60)

    start_time = datetime.now()

    try:
        # Step 1: 加载车次站点数据
        train_data = load_train_station_data()

        # Step 2: 加载已存在的记录
        existing = load_existing_distances()

        # Step 3: 计算站间距离
        distance_records = calculate_distances(train_data)

        # Step 4: 过滤已存在的记录
        new_records = filter_new_records(distance_records, existing)

        # Step 5: 导入数据库
        imported = import_to_database(new_records)

        # Step 6: 校验结果
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
