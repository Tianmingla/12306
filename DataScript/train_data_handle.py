# import_train_main.py
import os
import json
import pymysql

DATA_ROOT = "data"
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'my12306',
    'charset': 'utf8mb4'
}
def get_train_type_and_brand(train_number):
    """根据车次号返回 (train_type, train_brand)"""
    if not train_number:
        return (2, None)
    
    prefix = train_number[0].upper()
    brand = prefix
    
    if prefix in ('G', 'C'):
        train_type = 0  # 高铁
    elif prefix == 'D':
        train_type = 1  # 动车
    else:
        train_type = 2  # 普速（Z/T/K/Y/S/L 等）
    
    return train_type, brand

def collect_unique_trains():
    """遍历所有 JSON，收集唯一 train_number"""
    train_set = set()

    # 遍历 data/G/, data/D/, data/Z/ 等目录
    for train_type_dir in os.listdir(DATA_ROOT):
        dir_path = os.path.join(DATA_ROOT, train_type_dir)
        if not os.path.isdir(dir_path) or train_type_dir == "index":
            continue

        for filename in os.listdir(dir_path):
            if not filename.endswith(".json"):
                continue

            json_path = os.path.join(dir_path, filename)
            try:
                with open(json_path, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                stations = data.get("data", {}).get("data", [])
                if stations:
                    train_num = stations[0].get("station_train_code", "").strip()
                    
                    if train_num:
                        train_set.add(train_num)
            except Exception as e:
                print(f"⚠️ 解析失败: {json_path}, {e}")

    return sorted(train_set)

def insert_into_t_train(train_numbers):
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()
    try:
        sql = """
        INSERT IGNORE INTO t_train (
            train_number, train_type, train_brand, sale_status, del_flag
        ) VALUES (%s, %s, %s, 0, 0)
        """
        data = []
        for tn in train_numbers:
            train_type, brand = get_train_type_and_brand(tn)
            data.append((tn, train_type, brand))

        cursor.executemany(sql, data)
        conn.commit()
        print(f"✅ 成功插入 {cursor.rowcount} 条列车记录（忽略重复）")
    except Exception as e:
        print(f"❌ 数据库错误: {e}")
        conn.rollback()
    finally:
        cursor.close()
        conn.close()

def main():
    print("🔍 扫描所有 JSON 文件，提取唯一车次...")
    unique_trains = collect_unique_trains()
    print(f"📊 共发现 {len(unique_trains)} 个唯一车次")
    print("🚀 开始插入 t_train 表...")
    insert_into_t_train(unique_trains)

if __name__ == '__main__':
    main()