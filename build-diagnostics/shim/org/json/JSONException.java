package org.json;

/**
 * Minimal stand-in for org.json, used only by the off-device harness in
 * build-diagnostics. See JSONObject for why it exists.
 */
public class JSONException extends Exception {
    public JSONException(String message) {
        super(message);
    }
}
