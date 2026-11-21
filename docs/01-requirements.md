# 01 — Requirements

## Build host

| Component | Minimum | Notes |
|---|---|---|
| JDK | 17 (21 recommended) | Must be a JDK, not a JRE. `java -version` and `$JAVA_HOME/bin/keytool` must both work |
| Android SDK | API 34, build-tools 34.0.0+ | Provides `aapt2`, `zipalign`, `apksigner`. Accept licenses with `sdkmanager --licenses` |
| Gradle | 8.10+ | Bundled via `./gradlew` |
| Python | 3.x | Only for option 7 (`binder.py`) |
| Node/npm | 18 / 9+ | Only if you build or run the relay |

## OS

Linux (Kali, Ubuntu, Debian, Arch), macOS (Intel + Apple Silicon), Windows 10/11 via
PowerShell, CMD or WSL2.

**macOS arm64:** build-tools 35+ ships a broken AAPT2 that dies with exit 138 / SIGBUS.
`bind.sh` pins `aapt` v1 from build-tools 30–34 for this reason. Don't override it.

## Relay host

512 MB RAM, Node 18+, `npm install && npm start`. Render free tier is sufficient but
cold-sleeps after inactivity — first hit after idle takes ~30s.

## Device

- Android 5.0 (API 21) minimum
- Android 14–17 (API 34–37) fully supported
- Samsung, Pixel, OnePlus, Xiaomi, Motorola, HTC, LG, generic devices and emulators
- **Public IPv6 GUA required for direct mode** (`2xxx:` / `3xxx:`). Verify with
  `ip -6 addr show scope global`. No GUA → use the relay.
