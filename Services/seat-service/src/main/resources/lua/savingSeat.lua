-- 尝试占定指定座位，做 CAS 操作
-- KEYS[1]: Redis Key (例如 "G123::20231027::5")
-- -- ARGV[1]: 起始区间索引 (0-based)
-- -- ARGV[2]: 结束区间索引 (0-based)
-- -- ARGV[3]: 指定座位索引 (1-based? 注意：下面按 1-based 处理)
-- -- ps:注意seatIdx与lua索引
--
-- local key = KEYS[1]
-- local startIdx = tonumber(ARGV[1])
-- local endIdx = tonumber(ARGV[2])
-- local seatIdx = tonumber(ARGV[3])+1
--
-- -- 获取区间内所有段
-- local rangeData = redis.call('LRANGE', key, startIdx, endIdx)
--
-- -- 检查是否全部可用
-- for _, segmentStr in ipairs(rangeData) do
--     if string.sub(segmentStr, seatIdx, seatIdx) ~= '0' then
--         return 0  -- 不可用，直接返回失败
--     end
-- end
--
-- -- 全部可用，执行占用
-- for i = 0, (endIdx - startIdx) do
--     local realIdx = startIdx + i
--     local oldStr = rangeData[i + 1]
--     local prefix = string.sub(oldStr, 1, seatIdx-1)
--     local suffix = string.sub(oldStr, seatIdx + 1)
--     local newStr = prefix .. "1" .. suffix
--     redis.call('LSET', key, realIdx, newStr)
-- end
--
-- return 1  -- 成功
local key = KEYS[1]
local startIdx = tonumber(ARGV[1])
local endIdx = tonumber(ARGV[2])
local seatIdx = tonumber(ARGV[3]) + 1  -- 注意：这里 +1 是因为 Java 传的是 0-based？

redis.log(redis.LOG_WARNING, "DEBUG: key=" .. tostring(key))
redis.log(redis.LOG_WARNING, "DEBUG: startIdx=" .. tostring(startIdx) .. ", endIdx=" .. tostring(endIdx))
redis.log(redis.LOG_WARNING, "DEBUG: seatIdx (1-based)=" .. tostring(seatIdx))

local rangeData = redis.call('LRANGE', key, startIdx, endIdx)

redis.log(redis.LOG_WARNING, "DEBUG: rangeData length=" .. tostring(#rangeData))
for i, seg in ipairs(rangeData) do
    redis.log(redis.LOG_WARNING, "DEBUG: segment[" .. i .. "] = '" .. tostring(seg) .. "'")
    if string.len(seg) < seatIdx then
        redis.log(redis.LOG_WARNING, "ERROR: seatIdx out of bounds! seg len=" .. string.len(seg))
        return 0
    end
    local charAt = string.sub(seg, seatIdx, seatIdx)
    redis.log(redis.LOG_WARNING, "DEBUG: char at seatIdx=" .. seatIdx .. " is '" .. charAt .. "'")
    if charAt ~= '0' then
        redis.log(redis.LOG_WARNING, "INFO: Seat occupied, returning 0")
        return 0
    end
end

-- 如果走到这里，说明全可用
redis.log(redis.LOG_WARNING, "INFO: All segments available, proceeding to occupy...")

for i = 0, (endIdx - startIdx) do
    local realIdx = startIdx + i
    local oldStr = rangeData[i + 1]
    local newStr = string.sub(oldStr, 1, seatIdx - 1) .. "1" .. string.sub(oldStr, seatIdx + 1)
    redis.log(redis.LOG_WARNING, "DEBUG: LSET index=" .. realIdx .. ", newStr='" .. newStr .. "'")
    redis.call('LSET', key, realIdx, newStr)
end

redis.log(redis.LOG_WARNING, "INFO: Successfully occupied, returning 1")
return 1