package com.lalal.modules.service;

import com.lalal.modules.dto.request.SeatSelectionRequestDTO;
import com.lalal.modules.dto.response.TicketDTO;


public interface SeatSelectionService {

    TicketDTO select(SeatSelectionRequestDTO seatSelectionRequest);

}
