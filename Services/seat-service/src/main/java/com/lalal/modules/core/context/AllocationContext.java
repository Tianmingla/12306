package com.lalal.modules.core.context;

import com.lalal.modules.dto.request.SeatSelectionRequestDTO;
import com.lalal.modules.model.Passenger;
import com.lalal.modules.model.PassengerGroup;
import com.lalal.modules.model.SeatInventory;
import com.lalal.modules.model.SeatLocation;
import com.lalal.modules.model.Train;
import lombok.Data;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 1. 上下文：保存这一轮分配的所有状态
@Data
public class AllocationContext {

    private final SeatSelectionRequestDTO request;

    private final SeatInventory inventory;

    private final Train train;

    private int tryCount=1;



    // 结果容器：Passenger -> Location
    private Map<Passenger, SeatLocation> allocatedResult = new HashMap<>();

    public AllocationContext(SeatSelectionRequestDTO request, SeatInventory inventory, Train train) {
        this.request = request;
        this.inventory = inventory;
        this.train = train;
    }

    public void addMatch(Passenger p, int carIndex,String carNo, int seatIndex, String seatNo) {
        allocatedResult.put(p, new SeatLocation(carIndex,carNo, seatIndex, seatNo));
    }

    public boolean isFullyAllocated(List<Passenger> groupMembers) {
        return groupMembers.stream().allMatch(allocatedResult::containsKey);
    }

    public Map<Passenger, SeatLocation> getResult() { return allocatedResult; }
}