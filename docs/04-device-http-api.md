---

### `docs/04-device-http-api.md`

# HTTP API

Two servers expose HTTP. Both are plain HTTP, no TLS.

- **Device server** — `0.0.0.0:9191`. Serves the dashboard and every control endpoint.
- **Relay** — the Node backend. Device-facing ingest plus an operator proxy.

All device endpoints require the session cookie `c2_auth_token=AUTH_OK_1337`. A request without it
gets the login page (HTTP 200, not 401 — scripts that check status codes will be fooled).

## Login
# 04 — HTTP API

Two HTTP servers exist. Both are plaintext — no TLS anywhere in the stack.

- **Device server** — listens on `0.0.0.0:9191`. Serves the dashboard and every
  control endpoint. Reachable externally via the device's IPv6 GUA as
  `http://[2001:db8::1]:9191`; IPv4 only via the relay.
- **Relay** — Node backend. Device-facing ingest plus an operator proxy.

## Auth

All device endpoints require the cookie `c2_auth_token=AUTH_OK_1337`. Credentials are
reversed then Base64-encoded with a `0x_` prefix before transmission.

**Missing cookie returns HTTP 200 with the login page**, not 401. Status-code checks
will be fooled — inspect the body.

## Known endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Dashboard, or login page if unauthenticated |
| GET | `/download/` | Serve the hosted APK from the device itself |
| GET | `/ping` | Liveness |
| POST | `/restart` | Restart the service (Terminal tab `RESTART_SERVER`) |

For the remainder, read the tab's JS in `c2-server/public/` — endpoints are named after
their control ID and are not independently versioned.
