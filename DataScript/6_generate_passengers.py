#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
乘客数据生成脚本

功能：
    为已生成的用户生成乘客（乘车人）数据到 t_passenger 表。
    每个用户平均拥有 1.5-2.5 个乘车人，总计约 150-250 万条记录。

乘客特征：
    - 真实姓名：使用常见中文姓名库
    - 身份证号：符合校验规则生成（前 17 位随机，第 18 位校验码）
    - 证件类型：1=身份证，2=护照，3=港澳台通行证
    - 乘客类型：1=成人，2=儿童，3=学生
    - 手机号：关联用户或独立

性能优化：
    - 批量插入（每批 10000 条）
    - 进度显示
    - 数据库级去重（唯一索引：user_id + id_card_number）

用法：
    python 6_generate_passengers.py [--ratio 2.0]

作者：Cluade
日期：2026-03-29
"""

import sys
import random
import logging
import argparse
from datetime import datetime
from typing import List, Dict, Any, Set
import pymysql
from pymysql.cursors import DictCursor

# =============================================================================
# 配置区域
# =============================================================================

DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'my12306',
    'charset': 'utf8mb4',
    'cursorclass': DictCursor
}

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger(__name__)

# 批量插入大小
BATCH_SIZE = 10_000

# 乘客类型
PASSENGER_TYPE_ADULT = 1   # 成人
PASSENGER_TYPE_CHILD = 2   # 儿童
PASSENGER_TYPE_STUDENT = 3 # 学生

# 证件类型
ID_CARD_TYPE_IDCARD = 1    # 居民身份证
ID_CARD_TYPE_PASSPORT = 2  # 护照
ID_CARD_TYPE_HKMC = 3      # 港澳居民来往内地通行证
ID_CARD_TYPE_TW = 4        # 台湾居民来往大陆通行证


# =============================================================================
# 中文姓名库
# =============================================================================

COMMON_SURNAMES = [
    '王', '李', '张', '刘', '陈', '杨', '黄', '赵', '周', '吴',
    '徐', '孙', '马', '朱', '胡', '郭', '何', '高', '林', '罗',
    '郑', '梁', '谢', '宋', '唐', '冯', '于', '董', '袁', '潘',
    '蒋', '蔡', '余', '叶', '许', '傅', '沈', '曾', '彭', '吕',
    '苏', '卢', '汪', '田', '任', '姜', '范', '方', '石', '姚'
]

COMMON_NAMES_FIRST = [
    '伟', '芳', '娜', '秀英', '敏', '静', '丽', '强', '磊', '洋',
    '艳', '勇', '军', '杰', '娟', '涛', '明', '超', '秀兰', '霞',
    '平', '刚', '桂英', '华', '辉', '鹏', '红', '波', '燕', '梅',
    '欣', '丹', '萍', '蓉', '玲', '珍', '军', '婷', '琴', '琳',
    '浩', '宇', '涵', '悦', '佳', '艺', '雯', '慧', '琪', '颖'
]

COMMON_NAMES_SECOND = [
    '小', '大', '阿', '老', '爱', '金', '银', '玉', '翠', '凤',
    '花', '雪', '梅', '兰', '竹', '菊', '春', '秋', '冬', '夏',
    '月', '星', '天', '地', '山', '水', '云', '风', '雷', '电',
    '雨', '露', '霜', '冰', '阳', '光', '明', '亮', '安', '宁',
    '和', '平', '顺', '福', '禄', '寿', '喜', '财', '贵', '健'
]


def generate_chinese_name() -> str:
    """
    生成一个常见的中文姓名。

    格式：姓（单字）+ 名（1-2 字）
    例如：王伟、李秀英、张志强

    Returns:
        中文姓名字符串
    """
    surname = random.choice(COMMON_SURNAMES)

    if random.random() < 0.7:  # 70% 概率为双字名
        given_name = random.choice(COMMON_NAMES_FIRST) + random.choice(COMMON_NAMES_SECOND)
    else:  # 30% 概率为单字名
        given_name = random.choice(COMMON_NAMES_FIRST)

    return surname + given_name


def generate_id_card_number() -> str:
    """
    生成一个有效的中国身份证号。

    规则：
        - 前 6 位：地区代码（模拟）
        - 第 7-14 位：出生日期（YYYYMMDD）
        - 第 15-17 位：顺序码（同一地区同日出生人的序号）
        - 第 18 位：校验码（根据前面 17 位计算）

    Returns:
        18 位身份证号字符串
    """
    # 地区代码（常用省份）
    region_codes = ['110000', '120000', '310000', '320000', '330000',
                    '440000', '510000', '520000', '370000', '420000']
    region = random.choice(region_codes)

    # 出生日期（过去 18-70 年间）
    birth_year = random.randint(1956, 2008)
    birth_month = random.randint(1, 12)
    birth_day = random.randint(1, 28)  # 避免月末问题

    birth_date = f"{birth_year:04d}{birth_month:02d}{birth_day:02d}"

    # 顺序码（同一地区同日出生人的序号）
    sequence = random.randint(1, 999)
    sequence_str = f"{sequence:03d}"

    # 校验码计算
    weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
    check_codes = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2']

    first_17 = region + birth_date + sequence_str
    sum_val = sum(int(first_17[i]) * weights[i] for i in range(17))
    checksum_index = sum_val % 11
    check_code = check_codes[checksum_index]

    return first_17 + check_code


def generate_phone_number() -> str:
    """生成一个随机手机号。"""
    prefixes = ['138', '139', '150', '151', '186', '135', '136', '137', '134', '188']
    suffix = ''.join([str(random.randint(0, 9)) for _ in range(8)])
    return random.choice(prefixes) + suffix


# =============================================================================
# 数据生成函数
# =============================================================================

def get_users_for_passengers(min_count: int = 50000) -> List[Dict]:
    """
    获取用于生成乘客数据的用户列表。

    筛选条件：
        - del_flag = 0（未删除）
        - 优先选择已有部分乘客的用户（实现分布合理）

    Args:
        min_count: 最小用户数（默认 5 万）

    Returns:
        用户列表
    """
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        cursor.execute("""
            SELECT id, phone, email, status
            FROM t_user
            WHERE del_flag = 0
            ORDER BY RAND()
            LIMIT %s
        """, (min_count,))

        return cursor.fetchall()
    finally:
        cursor.close()
        conn.close()


def determine_passenger_count(user_id: int) -> int:
    """
    确定单个用户的乘客数量。

    分布规律：
        - 60% 用户有 1 个乘客（自己）
        - 30% 用户有 2 个乘客（带家人）
        - 8% 用户有 3 个乘客
        - 2% 用户有 4 个及以上

    Args:
        user_id: 用户 ID（用于确保确定性）

    Returns:
        乘客数量
    """
    seed = hash(user_id) % 100

    if seed < 60:
        return 1
    elif seed < 90:
        return 2
    elif seed < 98:
        return 3
    else:
        return random.randint(4, 6)


def determine_passenger_type(real_name: str, id_card: str) -> int:
    """
    判断乘客类型。

    规则：
        - 18 岁以下：儿童
        - 18-25 岁且包含特定名字特征：学生
        - 其他：成人

    Args:
        real_name: 姓名
        id_card: 身份证号

    Returns:
        乘客类型
    """
    # 从身份证号提取出生年份
    birth_year = int(id_card[6:10])
    current_year = datetime.now().year
    age = current_year - birth_year

    if age < 18:
        return PASSENGER_TYPE_CHILD
    elif age >= 18 and age <= 25:
        return PASSENGER_TYPE_STUDENT
    else:
        return PASSENGER_TYPE_ADULT


def generate_passengers_for_user(user: Dict) -> List[Dict[str, Any]]:
    """
    为单个用户生成其所有乘客记录。

    Args:
        user: 用户数据字典

    Returns:
        乘客记录列表
    """
    user_id = user['id']
    passenger_count = determine_passenger_count(user_id)

    passengers = []

    for i in range(passenger_count):
        real_name = generate_chinese_name()

        # 证件类型：95% 身份证，其余为其他证件
        if random.random() < 0.95:
            id_card_type = ID_CARD_TYPE_IDCARD
            id_card_number = generate_id_card_number()
        else:
            id_card_type = random.choice([ID_CARD_TYPE_PASSPORT, ID_CARD_TYPE_HKMC, ID_CARD_TYPE_TW])
            id_card_number = f"P{random.randint(1000000, 9999999)}" if id_card_type == ID_CARD_TYPE_PASSPORT else "HK12345678"

        passenger_type = determine_passenger_type(real_name, id_card_number)

        # 手机号：50% 与用户相同，50% 新生成
        if random.random() < 0.5 and user.get('phone'):
            phone = user['phone']
        else:
            phone = generate_phone_number()

        now = datetime.now()

        passengers.append({
            'user_id': user_id,
            'real_name': real_name,
            'id_card_type': id_card_type,
            'id_card_number': id_card_number,
            'passenger_type': passenger_type,
            'phone': phone,
            'create_time': now,
            'update_time': now,
            'del_flag': 0
        })

    return passengers


# =============================================================================
# 数据库操作
# =============================================================================

def insert_passengers_batch(passengers: List[Dict[str, Any]]) -> int:
    """
    批量插入乘客数据。

    使用 IGNORE 忽略重复数据（基于唯一索引：user_id + id_card_number）。

    Args:
        passengers: 乘客数据列表

    Returns:
        成功插入的记录数
    """
    if not passengers:
        return 0

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    sql = """
        INSERT IGNORE INTO t_passenger (
            user_id, real_name, id_card_type, id_card_number, passenger_type,
            phone, create_time, update_time, del_flag
        ) VALUES (
            %(user_id)s, %(real_name)s, %(id_card_type)s, %(id_card_number)s,
            %(passenger_type)s, %(phone)s, %(create_time)s, %(update_time)s, %(del_flag)s
        )
    """

    try:
        cursor.executemany(sql, passengers)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        conn.rollback()
        logger.error(f"插入失败：{e}")
        raise
    finally:
        cursor.close()
        conn.close()


def verify_data() -> Dict[str, Any]:
    """验证生成的数据。"""
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        # 总乘客数
        cursor.execute("SELECT COUNT(*) as count FROM t_passenger WHERE del_flag = 0")
        total = cursor.fetchone()['count']

        # 按乘客类型统计
        cursor.execute("""
            SELECT passenger_type, COUNT(*) as count
            FROM t_passenger
            WHERE del_flag = 0
            GROUP BY passenger_type
        """)
        by_type = cursor.fetchall()

        type_names = {1: '成人', 2: '儿童', 3: '学生'}
        type_dist = {type_names.get(r['passenger_type'], '未知'): r['count'] for r in by_type}

        # 按证件类型统计
        cursor.execute("""
            SELECT id_card_type, COUNT(*) as count
            FROM t_passenger
            WHERE del_flag = 0
            GROUP BY id_card_type
        """)
        by_id_type = cursor.fetchall()

        id_type_names = {1: '身份证', 2: '护照', 3: '港澳证', 4: '台胞证'}
        id_type_dist = {id_type_names.get(r['id_card_type'], '未知'): r['count'] for r in by_id_type}

        stats = {
            'total': total,
            'by_type': type_dist,
            'by_id_type': id_type_dist
        }

        logger.info("\n=== 乘客数据统计 ===")
        logger.info(f"总乘客数：{total:,}")
        logger.info(f"乘客类型分布：")
        for name, count in type_dist.items():
            logger.info(f"  - {name}: {count:,} ({count/total*100:.1f}%)")
        logger.info(f"证件类型分布：")
        for name, count in id_type_dist.items():
            logger.info(f"  - {name}: {count:,} ({count/total*100:.1f}%)")
        logger.info("")

        return stats

    finally:
        cursor.close()
        conn.close()


# =============================================================================
# 主程序
# =============================================================================

def main():
    parser = argparse.ArgumentParser(description='生成乘客测试数据')
    parser.add_argument('--users', type=int, default=50000, help='参与生成乘客的用户数（默认 50000）')
    args = parser.parse_args()

    user_count = args.users

    logger.info("=" * 60)
    logger.info("乘客数据生成脚本启动")
    logger.info("=" * 60)

    start_time = datetime.now()

    try:
        # Step 1: 获取用户列表
        logger.info(f"从数据库中获取 {user_count:,} 个用户...")
        users = get_users_for_passengers(user_count)

        if not users:
            logger.warning("没有找到符合条件的用户")
            return 1

        logger.info(f"找到 {len(users):,} 个用户，开始生成乘客数据...")

        # Step 2: 批量生成并插入
        all_passengers = []
        processed_users = 0
        total_generated = 0

        for user in users:
            passengers = generate_passengers_for_user(user)
            all_passengers.extend(passengers)
            processed_users += 1

            # 每处理 1000 个用户输出一次进度
            if processed_users % 1000 == 0:
                progress = processed_users / len(users) * 100
                logger.info(f"用户进度：{processed_users:,}/{len(users):,} ({progress:.1f}%)")

            # 当累积数据达到批次大小时进行插入
            if len(all_passengers) >= BATCH_SIZE:
                inserted = insert_passengers_batch(all_passengers)
                total_generated += inserted
                logger.info(f"已插入 {total_generated:,} 条乘客记录")
                all_passengers = []

        # 处理剩余数据
        if all_passengers:
            inserted = insert_passengers_batch(all_passengers)
            total_generated += inserted

        # Step 3: 验证结果
        verify_data()

        elapsed = (datetime.now() - start_time).total_seconds()
        logger.info(f"脚本执行完成，耗时 {elapsed:.2f} 秒，共生成 {total_generated:,} 条乘客记录")

        return 0

    except Exception as e:
        logger.error(f"脚本执行失败：{e}", exc_info=True)
        return 1


if __name__ == '__main__':
    sys.exit(main())
