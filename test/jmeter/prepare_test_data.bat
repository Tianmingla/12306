@echo off
setlocal enabledelayedexpansion

:: ============================================================
:: 12306 压测数据准备脚本 (Windows Bat版)
:: 用途：批量注册测试用户 + 添加乘车人
:: 前置：确保后端服务已启动，SMS mock 已开启，且系统有 curl 和 python3
:: ============================================================

:: 设置默认参数，如果未传参则默认为 20
if "%~1"=="" (
    set "USER_COUNT=20"
) else (
    set "USER_COUNT=%~1"
)

set "BASE_URL=http://localhost:8080/api"
set "PHONE_PREFIX=1380000"

echo.
echo ===== 准备 %USER_COUNT% 个测试用户 =====
echo.

:: 使用 for /L 循环生成从 1 到 USER_COUNT 的数字
for /L %%i in (1, 1, %USER_COUNT%) do (
    :: 格式化数字为固定宽度 (例如 1 -> 01, 20 -> 20)，以匹配手机号长度
    set "NUM=00%%i"
    set "NUM=!NUM:~-2!"

    set "PHONE=%PHONE_PREFIX%!NUM!"
    echo | set /p dummy=User !PHONE!:

    :: 1. 发送验证码
    curl -s -X POST "%BASE_URL%/user/sms/send" ^
        -H "Content-Type: application/json" ^
        -d "{\"phone\":\"!PHONE!\"}" > temp_sms.json

    :: 使用 Python 解析 JSON 获取 code
    for /f "delims=" %%a in ('python -c "import json; print(json.load(open('temp_sms.json')).get('code',''))"') do set "SMS_CODE=%%a"

    if not "!SMS_CODE!"=="200" (
        echo SMS failed:
        type temp_sms.json
        del temp_sms.json >nul 2>&1
        goto :continue_loop
    )

    :: 2. 登录
    curl -s -X POST "%BASE_URL%/user/login" ^
        -H "Content-Type: application/json" ^
        -d "{\"phone\":\"!PHONE!\",\"smsCode\":\"123456\"}" > temp_login.json

    :: 使用 Python 解析 JSON 获取 token
    for /f "delims=" %%b in ('python -c "import json; d=json.load(open('temp_login.json')); print(d.get('data',{}).get('token',''))"') do set "TOKEN=%%b"

    if "!TOKEN!"=="" (
        echo Login failed:
        type temp_login.json
        del temp_login.json >nul 2>&1
        del temp_sms.json >nul 2>&1
        goto :continue_loop
    )

    :: 3. 检查是否已有乘车人
    curl -s -X GET "%BASE_URL%/user/passengers" ^
        -H "Authorization: Bearer !TOKEN!" > temp_passengers.json

    :: 使用 Python 计算乘客数组长度
    for /f "delims=" %%c in ('python -c "import json; d=json.load(open('temp_passengers.json')).get('data',[]); print(len(d))"') do set "PASSENGER_COUNT=%%c"

    if !PASSENGER_COUNT! GTR 0 (
        echo OK (already has !PASSENGER_COUNT! passengers)
        del temp_passengers.json >nul 2>&1
        del temp_login.json >nul 2>&1
        del temp_sms.json >nul 2>&1
        goto :continue_loop
    )

    :: 4. 添加乘车人
    :: 注意：这里使用了固定的身份证格式，实际生产环境可能需要更复杂的逻辑
    curl -s -X POST "%BASE_URL%/user/passengers" ^
        -H "Authorization: Bearer !TOKEN!" ^
        -H "Content-Type: application/json" ^
        -d "{\"realName\":\"测试用户!NUM!\",\"idCardType\":1,\"idCardNumber\":\"1101011990010!NUM!3X\",\"passengerType\":1,\"phone\":\"!PHONE!\"}" > temp_add.json

    for /f "delims=" %%d in ('python -c "import json; print(json.load(open('temp_add.json')).get('code',''))"') do set "ADD_CODE=%%d"

    if "!ADD_CODE!"=="200" (
        echo OK (passenger added)
    ) else (
        echo Add passenger failed:
        type temp_add.json
    )

    :: 清理本次循环产生的临时文件
    del temp_add.json >nul 2>&1
    del temp_passengers.json >nul 2>&1
    del temp_login.json >nul 2>&1
    del temp_sms.json >nul 2>&1

    :continue_loop
)

echo.
echo ===== 数据准备完成 =====
echo 测试用户文件: test_users.csv
echo 运行压测: jmeter -n -t 12306_stress_test.jmx -l result.jtl -j jmeter.log
echo.

endlocal