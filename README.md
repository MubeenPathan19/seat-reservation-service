# Seat Reservation Service

Backend take-home exercise for Paytm Money — Deploy & Observe round.

## Assignment fit

This implementation uses a single PostgreSQL database as the system of record. Reservation correctness is enforced by database transactions and row locks rather than an application-level read-then-write check.

- Money is stored and returned as integer paise (`BIGINT` / Java `long`).
- Multi-seat requests are **all-or-nothing**.
- The chosen release model is explicit cancellation; reservations are immediately confirmed, so `held_seats` is always `0`.
- Authentication is intentionally a small opaque bearer-token mechanism for the take-home: the token value is the user identity. The configured admin token is the only token with the admin role. There is no user ID accepted from the request body.
- PostgreSQL is used in Docker so a clean checkout can build and run without the author's local database.

## Stack

- Java 21
- Spring Boot 3.5
- Spring Data JPA / Hibernate
- PostgreSQL 17
- Flyway
- Spring Security
- Micrometer + Prometheus
- Docker / Docker Compose

## Concurrency design

### No double-sell

`POST /shows/{id}/reserve` runs in one database transaction. Requested seat names are normalized, de-duplicated and sorted. The service then executes a JPA `PESSIMISTIC_WRITE` query for all requested seat rows in deterministic seat-number order. Only after all locks are held does it verify that every seat is `AVAILABLE` and change them to `CONFIRMED`.

A concurrent request for the same seat therefore waits for the first transaction. After the winner commits, the loser sees `CONFIRMED` and receives `409 SEAT_TAKEN`. There is no application-level check followed by an unlocked write.

### Per-user limit

A `(show_id, user_id)` row in `user_show_locks` is inserted if necessary and then pessimistically locked before the current confirmed-seat count is checked. This serializes concurrent reservations by the same user even when they target different seats.

### Multi-seat requests

The policy is **all-or-nothing**. If any requested seat is missing or already taken, the whole request is rejected with `409` and no seat from that request is confirmed. All requested seat rows are locked in sorted order, which also reduces deadlock risk between competing multi-seat requests.

## Idempotency

The idempotency key can be supplied in either the `Idempotency-Key` header or the JSON body as `idempotency_key`. The header wins if both are supplied.

`idempotency_keys` has a unique constraint on `(user_id, show_id, idempotency_key)`. The service inserts the row using PostgreSQL `ON CONFLICT DO NOTHING`, then pessimistically locks that row before deciding what to do. The stored request hash is SHA-256 of the canonical sorted seat list.

- Same user + show + same key + same seats → original reservation is returned.
- Same user + show + same key + different seats → `409 IDEMPOTENCY_KEY_REUSED`.
- Different bearer-token identities are different users, even if they choose the same key string.

This also makes concurrent retries of the same key serialize around the same database row.

## Release model

The assignment permits either cancellation or timed holds. This implementation chooses **explicit cancellation**: `POST /reservations/{id}/cancel`. Only the owner can cancel. The reservation row is locked first and its seat rows are then locked in deterministic order. A seat is released only if it still belongs to that reservation, so a cancellation cannot resurrect a seat that has already moved to another reservation.

Reservations are confirmed immediately; therefore the state machine in this version has `AVAILABLE` and `CONFIRMED` seat states, with `HELD` represented by a count of zero.

## API

### 1. Create show — admin

```http
POST /shows
Authorization: Bearer <admin-token>
Content-Type: application/json
```

```json
{
  "name": "friday-night",
  "seats": ["A1", "A2", "A3", "A12"],
  "price_paise": 25000,
  "per_user_limit": 4
}
```

`per_user_limit` is optional; the default is 4.

### 2. Get show state

```http
GET /shows/{id}
```

The response contains every seat plus counts. The reconciliation invariant is:

```text
available_seats + held_seats + confirmed_seats == total_seats
```

In this implementation `held_seats == 0` because reservations are immediately confirmed.

### 3. Reserve

```http
POST /shows/{id}/reserve
Authorization: Bearer user-001
Idempotency-Key: booking-001
Content-Type: application/json
```

```json
{ "seats": ["A12"] }
```

The same key may instead be included in the body:

```json
{ "seats": ["A12"], "idempotency_key": "booking-001" }
```

Success is HTTP 201:

```json
{
  "reservation_id": "...",
  "show_id": "...",
  "user_id": "user-001",
  "seats": ["A12"],
  "amount_paise": 25000,
  "status": "confirmed"
}
```

Domain conflicts such as a taken seat, limit violation or idempotency mismatch return HTTP 409.

### 4. Cancel

```http
POST /reservations/{reservationId}/cancel
Authorization: Bearer user-001
```

Only the reservation owner can cancel it.

## Health and metrics

Liveness:

```text
GET /actuator/health/liveness
```

Readiness includes both application readiness state and the PostgreSQL `db` health indicator:

```text
GET /actuator/health/readiness
```

If the database is unavailable, readiness is not healthy.

Prometheus:

```text
GET /actuator/prometheus
```

Important metrics include:

```text
reservations_confirmed_total
reservations_declined_total{reason="seat-taken"}
reservations_declined_total{reason="per-user-limit"}
reservations_declined_total{reason="idempotent-replay"}
reservations_declined_total{reason="idempotency-mismatch"}
seats_available
seats_confirmed
database_up
```

Logs are JSON structured and include `request_id` and `user_id` fields when available. The request ID is accepted from `X-Request-Id` or generated by the service and returned in the response header.

## Run locally

Requirements: Docker and Docker Compose.

```bash
docker compose up --build
```

API:

```text
http://localhost:8080
```

The default local admin token is `change-me`. Override it before sharing the service:

```bash
ADMIN_TOKEN='replace-me' DB_PASSWORD='replace-db-password' docker compose up --build
```

For direct Maven development, use Java 21 and Maven 3.9+ with PostgreSQL running:

```bash
DB_CONNECTION_STRING=postgresql://postgres:postgres@localhost:5432/seat_reservation mvn clean test
DB_CONNECTION_STRING=postgresql://postgres:postgres@localhost:5432/seat_reservation mvn clean package
```

On Windows PowerShell, set the variable first with `$env:DB_CONNECTION_STRING = "postgresql://postgres:postgres@localhost:5432/seat_reservation"`.

## One-command burst

The burst script creates a fresh show and storms one hot seat from many different token identities. It prints the outcome distribution and final reconciliation.

Local smoke test:

```bash
REQUESTS=1000 WORKERS=200 ADMIN_TOKEN=change-me python3 scripts/burst.py http://localhost:8080
```

The same command can target the deployed URL:

```bash
REQUESTS=20000 WORKERS=500 ADMIN_TOKEN=<admin-token> python3 scripts/burst.py https://<live-host>
```

Expected hot-seat correctness:

```text
201: 1
409: REQUESTS-1
5xx: 0
```

and:

```text
available + held + confirmed == total
confirmed == 1
```

The script also supports `MODE=idempotency` to storm the same idempotency key concurrently and `MODE=per-user-limit` to exercise the per-user limit.

Examples:

```bash
MODE=idempotency REQUESTS=100 WORKERS=50 python3 scripts/burst.py http://localhost:8080
MODE=per-user-limit REQUESTS=20 WORKERS=20 python3 scripts/burst.py http://localhost:8080
```

## Docker / clean checkout

The repository's Dockerfile uses Maven for the build stage and a JRE-only runtime image. Docker Compose starts PostgreSQL first, waits for its health check, then starts the API. Flyway creates the schema on startup. No local Oracle installation is required.

## Project structure

```text
seat-reservation-service/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── Makefile
├── README.md
├── WRITEUP.md
├── scripts/
│   └── burst.py
└── src/
    ├── main/java/io/github/mubeenpathan19/seatreservation/
    │   ├── api/
    │   ├── config/
    │   ├── domain/
    │   ├── exception/
    │   ├── observability/
    │   ├── repository/
    │   ├── security/
    │   └── service/
    └── main/resources/
        ├── application.yml
        └── logback-spring.xml
```

## Notes for the evaluator

The repository deliberately keeps the architecture small: one Spring Boot service and one PostgreSQL database. The database is the source of truth for seat state, per-user serialization, and idempotency. No Redis, Kafka or Kubernetes is required for correctness.

## Deploy to Render

A `render.yaml` Blueprint is included for a Docker web service plus PostgreSQL. Create a new Blueprint from the repository, provide the `ADMIN_TOKEN` secret when prompted, and let Render provision the web service and database. The service health check is `/actuator/health/readiness`.

After deployment, use the generated `https://<service>.onrender.com` URL as the live URL in the submission email. Render's service dashboard provides the application logs; `/actuator/prometheus` provides the application metrics endpoint.

The exact live URL is intentionally not hard-coded in this repository because it is generated by the deployer/workspace.

See `DEPLOYMENT.md` for the Render deployment checklist and final pre-submission commands.
