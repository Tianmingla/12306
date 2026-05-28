-- KEYS[1]: Carriage BitMap Key (TICKET::DETAIL::trainId::date::carriageNum)
-- KEYS[2]: Inventory Count Key (TICKET::REMAINING::trainId::date::seatType)
-- ARGV[1]: Start Segment Index (0-based)
-- ARGV[2]: End Segment Index (0-based)
-- ARGV[3]: Seat indices to release (comma separated, e.g., "0,1,2")
-- ARGV[4]: Total segments count

local detail_key = KEYS[1]
local remaining_key = KEYS[2]
local start_seg = tonumber(ARGV[1])
local end_seg = tonumber(ARGV[2])
local seats_str = ARGV[3]
local seg_count = tonumber(ARGV[4])

local bitmap = redis.call('GET', detail_key)
if not bitmap then
    return "1"
end

-- Build per-byte AND-NOT masks for all seats at once
-- For each byte position, accumulate the bits that need to be cleared
local function build_and_not_masks(seat_indices, s, e, sc)
    local masks = {}
    for _, idx in ipairs(seat_indices) do
        -- 计算当前座位覆盖的全局绝对比特范围 [global_start, global_end]
        local global_start = idx * sc + s
        local global_end   = idx * sc + e

        -- 计算起始和结束所在的字节索引（0-based）
        local start_byte_idx = math.floor(global_start / 8)
        local end_byte_idx   = math.floor(global_end / 8)

        -- 遍历受影响的每一个字节
        for byte_idx = start_byte_idx, end_byte_idx do
            -- 将 0-based 转为 1-based 作为 Lua table 的 key
            local byte_pos = byte_idx + 1

            -- 计算当前字节内的局部起始和结束比特位置 (0-7, LSB=0)
            local local_start_in_byte = global_start - byte_idx * 8
            local local_end_in_byte   = global_end - byte_idx * 8

            -- 限制在当前字节的 [0, 7] 范围内
            local clamped_start = math.max(local_start_in_byte, 0)
            local clamped_end   = math.min(local_end_in_byte, 7)

            -- 【核心优化】使用减法技巧生成该字节内的连续掩码 (LSB=0 视角)
            -- 生成 clamped_end+1 个 1，然后减去 clamped_start 个 1
            local local_mask_lsb0 = (bit.lshift(1, clamped_end + 1) - 1) - (bit.lshift(1, clamped_start) - 1)

            -- 累加到总掩码中
            masks[byte_pos] = bit.bor(masks[byte_pos] or 0, local_mask_lsb0)
        end
    end
    return masks
end

-- Check if all bits in masks are set to 1 in the bitmap (all seats occupied)
local function check_occupied(bm, masks)
    for byte_pos, mask in pairs(masks) do
        local byte = string.byte(bm, byte_pos) or 0
        if bit.band(byte, mask) ~= mask then
            return false
        end
    end
    return true
end

-- Apply AND-NOT masks to clear seat bits, return new bitmap
local function apply_and_not_masks(bm, masks)
    local positions = {}
    for pos, _ in pairs(masks) do
        table.insert(positions, pos)
    end
    table.sort(positions)

    if #positions == 0 then return bm end

    local result = {}
    local prev_end = 0
    for _, pos in ipairs(positions) do
        local mask = masks[pos]
        local byte = string.byte(bm, pos) or 0
        local new_byte = bit.band(byte, bit.bnot(mask))
        if pos - 1 > prev_end then
            table.insert(result, string.sub(bm, prev_end + 1, pos - 1))
        end
        table.insert(result, string.char(new_byte))
        prev_end = pos
    end
    if prev_end < #bm then
        table.insert(result, string.sub(bm, prev_end + 1))
    end
    return table.concat(result)
end

-- Parse seat indices
local seat_indices = {}
for s in string.gmatch(seats_str, "([^,]+)") do
    table.insert(seat_indices, tonumber(s))
end

-- Build masks for all seats at once
local masks = build_and_not_masks(seat_indices, start_seg, end_seg, seg_count)

-- Check that all seats are actually occupied before releasing
if not check_occupied(bitmap, masks) then
    return "0"
end

-- Apply AND-NOT to clear all seat bits at once
local updated_bitmap = apply_and_not_masks(bitmap, masks)
redis.call('SET', detail_key, updated_bitmap)

-- Update Inventory Count (increase by number of seats released)
local released_count = #seat_indices
for i = start_seg, end_seg do
    local count = tonumber(redis.call('LINDEX', remaining_key, i))
    if count then
        redis.call('LSET', remaining_key, i, count + released_count)
    end
end

return tostring(released_count)
