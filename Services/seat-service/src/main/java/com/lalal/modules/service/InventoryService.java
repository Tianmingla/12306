package com.lalal.modules.service;


import com.lalal.modules.core.context.AllocationContext;
import com.lalal.modules.model.SeatInventory;
import com.lalal.modules.model.SeatLocation;
import com.lalal.modules.model.Train;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

public interface  InventoryService{
    //从Redis加载原始区间位图并合并
    SeatInventory loadInventory(Train train, String date, String start, String end);
    //最终扣减库存（Lua脚本，乐观锁）
    boolean commit(AllocationContext ctx);
    //lua脚本选座 最终降级 返回座位索引
    int selectSeat(AllocationContext ctx);

}