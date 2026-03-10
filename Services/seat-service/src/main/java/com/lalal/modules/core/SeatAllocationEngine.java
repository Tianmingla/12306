package com.lalal.modules.core;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.core.context.AllocationContext;
import com.lalal.modules.core.selector.CarriageSelector;
import com.lalal.modules.core.strategy.SeatAllocationStrategy;
import com.lalal.modules.core.strategy.StrategyRouter;
import com.lalal.modules.dao.CarriageDO;
import com.lalal.modules.dao.TrainDO;
import com.lalal.modules.dao.TrainStationDO;
import com.lalal.modules.dto.request.SeatSelectionRequestDTO;
import com.lalal.modules.model.*;
import com.lalal.modules.model.PassengerGroup;
import com.lalal.modules.dto.response.TicketDTO;
import com.lalal.modules.mapper.*;

import com.lalal.modules.service.InventoryService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class SeatAllocationEngine {


    private InventoryService inventoryService;

    private StrategyRouter strategyRouter;

    private CarriageSelector carriageSelector;

    private SafeCacheTemplate safeCacheTemplate;

    TrainMapper trainMapper;
    CarriageMapper carriageMapper;
    TicketMapper ticketMapper;
    SeatMapper seatMapper;
    TrainStationMapper trainStationMapper;

    public TicketDTO execute(SeatSelectionRequestDTO request) {
        // 1. 构建物理列车模型 (通常缓存)
        String trainNum=request.getTrainNum();
        String date=request.getDate();
        Long trainId=safeCacheTemplate.safeGet(
                CacheConstant.trainCodeToDetail(trainNum),
                ()->{
                    LambdaQueryWrapper<TrainDO> lambdaQueryWrapper=new LambdaQueryWrapper<>();
                    lambdaQueryWrapper.eq(TrainDO::getTrainNumber,trainNum);
                    return trainMapper.selectOne(lambdaQueryWrapper);
                },
                new TypeReference<TrainDO>(){},
                10,
                TimeUnit.DAYS
        ).getId();
        List<CarriageDO> carriages=safeCacheTemplate.safeGet(
                CacheConstant.trainCarriage(trainId),
                ()->{
                    LambdaQueryWrapper<CarriageDO> lambdaQueryWrapper=new LambdaQueryWrapper<>();
                    lambdaQueryWrapper
                            .select(CarriageDO::getCarriageNumber,CarriageDO::getCarriageType)
                            .eq(CarriageDO::getTrainId,trainId);
                    List<CarriageDO> carriageDOS=carriageMapper.selectList(lambdaQueryWrapper);
                    return new ArrayList<>(carriageDOS);
                },
                new TypeReference<List<CarriageDO>>(){},
                10,
                TimeUnit.DAYS
        );

        List<String> stationList=safeCacheTemplate.safeGet(
                CacheConstant.trainStation(trainId),
                ()->{
                    LambdaQueryWrapper<TrainStationDO> lambdaQueryWrapper=new LambdaQueryWrapper<>();
                    lambdaQueryWrapper
                            .select(TrainStationDO::getStationName)
                            .eq(TrainStationDO::getTrainId,trainId)
                            .orderByAsc(TrainStationDO::getSequence);
                    return trainStationMapper.selectObjs(lambdaQueryWrapper)
                            .stream()
                            .map(s->(String)s)
                            .collect(Collectors.toCollection(ArrayList::new));
                },
                new TypeReference<List<String>>(){},
                10,
                TimeUnit.DAYS
        );
        Train train = new Train(
                trainId,
                trainNum,
                carriages.stream()
                        .map(CarriageDO::toCarriage)
                        .toList(),
                stationList
                );

        // 2. 加载并聚合库存 (从 Redis 获取位图并合并)
        SeatInventory inventory = inventoryService.loadInventory(
                train,
                request.getDate(),
                request.getStartStation(),
                request.getEndStation()
        );

        // 3. 构建乘客组 (处理拆单逻辑可以在这里做，或者在策略里做)
        List<PassengerGroup> groups=PassengerGroup.groupBySeatType(request.getPassengers());
        // 4. 初始化上下文
        AllocationContext ctx = new AllocationContext(request, inventory, train);
        for(PassengerGroup mainGroup:groups) {
            boolean flag=false;
            ctx.setPassengerGroup(mainGroup);
            while(!flag) {
                // 5. 获取策略链 (例如：Adjacent -> SameCarriage -> Any)
                List<SeatAllocationStrategy> strategies = strategyRouter.getStrategies(request);

                // 6. 选车厢 (负载均衡)
                List<Integer> candidateCarriages = carriageSelector.selectCandidates(
                        train, mainGroup.getRequiredType(), request.getPassengers().size()
                );

                // 7. 执行策略
                boolean success = false;
                for (SeatAllocationStrategy strategy : strategies) {
                    // 如果是 Adjacent 策略，必须一次性满足；如果是 Any 策略，可以部分满足
                    if (strategy.tryAllocate(ctx, mainGroup, candidateCarriages)) {
                        success = true;
                        break;
                    }
                }

                if (!success) {
                    throw new RuntimeException("余票不足");
                }

                // 8. 最终构建 TicketDTO 并调用 Redis 扣减
                // 注意：这里需要将 ctx.getResult() 转化为扣减指令
                flag=inventoryService.commit(ctx);

            }
        }

        return buildTicketDTO(ctx);
    }

    private TicketDTO buildTicketDTO(AllocationContext ctx) {
        SeatSelectionRequestDTO request = ctx.getRequest();
        Map<Passenger, SeatLocation> resultMap = ctx.getResult();

        List<TicketDTO.TicketItem> items = new ArrayList<>();

        // 遍历分配结果，将 Passenger 和 SeatLocation 转换为 TicketItem
        for (Map.Entry<Passenger, SeatLocation> entry : resultMap.entrySet()) {
            Passenger passenger = entry.getKey();
            SeatLocation location = entry.getValue();

            // 获取物理车厢信息
            Carriage carriage = ctx.getTrain().getCarriage(location.getCarIndex());

            TicketDTO.TicketItem item = new TicketDTO.TicketItem(passenger.getId(), location.getSeatNo(), carriage.getSeatType().getCode(), location.getCarNo());

            items.add(item);
        }

        // 构建最终返回给前端的 DTO
        TicketDTO ticketDTO = new TicketDTO();
        ticketDTO.setTrainNum(ctx.getTrain().getTrainNum());
        ticketDTO.setEndStation(ctx.getRequest().getEndStation());
        ticketDTO.setStartStation(ctx.getRequest().getStartStation());
        ticketDTO.setAccount(ctx.getRequest().getAccount());
        ticketDTO.setDate(ctx.getRequest().getDate());
        ticketDTO.setItems(items);

        return ticketDTO;
    }
}