@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo   12306 RocketMQ Topic 创建脚本
echo   Windows 版本
echo ========================================
echo.

:: 设置 RocketMQ 地址
set NAMESRV_ADDR=localhost:9876
if not "%1"=="" set NAMESRV_ADDR=%1

echo [信息] NameServer 地址: %NAMESRV_ADDR%
echo.

:: 检查 mqadmin 命令是否存在
where mqadmin >nul 2>&1
if %errorlevel% neq 0 (
    echo [警告] mqadmin 命令未找到，请确保 RocketMQ 已安装并配置环境变量
    echo [提示] 或者使用完整路径，如: C:\rocketmq\bin\mqadmin.cmd
    echo.
    set MQADMIN=mqadmin
) else (
    set MQADMIN=mqadmin
)

:: 如果设置了 ROCKETMQ_HOME，使用完整路径
if defined ROCKETMQ_HOME (
    set MQADMIN=%ROCKETMQ_HOME%\bin\mqadmin.cmd
)

echo [信息] 使用命令: %MQADMIN%
echo.

:: Topic 列表
set TOPICS[0]=seat-selection-topic
set TOPICS[1]=seat-selection-result-topic
set TOPICS[2]=order-creation-topic
set TOPICS[3]=order-creation-result-topic
set TOPICS[4]=order-timeout-cancel-topic
set TOPICS[5]=travel-reminder-topic
set TOPICS[6]=seat-release-topic
set TOPICS[7]=waitlist-fulfill-topic

:: Topic 描述
set DESC[0]=选座请求 Topic
set DESC[1]=选座结果返回 Topic
set DESC[2]=订单创建请求 Topic
set DESC[3]=订单创建结果回调 Topic
set DESC[4]=订单超时取消 Topic（延迟消息）
set DESC[5]=出行提醒 Topic（延迟消息）
set DESC[6]=座位释放 Topic
set DESC[7]=候补兑现触发 Topic

echo ========================================
echo   开始创建 Topics...
echo ========================================
echo.

set SUCCESS_COUNT=0
set FAIL_COUNT=0

for /L %%i in (0,1,7) do (
    set TOPIC=!TOPICS[%%i]!
    set DESCRIPT=!DESC[%%i]!

    echo [创建] !TOPIC! - !DESCRIPT!

    %MQADMIN% updateTopic -n %NAMESRV_ADDR% -t !TOPIC! -c DefaultCluster -r 4 -w 4 >nul 2>&1

    if !errorlevel! equ 0 (
        echo [成功] !TOPIC! 创建成功
        set /a SUCCESS_COUNT+=1
    ) else (
        echo [失败] !TOPIC! 创建失败或已存在
        set /a FAIL_COUNT+=1
    )
    echo.
)

echo ========================================
echo   Topic 创建完成
echo ========================================
echo.
echo [统计] 成功: %SUCCESS_COUNT%, 失败/已存在: %FAIL_COUNT%
echo.

:: 显示所有 Topic
echo [信息] 显示当前所有 Topics:
echo ----------------------------------------
%MQADMIN% topicList -n %NAMESRV_ADDR% 2>nul
echo ----------------------------------------
echo.

echo [完成] 按任意键退出...
pause >nul
