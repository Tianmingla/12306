package com.lalal.modules.user.dto;

import lombok.Data;

@Data
public class LoginRequest {
    /**
     * 手机号
     */
    private String phone;
    /**
     * 短信验证码
     */
    private String smsCode;
}
