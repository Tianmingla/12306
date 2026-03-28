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

        // 切换状态：0-正常, 1-禁用
        int newStatus = (user.getStatus() == null || user.getStatus() == 0) ? 1 : 0;

        LambdaUpdateWrapper<UserDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserDO::getId, id)
                .set(UserDO::getStatus, newStatus);
        userMapper.update(null, wrapper);
    }
}
