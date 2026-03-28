package com.lalal.modules.admin.service;

import com.lalal.modules.admin.dto.UserQueryRequest;
import com.lalal.modules.admin.dao.UserDO;
import com.lalal.modules.admin.dao.PassengerDO;
import com.lalal.modules.dto.PageResult;

import java.util.List;

public interface AdminUserService {

    /**
     * 分页查询用户列表
     */
    PageResult<UserDO> listUsers(UserQueryRequest request);

    /**
     * 查询用户的乘车人列表
     */
    List<PassengerDO> listPassengersByUserId(Long userId);

    /**
     * 切换用户状态（禁用/启用）
     */
    void toggleUserStatus(Long id);
}
