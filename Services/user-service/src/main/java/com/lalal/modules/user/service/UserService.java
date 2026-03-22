package com.lalal.modules.user.service;

import com.lalal.modules.user.dao.UserDO;
import com.lalal.modules.user.dto.LoginRequest;

public interface UserService {

    /**
     * 手机验证码登录：校验通过后若账号不存在则自动注册
     */
    UserDO loginBySms(LoginRequest loginRequest);

    UserDO findById(Long id);

    UserDO findByPhone(String phone);
}
