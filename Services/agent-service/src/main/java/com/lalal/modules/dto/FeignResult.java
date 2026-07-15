package com.lalal.modules.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * Feign 调用通用返回包装
 * 与其他微服务的 Result<T> 结构对齐
 */
@Data
@Builder
public class FeignResult<T> implements Serializable {
    private Integer code;
    private String message;
    private T data;
    private String requestId;

    public static <T> FeignResult success(String s) {
        return FeignResult.builder()
                .message(s)
                .build();
    }
    public static <T> FeignResult success(T data) {
        return FeignResult.builder()
                .data(data)
                .build();
    }

    public boolean isSuccess() {
        return code != null && code == 200;
    }
}
