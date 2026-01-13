-- 最终降级方案：从多个 segment 中找第一个空闲位并占用
local key = KEYS[1]
local startIdx = tonumber(ARGV[1])
local endIdx = tonumber(ARGV[2])

local segments = redis.call('LRANGE', key, startIdx, endIdx)
if #segments == 0 then
    return 0
end

-- ✅ 修正1: 取第一个 segment（Lua 表从 1 开始）
local firstSeg = segments[1]

-- ✅ 修正2: 最后一字节表示位图大小（假设 bitSize 存在最后一字节）
-- 注意：这里假设每个 segment 长度相同，且最后 1 字节是 bitSize
-- 如果 bitSize 是全局的，可能需要单独传入或约定位置
local segLen = #firstSeg
if segLen == 0 then
    return -1
end
local bitSize = string.byte(firstSeg, segLen)  -- 最后一字节

-- 边界检查
if not bitSize or bitSize <= 0 then
    return -1
end

local seatIdx = nil
local lastByteIdx = -1
local combined = 0
local mask = 0

-- ✅ 修正3: 位索引从 0 到 bitSize - 1
for i = 0, bitSize - 1 do
    local byteIdx = math.floor(i / 8) + 1  -- Lua 字符串索引从 1 开始！

    -- 检查是否进入新字节
    if byteIdx ~= lastByteIdx then
        lastByteIdx = byteIdx
        combined = 0

        -- 对每个 segment 的同一字节位置进行 OR 合并
        for _, seg in ipairs(segments) do
            -- ✅ 修正4: 确保 byteIdx 不越界
            if byteIdx > #seg then
                -- 超出 segment 长度，视为 0（空闲），但通常不应发生
                -- 可选择跳过或报错
                -- 这里按 0 处理（不影响 OR）
            else
                local b = string.byte(seg, byteIdx)
                combined = bit.bor(combined, b)
            end
        end
    end

    -- 计算掩码：第 i 位（从 LSB 开始）
    mask = bit.lshift(1, i % 8)  -- ✅ 修正5: 只取低 8 位偏移

    -- 检查该位是否空闲（0 表示空闲）
    if bit.band(combined, mask) == 0 then
        seatIdx = i
        break
    end
end

if seatIdx == nil then
    return -1
end

-- 计算字节位置（Lua 字符串索引从 1 开始！）
local bytePos = math.floor(seatIdx / 8) + 1
local bitOffset = seatIdx % 8
local finalMask = bit.lshift(1, bitOffset)

-- ======== 占用：对每个 segment 设置该位 ========
for idx = 1, #segments do
    local realIdx = startIdx + idx - 1  -- 因为 idx 从 1 开始
    local seg = segments[idx]

    local currentByte = string.byte(seg, bytePos)
    local newByte = bit.bor(currentByte, finalMask)

    -- 构造新字符串
    local newSeg = string.sub(seg, 1, bytePos - 1) ..
                   string.char(newByte) ..
                   string.sub(seg, bytePos + 1)

    redis.call('LSET', key, realIdx, newSeg)
end

return seatIdx