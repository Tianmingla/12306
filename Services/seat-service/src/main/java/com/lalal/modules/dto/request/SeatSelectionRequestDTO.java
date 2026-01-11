package com.lalal.modules.dto.request;

import com.lalal.modules.model.Passenger;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeatSelectionRequestDTO {
    /**
     * 购票账号
     */
    String account;

    List<Passenger> passengers;
    /**
     * 车次
     */
    String trainNum;
    /**
     * 开始站台
     */
    String startStation;
    /**
     * 到达站台
     */
    String endStation;
    /**
     * 购票日期
     */
    String date;
}
