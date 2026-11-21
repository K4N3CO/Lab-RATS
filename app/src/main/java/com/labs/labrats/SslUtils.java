package com.labs.labrats;

import android.util.Log;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * Utility to resolve TLS handshake issues on legacy Android versions (Android 7 / API 24-25)
 * caused by expired or missing root certificates (e.g., Let's Encrypt ISRG Root X1).
 */
public class SslUtils {
    private static final String TAG = "SslUtils";
    private static boolean isTrustAllInstalled = false;

    public static synchronized void trustAllCertificates() {
        if (isTrustAllInstalled) return;

        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            isTrustAllInstalled = true;
            Log.d(TAG, "Global SSL/TLS socket factory installed for legacy compatibility.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to install global SSL/TLS socket factory: " + e.getMessage());
        }
    }
}
