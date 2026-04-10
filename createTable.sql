-- MySQL dump 10.13  Distrib 8.0.40, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: my12306
-- ------------------------------------------------------
-- Server version	8.0.40

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `t_carriage`
--

DROP TABLE IF EXISTS `t_carriage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_carriage` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `train_id` bigint NOT NULL COMMENT '列车ID',
  `carriage_number` varchar(10) NOT NULL COMMENT '车厢号',
  `carriage_type` int NOT NULL COMMENT '车厢类型（0:普通, 1:卧铺, 2:商务等）',
  `seat_count` int NOT NULL COMMENT '座位数',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` int NOT NULL DEFAULT '0' COMMENT '删除标志（0:正常, 1:已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_train_carriage_num` (`train_id`,`carriage_number`),
  KEY `idx_train_id` (`train_id`),
  KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB AUTO_INCREMENT=64670 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='车厢信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `t_region`
--

DROP TABLE IF EXISTS `t_region`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_region` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(32) NOT NULL COMMENT '地区名称（如 北京）',
  `full_name` varchar(64) NOT NULL COMMENT '全名（如 北京市）',
  `code` varchar(10) NOT NULL COMMENT '地区编码（如 110000）',
  `initial` char(1) NOT NULL COMMENT '首字母（B）',
  `spell` varchar(64) NOT NULL COMMENT '拼音（beijing）',
  `popular_flag` int NOT NULL DEFAULT '0' COMMENT '热门标识（0:普通, 1:热门）',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` int NOT NULL DEFAULT '0' COMMENT '删除标志（0:正常, 1:已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_name` (`name`),
  KEY `idx_initial` (`initial`),
  KEY `idx_popular` (`popular_flag`),
  KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='地区信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `t_seat`
--

DROP TABLE IF EXISTS `t_seat`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_seat` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `train_id` bigint NOT NULL COMMENT '列车ID',
  `carriage_number` varchar(10) NOT NULL COMMENT '车厢号',
  `seat_number` varchar(10) NOT NULL COMMENT '座位号',
  `seat_type` int NOT NULL COMMENT '座位类型（0:硬座, 1:二等座, 2:一等座, 3:商务座等）',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` int NOT NULL DEFAULT '0' COMMENT '删除标志（0:正常, 1:已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_train_carriage_seat` (`train_id`,`carriage_number`,`seat_number`),
  KEY `idx_train_id` (`train_id`),
  KEY `idx_seat_type` (`seat_type`),
  KEY `idx_del_flag` (`del_flag`),
  KEY `idx_count` (`train_id`,`seat_type`,`del_flag`)
) ENGINE=InnoDB AUTO_INCREMENT=4964621 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='座位信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `t_station`
--

DROP TABLE IF EXISTS `t_station`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_station` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(20) NOT NULL COMMENT '车站编码',
  `name` varchar(100) NOT NULL COMMENT '车站中文名',
  `spell` varchar(100) DEFAULT NULL COMMENT '拼音全拼',
  `region` varchar(10) DEFAULT NULL COMMENT '地区编号（城市编码）',
  `region_name` varchar(100) DEFAULT NULL COMMENT '地区名称',
  `del_flag` tinyint DEFAULT '0' COMMENT '删除标志：0-正常，1-删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_region` (`region`)
) ENGINE=InnoDB AUTO_INCREMENT=3346 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `t_ticket`
--

DROP TABLE IF EXISTS `t_ticket`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_ticket` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `train_id` bigint NOT NULL COMMENT '列车ID',
  `carriage_number` varchar(10) NOT NULL COMMENT '车厢号',
  `seat_number` varchar(10) NOT NULL COMMENT '座位号',
  `seat_type` int NOT NULL COMMENT '座位类型（冗余）',
  `passenger_id` varchar(32) NOT NULL COMMENT '乘车人ID',
  `travel_date` datetime NOT NULL COMMENT '乘车出发时间',
  `departure_station` varchar(32) NOT NULL COMMENT '出发站点',
  `arrival_station` varchar(32) NOT NULL COMMENT '到达站点',
  `ticket_status` int NOT NULL DEFAULT '0' COMMENT '车票状态（0:未支付, 1:已支付, 2:已退票等）',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` int NOT NULL DEFAULT '0' COMMENT '删除标志（0:正常, 1:已删除）',
  PRIMARY KEY (`id`),
  KEY `idx_username` (`username`),
  KEY `idx_train_id` (`train_id`),
  KEY `idx_travel_date` (`travel_date`),
  KEY `idx_departure_arrival` (`departure_station`,`arrival_station`),
  KEY `idx_passenger_id` (`passenger_id`),
  KEY `idx_del_flag` (`del_flag`),
  KEY `t_ticket_detail` (`id`,`train_id`,`travel_date`,`carriage_number`,`seat_type`,`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='车票表（含冗余字段）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `t_train`
--

DROP TABLE IF EXISTS `t_train`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_train` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `train_number` varchar(20) NOT NULL COMMENT '列车车次，如 D1',
  `train_type` int DEFAULT NULL COMMENT '列车类型 0：高铁 1：动车 2：普通车',
  `train_tag` varchar(100) DEFAULT NULL COMMENT '列车标签，JSON 或逗号分隔',
  `train_brand` varchar(50) DEFAULT NULL COMMENT '品牌类型，如 GC/D/Z/T/K',
  `sale_status` int DEFAULT '0' COMMENT '销售状态 0：可售 1：不可售 2：未知',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` int DEFAULT '0' COMMENT '删除标志 0:normal 1:deleted',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_train_number` (`train_number`),
  KEY `idx_train_num` (`train_number`,`del_flag`)
) ENGINE=InnoDB AUTO_INCREMENT=10477 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='列车主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `t_train_route_pair`
--

DROP TABLE IF EXISTS `t_train_route_pair`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_train_route_pair` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `train_id` bigint NOT NULL COMMENT '列车ID',
  `train_number` varchar(20) NOT NULL COMMENT '车次号（如 G1234）',
  `departure_station` varchar(32) NOT NULL COMMENT '出发站点',
  `arrival_station` varchar(32) NOT NULL COMMENT '到达站点',
  `start_time` time DEFAULT NULL,
  `end_time` time NOT NULL COMMENT '到达时间',
  `start_region` varchar(32) NOT NULL COMMENT '起始城市',
  `end_region` varchar(32) NOT NULL COMMENT '终点城市',
  `day_diff` varchar(8) NOT NULL COMMENT '路线的跨天数',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` int NOT NULL DEFAULT '0' COMMENT '删除标志（0:正常, 1:已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_train_dep_arr` (`train_id`,`departure_station`,`arrival_station`),
  KEY `idx_train_number` (`train_number`),
  KEY `idx_departure` (`departure_station`),
  KEY `idx_arrival` (`arrival_station`),
  KEY `idx_start_end_region` (`start_region`,`end_region`),
  KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB AUTO_INCREMENT=582532 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预计算列车路线对（用于快速查询余票/价格）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `t_train_station`
--

DROP TABLE IF EXISTS `t_train_station`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_train_station` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `train_id` bigint DEFAULT NULL COMMENT '冗余：关联 t_train.id（可为空）',
  `train_number` varchar(20) NOT NULL COMMENT '冗余：车次号，如 D1',
  `station_id` bigint DEFAULT NULL COMMENT '车站ID（可后续补充）',
  `station_name` varchar(50) NOT NULL COMMENT '车站名称',
  `sequence` int NOT NULL COMMENT '站点顺序，从1开始',
  `arrival_time` time DEFAULT NULL COMMENT '到站时间',
  `departure_time` time DEFAULT NULL COMMENT '出站时间',
  `stopover_time` int DEFAULT NULL COMMENT '停留时间（分钟）',
  `arrive_day_diff` int DEFAULT 0 COMMENT  '到达本站经过的天数',
  `run_date` date NOT NULL COMMENT '列车运行日期',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_run_date_station` (`run_date`,`station_name`),
  KEY `idx_train_number_run_date` (`train_number`,`run_date`),
  KEY `idx_station_departure` (`station_id`,`departure_time`,`del_flag`),
  KEY `idx_query_route` (`run_date`,`station_name`,`train_number`,`sequence`),
  KEY `idx_t2_lookup` (`station_name`,`train_id`,`sequence`),
  KEY `idx_t1_lookup` (`run_date`,`station_name`,`train_id`,`sequence`,`train_number`)
) ENGINE=InnoDB AUTO_INCREMENT=95664 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='列车站点关系表（含冗余字段）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-03-10 22:05:44

--
-- 用户账号（手机验证码登录）与乘车人（一对多）
--

CREATE TABLE IF NOT EXISTS `t_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `phone` varchar(20) NOT NULL COMMENT '登录手机号',
  `email` varchar(64) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0-正常, 1-禁用',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录账号';

CREATE TABLE IF NOT EXISTS `t_passenger` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `real_name` varchar(64) NOT NULL,
  `id_card_type` int NOT NULL DEFAULT '1',
  `id_card_number` varchar(32) NOT NULL,
  `passenger_type` int NOT NULL DEFAULT '1',
  `phone` varchar(20) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id_card` (`user_id`,`id_card_number`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='乘车人';

CREATE TABLE `t_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_sn` VARCHAR(64) NOT NULL COMMENT '订单编号',
  `username` VARCHAR(64) DEFAULT NULL COMMENT '用户名',
  `train_number` VARCHAR(20) NOT NULL COMMENT '车次',
  `start_station` VARCHAR(64) NOT NULL COMMENT '出发站',
  `end_station` VARCHAR(64) NOT NULL COMMENT '到达站',
  `run_date` datetime NOT NULL COMMENT '乘车日期',
  `total_amount` DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
  `pay_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '支付时间',
  `status` INT NOT NULL DEFAULT 0 COMMENT '订单状态：0-待支付, 1-已支付, 2-已取消, 3-已退票',

  -- BaseDO 字段
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  `del_flag` TINYINT DEFAULT 0 COMMENT '删除标志：0-正常, 1-已删除',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_sn` (`order_sn`),
  KEY `idx_username` (`username`),
  KEY `idx_run_date` (`run_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='火车票订单主表';

CREATE TABLE `t_order_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` BIGINT NOT NULL COMMENT '关联订单ID',
  `order_sn` VARCHAR(64) NOT NULL COMMENT '关联订单编号',
  `passenger_id` BIGINT DEFAULT NULL COMMENT '乘客ID',
  `passenger_name` VARCHAR(64) NOT NULL COMMENT '乘客姓名',
  `id_card` VARCHAR(32) NOT NULL COMMENT '身份证号',
  `carriage_number` VARCHAR(10) NOT NULL COMMENT '车厢号',
  `seat_number` VARCHAR(10) NOT NULL COMMENT '座位号',
  `seat_type` INT NOT NULL COMMENT '座位类型',
  `amount` DECIMAL(10,2) NOT NULL COMMENT '单项金额',

  -- BaseDO 字段
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP  COMMENT '修改时间',
  `del_flag` TINYINT DEFAULT 0 COMMENT '删除标志：0-正常, 1-已删除',

  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_sn` (`order_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='火车票订单明细表';

-- ------------------------------------------------------
-- 异步购票请求跟踪表
-- ------------------------------------------------------

CREATE TABLE IF NOT EXISTS `t_ticket_async_request` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `request_id` VARCHAR(64) NOT NULL COMMENT '请求唯一ID，雪花算法生成',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `train_num` VARCHAR(20) NOT NULL COMMENT '车次号',
  `date` DATE NOT NULL COMMENT '乘车日期',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-处理中，1-成功，2-失败，3-发送失败，4-选座成功，5-订单创建中',
  `order_sn` VARCHAR(64) DEFAULT NULL COMMENT '成功后的订单号',
  `error_message` TEXT DEFAULT NULL COMMENT '失败原因',
  `request_params` JSON DEFAULT NULL COMMENT '原始请求参数JSON（用于重试）',
  `account` VARCHAR(20) DEFAULT NULL COMMENT '购票账号(手机号)',
  `start_station` VARCHAR(50) DEFAULT NULL COMMENT '出发站',
  `end_station` VARCHAR(50) DEFAULT NULL COMMENT '到达站',
  `passenger_ids_json` JSON DEFAULT NULL COMMENT '乘车人ID列表JSON',
  `seat_type_list_json` JSON DEFAULT NULL COMMENT '座位类型列表JSON',
  `choose_seats_json` JSON DEFAULT NULL COMMENT '选座偏好列表JSON',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_id` (`request_id`),
  KEY `idx_user_train_date` (`user_id`,`train_num`,`date`),
  KEY `idx_status_create_time` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异步购票请求跟踪表';

-- ------------------------------------------------------
-- 运营管理人员表（与普通用户分离）
-- ------------------------------------------------------

CREATE TABLE IF NOT EXISTS `t_admin_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `password` varchar(128) NOT NULL COMMENT '密码(BCrypt加密)',
  `real_name` varchar(64) DEFAULT NULL COMMENT '真实姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(64) DEFAULT NULL,
  `role` varchar(32) NOT NULL DEFAULT 'OPERATOR' COMMENT '角色: ADMIN-管理员, OPERATOR-运营人员',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0-正常, 1-禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营管理人员';

-- 初始管理员账号：admin / 123456
INSERT INTO `t_admin_user` (`username`, `password`, `real_name`, `role`)
VALUES ('admin', '$2a$10$8h4PBGvFnhSKKS67jBfyc.qu.hpvKwiM8el1cyZqin3zLqzKBD0ZW', '系统管理员', 'ADMIN')
ON DUPLICATE KEY UPDATE `username` = `username`;

-- ------------------------------------------------------
-- 角色权限管理表
-- ------------------------------------------------------

CREATE TABLE IF NOT EXISTS `t_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_name` varchar(64) NOT NULL COMMENT '角色名称',
  `role_code` varchar(32) NOT NULL COMMENT '角色编码',
  `description` varchar(256) DEFAULT NULL COMMENT '角色描述',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0-正常, 1-禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `t_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `permission_name` varchar(64) NOT NULL COMMENT '权限名称',
  `permission_code` varchar(64) NOT NULL COMMENT '权限编码',
  `resource_type` tinyint NOT NULL DEFAULT 1 COMMENT '资源类型: 1-菜单, 2-按钮, 3-API',
  `parent_id` bigint DEFAULT 0 COMMENT '父级ID',
  `resource_url` varchar(256) DEFAULT NULL COMMENT '资源路径',
  `sort_order` int DEFAULT 0 COMMENT '排序',
  `description` varchar(256) DEFAULT NULL COMMENT '权限描述',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0-正常, 1-禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

CREATE TABLE IF NOT EXISTS `t_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

CREATE TABLE IF NOT EXISTS `t_admin_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_user_id` bigint NOT NULL COMMENT '管理员ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`admin_user_id`, `role_id`),
  KEY `idx_admin_user_id` (`admin_user_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员角色关联表';

-- 初始化角色数据
INSERT INTO `t_role` (`role_name`, `role_code`, `description`) VALUES
('超级管理员', 'SUPER_ADMIN', '拥有所有权限'),
('管理员', 'ADMIN', '拥有管理权限'),
('运营人员', 'OPERATOR', '拥有运营相关权限')
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`);

-- 初始化权限数据（菜单权限）
INSERT INTO `t_permission` (`permission_name`, `permission_code`, `resource_type`, `parent_id`, `resource_url`, `sort_order`) VALUES
('数据统计', 'dashboard', 1, 0, '/dashboard', 1),
('车票管理', 'train', 1, 0, '/train', 2),
('车次管理', 'train:list', 1, 0, '/train/list', 1),
('站点管理', 'train:station', 1, 0, '/train/station', 2),
('线路管理', 'train:route', 1, 0, '/train/route', 3),
('订单管理', 'order', 1, 0, '/order', 3),
('订单列表', 'order:list', 1, 0, '/order/list', 1),
('退款管理', 'order:refund', 1, 0, '/order/refund', 2),
('系统管理', 'system', 1, 0, '/system', 4),
('用户管理', 'system:user', 1, 0, '/system/user', 1),
('角色管理', 'system:role', 1, 0, '/system/role', 2),
('操作日志', 'system:log', 1, 0, '/system/log', 3)
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`);

-- ------------------------------------------------------
-- 操作日志表
-- ------------------------------------------------------

CREATE TABLE IF NOT EXISTS `t_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_user_id` bigint NOT NULL COMMENT '操作人ID',
  `admin_username` varchar(64) NOT NULL COMMENT '操作人用户名',
  `operation_type` varchar(32) NOT NULL COMMENT '操作类型: CREATE/UPDATE/DELETE/LOGIN/EXPORT',
  `module` varchar(64) NOT NULL COMMENT '操作模块',
  `description` varchar(256) DEFAULT NULL COMMENT '操作描述',
  `request_method` varchar(10) DEFAULT NULL COMMENT '请求方法',
  `request_url` varchar(256) DEFAULT NULL COMMENT '请求URL',
  `request_params` text COMMENT '请求参数',
  `response_result` text COMMENT '响应结果',
  `ip` varchar(64) DEFAULT NULL COMMENT 'IP地址',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '操作状态: 0-成功, 1-失败',
  `error_msg` text COMMENT '错误信息',
  `duration` bigint DEFAULT NULL COMMENT '执行时长(毫秒)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_user_id` (`admin_user_id`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_module` (`module`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- 候补购票表
CREATE TABLE IF NOT EXISTS `t_waitlist_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `waitlist_sn` VARCHAR(32) NOT NULL COMMENT '候补订单号',
    `username` VARCHAR(50) NOT NULL COMMENT '用户账号（手机号）',
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
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_waitlist_sn` (`waitlist_sn`),
    KEY `idx_username` (`username`),
    KEY `idx_train_date` (`train_number`, `travel_date`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候补购票表';

-- ------------------------------------------------------
-- 票价计算相关表
-- ------------------------------------------------------

-- 站间距离表
CREATE TABLE IF NOT EXISTS `t_station_distance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `train_id` bigint NOT NULL COMMENT '列车ID',
  `train_number` varchar(20) NOT NULL COMMENT '车次号',
  `departure_station_id` bigint DEFAULT NULL COMMENT '出发站ID',
  `departure_station_name` varchar(50) NOT NULL COMMENT '出发站名',
  `arrival_station_id` bigint DEFAULT NULL COMMENT '到达站ID',
  `arrival_station_name` varchar(50) NOT NULL COMMENT '到达站名',
  `distance` int NOT NULL COMMENT '里程(公里)',
  `line_name` varchar(100) DEFAULT NULL COMMENT '线路名称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_train_stations` (`train_id`, `departure_station_name`, `arrival_station_name`),
  KEY `idx_train_number` (`train_number`),
  KEY `idx_stations` (`departure_station_name`, `arrival_station_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站间距离表';

-- 列车票价配置表
CREATE TABLE IF NOT EXISTS `t_train_fare_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `train_id` bigint NOT NULL COMMENT '列车ID',
  `train_number` varchar(20) NOT NULL COMMENT '车次号',
  `surcharge_type` tinyint NOT NULL DEFAULT 0 COMMENT '上浮类型: 0-普通车, 1-新空调50%, 2-新空调一档40%, 3-新空调二档30%, 4-高软180%, 5-高软208%',
  `is_peak_season` tinyint NOT NULL DEFAULT 0 COMMENT '是否春运期间',
  `effective_date` date DEFAULT NULL COMMENT '生效日期',
  `expire_date` date DEFAULT NULL COMMENT '失效日期',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_train_config` (`train_id`),
  KEY `idx_train_number` (`train_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='列车票价上浮配置表';

