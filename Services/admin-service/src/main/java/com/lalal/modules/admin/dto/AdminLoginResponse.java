package com.lalal.modules.admin.dto;

import lombok.Data;

@Data
public class AdminLoginResponse {

    private String token;

    private Long userId;

    private String username;

    private String realName;

    private String role;
}
