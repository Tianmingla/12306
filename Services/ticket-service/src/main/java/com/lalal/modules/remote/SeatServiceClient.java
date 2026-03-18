package com.lalal.modules.remote;


import com.lalal.modules.dto.SeatSelectionRequestDTO;
import com.lalal.modules.dto.TicketDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "seat-service", url = "${remote.seat-service.url:http://seat-service:8083}")
@Service
public interface SeatServiceClient {
    @PostMapping("/api/seat/select")
    TicketDTO select(@RequestBody SeatSelectionRequestDTO request);
}
