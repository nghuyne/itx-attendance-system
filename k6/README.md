# k6 Performance Tests

Initialized as part of `[TF] Test Framework` (R-005 mitigation groundwork — see
`_bmad-output/test-artifacts/test-design-architecture.md`). `smoke.js` runs two
scenarios covering both halves of P0-012's acceptance criteria (per
`_bmad-output/test-artifacts/automation-summary.md`, Bước 4):

- `history_smoke` — `GET /api/attendance/history` under `K6_SMOKE_VUS` concurrent users (default 20; raise toward 200 for the separate full load run).
- `checkin_smoke` — a single `POST /api/attendance/check-in` call, asserting the check-in flow completes in <30s. Single-shot by design: the endpoint enforces one check-in per employee per day, so it can't run as a multi-iteration scenario against the shared smoke account.

## Prerequisites

- [k6](https://k6.io/docs/get-started/installation/) installed locally, or use the `grafana/k6` Docker image.
- Backend stack running: `docker compose up -d mysql minio mailhog backend` (from repo root).

## Run

```bash
k6 run k6/smoke.js

# Full 200-concurrent load run (P0-012):
K6_SMOKE_VUS=200 k6 run k6/smoke.js
```

**Do not use `K6_VUS`/`K6_DURATION` to scale this script** — those are k6's own reserved global-option env vars (equivalent to `--vus`/`--duration`). Setting them while `options.scenarios` is also defined makes k6 hard-fail with `executor default: function 'default' not found in exports` instead of scaling the scenario. Use `K6_SMOKE_VUS`/`K6_SMOKE_DURATION` instead — confirmed working against a real 200-VU run 2026-07-21 (p95 697ms, 0% failed, both thresholds passed).

### Environment variables

| Var                 | Default                 | Purpose                                    |
| -------------------- | ------------------------ | -------------------------------------------- |
| `K6_BASE_URL`         | `http://localhost:8080`    | Backend base URL                             |
| `K6_USERNAME`         | `employee1`                 | Seeded dev user to authenticate as (`dev` profile only) |
| `K6_PASSWORD`         | `admin123`                  | —                                             |
| `K6_SMOKE_VUS`        | `20`                        | Concurrent virtual users for `history_smoke` (raise toward 200 for a full load run) |
| `K6_SMOKE_DURATION`   | `30s`                       | `history_smoke` duration (`checkin_smoke` is always a single iteration) |

## Thresholds

Per PRD §8: API p95 ≤ 2s @ ≤ 200 concurrent (`history_smoke`: `p(95)<2000`, `http_req_failed rate<0.01`), check-in flow < 30s (`checkin_smoke`: `p(100)<30000`).

`checkin_smoke` accepts either `201` (fresh check-in) or `409 ALREADY_CHECKED_IN` (re-run against a non-fresh environment) as a passing status — the SLA is about response latency, not the business outcome.
