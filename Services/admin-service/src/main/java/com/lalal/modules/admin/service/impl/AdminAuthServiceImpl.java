package com.lalal.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lalal.modules.admin.dao.AdminUserDO;
import com.lalal.modules.admin.dto.AdminLoginRequest;
import com.lalal.modules.admin.dto.AdminLoginResponse;
import com.lalal.modules.admin.mapper.AdminUserMapper;
import com.lalal.modules.admin.service.AdminAuthService;
import com.lalal.modules.admin.utils.AdminJwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private AdminJwtUtils adminJwtUtils;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public AdminLoginResponse login(AdminLoginRequest request) {
        // 查询用户
        AdminUserDO adminUser = findByUsername(request.getUsername());
        if (adminUser == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 检查状态
        if (adminUser.getStatus() != null && adminUser.getStatus() == 1) {
            throw new RuntimeException("账号已被禁用");
        }

        // 验证密码
//        System.out.printf(passwordEncoder.encode("123456"));
        if (!passwordEncoder.matches(request.getPassword(), adminUser.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 生成 Token
        String token = adminJwtUtils.generateToken(adminUser.getId(), adminUser.getUsername());

        // 构建响应
        AdminLoginResponse response = new AdminLoginResponse();
        response.setToken(token);
        response.setUserId(adminUser.getId());
        response.setUsername(adminUser.getUsername());
        response.setRealName(adminUser.getRealName());
        response.setRole(adminUser.getRole());

        return response;
    }

    @Override
    public AdminUserDO findById(Long id) {
        return adminUserMapper.selectById(id);
    }

    @Override
    public AdminUserDO findByUsername(String username) {
        LambdaQueryWrapper<AdminUserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdminUserDO::getUsername, username)
                .eq(AdminUserDO::getDelFlag, 0);
        return adminUserMapper.selectOne(wrapper);
    }
}
