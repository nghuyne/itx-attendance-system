# k6 Performance Tests

Initialized as part of `[TF] Test Framework` (R-005 mitigation groundwork — see
`_bmad-output/test-artifacts/test-design-architecture.md`). Full P0-012
(check-in flow smoke, 200-concurrent target) lands in Bước 3/4; `smoke.js`
here is the first working script, proving the tool + thresholds + auth flow.

## Prerequisites

- [k6](https://k6.io/docs/get-started/installation/) installed locally, or use the `grafana/k6` Docker image.
- Backend stack running: `docker compose up -d mysql minio mailhog backend` (from repo root).

## Run

```bash
k6 run k6/smoke.js
```

### Environment variables

| Var             | Default                 | Purpose                                    |
| ---------------- | ------------------------ | -------------------------------------------- |
| `K6_BASE_URL`     | `http://localhost:8080`    | Backend base URL                             |
| `K6_USERNAME`      | `employee1`                 | Seeded dev user to authenticate as (`dev` profile only) |
| `K6_PASSWORD`      | `admin123`                  | —                                             |
| `K6_VUS`           | `20`                        | Concurrent virtual users (raise toward 200 for a full load run) |
| `K6_DURATION`      | `30s`                       | Test duration                                |

## Thresholds

Per PRD §8: API p95 ≤ 2s @ ≤ 200 concurrent. `smoke.js` asserts `p(95)<2000` and `http_req_failed rate<0.01`.
