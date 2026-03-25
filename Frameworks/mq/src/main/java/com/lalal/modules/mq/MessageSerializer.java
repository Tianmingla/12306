package com.lalal.modules.mq;

/**
 * 消息序列化接口
 */
public interface MessageSerializer {

    /**
     * 序列化
     * @param object 对象
     * @return 字节数组
     */
    byte[] serialize(Object object);

    /**
     * 反序列化
     * @param bytes 字节数组
     * @param clazz 目标类型
     * @return 对象
     */
    <T> T deserialize(byte[] bytes, Class<T> clazz);
}
