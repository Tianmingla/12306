package com.lalal.modules.user.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.user.mapper.UserMapper;
import com.lalal.modules.user.service.UserService;



import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lalal.modules.user.dao.UserDO;

import com.lalal.modules.user.dto.LoginRequest;
import com.lalal.modules.user.dto.RegisterRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.lang.ref.Reference;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private SafeCacheTemplate safeCacheTemplate;

    // 实际生产中建议使用随机盐值，这里为了演示简单使用固定盐，或者你可以将盐存在数据库中
    // 更好的做法是：注册时生成随机盐，存入数据库；登录时取出盐再加密比对。
    @Value("${salt}")
    private static String SALT;

    /**
     * MD5 加密工具方法
     */
    private String encryptPassword(String password) {
        if (!StringUtils.hasText(password)) {
            return null;
        }
        // 拼接盐值后进行 MD5 加密
        String saltedPassword = password + SALT;
        return DigestUtils.md5DigestAsHex(saltedPassword.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public UserDO login(LoginRequest loginRequest) {
        if (loginRequest == null || !StringUtils.hasText(loginRequest.getUsername()) || !StringUtils.hasText(loginRequest.getPassword())) {
            return null;
        }

        UserDO user = safeCacheTemplate.safeGet(
                CacheConstant.userDetailByName(loginRequest.getUsername()),
                ()->{
                    // 1. 根据用户名查询用户
                    LambdaQueryWrapper<UserDO> queryWrapper = new LambdaQueryWrapper<>();
                    queryWrapper.eq(UserDO::getUsername, loginRequest.getUsername());
                    return userMapper.selectOne(queryWrapper);
                },
                new TypeReference<UserDO>(){},
                2,
                TimeUnit.HOURS
        );


        if (user == null) {
            return null;
        }

        // 2. 验证密码
        // 将前端传来的密码进行同样的加密处理，然后与数据库中的密文比对
        String encryptedInputPassword = encryptPassword(loginRequest.getPassword());

        if (encryptedInputPassword != null && encryptedInputPassword.equals(user.getPassword())) {
            // 登录成功，为了安全，返回前将密码字段置空
            user.setPassword(null);
            return user;
        }

        return null;
    }

    @Override
    public boolean register(RegisterRequest registerRequest) {
        // TODO 用户名禁止为纯数字 或身份证格式
        if (registerRequest == null || !StringUtils.hasText(registerRequest.getUsername()) || !StringUtils.hasText(registerRequest.getPassword())) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }

        // 1. 检查用户名是否已存在
        UserDO user = safeCacheTemplate.safeGet(
                CacheConstant.userDetailByName(registerRequest.getUsername()),
                ()->{
                    // 1. 根据用户名查询用户
                    LambdaQueryWrapper<UserDO> queryWrapper = new LambdaQueryWrapper<>();
                    queryWrapper.eq(UserDO::getUsername, registerRequest.getUsername());
                    return userMapper.selectOne(queryWrapper);
                },
                new TypeReference<UserDO>(){},
                2,
                TimeUnit.HOURS
        );
        if (user!=null) {
            throw new RuntimeException("用户名已存在");
        }

        // 2. 如果提供了证件号，检查证件号是否已存在
        if (StringUtils.hasText(registerRequest.getIdCardNumber())) {
             user= findByIdCard(registerRequest.getIdCardNumber());
            if (user!=null) {
                throw new RuntimeException("该证件号码已被注册");
            }
        }

        // 3. 构建用户对象
        UserDO newUser = new UserDO();
        newUser.setUsername(registerRequest.getUsername());
        // 加密密码
        newUser.setPassword(encryptPassword(registerRequest.getPassword()));
        newUser.setRealName(registerRequest.getRealName());
        newUser.setIdCardType(registerRequest.getIdCardType());
        newUser.setIdCardNumber(registerRequest.getIdCardNumber());
        newUser.setPhone(registerRequest.getPhone());
        newUser.setEmail(registerRequest.getEmail());
        newUser.setPassengerType(registerRequest.getPassengerType());

        // 4. 插入数据库 TODO 异步？
        int result = userMapper.insert(newUser);
        return result > 0;
    }

    @Override
    public UserDO findById(Long id) {
        if (id == null) {
            return null;
        }
        UserDO user = safeCacheTemplate.safeGet(
                CacheConstant.userDetailById(id),
                ()-> userMapper.selectById(id),
                new TypeReference<UserDO>(){},
                2,
                TimeUnit.HOURS
        );
        if (user != null) {
            user.setPassword(null); // 不返回密码
        }
        return user;
    }

    @Override
    public UserDO findByIdCard(String idCardNumber) {
        if (!StringUtils.hasText(idCardNumber)) {
            return null;
        }
        LambdaQueryWrapper<UserDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserDO::getIdCardNumber, idCardNumber);
        UserDO user = safeCacheTemplate.safeGet(
                CacheConstant.userDetailByIdCard(idCardNumber),
                ()->{
                    // 1. 根据用户名查询用户
                    LambdaQueryWrapper<UserDO> checkIdCardWrapper = new LambdaQueryWrapper<>();
                    checkIdCardWrapper.eq(UserDO::getIdCardNumber, idCardNumber);
                    return userMapper.selectOne(checkIdCardWrapper);
                },
                new TypeReference<UserDO>(){},
                2,
                TimeUnit.HOURS
        );

        if (user != null) {
            user.setPassword(null); // 不返回密码
        }
        return user;
    }
}