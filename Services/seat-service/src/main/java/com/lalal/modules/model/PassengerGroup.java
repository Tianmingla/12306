package com.lalal.modules.model;

import com.lalal.modules.enumType.train.SeatType;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class PassengerGroup {
    private final List<Passenger> members;
    private final SeatType requiredType;

    // 扩展属性：例如用户是否勾选了“必须相邻”
    private final boolean forceAdjacent;

    public PassengerGroup(List<Passenger> members, SeatType requiredType, boolean forceAdjacent) {
        this.members = members;
        this.requiredType = requiredType;
        this.forceAdjacent = forceAdjacent;
    }
    public static List<PassengerGroup> groupBySeatType(List<Passenger> members) {
        return members.stream()
                .collect(Collectors.groupingBy(
                        p -> SeatType.findByDesc(p.getSeatType()),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> new PassengerGroup(list, SeatType.findByDesc(list.get(0).getSeatType()), true)
                        )
                ))
                .values()
                .stream()
                .toList();
    }

    public void add(Passenger passenger){
        members.add(passenger);
    }
    /**
     * 核心能力：拆分逻辑
     * 如果整组无法分配，可能需要拆分为更小的组（例如 3人组拆为 2+1）
     */
    public List<PassengerGroup> splitToSubGroups() {
        List<PassengerGroup> subGroups = new ArrayList<>();
        // 简单的拆分逻辑：每个人一个独立组
        for (Passenger p : members) {
            subGroups.add(new PassengerGroup(List.of(p), requiredType, false));
        }
        return subGroups;
    }

    public int size() {
        return members.size();
    }
}