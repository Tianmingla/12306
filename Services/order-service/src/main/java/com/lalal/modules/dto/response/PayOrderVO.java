package com.lalal.modules.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前端将 payFormHtml 插入页面并自动提交表单以跳转支付宝收银台
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayOrderVO {
    private String orderSn;
    /**
     * 支付宝返回的完整 HTML 表单；为空时表示未调起（如未配置沙箱）
     */
    private String payFormHtml;
    private String hint;
}
