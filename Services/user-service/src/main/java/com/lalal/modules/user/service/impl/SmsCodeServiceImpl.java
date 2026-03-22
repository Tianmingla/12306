package com.lalal.modules.user.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.user.service.SmsCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
public class SmsCodeServiceImpl implements SmsCodeService {

    private static final Logger log = LoggerFactory.getLogger(SmsCodeServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private SafeCacheTemplate safeCacheTemplate;

    @Value("${user.sms.mock-enabled:true}")
    private boolean mockEnabled;

    @Value("${user.sms.mock-code:123456}")
    private String mockCode;

    @Value("${user.sms.expire-minutes:5}")
    private int expireMinutes;

    @Value("${user.sms.send-interval-seconds:60}")
    private int sendIntervalSeconds;

    @Override
    public void sendLoginCode(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        String p = phone.trim();
        if (!p.matches("^1[3-9]\\d{9}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        safeCacheTemplate.setDefaultValueSerializer();
        String rateKey = CacheConstant.smsSendRateLimitKey(p);
        Object hit = safeCacheTemplate.get(rateKey);
        if (hit != null) {
            throw new RuntimeException("发送过于频繁，请稍后再试");
        }

        String code = mockEnabled ? mockCode : String.format("%06d", RANDOM.nextInt(1_000_000));
        safeCacheTemplate.set(CacheConstant.smsLoginCodeKey(p), code, expireMinutes, TimeUnit.MINUTES);
        safeCacheTemplate.set(rateKey, "1", sendIntervalSeconds, TimeUnit.SECONDS);
        if (mockEnabled) {
            log.info("[MOCK SMS] phone={} code={}", p, code);
        }
        //TODO 发送code
    }

    @Override
    public boolean verifyAndConsumeLoginCode(String phone, String inputCode) {
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(inputCode)) {
            return false;
        }
        String p = phone.trim();
        String c = inputCode.trim();
        if (!p.matches("^1[3-9]\\d{9}$")) {
            return false;
        }
        safeCacheTemplate.setDefaultValueSerializer();
        String key = CacheConstant.smsLoginCodeKey(p);
        String expected = safeCacheTemplate.get(key, new TypeReference<String>() {});
        if (expected == null) {
            return false;
        }
        if (!expected.equals(c)) {
            return false;
        }
        safeCacheTemplate.del(key);
        return true;
    }
}
