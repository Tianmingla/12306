@echo off
:: 设置字符集为 UTF-8，防止中文乱码
chcp 65001 >nul

echo ========================================================
echo 正在一键启动本地开发环境 (RocketMQ, Redis, Nacos)...
echo ========================================================

echo [1/3] 正在启动 RocketMQ...
:: /d 参数用于指定程序的起始工作目录，确保相对路径不出错
start "RocketMQ" /d "E:\JavaWebPlugin\rocketmq\rocketmq-all-5.3.2-bin-release\bin" play.cmd

echo [2/3] 正在启动 Redis...
start "Redis" /d "E:\JavaWebPlugin\Redis\Redis-8.0.3-Windows-x64-msys2-with-Service" start.bat

echo [3/3] 正在启动 Nacos...
start "Nacos" /d "E:\JavaWebPlugin\nacos\bin" run.bat

echo ========================================================
echo 所有服务启动指令已发送！你应该会看到弹出了 3 个独立的控制台窗口。
echo ========================================================
pause