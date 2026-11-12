# 07 ⏤ Troubleshooting & Diagnostics Guide

> Solutions for common build failures, installation blocks, and runtime connection issues.

---

## 🛠️ Build Issues

#### `Java not found` / `keytool not found`
- **Cause:** JDK is missing or `JAVA_HOME` is pointing to a JRE instead of a full JDK.
- **Fix:** Install JDK 17 or JDK 21 and ensure `keytool` exists in `$JAVA_HOME/bin/keytool`. Select Option 4 in `./build.sh` for one-click OS install commands.

#### `BUILD FAILED` with Gradle Stack Trace
- **Cause:** Missing initial network connection or unaccepted SDK licenses.
- **Fix:** Ensure internet connectivity, run `./gradlew clean`, and accept SDK licenses via `sdkmanager --licenses`.

#### `APK Not Signed`
- **Cause:** Missing keystore binary in `apk-builder/`.
- **Fix:** Run `./build.sh` Option 1 or Option 2 to generate a campaign keystore before compiling.

#### `AAPT2 Exit Code 138 / SIGBUS` (macOS Apple Silicon)
- **Cause:** AAPT2 binary incompatibility on macOS arm64 in build-tools 35+.
- **Fix:** `build.sh` automatically uses `aapt` v1 from build-tools 30–34. Do not force build-tools 35+.

---

## 📱 Installation Issues

#### `App not installed` / Parse Error
- **Cause:** Signature collision with a previously installed APK using a different keystore.
- **Fix:** Uninstall the old app completely from the target device or build with a new package ID.

#### `Play Protect Blocks Installation`
- **Cause:** Play Protect flags unverified RSA signatures on new apps.
- **Fix:** Tap **"More details"** $\rightarrow$ **"Install anyway"**. Deploying via the Play Protect decoy carrier wrapper reduces user friction.

#### `Install Succeeds, but Nothing Starts`
- **Cause:** The app has not been launched yet.
- **Fix:** Open the app once manually to initialize background services and permissions behind the decoy interface.

---

## 🌐 Runtime & Connection Issues

#### `Dashboard Loads but Controls Time Out`
- **Cause:** Background service died or was killed by OEM battery optimization.
- **Fix:** Send `RESTART_SERVER` from the C2 Terminal tab or send SMS command `!RESTART_C2`.

#### `Direct IPv6 Address Unreachable` (`http://[ipv6]:9191`)
- **Cause:** Carrier firewall filtering or missing Global Unicast Address.
- **Fix:**
  1. Verify the phone has a `2xxx:` or `3xxx:` address (`ip -6 addr show scope global`).
  2. Verify local binding (`curl http://[::1]:9191/`).
  3. If carrier filters inbound IPv6, switch from Direct Mode to **Render Relay Mode** (`03-C2_Deployment.md`).

---

## 📲 Termux Integration Issues

#### `Termux allow-external-apps setting is disabled`
- **Cause:** Termux blocks external app commands by default.
- **Fix:** Open Termux on the target device and run:
  ```bash
  echo "allow-external-apps = true" >> ~/.termux/termux.properties
  termux-reload-settings
  ```
