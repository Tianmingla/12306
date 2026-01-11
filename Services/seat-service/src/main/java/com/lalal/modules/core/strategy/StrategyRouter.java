package com.lalal.modules.core.strategy;


import com.lalal.modules.dto.request.SeatSelectionRequestDTO;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class StrategyRouter {

    private final AdjacentSeatsStrategy adjacentStrategy;
    private final AnyAvailableStrategy anyAvailableStrategy;

    public StrategyRouter(AdjacentSeatsStrategy adjacentStrategy, AnyAvailableStrategy anyAvailableStrategy) {
        this.adjacentStrategy = adjacentStrategy;
        this.anyAvailableStrategy = anyAvailableStrategy;
    }

    /**
     * 根据请求返回策略序列
     * 通常顺序：相邻策略 -> (降级) -> 散座策略
     */
    public List<SeatAllocationStrategy> getStrategies(SeatSelectionRequestDTO req) {
        List<SeatAllocationStrategy> strategies = new ArrayList<>();

        // 1. 如果乘客大于1人，优先尝试相邻分配
        if (req.getPassengers().size() > 1) {
            strategies.add(adjacentStrategy);
        }

        // 2. 无论如何，最后都要有一个兜底的散座策略
        strategies.add(anyAvailableStrategy);

        return strategies;
    }
}