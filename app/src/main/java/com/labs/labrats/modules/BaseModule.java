package com.labs.labrats.modules;

import android.content.Context;
import com.labs.labrats.FirebaseConfig;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;

public abstract class BaseModule {
    protected final Context context;
    protected final FirebaseConfig server;

    public BaseModule(Context context, FirebaseConfig server) {
        this.context = context;
        this.server = server;
    }

    protected String getHeader(String uri) {
        return server.getHeaderProxy(uri);
    }

    protected String getFooter() {
        return server.getFooter();
    }

    protected Response newResponse(Response.Status status, String mimeType, String data) {
        return NanoHTTPD.newFixedLengthResponse(status, mimeType, data);
    }

    protected String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
