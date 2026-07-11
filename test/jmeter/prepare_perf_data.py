#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
12306 压测数据准备脚本
功能：
  1. 生成 N 个测试用户（手机号 + 预登录 Token）
  2. 为每个用户添加 1-3 个乘车人
  3. 输出 test_users_N.csv 供 JMeter 使用
  4. 输出 test_passengers_N.csv 供购票压测使用

用法：
  python prepare_perf_data.py --count 2000 --gateway http://localhost:8080
  python prepare_perf_data.py --count 2000 --skip-login   # 仅生成 CSV，不登录
"""

import argparse
import csv
import json
import os
import sys
import time
import random
import logging
import urllib.request
import urllib.error
from datetime import datetime, timedelta

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%H:%M:%S'
)
logger = logging.getLogger('perf-data')

# ============================================================================
# 常量
# ============================================================================
PHONE_PREFIXES = [
    '130', '131', '132', '133', '134', '135', '136', '137', '138', '139',
    '150', '151', '152', '153', '155', '156', '157', '158', '159',
    '170', '171', '172', '173', '175', '176', '177', '178',
    '180', '181', '182', '183', '184', '185', '186', '187', '188', '189',
    '191', '199'
]

# 中文姓名库（小规模，够用）
SURNAMES = ['张', '李', '王', '刘', '陈', '杨', '赵', '黄', '周', '吴',
            '徐', '孙', '胡', '朱', '高', '林', '何', '郭', '马', '罗']
GIVEN_NAMES = ['伟', '芳', '娜', '秀英', '敏', '静', '丽', '强', '磊', '洋',
               '艳', '勇', '军', '杰', '娟', '涛', '明', '超', '秀兰', '霞',
               '平', '刚', '桂英', '文', '云', '华', '建华', '玲', '建国', '英']

# 身份证区域码
REGION_CODES = ['110101', '120101', '310101', '320102', '330102',
                '440103', '510104', '520103', '370102', '420102']

# ID 卡校验位权重
ID_WEIGHTS = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
ID_CHECK_CODES = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2']


def generate_id_card():
    """生成合法的18位身份证号"""
    region = random.choice(REGION_CODES)
    year = random.randint(1960, 2005)
    month = random.randint(1, 12)
    day = random.randint(1, 28)
    seq = random.randint(10, 99)
    body = f"{region}{year}{month:02d}{day:02d}{seq}"
    # 计算校验位
    total = sum(int(body[i]) * ID_WEIGHTS[i] for i in range(17))
    check = ID_CHECK_CODES[total % 11]
    return body + check


def generate_phone(index: int) -> str:
    """根据索引生成手机号，保证唯一且符合 ^1[3-9]\d{9}$"""
    # 使用 138 前缀 + 8位序号，确保唯一，总长 11 位
    return f"138{index:08d}"


def generate_name() -> str:
    """生成随机中文姓名"""
    return random.choice(SURNAMES) + random.choice(GIVEN_NAMES)


# ============================================================================
# API 调用
# ============================================================================
class ApiClient:
    def __init__(self, gateway_url: str):
        self.base = gateway_url.rstrip('/')

    def _post(self, path: str, body: dict) -> dict:
        data = json.dumps(body).encode('utf-8')
        req = urllib.request.Request(
            f"{self.base}{path}",
            data=data,
            headers={'Content-Type': 'application/json'},
            method='POST'
        )
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                return json.loads(resp.read().decode('utf-8'))
        except urllib.error.HTTPError as e:
            body_text = e.read().decode('utf-8', errors='replace')
            return {'code': e.code, 'message': body_text}
        except Exception as e:
            return {'code': -1, 'message': str(e)}

    def _get(self, path: str, token: str) -> dict:
        req = urllib.request.Request(
            f"{self.base}{path}",
            headers={'Authorization': f'Bearer {token}'},
            method='GET'
        )
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                return json.loads(resp.read().decode('utf-8'))
        except Exception as e:
            return {'code': -1, 'message': str(e)}

    def send_sms(self, phone: str) -> dict:
        return self._post('/api/user/sms/send', {'phone': phone})

    def login(self, phone: str, sms_code: str = '123456') -> dict:
        return self._post('/api/user/login', {'phone': phone, 'smsCode': sms_code})

    def get_passengers(self, token: str) -> dict:
        return self._get('/api/user/passengers', token)

    def add_passenger(self, token: str, name: str, id_card: str, phone: str) -> dict:
        return self._post('/api/user/passengers', {
            'realName': name,
            'idCardType': 1,
            'idCardNumber': id_card,
            'passengerType': 1,
            'phone': phone
        })


# ============================================================================
# 主流程
# ============================================================================
def main():
    parser = argparse.ArgumentParser(description='12306 压测数据准备')
    parser.add_argument('--count', '-n', type=int, default=2000, help='测试用户数量')
    parser.add_argument('--gateway', '-g', default='http://localhost:8080', help='网关地址')
    parser.add_argument('--output-dir', '-o', default='.', help='CSV 输出目录')
    parser.add_argument('--skip-login', action='store_true', help='跳过登录，仅生成 CSV')
    parser.add_argument('--start-index', type=int, default=1, help='手机号起始索引')
    parser.add_argument('--passengers-per-user', type=int, default=2, help='每用户乘车人数')
    args = parser.parse_args()

    os.makedirs(args.output_dir, exist_ok=True)
    count = args.count
    start = args.start_index

    # ---- 生成用户 CSV ----
    users_csv = os.path.join(args.output_dir, f'test_users_{count}.csv')
    passengers_csv = os.path.join(args.output_dir, f'test_passengers_{count}.csv')

    logger.info(f"准备 {count} 个测试用户，手机号范围: {generate_phone(start)} ~ {generate_phone(start + count - 1)}")

    if args.skip_login:
        # 仅生成 CSV，Token 留空（JMeter 中会先登录获取）
        logger.info("Skip-login 模式：仅生成 CSV，Token 需在压测时获取")
        with open(users_csv, 'w', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            writer.writerow(['phone', 'smsCode', 'token'])
            for i in range(start, start + count):
                writer.writerow([generate_phone(i), '123456', ''])

        with open(passengers_csv, 'w', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            writer.writerow(['phone', 'passengerId', 'passengerName', 'idCardNumber'])
            for i in range(start, start + count):
                for j in range(args.passengers_per_user):
                    name = generate_name()
                    id_card = generate_id_card()
                    writer.writerow([generate_phone(i), '', name, id_card])

        logger.info(f"✅ 用户 CSV: {users_csv}")
        logger.info(f"✅ 乘车人 CSV: {passengers_csv}")
        return

    # ---- 在线模式：登录获取 Token + 添加乘车人 ----
    client = ApiClient(args.gateway)
    users_data = []
    passengers_data = []
    success_count = 0
    fail_count = 0

    for i in range(start, start + count):
        phone = generate_phone(i)
        token = ''
        passenger_ids = []

        # 1. 发送验证码
        sms_resp = client.send_sms(phone)
        if sms_resp.get('code') != 200:
            # 可能是频率限制，等一下重试
            time.sleep(1)
            sms_resp = client.send_sms(phone)

        # 2. 登录
        login_resp = client.login(phone)
        if login_resp.get('code') == 200:
            token = login_resp.get('data', {}).get('token', '')
        else:
            logger.warning(f"  用户 {phone} 登录失败: {login_resp.get('message', '')}")
            fail_count += 1
            users_data.append([phone, '123456', ''])
            continue

        # 3. 获取已有乘车人
        psg_resp = client.get_passengers(token)
        existing_passengers = psg_resp.get('data', []) if psg_resp.get('code') == 200 else []

        if len(existing_passengers) > 0:
            # 已有乘车人，直接用
            for p in existing_passengers:
                passenger_ids.append(p.get('id', ''))
                passengers_data.append([phone, p.get('id', ''), p.get('realName', ''), p.get('idCardNumber', '')])
        else:
            # 添加乘车人
            for j in range(args.passengers_per_user):
                name = generate_name()
                id_card = generate_id_card()
                add_resp = client.add_passenger(token, name, id_card, phone)
                if add_resp.get('code') == 200:
                    pid = add_resp.get('data', '')
                    passenger_ids.append(pid)
                    passengers_data.append([phone, pid, name, id_card])
                else:
                    logger.warning(f"  用户 {phone} 添加乘车人失败: {add_resp.get('message', '')}")
                time.sleep(0.1)  # 避免请求过快

        users_data.append([phone, '123456', token])
        success_count += 1

        if success_count % 100 == 0:
            logger.info(f"  进度: {success_count}/{count} (成功), {fail_count} 失败")

        # 避免触发 SMS 频率限制（60秒/手机号）
        time.sleep(0.05)

    # 写入 CSV
    with open(users_csv, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        writer.writerow(['phone', 'smsCode', 'token'])
        writer.writerows(users_data)

    with open(passengers_csv, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        writer.writerow(['phone', 'passengerId', 'passengerName', 'idCardNumber'])
        writer.writerows(passengers_data)

    logger.info("=" * 50)
    logger.info(f"✅ 完成: {success_count} 成功, {fail_count} 失败")
    logger.info(f"📄 用户 CSV: {users_csv}")
    logger.info(f"📄 乘车人 CSV: {passengers_csv}")


if __name__ == '__main__':
    main()
