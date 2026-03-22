package com.lalal.modules.user.dto;

import lombok.Data;

@Data
public class PassengerSaveRequest {
    private String realName;
    private Integer idCardType;
    private String idCardNumber;
    private Integer passengerType;
    private String phone;
}
