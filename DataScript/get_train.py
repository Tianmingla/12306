import os
import json
import csv
import pymysql
from datetime import datetime

DATA_ROOT = "data"
DEFAULT_RUN_DATE = "2025-01-01"


DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'my12306',
    'charset': 'utf8mb4'
}

# === 步骤1：从数据库加载 train_number -> id 映射 ===
def load_train_mapping():
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor(pymysql.cursors.DictCursor)
    try:
        cursor.execute("SELECT id, train_number FROM t_train WHERE del_flag = 0")
        mapping = {row['train_number']: row['id'] for row in cursor.fetchall()}
        print(f"✅ 成功加载 {len(mapping)} 条列车映射")
        return mapping
    finally:
        cursor.close()
        conn.close()

# === 工具函数 ===
def parse_time(time_str):
    if not time_str or time_str == "--":
        return None
    try:
        return datetime.strptime(f"{DEFAULT_RUN_DATE} {time_str}", "%Y-%m-%d %H:%M")
    except:
        return None

def calculate_stopover(arrival, departure):
    if arrival and departure and departure > arrival:
        return int((departure - arrival).total_seconds() // 60)
    return None

# === 步骤2：处理所有站点数据并填充 train_id ===
def process_all_stations(train_mapping):
    all_records = []
    index_dir = os.path.join(DATA_ROOT, "index")

    for csv_file in os.listdir(index_dir):
        if not csv_file.endswith(".csv"):
            continue

        train_type = csv_file[:-4]  # e.g., "G"
        csv_path = os.path.join(index_dir, csv_file)

        with open(csv_path, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                filename = os.path.basename(row['path'])
                json_path = os.path.join(DATA_ROOT, train_type, filename)

                if not os.path.exists(json_path):
                    continue

                try:
                    with open(json_path, 'r', encoding='utf-8') as jf:
                        data = json.load(jf)
                except Exception as e:
                    continue

                stations = data.get("data", {}).get("data", [])
                if not stations:
                    continue

                train_number = stations[0].get("station_train_code", "").strip()
                if not train_number:
                    continue

                # 🔑 关键：通过 train_number 获取 train_id
                train_id = train_mapping.get(train_number)
                if train_id is None:
                    print(f"⚠️ 车次未在 t_train 中找到: {train_number}")
                    continue

                for st in stations:
                    seq = int(st["station_no"])
                    name = st["station_name"].strip()

                    arrive_time = parse_time(st.get("arrive_time"))
                    depart_time = parse_time(st.get("start_time"))

                    if seq == 1:
                        arrive_time = None
                    if seq == len(stations):
                        depart_time = None

                    stopover = calculate_stopover(arrive_time, depart_time)

                    all_records.append({
                        "train_id": train_id,
                        "train_number": train_number,
                        "station_id": None,
                        "station_name": name,
                        "sequence": seq,
                        "arrival_time": arrive_time,
                        "departure_time": depart_time,
                        "stopover_time": stopover,
                        "run_date": DEFAULT_RUN_DATE
                    })

    return all_records

# === 步骤3：插入 t_train_station ===
def save_stations_to_db(records):
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()
    try:
        sql = """
        INSERT INTO t_train_station (
            train_id, train_number, station_id, station_name, sequence,
            arrival_time, departure_time, stopover_time, run_date, del_flag
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (
                r["train_id"],
                r["train_number"],
                r["station_id"],
                r["station_name"],
                r["sequence"],
                r["arrival_time"],
                r["departure_time"],
                r["stopover_time"],
                r["run_date"],
                0
            )
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        print(f"✅ 成功插入 {len(records)} 条站点记录")
    except Exception as e:
        print(f"❌ 插入失败: {e}")
        conn.rollback()
    finally:
        cursor.close()
        conn.close()

# === 主流程 ===
def main():
    print("🔄 步骤1: 从数据库加载 t_train 映射...")
    train_mapping = load_train_mapping()
    
    if not train_mapping:
        print("❌ t_train 表为空，请先插入列车主数据！")
        return

    print("🔄 步骤2: 处理所有站点数据...")
    records = process_all_stations(train_mapping)

    if not records:
        print("🚫 无有效站点数据")
        return

    print("🔄 步骤3: 插入 t_train_station 表...")
    save_stations_to_db(records)

if __name__ == '__main__':
    main()