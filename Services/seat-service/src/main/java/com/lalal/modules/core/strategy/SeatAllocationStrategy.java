package com.lalal.modules.core.strategy;

import com.lalal.modules.core.context.AllocationContext;
import com.lalal.modules.model.PassengerGroup;

import java.util.List;

// 2. 策略接口
public interface SeatAllocationStrategy {
    /**
     * 尝试分配
     * @param candidates 候选车厢索引列表 (由 Selector 提供)
     * @return 是否分配成功
     */
    boolean tryAllocate(AllocationContext ctx, PassengerGroup group, List<Integer> candidates);
}
