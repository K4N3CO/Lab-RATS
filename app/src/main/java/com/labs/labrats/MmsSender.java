package com.labs.labrats;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MmsSender {
    private static final String TAG = "MmsSender";

    public static boolean send(Context context, String number, String text, File sourceImage) {
        try {
            if (sourceImage.length() > 1024 * 1024) {
                Log.e(TAG, "MMS Error: File too large (> 1MB)");
                return false;
            }

            String mimeType = getMimeType(sourceImage.getAbsolutePath());
            byte[] pdu = composePdu(number, text, sourceImage, mimeType);
            
            File pduFile = new File(context.getCacheDir(), "mms.pdu");
            try (FileOutputStream fos = new FileOutputStream(pduFile)) {
                fos.write(pdu);
            }

            Uri contentUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", pduFile);
            
            // Grant permissions to common telephony packages
            String[] telephonyPkgs = {"com.android.phone", "com.android.providers.telephony", "com.google.android.apps.messaging"};
            for (String pkg : telephonyPkgs) {
                try {
                    context.grantUriPermission(pkg, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
            }

            SmsManager smsManager;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            PendingIntent sentIntent = PendingIntent.getBroadcast(context, 0, new Intent("MMS_SENT"), (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));
            smsManager.sendMultimediaMessage(context, contentUri, null, null, sentIntent);
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "MMS Error: " + e.getMessage());
            return false;
        }
    }

    private static byte[] composePdu(String number, String text, File imageFile, String mimeType) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // m-send-req
        baos.write(0x8C); baos.write(0x80);
        // transaction id
        baos.write(0x98); baos.write((System.currentTimeMillis() + "T").getBytes()); baos.write(0x00);
        // version 1.2
        baos.write(0x8D); baos.write(0x92);
        // from
        baos.write(0x89); baos.write(0x81);
        // to
        baos.write(0x97); baos.write(number.getBytes()); baos.write(0x00);
        // content type: multipart/related
        baos.write(0x84); baos.write(0xA3);

        // parts
        byte[] imgData = readFile(imageFile);
        int partCount = (text != null && !text.isEmpty()) ? 2 : 1;
        
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(partCount);
        
        writePart(body, imgData, mimeType, "<image>");
        if (text != null && !text.isEmpty()) {
            writePart(body, text.getBytes(), "text/plain", "<text>");
        }
        
        baos.write(body.toByteArray());
        return baos.toByteArray();
    }

    private static void writePart(ByteArrayOutputStream out, byte[] data, String mime, String contentId) throws IOException {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.write(0x03); header.write(mime.getBytes()); header.write(0x00);
        header.write(0xC0); header.write(contentId.getBytes()); header.write(0x00);

        writeUintvar(out, header.size());
        writeUintvar(out, data.length);
        out.write(header.toByteArray());
        out.write(data);
    }

    private static void writeUintvar(OutputStream out, int value) throws IOException {
        int temp = value;
        byte[] buf = new byte[5];
        int ptr = 0;
        do {
            buf[ptr++] = (byte) (temp & 0x7F);
            temp >>>= 7;
        } while (temp != 0);
        while (ptr > 1) {
            out.write(buf[--ptr] | 0x80);
        }
        out.write(buf[0]);
    }

    private static byte[] readFile(File file) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) != -1) bos.write(buf, 0, len);
        }
        return bos.toByteArray();
    }

    private static String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        }
        return (type == null) ? "image/jpeg" : type;
    }
}
