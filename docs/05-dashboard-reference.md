# 05 — Dashboard reference

Thirteen tabs, served from the device. There is no builder tab — payloads are built on the
workstation only.

**Auth:** default password `admin1337`. Change it from tab 01 on first login. The cookie is a
constant, so changing the password does not evict anyone who already has it
([09](09-security-notes.md)).

**Read the prerequisite column before blaming a control.** Most tabs silently no-op when
their permission is missing. "Accessibility" means the service must be enabled and the app
must not be force-stopped.

| # | Tab | Primary prerequisite |
|---|---|---|
| 01 | Terminal | none |
| 02 | Ghost Operations | Accessibility for most controls |
| 03 | Optics | Camera; Mic for audio |
| 04 | Locate | Location (fine + coarse) |
| 05 | Exploit Factory | none to generate; overlay injection needs Accessibility |
| 06 | Storage | Storage |
| 07 | Intel | Notification access |
| 08 | SMS | SMS send/read |
| 09 | MMS | SMS + storage |
| 10 | Acoustics | Mic; Phone state for call capture |
| 11 | Call Logs | Call log read |
| 12 | Contacts | Contacts read |
| 13 | Hardware | none |

---

## 01 — Terminal

The landing tab after login. `root@Android` prompt, command history via Up/Down, retry logic
with per-command execution tracking.

**Controls**

- Arbitrary shell as the app's UID
- `help` prints the built-in menu
- `RESTART_SERVER` — bounces the background service
- Password change

**Termux bridge:** if Termux is installed and `allow-external-apps` is enabled, `pkg`, `apt`,
`pip` and `python` route through it. Without that flag every Termux command fails.

**Caveats**

- Not root. `su` only works on a rooted device.
- A command that hangs holds the tab until the socket times out.
- `RESTART_SERVER` kills the session — expect to re-login.

## 02 — Ghost Operations

The stealth tab. Highest-privilege surface in the dashboard.

| Control | Effect | Cost |
|---|---|---|
| Ghost Control | Live screen cast + input injection, no consent prompt | Continuous CPU + bandwidth |
| Blackout | Blanks the physical display, feed stays live | Screen unreadable to the user |
| Ghost Toast | Persistent overlay, controlled text/colour/size/position/animation | Visible tell |
| Burnt Toast | Floods screen with randomized pop-ups | Unusable device while active |
| Denial Lock | Full-screen overlay blocks all input until unlocked | Device inoperable |
| Live keylogging | Intercepts keystrokes and screen text, highlights OTPs/passcodes/emails | Continuous Accessibility load |
| Anti-Removal | Blocks uninstall and force-stop | `adb uninstall` still bypasses |
| REPAIR PERMISSIONS | Re-raises revoked permission prompts | None |
| Suicide | Wipes config, uninstalls the core | Irreversible |

**Order of operations:** Accessibility → Anti-Removal → anything else. Anti-Removal off
before a clean uninstall.

**Caveats**

- Blackout + Ghost Control together is the intended pairing; Blackout alone just looks broken.
- Denial Lock survives until unlocked or hard-restarted — a stuck lock needs a reboot.
- Keylogging catches everything on screen, including other apps' OTP dialogs. Volume is high;
  filter rather than scroll.

## 03 — Optics

Camera hub.

- **Live feed** — continuous preview, one-click start
- **Covert photo** — still capture inside the stream
- **Covert video** — record without user-facing activity
- **Nightmode V2** — aggressive electronic brightening for low light

**Caveats**

- Front/back switch mid-stream is not seamless.
- The camera is exclusive — if another app holds it, start fails; check the app in foreground.
- Nightmode amplifies sensor noise along with signal. Use it, then shoot stills rather than
  judging the live feed.
- Long recordings fill device storage before they fill your disk.

## 04 — Locate

- **Live uplink** — opens the device's current position in Google Maps
- Refresh to re-poll

**Caveats**

- Indoor fixes drift by tens of metres; that is the sensor, not the tool.
- Maps opens on your machine, not the device — no notification appears there.
- Google Maps requests from a datacentre IP sometimes hit a 403 on the embed; reloading
  usually clears it.
- Location off at the OS level returns a stale cached fix rather than an error.

## 05 — Exploit Factory

Generates delivery material from the dashboard, for when you're already connected and want a
second vector.

| Control | Output |
|---|---|
| QR generator | High-density QR pointing at a URL |
| NFC writer | Binary NDEF record for physical tags |
| Smishing library | Pre-written pretext templates |
| Shadow overlay | Injects a pixel-accurate credential-harvesting overlay on the device |

**Caveats**

- Overlay injection is the one destructive-ish control here: a bad overlay blocks the real UI
  until dismissed. Always include a close affordance.
- Smishing templates are starting points, not tuned copy — they read as generic.
- QR and NDEF encode a URL, not a payload. Host the APK somewhere durable first.

## 06 — Storage

File manager over the app's reachable storage.

- Navigate, download, delete
- Instant search, category filters
- **Direct editor** for text, JSON and log files

**Caveats**

- Reach is the app's UID, not the whole filesystem. `/sdcard` subtrees it has been granted,
  not `/data` of other apps.
- The editor is live — saving a malformed JSON breaks whatever reads it.
- Large downloads over a slow link time out rather than resuming.

## 07 — Intel

Notification stream. Every notification from every app, live: WhatsApp, Telegram, RCS, system.

**Caveats**

- Highest-volume tab by far. Leave it open on a busy device and it scrolls faster than you
  read.
- Message text appears here in full, including OTPs — often faster than the SMS tab.
- Requires notification access; if it's blank, that grant was revoked.

## 08 — SMS

Read the thread list, read a thread, send.

**Caveats**

- Sending costs the target's plan, and the sent message appears in their real SMS app.
- Multi-SIM devices may pick the wrong subscription; check before a long thread.
- Inbound `!RESTART_C2` is matched here — any text containing the string triggers a restart.

## 09 — MMS

Multimedia messages, including attachments.

**Caveats**

- MMS needs mobile data; on Wi-Fi only the attachment fetch fails and shows a placeholder.
- Tri-engine dispatch means a send can take several seconds to confirm.
- Attachment sizes are carrier-capped; large media often arrives downscaled.

## 10 — Acoustics

Microphone and call audio.

- Timed or live mic recording
- Automated recording of inbound and outbound calls

**Caveats**

- Mic is exclusive with anything else using it — a call or voice note blocks recording.
- Call recording quality depends on the OEM; some route through the earpiece and sound thin.
- Recordings land in the relay vault when relayed, otherwise on device storage.

## 11 — Call Logs

Full call history with direction, duration and contact.

Read-only. Most useful as corroboration alongside 08 and 10 — a call in the log with a
matching recording is a complete record.

## 12 — Contacts

Full contact list, exportable.

**Caveats**

- Large address books are slow to render; the tab can appear hung while it loads.
- Duplicates are shown as stored — the tool does not deduplicate.

## 13 — Hardware

Device identity and telemetry: model, Android version, build, battery, power state, free
storage, connection type, IP, port.

This is the same data the check-in row carries. Use it to confirm you're on the device you
think you are before acting — model mismatch is the usual sign of a stale C2 link pointing at
a reassigned address.

---

## Cross-tab behaviour

- **Last write wins.** Two operators on the same device will clobber each other's settings.
- **A dead service fails every tab identically** — the symptom looks like "everything times
  out", not like a permission problem. `RESTART_SERVER` first, then diagnose.
- **Accessibility revocation breaks 02 and 05-overlay silently.** No error, just nothing.
- **Camera, mic and screen capture compete for hardware.** Only one of 03 / 10 / 02-stream
  can hold each resource at a time.

## Recommended first pass on a new device

1. Change the password (01)
2. Confirm model and battery look right (13)
3. Enable Anti-Removal, verify the 10-tap unlock (02)
4. Pull device info, contacts, call logs, notification backlog (13, 12, 11, 07)
5. Only then start live work (03, 04, 10)
