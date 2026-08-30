package com.labs.labrats;

/**
 * Global interface for screenshot results to avoid inner-class verification issues on legacy APIs.
 */
public interface ScreenshotCallback {
    void onSuccess(byte[] jpegData);
    void onFailure(String error);
}
