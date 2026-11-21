# 16 — Resources and tools

## Repository

- This fork: https://github.com/kool277/Lab-RATS-v1.6
- Upstream: https://github.com/K4N3CO/Lab-RATS-v1.6
- Compare against upstream: `git fetch upstream && git log --oneline HEAD..upstream/main`

## Toolchain

| Tool | Purpose | Install |
|---|---|---|
| JDK 17/21 | Compile, keystore, signing | `winget install EclipseAdoptium.Temurin.21.JDK` / `brew install openjdk@21` / `sudo apt install openjdk-21-jdk` |
| Android SDK + build-tools 34 | aapt2, zipalign, apksigner | Android Studio, or `sdkmanager "build-tools;34.0.0"` |
| Python 3 | `binder.py` smali injection | System package manager |
| Node 18+ / npm 9+ | Relay only | `nvm install 18` |
| adb | Sideload, logcat | `sudo apt install adb` / platform-tools |
| ImageMagick | Optional vector generation | `sudo apt install imagemagick` |

## Hosting

| Service | Role | Notes |
|---|---|---|
| Render | Relay backend | Free tier cold-sleeps; root dir `c2-server/` |
| Google Apps Script | Sheet webhook | Deploy as Web App, access "Anyone" |
| Catbox / Litterbox / Tmpfiles | Anonymous APK hosting | Wizard default with fallback |
| is.gd | URL shortening | Automatic in wizard |
| Cloudflare / ngrok | Alternative delivery tunnel | Manual, for self-hosted |

## Verification

- `ip -6 addr show scope global` — GUA presence
- `curl -6 http://[gua]:9191/ping` — reachability
- `adb logcat | grep -Ei 'decoy|watchdog|9191'` — runtime trace
- `sdkmanager --licenses` — SDK licence state
