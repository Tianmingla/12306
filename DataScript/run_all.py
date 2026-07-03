#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
12306 数据导入主控脚本 (重构版)
用法:
    python run_all.py                       # 使用默认配置 import.json
    python run_all.py --config my.json      # 使用自定义 JSON 配置
    python run_all.py --dry-run             # 仅显示执行计划
"""

import json
import os
import sys
import time
import logging
import argparse
import importlib.util
from dataclasses import dataclass, field
from typing import Dict, Any, Optional, List

# ============================================================================
# 日志
# ============================================================================
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger('importer')

# ============================================================================
# 数据模型
# ============================================================================
@dataclass
class DatabaseConfig:
    """数据库连接配置"""
    host: str = 'localhost'
    port: int = 3306
    user: str = 'root'
    password: str = '123456'
    database: str = 'my12306'

    @classmethod
    def from_dict(cls, data: dict) -> 'DatabaseConfig':
        return cls(
            host=data.get('host', 'localhost'),
            port=int(data.get('port', 3306)),
            user=data.get('user', 'root'),
            password=data.get('password', '123456'),
            database=data.get('database', 'my12306')
        )

    def apply_env_overrides(self):
        """环境变量覆盖配置"""
        self.host = os.environ.get('MYSQL_HOST', self.host)
        self.port = int(os.environ.get('MYSQL_PORT', self.port))
        self.user = os.environ.get('MYSQL_USER', self.user)
        self.password = os.environ.get('MYSQL_PASSWORD', self.password)
        self.database = os.environ.get('MYSQL_DATABASE', self.database)


@dataclass
class ScriptMeta:
    """单个导入脚本的元数据"""
    name: str                           # 标识符，如 "import_stations"
    path: str                           # 相对脚本路径，如 "1_import_stations.py"
    description: str = ""               # 说明
    enabled: bool = True                # 是否执行
    params: Dict[str, Any] = field(default_factory=dict)  # 传递给脚本的参数

    def get_abs_path(self, base_dir: str) -> str:
        return os.path.join(base_dir, self.path)


# ============================================================================
# 配置加载
# ============================================================================
class ImportConfig:
    """整体配置"""
    def __init__(self, db: DatabaseConfig, scripts: List[ScriptMeta]):
        self.db = db
        self.scripts = scripts

    @classmethod
    def from_json(cls, config_path: str) -> 'ImportConfig':
        with open(config_path, 'r', encoding='utf-8') as f:
            data = json.load(f)

        db = DatabaseConfig.from_dict(data.get('database', {}))
        db.apply_env_overrides()  # 环境变量优先级最高

        scripts = []
        for item in data.get('scripts', []):
            scripts.append(ScriptMeta(
                name=item['name'],
                path=item['path'],
                description=item.get('description', ''),
                enabled=item.get('enabled', True),
                params=item.get('params', {})
            ))
        return cls(db=db, scripts=scripts)


# ============================================================================
# 脚本执行器
# ============================================================================
class ScriptRunner:
    """负责执行单个子脚本"""
    def __init__(self, meta: ScriptMeta, db_config: DatabaseConfig):
        self.meta = meta
        self.db = db_config

    def run(self, base_dir: str) -> bool:
        script_path = self.meta.get_abs_path(base_dir)
        if not os.path.exists(script_path):
            logger.error(f"脚本文件不存在: {script_path}")
            return False

        logger.info(f"▶ 开始执行: {self.meta.name} - {self.meta.description}")

        # ---- 新增：设置环境变量 ----
        env_backup = {}
        env_map = {
            'DB_HOST': self.db.host,
            'DB_PORT': str(self.db.port),
            'DB_USER': self.db.user,
            'DB_PASSWORD': self.db.password,
            'DB_NAME': self.db.database,
        }
        for key, value in env_map.items():
            env_backup[key] = os.environ.get(key)  # 备份原始值
            os.environ[key] = value

        start = time.time()
        try:
            module_name = f"importer.{self.meta.name}"
            spec = importlib.util.spec_from_file_location(module_name, script_path)
            module = importlib.util.module_from_spec(spec)

            # 仍然保留覆盖模块 DB_CONFIG 的尝试（兼容老脚本）
            if not hasattr(module, 'DB_CONFIG'):
                module.DB_CONFIG = {}
            module.DB_CONFIG.update({
                'host': self.db.host,
                'port': self.db.port,
                'user': self.db.user,
                'password': self.db.password,
                'database': self.db.database,
                'charset': 'utf8mb4',
            })
            logger.info(f"  数据库配置已覆盖: {self.db.host}:{self.db.port}")

            spec.loader.exec_module(module)

            # 准备模拟命令行参数
            original_argv = sys.argv
            sys.argv = self._build_argv()
            try:
                if hasattr(module, 'main'):
                    module.main()
                else:
                    logger.warning(f"脚本 {self.meta.name} 没有 main() 函数")
                elapsed = time.time() - start
                logger.info(f"✅ {self.meta.name} 完成 (耗时 {elapsed:.1f}s)")
                return True
            finally:
                sys.argv = original_argv
        except Exception as e:
            elapsed = time.time() - start
            logger.error(f"❌ {self.meta.name} 失败 (耗时 {elapsed:.1f}s): {e}")
            import traceback
            traceback.print_exc()
            return False
        finally:
            # ---- 恢复环境变量 ----
            for key, value in env_backup.items():
                if value is None:
                    os.environ.pop(key, None)
                else:
                    os.environ[key] = value

    def _build_argv(self) -> List[str]:
        """根据 self.meta.params 构造模拟的命令行参数"""
        argv = [self.meta.name]  # 脚本名占位
        for key, value in self.meta.params.items():
            # 假设参数格式为 --key value
            argv.append(f'--{key}')
            argv.append(str(value))
        return argv


# ============================================================================
# 数据库连接等待
# ============================================================================
def wait_for_mysql(db: DatabaseConfig, retries=30, interval=5):
    """等待 MySQL 可用"""
    import pymysql
    logger.info(f"等待 MySQL 就绪 ({db.host}:{db.port})...")
    for i in range(retries):
        try:
            conn = pymysql.connect(
                host=db.host, port=db.port,
                user=db.user, password=db.password,
                charset='utf8mb4'
            )
            conn.close()
            logger.info("MySQL 已就绪")
            return True
        except Exception:
            if i < retries - 1:
                logger.info(f"  重试 {i+1}/{retries}，{interval}s 后重试...")
                time.sleep(interval)
    logger.error("MySQL 连接超时")
    return False


# ============================================================================
# 主函数
# ============================================================================
def main():
    parser = argparse.ArgumentParser(description='12306 数据导入主控脚本')
    parser.add_argument('--config', '-c', default='import.json', help='JSON 配置文件路径')
    parser.add_argument('--dry-run', action='store_true', help='仅显示执行计划，不运行')
    args = parser.parse_args()

    # 确定脚本所在目录（子脚本位置）
    base_dir = os.path.dirname(os.path.abspath(__file__))

    # 加载配置
    config_path = os.path.join(base_dir, args.config)
    if not os.path.exists(config_path):
        logger.error(f"配置文件不存在: {config_path}")
        sys.exit(1)

    config = ImportConfig.from_json(config_path)

    # 打印执行计划
    logger.info("=" * 60)
    logger.info("12306 数据导入计划")
    logger.info("=" * 60)
    logger.info(f"数据库: {config.db.host}:{config.db.port}/{config.db.database}")
    logger.info(f"脚本数量: {len(config.scripts)}")
    logger.info("-" * 60)

    active_scripts = []
    for s in config.scripts:
        status = "✅ 启用" if s.enabled else "⏭️ 跳过"
        extra = f" (参数: {s.params})" if s.params else ""
        logger.info(f"  {status}  {s.name} - {s.description}{extra}")
        if s.enabled:
            active_scripts.append(s)
    logger.info("=" * 60)

    if args.dry_run:
        logger.info("Dry-run 模式，退出")
        return

    # 等待 MySQL
    if not wait_for_mysql(config.db):
        sys.exit(1)

    # 按序执行
    total_start = time.time()
    success = 0
    failed = 0

    for meta in active_scripts:
        runner = ScriptRunner(meta, config.db)
        result = runner.run(base_dir)
        if result:
            success += 1
        else:
            failed += 1
            # 默认失败后继续（可通过环境变量改变）
            if os.environ.get('STOP_ON_ERROR', '').lower() == 'true':
                logger.error("STOP_ON_ERROR 已设置，终止执行")
                break

    # 汇总
    total_elapsed = time.time() - total_start
    logger.info("=" * 60)
    logger.info(f"导入完成: 成功 {success}, 失败 {failed}, 总计 {len(active_scripts)}")
    logger.info(f"总耗时: {total_elapsed:.1f}s")
    logger.info("=" * 60)

    if failed > 0:
        sys.exit(1)


if __name__ == '__main__':
    main()