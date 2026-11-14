# 13 ⏤ Direct IPv6 Hardening Guide

> This document details security hardening procedures, threat models, credential management, network restrictions, transport layer security, fingerprint reduction, operational hygiene, verification steps, and teardown procedures for Direct IPv6 deployments.

---

## 🔒 Threat Model

Before applying security controls, understand what risks you are defending against:

| Adversary | Capability | Realistic Threat? |
| :--- | :--- | :--- |
| **Automated Address Scanner** | Sweeps `2xxx::/16` for open port `9191` and attempts default credentials | **Yes, constantly** |
| **Curious Target User** | Discovers the app, opens Android Settings, attempts uninstall | **Yes** |
| **Network Observer (MITM)** | Inspects unencrypted cleartext traffic on shared Wi-Fi networks | **Yes** |
| **Operator with Leaked Cookie** | Full access to the C2 dashboard without needing the password | **Yes** |
| **Malware / Forensic Analyst** | Decompiles the APK, greps for hardcoded secrets and endpoints | **Medium** |
| **Carrier / ISP** | Monitors traffic streams, potential cleartext interception | **Low** |

* **Dominant Risk:** Automated IPv6 port scanners finding default port `9191` and attempting default credentials (`admin1337`).
* **Secondary Risk:** Over-hardening the network and blocking valid reachability to the target payload.

---

## ⚡ Severity of Default Posture

| Setting | Default Posture | Risk Level | Description |
| :--- | :--- | :--- | :--- |
| **Password** | `admin1337` | **Critical** | Public default password known to scanners |
| **Session Cookie** | `AUTH_OK_1337` | **High** | Portable static bearer token without expiration |
| **Transport** | Plain HTTP | **High** | Cleartext traffic susceptible to interception on open networks |
| **Rate Limiting** | None | **High** | Allows rapid brute-force passphrase attempts |
| **Bind Scope** | `0.0.0.0` | **Medium** | Listens on all active interfaces (Wi-Fi, Cellular, Tethering) |
| **Source Allowlist** | None | **Medium** | Open to all incoming IPv6 addresses |
| **Port** | `9191` | **Low** | Fixed port fingerprint |
| **Package / Signer** | Lab-RATS Defaults | **Low** | Default app package name and signature attribution |

---

## 🔑 1. Credentials (Do This First)

### 1.1 Set a Long Passphrase
- Change the default password upon first login via **Terminal** $\rightarrow$ **Secure Login**.
- Use a 20+ character passphrase or a unique 5-word diceware passphrase to prevent offline or over-the-wire brute-forcing.

### 1.2 Replace the Session Cookie
- The default session cookie constant (`AUTH_OK_1337`) is static across installs.
- Replace `AUTH_OK_1337` with a unique value across `app/src/main/java/` and `c2-server/`:
  ```bash
  grep -rn "AUTH_OK_1337" app/src/main/java/ c2-server/
  ```

---

## 🛡️ 2. Network Restriction

### Tier A — Source Allowlist (Best if your IP prefix is static)
Apply `ip6tables` firewall rules on the target device (if rooted) or router to allow only your explicit IPv6 source prefix:
```bash
# Allow only your management IPv6 prefix, drop all other traffic on port 9191
sudo ip6tables -A INPUT -p tcp --dport 9191 -s 2a02:8109:abcd:1234::/64 -j ACCEPT
sudo ip6tables -A INPUT -p tcp --dport 9191 -j DROP
```

### Tier B — Overlay Network (Best without root)
Install Tailscale or WireGuard on the target device to route all C2 management traffic over a private IPv6 overlay network rather than exposing port `9191` to the public internet:
```bash
tailscale status
tailscale ip -6
```

### Tier C — Non-Standard Port Obfuscation
Reconfigure the default server port `9191` to a non-standard port (e.g., `38491`) to avoid automated internet-wide port sweeps.

---

## 🌐 3. Transport Security

### 3.1 Encrypt Management Traffic
- **SSH Local Port Forwarding:** Tunnel sessions through a secure Linux host:
  ```bash
  ssh -N -L 9191:[<target-ipv6>]:9191 user@your-server
  # Access locally via http://localhost:9191
  ```
- **Caddy Reverse Proxy (TLS Termination):** Map a domain AAAA record to the IPv6 address and terminate TLS automatically using Caddy:
  ```caddyfile
  lab1.yourdomain.com {
      reverse_proxy [<target-ipv6>]:9191
  }
  ```

### 3.2 Protect Secrets on Untrusted Networks
Direct mode uses cleartext HTTP by default. Never transmit sensitive files, credentials, or audio streams across unencrypted Wi-Fi without SSH or TLS tunneling.

---

## 📡 4. Bind Scope Control

The server binds to `0.0.0.0` by default (listening on Wi-Fi, cellular, and USB/tethering interfaces). To reduce exposure:
- Turn off USB / Wi-Fi tethering when not actively needed.
- Prefer cellular interfaces over shared public Wi-Fi networks when both are available.

---

## 🎭 5. Fingerprint Reduction

Customize build attributes to prevent automated detection and static signature matching:
- **Package ID:** Change default package name `com.android.system.stability` in `local.properties`.
- **Signing Key:** Generate a unique RSA keypair and keystore subject in `./build.sh` option `2`.
- **Port:** Change default port `9191` in `FirebaseConfig.java`.
- **Accessibility Service Label:** Rename service labels in `strings.xml`.
- **Notification Text:** Customize foreground notification strings in `StatusNotification.java`.
- **Decoy Assets:** Replace default icon PNGs in `res/mipmap/`.
- **HTTP Server Header:** Customize the `Server` header returned by the HTTP server handler.

---

## 🛑 6. Permission Minimization

Request only the permissions strictly required for your specific engagement:
- **Accessibility Service:** Required for UI automation and persistence.
- **SMS / Telephony:** Enable only if SMS control or backdoor triggers are required.
- **Camera / Microphone / Location / Contacts / Storage:** Enable on a need-to-use basis to reduce visibility in Android Settings.

---

## 💾 7. Data at Rest Security

- **On Device:** Configuration files are encrypted using the build-time `ENCRYPTION_KEY`. Exfiltrated media files land in private app storage (`System-Security/`).
- **On Workstation:** Keep local check-in logs (`checkins.log`) secured. Back up critical secrets off-device: `lab-rats-keystore.jks`, `keystore.properties`, and `ENCRYPTION_KEY`.

---

## 🧹 8. Operational Hygiene

- **Fresh Keystore per Campaign:** Never reuse signing keys across separate deployments.
- **Unique Passwords per Device:** Mandatory to prevent credential re-use attacks.
- **Rotate Cookie Constants:** Change `c2_auth_token` between major engagements.
- **Increment `versionCode`:** Always increment `versionCode` in `app/build.gradle` for updates (Android rejects APK installs with equal or lower version codes).
- **Clean Output Directory:** Delete old binaries in `apk-builder/output/` before building new payloads.
- **Maintain Deployment Logs:** Track device IDs, package names, and assigned ports in a secure record.

---

## 🧪 9. Verification & Auditing

### Audit Build Before Distribution
```bash
# 1. Verify default password admin1337 is not present
grep -n "admin1337" app/src/main/java/com/labs/labrats/PasswordHasher.java

# 2. Confirm cookie constant differs from AUTH_OK_1337
grep -rn "c2_auth_token" app/src/main/java/ c2-server/server.js

# 3. Verify package name and keystore details
aapt2 dump badging apk-builder/output/signed_v1.apk | head -2
keytool -list -v -keystore lab-rats-keystore.jks | grep -i "owner\|alias"
```

### Audit Deployment Reachability
```bash
# 1. Confirm port responds externally
curl -sI --max-time 8 "http://[<target-ipv6>]:9191/" | head -1

# 2. Confirm incorrect password behavior
curl -s -o /dev/null -w '%{http_code}\n' -X POST -d 'p=invalid_pass' "http://[<target-ipv6>]:9191/login"
```

---

## 🗑️ 10. Teardown and Revocation

1. **Revoke Password:** Update the password via C2 Terminal to block future log-ins.
2. **Rotate Cookie:** Update `c2_auth_token` and rebuild the payload if session credentials are compromised.
3. **Remote Wipe (Ghost Tab):** Trigger local wipe to clear storage and initiate app removal.
4. **Manual Removal:** Disable Anti-Removal in settings $\rightarrow$ Android Settings $\rightarrow$ Apps $\rightarrow$ Uninstall. Clean remaining directory paths under `Android/data/<package>/`.

*(Note: `adb uninstall` and Safe Mode bypass Anti-Removal mechanisms).*

---

## ⚠️ 11. Residual Risk

Even after full hardening:
- Session cookies act as bearer tokens without built-in expiration dates.
- Direct mode traffic requires SSH or TLS proxies to prevent cleartext network inspection.
- Anyone with port access can attempt authentication on the login endpoint.
- Accessibility remains a visible service listing in Android System Settings.

---

## 📋 12. Hardening Checklist

- [ ] Long, unique passphrase set at first login.
- [ ] `admin1337` default password overridden in source code and rebuilt.
- [ ] Cookie constant changed from `AUTH_OK_1337` in `app` and `c2-server`.
- [ ] Non-default Package ID and RSA Keystore generated.
- [ ] Port `9191` changed, or IPv6 allowlist / Tailscale overlay configured.
- [ ] SSH tunnel or Caddy TLS reverse proxy configured for unencrypted transport protection.
- [ ] Android permissions trimmed to minimal required feature set.
- [ ] Signing keystore, encryption keys, and master passcodes backed up off-device.
- [ ] `versionCode` incremented and `apk-builder/output/` cleaned prior to build.
- [ ] Anti-Removal verified enabled and app confirmed hidden from Recent Apps.
- [ ] Verification commands from §9 executed and passed.
