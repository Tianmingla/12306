#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
12306 数据导入主控脚本

功能：
    按顺序调用各子脚本完成全量数据导入，支持通过配置文件控制每个脚本的开关和参数。

用法：
    python run_all.py                        # 使用默认配置
    python run_all.py --config import.yml    # 使用自定义配置文件
    python run_all.py --dry-run              # 仅显示执行计划

配置文件说明见 import.example.yml
"""

import os
import sys
import time
import yaml
import importlib
import argparse
import logging
import traceback

# =============================================================================
# 日志配置
# =============================================================================
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger('data-importer')

# =============================================================================
# 脚本执行顺序（固定）
# =============================================================================
SCRIPT_ORDER = [
    '1_import_stations',
    '2_import_trains',
    '3_import_train_stations',
    '4_5_generate_seats_and_carriages',
    '5_generate_users',
    '6_generate_passengers',
    '7_generate_orders',
    '8_generate_station_distances',
    '9_generate_train_fare_configs',
]

# 脚本描述
SCRIPT_DESCRIPTIONS = {
    '1_import_stations': '导入车站数据 (t_station)',
    '2_import_trains': '导入车次数据 (t_train)',
    '3_import_train_stations': '导入车次经停站数据 (t_train_station)',
    '4_5_generate_seats_and_carriages': '生成座位和车厢数据 (t_seat, t_carriage)',
    '5_generate_users': '生成用户数据 (t_user)',
    '6_generate_passengers': '生成乘车人数据 (t_passenger)',
    '7_generate_orders': '生成订单数据 (t_order, t_order_item)',
    '8_generate_station_distances': '生成站间距离数据 (t_station_distance)',
    '9_generate_train_fare_configs': '生成票价配置数据 (t_train_fare_config)',
}

# 支持命令行参数的脚本（argparse）
# key: 脚本名, value: {yml配置key: 命令行flag}
SCRIPT_ARGS_MAP = {
    '5_generate_users': {
        'count': '--count',
    },
    '7_generate_orders': {
        'count': '--count',
    },
}

# =============================================================================
# 默认配置
# =============================================================================
DEFAULT_CONFIG = {
    'db': {
        'host': os.environ.get('MYSQL_HOST', 'localhost'),
        'port': int(os.environ.get('MYSQL_PORT', '3306')),
        'user': os.environ.get('MYSQL_USER', 'root'),
        'password': os.environ.get('MYSQL_PASSWORD', '123456'),
        'database': os.environ.get('MYSQL_DATABASE', 'my12306'),
    },
    'scripts': {
        '1_import_stations': {'enabled': True},
        '2_import_trains': {'enabled': True},
        '3_import_train_stations': {'enabled': True},
        '4_5_generate_seats_and_carriages': {'enabled': True},
        '5_generate_users': {'enabled': True},
        '6_generate_passengers': {'enabled': True},
        '7_generate_orders': {'enabled': True},
        '8_generate_station_distances': {'enabled': True},
        '9_generate_train_fare_configs': {'enabled': True},
    }
}


def deep_merge(base: dict, override: dict) -> dict:
    """深度合并字典，override 覆盖 base"""
    result = base.copy()
    for key, value in override.items():
        if key in result and isinstance(result[key], dict) and isinstance(value, dict):
            result[key] = deep_merge(result[key], value)
        else:
            result[key] = value
    return result


def load_config(config_path: str) -> dict:
    """加载配置文件，与默认配置深度合并"""
    config = DEFAULT_CONFIG.copy()

    if config_path and os.path.exists(config_path):
        with open(config_path, 'r', encoding='utf-8') as f:
            user_config = yaml.safe_load(f) or {}
        config = deep_merge(config, user_config)
        logger.info(f"已加载配置文件: {config_path}")
    else:
        logger.info("未找到配置文件，使用默认配置")

    return config


def set_env_from_config(db_config: dict) -> dict:
    """将数据库配置设置为环境变量，供子脚本读取"""
    env = os.environ.copy()
    env['DB_HOST'] = str(db_config.get('host', 'localhost'))
    env['DB_PORT'] = str(db_config.get('port', '3306'))
    env['DB_USER'] = str(db_config.get('user', 'root'))
    env['DB_PASSWORD'] = str(db_config.get('password', '123456'))
    env['DB_NAME'] = str(db_config.get('database', 'my12306'))
    return env


def build_argv(script_name: str, script_config: dict) -> list:
    """
    根据 yml 配置构建命令行参数列表。
    只为 SCRIPT_ARGS_MAP 中声明的脚本生成参数。
    """
    argv = [script_name]
    args_map = SCRIPT_ARGS_MAP.get(script_name, {})

    for yml_key, cli_flag in args_map.items():
        if yml_key in script_config:
            value = script_config[yml_key]
            argv.extend([cli_flag, str(value)])

    return argv


def run_script(script_name: str, script_config: dict, env: dict, script_dir: str) -> bool:
    """
    通过 importlib 执行子脚本。
    在执行前覆盖全局 DB_CONFIG，并将 yml 参数映射为 sys.argv。
    """
    script_path = os.path.join(script_dir, f'{script_name}.py')

    if not os.path.exists(script_path):
        logger.error(f"脚本不存在: {script_path}")
        return False

    # 构建 sys.argv
    original_argv = sys.argv
    sys.argv = build_argv(script_name, script_config)

    # 保存原始 DB_CONFIG 引用
    original_db_config = None

    logger.info(f"开始执行: {script_name} - {SCRIPT_DESCRIPTIONS.get(script_name, '')}")
    start_time = time.time()

    try:
        # 使用 importlib 加载模块
        spec = importlib.util.spec_from_file_location(script_name, script_path)
        module = importlib.util.module_from_spec(spec)

        # 先执行模块代码到定义阶段（不执行 main）
        spec.loader.exec_module(module)

        # 覆盖 DB_CONFIG（如果模块有定义）
        if hasattr(module, 'DB_CONFIG'):
            module.DB_CONFIG.update({
                'host': env.get('DB_HOST', 'localhost'),
                'port': int(env.get('DB_PORT', '3306')),
                'user': env.get('DB_USER', 'root'),
                'password': env.get('DB_PASSWORD', '123456'),
                'database': env.get('DB_NAME', 'my12306'),
                'charset': 'utf8mb4',
            })
            logger.info(f"  数据库配置已覆盖: {env.get('DB_HOST')}:{env.get('DB_PORT')}")

        # 调用 main 函数
        if hasattr(module, 'main'):
            module.main()
        # 如果没有 main 函数，模块已在 exec_module 时执行

        elapsed = time.time() - start_time
        logger.info(f"✅ {script_name} 完成 (耗时 {elapsed:.1f}s)")
        return True

    except Exception as e:
        elapsed = time.time() - start_time
        logger.error(f"❌ {script_name} 失败 (耗时 {elapsed:.1f}s)")
        logger.error(f"   错误: {e}")
        traceback.print_exc()
        return False

    finally:
        sys.argv = original_argv


def wait_for_mysql(db_config: dict, max_retries: int = 30, interval: int = 5):
    """等待 MySQL 可用"""
    import pymysql

    logger.info(f"等待 MySQL 就绪 ({db_config['host']}:{db_config['port']})...")

    for i in range(max_retries):
        try:
            conn = pymysql.connect(
                host=db_config['host'],
                port=int(db_config['port']),
                user=db_config['user'],
                password=db_config['password'],
                charset='utf8mb4',
            )
            conn.close()
            logger.info("MySQL 已就绪")
            return True
        except Exception:
            if i < max_retries - 1:
                logger.info(f"MySQL 未就绪，{interval}s 后重试 ({i + 1}/{max_retries})...")
                time.sleep(interval)
            else:
                logger.error("MySQL 连接超时")
                return False


def main():
    parser = argparse.ArgumentParser(description='12306 数据导入主控脚本')
    parser.add_argument('--config', '-c', default='import.yml', help='配置文件路径 (默认: import.yml)')
    parser.add_argument('--skip-wait', action='store_true', help='跳过 MySQL 就绪等待')
    parser.add_argument('--dry-run', action='store_true', help='仅显示执行计划，不实际运行')
    args = parser.parse_args()

    # 确定脚本目录（与 run_all.py 同目录）
    script_dir = os.path.dirname(os.path.abspath(__file__))

    # 加载配置
    config = load_config(os.path.join(script_dir, args.config))
    db_config = config['db']
    scripts_config = config.get('scripts', {})

    # 构建环境变量
    env = set_env_from_config(db_config)

    # 打印执行计划
    logger.info("=" * 60)
    logger.info("12306 数据导入计划")
    logger.info("=" * 60)
    logger.info(f"数据库: {db_config['host']}:{db_config['port']}/{db_config['database']}")
    logger.info("-" * 60)

    planned_scripts = []
    for script_name in SCRIPT_ORDER:
        script_cfg = scripts_config.get(script_name, {})
        enabled = script_cfg.get('enabled', True)
        status = "✅ 启用" if enabled else "⏭️ 跳过"
        desc = SCRIPT_DESCRIPTIONS.get(script_name, '')
        extra = ''
        if enabled:
            extras = {k: v for k, v in script_cfg.items() if k != 'enabled'}
            if extras:
                extra = f" (参数: {extras})"
        logger.info(f"  {status}  {script_name} - {desc}{extra}")
        if enabled:
            planned_scripts.append((script_name, script_cfg))

    logger.info("=" * 60)

    if args.dry_run:
        logger.info("Dry-run 模式，不实际执行")
        return

    # 等待 MySQL
    if not args.skip_wait:
        if not wait_for_mysql(db_config):
            logger.error("MySQL 不可用，退出")
            sys.exit(1)

    # 按顺序执行
    total_start = time.time()
    results = {}

    for script_name, script_cfg in planned_scripts:
        success = run_script(script_name, script_cfg, env, script_dir)
        results[script_name] = success
        if not success:
            continue_on_error = os.environ.get('IMPORT_CONTINUE_ON_ERROR', 'true').lower() == 'true'
            if not continue_on_error:
                logger.error("停止后续脚本执行")
                break
            else:
                logger.warning(f"{script_name} 失败，继续执行下一个脚本")

    # 打印汇总
    total_elapsed = time.time() - total_start
    logger.info("=" * 60)
    logger.info("数据导入汇总")
    logger.info("=" * 60)

    success_count = 0
    fail_count = 0
    skip_count = 0

    for script_name in SCRIPT_ORDER:
        script_cfg = scripts_config.get(script_name, {})
        enabled = script_cfg.get('enabled', True)

        if not enabled:
            skip_count += 1
            status = "⏭️ 跳过"
        elif results.get(script_name, False):
            success_count += 1
            status = "✅ 成功"
        else:
            fail_count += 1
            status = "❌ 失败"

        desc = SCRIPT_DESCRIPTIONS.get(script_name, '')
        logger.info(f"  {status}  {script_name} - {desc}")

    logger.info("-" * 60)
    logger.info(f"成功: {success_count}  失败: {fail_count}  跳过: {skip_count}")
    logger.info(f"总耗时: {total_elapsed:.1f}s")
    logger.info("=" * 60)

    if fail_count > 0:
        sys.exit(1)


if __name__ == '__main__':
    main()
