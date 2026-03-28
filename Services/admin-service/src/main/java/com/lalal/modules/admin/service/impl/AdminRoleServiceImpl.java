package com.lalal.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lalal.modules.admin.dao.PermissionDO;
import com.lalal.modules.admin.dao.RoleDO;
import com.lalal.modules.admin.dao.RolePermissionDO;
import com.lalal.modules.admin.dto.RoleDetailResponse;
import com.lalal.modules.admin.dto.RoleQueryRequest;
import com.lalal.modules.admin.dto.RoleSaveRequest;
import com.lalal.modules.admin.mapper.PermissionMapper;
import com.lalal.modules.admin.mapper.RoleMapper;
import com.lalal.modules.admin.mapper.RolePermissionMapper;
import com.lalal.modules.admin.service.AdminRoleService;
import com.lalal.modules.dto.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色管理服务实现
 */
@Service
public class AdminRoleServiceImpl implements AdminRoleService {

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Override
    public PageResult<RoleDO> listRoles(RoleQueryRequest request) {
        LambdaQueryWrapper<RoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleDO::getDelFlag, 0);

        // 角色名称搜索
        if (request.getRoleName() != null && !request.getRoleName().isEmpty()) {
            wrapper.like(RoleDO::getRoleName, request.getRoleName());
        }

        // 角色编码搜索
        if (request.getRoleCode() != null && !request.getRoleCode().isEmpty()) {
            wrapper.like(RoleDO::getRoleCode, request.getRoleCode());
        }

        // 状态筛选
        if (request.getStatus() != null) {
            wrapper.eq(RoleDO::getStatus, request.getStatus());
        }

        // 按ID降序
        wrapper.orderByDesc(RoleDO::getId);

        Page<RoleDO> page = new Page<>(request.getPageNum(), request.getPageSize());
        Page<RoleDO> result = roleMapper.selectPage(page, wrapper);

        return PageResult.ofPage(result.getRecords(), result.getTotal(), request.getPageNum(), request.getPageSize());
    }

    @Override
    public RoleDetailResponse getRoleDetail(Long id) {
        RoleDO role = roleMapper.selectById(id);
        if (role == null) {
            throw new RuntimeException("角色不存在");
        }

        RoleDetailResponse response = new RoleDetailResponse();
        response.setId(role.getId());
        response.setRoleName(role.getRoleName());
        response.setRoleCode(role.getRoleCode());
        response.setDescription(role.getDescription());
        response.setStatus(role.getStatus());

        // 获取角色权限ID列表
        List<Long> permissionIds = getRolePermissionIds(id);
        response.setPermissionIds(permissionIds);

        // 获取权限详情
        if (!permissionIds.isEmpty()) {
            List<PermissionDO> permissions = permissionMapper.selectBatchIds(permissionIds);
            List<RoleDetailResponse.PermissionVO> permissionVOs = permissions.stream()
                    .map(p -> {
                        RoleDetailResponse.PermissionVO vo = new RoleDetailResponse.PermissionVO();
                        vo.setId(p.getId());
                        vo.setPermissionName(p.getPermissionName());
                        vo.setPermissionCode(p.getPermissionCode());
                        vo.setResourceType(p.getResourceType());
                        vo.setParentId(p.getParentId());
                        return vo;
                    })
                    .collect(Collectors.toList());
            response.setPermissions(permissionVOs);
        }

        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createRole(RoleSaveRequest request) {
        // 检查角色编码是否已存在
        LambdaQueryWrapper<RoleDO> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(RoleDO::getRoleCode, request.getRoleCode())
                .eq(RoleDO::getDelFlag, 0);
        if (roleMapper.selectCount(existWrapper) > 0) {
            throw new RuntimeException("角色编码已存在");
        }

        RoleDO role = new RoleDO();
        role.setRoleName(request.getRoleName());
        role.setRoleCode(request.getRoleCode());
        role.setDescription(request.getDescription());
        role.setStatus(0);
        roleMapper.insert(role);

        // 分配权限
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            assignPermissions(role.getId(), request.getPermissionIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, RoleSaveRequest request) {
        RoleDO role = roleMapper.selectById(id);
        if (role == null) {
            throw new RuntimeException("角色不存在");
        }

        // 检查角色编码是否被其他角色使用
        if (!role.getRoleCode().equals(request.getRoleCode())) {
            LambdaQueryWrapper<RoleDO> existWrapper = new LambdaQueryWrapper<>();
            existWrapper.eq(RoleDO::getRoleCode, request.getRoleCode())
                    .eq(RoleDO::getDelFlag, 0)
                    .ne(RoleDO::getId, id);
            if (roleMapper.selectCount(existWrapper) > 0) {
                throw new RuntimeException("角色编码已存在");
            }
        }

        role.setRoleName(request.getRoleName());
        role.setRoleCode(request.getRoleCode());
        role.setDescription(request.getDescription());
        roleMapper.updateById(role);

        // 更新权限
        if (request.getPermissionIds() != null) {
            assignPermissions(id, request.getPermissionIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        RoleDO role = roleMapper.selectById(id);
        if (role == null) {
            throw new RuntimeException("角色不存在");
        }

        // 逻辑删除
        role.setDelFlag(1);
        roleMapper.updateById(role);

        // 删除角色权限关联
        LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermissionDO::getRoleId, id);
        rolePermissionMapper.delete(wrapper);
    }

    @Override
    public void updateRoleStatus(Long id, Integer status) {
        RoleDO role = roleMapper.selectById(id);
        if (role == null) {
            throw new RuntimeException("角色不存在");
        }

        role.setStatus(status);
        roleMapper.updateById(role);
    }

    @Override
    public List<PermissionDO> getAllPermissions() {
        LambdaQueryWrapper<PermissionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PermissionDO::getStatus, 0)
                .eq(PermissionDO::getDelFlag, 0)
                .orderByAsc(PermissionDO::getSortOrder);
        return permissionMapper.selectList(wrapper);
    }

    @Override
    public List<Long> getRolePermissionIds(Long roleId) {
        LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermissionDO::getRoleId, roleId);
        List<RolePermissionDO> rolePermissions = rolePermissionMapper.selectList(wrapper);
        return rolePermissions.stream()
                .map(RolePermissionDO::getPermissionId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        // 先删除原有权限
        LambdaQueryWrapper<RolePermissionDO> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(RolePermissionDO::getRoleId, roleId);
        rolePermissionMapper.delete(deleteWrapper);

        // 添加新权限
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permissionId : permissionIds) {
                RolePermissionDO rp = new RolePermissionDO();
                rp.setRoleId(roleId);
                rp.setPermissionId(permissionId);
                rolePermissionMapper.insert(rp);
            }
        }
    }
}
