package com.lalal.modules.user.service;


import com.lalal.modules.user.dao.UserDO;
import com.lalal.modules.user.dto.LoginRequest;
import com.lalal.modules.user.dto.RegisterRequest;

public interface UserService {

    /**
     * 用户登录
     * @param loginRequest 登录请求参数
     * @return 登录成功的用户信息 (不含密码)，失败返回 null
     */
    UserDO login(LoginRequest loginRequest);

    /**
     * 用户注册
     * @param registerRequest 注册请求参数
     * @return 注册是否成功
     */
    boolean register(RegisterRequest registerRequest);

    /**
     * 根据ID查找用户
     * @param id 用户ID
     * @return 用户对象
     */
    UserDO findById(Long id);

    /**
     * 根据证件号码查找用户
     * @param idCardNumber 证件号码
     * @return 用户对象
     */
    UserDO findByIdCard(String idCardNumber);
}