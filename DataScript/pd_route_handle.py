import pandas as pd
import pymysql
from sqlalchemy import create_engine
import logging

# 配置
DB_URI = "mysql+pymysql://user:password@localhost:3306/my12306?charset=utf8mb4"
TABLE_TRAIN_STATION = "train_station"
TABLE_STATION = "station"
TABLE_ROUTE_PAIR = "t_train_route_pair"

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def update_train_route_pair_with_pandas():
    logger.info("开始用 pandas 更新 t_train_route_pair 表...")

    # 1. 读取数据
    engine = create_engine(DB_URI)
    df_stations = pd.read_sql(f"SELECT * FROM {TABLE_TRAIN_STATION}", engine)
    df_station_info = pd.read_sql(f"SELECT name, region_name FROM {TABLE_STATION}", engine)

    logger.info(f"加载 {len(df_stations)} 条车次站点，{len(df_station_info)} 个车站信息")

    # 2. 构建区域映射
    station_to_region = df_station_info.set_index('name')['region_name'].to_dict()

    # 3. 按 train_number 分组并排序
    df_stations = df_stations.sort_values(['train_number', 'sequence'])

    # 4. 为每个车次生成 (i, j) 区间对（i < j）
    route_pairs = []

    for train_num, group in df_stations.groupby('train_number'):
        group = group.reset_index(drop=True)
        n = len(group)
        if n < 2:
            continue

        # 生成所有 i < j 的组合（向量化，避免双重循环）
        dep_indices = []
        arr_indices = []
        for i in range(n - 1):
            for j in range(i + 1, n):
                dep_indices.append(i)
                arr_indices.append(j)

        if not dep_indices:
            continue

        # 提取出发站和到达站
        dep_df = group.iloc[dep_indices].reset_index(drop=True)
        arr_df = group.iloc[arr_indices].reset_index(drop=True)

        # 合并成 pair
        pairs = pd.DataFrame({
            'train_id': dep_df['train_id'],
            'train_number': train_num,
            'departure_station': dep_df['station_name'],
            'arrival_station': arr_df['station_name'],
            'start_time': dep_df['departure_time'],
            'end_time': arr_df['arrival_time']
        })

        route_pairs.append(pairs)

    # 合并所有车次
    if not route_pairs:
        logger.warning("未生成任何线路对")
        return

    df_pairs = pd.concat(route_pairs, ignore_index=True)
    logger.info(f"原始生成 {len(df_pairs)} 条线路对")

    # 5. 映射区域
    df_pairs['start_region'] = df_pairs['departure_station'].map(station_to_region)
    df_pairs['end_region'] = df_pairs['arrival_station'].map(station_to_region)

    # 6. 过滤无效站点（不在 station 表中的）
    df_pairs = df_pairs.dropna(subset=['start_region', 'end_region'])
    logger.info(f"过滤后剩余 {len(df_pairs)} 条")

    # 7. 去重（关键！避免唯一索引冲突）
    df_pairs = df_pairs.drop_duplicates(
        subset=['train_id', 'departure_station', 'arrival_station'],
        keep='first'
    )
    logger.info(f"去重后剩余 {len(df_pairs)} 条")

    # 8. 清空并批量插入
    with engine.connect() as conn:
        conn.execute(f"TRUNCATE TABLE {TABLE_ROUTE_PAIR}")
        logger.info("已清空表")

    # 使用 to_sql 批量插入（自动分批，高效）
    df_pairs.to_sql(
        name=TABLE_ROUTE_PAIR,
        con=engine,
        if_exists='append',
        index=False,
        method='multi',  # 启用批量 INSERT
        chunksize=10000  # 每批 1 万条
    )

    logger.info(f"成功插入 {len(df_pairs)} 条记录到 {TABLE_ROUTE_PAIR}")

if __name__ == '__main__':
    update_train_route_pair_with_pandas()