# Lab-RATS v1.6 — Documentation

> Android remote administration framework. Builds a signed APK, hosts the C2 dashboard
> on the device itself on port 9191, and reaches it over the device's IPv6 global
> unicast address (or relays through a hosted Node backend when IPv6 isn't routable).

**This fork:** https://github.com/kool277/Lab-RATS-v1.6
**Upstream:** https://github.com/K4N3CO/Lab-RATS-v1.6 · **License:** MIT · **Author:** K4N3CO

## What this actually is

Two programs that talk to each other:

1. **Payload** — Android app. On first launch it registers a decoy identity, walks the user
   through a fake "system update" permission sequence, then starts an HTTP server on
   **port 9191 bound to all interfaces**. That server *is* the C2 dashboard. There is no
   separate control plane.
2. **Builder** — a shell/PowerShell menu in `apk-builder/`. Rewrites package ID, name and
   version, generates a throwaway signing keystore, compiles via Gradle, and can wrap the
   download link in a carrier file (PDF, MP4, ICS, etc.).

Optional third component: a Node/Express relay (`c2-server/`) you can host on Render when
the target has no routable IPv6. It collects check-ins, stores exfiltrated media, and
proxies dashboard requests.

The IPv6 bet: carriers hand out publicly routable GUAs, so binding the server to the
device sidesteps CGNAT and inbound firewall rules. No port forwarding, no ngrok.

**The builder is CLI-only.** There is no "APK Builder" tab in the dashboard.

**There is no TLS.** Direct mode is plaintext end to end. See [18](18-tls-reverse-proxy.md)
before using it on an untrusted network.

## Start here

| If you are… | Read |
|---|---|
| New and want a device in a dashboard | [00](00-quickstart.md) → [15](15-operator-runbook.md) |
| Building for the first time | [01](01-requirements.md) → [02](02-building-payloads.md) |
| Deciding on transport | [03](03-c2-deployment.md) |
| Deciding whether to trust it | [09](09-security-notes.md) |
| Putting it on a hostile network | [18](18-tls-reverse-proxy.md) |
| Debugging a failure | [07](07-troubleshooting.md) |

## Docs index

| Doc | Contents |
|---|---|
| [00-quickstart.md](00-quickstart.md) | Clone to a phone appearing in a dashboard |
| [01-requirements.md](01-requirements.md) | Toolchain, SDK, device and OS support |
| [02-building-payloads.md](02-building-payloads.md) | All nine builder options, config keys, artifacts |
| [03-c2-deployment.md](03-c2-deployment.md) | Google Sheets webhook vs. Node relay vs. direct IPv6 |
| [04-device-http-api.md](04-device-http-api.md) | Endpoint reference for the on-device server and relay |
| [05-dashboard-reference.md](05-dashboard-reference.md) | All 13 tabs: controls, prerequisites, caveats |
| [06-stealth-and-persistence.md](06-stealth-and-persistence.md) | Decoys, blackout, watchdog layers, recovery hooks |
| [07-troubleshooting.md](07-troubleshooting.md) | Build failures, Termux bridge, AAPT2 crashes, dead links |
| [08-architecture.md](08-architecture.md) | Component map, data flow, trust domains |
| [09-security-notes.md](09-security-notes.md) | What is protected, what is obfuscation, what is absent |
| [10-faq.md](10-faq.md) | Short answers to the usual questions |
| [11-maintaining-your-fork.md](11-maintaining-your-fork.md) | Rebranding, upgrading, upstream drift |
| [12-server-setup.md](12-server-setup.md) | Relay and VPS setup |
| [13-direct-ipv6-hardening.md](13-direct-ipv6-hardening.md) | IPv6 pre-flight checks |
| [14-free-ipv6-hosting.md](14-free-ipv6-hosting.md) | Hosting provider comparison |
| [15-operator-runbook.md](15-operator-runbook.md) | End-to-end operational procedure |
| [16-resources-and-tools.md](16-resources-and-tools.md) | Every dependency, provider and external tool |
| [17-expected-results.md](17-expected-results.md) | Verification: what success looks like |
| [18-tls-reverse-proxy.md](18-tls-reverse-proxy.md) | TLS termination via Tailscale, Caddy, Cloudflare |

## 60-second tour

    git clone https://github.com/kool277/Lab-RATS-v1.6.git
    cd Lab-RATS-v1.6/apk-builder
    chmod +x build.sh && ./build.sh

## Conventions in these docs

- **Bold** marks a destructive or irreversible action.
- "Device" always means the target. "Host" always means your build machine.
- `9191` is the fixed port unless a doc says otherwise.
- Where a doc says "prerequisite: Accessibility", the control silently no-ops without it.
- Security claims carry a shelf life. Anything about AV verdicts is dated within weeks.
