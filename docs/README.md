# Lab-RATS v1.6 — Documentation

> Lightweight Android remote administration framework. Builds a signed APK, hosts a C2
> dashboard on the device itself, and reaches it over IPv6 (or relays through a hosted
> Node backend).

**Upstream:** https://github.com/K4N3CO/Lab-RATS-v1.6 · **License:** MIT · **Author:** K4N3CO

---

## What this thing actually is

Lab-RATS is two separate programs that talk to each other:

1. **The payload** — an Android app. On first launch it registers a decoy identity, requests
   permissions behind a fake "system update" screen, then starts an HTTP server on **port 9191**
   bound to the device's global IPv6 address. That server *is* the C2 dashboard. There is no
   separate control plane.
2. **The builder** — a shell/PowerShell menu on your workstation. It rewrites the app's package
   ID, name and version, generates a throwaway signing keystore, compiles with Gradle, and can
   wrap the download link inside a carrier file (PDF, MP4, calendar invite, etc.).

Optional third component: a small Node/Express relay you can host on Render if the target has no
routable IPv6 — it collects check-ins, stores exfiltrated media, and proxies dashboard requests.

The IPv6 trick is the whole architectural bet: carriers hand out publicly routable addresses, so
binding to the device's GUA sidesteps CGNAT and inbound firewall rules. No port forwarding, no
ngrok.

---

## Docs index

| Doc | Contents |
| --- | --- |
| [00-quickstart.md](00-quickstart.md) | Fastest path from clone to a phone showing up in a dashboard |
| [01-requirements.md](01-requirements.md) | Toolchain, SDK, device and OS support |
| [02-building-payloads.md](02-building-payloads.md) | Every builder menu option, config keys, output artifacts |
| [03-c2-deployment.md](03-c2-deployment.md) | Google Sheets webhook vs. Node relay vs. direct IPv6 |
| [04-device-http-api.md](04-device-http-api.md) | Endpoint reference for the on-device server and relay |
| [05-dashboard-reference.md](05-dashboard-reference.md) | The 13 tabs and what each control does |
| [06-stealth-and-persistence.md](06-stealth-and-persistence.md) | Decoys, blackout, watchdog layers, recovery hooks |
| [07-troubleshooting.md](07-troubleshooting.md) | Build failures, Termux bridge, AAPT2 crashes, dead links |
| [08-architecture.md](08-architecture.md) | Component map and source layout |
| [09-security-notes.md](09-security-notes.md) | What's actually protected and what isn't |
| [10-faq.md](10-faq.md) | Short answers to the usual questions |
| [11-maintaining-your-fork.md](11-maintaining-your-fork.md) | Rebranding, upgrading, upstream drift |
| [12-server-setup.md](12-server-setup.md) | server setup |
| [13-direct-ipv6-hardening.md](13-direct-ipv6-hardening.md) | direct-ipv6 hardening |
---

## 60-second tour

```bash
git clone https://github.com/K4N3CO/Lab-RATS-v1.6.git
cd Lab-RATS-v1.6/apk-builder
chmod +x build.sh && ./build.sh
