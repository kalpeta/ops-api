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

text
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

Pull latest + clean state:

bash
Copy code
git status
git pull
Run tests:

bash
Copy code
cd app
./mvnw test
Run the app once and hit probes:

bash
Copy code
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
Then run Probe 1 + Probe 2 quickly.

If Docker stack is part of the release:

bash
Copy code
docker compose -f docker/docker-compose.local.yml up -d --build
curl -s http://localhost:8080/actuator/health | jq
If something breaks: rollback plan (simple + reliable)
Mental model:

Your code change is a commit

Rollback = undo that commit (with history preserved) → rebuild → restart

Find the bad commit hash:

bash
Copy code
git log --oneline --max-count=10
Revert it (creates a new “undo” commit):

bash
Copy code
git revert <BAD_COMMIT_HASH>
Push the rollback:

bash
Copy code
git push
Rebuild + restart locally:

bash
Copy code
cd app
./mvnw test
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
Smoke test curls after rollback (minimum):

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
9) Failure drill: “Dependency down” (Level 4 — Slice 1)
This drill is specifically for when the upstream dependency stub behaves badly (fail / slow / timeout / bulkhead reject / circuit open).

A) Symptoms (what you’ll see)
Symptom 1 — Customer dependency-check returns 5xx
Example call:

bash
Copy code
curl -i -s "http://localhost:8080/customers/<CUSTOMER_ID>/dependency-check?mode=fail"
Possible results:

503 with stub body (upstream failure propagated)

504 (timeout after retries)

503 with error=CIRCUIT_OPEN (circuit breaker open, fail-fast)

429 with error=BULKHEAD_FULL (too many concurrent outbound calls)

Symptom 2 — Requests are “slow”
Example call:

bash
Copy code
curl -i -s "http://localhost:8080/customers/<CUSTOMER_ID>/dependency-check?mode=slow&delayMs=2000"
You’ll see:

request latency ~ delayMs

logs show outbound latency and which attempt succeeded/failed

Symptom 3 — Health still UP (important!)
Even if dependency calls fail, /actuator/health may still be UP.
That means “service is alive” — not “all dependencies are healthy”.

B) Where to look (fast triage order)
1) Confirm the app is up
bash
Copy code
curl -s http://localhost:8080/actuator/health | jq
If it’s not reachable → app is down, stop this drill and fix startup/runtime first.

2) Confirm the dependency stub endpoint itself
Directly call the stub:

bash
Copy code
curl -i -s "http://localhost:8080/_stub/dependency?mode=ok"
curl -i -s "http://localhost:8080/_stub/dependency?mode=fail"
curl -i -s "http://localhost:8080/_stub/dependency?mode=slow&delayMs=2000"
If these don’t behave as expected → the stub controller itself is broken.

3) Check logs (single request)
Run one failing call and immediately inspect logs for the SAME correlation id:

REQUEST_START ... /customers/.../dependency-check

OUTBOUND_ATTEMPT ...

OUTBOUND_END ... status=... latency_ms=...

REQUEST_END ... status=... latency_ms=...

Quick filters (if logging to a file):

bash
Copy code
grep "dependency-check" app.log | tail -n 50
grep "OUTBOUND_" app.log | tail -n 50
grep "CB_" app.log | tail -n 50
grep "BULKHEAD_" app.log | tail -n 50
What each implies:

OUTBOUND_END ... status=503 → upstream returned 503 (mode=fail)

OUTBOUND_END ... status=TIMEOUT_OR_IO → timeout / IO issue

CB_OPEN_FAILFAST or CB_TRANSITION ... OPEN → circuit breaker is open

BULKHEAD_FULL / BULKHEAD_REJECT → concurrency limit reached

4) Check metrics for “is it slow/failing?”
bash
Copy code
curl -s http://localhost:8080/actuator/metrics/http.server.requests | jq
If you want just this endpoint:

bash
Copy code
curl -s "http://localhost:8080/actuator/metrics/http.server.requests?tag=uri:/customers/{id}/dependency-check" | jq
Note: exact uri tag formatting can vary; if it doesn’t match, use logs to infer the uri tag value.

C) Mitigation steps (what to do right now)
Pick the branch that matches your symptom.

Case 1 — Upstream is failing (503) but app is healthy
Action:

Confirm the stub returns fail only when mode=fail

Switch to mode=ok to confirm the path works

If the circuit opened from repeated failures: stop sending fail traffic and wait for it to half-open automatically.

Confirm:

bash
Copy code
curl -i -s "http://localhost:8080/customers/<CUSTOMER_ID>/dependency-check?mode=ok"
Case 2 — Upstream is slow and causing timeouts (504)
Action:

Reduce delayMs to confirm behavior scales with delay

If timeouts happen: treat as a dependency performance issue.

Immediate mitigation is “degrade”: return stable error quickly (timeout / circuit open) instead of hanging.

Confirm:

bash
Copy code
curl -i -s "http://localhost:8080/customers/<CUSTOMER_ID>/dependency-check?mode=slow&delayMs=200"
curl -i -s "http://localhost:8080/customers/<CUSTOMER_ID>/dependency-check?mode=slow&delayMs=2000"
Case 3 — Bulkhead rejections (429 BULKHEAD_FULL)
Action:

You have too many concurrent dependency calls.

Reduce concurrency from caller side OR increase bulkhead capacity (config change) later.

Immediate mitigation: retry later (caller backoff), because bulkhead rejection is a load-shedding signal.

Confirm load-shedding:

fire multiple concurrent requests (e.g., from two terminals quickly or a small loop)

expect some 429 while others succeed

Case 4 — Circuit breaker open (503 CIRCUIT_OPEN)
Action:

Stop sending failing traffic (mode=fail) and wait waitDurationInOpenState (configured).

Then send a couple of mode=ok requests to allow recovery.

Confirm recovery:

bash
Copy code
# 1) while open, you should see CIRCUIT_OPEN
curl -i -s "http://localhost:8080/customers/<CUSTOMER_ID>/dependency-check?mode=ok"

# 2) after waiting, try again (expect success if dependency is healthy)
curl -i -s "http://localhost:8080/customers/<CUSTOMER_ID>/dependency-check?mode=ok"
D) Confirmation checks (prove it’s stable again)
Minimum confirmation sequence:

Health is UP

bash
Copy code
curl -s http://localhost:8080/actuator/health | jq
Dependency stub direct is OK

bash
Copy code
curl -i -s "http://localhost:8080/_stub/dependency?mode=ok"
Customer dependency-check OK

bash
Copy code
curl -i -s "http://localhost:8080/customers/<CUSTOMER_ID>/dependency-check?mode=ok"
Logs show clean flow:

REQUEST_END ... status=200

OUTBOUND_END ... status=200

no CB_OPEN_FAILFAST

no BULKHEAD_FULL

Optional: re-run a slow call and confirm latency matches expectation (no surprise timeouts).

Phase 5 — Kafka + Outbox + Idempotency
What “success” looks like in logs

When you create a customer, you should see both:

Outbox write (same transaction as customer creation)

OUTBOX_ENQUEUED ...

Poller publish

OUTBOX_SENT ...

Consumer receive/process

EVENT_CONSUMED ...

EVENT_PROCESSED ...

(and if duplicate): EVENT_DUPLICATE_IGNORED ...

To watch it live:

docker compose -f docker/docker-compose.local.yml logs -f app

Kafka drills
Drill A — Kafka down should NOT lose events (Outbox reliability)

Goal: prove “customer created while Kafka is down” still eventually produces an event.

Stop Kafka:

docker compose -f docker/docker-compose.local.yml stop kafka


Create a customer:

curl -i -X POST "http://localhost:8080/customers" \
  -H "Content-Type: application/json" \
  -d '{"name":"Kafka Down User","email":"kafkadown@example.com"}'


Expected:

Request should still succeed (customer saved).

App logs should show:

OUTBOX_ENQUEUED ...

OUTBOX_SEND_FAILED ... (poller cannot publish)

Start Kafka back:

docker compose -f docker/docker-compose.local.yml start kafka


Expected:

App logs soon show:

OUTBOX_SENT ...

EVENT_CONSUMED ... / EVENT_PROCESSED ...

Drill B — Duplicate events can happen; consumer must stay correct (Idempotency/dedupe)

Reality: duplicates can happen due to retries, producer uncertainty, consumer rebalances, etc.

Expected log when duplicates happen:

EVENT_DUPLICATE_IGNORED eventId=...

How to “force” the idea:

Re-run the app quickly / restart consumer / simulate re-delivery (depends on your setup).

The important invariant: same eventId must not be processed twice.

Event versioning mindset (compatibility rules)

Every event should carry a schema version (e.g., schemaVersion: 1).

Rules of thumb:

Consumer must accept older versions (don’t break old producers).

Adding a field is safe if it’s optional for consumers.

Removing/renaming a field is breaking unless you support both old+new fields for a period.

Expected behavior:

Consumer can handle v1 and v2 payloads where v2 adds an optional field.

Logs should still show EVENT_PROCESSED ... for both versions.

Trace & debug async (follow a workflow across HTTP → Kafka)
How to follow one request end-to-end

Send a request with your own correlation id:

curl -i -X POST "http://localhost:8080/customers" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: flow-001" \
  -d '{"name":"Trace Me","email":"trace@example.com"}'


In logs, search for corr=flow-001:

docker compose -f docker/docker-compose.local.yml logs app | grep "corr=flow-001"


You should be able to see:

REQUEST_START ...

OUTBOX_ENQUEUED ... corr=flow-001

OUTBOX_SENT ... corr=flow-001

EVENT_CONSUMED ... corr=flow-001 (or correlation id inside payload/headers)

EVENT_PROCESSED ... corr=flow-001

If you prefer to follow by eventId, grab it from the event JSON and grep it too.

Tests

Run tests:

cd app
./mvnw test


Note: Kafka-driven behavior is primarily validated via docker compose + logs in Phase 5.

DB quick checks (optional)

Customers:

docker exec -it ops-api-postgres psql -U ops -d opsdb \
  -c "select id, name, email, created_at, updated_at from customers order by created_at desc;"


Outbox queue (example columns may differ):

docker exec -it ops-api-postgres psql -U ops -d opsdb \
  -c "select id, aggregate_type, aggregate_id, event_type, status, attempts, created_at, sent_at from outbox_events order by created_at desc limit 20;"


Processed events (dedupe table):

docker exec -it ops-api-postgres psql -U ops -d opsdb \
  -c "select event_id, event_type, correlation_id, processed_at from processed_events order by processed_at desc limit 20;"

Common issues (fast fixes)
“Kafka works on my laptop but app container can’t connect”

Inside Docker, localhost means “the container itself”, not your machine.

Fix:

In compose, app should use:

SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092

For host access, use:

localhost:29092

“I don’t see EVENT logs”

Make sure you’re tailing the app container:

docker compose -f docker/docker-compose.local.yml logs -f app


Also ensure Kafka is healthy:

docker compose -f docker/docker-compose.local.yml ps

What to paste when asking for help

The curl command + response

The matching log chain:

REQUEST_START + REQUEST_END

OUTBOX_ENQUEUED

OUTBOX_SENT or OUTBOX_SEND_FAILED

EVENT_CONSUMED / EVENT_PROCESSED

Output of:

docker compose -f docker/docker-compose.local.yml ps
docker compose -f docker/docker-compose.local.yml logs --tail=200 app
docker compose -f docker/docker-compose.local.yml logs --tail=200 kafka