package com.labs.labrats.router;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Tactical Command Pattern interface for HTTP Request Routing.
 */
@FunctionalInterface
public interface RouteHandler {
    Response handle(IHTTPSession session);
}
