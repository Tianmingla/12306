package com.lalal.modules.core.strategy;

import com.lalal.modules.core.context.AllocationContext;
import com.lalal.modules.model.BooleanMask;
import com.lalal.modules.model.Carriage;
import com.lalal.modules.model.Passenger;
import com.lalal.modules.model.PassengerGroup;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AnyAvailableStrategy implements SeatAllocationStrategy {
    @Override
    public boolean tryAllocate(AllocationContext ctx, PassengerGroup group, List<Integer> candidates) {
        List<Passenger> unassigned = group.getMembers().stream()
                .filter(p -> !ctx.getResult().containsKey(p))
                .collect(Collectors.toList());

        if (unassigned.isEmpty()) return true;

        for (Integer carIndex : candidates) {
            BooleanMask mask = ctx.getInventory().getMask(carIndex);
            List<Integer> freeIndices = mask.findAnyClearBits(unassigned.size());

            Carriage carriage = ctx.getTrain().getCarriage(carIndex);

            int assignedCount = 0;
            for (Integer seatIdx : freeIndices) {
                if (assignedCount >= unassigned.size()) break;

                Passenger p = unassigned.get(assignedCount);
                String seatNo = carriage.getLayout().getSeatNo(seatIdx);
                ctx.addMatch(p, carIndex, carriage.getCarNumber(), seatIdx, seatNo);
                assignedCount++;
            }

            // 如果分配完了，就退出
            if (ctx.isFullyAllocated(group.getMembers())) return true;

            // 否则更新待分配列表 (这里简化逻辑，实际上 ctx 已经记录了)
            unassigned = group.getMembers().stream()
                    .filter(p -> !ctx.getResult().containsKey(p))
                    .collect(Collectors.toList());
        }

        return ctx.isFullyAllocated(group.getMembers());
    }
}