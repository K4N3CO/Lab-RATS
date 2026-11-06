---

### `docs/01-requirements.md`

```markdown
# Requirements

## Build host

| Component | Minimum | Notes |
| --- | --- | --- |
| JDK | 17 or 21 | **JDK 21 recommended.** Must be JDK, not JRE — `keytool` is required. |
| `JAVA_HOME` | set | Both the builder and Gradle read it. |
| Android SDK | API 34+ | Build-tools 34.0.0+ for `aapt2`, `zipalign`, `apksigner`. |
| Gradle | bundled | `./gradlew` pins 8.10.x. Don't swap it. |
| Python 3 | for option 7 | `binder.py` does the manifest surgery. |
| `bc` or `awk` | either one | `build.sh` hard-fails without one. |

`build.sh` auto-detects Java and offers `apt` / `dnf` / `pacman` / `brew` / `winget` / `choco`
installs. It warns outside 17–21 but does not stop.

## C2 relay host

| Component | Minimum |
| --- | --- |
| Node.js | 18.0.0 |
| npm | 9.0.0 |
| RAM | 512 MB |
| Disk | ~1 GB (upload vault grows unbounded) |

Render free tier, Railway and Fly.io all work. Render sleeps idle services, which stalls
long-polling — expect a 30–50s cold start on the first check-in after a quiet period.

## Target devices

- Android **5.0 (API 21)** through **17 (API 37)**.
- Tested on Samsung, Pixel, OnePlus, Xiaomi, Motorola, HTC, LG.
- Root not required. Root only matters for the optional priv-app/AOSP path.

## Host OS

- Linux: Kali, Ubuntu, Debian, Arch — full support.
- macOS: Monterey through Sequoia, Intel and Apple Silicon.
- Windows: 10/11 via PowerShell, cmd, or WSL2. WSL2 has a `wsl-setup.sh` helper.

## Things that will bite you

- **AAPT2 SIGBUS (exit 138)** on macOS arm64. `bind.sh` works around it by preferring `aapt` v1
  from build-tools 30–34. Don't "fix" that preference.
- **Gradle needs network** on first run to fetch dependencies. Offline builds fail with an opaque
  error.
- **Play Protect scanning** can delay or block install even for clean builds. Ship a fresh
  signature per campaign — reused keystores correlate installs.