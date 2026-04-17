package com.lalal.modules.graph;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 时间展开图的节点
 * 节点 = (站点名, 时间戳, 是否为出发节点)
 *
 * 例如：
 * - 武汉@10:00-到达  表示到达武汉的时刻
 * - 武汉@10:30-出发  表示从武汉出发的时刻
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StationTimeNode implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 车站名称
     */
    private String station;

    /**
     * 时间（包含日期和时间）
     */
    private LocalDateTime time;

    /**
     * 是否为出发节点
     * true = 出发节点，false = 到达节点
     */
    private boolean isDeparture;

    /**
     * 节点唯一标识 key
     */
    public String getKey() {
        String type = isDeparture ? "D" : "A";
        return station + "@" + time.format(TIME_FORMAT) + "-" + type;
    }

    /**
     * 创建唯一 key
     */
    public static String makeKey(String station, LocalDateTime time, boolean isDeparture) {
        String type = isDeparture ? "D" : "A";
        return station + "@" + time.format(TIME_FORMAT) + "-" + type;
    }

    /**
     * 解析 key 获取站点名
     */
    public static String parseStation(String key) {
        return key.split("@")[0];
    }

    /**
     * 解析 key 获取时间
     */
    public static LocalDateTime parseTime(String key) {
        String[] parts = key.split("@")[1].split("-");
        return LocalDateTime.parse(parts[0], TIME_FORMAT);
    }

    /**
     * 解析 key 判断是否出发节点
     */
    public static boolean parseIsDeparture(String key) {
        String[] parts = key.split("@")[1].split("-");
        return "D".equals(parts[1]);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StationTimeNode that = (StationTimeNode) o;
        return Objects.equals(getKey(), that.getKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getKey());
    }

    @Override
    public String toString() {
        return getKey();
    }
}
