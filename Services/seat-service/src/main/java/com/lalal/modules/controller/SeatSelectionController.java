package com.lalal.modules.controller;



import com.lalal.modules.dto.request.SeatSelectionRequestDTO;
import com.lalal.modules.dto.response.TicketDTO;
import com.lalal.modules.service.SeatSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seat")
@RequiredArgsConstructor
public class SeatSelectionController {

    private final SeatSelectionService seatSelectionService;

    @PostMapping("/select")
    public TicketDTO select(@RequestBody SeatSelectionRequestDTO request) {
        return seatSelectionService.select(request);
    }
}
