#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
车次车站关系数据导入脚本

功能：
    从 data/ 目录下的 JSON 文件中提取车次经停站信息并导入到 t_train_station 表。
    支持自动关联 train_id、数据清洗、去重、增量导入。

数据源：
    data/G/*.json  - 高铁数据
    data/D/*.json  - 动车数据
    ...

用法：
    python 3_import_train_stations.py


"""

import os
import json
import re
import sys
import logging
from typing import Dict, List, Set, Any, Optional
from datetime import datetime,time, timedelta
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

# 默认运行日期（用于构造完整的时间戳）
DEFAULT_RUN_DATE = '2025-01-01'

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

def load_train_mapping() -> Dict[str, int]:
    """
    从数据库加载车次号到 ID 的映射。

    Returns:
        {train_number: train_id} 字典
    """
    logger.info("加载车次映射...")

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        cursor.execute("SELECT id, train_number FROM t_train WHERE del_flag = 0")
        mapping = {row['train_number']: row['id'] for row in cursor.fetchall()}
        logger.info(f"加载完成: {len(mapping)} 个车次映射")
        return mapping
    finally:
        cursor.close()
        conn.close()


def load_station_mapping() -> Dict[str, int]:
    """
    从数据库加载车站名到 ID 的映射。

    Returns:
        {station_name: station_id} 字典
    """
    logger.info("加载车站映射...")

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        cursor.execute("SELECT id, name FROM t_station WHERE del_flag = 0")
        mapping = {row['name']: row['id'] for row in cursor.fetchall()}
        logger.info(f"加载完成: {len(mapping)} 个车站映射")
        return mapping
    finally:
        cursor.close()
        conn.close()


# =============================================================================
# 时间解析
# =============================================================================

def parse_time(time_str: Optional[str]) -> Optional[datetime]:
    """
    解析时间字符串为 datetime 对象。

    输入格式：'08:30', '--', None
    输出：datetime(2025-01-01 08:30:00) 或 None

    Args:
        time_str: 时间字符串

    Returns:
        datetime 对象或 None
    """
    if not time_str or time_str == '--':
        return None

    try:
        return datetime.strptime(f"{DEFAULT_RUN_DATE} {time_str}", "%Y-%m-%d %H:%M")
    except ValueError:
        return None


def calculate_stopover(arrival: Optional[time], departure: Optional[time]) -> Optional[int]:
   """
       计算停留时间（分钟）。

       Args:
           arrival: 到站时间 (datetime.time)
           departure: 离站时间 (datetime.time)
           diff_day: 是否隔天标记。为 '0' (当天) 或 '1' (次日)。


       Returns:
           停留分钟数，或 None
   """
   # 1. 基础校验：必须有到站和离站时间
   if not arrival or not departure:
       return None

   # 2. 选取一个基准日期（任意日期均可，如今天）
   base_date = datetime.now().date()

   # 3. 组合成完整的 datetime 对象
   dt_arrival = datetime.combine(base_date, arrival)
   dt_departure = datetime.combine(base_date, departure)

   # 4. 处理跨天逻辑
   if dt_arrival>dt_departure:
       dt_departure += timedelta(days=1)

   # 5. 计算差值
   delta = dt_departure - dt_arrival

   # 6. 转换为分钟
   total_seconds = max(0, delta.total_seconds())
   return int(total_seconds // 60)


# =============================================================================
# 数据处理
# =============================================================================

def process_station_data(
    train_mapping: Dict[str, int],
    station_mapping: Dict[str, int]
) -> List[Dict[str, Any]]:
    """
    遍历 data/ 目录，解析 JSON 文件，提取车次站点数据。

    JSON 文件格式：
        {
            "data": {
                "data": [
                    {
                        "station_no": "1",
                        "station_name": "北京南",
                        "arrive_time": "--",
                        "start_time": "08:00",
                        ...
                    },
                    ...
                ]
            }
        }

    Args:
        train_mapping: 车次号 -> train_id 映射
        station_mapping: 车站名 -> station_id 映射

    Returns:
        站点记录列表
    """
    logger.info("开始处理车次站点数据...")

    all_records = []
    file_count = 0
    skipped_trains = 0
    skipped_stations = 0

    # 遍历 data/ 下的子目录
    for train_type_dir in os.listdir(DATA_ROOT):
        dir_path = os.path.join(DATA_ROOT, train_type_dir)

        # 跳过非目录文件和 index 目录
        if not os.path.isdir(dir_path) or train_type_dir == 'index':
            continue

        logger.info(f"处理目录: {train_type_dir}")

        # 遍历 JSON 文件
        for filename in os.listdir(dir_path):
            if not filename.endswith('.json'):
                continue

            json_path = os.path.join(dir_path, filename)
            file_count += 1

            try:
                with open(json_path, 'r', encoding='utf-8') as f:
                    data = json.load(f)

                stations = data.get('data', {}).get('data', [])
                if not stations:
                    continue

                # 提取车次号
                train_number = stations[0].get('station_train_code', '').strip()
                if not train_number:
                    continue

                # 获取 train_id
                train_id = train_mapping.get(train_number)
                if train_id is None:
                    skipped_trains += 1
                    logger.debug(f"车次不存在于 t_train: {train_number}")
                    continue

                # 处理每个站点
                for st in stations:
                    seq = int(st.get('station_no', 0))
                    station_name = st.get('station_name', '').strip()
                    # 清洗车站名（去除空白字符）
                    station_name = re.sub(r'\s+', '', station_name)

                    if not station_name:
                        continue

                    # 获取 station_id（可能不存在）
                    station_id = station_mapping.get(station_name)
                    arrive_time= None
                    departure_time = None

                    arrive_datetime=parse_time(st.get('arrive_time'))
                    departure_datetime=parse_time(st.get('start_time'))
                    # 解析时间
                    if arrive_datetime is not None:
                        arrive_time = arrive_datetime.time()
                    else:
                        arrive_time = None
                    if departure_datetime is not None:
                        depart_time = departure_datetime.time()
                    else:
                        depart_time = None

                    # 首站：arrival_time 为 None
                    if seq == 1:
                        arrive_time = None
                    # 末站：departure_time 为 None
                    if seq == len(stations):
                        depart_time = None

                    # 计算停留时间
                    stopover = calculate_stopover(arrive_time, depart_time)

                    arrive_day_diff=st.get('arrive_day_diff')

                    # 数据清洗：检查必填字段
                    if arrive_time is None and depart_time is None:
                        skipped_stations += 1
                        continue

                    all_records.append({
                        'train_id': train_id,
                        'train_number': train_number,
                        'station_id': station_id,
                        'station_name': station_name,
                        'sequence': seq,
                        'arrival_time': arrive_time,
                        'departure_time': depart_time,
                        'stopover_time': stopover,
                        'arrive_day_diff': arrive_day_diff,
                        'run_date': datetime.strptime(DEFAULT_RUN_DATE, '%Y-%m-%d').date()
                    })

            except json.JSONDecodeError as e:
                logger.warning(f"JSON 解析失败: {json_path}, 错误: {e}")
            except Exception as e:
                logger.warning(f"文件处理失败: {json_path}, 错误: {e}")

    logger.info(
        f"处理完成: {file_count} 个文件，"
        f"{len(all_records)} 条站点记录，"
        f"跳过 {skipped_trains} 个未知车次，"
        f"{skipped_stations} 个无效站点"
    )

    return all_records


def deduplicate_records(records: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    去重：同一车次同一站点序号只保留一条。

    Args:
        records: 原始记录列表

    Returns:
        去重后的记录列表
    """
    seen: Set[tuple] = set()
    unique_records = []

    for record in records:
        key = (record['train_id'], record['sequence'])
        if key not in seen:
            seen.add(key)
            unique_records.append(record)

    logger.info(f"去重完成: {len(records)} -> {len(unique_records)} 条")
    return unique_records


# =============================================================================
# 数据导入
# =============================================================================

def import_to_database(records: List[Dict[str, Any]]) -> int:
    """
    将车次站点数据批量导入到 t_train_station 表。

    使用 INSERT IGNORE 忽略已存在的记录。

    Args:
        records: 站点记录列表

    Returns:
        成功导入的记录数
    """
    if not records:
        logger.warning("没有需要导入的数据")
        return 0

    logger.info(f"开始导入 {len(records)} 条车次站点数据...")

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    sql = """
        INSERT IGNORE INTO t_train_station (
            train_id, train_number, station_id, station_name, arrive_day_diff,
            sequence, arrival_time, departure_time, stopover_time, run_date, del_flag
        ) VALUES (
            %(train_id)s, %(train_number)s, %(station_id)s, %(station_name)s,%(arrive_day_diff)s,
            %(sequence)s, %(arrival_time)s, %(departure_time)s, %(stopover_time)s, %(run_date)s, 0
        )
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
    校验导入结果。

    Returns:
        统计信息字典
    """
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        # 统计总数
        cursor.execute("SELECT COUNT(*) as total FROM t_train_station WHERE del_flag = 0")
        total = cursor.fetchone()['total']

        # 统计车次数
        cursor.execute("""
            SELECT COUNT(DISTINCT train_number) as count
            FROM t_train_station
            WHERE del_flag = 0
        """)
        train_count = cursor.fetchone()['count']

        # 统计车站数
        cursor.execute("""
            SELECT COUNT(DISTINCT station_name) as count
            FROM t_train_station
            WHERE del_flag = 0
        """)
        station_count = cursor.fetchone()['count']

        # 平均每车次站点数
        avg_stations = total / train_count if train_count > 0 else 0

        stats = {
            'total': total,
            'train_count': train_count,
            'station_count': station_count,
            'avg_stations_per_train': round(avg_stations, 2)
        }

        logger.info(f"数据校验完成:")
        logger.info(f"  - 总站点记录: {total}")
        logger.info(f"  - 覆盖车次: {train_count}")
        logger.info(f"  - 覆盖车站: {station_count}")
        logger.info(f"  - 平均每车次站点数: {avg_stations:.2f}")

        return stats

    finally:
        cursor.close()
        conn.close()


# =============================================================================
# 主程序
# =============================================================================

def main():
    """主函数：加载映射 -> 处理数据 -> 去重 -> 导入 -> 校验"""
    logger.info("=" * 60)
    logger.info("车次车站关系数据导入脚本启动")
    logger.info("=" * 60)

    start_time = datetime.now()

    try:
        # Step 1: 加载映射
        train_mapping = load_train_mapping()
        station_mapping = load_station_mapping()

        # Step 2: 处理数据
        records = process_station_data(train_mapping, station_mapping)

        # Step 3: 去重
        records = deduplicate_records(records)

        # Step 4: 导入数据库
        imported = import_to_database(records)

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
