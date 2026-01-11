package com.lalal.modules.core.seat;

import com.lalal.modules.enumType.train.SeatType;
import com.lalal.modules.model.CarLayout;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SeatTypeParser {
    private final Map<String,Parser> map=Map.of(
            SeatType.SECOND_CLASS.getDesc(),new SecondClassSeatParser(),
            SeatType.FIRST_CLASS.getDesc(),new FirstClassSeatParser(),
            SeatType.BUSINESS.getDesc(),new BusinessClassSeatParser(),
            SeatType.HARD_SLEEPER.getDesc(),new SleeperSeatParser(),
            SeatType.SOFT_SLEEPER.getDesc(),new SleeperSeatParser(),
            SeatType.HARD_SEAT.getDesc(),new HardSeatParser()
    );
    public int index(String seatType, String seatNumber){
        return map.get(seatType).index(seatNumber);
    }
    public CarLayout carLayout(String seatType){
        return map.get(seatType).carLayout();
    }
}
