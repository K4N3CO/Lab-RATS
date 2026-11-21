# FAQ

Answers are the short form. Where an answer is "it's weak, here's why", the long form is in
[09 — Security notes](09-security-notes.md).

---

## Authentication

**Is the password actually secure?**
No. It's a single shared secret with no rate limit and no lockout, so online guessing is
unbounded — a six-character password falls in minutes against a scripted client. Use 16+
characters, or accept that the password is a speed bump, not a boundary.

**Why does the cookie never change between devices?**
It's a constant (`AUTH_OK_1337`), not a session ID. There's no server-side session table to
issue tokens from — the check is "is the cookie present". That's the whole design.

**Can I revoke a leaked cookie?**
Not without rebuilding. The cookie value is compiled in, so revoking means editing
`router/AuthController.java` and shipping a new APK to every device.

**Does changing the password lock out someone who already has a cookie?**
No. The cookie check doesn't consult the password. Someone who captured it keeps access
until you rebuild.

**Why does `admin1337` still work after I changed the password?**
It's a deliberate master override — the recovery path when you forget a per-device password.
It is also unrevocable, so it's a permanent backdoor in every build. Change it in source if
you care.

**Is there brute-force protection?**
No. No delay, no counter, no lockout, no CAPTCHA. Nothing slows a guess loop.

**Is there CSRF protection?**
No token. Cross-site requests against a device on a routable address are possible if you can
get a browser to issue them with the cookie present.

**Do sessions expire?**
No. Logout wipes the cookie client-side (`AuthController` clears tokens and cookies) but
nothing server-side expires. Re-login is enforced after logout because the cookie is gone.

**What does the relay do better?**
JWT with rotation, plus TLS on the operator leg. The device-side auth model is unchanged —
the relay only improves transport and session handling, not the device's own weakness.

---

## Transport

**Is traffic encrypted?**
Direct mode: no. Plain HTTP on 9191, whole session readable on path. Relay mode: operator
leg is HTTPS, relay-to-device depends on the tunnel.

**What's `base64(reverse(password))` then?**
Obfuscation. It stops the password being literal in a request body in DevTools. Reverse it
and base64-decode it and you have the plaintext.

**Can I add TLS?**
Yes, but only by putting something in front of the device — a reverse proxy holding a cert,
or a WireGuard/Tailscale overlay. There is no TLS in the payload. See
[18 — TLS reverse proxy](18-tls-reverse-proxy.md).

**Is the dashboard safe on café Wi-Fi?**
Only for non-sensitive work. Use the relay or the overlay.

---

## Exposure

**Is the device reachable by anyone on the internet?**
In direct IPv6 mode, yes. No allowlist, no geo filter. `0.0.0.0` binding also covers tethered
interfaces.

**Does a port scan find it?**
Yes, and the login page identifies the tool. Changing `PORT` in `Constants.java` reduces
drive-by finds but not a targeted scan.

**Can I restrict by source IP?**
Not in the payload. Put a filtering proxy in front, or use the overlay.

---

## Data at rest

**Is the on-device config encrypted?**
Nominally — `Obf.java` with a build-time `ENCRYPTION_KEY`. The key is in the same APK, so
anyone who pulls the APK can decrypt. It's anti-casual-forensics only.

**Are the relay uploads protected?**
No. `/c2/downloads` is unauthenticated. Anyone with the URL and a filename reads the media.

**Is `devices.json` sensitive?**
Yes — plaintext device names, IPs and timestamps. Treat it as a fleet inventory.

**Is Render storage durable?**
No. Free-tier disk is ephemeral. Export the vault; don't archive to it.

---

## Detection

**Is it detected by Play Protect right now?**
Test a fresh build. Signature updates land constantly; any answer here expires in weeks.

**What gives it away first?**
Settings → Apps: the package name, then the Accessibility entry. Both are unavoidable-ish —
the package you can change, Accessibility you can't.

**Does a fresh keystore help?**
Yes, more than any other single control. Signature reuse fingerprints a fleet even when
names and IDs all differ. One keystore per campaign.

**Can the version string be a tell?**
It's randomized to look like an OTA patch, which is plausible until someone compares two
devices with mismatched "system" versions.

**Does the notification show?**
The FGS notification is detached from the task, not removed. It's still in the service list.

---

## Capability

**Does it need root?**
No. Root only widens the shell and enables priv-app install via the AOSP path.

**Does it survive a reboot?**
Yes, via the boot receiver and watchdog — provided it isn't force-stopped and Accessibility
is still enabled. Force-stop breaks it until the next manual launch.

**Are the "zero-click" vectors actually zero-click?**
Best-effort. They raise the odds; none guarantees install with no interaction.

**Does the binder work on any APK?**
Mostly. Split APKs, obfuscated manifests and unusual launcher aliases fail. It bails rather
than emitting a broken APK.

**Can two operators connect at once?**
Yes, same shared cookie. No write conflict handling — last write wins.

---

## Operation

**How do I change the port?**
`Constants.java`, then rebuild. `bind.sh` and the direct-IPv6 template hardcode 9191 as well.

**How do I remove it cleanly?**
Ghost → Anti-Removal off → Settings → Uninstall. Or Ghost → Suicide for remote wipe and
removal.

**Is the Google Sheet secure?**
No more than the URL. Anyone with it appends rows. Unlisted only, never in a public repo.

**Why does Render lose my devices?**
Ephemeral filesystem, idle sleep. Attach a disk and pay, or use direct IPv6.

**Can I build on Windows without WSL?**
Yes, `build.bat` / `build.ps1` are native. Option 7 needs Python on PATH.

**Does it work on emulators?**
Yes, but emulators rarely have routable IPv6 — test with the relay.
