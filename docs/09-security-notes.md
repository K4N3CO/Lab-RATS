# 09 ⏤ Security Notes & Considerations  

> **Audience**: Operators, security researchers, and analysts evaluating threat models, transport security, forensic footprints, and attack surface mitigations on target deployments.

---

## 🔑 Authentication & Session Security

- **PBKDF2/SHA-256 On-Device Verification**: Passwords are authenticated on-device using PBKDF2 with SHA-256 hashing (`PasswordHasher.java`).
- **Configurable Master Key**: While `admin1337` is the default initial key for fresh installations, setting a custom C2 password permanently revokes `admin1337`. The master override is **not** permanent once a custom password is configured.
- **Session Tokens**: Relies on session token verification (`AUTH_OK_1337`). Presence of a valid token grants access to on-device HTTP endpoints.
- **Emergency Password Reset**: Dialing the emergency secret code (`*#7287#`) via `TelephonyState.java` allows local recovery by resetting the access key back to default.
- **Cache-Busting Resources**: CSS and JS dashboard assets append dynamic timestamp queries (`style.css?v=${Date.now()}`) to prevent stale browser session state exploits.

> **Security Mitigation**: Always set a strong custom C2 password on first access to revoke default initial keys.

---

## 👑 Device Administration & Recent Apps Persistence

- **DeviceAdminReceiver Policy (`StabilityPolicy.java`)**: Implements an active `DeviceAdminReceiver` registered via `res/xml/device_admin.xml`.
- **Admin Elevation Trigger (`elevateAdmin`)**: Operators can remotely trigger Device Admin elevation via the Ghost tab.
- **Prevention of Force-Stop / Task Killing**:
  - **Recent Apps & Task Manager Shield**: Elevating privileges to Device Admin level prevents the application from being stopped or killed when swiped away in the **Recent Apps screen** or cleared by task-killer managers.
  - **Settings Force-Stop Lock**: Disables the OS-level "Force Stop" and "Uninstall" controls inside **Settings → Apps**.
  - **Remote Instant Screen Lock**: Allows instant hardware screen locking via `DevicePolicyManager`.
  - **Emergency Remote Wipe**: Grants permission for remote factory wipe/purge if deployed.

---

## 🌐 Transport Security & Relay Tunnels

- **Direct Mode Exposure**: In direct IPv6/IPv4 mode without a relay, traffic runs unencrypted on port `9191`. Basic authentication headers use reverse-base64 client-side obfuscation.
- **C2 Relay TLS Termination**: When connected via Webhook or Render C2 Relay tunnels (`server.js`), all operator-to-relay traffic is encrypted over HTTPS / TLS.
- **Base64 Frame Polling**: Video feeds (`/camera/frame` & `/ghost/screenshot`) poll JPEG buffers as Base64 Data URIs over HTTP/200 OK responses, avoiding unauthenticated WebSocket or proxy stream injection attacks.

> **Security Mitigation**: Do not expose direct port `9191` to untrusted networks without fronting the device with a TLS reverse proxy or using the Render C2 relay.

---

## 💾 Forensic Footprint & Data at Rest

- **Device Configuration Encryption**: Local properties (`WEBHOOK_URL`, `DECOY_CHOICE`) are encrypted with build-time `ENCRYPTION_KEY`. Because the key ships within the APK binary, it protects against casual device inspection, but not static binary reversing.
- **Exfiltrated Data Vaults**: Captured audio (`/audio`), photos (`/camera`), and keylogs (`/ghost`) are stored in app-private storage (`context.getFilesDir()`) or public storage if local backup is enabled.
- **Relay Server Disk**: Cloud relay storage (e.g. Render.com) is ephemeral; exfiltrated files stored in `/c2/downloads/` should be retrieved immediately.

---

## 👻 Ghost Control, Blackout & Package Visibility

- **Covert Screen Capture**: Screenshots taken during Blackout Mode (`screenBrightness = 0.001f`) capture the un-dimmed native screen buffer in 720p HD resolution without lighting distortion.
- **Package Visibility (`<queries>`)**: Implements strict Android 11+ (API 30–36) package visibility declarations in `AndroidManifest.xml` for `TTS_SERVICE` and system intents, preventing OS-level background service blocks.
- **Keylogger Encryption**: Keylogs are encrypted at rest and dynamically decrypted for display, highlighting sensitive patterns (credit cards, PINs, passwords) in red.

---

## 🛠️ Supply Chain & Build Automation

- **Dependency Cleanliness**: Carrier files and assets are generated locally during compilation without third-party remote code execution risks.
- **Android SDK Isolation**: `apk-builder/build.sh` auto-configures the local Android SDK path (`sdk.dir`), enforcing isolated local compilation.
- **Gradle Network Timeout**: Configured with a 120-second timeout (`networkTimeout=120000`) to prevent MITM/timeout interruptions during wrapper downloads.

---

## 🕵️ Detection Indicators & Forensic Reduction

| Tell / Indicator | Risk Level | Mitigation Strategy |
| :--- | :--- | :--- |
| Default Package (`com.android.system.stability`) | Medium | Change `PKG_NAME` in `build_config.txt` / `build.sh` |
| Default Developer Signer (`CN=Lab-RATS`) | High | Generate a unique custom keystore per campaign |
| Device Admin & Accessibility Entry | Medium | Unavoidable — required for anti-kill & remote UI control |
| Persistent Service Notification | Low | Minimized to `IMPORTANCE_MIN` (silent / no sound) |
| Listening Port (`9191`) | Medium | Customize `PORT` constant in `Constants.java` |

---

## 🚫 Operational Limits & Threat Model Boundaries

- **Factory Reset**: Wiped if the target device undergoes a factory reset.
- **Non-Root Execution**: Runs within standard Android app process boundaries. Privileged operations (priv-app installation, system binary execution) require AOSP integration or `PrivilegedInstaller`.
- **Manual User Discovery**: Users navigating manually to **Settings → Apps → Special App Access** can view active Device Admin and Accessibility permissions.
