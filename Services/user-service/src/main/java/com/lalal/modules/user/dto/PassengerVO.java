package com.lalal.modules.user.dto;

import lombok.Data;

@Data
public class PassengerVO {
    private Long id;
    private String realName;
    private Integer idCardType;
    private String idCardNumber;
    private Integer passengerType;
    private String phone;
}
