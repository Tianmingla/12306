package com.lalal.modules.user.service;

public interface SmsCodeService {

    /**
     * 发送登录验证码（写入 Redis，真实短信可在此对接第三方）
     */
    void sendLoginCode(String phone);

    /**
     * 校验并消费验证码（一次性）
     */
    boolean verifyAndConsumeLoginCode(String phone, String inputCode);
}
