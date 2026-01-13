package com.lalal.modules.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.lalal.modules.model.Serialization.BooleanMaskDeserializer;
import com.lalal.modules.model.Serialization.BooleanMaskSerializer;
import lombok.Getter;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

//放弃json自定义序列化 因为不管如何控制 他都必须是字符串或json在redis会存在/"/"双引号 虽然可在lua规避 但终究不够好 我需要更加自由的序列化处理包装
//@JsonSerialize(using = BooleanMaskSerializer.class)
//@JsonDeserialize(using = BooleanMaskDeserializer.class)
public class BooleanMask {

    private final BitSet bits;
    @Getter
    private final int size;

    public BooleanMask(int size) {
        this.size = size;
        this.bits = new BitSet(size);
    }


    public BooleanMask(  int size,
                        BitSet bits) {
        this.size = size;
        this.bits = bits;
    }
    public BooleanMask(byte[] bytes) {
        bits= BitSet.valueOf(bytes);
        size= bits.size();
    }
    public byte[] getBytes(){
        return bits.toByteArray();
    }
    /**
     * 合并操作 (OR)
     * 场景：区间A占用 OR 区间B占用 = 最终占用
     */
    public void or(BooleanMask other) {
        this.bits.or(other.bits);
    }

    public boolean get(int index) {
        return bits.get(index);
    }

    public void set(int index, boolean value) {
        bits.set(index, value);
    }

    /**
     * 核心算法：查找连续 k 个空位 (值为0)
     * @param k 需要的连续座位数
     * @return 连续座位的起始索引列表 (即第一个座位的 index)，没找到返回空列表
     */
    public List<Integer> findContinuousClearBits(int k) {
        List<Integer> result = new ArrayList<>();
        // 简单滑动窗口，BitSet 有 nextSetBit 优化，但在密集查找中，直接遍历也很快
        // 这里使用 nextSetBit 优化跳跃
        int i = 0;
        while (i <= size - k) {
            // 找到 i 之后第一个被占用(true)的位置
            int nextOccupied = bits.nextSetBit(i);

            if (nextOccupied == -1) {
                // i 之后全是空的
                result.add(i);
                // 既然找到了，为了演示简单我们返回第一个，或者继续找所有的
                return result;
            }

            // 如果中间空闲的长度 >= k，说明找到了
            if (nextOccupied - i >= k) {
                result.add(i);
                return result; // 贪心策略：找到一组就返回
            } else {
                // 否则跳过这个占用块
                i = nextOccupied + 1;
            }
        }
        return result;
    }

    // 查找任意 k 个空位
    public List<Integer> findAnyClearBits(int k) {
        List<Integer> result = new ArrayList<>();
        int current = 0;
        while (result.size() < k && current < size) {
            int nextClear = bits.nextClearBit(current);
            if (nextClear >= size) break;
            result.add(nextClear);
            current = nextClear + 1;
        }
        return result;
    }
}

