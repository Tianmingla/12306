package com.lalal.modules.user.dto;


import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String realName;
    private Integer idCardType;
    private String idCardNumber;
    private String phone;
    private String email;
    private Integer passengerType;
}
