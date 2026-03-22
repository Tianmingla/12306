package com.lalal.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lalal.modules.user.dao.PassengerDO;
import com.lalal.modules.user.dto.PassengerSaveRequest;
import com.lalal.modules.user.dto.PassengerVO;
import com.lalal.modules.user.mapper.PassengerMapper;
import com.lalal.modules.user.service.PassengerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PassengerServiceImpl implements PassengerService {

    @Autowired
    private PassengerMapper passengerMapper;

    @Override
    public List<PassengerVO> listByUserId(Long userId) {
        LambdaQueryWrapper<PassengerDO> qw = new LambdaQueryWrapper<>();
        qw.eq(PassengerDO::getUserId, userId).eq(PassengerDO::getDelFlag, 0).orderByDesc(PassengerDO::getId);
        return passengerMapper.selectList(qw).stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    public List<PassengerVO> listByUserIdAndPassengerIdsOrdered(Long userId, List<Long> passengerIds) {
        if (passengerIds == null || passengerIds.isEmpty()) {
            throw new IllegalArgumentException("乘车人列表不能为空");
        }
        LambdaQueryWrapper<PassengerDO> qw = new LambdaQueryWrapper<>();
        qw.eq(PassengerDO::getUserId, userId).in(PassengerDO::getId, passengerIds).eq(PassengerDO::getDelFlag, 0);
        List<PassengerDO> rows = passengerMapper.selectList(qw);
        Map<Long, PassengerVO> map = rows.stream().collect(Collectors.toMap(PassengerDO::getId, this::toVo));
        List<PassengerVO> ordered = new ArrayList<>();
        for (Long id : passengerIds) {
            PassengerVO vo = map.get(id);
            if (vo == null) {
                throw new RuntimeException("乘车人不存在或不属于当前用户");
            }
            ordered.add(vo);
        }
        return ordered;
    }

    @Override
    public Long addPassenger(Long userId, PassengerSaveRequest request) {
        validateSaveRequest(request);
        LambdaQueryWrapper<PassengerDO> dup = new LambdaQueryWrapper<>();
        dup.eq(PassengerDO::getUserId, userId)
                .eq(PassengerDO::getIdCardNumber, request.getIdCardNumber().trim())
                .eq(PassengerDO::getDelFlag, 0);
        if (passengerMapper.selectCount(dup) > 0) {
            throw new RuntimeException("该证件号已添加为乘车人");
        }
        PassengerDO p = new PassengerDO();
        p.setUserId(userId);
        p.setRealName(request.getRealName().trim());
        p.setIdCardType(request.getIdCardType() != null ? request.getIdCardType() : 1);
        p.setIdCardNumber(request.getIdCardNumber().trim());
        p.setPassengerType(request.getPassengerType() != null ? request.getPassengerType() : 1);
        p.setPhone(StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null);
        p.setDelFlag(0);
        passengerMapper.insert(p);
        return p.getId();
    }

    @Override
    public void updatePassenger(Long userId, Long passengerId, PassengerSaveRequest request) {
        validateSaveRequest(request);
        PassengerDO existing = loadOwned(userId, passengerId);
        if (existing == null) {
            throw new RuntimeException("乘车人不存在");
        }
        LambdaQueryWrapper<PassengerDO> dup = new LambdaQueryWrapper<>();
        dup.eq(PassengerDO::getUserId, userId)
                .eq(PassengerDO::getIdCardNumber, request.getIdCardNumber().trim())
                .eq(PassengerDO::getDelFlag, 0)
                .ne(PassengerDO::getId, passengerId);
        if (passengerMapper.selectCount(dup) > 0) {
            throw new RuntimeException("该证件号已被其他乘车人使用");
        }
        existing.setRealName(request.getRealName().trim());
        existing.setIdCardType(request.getIdCardType() != null ? request.getIdCardType() : 1);
        existing.setIdCardNumber(request.getIdCardNumber().trim());
        existing.setPassengerType(request.getPassengerType() != null ? request.getPassengerType() : 1);
        existing.setPhone(StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null);
        passengerMapper.updateById(existing);
    }

    @Override
    public void deletePassenger(Long userId, Long passengerId) {
        PassengerDO existing = loadOwned(userId, passengerId);
        if (existing == null) {
            throw new RuntimeException("乘车人不存在");
        }
        existing.setDelFlag(1);
        passengerMapper.updateById(existing);
    }

    private PassengerDO loadOwned(Long userId, Long passengerId) {
        LambdaQueryWrapper<PassengerDO> qw = new LambdaQueryWrapper<>();
        qw.eq(PassengerDO::getId, passengerId).eq(PassengerDO::getUserId, userId).eq(PassengerDO::getDelFlag, 0);
        return passengerMapper.selectOne(qw);
    }

    private void validateSaveRequest(PassengerSaveRequest request) {
        if (request == null || !StringUtils.hasText(request.getRealName()) || !StringUtils.hasText(request.getIdCardNumber())) {
            throw new IllegalArgumentException("姓名与证件号不能为空");
        }
    }

    private PassengerVO toVo(PassengerDO p) {
        PassengerVO vo = new PassengerVO();
        vo.setId(p.getId());
        vo.setRealName(p.getRealName());
        vo.setIdCardType(p.getIdCardType());
        vo.setIdCardNumber(p.getIdCardNumber());
        vo.setPassengerType(p.getPassengerType());
        vo.setPhone(p.getPhone());
        return vo;
    }
}
