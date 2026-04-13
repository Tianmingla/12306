package com.lalal.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 候补兑现消息
 *
 * <p>当发生退票或新增座位时，发送此消息触发候补兑现流程。
 *
 * @author Claude
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WaitlistFulfillMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 车次号
     */
    private String trainNumber;

    /**
     * 乘车日期 yyyy-MM-dd
     */
    private LocalDate travelDate;

    /**
     * 座位类型（可选，为null时表示所有类型）
     */
    private Integer seatType;

    /**
     * 触发来源：REFUND（退票）/ NEW_SEAT（新加座位）
     */
    private String source;

    /**
     * 时间戳
     */
    private Long timestamp;
}
