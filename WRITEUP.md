# Design Write-up — Seat Reservation at Scale

## 1. Atomic decision

PostgreSQL is the system of record. A reservation is one database transaction.

The exact seat decision is made by pessimistic database row locking:

1. Normalize and sort requested seat numbers.
2. Lock the `(show_id, user_id)` row in `user_show_locks` to serialize the user's concurrent requests for that show.
3. Lock every requested seat row with JPA `PESSIMISTIC_WRITE`, ordered by seat number.
4. Verify that all locked seats are `AVAILABLE`.
5. Create the reservation and change all requested seats to `CONFIRMED` in the same transaction.
6. Commit.

The important property is that there is no unlocked `isAvailable()` read followed by a later write. The row lock is held until transaction commit. For a hot seat, one transaction gets the lock and confirms it; concurrent transactions wait and then see `CONFIRMED`, returning `409 SEAT_TAKEN`.

### Multi-seat and deadlocks

The implementation chooses **all-or-nothing** semantics. If one requested seat is unavailable or missing, no seat from that request is confirmed. All seat rows are locked in deterministic lexicographic seat-number order. This gives competing multi-seat transactions the same lock acquisition order and materially reduces deadlock risk.

## 2. Per-user limit under concurrency

A seat-row lock alone does not protect a per-user limit when a user sends ten requests for ten different seats: each request could otherwise read the same current count.

The service therefore uses a persistent `(show_id, user_id)` lock row. Every reservation request for that user/show locks this row before counting confirmed seats. The count and subsequent seat confirmation happen in the same transaction. With a default limit of 4, ten concurrent requests cannot collectively confirm more than four seats for that user.

## 3. Idempotency

The key is accepted in either the `Idempotency-Key` header or `idempotency_key` body field.

The `idempotency_keys` table stores:

- authenticated `user_id`
- `show_id`
- idempotency key
- SHA-256 hash of the canonical sorted seat list
- resulting `reservation_id`
- creation time

A unique database constraint on `(user_id, show_id, idempotency_key)` establishes one logical idempotency record. The service uses PostgreSQL `INSERT ... ON CONFLICT DO NOTHING`, then pessimistically locks the row. This means concurrent retries of the same key serialize.

If `reservation_id` is already populated, the original reservation is returned. If the request hash differs, the service returns `409 IDEMPOTENCY_KEY_REUSED`. No second reservation is created.

The idempotency scope includes authenticated user identity because the same opaque key string from two different users is not treated as the same user's operation.

## 4. Holds and release

The assignment allows either explicit cancellation or time-boxed holds. This implementation chooses **explicit cancellation**. Reservations are confirmed immediately, so there is no temporary `HELD` state and `held_seats` is always zero.

`POST /reservations/{id}/cancel` first locks the reservation and checks ownership. It then locks its seat rows in deterministic order and releases only seats whose `reservation_id` still equals the cancellation target. Finally it marks the reservation `CANCELLED`.

That ownership and reservation-ID check prevents a release from resurrecting a seat belonging to a later reservation. A cancelled seat becomes `AVAILABLE` and can then be booked again.

## 5. Consistency vs availability under a partition

The service deliberately prefers correctness over availability when the database is unavailable. PostgreSQL is the single source of truth; a reservation is not considered successful until the database transaction commits.

During database failure, readiness fails because the readiness group includes the database health indicator. Requests that cannot obtain a correct database decision are not converted into successful reservations. This sacrifices availability during a database outage rather than risking uncertain or duplicate seat ownership.

A production system would add database failover and carefully designed retry semantics, but it would keep the same principle: a client must not be told that a seat was confirmed without an authoritative committed decision.

## 6. Reconciliation invariant

There is exactly one `seats` row per seat in a show. In this implementation each row is either `AVAILABLE` or `CONFIRMED`. Therefore:

```text
available + held + confirmed == total_seats
```

with `held == 0`, so the operational invariant is:

```text
available + confirmed == total_seats
```

`GET /shows/{id}` calculates the counts from the authoritative seat rows. Prometheus exposes the corresponding `seats_available` and `seats_confirmed` gauges.

## 7. Observability

### Health

- `/actuator/health/liveness` checks application liveness.
- `/actuator/health/readiness` includes both readiness state and the PostgreSQL database health indicator.

### Metrics

The service exposes Prometheus metrics for:

- `reservations_confirmed_total`
- `reservations_declined_total{reason="seat-taken"}`
- `reservations_declined_total{reason="per-user-limit"}`
- `reservations_declined_total{reason="idempotent-replay"}`
- `reservations_declined_total{reason="idempotency-mismatch"}`
- `seats_available`
- `seats_confirmed`
- `database_up`

### Logs

Logs are structured JSON and include a correlation/request ID. The service accepts `X-Request-Id`, generates one when absent, returns it in the response header, and puts it into MDC. Authenticated user identity is also included in MDC.

### What I would page at 2am

- readiness failures or database connectivity failures
- sustained HTTP 5xx responses
- database connection-pool exhaustion
- abnormal reservation latency
- reconciliation mismatches
- unexpected growth in failed transaction/deadlock rates
- a sudden spike in application errors during an on-sale event

## 8. Burst testing

The repository contains `scripts/burst.py`. Its default mode creates a fresh show and sends concurrent requests from different user identities to one hot seat. It prints HTTP outcome distribution and final reconciliation.

The same script has dedicated modes for concurrent idempotent retries and per-user-limit contention.

A previously executed local hot-seat run used 1,000 concurrent reservation attempts with 200 workers and produced:

```text
201: 1
409: 999
total:      100
available:  99
confirmed:  1
sum:        100
PASS
```

This demonstrates the tested hot-seat behaviour; it is not presented as proof of an unexecuted 20,000-request run. The evaluator can run the same script against the live deployment with `REQUESTS=20000`.

## 9. AI usage

AI tools were used as an engineering assistant for project scaffolding, documentation drafting, concurrency edge-case review, API review, and test-scenario generation.

The final implementation decisions were reviewed against the assignment requirements and exercised through API and concurrency tests. In particular, the database transaction boundaries, pessimistic seat locking, deterministic lock order, per-user serialization, idempotency record locking, and release ownership checks were deliberately chosen and verified rather than blindly copied from generated output.

During the interview, I can explain the locking and transaction flow and extend the implementation live.

## 10. What I would do next

For a production ticketing service I would next consider:

- stronger OAuth2/JWT authentication and token lifecycle
- rate limiting and abuse protection
- dashboards and alerting with latency/error SLOs
- distributed tracing
- measured connection-pool and database-index tuning
- realistic load tests with multiple hot-seat distributions
- payment state handling separate from seat ownership
- an outbox/eventing mechanism for downstream systems
- time-boxed holds if product requirements require them
- database HA/failover and an explicit consistency model before considering multi-region writes
