# 03 ⏤ C2 Server & Deployment Options

> Lab-RATS offers 3 flexible C2 deployment options depending on your operational needs.

## 🚀 Comparison Matrix

| Option | Transport | C2 Web Dashboard | File Vault Storage | Setup Difficulty |
| :--- | :--- | :--- | :--- | :--- |
| **Option 1: Render C2 Relay** *(Recommended)* | HTTPS / WebSocket | **Yes** (Full 13 Tabs) | **Yes** (Audio/Video/Photos) | **Easy** (Free 1-click cloud deploy) |
| **Option 2: Direct IPv6 / IPv4** | HTTP (Port 9191) | **Yes** (Direct P2P) | **Yes** (Stored on phone) | **Medium** (Requires routable IP) |
| **Option 3: Google Sheets** | HTTP POST | No (Spreadsheet Rows) | No (Text logs only) | **Easy** (Google Apps Script) |

---

## 🌐 Option 1: Render.com C2 Relay Deployment (Recommended)

1. **Fork the Repository**: Fork `https://github.com/K4N3CO/Lab-RATS.git` to your GitHub account.
2. **Create a Free Web Service on Render**:
   - Log into [Render.com](https://render.com) and click **New** $\rightarrow$ **Web Service**.
   - Connect your forked `Lab-RATS` repository.
3. **Configure Service Settings**:
   - **Name**: `lab-rats-c2` (or your preferred name)
   - **Root Directory**: `c2-server`
   - **Build Command**: `npm install`
   - **Start Command**: `npm start`
4. **Deploy**: Click **Create Web Service**.
5. **Copy Your C2 URL**: Copy your live HTTPS Render URL (e.g. `https://your.name.here.onrender.com`) and enter it as your C2 Webhook URL when building the APK.

> 💡 **Auto-Deploy Note**: Render monitors changes inside `c2-server/`. You can also trigger an instant build at any time by clicking **Manual Deploy** $\rightarrow$ **Deploy latest commit** in your Render dashboard.

---

## 📡 Option 2: Direct IPv6 / Local IPv4 Mode

When the target device has a publicly routable IPv6 address or is on the same local Wi-Fi network:
1. The app starts a built-in HTTP server directly on the device at port **`9191`**.
2. Open your browser to `http://[device-ipv6-address]:9191` or `http://[device-local-ip]:9191`.
3. Enter your C2 password to access the full control dashboard directly without cloud relays.

---

## 📊 Option 3: Google Sheets Logging Webhook

For basic check-in logging without hosting a web server:
1. Create a new [Google Sheet](https://docs.google.com/spreadsheets/).
2. Open **Extensions** $\rightarrow$ **Apps Script**.
3. Paste the Apps Script code from `docs/12-Server_Setup.md` and click **Deploy as Web App**.
4. Set access to **Anyone**. Copy the published URL and use it as your C2 Webhook URL during APK build.
