-- 候补购票表
CREATE TABLE IF NOT EXISTS `t_waitlist_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `waitlist_sn` VARCHAR(32) NOT NULL COMMENT '候补订单号',
    `username` VARCHAR(50) NOT NULL COMMENT '用户账号（手机号）',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `train_number` VARCHAR(20) NOT NULL COMMENT '车次号',
    `start_station` VARCHAR(50) NOT NULL COMMENT '出发站',
    `end_station` VARCHAR(50) NOT NULL COMMENT '到达站',
    `travel_date` DATE NOT NULL COMMENT '乘车日期',
    `seat_types` VARCHAR(50) NOT NULL COMMENT '座位类型（逗号分隔）',
    `passenger_ids` VARCHAR(500) NOT NULL COMMENT '乘车人ID列表（逗号分隔）',
    `prepay_amount` DECIMAL(10, 2) NOT NULL COMMENT '预支付金额',
    `deadline` DATETIME NOT NULL COMMENT '截止时间',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0=待兑现，1=兑现中，2=已兑现，3=已取消，4=已过期',
    `fulfilled_order_sn` VARCHAR(64) DEFAULT NULL COMMENT '兑现成功后的订单号',
    `priority_score` DECIMAL(10, 2) DEFAULT 0 COMMENT '优先级分数',
    `retry_count` INT DEFAULT 0 COMMENT '重试次数',
    `last_error` VARCHAR(500) DEFAULT NULL COMMENT '最后一次错误信息',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_waitlist_sn` (`waitlist_sn`),
    KEY `idx_username` (`username`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_train_date` (`train_number`, `travel_date`),
    KEY `idx_status` (`status`),
    KEY `idx_priority` (`priority_score` DESC),
    KEY `idx_deadline` (`deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候补购票表';

-- 候补订单操作日志表
CREATE TABLE IF NOT EXISTS `t_waitlist_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `waitlist_sn` VARCHAR(32) NOT NULL COMMENT '候补订单号',
    `action` VARCHAR(32) NOT NULL COMMENT '操作动作：CREATE/CHECK/SEAT_SELECT/ORDER_CREATE/SUCCESS/FAIL/CANCEL',
    `status_before` TINYINT DEFAULT NULL COMMENT '操作前状态',
    `status_after` TINYINT DEFAULT NULL COMMENT '操作后状态',
    `message` TEXT COMMENT '操作描述/备注',
    `message_id` VARCHAR(64) DEFAULT NULL COMMENT 'MQ消息ID（幂等性）',
    `success` TINYINT NOT NULL DEFAULT 1 COMMENT '操作结果：0-失败 1-成功',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_waitlist_sn` (`waitlist_sn`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候补订单操作日志表';

-- 候补队列表（可选，用于持久化队列状态，主要依赖Redis）
CREATE TABLE IF NOT EXISTS `t_waitlist_queue` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `waitlist_sn` VARCHAR(32) NOT NULL COMMENT '候补订单号',
    `train_number` VARCHAR(20) NOT NULL COMMENT '车次号',
    `travel_date` DATE NOT NULL COMMENT '乘车日期',
    `seat_type` INT NOT NULL COMMENT '座位类型',
    `priority_score` DECIMAL(10, 2) NOT NULL COMMENT '优先级分数',
    `queue_position` INT NOT NULL COMMENT '队列位置',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_waitlist_sn` (`waitlist_sn`),
    KEY `idx_train_date_seat` (`train_number`, `travel_date`, `seat_type`),
    KEY `idx_priority` (`priority_score` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候补队列表（持久化，与Redis同步）';
