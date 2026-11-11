# 13 — Direct IPv6 hardening

> Path C puts an unauthenticated-by-default HTTP dashboard on the public internet, on a device you
> don't control physically, reachable by anyone who learns the address. This doc is the work you do
> to make that acceptable.

---

## Threat model

Be explicit about what you're defending against, because most of these controls only help against
some of it.

| Adversary | Capability | Realistic? |
| --- | --- | --- |
| Address scanner | Sweeps `2xxx::/16` for open 9191, tries defaults | **Yes, constantly** |
| Curious target | Knows the app exists, opens Settings, tries to remove it | Yes |
| Network observer | Reads cleartext traffic on shared Wi-Fi | Yes |
| Another operator with a leaked cookie | Full control, no password needed | Yes |
| Malware / forensic analyst | Pulls the APK, greps for secrets | Sometimes |
| Carrier / ISP | Sees the connection, could MITM cleartext | Low but nonzero |

The dominant risk is #1: an automated sweep finding 9191 and trying `admin1337`. It costs an attacker
nothing and it succeeds on any un-hardened build.

The secondary risk is that hardening the *network* breaks reachability, because Path C's whole value
is direct reachability. Every control below is a trade against that. Pick deliberately.

---

## Severity of the default posture

| Item | Default | Severity |
| --- | --- | --- |
| Password | `admin1337` | **Critical** — published, no lockout |
| Master override | Always accepted, even after change | **High** — can't be revoked by config |
| Session cookie | Constant `AUTH_OK_1337`, all devices | **High** — portable, no expiry |
| Transport | Plain HTTP | **High** on shared networks |
| Rate limiting | None | **High** — makes short passwords guessable |
| Bind scope | `0.0.0.0` | Medium — all interfaces, incl. tether |
| Source allowlist | None | Medium |
| Port | 9191 | Low — fingerprint only |
| Package / signer | Lab-RATS defaults | Low — attribution |

The first five are the ones that actually matter. Fix them in that order.

---

## 1. Credentials (do this first)

### 1.1 Set a long passphrase

Terminal tab → password field. There's no lockout and no rate limit, so length is your only defence
against offline-speed guessing over the wire.

- 20+ characters, or a 5-word diceware phrase.
- Unique per device. A shared password across a fleet means one leak is the whole fleet.
- Change it at first login, before anything else.

### 1.2 Kill the master override

`admin1337` is hardcoded as a permanent fallback in `PasswordHasher`. It survives every password
change. To remove it, edit the fallback comparison and rebuild:

```bash
grep -rn "admin1337" app/src/main/java/com/labs/labrats/
```

Change the literal to something only you know — cheaper than removing the branch, and it keeps the
recovery path if you lock yourself out. **Keep a copy of the new value outside the device**, or you
have no recovery once the password is also lost.

### 1.3 Replace the session cookie

The cookie value `AUTH_OK_1337` is identical on every device and every install, and the app checks
presence, not signature. A cookie lifted from one device works on all of them, forever.

```bash
grep -rn "AUTH_OK_1337" app/src/main/java/ c2-server/
```

Two places must agree (the login handler's `Set-Cookie` and the guard middleware). Change both, and
change them again per campaign if you run multiple devices you care about separately.

Note the cookie is not `HttpOnly` or `Secure` in direct mode — there's no TLS to make `Secure` mean
anything, and the dashboard JS reads it in places.

---

## 2. Network restriction

The single highest-value control after credentials. Choose one tier.

### Tier A — source allowlist (best, if your address is stable)

```bash
# allow only your own prefix, drop everything else on 9191
sudo ip6tables -A INPUT -p tcp --dport 9191 -s 2a02:8109:abcd:1234::/64 -j ACCEPT
sudo ip6tables -A INPUT -p tcp --dport 9191 -j DROP
```

But the *device* is the listener, not your box — the device's own firewall is not yours to edit
without root. So an `ip6tables` rule on your laptop does nothing for inbound traffic to the phone.

Real options for Tier A:

- **Rooted device** — `su -c ip6tables …` on the phone. Genuinely effective.
- **Router-level** if the target is on Wi-Fi you control — allow 9191 only from your prefix.
- **Overlay network** (§2.2) — the address stops being public at all.

### Tier B — overlay network (best without root)

Put both ends on WireGuard or Tailscale and connect over the overlay address instead of the GUA.
The dashboard is then unreachable from the open internet entirely.

```bash
# tailscale on the target requires root or the Android app installed
tailscale status
tailscale ip -6
```

On Android this needs the Tailscale app installed and logged in — a visible extra icon, and one more
thing for the target to notice. WireGuard has the same trade.

The upside is total: no scans, no carrier filtering to fight, and the address is stable. The cost is
a persistent VPN client on the device.

### Tier C — non-standard port only

Change 9191 and you drop off most sweep lists. See `12-server-setup.md` §8. Weak but free, and it
composes with A or B.

---

## 3. Transport

### 3.1 Accept cleartext, or front it

Direct mode is HTTP. You can't add TLS to the payload's own listener without patching it. Two ways
around it:

**SSH port-forward** — no certs, no DNS, per-session:

```bash
ssh -N -L 9191:[2a02:8109:...]:9191 you@your-v6-host
# then browse http://localhost:9191
```

The hop from your laptop to your host is encrypted; the hop from your host to the device is not. It
does mean the traffic is no longer on the open path between you and the device, which is the point.

**Reverse proxy with a real cert** — for a semi-permanent setup. Caddy on a v6 host with an `AAAA`
record gets you automatic TLS in six lines:

```caddyfile
lab1.yourdomain.com {
    reverse_proxy [2a02:8109:...]:9191
}
```

Then browse `https://lab1.yourdomain.com`. Bracket the upstream, or Caddy parses the last group as a
port.

The proxy leg (Caddy → device) is still cleartext on the internet. It only helps if the proxy is on
the same network as the device or on a trusted path.

### 3.2 Don't send secrets over hostile Wi-Fi

With no TLS, anything you type in the dashboard — passwords, SMS bodies, keylog contents, file
contents — is readable by anyone on the same open network. If you're on café Wi-Fi, tunnel.

---

## 4. Bind scope

The payload binds `0.0.0.0`, which includes every interface — Wi-Fi, cellular, and any USB or
Bluetooth tether. If the device is also tethering, the dashboard is reachable from tether clients too.

Without root you can't narrow the bind. You can narrow exposure:

- Turn tethering off while you're not using it.
- Prefer the cellular interface over Wi-Fi when both exist (usually the more filtered path).

---

## 5. Fingerprint reduction

Detection here is low-severity but cheap to fix, and matters if the target is technical.

| Tell | Change |
| --- | --- |
| Package `com.android.system.stability` | Your own ID at build |
| Signer `CN=Lab-RATS Developer, O=Lab-RATS.LABS, C=US` | Own keystore + `dname` in `build.sh` |
| Port 9191 | §2 Tier C |
| Accessibility service label | Rename in `strings.xml` |
| Notification text | Edit in `StatusNotification.java` |
| Default decoy art | Reskin the mipmap PNGs |
| Server banner in HTTP headers | Patch the handler's `Server` header |

Rebuild for each. Signature reuse is the one that matters most — the same cert across devices links
them even when names differ.

---

## 6. Permission minimization

Every permission you grant is a Settings entry the target can see and a prompt they can revoke.

Grant in order of need, and stop:

1. **Accessibility** — unavoidable, it's the persistence mechanism.
2. SMS — only if you need SMS/MMS and `!RESTART_C2`.
3. Camera / mic — only for surveillance work.
4. Location — only for tracking.
5. Contacts / call logs / storage — only for exfil.

Skip SMS and you lose the restart backdoor. That's a real trade; take it consciously.

Revocation is normal, not an attack: OEM battery managers strip permissions on their own schedule.
**REPAIR PERMISSIONS** in the Ghost tab is the fix, and you'll use it.

---

## 7. Data at rest

### On the device

Config is encrypted with the build-time `ENCRYPTION_KEY`. Key and ciphertext ship in the same APK, so
it stops casual forensics, not a determined analyst with the APK in hand.

Media lands in `System-Security/` in private app storage. It survives the app being updated and dies
with an uninstall.

### On your side

- **Vault / receiver log** — plaintext, unauthenticated. In direct mode there's no vault; if you run
  the receiver from §4b of doc 12, `checkins.log` is world-readable on whatever box hosts it.
- **Google Sheet** — anyone with the URL can append. Keep it unlisted, out of public repos.
- **Notes with addresses and passwords** — don't put them in a public gist.

### Back up

- `lab-rats-keystore.jks` + `keystore.properties` — lose these and you can't update over an existing
  install.
- `ENCRYPTION_KEY` from `local.properties` — lose it and existing config is unreadable.
- The overridden master passcode.

---

## 8. Operational hygiene

- **Fresh keystore per campaign.** Never reuse across unrelated targets.
- **Unique password per device.** Non-negotiable with no rate limiting.
- **Rotate the cookie constant** between campaigns if devices must not share sessions.
- **Bump `versionCode` on every shipped build** — Android won't replace a lower one.
- **Clean `output/`** between builds; stale APKs get shipped by accident.
- **Record which build is on which device.** After three devices you will not remember.

---

## 9. Verification

Audit the build before delivery:

```bash
# nothing should return the default password path
grep -n "admin1337" app/src/main/java/com/labs/labrats/PasswordHasher.java

# cookie constant should differ from AUTH_OK_1337
grep -rn "c2_auth_token" app/src/main/java/ c2-server/server.js

# package and signer should not be defaults
aapt2 dump badging apk-builder/output/signed_v1.apk | head -2
keytool -list -v -keystore lab-rats-keystore.jks | grep -i "owner\|alias"
```

Audit the deployment:

```bash
# confirm the port answers from outside
curl -sI --max-time 8 "http://[2a02:8109:...]:9191/" | head -1

# confirm a wrong password is rejected
curl -s -o /dev/null -w '%{http_code}\n' -X POST -d 'p=0x_' "http://[2a02:8109:...]:9191/login"
```

The login check returns 200 either way — the page is re-rendered on failure rather than a 401, so
check the body, not the status code. Scripts that trust status codes will report success on a wrong
password.

---

## 10. Teardown and revocation

In order of increasing finality:

1. **Revoke access** — change the device password. Existing sessions survive; the cookie constant is
   checked, not re-derived.
2. **Rotate the cookie** — requires a rebuild, so only worth it if a cookie actually leaked.
3. **`suicide`** (Ghost tab) — wipes local config, then uninstalls. Unrecoverable without a reinstall.
4. **Manual removal** — Anti-Removal off → Settings → Uninstall. Leaves app storage behind until the
   uninstall completes; check for leftovers under `Android/data/<package>/`.

`adb uninstall` and Safe Mode both bypass Anti-Removal, so a technical target can always get rid of
it.

---

## 11. Residual risk

After everything above, these remain true:

- `admin1337` still works unless you patched it.
- The cookie is a bearer token with no expiry.
- Traffic is cleartext unless you tunnelled.
- Anyone who reaches the login page can hammer it.
- Accessibility is a visible, revocable service.
- The app is in Settings → Apps, always.
- A reboot without Accessibility enabled drops persistence to the boot receiver alone.

None of these are fixable from config. If they're unacceptable, the answer isn't more hardening —
it's the relay plus an overlay network, or a different tool.

---

## 12. Hardening checklist

- [ ] Long unique passphrase set at first login
- [ ] `admin1337` overridden in source and rebuilt; new value recorded off-device
- [ ] Cookie constant changed from `AUTH_OK_1337` (both files)
- [ ] Non-default package ID and keystore subject
- [ ] Port changed from 9191, or allowlist/overlay in place
- [ ] Transport tunnelled or proxied if on untrusted networks
- [ ] Permissions trimmed to what the engagement needs
- [ ] Keystore, encryption key and master passcode backed up
- [ ] `versionCode` bumped, `output/` cleaned
- [ ] Anti-Removal confirmed on, app confirmed absent from Recents
- [ ] Verification commands from §9 run and clean