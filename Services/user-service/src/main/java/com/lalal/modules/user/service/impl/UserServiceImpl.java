package com.lalal.modules.user.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.user.dao.UserDO;
import com.lalal.modules.user.dto.LoginRequest;
import com.lalal.modules.user.mapper.UserMapper;
import com.lalal.modules.user.service.SmsCodeService;
import com.lalal.modules.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private SafeCacheTemplate safeCacheTemplate;
    @Autowired
    private SmsCodeService smsCodeService;

    @Override
    public UserDO loginBySms(LoginRequest loginRequest) {
        if (loginRequest == null || !StringUtils.hasText(loginRequest.getPhone()) || !StringUtils.hasText(loginRequest.getSmsCode())) {
            return null;
        }
        String phone = loginRequest.getPhone().trim();
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            return null;
        }
        if (!smsCodeService.verifyAndConsumeLoginCode(phone, loginRequest.getSmsCode())) {
            return null;
        }

        UserDO user = findByPhoneFromDb(phone);
        if (user != null) {
            return user;
        }

        UserDO created = new UserDO();
        created.setPhone(phone);
        created.setDelFlag(0);
        userMapper.insert(created);
        safeCacheTemplate.del(CacheConstant.userDetailByPhone(phone));
        return findById(created.getId());
    }

    private UserDO findByPhoneFromDb(String phone) {
        return safeCacheTemplate.safeGet(
                CacheConstant.userDetailByPhone(phone),
                () -> {
                    LambdaQueryWrapper<UserDO> qw = new LambdaQueryWrapper<>();
                    qw.eq(UserDO::getPhone, phone).eq(UserDO::getDelFlag, 0);
                    return userMapper.selectOne(qw);
                },
                new TypeReference<UserDO>() {},
                2,
                TimeUnit.HOURS
        );
    }

    @Override
    public UserDO findById(Long id) {
        if (id == null) {
            return null;
        }
        return safeCacheTemplate.safeGet(
                CacheConstant.userDetailById(id),
                () -> userMapper.selectById(id),
                new TypeReference<UserDO>() {},
                2,
                TimeUnit.HOURS
        );
    }

    @Override
    public UserDO findByPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String p = phone.trim();
        return safeCacheTemplate.safeGet(
                CacheConstant.userDetailByPhone(p),
                () -> findByPhoneFromDb(p),
                new TypeReference<UserDO>() {},
                2,
                TimeUnit.HOURS
        );
    }
}
