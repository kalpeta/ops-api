# Ops API Runbook (Phase 2)

This is the “2am doc” for Ops API.  
Goal: quickly answer **Is it up? Is it slow? Is it failing? Where do I look next?**

---

## 0) Quick mental model (30 seconds)

We observe the system using 3 signal types:

1) **Health**: “Is the service alive and ready?”
2) **Metrics**: “Is it slow or overloaded?”
3) **Logs**: “What happened for one request? How long? What status? Which id?”

We already log on every request:
- `REQUEST_START`
- `REQUEST_END status=... latency_ms=...`
…and include:
- `corr` (correlation id)
- `trace/span`

So the standard workflow is:

**Health → Metrics → Logs → Pinpoint → Verify**

---

## 1) Common commands

### Start the local DB (Postgres)
```bash
docker compose -f docker/docker-compose.local.yml up -d
Stop the local DB
bash
Copy code
docker compose -f docker/docker-compose.local.yml down
View DB logs
bash
Copy code
docker compose -f docker/docker-compose.local.yml logs -f
Run the app (local profile)
bash
Copy code
cd app
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
Run tests
bash
Copy code
cd app
./mvnw test
2) Health checks (is it up?)
Check health (basic)
bash
Copy code
curl -s http://localhost:8080/actuator/health | jq
Expected:

json
Copy code
{ "status": "UP" }
If not UP:

If you get curl: (7) Failed to connect → app not running or wrong port.

If you get DOWN → app started but a dependency is unhealthy (often DB).

3) Metrics: find what is slow
List all available metrics
bash
Copy code
curl -s http://localhost:8080/actuator/metrics | jq '.names | sort | .[:40]'
Check HTTP server request metrics (Spring default)
This metric measures latency for every endpoint handler.

bash
Copy code
curl -s http://localhost:8080/actuator/metrics/http.server.requests | jq
How to read it (intuition):

Metrics are counters + timers

A Timer has: COUNT / TOTAL_TIME / MAX (and later percentiles if enabled)

We use it to find endpoints that are being hit and are slow

Filter by URI (endpoint)
Most useful query is: “show metrics only for one URI”.

Example for customers create (if your URI is /customers):

bash
Copy code
curl -s "http://localhost:8080/actuator/metrics/http.server.requests?tag=uri:/customers" | jq
Example for health:

bash
Copy code
curl -s "http://localhost:8080/actuator/metrics/http.server.requests?tag=uri:/actuator/health" | jq
If you’re not sure about the exact uri tag values:

Use logs (REQUEST_START prints the path)

Then try the same string in the tag query

Check our custom timer (if enabled)
We added a stable custom meter name:

bash
Copy code
curl -s http://localhost:8080/actuator/metrics/ops.customers.create | jq
Expected after calling POST /customers at least once:

COUNT > 0

4) Logs: identify error spikes + debug one failure
What our log lines mean
Every request produces:

bash
Copy code
[corr=...] [trace=...] [span=...] ... REQUEST_START POST /customers
[corr=...] [trace=...] [span=...] ... REQUEST_END   POST /customers status=201 latency_ms=115
corr = app-generated id (or client provided) — best for user support tickets

trace/span = tracing ids — best for cross-service tracing later

status = HTTP status code returned

latency_ms = time spent in our app for that request

Find “error spikes” (lots of 5xx)
If you saved logs to a file (optional):

bash
Copy code
# Example: if you redirected logs to app.log
grep "REQUEST_END" app.log | grep "status=5"
Debug one failing request using correlation id
If a user reports: “I got error with X-Correlation-Id=abc...”
you can search logs for that id.

bash
Copy code
# Replace with the id from the response header
grep "corr=abc" app.log
Even without a file, in terminal you can visually match by the [corr=...] value.

5) The 5 production probes (curl checks)
These are the “smoke tests” you run to confirm core system behavior.

Tip: keep terminal history of these commands.

Probe 1 — Health
bash
Copy code
curl -i -s http://localhost:8080/actuator/health
Probe 2 — Create a customer
bash
Copy code
curl -i -s -X POST "http://localhost:8080/customers" \
  -H "Content-Type: application/json" \
  -d '{"name":"Probe Customer","email":"probe-customer@example.com"}'
Probe 3 — Get that customer by id
Take the id from Probe 2 response and run:

bash
Copy code
curl -i -s "http://localhost:8080/customers/<CUSTOMER_ID>"
Probe 4 — Create a task for that customer (plus note in same transaction)
bash
Copy code
curl -i -s -X POST "http://localhost:8080/customers/<CUSTOMER_ID>/tasks" \
  -H "Content-Type: application/json" \
  -d '{"title":"Probe Task"}'
Probe 5 — List tasks summary (latest note snippet)
bash
Copy code
curl -i -s "http://localhost:8080/customers/<CUSTOMER_ID>/tasks/summary"
Expected:

response contains tasks with latestNoteSnippet populated

6) Release checklist + rollback thinking (Phase 3 — Level 3 / Slice 1)
Before you “release” (even locally)
This is the mindset: don’t ship blind.

Pull latest + clean state

bash
Copy code
git status
git pull
Run tests

bash
Copy code
cd app
./mvnw test
Run the app once and hit probes

bash
Copy code
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
Then run Probe 1 + Probe 2 quickly.

If Docker stack is part of the release

bash
Copy code
docker compose -f docker/docker-compose.local.yml up -d --build
curl -s http://localhost:8080/actuator/health | jq
If something breaks: rollback plan (simple + reliable)
Mental model:

Your code change is a commit

Rollback = undo that commit (with history preserved) → rebuild → restart

Find the bad commit hash

bash
Copy code
git log --oneline --max-count=10
Revert it (creates a new “undo” commit)

bash
Copy code
git revert <BAD_COMMIT_HASH>
Push the rollback

bash
Copy code
git push
Rebuild + restart locally

bash
Copy code
cd app
./mvnw test
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
Smoke test curls after rollback
Run these (minimum):

Probe 1 (health)

Probe 2 (create customer)

Probe 3 (get customer)

If these pass, you’re back to a stable baseline.

7) Common issues (fast fixes)
Port 5433 already in use
bash
Copy code
lsof -i :5433
Fix:

Stop the process using it OR change compose to another port.

Docker not running
Open Docker Desktop and retry:

bash
Copy code
docker version
App cannot connect to DB
Symptoms:

health might be DOWN

startup error mentions datasource connection

Fix checklist:

DB container running:

bash
Copy code
docker ps
Can you connect to Postgres container?

bash
Copy code
docker exec -it ops-api-postgres psql -U ops -d opsdb -c "select 1;"
Confirm local profile is used:

bash
Copy code
cd app
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
Flyway migration mismatch
Symptoms:

startup fails mentioning Flyway checksum/history

Fix:

Don’t delete random rows from flyway history.

If you changed a migration that already ran, create a NEW migration instead.

8) What to paste when asking for help
When something breaks, paste:

The failing curl command + output

The two log lines: REQUEST_START + REQUEST_END for that request (same corr id)

If startup fails: the first error stack trace block

Output of:

bash
Copy code
docker ps
docker compose -f docker/docker-compose.local.yml logs --tail=100