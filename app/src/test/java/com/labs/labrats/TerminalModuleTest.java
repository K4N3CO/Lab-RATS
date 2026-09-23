package com.labs.labrats;

import android.content.Context;

import com.labs.labrats.modules.TerminalModule;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class TerminalModuleTest {

    private TerminalModule terminalModule;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        FirebaseConfig server = new FirebaseConfig(context, 8080);
        terminalModule = new TerminalModule(context, server);
    }

    @Test
    public void testClearCommand() {
        Map<String, String> params = new HashMap<>();
        params.put("cmd", "clear");
        Response response = handleShellHelper(params);
        Assert.assertNotNull(response);
        Assert.assertEquals(Response.Status.OK, response.getStatus());
    }

    @Test
    public void testHelpCommand() {
        Map<String, String> params = new HashMap<>();
        params.put("cmd", "help");
        Response response = handleShellHelper(params);
        Assert.assertNotNull(response);
        Assert.assertEquals(Response.Status.OK, response.getStatus());
    }

    @Test
    public void testIpAddrCommand() {
        Map<String, String> params = new HashMap<>();
        params.put("cmd", "ip addr");
        Response response = handleShellHelper(params);
        Assert.assertNotNull(response);
        Assert.assertEquals(Response.Status.OK, response.getStatus());
    }

    @Test
    public void testPsCommand() {
        Map<String, String> params = new HashMap<>();
        params.put("cmd", "ps");
        Response response = handleShellHelper(params);
        Assert.assertNotNull(response);
        Assert.assertEquals(Response.Status.OK, response.getStatus());
    }

    @Test
    public void testFreeCommand() {
        Map<String, String> params = new HashMap<>();
        params.put("cmd", "free");
        Response response = handleShellHelper(params);
        Assert.assertNotNull(response);
        Assert.assertEquals(Response.Status.OK, response.getStatus());
    }

    private Response handleShellHelper(Map<String, String> params) {
        Map<String, String> sessionParms = new HashMap<>(params);
        IHTTPSession session = new MockIHTTPSession("/device/shell", sessionParms);
        return terminalModule.handleRequest(session);
    }

    private static class MockIHTTPSession implements IHTTPSession {
        private final String uri;
        private final Map<String, String> parms;

        MockIHTTPSession(String uri, Map<String, String> parms) {
            this.uri = uri;
            this.parms = parms;
        }

        @Override public String getUri() { return uri; }
        @Override public Map<String, String> getParms() { return parms; }
        @Override public Map<String, List<String>> getParameters() {
            Map<String, List<String>> res = new HashMap<>();
            for (Map.Entry<String, String> entry : parms.entrySet()) {
                res.put(entry.getKey(), Collections.singletonList(entry.getValue()));
            }
            return res;
        }
        @Override public void execute() {}
        @Override public fi.iki.elonen.NanoHTTPD.CookieHandler getCookies() { return null; }
        @Override public Map<String, String> getHeaders() { return new HashMap<>(); }
        @Override public java.io.InputStream getInputStream() { return null; }
        @Override public fi.iki.elonen.NanoHTTPD.Method getMethod() { return fi.iki.elonen.NanoHTTPD.Method.GET; }
        @Override public String getQueryParameterString() { return ""; }
        @Override public void parseBody(Map<String, String> files) {}
        @Override public String getRemoteIpAddress() { return "127.0.0.1"; }
        @Override public String getRemoteHostName() { return "localhost"; }
    }
}
