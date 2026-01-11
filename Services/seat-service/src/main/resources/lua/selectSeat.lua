-- 脚本参数说明：
-- KEYS[1]: Redis Key (例如 "G123::20231027::5")
-- ARGV[1]: 起始区间索引 (0-based)
-- ARGV[2]: 结束区间索引 (0-based)

local key = KEYS[1]
local startIdx = tonumber(ARGV[1])
local endIdx = tonumber(ARGV[2])

-- 1. 获取所有涉及的区间数据
local segments = {}
-- 即使区间不多，也建议做一下范围保护
if startIdx > endIdx then
    return -1 -- 参数错误
end

-- 获取区间数据的总数，防止索引越界 (LLEN)
local listLen = redis.call('LLEN', key)
if endIdx >= listLen then
    return -2 -- 索引越界
end

-- 循环获取涉及的每个区间的座位状态字符串
-- 注意：Redis List 索引是 0-based，LRANGE 是闭区间
local rangeData = redis.call('LRANGE', key, startIdx, endIdx)

if not rangeData or #rangeData == 0 then
    return -3 -- 数据不存在
end

-- 2. 寻找可用的座位
-- 所有区间的座位总数是一样的，取第一个区间的长度作为座位总数
local totalSeats = string.len(rangeData[1])
local foundSeatIndex = -1 -- 0-based index relative to string, but Lua uses 1-based

-- 遍历每一个座位 (Lua 索引从 1 开始)
for seat = 1, totalSeats do
    local isAvailable = true

    -- 检查该座位在每一个区间是否都是 '0'
    for _, segmentStr in ipairs(rangeData) do
        -- string.sub(s, i, i) 获取第 i 个字符
        local status = string.sub(segmentStr, seat, seat)
        if status ~= '0' then
            isAvailable = false
            break
        end
    end

    -- 如果在所有区间都可用，锁死这个座位
    if isAvailable then
        foundSeatIndex = seat
        break
    end
end

-- 如果没找到可用座位
if foundSeatIndex == -1 then
    return 0 -- 失败：无票
end

-- 3. 执行扣减 (更新数据)
-- foundSeatIndex 是 Lua 的 1-based 索引
for i = 0, (endIdx - startIdx) do
    local realIdx = startIdx + i
    local oldStr = rangeData[i + 1]

    -- 拼接新字符串：前半部分 + '1' + 后半部分
    local prefix = string.sub(oldStr, 1, foundSeatIndex - 1)
    local suffix = string.sub(oldStr, foundSeatIndex + 1)
    local newStr = prefix .. "1" .. suffix

    -- 更新回 Redis
    redis.call('LSET', key, realIdx, newStr)
end

-- 返回成功选定的座位号 (为了方便阅读，返回 1-based 的座位号)
return foundSeatIndex