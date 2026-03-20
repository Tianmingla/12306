package com.lalal.modules.mq;

public class TestMqService implements MessageQueueService {
    @Override
    public void send(String topic, Object message) {

    }

    @Override
    public void sendDelay(String topic, Object message, long delayTime) {

    }
}
