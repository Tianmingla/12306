local key = KEYS[1]
local startIdx = tonumber(ARGV[1])
local endIdx = tonumber(ARGV[2])
local seatIdx = tonumber(ARGV[3])  --0-based

local bytePos = math.floor(seatIdx / 8) + 1  -- Lua string index from 1
local bitOffset = seatIdx % 8
local mask = bit.lshift(1, bitOffset)


local segments = redis.call('LRANGE', key, startIdx, endIdx)
if #segments == 0 then return 0 end

-- 手动 or 所有 segment 在目标字节上的值
local combined = 0
for _, seg in ipairs(segments) do
    local b = 0
    if #seg >= bytePos then
        b = string.byte(seg, bytePos)
    end
    combined = bit.bor(combined, b)
end

-- 检查目标位是否空闲
if bit.band(combined, mask) ~= 0 then
    return 0  -- 已被占用
end

-- ======== 占用：对每个 segment 设置该位 ========
for i = 0, #segments - 1 do
    local realIdx = startIdx + i
    local seg = segments[i + 1]

    local current = string.byte(seg, bytePos) or 0
    local newByte = bit.bor(current, mask)
    local newSeg = string.sub(seg, 1, bytePos - 1) ..
                   string.char(newByte) ..
                   string.sub(seg, bytePos + 1)

    redis.call('LSET', key, realIdx, newSeg)
end

return 1