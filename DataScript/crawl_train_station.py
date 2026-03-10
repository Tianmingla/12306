# crawl_train_detail.py
import os
import time
import json
import re
import logging
import requests
from datetime import datetime

# 导入你已有的解析函数
from station_data_handle import parse_station_data

# ==============================
# 配置区（请按需修改）
# ==============================
REQUEST_DELAY = 5  # 成功请求后等待秒数（建议 ≥5）
TIMEOUT = 10       # 请求超时
MAX_RETRIES = 2    # 失败重试次数

# 数据库配置（请替换为你的实际信息）
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'your_user',
    'password': 'your_password',
    'database': 'your_db',
    'charset': 'utf8mb4'
}

# ==============================
# 工具函数
# ==============================
def setup_logger():
    os.makedirs("logs", exist_ok=True)
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s - %(levelname)s - %(message)s',
        handlers=[
            logging.FileHandler("logs/crawl.log", encoding='utf-8'),
            logging.StreamHandler()
        ]
    )

def load_crawled_set():
    """加载已成功爬取的 (train_no|date) 集合"""
    try:
        with open("logs/crawled.log", "r", encoding='utf-8') as f:
            return set(line.strip() for line in f)
    except FileNotFoundError:
        return set()

def mark_crawled(train_no, run_date):
    """标记为已爬取"""
    with open("logs/crawled.log", "a", encoding='utf-8') as f:
        f.write(f"{train_no}|{run_date}\n")

def parse_12306_response(raw_stations, run_date_str):
    """解析 12306 返回的站点数据"""
    stations = []
    for item in raw_stations:
        seq = int(item["station_no"])
        name = item["station_name"]
        train_code_display = item.get("station_train_code", "")  # 如 G473

        # 出发时间
        dep_time = None
        if item.get("start_time") not in ("----", ""):
            try:
                dep_time = datetime.strptime(f"{run_date_str} {item['start_time']}", "%Y-%m-%d %H:%M")
            except:
                pass

        # 到达时间
        arr_time = None
        if item.get("arrive_time") not in ("----", ""):
            try:
                arr_time = datetime.strptime(f"{run_date_str} {item['arrive_time']}", "%Y-%m-%d %H:%M")
            except:
                pass

        # 停留时间（分钟）
        stop_min = None
        stop_str = item.get("stopover_time", "")
        if "分钟" in stop_str:
            try:
                stop_min = int(stop_str.replace("分钟", ""))
            except:
                pass

        stations.append({
            "train_number": train_code_display,
            "station_name": name,
            "sequence": seq,
            "arrival_time": arr_time,
            "departure_time": dep_time,
            "stopover_time": stop_min,
            "run_date": run_date_str
        })
    return stations

def save_to_database(stations):
    """保存到 t_train_station 表"""
    try:
        import pymysql
        conn = pymysql.connect(**DB_CONFIG, autocommit=False)
        cursor = conn.cursor()
        for st in stations:
            cursor.execute("""
                INSERT INTO t_train_station (
                    train_number, station_name, sequence,
                    arrival_time, departure_time, stopover_time, run_date, del_flag
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, 0)
                ON DUPLICATE KEY UPDATE
                    arrival_time = VALUES(arrival_time),
                    departure_time = VALUES(departure_time),
                    stopover_time = VALUES(stopover_time);
            """, (
                st["train_number"], st["station_name"], st["sequence"],
                st["arrival_time"], st["departure_time"], st["stopover_time"], st["run_date"]
            ))
        conn.commit()
        logging.info(f"✅ 成功保存 {len(stations)} 个站点（车次: {stations[0]['train_number']}）")
    except Exception as e:
        logging.error(f"❌ 数据库存储失败: {e}")
        if 'conn' in locals():
            conn.rollback()
    finally:
        if 'cursor' in locals():
            cursor.close()
        if 'conn' in locals():
            conn.close()

def fetch_train_detail(train_no, from_tele, to_tele, run_date):
    """调用 12306 接口获取车次详情"""
    url = "https://kyfw.12306.cn/otn/czxx/queryByTrainNo"
    params = {
        "train_no": train_no,
        "from_station_telecode": from_tele,
        "to_station_telecode": to_tele,
        "depart_date": run_date
    }
    head={
        'user-agent':
'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0'
    }

    for attempt in range(MAX_RETRIES):
        try:
            resp = requests.get(url, params=params, timeout=TIMEOUT,headers=head)
        
            if resp.status_code == 200:
                data = resp.json()
                if data.get("status") and data["data"]["data"]:
                    return parse_12306_response(data["data"]["data"], run_date)
                else:
                    logging.warning(f"⚠️ 接口返回无数据: {train_no} on {run_date}")
                    return None
            else:
                logging.warning(f"HTTP {resp.status_code} for {train_no}")
        except Exception as e:
            logging.error(f"请求异常 (尝试 {attempt + 1}): {e}")
        time.sleep(2)
    return None

# ==============================
# 主流程
# ==============================
def build_request_list():
    """构建待爬取列表（复用你的逻辑）"""
    # 1. 加载车次数据
    with open('train_list.txt', 'r', encoding='utf-8') as f:
        data = json.load(f)

    # 2. 解析车站电报码
    with open('station_name.txt', 'r', encoding='utf-8') as f:
        station_js = f.read()
    stations = parse_station_data(station_js)
    name_to_tele = {s['name']: s['tele_code'] for s in stations}

    # 3. 构建请求
    requests = []
    for run_date_str, trains_by_type in data.items():
        for train_list in trains_by_type.values():
            for train in train_list:
                code = train.get('station_train_code')
                train_no = train.get('train_no')
                if not code or not train_no:
                    continue

                # 解析始发终到站名
                result = re.findall(r"([^\(]+)\(([^-]+)-([^\)]+)\)", code)
                if not result:
                    continue
                _, from_name, to_name = result[0]

                # 映射电报码
                if from_name not in name_to_tele or to_name not in name_to_tele:
                    continue

                requests.append({
                    'train_no': train_no,
                    'from_tele': name_to_tele[from_name],
                    'to_tele': name_to_tele[to_name],
                    'run_date': run_date_str
                })
    return requests

def main():
    setup_logger()
    crawled = load_crawled_set()
    request_list = build_request_list()

    logging.info(f"共发现 {len(request_list)} 个待爬任务")

    for req in request_list:
        key = f"{req['train_no']}|{req['run_date']}"
        if key in crawled:
            continue

        logging.info(f"⏳ 开始爬取: {req['train_no']} | {req['run_date']}")
        stations = fetch_train_detail(
            req['train_no'],
            req['from_tele'],
            req['to_tele'],
            req['run_date']
        )

        if stations:
            save_to_database(stations)
            mark_crawled(req['train_no'], req['run_date'])
            time.sleep(REQUEST_DELAY)  # ⏸️ 关键：慢速！
        else:
            time.sleep(8)  # 失败多等一会

    logging.info("🏁 所有任务处理完毕")

if __name__ == '__main__':
    # 安全提示：避免在白天高峰运行
    current_hour = datetime.now().hour
    if current_hour not in [2, 3, 4]:
        print("⏰ 建议在凌晨 2-4 点运行此爬虫，以减少对 12306 的影响。")
        print("是否继续？(输入 y 继续，其他退出)")
        if input().strip().lower() != 'y':
            exit(0)
    main()