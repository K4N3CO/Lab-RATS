package com.labs.labrats;

import org.junit.Assert;
import org.junit.Test;

public class FirebaseConfigTest {

    @Test
    public void testLogActivityPriorityClassification() {
        String criticalMsg = "CRITICAL_CORE_FAILURE: Exception in thread";
        String priorityC = determinePriority(criticalMsg);
        Assert.assertEquals("[C]", priorityC);

        String intelMsg = "INTEL_EXTRACTED: Wi-Fi Networks Scanned";
        String priorityI = determinePriority(intelMsg);
        Assert.assertEquals("[I]", priorityI);

        String warningMsg = "BATTERY level low";
        String priorityW = determinePriority(warningMsg);
        Assert.assertEquals("[W]", priorityW);

        String successMsg = "SYNC_INIT: Uplink connected successfully";
        String priorityS = determinePriority(successMsg);
        Assert.assertEquals("[S]", priorityS);
    }

    private String determinePriority(String msg) {
        String upper = msg.toUpperCase();
        if (upper.contains("INTEL_EXTRACTED") || upper.contains("SNIFFED") || upper.contains("CREDENTIALS") || upper.contains("SNIFFER")) {
            return "[I]";
        } else if (upper.contains("CRITICAL") || upper.contains("AUTH") || upper.contains("OTP") || upper.contains("ALERT") || upper.contains("SECURITY")) {
            return "[C]";
        } else if (upper.contains("WARNING") || upper.contains("BATTERY") || upper.contains("LOST") || upper.contains("ERROR")) {
            return "[W]";
        } else {
            return "[S]";
        }
    }
}
