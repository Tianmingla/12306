-- 12306 场景：手机号验证码登录 + 多乘车人
-- 在 my12306 库执行（若已有 t_user 请按需 ALTER，以下为全新表结构示例）

CREATE TABLE IF NOT EXISTS `t_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `phone` varchar(20) NOT NULL COMMENT '登录手机号，唯一',
  `email` varchar(64) DEFAULT NULL COMMENT '邮箱（可选）',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` int NOT NULL DEFAULT '0' COMMENT '0 正常 1 删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录账号（手机）';

CREATE TABLE IF NOT EXISTS `t_passenger` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '账号用户 ID',
  `real_name` varchar(64) NOT NULL COMMENT '乘车人姓名',
  `id_card_type` int NOT NULL DEFAULT '1' COMMENT '证件类型',
  `id_card_number` varchar(32) NOT NULL COMMENT '证件号',
  `passenger_type` int NOT NULL DEFAULT '1' COMMENT '旅客类型',
  `phone` varchar(20) DEFAULT NULL COMMENT '乘车人手机',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id_card` (`user_id`,`id_card_number`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='乘车人（一个账号多个）';
