# import_train_data.py
import os
import json
import csv
import re
import pymysql
from datetime import datetime
from station_data_handle import parse_station_data
# === 配置 ===
DATA_ROOT = "data"  # 你的 data/ 目录路径
DEFAULT_RUN_DATE = "2025-01-01"  # 虚拟运行日期

DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'my12306',
    'charset': 'utf8mb4'
}
with open('station_name.txt', 'r', encoding='utf-8') as f:
    STATION_JS_CONTENT = f.read()
s=parse_station_data(STATION_JS_CONTENT)
stations_index={}
for idx,station in enumerate(s):
    stations_index[station['name']]=idx
train_mapping=None
station_mapping=None
def load_train_mapping():
    global train_mapping
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor(pymysql.cursors.DictCursor)
    try:
        cursor.execute("SELECT id, train_number FROM t_train WHERE del_flag = 0")
        train_mapping = {row['train_number']: row['id'] for row in cursor.fetchall()}
        print(f"✅ 成功加载 {len(train_mapping)} 条列车映射")
    finally:
        cursor.close()
        conn.close()
def load_station_mapping():
    global station_mapping
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor(pymysql.cursors.DictCursor)
    try:
        cursor.execute("SELECT id, name FROM t_station WHERE del_flag = 0")
        station_mapping = {row['name']: row['id'] for row in cursor.fetchall()}
        print(f"✅ 成功加载 {len(station_mapping)} 条站台映射")
    finally:
        cursor.close()
        conn.close()

def parse_time(time_str):
    """解析 '08:30' -> datetime (结合 DEFAULT_RUN_DATE)"""
    if not time_str or time_str == "--":
        return None
    try:
        dt_str = f"{DEFAULT_RUN_DATE} {time_str}"
        return datetime.strptime(dt_str, "%Y-%m-%d %H:%M")
    except:
        return None

def calculate_stopover(arrival, departure):
    """计算停留分钟数"""
    if arrival and departure and departure > arrival:
        return int((departure - arrival).total_seconds() // 60)
    return None

def process_csv_and_json(csv_path, train_type):
    """
    csv_path: 如 data/index/G.csv
    train_type_dir: 如 data/G/
    """
    records = []
    with open(csv_path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            filename = os.path.basename(row['path'])
            json_path = os.path.join(DATA_ROOT, train_type, filename)
            if not os.path.exists(json_path):
                print(f"⚠️ 文件不存在: {json_path}")
                continue

            try:
                with open(json_path, 'r', encoding='utf-8') as jf:
                    data = json.load(jf)
            except Exception as e:
                print(f"❌ JSON 解析失败: {json_path}, {e}")
                continue

            stations = data.get("data", {}).get("data", [])
            if not stations:
                continue

            # 提取统一的 train_number（如 G48）
            train_number = stations[0].get("station_train_code", "").strip()
            if not train_number:
                continue

            for st in stations:
                seq = int(st["station_no"])
                name = st["station_name"]
                name=re.sub(r'\s+', '', name)

                arrive_time = parse_time(st.get("arrive_time"))
                depart_time = parse_time(st.get("start_time"))

                # 首站：arrival_time 应为 null（但 JSON 里等于 depart_time）
                if seq == 1:
                    arrive_time = None
                # 末站：depart_time 应为 null
                if seq == len(stations):
                    depart_time = None

                stopover = calculate_stopover(arrive_time, depart_time)

                records.append({
                    "train_id": train_mapping[train_number],
                    "train_number": train_number,
                    "station_id": station_mapping[name] if name in station_mapping else 0, 
                    "station_name": name,
                    "sequence": seq,
                    "arrival_time": arrive_time,
                    "departure_time": depart_time,
                    "stopover_time": stopover,
                    "run_date": DEFAULT_RUN_DATE
                })

    return records

def save_to_db(records):
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()
    try:
        sql = """
        INSERT INTO t_train_station (
            train_id,train_number, station_id,station_name, sequence,
            arrival_time, departure_time, stopover_time, run_date, del_flag
        ) VALUES (%s, %s, %s, %s, %s, %s, %s,%s,%s,0)
        """
        data = [
            (
                r['train_id'],r["train_number"],r["station_id"], r["station_name"], r["sequence"],
                r["arrival_time"], r["departure_time"], r["stopover_time"], r["run_date"]
            )
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        print(f"✅ 成功插入 {len(records)} 条记录")
    except Exception as e:
        print(f"❌ 数据库错误: {e}")
        conn.rollback()
    finally:
        cursor.close()
        conn.close()

def main():
    all_records = []

    index_dir = os.path.join(DATA_ROOT, "index")

    load_train_mapping()
    load_station_mapping()
    for csv_file in os.listdir(index_dir):
        if not csv_file.endswith(".csv"):
            continue

        train_type = csv_file[:-4]  # "G.csv" → "G"
        csv_path = os.path.join(index_dir, csv_file)

        print(f"🔄 处理 {csv_file} (类型: {train_type}) ...")
        records = process_csv_and_json(csv_path, train_type)
        all_records.extend(records)

    print(f"📥 总共准备导入 {len(all_records)} 条站点记录")
    if all_records:
        save_to_db(all_records)
    else:
        print("🚫 无有效数据")

if __name__ == '__main__':
    main()


# import json
# import re
# from datetime import datetime
# from station_data_handle import parse_station_data

# def parse_station_train_code(code):
#     #D1(北京-沈阳南) 返回train_num from to
#     result = re.findall(r"([^\(]+)\(([^-]+)-([^\)]+)\)", code)
#     if len(result)==0:
        
#         return None
#     return result[0][0],result[0][1],result[0][2]
# def get_train_no():
#     #去重 (日期,车次):(起始车站,到达车站)
#     trains={}
#     with open('train_list.txt', 'r', encoding='utf-8') as f:
#        data = json.load(f)
#     for run_date_str, trains_by_type in data.items():
#         try:
#             run_date = datetime.strptime(run_date_str, '%Y-%m-%d').date()
#         except ValueError:
#             print(f"跳过无效日期: {run_date_str}")
#             continue
#         print(run_date)
#         for train_list in trains_by_type.values():
#             for train in train_list:
#                 code = train.get('station_train_code')
#                 if not code:
#                     continue
#                 train_number,from_station,to_station=parse_station_train_code(code)
#                 trains[(train['train_no'],run_date_str)]=(from_station,to_station)
                


#     return trains



# def main():
#     trains=get_train_no()
#     with open('station_name.txt', 'r', encoding='utf-8') as f:
#        STATION_JS_CONTENT = f.read()
#     stations=parse_station_data(STATION_JS_CONTENT)
#     stations_index={}
#     for idx,station in enumerate(stations):
#         stations_index[station['name']]=idx
#     final_trains=[]
#     for (train_number,run_date),(from_s,to_s) in trains.items():
#         if  from_s not in stations_index or to_s not in stations_index:
#             continue
#         if run_date<='2025-12-10':
#             continue
#         final_trains.append({
#             'train_no':train_number,
#             'from_station_telecode': stations[stations_index[from_s]]['tele_code'],
#             'to_station_telecode': stations[stations_index[to_s]]['tele_code'],
#             'depart_date' : run_date
#         })
#     print(final_trains[:5])
#     print(len(final_trains))

# if __name__ == '__main__':
#     main()