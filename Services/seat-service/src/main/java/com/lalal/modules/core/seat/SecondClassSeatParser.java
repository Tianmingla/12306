package com.lalal.modules.core.seat;

import com.lalal.modules.model.CarLayout;

import java.util.HashMap;
import java.util.Map;

public class SecondClassSeatParser implements Parser{
    private final Map<String,String> map=Map.of(
            "A","1",
            "B","2",
            "C","3",
            "D","4",
            "F","5"
    );
    @Override
    public int index(String seatNumber) {
        String index=seatNumber.substring(0,2)+map.get(seatNumber.substring(2));
        return Integer.parseInt(index);
    }

    @Override
    public CarLayout carLayout() {
        return new CarLayout(15,5);
    }
}
