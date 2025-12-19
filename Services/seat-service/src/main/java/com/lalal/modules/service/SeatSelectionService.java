package com.lalal.modules.service;

import com.lalal.modules.dao.request.SeatSelectionRequestDTO;
import com.lalal.modules.dao.response.TicketDTO;
import org.springframework.stereotype.Service;

@Service
public interface SeatSelectionService {

    TicketDTO select(SeatSelectionRequestDTO seatSelectionRequest);

}
