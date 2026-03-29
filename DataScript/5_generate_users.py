#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
用户数据生成脚本

功能：
    生成 100 万条测试用户数据到 t_user 表。
    模拟真实用户分布：手机号格式规范、状态分布合理。

用户特征：
    - 手机号：11 位数字，符合中国手机号规则（13/15/18/19 开头）
    - 状态：正常(0) 和 禁用(1) 分布，约 99% 正常
    - 邮箱：部分用户有邮箱，部分为空

性能优化：
    - 分批插入（每批 10000 条）
    - 使用 executemany 批量执行
    - 进度显示

用法：
    python 5_generate_users.py [--count 1000000]

作者：Cluade
日期：2026-03-29
"""

import sys
import random
import logging
import argparse
from datetime import datetime, timedelta
from typing import List, Dict, Any
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

# 默认生成数量
DEFAULT_COUNT = 1_000_000

# 批量插入大小
BATCH_SIZE = 10_000

# 手机号前缀（中国移动/联通/电信主流号段）
PHONE_PREFIXES = [
    '130', '131', '132', '133', '134', '135', '136', '137', '138', '139',
    '150', '151', '152', '153', '155', '156', '157', '158', '159',
    '170', '171', '172', '173', '175', '176', '177', '178',
    '180', '181', '182', '183', '184', '185', '186', '187', '188', '189',
    '191', '199'
]

# 邮箱域名
EMAIL_DOMAINS = ['qq.com', '163.com', '126.com', 'gmail.com', 'outlook.com', 'sina.com']


# =============================================================================
# 数据生成函数
# =============================================================================

def generate_phone_number(used_phones: set) -> str:
    """
    生成唯一的手机号。

    格式：1 + 前缀 + 8 位随机数字
    例如：13812345678

    Args:
        used_phones: 已使用的手机号集合（用于去重）

    Returns:
        唯一的手机号字符串
    """
    while True:
        prefix = random.choice(PHONE_PREFIXES)
        suffix = ''.join([str(random.randint(0, 9)) for _ in range(11 - len(prefix))])
        phone = prefix + suffix

        if phone not in used_phones:
            used_phones.add(phone)
            return phone


def generate_email(phone: str) -> str:
    """
    生成邮箱地址。

    规则：使用手机号作为邮箱前缀，随机选择邮箱域名。
    约 30% 的用户有邮箱，其余为空。

    Args:
        phone: 手机号

    Returns:
        邮箱地址或空字符串
    """
    if random.random() < 0.3:  # 30% 概率有邮箱
        domain = random.choice(EMAIL_DOMAINS)
        return f"{phone}@{domain}"
    return None


def generate_status() -> int:
    """
    生成用户状态。

    分布：
        - 正常(0)：99%
        - 禁用(1)：1%

    Returns:
        状态码
    """
    return 0 if random.random() < 0.99 else 1


def generate_create_time(base_date: datetime) -> datetime:
    """
    生成用户创建时间。

    分布：在过去 3 年内随机分布，但更偏向近期。

    Args:
        base_date: 基准日期（当前时间）

    Returns:
        创建时间
    """
    # 过去 3 年（约 1095 天）
    days_ago = random.randint(1, 1095)
    # 加上随机的小时和分钟
    hours = random.randint(0, 23)
    minutes = random.randint(0, 59)

    return base_date - timedelta(days=days_ago, hours=hours, minutes=minutes)


def generate_user_batch(batch_size: int, used_phones: set, base_date: datetime) -> List[Dict[str, Any]]:
    """
    批量生成用户数据。

    Args:
        batch_size: 批次大小
        used_phones: 已使用的手机号集合
        base_date: 基准日期

    Returns:
        用户数据字典列表
    """
    users = []

    for _ in range(batch_size):
        phone = generate_phone_number(used_phones)
        email = generate_email(phone)
        status = generate_status()
        create_time = generate_create_time(base_date)
        update_time = create_time + timedelta(days=random.randint(0, 30))

        users.append({
            'phone': phone,
            'email': email,
            'status': status,
            'create_time': create_time,
            'update_time': update_time,
            'del_flag': 0
        })

    return users


# =============================================================================
# 数据库操作
# =============================================================================

def get_existing_phone_count() -> int:
    """获取数据库中已存在的用户数量。"""
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        cursor.execute("SELECT COUNT(*) as count FROM t_user WHERE del_flag = 0")
        return cursor.fetchone()['count']
    finally:
        cursor.close()
        conn.close()


def get_existing_phones() -> set:
    """获取数据库中已存在的手机号集合。"""
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        cursor.execute("SELECT phone FROM t_user WHERE del_flag = 0")
        return {row['phone'] for row in cursor.fetchall()}
    finally:
        cursor.close()
        conn.close()


def insert_users_batch(users: List[Dict[str, Any]]) -> int:
    """
    批量插入用户数据。

    使用 INSERT IGNORE 忽略重复数据。

    Args:
        users: 用户数据列表

    Returns:
        成功插入的记录数
    """
    if not users:
        return 0

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    sql = """
        INSERT IGNORE INTO t_user (phone, email, status, create_time, update_time, del_flag)
        VALUES (%(phone)s, %(email)s, %(status)s, %(create_time)s, %(update_time)s, %(del_flag)s)
    """

    try:
        cursor.executemany(sql, users)
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
        # 总数
        cursor.execute("SELECT COUNT(*) as count FROM t_user WHERE del_flag = 0")
        total = cursor.fetchone()['count']

        # 状态分布
        cursor.execute("""
            SELECT status, COUNT(*) as count
            FROM t_user
            WHERE del_flag = 0
            GROUP BY status
        """)
        status_dist = cursor.fetchall()

        # 有邮箱的用户数
        cursor.execute("""
            SELECT COUNT(*) as count
            FROM t_user
            WHERE del_flag = 0 AND email IS NOT NULL AND email != ''
        """)
        with_email = cursor.fetchone()['count']

        stats = {
            'total': total,
            'status_distribution': {row['status']: row['count'] for row in status_dist},
            'with_email': with_email
        }

        logger.info("\n=== 用户数据统计 ===")
        logger.info(f"总用户数：{total:,}")
        logger.info(f"状态分布：{stats['status_distribution']}")
        logger.info(f"有邮箱用户：{with_email:,} ({with_email/total*100:.1f}%)\n")

        return stats

    finally:
        cursor.close()
        conn.close()


# =============================================================================
# 主程序
# =============================================================================

def main():
    parser = argparse.ArgumentParser(description='生成用户测试数据')
    parser.add_argument('--count', type=int, default=DEFAULT_COUNT, help=f'生成数量（默认 {DEFAULT_COUNT:,}）')
    args = parser.parse_args()

    target_count = args.count

    logger.info("=" * 60)
    logger.info("用户数据生成脚本启动")
    logger.info("=" * 60)

    start_time = datetime.now()

    try:
        # Step 1: 获取已存在的数据
        existing_count = get_existing_phone_count()
        used_phones = get_existing_phones()

        logger.info(f"数据库中已有 {existing_count:,} 个用户")
        logger.info(f"目标生成 {target_count:,} 个新用户")

        if existing_count >= target_count:
            logger.info("已达到目标数量，无需生成")
            verify_data()
            return 0

        # Step 2: 分批生成并插入
        remaining = target_count - existing_count
        generated = 0
        base_date = datetime.now()

        while remaining > 0:
            batch_size = min(BATCH_SIZE, remaining)
            users = generate_user_batch(batch_size, used_phones, base_date)

            inserted = insert_users_batch(users)
            generated += inserted
            remaining -= batch_size

            # 进度显示
            progress = generated / (target_count - existing_count) * 100
            logger.info(f"进度：{generated:,}/{target_count - existing_count:,} ({progress:.1f}%)")

        # Step 3: 验证结果
        verify_data()

        elapsed = (datetime.now() - start_time).total_seconds()
        logger.info(f"脚本执行完成，耗时 {elapsed:.2f} 秒，生成 {generated:,} 条记录")

        return 0

    except Exception as e:
        logger.error(f"脚本执行失败：{e}", exc_info=True)
        return 1


if __name__ == '__main__':
    sys.exit(main())
