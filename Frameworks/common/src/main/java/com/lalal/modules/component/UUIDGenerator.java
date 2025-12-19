package com.lalal.modules.component;

import java.util.UUID;

public class UUIDGenerator extends RequestIdGenerator{
    @Override
    public String generate() {
        return "req-" + UUID.randomUUID().toString().replace("-", "");
    }
}
