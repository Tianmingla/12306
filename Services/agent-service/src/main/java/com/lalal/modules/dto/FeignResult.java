package com.lalal.modules.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Feign 调用通用返回包装
 * 与其他微服务的 Result<T> 结构对齐
 */
@Data
public class FeignResult implements Serializable {
    private Integer code;
    private String message;
    private Object data;
    private String requestId;

    public boolean isSuccess() {
        return code != null && code == 200;
    }
}
