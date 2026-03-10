import os
import sys
import logging
from datetime import datetime
import pymysql.cursors

# ================== 配置数据库连接 ==================
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'my12306',
    'charset': 'utf8mb4',
    'cursorclass': pymysql.cursors.DictCursor
}

# ================== 日志配置 ==================
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

def get_db_connection():
    return pymysql.connect(**DB_CONFIG)

def update_train_route_pair_table():
    logger.info("开始更新 train_route_pair 表...")

    conn = get_db_connection()
    try:
        with conn.cursor() as cursor:
            # 1. 查询所有 TrainStationDO（车次站点）
            cursor.execute("SELECT * FROM t_train_station")
            station_list = cursor.fetchall()

            for station in station_list:
                if station['sequence']>1 and station['arrival_time'] is None:
                    print("脏数据")

            # 2. 查询所有 StationDO（车站信息）
            cursor.execute("SELECT name, region_name FROM t_station")
            station_dos = cursor.fetchall()

            # 3. 构建 station_name -> region_name 映射（替代 Java 的 index）
            station_region_map = {s['name']: s['region_name'] for s in station_dos}
            logger.info(f"加载 {len(station_region_map)} 个车站区域映射")

            # 4. 按 train_number 分组
            train_map = {}
            for station in station_list:
                train_num = station['train_number']
                if train_num not in train_map:
                    train_map[train_num] = []
                train_map[train_num].append(station)

            # 5. 生成 route pairs
            route_pairs = []
            total_pairs = 0

            for train_number, stations in train_map.items():
                # 按 sequence 排序
                stations.sort(key=lambda x: x['sequence'])
                n = len(stations)
                seen=set()
                for i in range(n - 1):
                    for j in range(i + 1, n):
                        dep_station = stations[i]['station_name']
                        arr_station = stations[j]['station_name']
                        if (train_number,dep_station,arr_station) in seen:
                            continue
                        else:
                            seen.add((train_number,dep_station,arr_station))
                        # 跳过不在 station 表中的站点（脏数据）
                        if dep_station not in station_region_map or arr_station not in station_region_map:
                            continue
                        if stations[j]['arrival_time'] is None:
                            continue
                        pair = {
                            'train_id': stations[i]['train_id'],
                            'train_number': train_number,
                            'departure_station': dep_station,
                            'arrival_station': arr_station,
                            'start_time': stations[i]['departure_time'],      # 假设是 TIME 类型或字符串
                            'end_time': stations[j]['arrival_time'],          # 同上
                            'start_region': station_region_map[dep_station],
                            'end_region': station_region_map[arr_station]
                        }
                        route_pairs.append(pair)
                        total_pairs += 1

            logger.info(f"共生成 {total_pairs} 条线路对")

            # 6. 清空旧数据（可选：根据业务决定是否 truncate）
            cursor.execute("TRUNCATE TABLE t_train_route_pair")
            logger.info("已清空 train_route_pair 表")

            # 7. 批量插入（关键！性能提升点）
            if route_pairs:
                columns = [
                    'train_id', 'train_number', 'departure_station', 'arrival_station',
                    'start_time', 'end_time', 'start_region', 'end_region'
                ]
                sql = f"""
                    INSERT INTO t_train_route_pair ({', '.join(columns)})
                    VALUES ({', '.join(['%s'] * len(columns))})
                """
                values = [
                    (
                        p['train_id'], p['train_number'], p['departure_station'], p['arrival_station'],
                        p['start_time'], p['end_time'], p['start_region'], p['end_region']
                    )
                    for p in route_pairs
                ]

                cursor.executemany(sql, values)
                conn.commit()
                logger.info(f"成功批量插入 {len(values)} 条记录")
            else:
                logger.warning("没有生成任何线路对，跳过插入")

    except Exception as e:
        logger.error(f"更新失败: {e}", exc_info=True)
        conn.rollback()
        raise
    finally:
        conn.close()

    logger.info("train_route_pair 表更新完成！")


# ================== 手动运行入口 ==================
if __name__ == '__main__':
    # 支持命令行参数：manual / schedule
    if len(sys.argv) > 1 and sys.argv[1] == 'manual':
        update_train_route_pair_table()
    else:
        # 默认：立即运行一次（用于测试）
        update_train_route_pair_table()