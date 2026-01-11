package com.lalal.modules.core.selector;

import com.lalal.modules.enumType.train.SeatType;
import com.lalal.modules.model.Carriage;
import com.lalal.modules.model.Train;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class RandomCarriageSelector implements CarriageSelector{
    @Override
    public List<Integer> selectCandidates(Train train, SeatType type, int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }

        List<Carriage> carriages = train.getCarriages();

        // 提取所有符合条件的车厢的 index（业务索引）
        List<Integer> eligibleCarriageIndices = IntStream.range(0, carriages.size())
                .filter(i -> carriages.get(i).getSeatType().equals(type))
                .mapToObj(i -> carriages.get(i).getIndex()) // 直接转为业务索引
                .collect(Collectors.toList());

        if (eligibleCarriageIndices.isEmpty()) {
            return Collections.emptyList();
        }

        // 随机打乱
        Collections.shuffle(eligibleCarriageIndices, new Random());

        // 截取前 count 个
        return eligibleCarriageIndices.size() <= count
                ? eligibleCarriageIndices
                : eligibleCarriageIndices.subList(0, count);
    }
}
