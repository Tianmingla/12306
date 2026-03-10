import re
import pymysql
import sys

# ================== 配置区 ==================
MYSQL_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'my12306',
    'charset': 'utf8mb4'
}


with open('station_name.txt', 'r', encoding='utf-8') as f:
    STATION_JS_CONTENT = f.read()
# ===========================================

def parse_station_data(js_content: str):
    """从 12306 的 station_names.txt 中提取站点数据"""
    # 使用正则提取单引号内的内容
    match = re.search(r"station_names\s*=\s*'([^']*)'", js_content)
    if not match:
        raise ValueError("未在 JS 内容中找到 station_names 数据")
    
    data_str = match.group(1)
    entries = data_str.split('@')
    stations = []

    for entry in entries[1:]:  # 跳过第一个空项
        if not entry.strip():
            continue
        fields = entry.split('|')
        # 12306 格式: @code|name|telegraph|pinyin_full|pinyin_short|sort|region|regionName|...
        if len(fields) < 8:
            print(f"跳过无效行（字段不足）: {entry[:50]}...")
            continue

        code = fields[0]
        name = fields[1]
        pinyin_full = fields[3]  # 全拼
        tele_code=fields[2]
        region = fields[6]       # 地区编号（城市编码）
        region_name = fields[7]  # 地区名称（城市名）

        # 过滤空 code 或 name
        if not code or not name:
            continue

        stations.append({
            'id':int(fields[5]),
            'code': code,
            'name': name,
            'spell': pinyin_full,
            'region': region if region else None,
            'region_name': region_name if region_name else None,
            'tele_code': tele_code
        })

    return stations

def import_to_mysql(stations):
    """批量插入到 MySQL t_station 表"""
    conn = pymysql.connect(**MYSQL_CONFIG)
    cursor = conn.cursor()

    sql = """
        INSERT INTO t_station (code, name, spell, region, region_name)
        VALUES (%(tele_code)s, %(name)s, %(spell)s, %(region)s, %(region_name)s)
        ON DUPLICATE KEY UPDATE
            name = VALUES(name),
            spell = VALUES(spell),
            region = VALUES(region),
            region_name = VALUES(region_name)
    """

    try:
        cursor.executemany(sql, stations)
        conn.commit()
        print(f"成功导入 {len(stations)} 条站点数据")
    except Exception as e:
        conn.rollback()
        print(f"导入失败: {e}")
        raise
    finally:
        cursor.close()
        conn.close()

if __name__ == '__main__':
    try:
        stations = parse_station_data(STATION_JS_CONTENT)
        print(f"解析出 {len(stations)} 条有效站点记录")
       
        import_to_mysql(stations)
    except Exception as e:
        print(f"错误: {e}")
        sys.exit(1)