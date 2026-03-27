# CLUADE.md

This file provides guidance to Cluade Code (cludae.ai/code) when working with code in this repository.

## Project Overview

This is a 12306 railway ticketing system implementation with a microservices architecture. The project consists of:
- **Backend**: Spring Cloud microservices (Java 17, Spring Boot 3.0.7)
- **Frontend**: React + TypeScript + Vite application
- **Data Scripts**: Python scripts for data processing and import

## Build Commands

### Backend (Maven)
```bash
# Build all modules
mvn clean install

# Build a specific service
mvn clean install -pl Services/ticket-service -am

# Skip tests
mvn clean install -DskipTests
```

### Frontend
```bash
cd 12306
npm install
npm run dev       # Development server (default Vite port)
npm run build     # Production build
```

## Service Ports

| Service | Port |
|---------|------|
| gateway-service | 8080 |
| ticket-service | 8081 |
| seat-service | 8082 |
| order-service | 8083 |
| user-service | 8084 |

## Infrastructure Dependencies

- **MySQL**: localhost:3306, database `my12306`, user: root
- **Redis**: localhost:6379
- **Nacos**: localhost:8848 (service discovery)

## Architecture

### Microservices Structure

```
Services/
├── gateway-service/    # API Gateway, routes requests, injects X-User-Id header
├── ticket-service/     # Train search, ticket purchase, orchestrates other services
├── seat-service/       # Seat selection and management
├── order-service/      # Order creation, payment (Alipay sandbox integration)
└── user-service/       # User auth (JWT), passenger management, SMS login
```

### Framework Modules

```
Frameworks/
├── common/           # Shared DTOs, Result wrapper, enums, utils
├── database/         # MyBatis-Plus config, base entities
├── cache/            # Redis config, SafeCacheTemplate
├── log/              # Log monitoring aspects
├── Idempotent/       # @Idempotent annotation for deduplication
└── mq/               # RocketMQ abstraction layer
```

### Inter-Service Communication

- ticket-service uses OpenFeign clients to call:
  - `user-service`: `/api/user/internal/passengers/batch`
  - `seat-service`: Seat selection APIs
  - `order-service`: Order creation

- Gateway routes by path prefix:
  - `/api/user/**` -> user-service
  - `/api/ticket/**`, `/api/trainDetail/**` -> ticket-service
  - `/api/seat/**` -> seat-service
  - `/api/order/**` -> order-service

### Key Patterns

1. **Idempotency**: Use `@Idempotent` annotation on methods that need duplicate request protection. Supports SpEL expressions for key generation.

2. **Result Wrapper**: All API responses use `Result<T>` from `common` module.

3. **User Context**: Gateway injects `X-User-Id` header for authenticated requests. Controllers extract it with `@RequestHeader("X-User-Id")`.

## Data Scripts (Python)

Located in `DataScript/` for importing station data, train routes, and generating seat/carriage data. Requires `pandas`, `pymysql`, `sqlalchemy`.

## Frontend API

Frontend services in `12306/services/` communicate via gateway at `http://localhost:8080/api`. Auth token stored in localStorage, sent as `Authorization: Bearer <token>`.

## Payment Integration

Order service integrates Alipay sandbox. Configuration in `Services/order-service/src/main/resources/application.yml`:
- Sandbox gateway URL
- App ID and keys (RSA2 signing)
- Notify/return URLs
