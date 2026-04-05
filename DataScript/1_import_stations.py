#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
车站数据导入脚本

功能：
    从 12306 的 station_name.txt 文件解析车站数据并导入到 t_station 表。
    支持增量导入、自动去重、数据清洗。

数据源格式：
    station_names = '@bjb|北京北|VAP|beijingbei|bjb|0|0501|北京市|bjsh|...'

用法：
    python 1_import_stations.py


"""

import re
import sys
import logging
from typing import List, Dict, Any
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

# 数据源文件路径
STATION_DATA_FILE = 'station_name.txt'

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
# 数据解析
# =============================================================================

def parse_station_data(file_path: str) -> List[Dict[str, Any]]:
    """
    从 station_name.txt 解析车站数据。

    12306 数据格式说明：
        @电报码|车站名|电报码|拼音全拼|拼音简写|排序|地区编码|地区名称|...
        例如：@bjb|北京北|VAP|beijingbei|bjb|0|0501|北京市|...

    Args:
        file_path: station_name.txt 文件路径

    Returns:
        解析后的车站列表，每个元素为字典格式：
        {
            'code': 电报码（如 VAP），
            'name': 车站名（如 北京北），
            'spell': 拼音全拼，
            'region': 地区编码，
            'region_name': 地区名称
        }

    Raises:
        FileNotFoundError: 文件不存在
        ValueError: 文件格式错误
    """
    logger.info(f"开始解析车站数据文件: {file_path}")

    # 读取文件内容
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except FileNotFoundError:
        logger.error(f"文件不存在: {file_path}")
        raise

    # 提取 station_names 变量内容
    # 格式：station_names = '...'
    match = re.search(r"station_names\s*=\s*'([^']*)'", content)
    if not match:
        logger.error("未找到 station_names 数据，文件格式可能已变更")
        raise ValueError("无法解析 station_name.txt 文件格式")

    data_str = match.group(1)
    entries = data_str.split('@')

    stations = []
    seen_names = set()  # 用于去重
    skipped_count = 0

    for entry in entries[1:]:  # 跳过第一个空项（split 后的首元素为空）
        entry = entry.strip()
        if not entry:
            continue

        fields = entry.split('|')

        # 字段数量校验（至少需要 8 个字段）
        if len(fields) < 8:
            logger.debug(f"跳过无效行（字段不足）: {entry[:50]}...")
            skipped_count += 1
            continue

        # 解析各字段
        # 格式：@code|name|telegraph|pinyin_full|pinyin_short|sort|region|region_name|...
        code = fields[0].strip()        # 电报码（有时与 telegraph 不同）
        name = fields[1].strip()        # 车站中文名
        tele_code = fields[2].strip()   # 电报码
        pinyin_full = fields[3].strip() # 拼音全拼
        # fields[4] = 拼音简写
        # fields[5] = 排序值
        region = fields[6].strip() if len(fields) > 6 else None      # 地区编码
        region_name = fields[7].strip() if len(fields) > 7 else None # 地区名称

        # 数据清洗：过滤无效数据
        if not name or not tele_code:
            logger.debug(f"跳过无效车站（名称或编码为空）: {entry[:50]}...")
            skipped_count += 1
            continue

        # 去重：同一车站名只保留一条
        if name in seen_names:
            logger.debug(f"跳过重复车站: {name}")
            skipped_count += 1
            continue

        seen_names.add(name)

        stations.append({
            'code': tele_code,       # 使用电报码作为唯一编码
            'name': name,
            'spell': pinyin_full,
            'region': region if region else None,
            'region_name': region_name if region_name else None
        })

    logger.info(f"解析完成: 共 {len(stations)} 个有效车站，跳过 {skipped_count} 条无效/重复数据")

    return stations


# =============================================================================
# 数据导入
# =============================================================================

def import_to_database(stations: List[Dict[str, Any]]) -> int:
    """
    将车站数据批量导入到 t_station 表。

    使用 INSERT ... ON DUPLICATE KEY UPDATE 实现增量导入：
    - 新车站：插入
    - 已存在车站：更新 name, spell, region, region_name

    Args:
        stations: 车站数据列表

    Returns:
        成功导入的记录数
    """
    if not stations:
        logger.warning("没有需要导入的车站数据")
        return 0

    logger.info(f"开始导入 {len(stations)} 条车站数据到数据库...")

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    # SQL 语句：使用 ON DUPLICATE KEY UPDATE 实现增量导入
    sql = """
        INSERT INTO t_station (code, name, spell, region, region_name)
        VALUES (%(code)s, %(name)s, %(spell)s, %(region)s, %(region_name)s)
        ON DUPLICATE KEY UPDATE
            name = VALUES(name),
            spell = VALUES(spell),
            region = VALUES(region),
            region_name = VALUES(region_name)
    """

    success_count = 0
    error_count = 0

    try:
        # 分批插入，避免单次 SQL 过大
        for i in range(0, len(stations), BATCH_SIZE):
            batch = stations[i:i + BATCH_SIZE]
            try:
                cursor.executemany(sql, batch)
                success_count += len(batch)
                logger.debug(f"已导入 {success_count}/{len(stations)} 条")
            except Exception as e:
                error_count += len(batch)
                logger.error(f"批次 {i//BATCH_SIZE + 1} 导入失败: {e}")

        conn.commit()
        logger.info(f"导入完成: 成功 {success_count} 条，失败 {error_count} 条")

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

def verify_import() -> Dict[str, int]:
    """
    校验导入结果，统计数据库中的车站数据。

    Returns:
        统计信息字典
    """
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        # 统计总数
        cursor.execute("SELECT COUNT(*) as total FROM t_station WHERE del_flag = 0")
        total = cursor.fetchone()['total']

        # 统计有地区信息的车站数
        cursor.execute("""
            SELECT COUNT(*) as count
            FROM t_station
            WHERE del_flag = 0 AND region IS NOT NULL AND region != ''
        """)
        with_region = cursor.fetchone()['count']

        # 统计地区分布（前 10）
        cursor.execute("""
            SELECT region_name, COUNT(*) as count
            FROM t_station
            WHERE del_flag = 0 AND region_name IS NOT NULL
            GROUP BY region_name
            ORDER BY count DESC
            LIMIT 10
        """)
        top_regions = cursor.fetchall()

        stats = {
            'total': total,
            'with_region': with_region,
            'top_regions': top_regions
        }

        logger.info(f"数据校验完成: 共 {total} 个车站，其中 {with_region} 个有地区信息")
        logger.info(f"车站数 Top 10 地区: {[r['region_name'] for r in top_regions]}")

        return stats

    finally:
        cursor.close()
        conn.close()


# =============================================================================
# 主程序
# =============================================================================

def main():
    """主函数：解析 -> 导入 -> 校验"""
    logger.info("=" * 60)
    logger.info("车站数据导入脚本启动")
    logger.info("=" * 60)

    start_time = datetime.now()

    try:
        # Step 1: 解析数据
        stations = parse_station_data(STATION_DATA_FILE)

        # Step 2: 导入数据库
        imported = import_to_database(stations)

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
