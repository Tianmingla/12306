package com.lalal.modules.core.seat;

import com.lalal.modules.model.CarLayout;

public interface Parser
{
    int index(String seatNumber);
    CarLayout carLayout();
}
