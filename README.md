# Ops API

Production-shaped backend built in phases.

## Phase 1 (current)
Local-only: Spring Boot + Postgres (Docker) + Flyway + Testcontainers.

## Run locally (Level 1)

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

Customers API
Create customer
curl -i -X POST "http://localhost:8080/customers" \
  -H "Content-Type: application/json" \
  -d '{"name":"Ada Lovelace","email":"ada@example.com"}'

Get customer by id
curl -i "http://localhost:8080/customers/<UUID>"

List customers (pagination)
curl -i "http://localhost:8080/customers?limit=2&offset=0"
curl -i "http://localhost:8080/customers?limit=2&offset=2"

Update customer
curl -i -X PUT "http://localhost:8080/customers/<UUID>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Updated Name","email":"updated@example.com"}'

Delete customer
curl -i -X DELETE "http://localhost:8080/customers/<UUID>"

Run tests
cd app
./mvnw test

DB quick check (optional)
docker exec -it ops-api-postgres psql -U ops -d opsdb -c "select id, name, email, created_at, updated_at from customers order by created_at desc;"