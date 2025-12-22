# Ops API Runbook (Phase 1)

## Common commands

Start DB:
```bash
docker compose -f docker/docker-compose.local.yml up -d

Stop DB:

docker compose -f docker/docker-compose.local.yml down


View logs:

docker compose -f docker/docker-compose.local.yml logs -f


Run app:

cd app
./mvnw spring-boot:run -Dspring-boot.run.profiles=local


Run tests:

cd app
./mvnw test

Common issues
Port 5433 already in use
lsof -i :5433

Docker not running

Open Docker Desktop and retry:

docker version