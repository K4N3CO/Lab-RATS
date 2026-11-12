# 10 ⏤ Frequently Asked Questions (FAQ)

Answers to common questions about Lab-RATS configuration, deployment, and security.

---

### Does it require root access?
No. Everything operates completely unrooted. Root is optional and only required for AOSP system-privilege installation.

### Does the payload survive a device reboot?
Yes. It registers a boot completion receiver (`BOOT_COMPLETED`) and an Accessibility watchdog that automatically revives the service upon device restart.

### Will the target user see the app icon?
The app icon displays under the chosen decoy persona (e.g. Play Protect, Calculator, Weather) in the launcher and Settings. It is automatically hidden from the Recent Apps switcher.

### Why use IPv6 instead of IPv4?
Cellular carriers assign devices publicly routable IPv6 Global Unicast Addresses (GUAs), allowing direct peer-to-peer connection without port forwarding or tunneling tools like ngrok.

### Can multiple operators connect simultaneously?
Yes. Multiple operators can connect using the shared C2 password or session token.

### Can I control a target device from a smartphone?
Yes. The C2 dashboard UI is responsive and optimized for mobile browser controls.

### How do I change the default C2 port `9191`?
Update the default port constant in `FirebaseConfig.java`, update the wizard configuration in `build.sh`, and rebuild the APK.

### How do I remove the app cleanly?
Navigate to C2 Terminal $\rightarrow$ disable Anti-Removal $\rightarrow$ open Android Settings $\rightarrow$ Apps $\rightarrow$ Uninstall. Alternatively, trigger the remote **Suicide Command** to wipe local data and uninstall.

### Is Google Sheets check-in logging secure?
Anyone with the Google Apps Script Web App URL can log check-in rows. Keep the Web App URL private and unlisted.

### Does it run on Android emulators?
Yes. However, Android emulators typically run on NATed IPv4 network bridges; use Render Relay Mode for emulator testing.

### Can I compile on Windows without WSL?
Yes. `build.bat` (CMD) and `build.ps1` (PowerShell) run natively on Windows.

### Is it detected by Play Protect?
Fresh builds signed with a unique RSA keystore pass undetected. Reusing public default keystores across multiple campaigns correlates signatures over time.
