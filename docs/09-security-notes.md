# Security notes

Written for people deciding whether to trust this on a real target.

## Authentication

- Single shared password per device, stored locally.
- Session cookie is the constant string `AUTH_OK_1337` — the same value on every device and every
  install. Cookie presence is the whole check.
- `admin1337` is accepted as a master override forever, even after the password is changed.
- No rate limit, no lockout, no CSRF token, no session expiry.

Consequence: a stolen cookie is portable to any device, and the master override can't be revoked.
Rotate by editing the check in `router/`, not by changing the password.

## Transport

Plain HTTP on 9191. Credentials are `base64(reverse(pw))` client-side — obfuscation. Anyone on the
path reads the whole session.

Mitigations: use the relay's TLS for the proxy leg, or front the device with a TLS terminator. Do not
send anything sensitive over a hostile network in direct mode.

## Exposure

In direct IPv6 mode the dashboard is internet-facing by default. There is no allowlist. Bind
`0.0.0.0` means all interfaces including any tethered ones.

## At rest

- Device config is encrypted with the build-time `ENCRYPTION_KEY`. Key and ciphertext ship in the
  same APK, so it's obfuscation against casual forensics, not cryptographic protection against
  someone who pulls the APK.
- Relay `uploads/` is unencrypted and unauthenticated at `/c2/downloads`.
- `devices.json` is plaintext.
- Render's disk is ephemeral — treat the vault as scratch, not archive.

## Supply chain

- Carrier files are generated, not fetched, so they're clean of third-party dependencies.
- `bind.sh` downloads `apktool.jar` 2.9.3 from GitHub on first use — pin the hash if you care.
- Gradle pulls its normal dependency tree on first build.
- Auto-installed JDKs come from `apt`/`brew`/`winget`/`choco` repos.

## Detection, and how to reduce it

| Tell | Fix |
| --- | --- |
| Package `com.android.system.stability` | Use your own ID |
| Signer `CN=Lab-RATS Developer` | Your own keystore + subject |
| Accessibility service entry | Unavoidable — it's the persistence mechanism |
| Persistent notification | Minimize, can't remove |
| Listens on 9191 | Change `PORT` constant and rebuild |
| Known decoy artwork | Slight reskin of the decoy PNGs |

A fresh keystore per campaign matters most. Signature reuse correlates installs across devices even
when names and IDs differ.

## What this is not

- Not persistent across a factory reset.
- Not root. Root-only paths (priv-app install, some shell features) need the AOSP/`PrivilegedInstaller`
  route, and even then not all of them work.
- Not covert against a technical user who opens Settings → Apps.