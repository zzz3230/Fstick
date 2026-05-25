# Fstick - Quick Start Guide

## Prerequisites
- Docker Desktop installed and running
- Docker Compose v3.8+
- At least 8GB RAM available
- Ports 5432-5438, 6379-6382, 8008, 8080-8085, 8448, 9010-9011 available

## Quick Start - Run Everything

### 1. Start All Services at Once
```bash
cd C:\F-stick\fstickbackend
docker-compose up -d
```

This command will:
- Start all databases (PostgreSQL x5)
- Start all cache services (Redis x3)
- Start all application services (Gateway, Registry, Installation, Integration, Runtime)
- Start MinIO storage
- Start Dendrite Matrix server
- Configure all service dependencies

### 2. Verify Services are Running
```bash
docker-compose ps
```

Expected output shows all services in "Up" state with healthy status ✓

### 3. Access Services

#### Frontend (Element)
- URL: http://localhost:3000 (if running locally with npm start)
- Matrix Server: http://localhost:8008

#### Backend API Gateway
- URL: http://localhost:8085
- All backend services accessible through this gateway

#### Individual Services
- Registry: http://localhost:8082
- Installation: http://localhost:8083
- Integration: http://localhost:8080
- Runtime: http://localhost:8084
- Gateway: http://localhost:8085

#### Storage (MinIO)
- Console: http://localhost:9011
- Credentials: admin / 11111111
- API: http://localhost:9010

#### Marketplace Integration
- Frontend talks to: http://localhost:8085/registry/api/v1/plugins
- (via Gateway Service)

## Running Individual Services

### Option 1: Single Service
```bash
cd registry-service
docker-compose up -d
```

### Option 2: Specific Service from Main Compose
```bash
cd C:\F-stick\fstickbackend
docker-compose up -d registry-service registry-db minio
```

## Important Environment Details

### Service Internal URLs (container-to-container)
All services can reach each other via service names:
- Registry: `http://registry-service:8082`
- Installation: `http://installation-service:8081`
- Integration: `http://integration-service:8080`
- Runtime: `http://runtime-service:8084`

### Database Credentials
- **Registry DB**: admin / 1
- **Other DBs**: postgres / postgres

### MinIO Credentials
- Username: admin
- Password: 11111111

## Common Commands

### View all logs
```bash
docker-compose logs -f
```

### View specific service logs
```bash
docker-compose logs -f gateway-service
```

### Restart a service
```bash
docker-compose restart registry-service
```

### Stop all services
```bash
docker-compose down
```

### Remove all data (WARNING: Deletes everything!)
```bash
docker-compose down -v
```

### Check service status
```bash
docker-compose ps
docker-compose ps registry-service
```

### Execute command in container
```bash
docker-compose exec registry-service bash
docker-compose exec registry-db psql -U admin -d registry_db
```

## Troubleshooting

### Services won't start
1. Check if Docker is running
2. Verify ports are available: `netstat -ano | findstr :8085`
3. Check logs: `docker-compose logs`
4. Clean up: `docker-compose down -v` and try again

### Database connection errors
1. Wait 30 seconds for databases to initialize
2. Check health: `docker-compose ps` (should show healthy)
3. Manual check: `docker-compose exec registry-db pg_isready -U admin`

### Service can't connect to another service
1. Verify service names in URLs match docker-compose.yml
2. Ensure all services are in same network (fstick-network)
3. Check logs for connection errors

### Port already in use
1. Find what's using the port: `netstat -ano | findstr :PORT`
2. Either:
   - Kill the process: `taskkill /PID PID_NUMBER /F`
   - Or modify port in docker-compose.yml

### Need to rebuild containers
```bash
docker-compose down
docker system prune -a
docker-compose up -d --build
```

## Configuration Files

### Main Configuration
- **C:\F-stick\fstickbackend\docker-compose.yml** - Main orchestration file
- **C:\F-stick\fstickbackend\PORT_CONFIGURATION.md** - Port mapping reference
- **C:\F-stick\fstickbackend\DOCKER_COMPOSE_README.md** - Detailed documentation

### Frontend Configuration
- **C:\F-stick\fstickfrontend\element-web\apps\web\config.json**
  - `fstick_marketplace_api_url`: Points to gateway registry endpoint

### Individual Service Configs
- Each service has its own docker-compose.yml in service directory:
  - `registry-service/docker-compose.yml`
  - `installation-service/docker-compose.yml`
  - `integration-service/docker-compose.yml`
  - `runtime-service/docker-compose.yml`
  - `gateway-service/docker-compose.yml`

## Development Workflow

### 1. Start backend services
```bash
cd C:\F-stick\fstickbackend
docker-compose up -d
```

### 2. Verify backend is ready
```bash
curl http://localhost:8085/health
```

### 3. Start frontend (in separate terminal)
```bash
cd C:\F-stick\fstickfrontend\element-web
npm install
npm start
```

### 4. Access in browser
- http://localhost:3000 (Element frontend)
- Configure homeserver: http://localhost:8008

### 5. Test marketplace
- Open Element
- Navigate to Marketplace tab
- Should see registry plugins

## Testing APIs

### Test Gateway
```bash
curl http://localhost:8085/health
```

### Test Registry
```bash
curl http://localhost:8082/health
curl http://localhost:8082/api/v1/plugins
```

### Test MinIO
```bash
curl http://localhost:9010/minio/health/live
```

### Test Dendrite
```bash
curl http://localhost:8008/_matrix/client/versions
```

## Performance Tips

1. **Resource Allocation**: Ensure Docker has 8GB+ RAM
2. **Disk Space**: ~10GB recommended for all containers and volumes
3. **Network**: Use bridge network (default) for internal communication
4. **Persistence**: Volumes are persistent across container restarts

## Next Steps

1. ✅ All services running
2. ✅ Databases initialized
3. ✅ Cache systems ready
4. ✅ Storage available
5. → Start developing!

## Additional Resources

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Port Configuration Details](./PORT_CONFIGURATION.md)
- [Full Docker Compose README](./DOCKER_COMPOSE_README.md)
- [Element Client Documentation](../fstickfrontend/element-web/README.md)

## Support

For issues or questions:
1. Check logs: `docker-compose logs service-name`
2. Verify health: `docker-compose ps`
3. Check ports: `netstat -ano | findstr :PORT`
4. Review configuration files

---

**Version**: 1.0
**Last Updated**: 2026-05-25
**Services**: 5 (Gateway, Registry, Installation, Integration, Runtime) + Matrix

