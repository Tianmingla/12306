package com.lalal.modules.user.service;

import com.lalal.modules.user.dto.PassengerSaveRequest;
import com.lalal.modules.user.dto.PassengerVO;

import java.util.List;

public interface PassengerService {

    List<PassengerVO> listByUserId(Long userId);

    Long addPassenger(Long userId, PassengerSaveRequest request);

    void updatePassenger(Long userId, Long passengerId, PassengerSaveRequest request);

    void deletePassenger(Long userId, Long passengerId);
}
