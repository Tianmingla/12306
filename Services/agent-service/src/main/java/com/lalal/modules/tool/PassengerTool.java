package com.lalal.modules.tool;

import com.alibaba.fastjson2.JSON;
import com.lalal.modules.dto.FeignResult;
import com.lalal.modules.feign.UserFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 乘客管理工具
 * 查询当前用户的乘车人列表
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PassengerTool {

    private final UserFeignClient userFeignClient;
    private final ToolRegistry toolRegistry;

    @jakarta.annotation.PostConstruct
    public void init() {
        toolRegistry.register(this);
    }

    @Tool(description = "查询我的乘车人列表。返回当前用户已添加的所有乘车人信息，包括姓名、证件号、乘车人类型等。购票时需要使用乘车人ID。")
    public String queryMyPassengers(ToolContext toolContext) {
        log.info("Tool: queryMyPassengers");

        UserContext user = ToolContextHelper.getUserContext(toolContext);
        user.requireAuthenticated();

        try {
            FeignResult result = userFeignClient.getPassengers(user.getUserId());
            if (result.isSuccess()) {
                return JSON.toJSONString(result.getData());
            } else {
                return "查询乘车人列表失败：" + result.getMessage();
            }
        } catch (Exception e) {
            log.error("queryMyPassengers error", e);
            return "查询乘车人列表时发生错误";
        }
    }
}
