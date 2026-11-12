# 05 ⏤ Dashboard & Control Feature Reference

> The C2 Dashboard is the central web interface served directly by the device's internal server on port `9191` (or proxied via the Render C2 Relay).

---

## 📑 Dashboard Tabs Overview

| # | Tab | Primary Function |
| :--- | :--- | :--- |
| **01** | **Terminal / Home** | Command shell, system monitor, C2 password management, Stealth, server restart. |
| **02** | **Ghost** | Live touch control, blackout mode, remote screen lock, toast popups, keylogger, admin elevation. |
| **03** | **Optics** | Remote camera photos, live video streaming, video recording, night mode exposure boost. |
| **04** | **Locate** | GPS location tracking, satellite triangulation, interactive map view. |
| **05** | **Exploit** | QR code generator, NDEF writer, smishing templates, shadow credential overlays. |
| **06** | **Storage** | Remote file manager, in-place file editor, file download & exfiltration. |
| **07** | **Intel** | Live notification feed, clipboard capture, browser credential extraction. |
| **08** | **SMS** | View inbox threads, read messages, send SMS, trigger worm broadcast. |
| **09** | **MMS** | View MMS threads, inspect image attachments, send MMS. |
| **10** | **Acoustics** | Real-time live listening (`LISTEN_LIVE`), Voice Emission (`TEXT-TO-SPEECH`), timed ambient recording, Acoustics Vault. |
| **11** | **Comms** | View full cellular call history (incoming, outgoing, missed). |
| **12** | **Contacts** | View complete address book contacts. |
| **13** | **Hardware** | Inspect battery level, hardware specs, sensors, installed apps (launch/force stop). |

---

## 💻 01. Terminal / Home Tab

- **Interactive Shell (`root@Android`)**: Executes system shell commands directly on the device.
- **Server Health Monitor**: Displays server status, active port, uptime, and active connection IP.
- **`RESTART_SERVER`**: Re-executes the internal NanoHTTPD server engine if controls time out or hang.
- **Stealth Operations**: Remotely swap the entire app identity and icon instantly using convincing, fully functional clones.
- **`CHANGE C2 PASSWORD`**: Updates the C2 password. Uses `PasswordHasher` PBKDF2 with SHA-256 salted hashing and permanently revokes the default `admin1337` key.
- **System Activity Log**: Live scrolling log of C2 events, check-ins, and executed commands.
<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/7f4e02c5-4984-48e5-a76c-6c4ba530495c" alt="Full Web Page Screenshot" width="60%">
</details>

---

## 🎭 02. Ghost Tab

- **`LIVE_CONTROL`**: High-speed screen mirror with interactive touch and swipe gesture injection without consent dialogs.
- **`BLACKOUT`**: Sets hardware screen backlight brightness to `0.001f` for physical stealth while keeping the remote screen mirror feed active.
- **`SYSTEM_DENIAL_LOCK`**: Deploys a full-screen lock overlay blocking physical touch input until released.
- **`TOAST_OVERLAY`**: Displays custom animated pop-up text on screen with customizable font size, color, and position. `BURNT` floods the screen with randomized pop-up overlays.
- **`KEYLOGGER`**: Intercepts real-time keystrokes, password entries, and form fields. Highlights passcodes, credit cards, and emails in red.
- **`REPAIR_PERMISSIONS`**: Re-prompts the user for any revoked permissions after OEM system updates.
- **`ANTI_REMOVAL`**: Prevents app uninstallation or force-stopping by hooking Settings intents.
- **`SUICIDE`**: Remotely wipes local app configuration and uninstalls the core payload.
<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/3eb33ae7-aebd-4464-8547-979bb296eb27" alt="Full Web Page Screenshot" width="60%">
</details>

---

## 📸 03. Optics Tab

- **Camera Selector**: Switch between Front and Rear camera sensors.
- **`TAKE_PHOTO`**: Captures a single high-resolution JPEG photo.
- **`LIVE_STREAM`**: Streams live camera video frames (`/camera/frame`) directly to your browser.
- **`RECORD_VIDEO`**: Timed background video recording.
- **`NIGHT_MODE`**: Aggressively boosts sensor exposure and ISO parameters in low-light environments.
<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/bea953b9-753e-412d-b10e-12ded5055d82" alt="Full Web Page Screenshot" width="60%">
</details>

---

## 📍 04. Locate Tab

- **`REFRESH_GPS`**: Obtains precise GPS latitude, longitude, altitude, and accuracy coordinates.
- **Interactive Map View**: Displays device location on an interactive map with 1-click Google Maps links.
- **Multi-Provider Triangulation**: Automatic fallback across GPS hardware, Wi-Fi network location, and Cell Tower triangulation.
<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/3425ccc3-db7b-43ab-b3b9-8cb3c555469b" alt="Full Web Page Screenshot" width="60%">
</details>

---

## 🎯 05. Exploit Tab

- **`QR_GENERATOR`**: Generates custom QR code delivery payloads for physical scanning.
- **`NDEF_WRITER`**: Writes payload URLs to NFC tags for close-range proximity delivery.
- **`SMISHING_TEMPLATES`**: Pre-formatted phishing SMS templates for delivery campaigns.
- **`SHADOW_OVERLAY`**: Projects screenshot-accurate credential harvesting login screens over target applications.
<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/2aaa4c63-1ee8-4589-a78c-c2fc6e4deadf" alt="Full Web Page Screenshot" width="60%">
</details>

---

## 📂 06. Storage Tab

- **Remote File Manager**: Browse internal and external SD card directories with category filters (Images, Documents, Audio, Video, Archives).
- **In-Place File Editor**: View and edit text, JSON, and XML files directly from your browser.
- **File Download & Vault Exfiltration**: Download any file directly to your workstation or C2 cloud vault.
<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/835edd2f-35b4-4b7d-b527-c36ffa18990f" alt="Full Web Page Screenshot" width="60%">
</details>

---

## 🧠 07. Intel Tab

- **Live Notification Feed**: Captures incoming app notifications, WhatsApp messages, and system alerts.
- **Clipboard Manager**: Reads and writes system clipboard text in real time.
- **Browser Credentials**: Extracts saved browser form data, autofill history, and stored session tokens.
<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/5ff097a0-4460-4a54-a149-253b628f7f01" alt="Full Web Page Screenshot" width="60%">
</details>

---

## 💬 08. SMS Tab

- **Inbox Thread Inspector**: Browse and read full SMS conversation threads.
- **`SEND_SMS`**: Transmits custom SMS messages to any phone number directly from the target phone.
- **`WORM_BROADCAST`**: Broadcasts payload delivery links to all contacts via SMS.
<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/54acccb7-b412-4a57-9c15-ca36ce58050d" alt="Full Web Page Screenshot" width="60%">
</details>

---

## 🖼️ 09. MMS Tab

- **MMS Thread Viewer**: View incoming multimedia messages and thread histories.
- **Attachment Inspector**: Inspect and download image, audio, and video MMS attachments.
- **`SEND_MMS`**: Send custom MMS messages with file attachments.
<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/5340b242-bec6-440f-80ae-b1850d6eb163" alt="Full Web Page Screenshot" width="60%">
</details>

---

## 🎙️ 10. Acoustics Tab

- **`LISTEN_LIVE`**: Streams real-time 16kHz microphone audio directly to your browser speakers using the Web Audio API without saving temporary files to disk.
- **`VOICE_EMISSION (TEXT-TO-SPEECH)`**:
  - **Text Payload**: Transmit any text to be spoken out loud via device speakers.
  - **Voice Styles**: US Male/Female, UK Male/Female, Australian, Spanish, French, and Ghostface (Super Deep).
  - **Volume Slider**: Adjust speech volume output from 10% to 100%.
- **Ambient Burst Recording**: Quick-record buttons (`30s_BURST`, `60s_BURST`, `5m_BURST`) save `.m4a` files directly into the **`ACOUSTICS_VAULT`**.
- **Acoustics Vault**: Lists saved recordings with timestamp, recording duration (`DUR: 00:30`), file size, and 1-click **`GET`** download link.
<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/4ec71933-6264-4d75-90db-aebfdf40e636" alt="Full Web Page Screenshot" width="60%">
</details>

---

## 📞 11. Comms Tab

- **Call Log Inspector**: View complete cellular call history (incoming, outgoing, missed calls).
- **Call Metadata**: Inspect call durations, timestamps, and resolved contact names.
<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/ae00ca08-43e0-42c8-8bfa-fde16523d096" alt="Full Web Page Screenshot" width="60%">
</details>

---

## 👥 12. Contacts Tab

- **Address Book Reader**: Search and inspect all saved contacts (names, phone numbers, email addresses, and physical addresses).
<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/1da37dba-a84a-4e0f-8453-d763e73bade0" alt="Full Web Page Screenshot" width="60%">
</details>

---

## ⚙️ 13. Hardware Tab

- **Battery & Sensor Status**: Live battery percentage, charging status, battery temperature, and health.
- **System Specifications**: Inspect device model, brand, Android version, API level, build fingerprint, and memory usage.
- **Installed Applications Manager**: View all installed system and user apps with 1-click **`LAUNCH`** or **`FORCE_STOP`** controls.
<details>
  <summary>📸 Click to View Dashboard Screenshot</summary>
  <br>
  <img src="https://github.com/user-attachments/assets/f1874c3d-0866-4c1f-9f94-f5ca2c1aed6e" alt="Full Web Page Screenshot" width="60%">
</details>

---

## 📱 Navigation & Keyboard Shortcuts

- **Desktop Layout**: Navigation is displayed as a fixed sidebar. Pressing the `Esc` key instantly closes active modal popups and overlays.
- **Mobile Layout**: Responsive bottom-navigation bar with touch-swipe support to slide between adjacent dashboard tabs.
- **Viewport Locking**: The dashboard is intentionally viewport-locked (`user-scalable=no`). Pinch-to-zoom is disabled to prevent accidental UI scaling during rapid touch control or screen mirroring sessions.
- **Context Menu & DevTools**: Right-click context menus and the `F12` key are intercepted by the dashboard interface. To open browser developer tools, use your browser's main menu.
