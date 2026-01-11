package com.lalal.modules.core.seat;

import com.lalal.modules.model.CarLayout;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SleeperSeatParser implements Parser {

    @Override
    public int index(String seatNumber) {
        if (seatNumber == null || seatNumber.isEmpty()) {
            throw new IllegalArgumentException("Sleeper seat number is empty");
        }

        // 移除中文字符，只保留数字和可能的方位
        String clean = seatNumber.replaceAll("[^0-9上下中]", "");

        // 提取铺位号（第一个数字序列）
        Pattern p = Pattern.compile("(\\d+)");
        Matcher m = p.matcher(clean);
        if (!m.find()) {
            throw new IllegalArgumentException("No berth number found in: " + seatNumber);
        }
        int berthNum = Integer.parseInt(m.group(1));

        // 默认是下铺（3），如果有明确标识则覆盖
        int position = 3; // 下=3, 中=2, 上=1
        if (seatNumber.contains("上")) position = 1;
        else if (seatNumber.contains("中")) position = 2;
        // 否则默认下铺

        // 格式：铺位号(2位) + 位置(1位) → 如 05上 → 051
        return Integer.parseInt(String.format("%02d%d", berthNum, position));
    }

    @Override
    public CarLayout carLayout() {
        return new CarLayout(15,6);
    }
}
