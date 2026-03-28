package com.lalal.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lalal.modules.admin.dao.PassengerDO;
import com.lalal.modules.admin.dao.UserDO;
import com.lalal.modules.admin.dto.UserQueryRequest;
import com.lalal.modules.admin.mapper.PassengerMapper;
import com.lalal.modules.admin.mapper.UserMapper;
import com.lalal.modules.admin.service.AdminUserService;
import com.lalal.modules.dto.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PassengerMapper passengerMapper;

    @Override
    public PageResult<UserDO> listUsers(UserQueryRequest request) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();

        // 游标分页：ID > lastId
        if (request.getLastId() != null && request.getLastId() > 0) {
            wrapper.gt(UserDO::getId, request.getLastId());
        }

        // 关键字搜索（手机号）
        if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
            wrapper.like(UserDO::getPhone, request.getKeyword());
        }

        // 逻辑删除过滤
        wrapper.eq(UserDO::getDelFlag, 0);

        // 按ID升序
        wrapper.orderByAsc(UserDO::getId);

        // 查询 pageSize + 1 条，判断是否有更多
        int limit = request.getPageSize() + 1;
        wrapper.last("LIMIT " + limit);

        List<UserDO> list = userMapper.selectList(wrapper);

        boolean hasMore = list.size() > request.getPageSize();
        if (hasMore) {
            list = list.subList(0, request.getPageSize());
        }

        Long nextId = list.isEmpty() ? null : list.get(list.size() - 1).getId();

        // 统计总数（可选，大数据量时可能不精确）
        Long total = null;

        return PageResult.of(list, total, nextId, hasMore);
    }

    @Override
    public List<PassengerDO> listPassengersByUserId(Long userId) {
        LambdaQueryWrapper<PassengerDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PassengerDO::getUserId, userId)
                .eq(PassengerDO::getDelFlag, 0);
        return passengerMapper.selectList(wrapper);
    }

    @Override
    public void toggleUserStatus(Long id) {
        UserDO user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 这里假设用 del_flag 作为状态标志（实际应该有独立 status 字段）
        // 由于 t_user 表没有 status 字段，这里仅作演示
        throw new RuntimeException("用户表无状态字段，请在 t_user 表添加 status 字段");
    }
}
