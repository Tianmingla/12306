#!/bin/bash
# ============================================================
# 12306 压测数据准备脚本
# 用途：批量注册测试用户 + 添加乘车人
# 前置：确保后端服务已启动，SMS mock 已开启
# ============================================================

BASE_URL="http://localhost:8080/api"
USER_COUNT=${1:-20}
PHONE_PREFIX="1380000"

echo "===== 准备 ${USER_COUNT} 个测试用户 ====="

for i in $(seq -w 1 $USER_COUNT); do
    PHONE="${PHONE_PREFIX}${i}"
    echo -n "User ${PHONE}: "

    # 1. 发送验证码
    SMS_RESP=$(curl -s -X POST "${BASE_URL}/user/sms/send" \
        -H "Content-Type: application/json" \
        -d "{\"phone\":\"${PHONE}\"}")
    SMS_CODE=$(echo "$SMS_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null)

    if [ "$SMS_CODE" != "200" ]; then
        echo "SMS failed: $SMS_RESP"
        continue
    fi

    # 2. 登录
    LOGIN_RESP=$(curl -s -X POST "${BASE_URL}/user/login" \
        -H "Content-Type: application/json" \
        -d "{\"phone\":\"${PHONE}\",\"smsCode\":\"123456\"}")
    TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('token',''))" 2>/dev/null)

    if [ -z "$TOKEN" ] || [ "$TOKEN" = "None" ]; then
        echo "Login failed: $LOGIN_RESP"
        continue
    fi

    # 3. 检查是否已有乘车人
    PASSENGERS_RESP=$(curl -s -X GET "${BASE_URL}/user/passengers" \
        -H "Authorization: Bearer ${TOKEN}")
    PASSENGER_COUNT=$(echo "$PASSENGERS_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin).get('data',[]); print(len(d))" 2>/dev/null)

    if [ "$PASSENGER_COUNT" -gt "0" ] 2>/dev/null; then
        echo "OK (already has ${PASSENGER_COUNT} passengers)"
        continue
    fi

    # 4. 添加乘车人
    ADD_RESP=$(curl -s -X POST "${BASE_URL}/user/passengers" \
        -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: application/json" \
        -d "{\"realName\":\"测试用户${i}\",\"idCardType\":1,\"idCardNumber\":\"1101011990010${i}3X\",\"passengerType\":1,\"phone\":\"${PHONE}\"}")
    ADD_CODE=$(echo "$ADD_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null)

    if [ "$ADD_CODE" = "200" ]; then
        echo "OK (passenger added)"
    else
        echo "Add passenger failed: $ADD_RESP"
    fi
done

echo ""
echo "===== 数据准备完成 ====="
echo "测试用户文件: test_users.csv"
echo "运行压测: jmeter -n -t 12306_stress_test.jmx -l result.jtl -j jmeter.log"
