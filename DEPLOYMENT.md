# Deployment checklist

## Local Docker

```bash
cp .env.example .env
# edit .env if desired
docker compose up --build
```

Verify:

```bash
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/actuator/prometheus
```

## Render

The repository contains `render.yaml` for a Docker web service and PostgreSQL database.

1. Push the repository to GitHub with the full incremental commit history.
2. In Render, create a Blueprint from the GitHub repository.
3. Review the web service and PostgreSQL database defined by `render.yaml`.
4. Supply `ADMIN_TOKEN` when prompted for the secret environment variable.
5. Deploy.
6. Wait for the service health check at `/actuator/health/readiness` to become healthy.
7. Copy the generated HTTPS service URL into the Paytm submission email.
8. Open the Render service's Logs view while running the burst script; this provides the public-platform operational evidence requested by the assignment.
9. Give the evaluator these endpoints:
   - `<LIVE_URL>/shows/<show-id>`
   - `<LIVE_URL>/actuator/health/liveness`
   - `<LIVE_URL>/actuator/health/readiness`
   - `<LIVE_URL>/actuator/prometheus`

## Pre-submission verification

Run the following against the live URL:

```bash
REQUESTS=1000 WORKERS=200 ADMIN_TOKEN=<admin-token> python3 scripts/burst.py https://<live-host>
MODE=idempotency REQUESTS=100 WORKERS=50 python3 scripts/burst.py https://<live-host>
MODE=per-user-limit REQUESTS=20 WORKERS=20 python3 scripts/burst.py https://<live-host>
```

Then run the evaluator-scale hot-seat test:

```bash
REQUESTS=20000 WORKERS=500 ADMIN_TOKEN=<admin-token> TIMEOUT=120 python3 scripts/burst.py https://<live-host>
```

The expected hot-seat property is one `201`, the rest `409`, zero 5xx, and a final seat reconciliation with exactly one confirmed hot seat.
