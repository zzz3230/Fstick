# Fstick Backend - Docker Compose Documentation

## Overview
This document describes the Docker Compose setup for the Fstick backend services.

## Services Architecture

### Microservices
- **Gateway Service** (8085) - API Gateway / Service Router
- **Registry Service** (8082) - Plugin Registry Management
- **Installation Service** (8083) - Plugin Installation Management
- **Integration Service** (8080) - Integration Hub
- **Runtime Service** (8084) - Runtime Execution Engine

### Database Services
- **registry-db** (5434) - PostgreSQL for Registry Service
- **installation-db** (5435) - PostgreSQL for Installation Service
- **integration-db** (5436) - PostgreSQL for Integration Service
- **runtime-db** (5437) - PostgreSQL for Runtime Service
- **gateway-db** (5438) - PostgreSQL for Gateway Service
- **dendrite-postgres** - PostgreSQL for Dendrite Matrix Server

### Cache Services
- **installation-redis** (6380) - Redis for Installation Service
- **integration-redis** (6381) - Redis for Integration Service
- **runtime-redis** (6382) - Redis for Runtime Service

### Storage Services
- **minio** (9010, 9011) - S3-Compatible Storage for Plugins
  - API: http://localhost:9010
  - Console: http://localhost:9011

### Matrix Server
- **dendrite** (8008, 8448) - Matrix Homeserver

## Port Mapping

| Service | Port | Purpose |
|---------|------|---------|
| Gateway | 8085 | API Gateway |
| Registry | 8082 | Plugin Registry API |
| Installation | 8083 | Installation API |
| Integration | 8080 | Integration API |
| Runtime | 8084 | Runtime API |
| Dendrite | 8008 | Matrix Client API |
| Dendrite | 8448 | Matrix Federation API |
| MinIO Console | 9011 | Storage Console UI |
| MinIO API | 9010 | S3-Compatible API |
| Registry DB | 5434 | PostgreSQL |
| Installation DB | 5435 | PostgreSQL |
| Integration DB | 5436 | PostgreSQL |
| Runtime DB | 5437 | PostgreSQL |
| Gateway DB | 5438 | PostgreSQL |
| Installation Redis | 6380 | Cache |
| Integration Redis | 6381 | Cache |
| Runtime Redis | 6382 | Cache |

## Running All Services

### Option 1: Run All Services Together (Recommended)

```bash
cd C:\F-stick\fstickbackend
docker-compose up -d
```

This will start all services in the correct dependency order.

### Option 2: Run Individual Services

Each service has its own docker-compose.yml file.

#### Registry Service
```bash
cd registry-service
docker-compose up -d
```

#### Installation Service
```bash
cd installation-service
docker-compose up -d
```

#### Integration Service
```bash
cd integration-service
docker-compose up -d
```

#### Runtime Service
```bash
cd runtime-service
docker-compose up -d
```

#### Gateway Service
```bash
cd gateway-service
docker-compose up -d
```

## Service Dependencies

```
Gateway Service
├── Registry Service
├── Installation Service
├── Integration Service
└── Runtime Service
    ├── Registry Service
    ├── Integration Service
    └── Gateway Service

Installation Service
├── Registry Service
└── Integration Service

Integration Service
└── Registry Service

Runtime Service
├── Registry Service
├── Integration Service
└── Gateway Service

All Services → Their respective Databases & Caches
Registry Service → MinIO Storage
```

## Environment Variables

All services are configured with appropriate environment variables for:
- Database connectivity
- Redis cache connectivity
- Service discovery (inter-service URLs)
- S3/MinIO configuration
- Spring Boot settings

## Data Persistence

All services use Docker volumes for data persistence:
- Databases: `{service}-db-data` volumes
- Redis: `{service}-redis-data` volumes
- MinIO: `minio-data` volume
- Dendrite: Various volumes for media, jetstream, search index

Volumes are automatically created and managed by Docker.

## Health Checks

All services include health checks:
- PostgreSQL databases check with `pg_isready`
- Redis instances check with `redis-cli ping`
- MinIO checks with HTTP health endpoint

Services wait for their dependencies to be healthy before starting.

## Network

All services are connected via the `fstick-network` bridge network, enabling inter-service communication by service names.

## Useful Commands

### View logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f registry-service
```

### Stop all services
```bash
docker-compose down
```

### Remove volumes (WARNING: Deletes all data!)
```bash
docker-compose down -v
```

### View running services
```bash
docker-compose ps
```

### Restart a service
```bash
docker-compose restart registry-service
```

## Access Points

- **Gateway**: http://localhost:8085
- **Registry**: http://localhost:8082
- **Installation**: http://localhost:8083
- **Integration**: http://localhost:8080
- **Runtime**: http://localhost:8084
- **Dendrite**: http://localhost:8008
- **MinIO Console**: http://localhost:9011 (admin / 11111111)
- **Matrix Federation**: https://localhost:8448

## Troubleshooting

### Services fail to start
1. Check if ports are already in use
2. Ensure Docker daemon is running
3. Check logs: `docker-compose logs service-name`

### Database connection errors
1. Wait for postgres containers to be healthy (check with `docker-compose ps`)
2. Verify database credentials in docker-compose.yml
3. Check network connectivity

### Service cannot reach other services
1. Verify service names are correct in URLS
2. Ensure all services are in the same network
3. Check firewall rules

## Configuration

To modify service configuration:
1. Edit the relevant service section in docker-compose.yml
2. Run `docker-compose up -d` to restart services
3. Or edit individual service docker-compose.yml files

## Notes

- The setup uses PostgreSQL 16 for all databases
- Redis 7-Alpine for cache layers
- MinIO for S3-compatible object storage
- Dendrite as the Matrix homeserver
- All services are configured for development/testing
- For production use, consider:
  - Using environment files (.env)
  - Setting appropriate resource limits
  - Configuring persistent volume backups
  - Using secrets management
  - Load balancing setup

