package com.lalal.modules.controller;

import com.lalal.modules.dto.response.TrainSearchResponseDTO;
import com.lalal.modules.result.Result;
import com.lalal.modules.service.TrainRoutePairService;
import com.lalal.modules.entity.TrainRoutePairDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/ticket")
public class TrainSearchController {
    @Autowired
    private TrainRoutePairService trainRoutePairService;

    @GetMapping("/search")
    public Result<List<TrainSearchResponseDTO>> searchTrains(@RequestParam String from,
                                                       @RequestParam(required = false) String mid,
                                                       @RequestParam String to,
                                                       @RequestParam String date) {
        List<TrainSearchResponseDTO> result = trainRoutePairService.searchTrains(from, mid, to, date);
        return Result.success(result);
    }
}
