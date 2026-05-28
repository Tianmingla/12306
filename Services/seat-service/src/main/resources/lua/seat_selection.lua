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
if not bitmap then
    redis.call('SETBIT', detail_key, 0, 0)
    bitmap = redis.call('GET', detail_key)
end

-- Precompute: for each seat index, build a per-byte OR mask covering [start_seg, end_seg]
-- This replaces the inner loop of is_free / mark_occupied with a single bitwise check per byte
local function build_byte_masks(seat_indices, s, e, sc)
    -- masks[byte_pos] = OR of all bits that need to be checked/set for all seats in this byte
    local masks = {}
    for _, idx in ipairs(seat_indices) do
        for i = s, e do
            local bit_pos = idx * sc + i
            local byte_pos = math.floor(bit_pos / 8) + 1
            local bit_offset = 7 - (bit_pos % 8)
            masks[byte_pos] = bit.bor(masks[byte_pos] or 0, bit.lshift(1, bit_offset))
        end
    end
    return masks
end

-- Check if all bits in masks are 0 in the bitmap (all seats free)
local function check_free(bm, masks)
    for byte_pos, mask in pairs(masks) do
        local byte = string.byte(bm, byte_pos) or 0
        if bit.band(byte, mask) ~= 0 then
            return false
        end
    end
    return true
end

-- Apply OR masks to mark seats occupied, return new bitmap
local function apply_or_masks(bm, masks)
    -- Collect byte positions and sort for deterministic processing
    local positions = {}
    for pos, _ in pairs(masks) do
        table.insert(positions, pos)
    end
    table.sort(positions)

    if #positions == 0 then return bm end

    -- Build new bitmap by splicing only the changed bytes
    local result = {}
    local prev_end = 0
    for _, pos in ipairs(positions) do
        local mask = masks[pos]
        local byte = string.byte(bm, pos) or 0
        local new_byte = bit.bor(byte, mask)
        -- Add unchanged segment before this byte
        if pos - 1 > prev_end then
            table.insert(result, string.sub(bm, prev_end + 1, pos - 1))
        end
        table.insert(result, string.char(new_byte))
        prev_end = pos
    end
    -- Add remaining unchanged tail
    if prev_end < #bm then
        table.insert(result, string.sub(bm, prev_end + 1))
    end
    return table.concat(result)
end

-- Parse groups
local function split(inputstr, sep)
    if sep == nil then sep = "%s" end
    local t = {}
    for str in string.gmatch(inputstr, "([^"..sep.."]+)") do
        table.insert(t, str)
    end
    return t
end

local groups = split(groups_str, ";")
for _, group_str in ipairs(groups) do
    local seat_strs = split(group_str, ",")
    if #seat_strs == num_seats then
        local seat_indices = {}
        for _, s in ipairs(seat_strs) do
            table.insert(seat_indices, tonumber(s))
        end

        -- Build mask for all seats in this group at once
        local masks = build_byte_masks(seat_indices, start_seg, end_seg, seg_count)

        if check_free(bitmap, masks) then
            -- Mark occupied using the same masks
            local updated_bitmap = apply_or_masks(bitmap, masks)
            redis.call('SET', detail_key, updated_bitmap)

            -- Update Inventory Count
            for i = start_seg, end_seg do
                local count = tonumber(redis.call('LINDEX', remaining_key, i))
                if count and count > 0 then
                    redis.call('LSET', remaining_key, i, count - num_seats)
                end
            end

            return group_str
        end
    end
end

return nil
