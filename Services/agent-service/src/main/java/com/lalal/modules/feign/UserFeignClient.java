package com.lalal.modules.feign;

import com.lalal.modules.dto.FeignResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 用户服务 Feign 客户端
 */
@FeignClient(name = "user-service")
public interface UserFeignClient {

    /**
     * 查询当前用户的乘车人列表
     */
    @GetMapping("/api/user/passengers")
    FeignResult getPassengers(@RequestHeader("X-User-Id") String userId);
}
