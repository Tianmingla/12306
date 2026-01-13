package com.lalal.modules.controller;

import com.lalal.modules.core.SeatAllocationEngine;

import com.lalal.modules.dto.response.TicketDTO;
import com.lalal.modules.model.Passenger;
import com.lalal.modules.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lalal.modules.dto.request.SeatSelectionRequestDTO;

import java.util.Collections;

@RestController
public class Test {
    @Autowired
    SeatAllocationEngine seatAllocationEngine;
    @GetMapping("/test")
    public Result<TicketDTO> testSelectSeat(){
        SeatSelectionRequestDTO requestDTO=new SeatSelectionRequestDTO();
        requestDTO.setEndStation("张家界西");
        requestDTO.setStartStation("长沙");
        requestDTO.setDate("2025-12-21");
        requestDTO.setTrainNum("D7452");

        Passenger p= Passenger.builder().id(111L).seatPreference("F").seatType("二等座").build();

        requestDTO.setPassengers(Collections.singletonList(p));

        return Result.success(seatAllocationEngine.execute(requestDTO));
    }
}
