CREATE TABLE IF NOT EXISTS `t_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_sn` varchar(64) NOT NULL COMMENT '订单号',
  `username` varchar(64) NOT NULL COMMENT '下单账号(手机号)',
  `train_number` varchar(32) NOT NULL,
  `start_station` varchar(64) NOT NULL,
  `end_station` varchar(64) NOT NULL,
  `run_date` datetime DEFAULT NULL,
  `total_amount` decimal(12,2) DEFAULT NULL,
  `status` int NOT NULL DEFAULT '0' COMMENT '0待支付 1已支付 2已取消 3已退票',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_sn` (`order_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `order_sn` varchar(64) NOT NULL,
  `passenger_id` bigint DEFAULT NULL,
  `passenger_name` varchar(64) DEFAULT NULL,
  `id_card` varchar(32) DEFAULT NULL,
  `carriage_number` varchar(16) DEFAULT NULL,
  `seat_number` varchar(16) DEFAULT NULL,
  `seat_type` int DEFAULT NULL,
  `amount` decimal(12,2) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order_sn` (`order_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
