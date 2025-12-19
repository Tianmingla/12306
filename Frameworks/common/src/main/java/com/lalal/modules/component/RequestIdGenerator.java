package com.lalal.modules.component;

import org.springframework.stereotype.Component;

import java.util.UUID;


public abstract class RequestIdGenerator {
    public abstract String generate();
}
