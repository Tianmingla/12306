package com.lalal.modules.core.tool;

import com.lalal.modules.core.seat.SeatTypeParser;
import com.lalal.modules.enumType.train.SeatType;
import com.lalal.modules.model.CarLayout;

public class CarriageUtils {
    private static final SeatTypeParser parser=new SeatTypeParser();
    public static CarLayout getLayout(SeatType carriageType){
        return parser.carLayout(carriageType.getDesc());
    }
    public static int getIndex(SeatType carriageType,String seatNum){
        return parser.index(carriageType.getDesc(),seatNum);
    }
}
