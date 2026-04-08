package com.lalal.modules.mq.consumer;

import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.mq.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

/**
 * 消息消费者基类
 * 继承此类并配合 @MessageConsumer 注解使用，简化消费者开发
 *
 * <p>内置消息幂等性支持，基于消息ID去重</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * {@code
 * @MessageConsumer(
 *     topic = "order-topic",
 *     tag = "create",
 *     consumerGroup = "order-create-consumer"
 * )
 * public class OrderCreateConsumer extends BaseMessageConsumer<OrderDTO> {
 *
 *     @Override
 *     protected void doProcess(OrderDTO message) {
 *         // 处理订单创建逻辑
 *         orderService.createOrder(message);
 *     }
 *
 *     @Override
 *     protected void onException(OrderDTO message, Exception e) {
 *         // 自定义异常处理
 *         log.error("订单创建失败: {}", message.getOrderId(), e);
 *     }
 * }
 * }
 * </pre>
 *
 *
 */
public abstract class BaseMessageConsumer {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:";
    private static final long DEFAULT_IDEMPOTENT_EXPIRE_HOURS = 24;

    @Autowired(required = false)
    private SafeCacheTemplate safeCacheTemplate;

    /**
     * 处理消息入口
     * 子类不应该重写此方法，而是实现 doProcess 方法
     *
     * @param message 消息对象
     * @return 处理结果，true-成功，false-失败（会触发重试）
     */
    public boolean process(Message message) {
        Object body = null;
        try {
            // 0. 幂等性检查
            if (!checkIdempotent(message)) {
                log.warn("消息重复消费，跳过处理。Topic: {}, MessageId: {}",
                        message.getTopic(), message.getMessageId());
                return true;
            }

            // 1. 解析消息体
            body = parseMessageBody(message);

            // 2. 前置处理（可用于日志记录、参数校验等）
            if (!preProcess(message, body)) {
                log.warn("消息前置处理返回false，跳过处理。Topic: {}, MessageId: {}",
                        message.getTopic(), message.getMessageId());
                return true;
            }

            // 3. 执行业务处理
            doProcess(body);

            // 4. 后置处理
            postProcess(message, body);

            return true;

        } catch (Exception e) {
            log.error("消息处理异常。Topic: {}, MessageId: {}",
                    message.getTopic(), message.getMessageId(), e);

            // 调用异常处理
            onException(body, e);

            // 返回false触发重试
            return false;
        }
    }

    /**
     * 幂等性检查
     * 基于消息ID去重，使用Redis SET NX实现
     *
     * @param message 消息对象
     * @return true-可以处理，false-重复消息
     */
    protected boolean checkIdempotent(Message message) {
        // 如果没有注入缓存模板，跳过幂等性检查
        if (safeCacheTemplate == null) {
            log.debug("未配置SafeCacheTemplate，跳过幂等性检查");
            return true;
        }

        // 如果消息ID为空，跳过幂等性检查（不建议）
        if (message.getMessageId() == null || message.getMessageId().isEmpty()) {
            log.warn("消息ID为空，跳过幂等性检查。Topic: {}", message.getTopic());
            return true;
        }

        String key = IDEMPOTENT_KEY_PREFIX + message.getTopic() + ":" + message.getMessageId();

        try {
            // 尝试设置幂等键，如果已存在则返回false
            Boolean success = safeCacheTemplate.setIfAbsent(key, "1", DEFAULT_IDEMPOTENT_EXPIRE_HOURS, TimeUnit.HOURS);
            if (success == null || !success) {
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("幂等性检查异常，允许消息处理。Key: {}", key, e);
            return true;
        }
    }

    /**
     * 解析消息体
     * 子类可重写以实现自定义解析逻辑
     *
     * @param message 消息对象
     * @return 解析后的消息体
     */
    @SuppressWarnings("unchecked")
    protected Object parseMessageBody(Message message) {
        return  message.getBody();
    }

    /**
     * 前置处理
     * 子类可重写以添加预处理逻辑（如参数校验、日志记录等）
     *
     * @param message 消息对象
     * @param body    消息体
     * @return true-继续处理，false-跳过处理
     */
    protected boolean preProcess(Message message, Object body) {
        // 默认实现：记录日志
        if (log.isDebugEnabled()) {
            log.debug("开始处理消息。Topic: {}, Tag: {}, MessageId: {}",
                    message.getTopic(), message.getTag(), message.getMessageId());
        }
        return true;
    }

    /**
     * 执行业务处理
     * 子类必须实现此方法
     *
     * @param body 消息体
     */
    protected abstract void doProcess(Object body);

    /**
     * 后置处理
     * 子类可重写以添加后处理逻辑（如清理资源、发送通知等）
     *
     * @param message 消息对象
     * @param body    消息体
     */
    protected void postProcess(Message message, Object body) {
        // 默认实现：记录日志
        if (log.isDebugEnabled()) {
            log.debug("消息处理完成。Topic: {}, MessageId: {}",
                    message.getTopic(), message.getMessageId());
        }
    }

    /**
     * 异常处理
     * 子类可重写以自定义异常处理逻辑
     *
     * @param body 消息体（可能为null，如果解析失败）
     * @param e    异常信息
     */
    protected void onException(Object body, Exception e) {
        // 默认实现：仅记录日志，子类可重写
    }
}
