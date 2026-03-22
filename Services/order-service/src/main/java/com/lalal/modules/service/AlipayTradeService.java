package com.lalal.modules.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.lalal.modules.config.AlipayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AlipayTradeService {

    private final AlipayProperties alipayProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String buildPagePayForm(String outTradeNo, BigDecimal totalAmount, String subject) throws AlipayApiException {
        if (!alipayProperties.isEnabled()) {
            throw new IllegalStateException("未启用支付宝（alipay.enabled=false）");
        }
        if (isBlank(alipayProperties.getAppId()) || isBlank(alipayProperties.getPrivateKey())
                || isBlank(alipayProperties.getAlipayPublicKey())) {
            throw new IllegalStateException("支付宝 app-id / 密钥未配置完整");
        }

        AlipayClient client = new DefaultAlipayClient(
                alipayProperties.getGatewayUrl(),
                alipayProperties.getAppId(),
                alipayProperties.getPrivateKey().trim(),
                "json",
                StandardCharsets.UTF_8.name(),
                alipayProperties.getAlipayPublicKey().trim(),
                alipayProperties.getSignType()
        );

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        if (!isBlank(alipayProperties.getNotifyUrl())) {
            request.setNotifyUrl(alipayProperties.getNotifyUrl().trim());
        }
        if (!isBlank(alipayProperties.getReturnUrl())) {
            request.setReturnUrl(alipayProperties.getReturnUrl().trim());
        }

        ObjectNode biz = objectMapper.createObjectNode();
        biz.put("out_trade_no", outTradeNo);
        biz.put("total_amount", totalAmount.stripTrailingZeros().toPlainString());
        biz.put("subject", subject);
        biz.put("product_code", "FAST_INSTANT_TRADE_PAY");
        request.setBizContent(biz.toString());

        return client.pageExecute(request).getBody();
    }

    public boolean verifyNotify(Map<String, String> params) throws AlipayApiException {
        if (!alipayProperties.isEnabled()) {
            return false;
        }
        return AlipaySignature.rsaCheckV1(
                params,
                alipayProperties.getAlipayPublicKey().trim(),
                StandardCharsets.UTF_8.name(),
                alipayProperties.getSignType()
        );
    }

    public boolean verifyReturn(Map<String, String> params) throws AlipayApiException {
        return verifyNotify(params);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
