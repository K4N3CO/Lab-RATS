package com.labs.labrats;

import android.content.Context;

import com.labs.labrats.router.PasswordHasher;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class SecurityTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @Test
    public void testPasswordHasherVerificationAndMigration() {
        String defaultPass = com.labs.labrats.BuildConfig.C2_PASSWORD;
        if (defaultPass == null || defaultPass.trim().isEmpty()) {
            defaultPass = "admin1337";
        }
        // Initial state: Default configured password
        boolean validDefault = PasswordHasher.verifyPassword(context, defaultPass);
        Assert.assertTrue(validDefault);

        // After initial login, password is automatically migrated to PBKDF2 hash
        boolean validAfterMigration = PasswordHasher.verifyPassword(context, defaultPass);
        Assert.assertTrue(validAfterMigration);

        // Wrong password should fail
        boolean invalidPass = PasswordHasher.verifyPassword(context, "wrongpass123");
        Assert.assertFalse(invalidPass);
    }

    @Test
    public void testPasswordHasherSaveNewPassword() {
        PasswordHasher.savePassword(context, "NewTacticalKey2026");

        boolean validNew = PasswordHasher.verifyPassword(context, "NewTacticalKey2026");
        Assert.assertTrue(validNew);

        boolean invalidOld = PasswordHasher.verifyPassword(context, "OldPassword123");
        Assert.assertFalse(invalidOld);
    }

    @Test
    public void testSystemAnalyticsEncryptionDecryption() {
        String testData = "CONFIDENTIAL_TELEMETRY_LOG_ENTRY_1337";
        byte[] encrypted = SystemAnalytics.encrypt(testData);

        Assert.assertNotNull(encrypted);
        Assert.assertTrue(encrypted.length > 0);

        String decrypted = SystemAnalytics.decrypt(encrypted);
        Assert.assertEquals(testData, decrypted);
    }
}
