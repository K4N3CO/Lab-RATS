---

### `docs/04-device-http-api.md`

```markdown
# HTTP API

Two servers expose HTTP. Both are plain HTTP, no TLS.

- **Device server** — `0.0.0.0:9191`. Serves the dashboard and every control endpoint.
- **Relay** — the Node backend. Device-facing ingest plus an operator proxy.

All device endpoints require the session cookie `c2_auth_token=AUTH_OK_1337`. A request without it
gets the login page (HTTP 200, not 401 — scripts that check status codes will be fooled).

## Login