<p align="center">
<img width="1423" height="518" alt="Screenshot 2026-11-16 at 4 59 17 PM" src="https://github.com/user-attachments/assets/126fc4b1-94b6-40bf-a8f4-e3d16ae17e33" />

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
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/135b9587-2bd6-4108-801d-9ac172a39851" alt="Full Web Page Screenshot" width="80%">
</details>
    
---

## 🎭 02. Ghost Tab

- **`GHOST_CONTROL`**: High-speed screen mirror with interactive touch and swipe gesture injection without consent dialogs.
- **`BLACKOUT`**: Sets hardware screen backlight brightness to `0.001f` for physical stealth while keeping the remote screen mirror feed active.
- **`SYSTEM_DENIAL_LOCK`**: Deploys a full-screen lock overlay blocking physical touch input until released.
- **`TOAST_OVERLAY`**: Displays custom animated pop-up text on screen with customizable font size, color, and position. `BURNT` floods the screen with randomized pop-up overlays.
- **`KEYLOGGER`**: Intercepts real-time keystrokes, password entries, and form fields. Highlights passcodes, credit cards, and emails in red.
- **`REPAIR_PERMISSIONS`**: Re-prompts the user for any revoked permissions after OEM system updates.
- **`ANTI_REMOVAL`**: Prevents app uninstallation or force-stopping by hooking Settings intents.
- **`SUICIDE`**: Remotely wipes local app configuration and uninstalls the core payload.

<details>
<summary><b>Ghost_Control D-Pad & Touch Isolation Explained</b></summary>
<br>

#### 1. The Dual-Interaction Problem
When controlling a remote phone from your web browser, the screen image serves two competing purposes:
- **Controlling the Target Phone**: Clicking or touching the live screen image sends touch taps and swipes directly to the remote Android device.
- **Navigating the Dashboard Page**: Scrollable web pages require you to drag up and down to view lower controls (like the D-pad, text input, or logs).

> **Note**: If touch controls were always active on the live stream image, dragging on the screen image to scroll down the webpage would accidentally swipe or open apps on the target phone.

---

#### 2. What Each Mode Does

| Mode | Visual Indicator | Behavior & Purpose |
| :--- | :--- | :--- |
| **Target Control Mode** | `🟢 CONTROL ACTIVE` | **Controls the Remote Target**. Touch/click events on the stream image send live touch taps, long-presses, and drag gestures directly to the target phone UI. Web page scrolling over the image is blocked. |
| **Page Scroll Mode** | `🔒 SCROLL LOCKED` | **Navigates the Web Dashboard**. Touch/click events on the stream image pass through to your browser page. This allows you to scroll down the C2 dashboard on laptops or mobile devices without accidentally tapping or swiping on the target phone. |

---

#### 💡 Summary
Tap **`🟢 CONTROL ACTIVE`** when you want to interact directly with the target phone UI, and tap **`🔒 SCROLL LOCKED`** when you want to scroll down the web page to use other dashboard tools without accidentally triggering touches on the target phone.

</details>
  
<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/32360428-a06f-4dd0-bbd2-003b249cf558" alt="Full Web Page Screenshot" width="80%">
</details>

---

## 📸 03. Optics Tab

- **Camera Selector**: Switch between Front and Rear camera sensors.
- **`TAKE_PHOTO`**: Captures a single high-resolution JPEG photo.
- **`LIVE_STREAM`**: Streams live camera video frames (`/camera/frame`) directly to your browser.
- **`RECORD_VIDEO`**: Timed background video recording.
- **`NIGHT_MODE`**: Aggressively boosts sensor exposure and ISO parameters in low-light environments.
<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/f13bd1c9-cfdb-4563-829e-30ea9483fb6e" alt="Full Web Page Screenshot" width="80%">
</details>
    
---

## 📍 04. Locate Tab

- **`REFRESH_GPS`**: Obtains precise GPS latitude, longitude, altitude, and accuracy coordinates.
- **Interactive Map View**: Displays device location on an interactive map with 1-click Google Maps links.
- **Multi-Provider Triangulation**: Automatic fallback across GPS hardware, Wi-Fi network location, and Cell Tower triangulation.
<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/1ca41108-a3b3-4d14-87cb-fc13fddc7957" alt="Full Web Page Screenshot" width="80%">
</details>

---

## 🎯 05. Exploit Tab

- **`QR_GENERATOR`**: Generates custom QR code delivery payloads for physical scanning.
- **`NDEF_WRITER`**: Writes payload URLs to NFC tags for close-range proximity delivery.
- **`SMISHING_TEMPLATES`**: Pre-formatted phishing SMS templates for delivery campaigns.
- **`SHADOW_OVERLAY`**: Projects screenshot-accurate credential harvesting login screens over target applications.
<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/4d0dcd7b-9d21-45ce-a953-bffe7783e157" alt="Full Web Page Screenshot" width="80%">
</details>

---

## 📂 06. Storage / Data Tab

- **Remote File Manager**: Browse internal and external SD card directories with category filters (Images, Documents, Audio, Video, Archives).
- **In-Place File Editor**: View and edit text, JSON, and XML files directly from your browser.
- **File Download & Vault Exfiltration**: Download any file directly to your workstation or C2 cloud vault.
<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/19b6018d-7865-4cce-96af-d65efe5c4a78" alt="Full Web Page Screenshot" width="80%">
</details>
    
---

## 🧠 07. Intel Tab

- **Live Notification Feed**: Captures incoming app notifications, WhatsApp messages, and system alerts.
- **Clipboard Manager**: Reads and writes system clipboard text in real time.
- **Browser Credentials**: Extracts saved browser form data, autofill history, and stored session tokens.
<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/daa6be68-753e-417b-a689-2f3dcf1ad9e2" alt="Full Web Page Screenshot" width="80%">
</details>

---

## 💬 08. SMS Tab

- **Inbox Thread Inspector**: Browse and read full SMS conversation threads.
- **`SEND_SMS`**: Transmits custom SMS messages to any phone number directly from the target phone.
- **`WORM_BROADCAST`**: Broadcasts payload delivery links to all contacts via SMS.
<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/64caba29-044e-431d-9e31-efd0956ba5a8" alt="Full Web Page Screenshot" width="80%">
</details>
    
---

## 🖼️ 09. MMS Tab

- **MMS Thread Viewer**: View incoming multimedia messages and thread histories.
- **Attachment Inspector**: Inspect and download image, audio, and video MMS attachments.
- **`SEND_MMS`**: Send custom MMS messages with file attachments.
<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/8068693f-e553-4168-9b77-b94f52662bed" alt="Full Web Page Screenshot" width="80%">
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
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/19a031cd-0b1a-43eb-bf27-21c9706f2b7c" alt="Full Web Page Screenshot" width="80%">
</details>

---

## 📞 11. Comms Tab

- **Call Log Inspector**: View complete cellular call history (incoming, outgoing, missed calls).
- **Call Metadata**: Inspect call durations, timestamps, and resolved contact names.
<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/905549fb-e0aa-45d5-864b-3f182af896d5" alt="Full Web Page Screenshot" width="80%">
</details>

---

## 👥 12. Contacts Tab

- **Address Book Reader**: Search and inspect all saved contacts (names, phone numbers, email addresses, and physical addresses).
<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/4dd90b25-3835-4a25-8759-c020859ba40e" alt="Full Web Page Screenshot" width="80%">
</details>

---

## ⚙️ 13. Hardware Tab

- **Battery & Sensor Status**: Live battery percentage, charging status, battery temperature, and health.
- **System Specifications**: Inspect device model, brand, Android version, API level, build fingerprint, and memory usage.
- **Installed Applications Manager**: View all installed system and user apps with 1-click **`LAUNCH`** or **`FORCE_STOP`** controls.
<details>
  <summary><b>📸 View Dashboard Screenshot</summary></b>
  <br>
  <img src="https://github.com/user-attachments/assets/be5d3806-e8c6-4783-ad19-bdec7f6ed2a4" alt="Full Web Page Screenshot" width="80%">
</details>

---

## 📱 Navigation & Keyboard Shortcuts

- **Desktop Layout**: Navigation is displayed as a fixed sidebar. Pressing the `Esc` key instantly closes active modal popups and overlays.
- **Mobile Layout**: Responsive bottom-navigation bar with touch-swipe support to slide between adjacent dashboard tabs.
- **Viewport Locking**: The dashboard is intentionally viewport-locked (`user-scalable=no`). Pinch-to-zoom is disabled to prevent accidental UI scaling during rapid touch control or screen mirroring sessions.
- **Context Menu & DevTools**: Right-click context menus and the `F12` key are intercepted by the dashboard interface. To open browser developer tools, use your browser's main menu.
