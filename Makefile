.PHONY: test build up down burst burst-idempotency burst-limit

run:
	mvn spring-boot:run

test:
	mvn clean test

build:
	mvn clean package

up:
	docker compose up --build

down:
	docker compose down

burst:
	REQUESTS=$${REQUESTS:-1000} WORKERS=$${WORKERS:-200} python3 scripts/burst.py http://localhost:8080

burst-idempotency:
	MODE=idempotency REQUESTS=$${REQUESTS:-100} WORKERS=$${WORKERS:-50} python3 scripts/burst.py http://localhost:8080

burst-limit:
	MODE=per-user-limit REQUESTS=$${REQUESTS:-20} WORKERS=$${WORKERS:-20} python3 scripts/burst.py http://localhost:8080
