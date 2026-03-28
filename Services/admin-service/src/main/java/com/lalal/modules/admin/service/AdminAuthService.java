package com.lalal.modules.admin.service;

import com.lalal.modules.admin.dao.AdminUserDO;
import com.lalal.modules.admin.dto.AdminLoginRequest;
import com.lalal.modules.admin.dto.AdminLoginResponse;

public interface AdminAuthService {

    /**
     * 管理员登录
     */
    AdminLoginResponse login(AdminLoginRequest request);

    /**
     * 根据ID查询管理员
     */
    AdminUserDO findById(Long id);

    /**
     * 根据用户名查询管理员
     */
    AdminUserDO findByUsername(String username);
}
