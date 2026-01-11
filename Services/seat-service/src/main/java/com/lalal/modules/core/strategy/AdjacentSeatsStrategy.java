package com.lalal.modules.core.strategy;

import com.lalal.modules.core.context.AllocationContext;
import com.lalal.modules.model.BooleanMask;
import com.lalal.modules.model.Carriage;
import com.lalal.modules.model.Passenger;
import com.lalal.modules.model.PassengerGroup;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdjacentSeatsStrategy implements SeatAllocationStrategy {

    @Override
    public boolean tryAllocate(AllocationContext ctx, PassengerGroup group, List<Integer> candidates) {
        int requiredCount = group.getMembers().size();

        for (Integer carIndex : candidates) {
            BooleanMask mask = ctx.getInventory().getMask(carIndex);

            // 1. 核心逻辑：在位图中找连续的0
            List<Integer> startIndices = mask.findContinuousClearBits(requiredCount);

            if (!startIndices.isEmpty()) {
                // 找到了！取第一个方案
                int startSeatIndex = startIndices.get(0);
                Carriage carriage = ctx.getTrain().getCarriage(carIndex);

                // 2. 映射并保存结果
                for (int i = 0; i < requiredCount; i++) {
                    int actualSeatIdx = startSeatIndex + i;
                    Passenger p = group.getMembers().get(i);
                    String seatNo = carriage.getLayout().getSeatNo(actualSeatIdx);

                    ctx.addMatch(p, carIndex, carriage.getCarNumber(), actualSeatIdx, seatNo);
                }
                return true; // 成功
            }
        }
        return false; // 所有候选车厢都无法满足相邻
    }
}