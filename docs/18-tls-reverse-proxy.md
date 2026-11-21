# 18 — TLS reverse proxy for direct mode

The payload speaks plain HTTP and always will. The only way to get TLS on the operator leg
in direct mode is to terminate it somewhere else. This is that somewhere.

**Scope:** direct IPv6 mode only. If you're on the relay, you already have HTTPS — this doc
buys you nothing there.

**Trade-off:** you give up the zero-infrastructure property. A proxy is another host, another
cert, another thing that can be down. Read the whole page before committing.

## Three options

| Option | Effort | Trust model | Latency cost |
|---|---|---|---|
| A. Tailscale / WireGuard overlay | Low | Proxy provider sees nothing | Minimal |
| B. Caddy on your own VPS | Medium | VPS sees plaintext | +1 hop |
| C. Cloudflare Tunnel | Low | Cloudflare terminates TLS | +1 hop |

Option A is the best default: no certs, no public port, no proxy provider reading traffic.
Option B is right when you need a normal HTTPS URL a browser can hit from anywhere. Option C
is fastest to stand up and puts Cloudflare in the path.

---

## Option A — Tailscale overlay (recommended)

Neither end is publicly exposed. TLS is WireGuard's, not a cert.

**On the operator host:**

```bash
curl -fsSL https://tailscale.com/install.sh | sh
tailscale up
