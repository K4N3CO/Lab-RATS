# 07 ⏤ Troubleshooting

## Build

**`Java not found` / `keytool not found`**
JDK missing, or only a JRE. `java -version` must work and `$JAVA_HOME/bin/keytool` must exist. Menu
option 4 prints install commands per platform.

**`BUILD FAILED` with a Gradle stack trace**
Usually offline. Gradle fetches dependencies on first run. Then `./gradlew clean` and retry. Confirm
SDK licenses are accepted (`sdkmanager --licenses`).

**`APK Not Signed`**
No keystore. Run option 1 or 2 first.

**AAPT2 exit code 138 / SIGBUS (macOS arm64)**
Broken AAPT2 in build-tools 35+. `bind.sh` prefers `aapt` v1 from build-tools 30–34 for this reason.
Don't override it.

**`stray 'fi' syntax error` in `build.sh`**
Present in some forks. `bash -n build.sh` locates it; upstream fixed it Oct 30.

**Logo doesn't change**
Expected. The robot icon is correct at install; the swap happens after first launch. If it never
swaps, the decoy activity failed to start — check `logcat` for `DecoyActivity`.

**Icon stuck on the robot after a decoy swap**
Clear the launcher cache or reinstall. The adaptive icon is written at runtime and some launchers
cache hard.

---

## Install

**"App not installed" / parse error**
Signature collision with an existing install. Uninstall the old copy, or rebuild with a new
keystore.

**Play Protect blocks it**
Expected on a first-seen signature. "Install anyway", or wait for the scan to finish. Shipping via
the Play Protect decoy carrier reduces friction because the fake scan screen reads as legitimate.

**Install succeeds, nothing happens**
The app must be opened once. Nothing starts from the installer.

---

## Runtime

**Dashboard loads but every control times out**
Service died mid-session. `RESTART_SERVER` on the Terminal tab, or SMS `!RESTART_C2`.

**`http://[ipv6]:9191` unreachable from the laptop**
In order: is the phone on a network with a GUA (`ip -6 addr show scope global`)? Does a laptop on the
same subnet reach another host's 9191? Does the carrier allow inbound v6? If any answer is no, use
the relay.

**Permissions keep getting revoked**
OEM battery/background manager. Disable battery optimization for the package, then
REPAIR PERMISSIONS. On Xiaomi and Huawei also enable Autostart.

---

## Termux Bridge Issues
"**Termux `allow-external-apps` setting is disabled**" *(Most Common)*.
<br>**Solution**: **On the Target Device** open **Termux and run**:
```zsh
echo "allow-external-apps = true" >> ~/.termux/termux.properties
termux-reload-settings
```
