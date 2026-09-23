package com.labs.labrats.router;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Tactical Centralized HTTP Router.
 * Handles exact path and prefix path dispatching to modular controllers.
 */
public class Router {

    private final Map<String, RouteHandler> exactRoutes = new ConcurrentHashMap<>();
    private final Map<String, RouteHandler> prefixRoutes = new ConcurrentHashMap<>();

    public void registerExact(String path, RouteHandler handler) {
        if (path != null && handler != null) {
            exactRoutes.put(path, handler);
        }
    }

    public void registerPrefix(String prefix, RouteHandler handler) {
        if (prefix != null && handler != null) {
            prefixRoutes.put(prefix, handler);
        }
    }

    public Response dispatch(IHTTPSession session) {
        if (session == null) return null;

        String uri = session.getUri();
        if (uri == null) uri = "/";

        // 1. Check exact route match
        RouteHandler exact = exactRoutes.get(uri);
        if (exact != null) {
            return exact.handle(session);
        }

        // 2. Check prefix route match (sorted by longest prefix for accuracy)
        RouteHandler bestMatch = null;
        int maxLen = 0;
        for (Map.Entry<String, RouteHandler> entry : prefixRoutes.entrySet()) {
            String prefix = entry.getKey();
            if (uri.startsWith(prefix) && prefix.length() > maxLen) {
                bestMatch = entry.getValue();
                maxLen = prefix.length();
            }
        }

        if (bestMatch != null) {
            return bestMatch.handle(session);
        }

        return null; // Route not handled by this router
    }
}
