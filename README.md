# Ops API

Production-shaped backend built in phases.

## Phase 1 (current)
Local-only: Spring Boot + Postgres (Docker) + Flyway + Testcontainers.

## Run locally (Slice 1)

### 1) Start Postgres
From repo root:
```bash
docker compose -f docker/docker-compose.local.yml up -d
docker compose -f docker/docker-compose.local.yml ps

2) Run the app (profile=local)
cd app
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

App runs on:

http://localhost:8080

3) Verify with curl

Create customer:

curl -i -X POST "http://localhost:8080/customers" \
  -H "Content-Type: application/json" \
  -d '{"name":"Ada Lovelace","email":"ada@example.com"}'


Get customer (replace UUID):

curl -i "http://localhost:8080/customers/<UUID>"

4) Run tests
cd app
./mvnw test

DB quick check (optional)
docker exec -it ops-api-postgres psql -U ops -d opsdb -c "select id, name, email, created_at, updated_at fro