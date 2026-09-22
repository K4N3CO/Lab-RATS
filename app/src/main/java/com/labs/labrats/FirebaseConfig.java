public static void sendTelegramMessage(String message) {
    final String botToken = "8559444905:AAEKh7GOBABw5IiwfKDMCgpNfeif5kZRD3o";
    final String chatId = "8914347152";
    
    if (botToken.equals("YOUR_BOT_TOKEN") || chatId.equals("YOUR_CHAT_ID")) {
        return; // لم يتم ضبط البوت بعد
    }

    new Thread(() -> {
        try {
            String urlString = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            java.net.URL url = new java.net.URL(urlString);
            javax.net.ssl.HttpsURLConnection conn = (javax.net.ssl.HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            String jsonPayload = "{\"chat_id\":\"" + chatId + "\", \"text\":\"" + message.replace("\"", "\\\"") + "\"}";

            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {
            Log.e("TelegramSync", "Failed to send notification: " + e.getMessage());
        }
    }).start();
}
