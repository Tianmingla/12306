package com.lalal.modules.controller;

import com.lalal.modules.entity.TrainStationDO;
import com.lalal.modules.result.Result;
import com.lalal.modules.service.TrainStationService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/trainDetail")
public class TrainDetailController {
    TrainStationService trainStationService;

    /**
     * 本项目设计原则：
     * 接口设计原则 用唯一code或其他容易人类识别做接口
     * 缓存设计原则 用id做唯一缓存键
     * @param trainNum 车次
     * @return 车次经过的站台名
     * */
    @GetMapping("/stations")
    public Result<List<String>> getStationNamesByTrainNum(@RequestParam String trainNum){
        return Result.success(trainStationService.getStationNamesByTrainNum(trainNum));
    }

}
