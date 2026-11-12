# Official Lab-RATS — Documentation Index

> Lightweight Android remote administration framework. Builds a signed APK, hosts a C2
> dashboard on the device itself, and reaches it over IPv6 (or relays through a hosted
> Node backend).

**Upstream:** https://github.com/K4N3CO/Lab-RATS · **License:** MIT · **Author:** K4N3CO

---

## What in the Hell is Lab-RATS?
**Lab-RATS is two separate programs that talk to each other**:

1. **The Android Payload**: An Android app that assumes a stealth decoy identity (such as Play Protect, Calculator, Weather, System Update, or Smart Tutor) upon launch. Once initial permissions are set up, it starts a lightweight C2 web server on **port 9191** directly on the phone. The phone itself hosts the control dashboard, meaning no separate control panel is needed.

2. **The Workstation Builder**: An automated command-line wizard (`build.sh` / `build.bat`) for your computer. It customizes the app’s name, package ID, and C2 server URL, generates a unique signing key, and compiles your APK with Gradle. It can also package the download link into carrier files like PDFs, MP4s, or calendar invites.

**Optional Cloud Relay**: A lightweight Node.js web server you can host for free on Render.com. If the target phone is behind strict network firewalls or lacks direct IPv6 routing, this relay collects device check-ins, stores exfiltrated audio and video in a cloud vault, and proxies dashboard controls to the device.

**The Direct Link Advantage**: Cellular carriers assign devices publicly routable network addresses. By connecting directly to the phone's address, Lab-RATS establishes direct communication without needing port forwarding, complex router setup, or tunneling services like ngrok.

---

## 📚 Documentation Index

| File                                                               | Subject & Contents                                                  |
|:-------------------------------------------------------------------|:--------------------------------------------------------------------|
| **[00-Quickstart.md](00-Quickstart.md)**                           | 3-step quickstart guide from clone to live dashboard connection.    |
| **[01-Requirements.md](01-Requirements.md)**                       | Workstation build host, JDK, Android SDK, and device compatibility. |
| **[02-Building_Payloads.md](02-Building_Payloads.md)**             | Builder menu options, configuration keys, and decoy personas.       |
| **[03-C2_Deployment.md](03-C2_Deployment.md)**                     | C2 deployment options: Render C2 relay, Direct IP, Google Sheets.   |
| **[04-Device_HTTP-API.md](04-Device_HTTP-API.md)**                 | Device HTTP API reference for control endpoints.                    |
| **[05-Dashboard_Reference.md](05-Dashboard_Reference.md)**         | Overview of the 13 C2 dashboard tabs and features.                  |
| **[06-Stealth_and_Persistence.md](06-Stealth_and_Persistence.md)** | Decoy personas, 10-tap backdoor, watchdog layers, and anti-removal. |
| **[07-Troubleshooting.md](07-Troubleshooting.md)**                 | Solutions for common build, installation, and connection issues.    |
| **[08-Architecture.md](08-Architecture.md)**                       | High-level system architecture diagram and component map.           |
| **[09-Security_Notes.md](09-Security_Notes.md)**                   | Security considerations, authentication, and forensic reduction.    |
| **[10-FAQ.md](10-FAQ.md)**                                         | Frequently asked questions and answers.                             |
| **[11-Maintaining_Your_Fork.md](11-Maintaining_Your_Fork.md)**     | Guidelines for rebranding and syncing upstream updates.             |
| **[12-Server_Setup.md](12-Server_Setup.md)**                       | C2 server setup guide for Render.com and Linux VPS.                 |
| **[13-Direct_IPv6_Hardening.md](13-Direct_IPv6_Hardening.md)**     | Direct IPv6 connection & pre-flight security hardening guide.       |
| **[14-Free_IPv6_Hosting.md](14-Free_IPv6_Hosting.md)**             | Free hosting provider comparison.                                   |