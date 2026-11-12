# 02 ⏤ Building Payloads & Configuration

> The `apk-builder/` directory contains all tools needed to configure, compile, sign, and weaponize Lab-RATS.

## 📋 Builder Menu Options

> When you launch `./build.sh` or `build.bat`, you can choose from these options:

| Option | Feature | Description |
| :--- | :--- | :--- |
| **1** | **Start Build** | Runs dependency checks, generates signing key, prompts for app configuration, and compiles `signed_v1.apk`. |
| **2** | **Generate Keystore Only** | Creates a fresh cryptographic signing key (`lab-rats-keystore.jks`) for signing builds. |
| **3** | **Configure App Settings Only** | Updates app name, package ID, version, and C2 server URL without recompiling code. |
| **4** | **Check Requirements** | Tests local Java JDK, Android SDK, and system utilities for build readiness. |
| **5** | **Weaponized Payload Lab** | Packages the APK into 13 carrier formats (PDF, MP4, Docx, Calendar Invite, AOSP ZIP, etc.). |
| **6** | **Infection Wizard** | Automated 1-pass workflow: Configures, builds, hosts link, and weaponizes payload. |
| **7** | **Smali Surgery & APK Binder** | Injects the stealth backdoor directly into an existing third-party APK. |
| **8** | **Help** | Displays command-line reference and usage tips. |
| **9** | **Exit** | Exits the builder menu. |

---

## 🎭 Stealth Decoy Personas

> During build configuration, you can choose which decoy persona the app presents on the home screen:

1. **Google Play Protect** (`Play Protect`): Fake system security scan screen.
2. **Calculator** (`Calculator`): Functional scientific calculator.
3. **Weather** (`Weather`): Live weather forecast application.
4. **System Update** (`System Update`): Samsung/Android system update screen.
5. **Smart Tutor** (`Smart Tutor`): Samsung Remote Support & Customer Care screen.

---

## ⚙️ Configuration Files (`local.properties`)

> Build parameters are saved in `local.properties` at the root of the repository:

```ini
APP_NAME=Play Protect
PKG_NAME=com.android.system.stability
DECOY_CHOICE=1
C2_PASSWORD=YourCustomPassword
WEBHOOK_URL=https://your-c2-name.onrender.com (Render) or https://script.google.com/macros/s/AKfycbwXQS...(Google Sheet)
```

Output compiled binaries land in `apk-builder/output/signed_v1.apk`.
