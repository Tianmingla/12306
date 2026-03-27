-- KEYS[1]: Carriage BitMap Key (TICKET::DETAIL::trainId::date::carriageNum)
-- KEYS[2]: Inventory Count Key (TICKET::REMAINING::trainId::date::seatType)
-- ARGV[1]: Start Segment Index (0-based)
-- ARGV[2]: End Segment Index (0-based)
-- ARGV[3]: Number of seats requested
-- ARGV[4]: Groups of seat indices to try (e.g., "0,1;1,2;2,3")
-- ARGV[5]: Total segments count

local detail_key = KEYS[1]
local remaining_key = KEYS[2]
local start_seg = tonumber(ARGV[1])
local end_seg = tonumber(ARGV[2])
local num_seats = tonumber(ARGV[3])
local groups_str = ARGV[4]
local seg_count = tonumber(ARGV[5])

local bitmap = redis.call('GET', detail_key)
-- if not bitmap then return nil end
if not bitmap then
    redis.call('SETBIT', detail_key, 0, 0)
    bitmap = redis.call('GET', detail_key)
end
-- Helper to check if a seat is free for segments [start_seg, end_seg]
local function is_free(current_bitmap, seat_idx,s, e, sc)
    for i = s, e do
        local bit_pos = seat_idx * sc + i
        local byte_pos = math.floor(bit_pos / 8) + 1
        local bit_offset = 7 - (bit_pos % 8)
        local byte = string.byte(current_bitmap, byte_pos) or 0
        if bit.band(byte, bit.lshift(1, bit_offset)) ~= 0 then
            return false
        end
    end
    return true
end

-- Helper to mark a seat as occupied
local function mark_occupied(current_bitmap, seat_idx, s, e, sc)
    local new_bitmap = current_bitmap
    for i = s, e do
        local bit_pos = seat_idx * sc + i
        local byte_pos = math.floor(bit_pos / 8) + 1
        local bit_offset = 7 - (bit_pos % 8)
        local byte = string.byte(new_bitmap, byte_pos) or 0
        byte = bit.bor(byte, bit.lshift(1, bit_offset))
        new_bitmap = string.sub(new_bitmap, 1, byte_pos - 1) .. string.char(byte) .. string.sub(new_bitmap, byte_pos + 1)
    end
    return new_bitmap
end

-- Parse groups and try to find a successful one
local function split(inputstr, sep)
    if sep == nil then sep = "%s" end
    local t={}
    for str in string.gmatch(inputstr, "([^"..sep.."]+)") do
        table.insert(t, str)
    end
    return t
end

local groups = split(groups_str, ";")
for _, group_str in ipairs(groups) do
    local seat_indices = split(group_str, ",")
    if #seat_indices == num_seats then
        local all_free = true
        for _, idx_str in ipairs(seat_indices) do
            local idx = tonumber(idx_str)
            if not is_free(bitmap, idx, start_seg, end_seg, seg_count) then
                all_free = false
                break
            end
        end

        if all_free then
            -- Found a group! Mark them and update inventory
            local updated_bitmap = bitmap
            for _, idx_str in ipairs(seat_indices) do
                updated_bitmap = mark_occupied(updated_bitmap, tonumber(idx_str), start_seg, end_seg, seg_count)
            end
            
            -- Update BitMap
            redis.call('SET', detail_key, updated_bitmap)
            
            -- Update Inventory Count (Remaining tickets)
            for i = start_seg, end_seg do
                local count = tonumber(redis.call('LINDEX', remaining_key, i))
                if count and count > 0 then
                    redis.call('LSET', remaining_key, i, count - 1)
                end
            end
            
            return group_str -- Return the selected seat indices
        end
    end
end

return nil
