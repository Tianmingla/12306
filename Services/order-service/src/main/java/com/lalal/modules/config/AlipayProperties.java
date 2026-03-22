package com.lalal.modules.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付宝开放平台（沙箱）配置，见 application.yml 中 alipay 节点。
 */
@Data
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    /**
     * 是否启用真实调起支付宝；false 时支付接口返回提示信息，便于本地无密钥调试
     */
    private boolean enabled = false;

    private String gatewayUrl = "https://openapi.alipaydev.com/gateway.do";

    private String appId = "";

    /**
     * 应用私钥（PKCS8 文本，可含换行）
     */
    private String privateKey = "";

    /**
     * 支付宝公钥（非应用公钥）
     */
    private String alipayPublicKey = "";

    /**
     * 异步通知地址（需公网可达；本地开发可用内网穿透）
     */
    private String notifyUrl = "";

    /**
     * 同步跳转地址（用户支付完成后浏览器跳转）
     */
    private String returnUrl = "";

    /**
     * 支付完成后跳转的前端基址（用于服务端 redirect）
     */
    private String frontendBaseUrl = "http://localhost:5173";

    private String signType = "RSA2";
}
