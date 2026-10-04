#!/usr/bin/env python3
"""Concurrent correctness smoke test for the seat reservation service."""
import concurrent.futures
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from collections import Counter

BASE_URL = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else os.getenv("BASE_URL", "http://localhost:8080")
MODE = os.getenv("MODE", "hot-seat")
REQUESTS = int(os.getenv("REQUESTS", "1000"))
WORKERS = int(os.getenv("WORKERS", "200"))
ADMIN_TOKEN = os.getenv("ADMIN_TOKEN", "admin")
TIMEOUT = int(os.getenv("TIMEOUT", "60"))


def http(method, path, body=None, token=None, headers=None):
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = f"Bearer {token}"
    if headers:
        h.update(headers)
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE_URL + path, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
            raw = r.read().decode()
            return r.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            payload = json.loads(raw)
        except Exception:
            payload = {"raw": raw}
        return e.code, payload
    except Exception as e:
        return 599, {"error": str(e)}


def create_show(seats, limit=4):
    body = {
        "name": f"burst-{uuid.uuid4()}",
        "seats": seats,
        "price_paise": 25000,
        "per_user_limit": limit,
    }
    return http("POST", "/shows", body, ADMIN_TOKEN)


def final_state(show_id):
    status, payload = http("GET", f"/shows/{show_id}")
    if status != 200:
        raise RuntimeError(f"GET show failed: {status} {payload}")
    total = payload["total_seats"]
    available = payload["available_seats"]
    held = payload["held_seats"]
    confirmed = payload["confirmed_seats"]
    print("\nFinal reconciliation")
    print("--------------------")
    print("total:      ", total)
    print("available:  ", available)
    print("held:       ", held)
    print("confirmed:  ", confirmed)
    print("sum:        ", available + held + confirmed)
    return total, available, held, confirmed


def hot_seat():
    status, show = create_show(["A12"] + [f"B{i}" for i in range(1, 100)])
    if status != 201:
        print("Could not create show:", status, show)
        return 1
    show_id = show["id"]
    print(f"Show: {show_id}\nMode: hot-seat\nHot seat: A12\nRequests: {REQUESTS}, workers: {WORKERS}")

    def one(i):
        user = f"burst-user-{i}"
        return http(
            "POST", f"/shows/{show_id}/reserve", {"seats": ["A12"]}, user,
            {"Idempotency-Key": str(uuid.uuid4()), "X-Request-Id": str(uuid.uuid4())},
        )[0]

    start = time.time()
    with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        statuses = list(pool.map(one, range(REQUESTS)))
    elapsed = time.time() - start
    counts = Counter(statuses)
    print("\nOutcome distribution")
    print("--------------------")
    for code, count in sorted(counts.items()):
        print(f"{code}: {count}")
    total, available, held, confirmed = final_state(show_id)
    print("elapsed:   ", f"{elapsed:.2f}s")
    unexpected_5xx = sum(v for k, v in counts.items() if k >= 500)
    ok = (counts.get(201, 0) == 1 and counts.get(409, 0) == REQUESTS - 1
          and unexpected_5xx == 0 and available + held + confirmed == total and confirmed == 1)
    print("\nPASS" if ok else "\nFAIL")
    return 0 if ok else 2


def idempotency():
    status, show = create_show(["A12", "A13", "A14"], limit=4)
    if status != 201:
        print("Could not create show:", status, show)
        return 1
    show_id = show["id"]
    key = f"same-key-{uuid.uuid4()}"
    print(f"Show: {show_id}\nMode: idempotency\nKey: {key}\nRequests: {REQUESTS}, workers: {WORKERS}")

    def one(_):
        status, payload = http(
            "POST", f"/shows/{show_id}/reserve", {"seats": ["A12"], "idempotency_key": key},
            "same-user", {"X-Request-Id": str(uuid.uuid4())},
        )
        return status, payload.get("reservation_id")

    with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        results = list(pool.map(one, range(REQUESTS)))
    counts = Counter(s for s, _ in results)
    ids = {rid for _, rid in results if rid}
    print("\nOutcome distribution")
    print("--------------------")
    for code, count in sorted(counts.items()):
        print(f"{code}: {count}")
    print("unique reservation IDs:", len(ids))
    total, available, held, confirmed = final_state(show_id)
    ok = (counts.get(201, 0) == REQUESTS and len(ids) == 1
          and available + held + confirmed == total and confirmed == 1)
    print("\nPASS" if ok else "\nFAIL")
    return 0 if ok else 2


def per_user_limit():
    seats = [f"A{i}" for i in range(1, 11)]
    status, show = create_show(seats, limit=4)
    if status != 201:
        print("Could not create show:", status, show)
        return 1
    show_id = show["id"]
    print(f"Show: {show_id}\nMode: per-user-limit\nLimit: 4\nRequests: {REQUESTS}, workers: {WORKERS}")

    def one(i):
        seat = seats[i % len(seats)]
        return http(
            "POST", f"/shows/{show_id}/reserve", {"seats": [seat]},
            "same-user", {"Idempotency-Key": f"limit-{i}", "X-Request-Id": str(uuid.uuid4())},
        )[0]

    with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        statuses = list(pool.map(one, range(REQUESTS)))
    counts = Counter(statuses)
    print("\nOutcome distribution")
    print("--------------------")
    for code, count in sorted(counts.items()):
        print(f"{code}: {count}")
    total, available, held, confirmed = final_state(show_id)
    ok = (confirmed <= 4 and counts.get(201, 0) == 4
          and sum(v for k, v in counts.items() if k >= 500) == 0
          and available + held + confirmed == total)
    print("\nPASS" if ok else "\nFAIL")
    return 0 if ok else 2


if __name__ == "__main__":
    modes = {"hot-seat": hot_seat, "idempotency": idempotency, "per-user-limit": per_user_limit}
    if MODE not in modes:
        print("MODE must be one of: hot-seat, idempotency, per-user-limit")
        raise SystemExit(2)
    raise SystemExit(modes[MODE]())
