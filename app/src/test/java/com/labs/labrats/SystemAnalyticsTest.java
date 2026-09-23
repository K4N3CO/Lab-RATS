package com.labs.labrats;

import org.junit.Assert;
import org.junit.Test;

public class SystemAnalyticsTest {

    @Test
    public void testEncryptAndDecryptRoundTrip() {
        String originalText = "LAB-RATS_TEST_PAYLOAD_1337";
        byte[] encrypted = SystemAnalytics.encrypt(originalText);
        
        Assert.assertNotNull("Encrypted byte array should not be null", encrypted);
        Assert.assertTrue("Encrypted byte array length should be greater than 16 (IV size)", encrypted.length > 16);

        String decryptedText = SystemAnalytics.decrypt(encrypted);
        Assert.assertEquals("Decrypted text should match original input", originalText, decryptedText);
    }

    @Test
    public void testDecryptInvalidInput() {
        String resultNull = SystemAnalytics.decrypt(null);
        Assert.assertEquals("Decrypting null should return empty string", "", resultNull);

        byte[] shortBytes = new byte[10];
        String resultShort = SystemAnalytics.decrypt(shortBytes);
        Assert.assertEquals("Decrypting bytes shorter than IV length should return empty string", "", resultShort);
    }

    @Test
    public void testEncryptNullInput() {
        byte[] result = SystemAnalytics.encrypt(null);
        Assert.assertNotNull("Encrypting null should return non-null empty array", result);
        Assert.assertEquals("Encrypting null should return 0-length array", 0, result.length);
    }

    @Test
    public void testIsRootedDoesNotThrow() {
        boolean rooted = SystemAnalytics.isRooted();
        // Simply assert that invocation does not throw an exception on test environment
        Assert.assertTrue(rooted || !rooted);
    }
}
