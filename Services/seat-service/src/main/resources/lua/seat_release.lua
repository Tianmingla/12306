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
    -- 如果 bitmap 不存在，说明没有锁定，直接返回成功
    return "1"
end

-- Helper to check if a seat is occupied for segments [start_seg, end_seg]
local function is_occupied(current_bitmap, seat_idx, s, e, sc)
    for i = s, e do
        local bit_pos = seat_idx * sc + i
        local byte_pos = math.floor(bit_pos / 8) + 1
        local bit_offset = 7 - (bit_pos % 8)
        local byte = string.byte(current_bitmap, byte_pos) or 0
        if bit.band(byte, bit.lshift(1, bit_offset)) == 0 then
            return false
        end
    end
    return true
end

-- Helper to mark a seat as free (release)
local function mark_free(current_bitmap, seat_idx, s, e, sc)
    local new_bitmap = current_bitmap
    for i = s, e do
        local bit_pos = seat_idx * sc + i
        local byte_pos = math.floor(bit_pos / 8) + 1
        local bit_offset = 7 - (bit_pos % 8)
        local byte = string.byte(new_bitmap, byte_pos) or 0
        byte = bit.band(byte, bit.bnot(bit.lshift(1, bit_offset)))
        new_bitmap = string.sub(new_bitmap, 1, byte_pos - 1) .. string.char(byte) .. string.sub(new_bitmap, byte_pos + 1)
    end
    return new_bitmap
end

-- Helper to split string
local function split(inputstr, sep)
    if sep == nil then sep = "%s" end
    local t={}
    for str in string.gmatch(inputstr, "([^"..sep.."]+)") do
        table.insert(t, str)
    end
    return t
end

-- Parse seat indices
local seat_indices = split(seats_str, ",")
local updated_bitmap = bitmap
local released_count = 0

for _, idx_str in ipairs(seat_indices) do
    local idx = tonumber(idx_str)
    if idx and is_occupied(updated_bitmap, idx, start_seg, end_seg, seg_count) then
        updated_bitmap = mark_free(updated_bitmap, idx, start_seg, end_seg, seg_count)
        released_count = released_count + 1
    end
end

if released_count > 0 then
    -- Update BitMap
    redis.call('SET', detail_key, updated_bitmap)

    -- Update Inventory Count (Increase remaining tickets)
    for i = start_seg, end_seg do
        local count = tonumber(redis.call('LINDEX', remaining_key, i))
        if count then
            redis.call('LSET', remaining_key, i, count + released_count)
        end
    end
end

return tostring(released_count)
