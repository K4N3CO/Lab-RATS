---

### `docs/09-security-notes.md`

```markdown
# 09 — Security notes

For deciding whether to trust this on a real target. Split into what is actually
protected, what is obfuscation, and what is simply absent.

## Authentication

| Property | Implementation | Verdict |
|---|---|---|
| Credential storage | Single shared password per device, `PasswordHasher`, on-device | Fine |
| Session cookie | Constant `AUTH_OK_1337` on every device, every install | Weak |
| Cookie check | Presence only | Weak |
| Master override | `admin1337` accepted forever, even after password change | Weak |
| Rate limiting | None | Weak |
| Lockout | None | Weak |
| CSRF token | None | Weak |
| Session expiry | None (logout wipes tokens) | Weak |
| Relay auth | JWT with rotation | Better than device |

The cookie is not a session identifier — it's a shared constant. Any captured cookie
works on any device. Combined with the unrevocable `admin1337` override, changing the
password does not lock anyone out who has already seen a cookie.

**Rotating properly** means editing the check in `router/AuthController.java` and
rebuilding, not changing the password in the UI.

## Transport

Plain HTTP on 9191. No TLS anywhere on the device side.

Credential handling is `base64(reverse(password))` client-side with a `0x_` prefix. That
defeats casual reading of a request body in DevTools. It is not encryption — reverse and
decode it and the password is plaintext.

| Path | Encryption | Notes |
|---|---|---|
| Operator → device, direct IPv6 | None | Whole session readable on path |
| Operator → relay | HTTPS | Render terminates TLS |
| Relay → device | Depends on tunnel | WebSocket tunnel, TLS only if the device dials HTTPS |

Mitigations, best first:

1. **Use the relay for anything sensitive.** The operator leg is TLS.
2. **Terminate TLS in front of the device** — a reverse proxy holding a cert, forwarding
   to 9191. Loses the P2P latency advantage.
3. **Tunnel through WireGuard/Tailscale** on the operator host and reach the device over
   the private overlay.
4. **Accept it.** On a trusted Wi-Fi network, plaintext HTTP to a phone is roughly as
   exposed as unencrypted browsing.

## Exposure

Direct IPv6 mode is internet-facing by default. There is no allowlist, no geo filter, no
IP restriction. `0.0.0.0` binding also exposes any tethered interface.

Consequences:

- Port scans find 9191 and the login page
- The login page discloses it's Lab-RATS to anyone who looks
- Pre-auth surface is the login handler and the static assets only

Reducing exposure:

- Change `PORT` in `Constants.java` and rebuild
- Put the device behind a proxy that filters by source IP
- Use a non-obvious path prefix
- Keep the password long — no rate limit means online guessing is unbounded otherwise

## At rest

| Artifact | Protection | Reality |
|---|---|---|
| Device config | Encrypted with build-time `ENCRYPTION_KEY` (`Obf.java`) | Key ships in the same APK — obfuscation, not cryptography |
| System logs | Encrypted at build time | Same weakness |
| Relay `uploads/` | None | Served unauthenticated at `/c2/downloads` |
| Relay `devices.json` | None | Plaintext fleet list with names and IPs |
| Render disk | Ephemeral | Treat the vault as scratch, not archive |
| Keystore | Plain file on build host | Anyone with the host can sign as you |

Anyone who pulls the APK can extract the key and decrypt local config. The realistic
attacker for that is a forensic analyst with the device image — and against them this is
a speed bump, not a wall.

## Supply chain

| Dependency | Source | Risk |
|---|---|---|
| Gradle deps | Maven Central / Google | Normal; pin versions |
| `apktool.jar` 2.9.3 | Downloaded by `bind.sh` at first run | Unpinned — verify hash for a campaign |
| Auto-installed JDK | `apt` / `brew` / `winget` / `choco` | Repo-trusted |
| Catbox / Litterbox / Tmpfiles | Runtime upload | Anonymous, unauthenticated, expiring |
| Carrier files | Generated locally | No third-party templates, clean |

`apktool.jar` is the one unpinned download in the build path. Pin the SHA-256 in
`bind.sh` if builds must be reproducible.

## Permission surface

The Play Protect decoy auto-grants, in rough order of sensitivity:

SMS read/send · call logs · contacts · camera · microphone · location (fine + coarse) ·
storage · notifications access · phone state · Accessibility

Accessibility is the outlier — it cannot be auto-granted and must be toggled manually.
It is also the highest-privilege grant in the set: it can read every screen and inject
input.

## What leaves the device

Assume all of it does:

- Device identifiers, model, Android version
- IP, port, connection type, carrier
- Battery, power state, free storage
- Full notification stream (WhatsApp, Telegram, RCS, system)
- Keystrokes and screen text, including OTPs and passcodes
- SMS and MMS content, call logs, contacts
- GPS coordinates
- Photos, video, audio recordings
- File listings and file contents you open

The notification and keylog streams together reconstruct most of a person's day.

## Detection, and how to reduce it

| Tell | Fix |
|---|---|
| Package `com.android.system.stability` | Own ID per campaign |
| Signer `CN=Lab-RATS Developer` | Own keystore and subject |
| Accessibility entry | Unavoidable — it is the mechanism |
| Persistent notification | Detached; still present in FGS list |
| Listens on 9191 | Change `PORT` and rebuild |
| Login page design | Reskin `c2/public/` assets |
| Recognizable decoy artwork | Reskin the decoy PNGs |
| Traffic to `onrender.com` | Own domain in front of the relay |

**A fresh keystore per campaign is the highest-value single control.** Signature reuse
correlates installs across devices even when names, IDs and icons all differ. One keystore
across a fleet is one signature to fingerprint.

AV verdicts age. Play Protect, Knox and McAfee have each shipped signatures catching
earlier builds. A build that passed last month proves nothing about this month.

## Hardening checklist

**Build**

- [ ] Unique package ID, not the default
- [ ] Fresh keystore, custom subject (`CN=` at minimum)
- [ ] `PORT` changed from 9191
- [ ] Decoy artwork reskinned
- [ ] `apktool.jar` hash pinned

**Deploy**

- [ ] Password changed immediately after first login
- [ ] Relay behind your own domain
- [ ] No sensitive capture on untrusted networks in direct mode
- [ ] `devices.json` and `uploads/` treated as public

**Operate**

- [ ] Password rotated per engagement
- [ ] Keystore archived per campaign, not reused
- [ ] Relay vault exported before Render cycles the disk
- [ ] Logs pulled before suicide

## Residual risk

- Not persistent across factory reset
- Not root; priv-app paths need the AOSP installer and still don't cover everything
- Not covert against a technical user who opens Settings → Apps
- Not resistant to a device image + APK comparison
- No integrity checking — a modified APK installs fine
- No replay protection on control endpoints

## What this is not

Not a substitute for a commercial MDM. No policy engine, no compliance reporting, no
remote wipe beyond the self-destruct, no per-device certificate. It is a single-operator
tool that assumes a small fleet and an operator who reads logs.
