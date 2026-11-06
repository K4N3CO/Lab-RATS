# Dashboard reference

The dashboard is the device's own UI served at `:9191`. Same code on desktop and mobile — the layout
reflows, the feature set doesn't.

Tabs, in order:

| # | Tab | Purpose |
| --- | --- | --- |
| 01 | Terminal / Home | Shell, password change, `RESTART_SERVER`, activity log |
| 02 | Ghost | Blackout, live control, lock, toast, keylog, repair permissions, suicide |
| 03 | Optics | Camera photo, stream, record, night mode |
| 04 | Locate | GPS fix, track, map link |
| 05 | Exploit | QR, NDEF, smishing templates, shadow overlay |
| 06 | Storage | File manager, search, edit, download |
| 07 | Intel | Notification feed, clipboard, browser credentials |
| 08 | SMS | Threads, read, send |
| 09 | MMS | Threads, attachments, send |
| 10 | Acoustics | Timed/live mic, call recording |
| 11 | Comms | Call logs |
| 12 | Contacts | Contact list |
| 13 | Hardware | Model, build, sensors, installed apps |

## Terminal

`root@Android` prompt. Up/Down walks history. `RESTART_SERVER` re-execs the service — use it when
the dashboard loads but controls time out. Password change writes to the same store the login reads;
changing it does not invalidate the current session.

## Ghost

- **Live control** — screen cast plus injected touches. No consent dialog on any API level.
- **Blackout** — the panel goes dark while the feed stays live. Pairs with live control for a device
  that looks asleep and isn't.
- **Lock** — full-screen overlay. Blocks physical input until you release it or the device reboots.
  Not a factory lock; a hard reboot clears it.
- **Toast** — overlay text with color, size, position, animation. `Burnt` floods the screen with
  randomized pop-ups.
- **Keylog** — live keystroke and field text. Passcodes, OTPs and emails render red. Browser
  credential extraction is a separate toggle.
- **REPAIR PERMISSIONS** — re-raises system prompts for anything revoked. This is the fix when a
  feature quietly stops working after an OEM update.
- **Anti-Removal** — on by default. Hooks uninstall and force-stop. Toggle off before a clean
  removal.
- **Suicide** — wipes local config and uninstalls the core. Unrecoverable without reinstalling.

## Optics

Front/rear selection, single photo, stream, timed record. Night mode raises exposure aggressively
and uses a breathe-sync loop to avoid sensor freezes on high-latency hardware. Recordings land in
`System-Security/` in the app's private storage, and auto-upload to the relay vault when a relay is
configured.

## Exploit

QR generator and NDEF writer for delivery, plus smishing templates. **Shadow overlay** injects a
credential-harvesting layer on top of the device UI — it's a screenshot-accurate fake, not a real
app, so the target can type a password into it and nothing else happens.

## Files

Search and category filters, download, and a text/JSON/log editor that writes in place. Editing
`shared_prefs` XML by hand is possible and is the fastest way to brick the app's config — copy before
you edit.

## Hardware

Read-only except the app list, which can launch or force-stop packages.

---

## Keyboard and mobile behavior

- Desktop: sidebar tabs, `Esc` closes overlays.
- Mobile: bottom sheet navigation, swipe between adjacent tabs.
- The dashboard is viewport-locked — pinch-zoom is disabled, which is annoying on small screens and
  intentional.
- Right-click and `F12` are intercepted. Use the browser menu for devtools.