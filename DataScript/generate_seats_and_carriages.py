import pymysql
from datetime import datetime
from pymysql.cursors import DictCursor
# ========== 配置 ==========
DB_CONFIG = {
    'host': 'localhost',
    'user': 'root',
    'password': '123456',
    'database': 'my12306',
    'charset': 'utf8mb4'
}

# ========== 列车配置规则 ==========
# 格式: train_brand -> [ (carriage_type, seat_type, seat_count_per_carriage), ... ]
TRAIN_LAYOUTS = {
    'G': [  # 高铁
        (2, 3, 10),   # 商务车厢 x1
        (0, 2, 20),   # 一等座 x2
        (0, 2, 20),
        (0, 1, 80),   # 二等座 x5
        (0, 1, 80),
        (0, 1, 80),
        (0, 1, 80),
        (0, 1, 80),
    ],
    'D': [  # 动车
        (0, 2, 20),   # 一等座 x1
        (0, 1, 90),   # 二等座 x6
        (0, 1, 90),
        (0, 1, 90),
        (0, 1, 90),
        (0, 1, 90),
        (0, 1, 90),
    ],
    'C': [  # 城际
        (0, 1, 100),  # 全二等座 x4
        (0, 1, 100),
        (0, 1, 100),
        (0, 1, 100),
    ],
    'Z': [  # 直达特快（按硬座模拟）
        (0, 0, 110),  # 硬座 x10
        (0, 0, 110),
        (0, 0, 110),
        (0, 0, 110),
        (0, 0, 110),
        (0, 0, 110),
        (0, 0, 110),
        (0, 0, 110),
        (0, 0, 110),
        (0, 0, 110),
    ],
    'T': [  # 特快
        (0, 0, 110),  # 硬座 x8
        (0, 0, 110),
        (0, 0, 110),
        (0, 0, 110),
        (0, 0, 110),
        (0, 0, 110),
        (0, 0, 110),
        (0, 0, 110),
    ],
    'K': [  # 快速
        (0, 0, 110),  # 硬座 x6
        (0, 0, 110),
        (0, 0, 110),
        (0, 0, 110),
        (0, 0, 110),
        (0, 0, 110),
    ],
}

# 默认布局（未知品牌）
DEFAULT_LAYOUT = [
    (0, 0, 100),  # 1节硬座车厢
]

# 座位号生成规则（简单数字递增）
def generate_seat_numbers(seat_type, total_seats):
    """
    根据座位类型生成符合铁路规范的座位号列表
    :param seat_type: 0=硬座, 1=二等座, 2=一等座, 3=商务座
    :param total_seats: 该车厢总座位数
    :return: list of seat numbers (e.g., ['01A', '01B', ...])
    """
    seat_numbers = []

    if seat_type == 0:  # 硬座：纯数字，001 ~ total_seats
        for i in range(1, total_seats + 1):
            seat_numbers.append(f"{i:03d}")
        return seat_numbers

    # 动车/高铁类座位（带字母）
    layout_map = {
        1: ['A', 'B', 'C', 'D', 'F'],  # 二等座，5座
        2: ['A', 'C', 'D', 'F'],       # 一等座，4座
        3: ['A', 'C', 'F'],            # 商务座，3座
    }

    if seat_type not in layout_map:
        # 未知类型 fallback 到硬座
        for i in range(1, total_seats + 1):
            seat_numbers.append(f"{i:03d}")
        return seat_numbers

    letters = layout_map[seat_type]
    seats_per_row = len(letters)

    row = 1
    generated = 0
    while generated < total_seats:
        for letter in letters:
            if generated >= total_seats:
                break
            seat_numbers.append(f"{row:02d}{letter}")
            generated += 1
        row += 1

    return seat_numbers

# ========== 主函数 ==========
def generate_seats_and_carriages():
    conn = pymysql.connect(cursorclass=DictCursor,**DB_CONFIG)
    try:
        with conn.cursor() as cursor:
            # 1. 获取所有列车
            cursor.execute("SELECT id, train_number, train_brand FROM t_train WHERE del_flag = 0")
            trains = cursor.fetchall()

            carriage_records = []
            seat_records = []

            for train in trains:
                train_id = train['id']
                train_number = train['train_number']
                brand = train['train_brand'] or 'OTHER'

                layout = TRAIN_LAYOUTS.get(brand, DEFAULT_LAYOUT)

                # 生成车厢和座位
                for idx, (carriage_type, seat_type, seat_count) in enumerate(layout):
                    carriage_number = str(idx + 1).zfill(2)  # 01, 02, ...

                    # 插入 t_carriage
                    carriage_records.append((
                        train_id,
                        carriage_number,
                        carriage_type,
                        seat_count,
                        datetime.now(),
                        datetime.now(),
                        0
                    ))

                    # 生成座位
                    seat_numbers = generate_seat_numbers(seat_type,seat_count)
                    for seat_num in seat_numbers:
                        seat_records.append((
                            train_id,
                            carriage_number,
                            seat_num,
                            seat_type,
                            datetime.now(),
                            datetime.now(),
                            0
                        ))

            # 2. 批量插入 t_carriage
            if carriage_records:
                cursor.executemany("""
                    INSERT INTO t_carriage (
                        train_id, carriage_number, carriage_type, seat_count,
                        create_time, update_time, del_flag
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s)
                """, carriage_records)
                print(f"Inserted {len(carriage_records)} carriage records.")

            # 3. 批量插入 t_seat
            if seat_records:
                cursor.executemany("""
                    INSERT INTO t_seat (
                        train_id, carriage_number, seat_number, seat_type,
                        create_time, update_time, del_flag
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s)
                """, seat_records)
                print(f"Inserted {len(seat_records)} seat records.")

            conn.commit()
            print("✅ 车厢和座位数据生成完成！")

    except Exception as e:
        print(f"❌ Error: {e}")
        conn.rollback()
    finally:
        conn.close()

# ========== 运行入口 ==========
if __name__ == '__main__':
    generate_seats_and_carriages()