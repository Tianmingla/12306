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
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime NOT NULL COMMENT '到达时间',
  `start_region` varchar(32) NOT NULL COMMENT '起始城市',
  `end_region` varchar(32) NOT NULL COMMENT '终点城市',
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
  `arrival_time` datetime DEFAULT NULL COMMENT '到站时间',
  `departure_time` datetime DEFAULT NULL COMMENT '出站时间',
  `stopover_time` int DEFAULT NULL COMMENT '停留时间（分钟）',
  `run_date` date NOT NULL COMMENT '列车运行日期',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_run_date_station` (`run_date`,`station_name`),
  KEY `idx_train_number_run_date` (`train_number`,`run_date`),
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
