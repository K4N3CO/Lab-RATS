# 10 ⏤ FAQ

### Does it need root?
No. Everything works unrooted. Root only expands the shell and enables priv-app install via the AOSP
path.

### Does it survive a reboot?
Yes, via the boot receiver and the accessibility watchdog — provided the app isn't force-stopped and
Accessibility is still enabled.

### Will the target see the app?
It's in Settings → Apps and in the app drawer under the decoy name. It is not in Recents. It's not
invisible, it's disguised.

### Why IPv6? What if the target has no IPv6?
IPv6 avoids CGNAT and port forwarding entirely. Without it, use the relay — it's outbound-only and
works everywhere.

### Can two operators connect at once?
Yes on the device side (same shared cookie). No meaningful conflict handling on writes — the last
write wins.

### Can I have multiple devices?
Direct mode: yes, each has its own address. Relay mode: yes, via the fleet list and
`c2_target_device` cookie.

### Can I control a device from my phone?
Yes. The dashboard is the same responsive UI; that's what the mobile layout is for.

### How do I change the port?
It's a constant in the payload source. Change it and rebuild. `bind.sh` and the wizard's direct-IPv6
template both hardcode 9191 too.

### How do I remove it cleanly?
Ghost → Anti-Removal off → Settings → Uninstall. Or Ghost → Suicide for a remote wipe-and-remove.

### Is the Google Sheet secure?
No more than the URL. Anyone with it can append rows. Keep it unlisted and don't put it in a
public repo.

### Why is the version number weird?
Randomized per build to look like an OTA patch. Override with option 3.

### Does it work on emulators?
Yes, but emulators usually have no routable IPv6 — test with the relay.

### Can I build on Windows without WSL?
Yes, `build.bat` / `build.ps1` are native. Option 7 needs Python on PATH.

### Does the binder work on any APK?
Mostly. Split APKs, heavily obfuscated manifests and apps with unusual launcher aliases fail. The
script bails rather than producing a broken APK.

### Are the "zero-click" vectors really zero-click?
Best-effort. They raise the odds substantially; none guarantees an install with no user interaction.

### Why does Render lose my devices?
Free-tier filesystem is ephemeral and idle services sleep. Attach a disk and pay, or use direct IPv6.

### Is it detected by Play Protect right now?
Test with a fresh build. Signature updates land constantly; any answer here has a short shelf life.
