package com.lalal.modules.core.tool;

import com.lalal.modules.model.BooleanMask;

import java.util.List;

// 工具类
public class IntervalCombiner {
    public static BooleanMask combine(List<BooleanMask> masks,int start,int end) {
        BooleanMask result = new BooleanMask(masks.get(0).getSize());
        // 初始为全0（空闲）
        // 执行 OR 操作：只要有一个区间占用了，结果就是占用
        for(int i=start;i<=end;i++){
            result.or(masks.get(i));
        }
        return result;
    }
}