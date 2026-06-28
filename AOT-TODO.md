# AOT / GraalVM Native 编译 TODO

> 分支: `aot` | 创建时间: 2026-06-27

## 已完成

- [x] 引入 `native-maven-plugin:0.10.4`
- [x] 升级 Spring Boot 3.2.12 + Spring Cloud 2023.0.3 + Spring Cloud Alibaba 2023.0.1.0
- [x] 升级 MyBatis-Plus 3.5.7 + 强制覆盖 mybatis-spring 3.0.4
- [x] 每个服务添加 native profile（skip repackage, compile-no-fork）
- [x] 创建框架层 native-image metadata（reflect/proxy/resource/serialization config）
- [x] 创建服务层 native-image metadata
- [x] 创建 Dockerfile.native（6个服务，2阶段：GraalVM编译 + Ubuntu运行时）
- [x] 适配 `spring.cloud.bootstrap.enabled=false`（禁掉 Spring Cloud Bootstrap）
- [x] 适配 `spring.aot.enabled=false`（运行时禁 AOT，已内联到二进制）
- [x] 修复 `spring-cloud-context` spring.factories 反射配置
- [x] 修复 SLF4J/Logback GraalVM 初始化 (`--initialize-at-build-time`)
- [x] 创建 `GraalVM_init/` 目录（Docker Compose + tracing agent 方案）
- [x] 创建 `scripts/collect-native-metadata.sh`

## 待解决

### 编译
- [ ] **user-service 启动报 SLF4J/Logback 问题** — `-H:-UseServiceLoaderFeature` 和 `--initialize-at-build-time` 配合仍不工作，需要进一步排查
- [ ] **native-image OOM** — 不加 `-H:-UseServiceLoaderFeature` 时 8G 内存不足(25分钟 OOM)，需优化或换更大内存机器
- [ ] **其他 5 个服务** 未验证编译（ticket/seat/order/admin/gateway），各服务依赖不同可能出现新问题：
  - ticket-service: OpenFeign + RocketMQ consumer
  - order-service: Alipay SDK + OpenFeign + RocketMQ
  - seat-service: RocketMQ + 策略模式
  - admin-service: Spring Security + BCrypt
  - gateway-service: Spring Cloud Gateway (Netty, native支持差)

### 运行时 metadata
- [ ] **运行 tracing agent** — `GraalVM_init/docker-compose.yml` 已准备好，需要启动基础设施 + 各服务 agent，手动调用所有 API 生成完整 metadata
- [ ] **补充 reflect-config** — agent 生成后补齐缺失的反射条目
- [ ] **补充 proxy-config** — Feign 客户端、MyBatis Mapper 代理
- [ ] **补充 serialization-config** — RocketMQ 消息 DTO

### 优化
- [ ] 评估是否值得上 Spring Boot 3.3+（等 MyBatis-Plus 完全适配后）
- [ ] 编译时间优化（当前单服务 ~5-15分钟）
- [ ] 二进制体积优化
- [ ] CI/CD 集成
