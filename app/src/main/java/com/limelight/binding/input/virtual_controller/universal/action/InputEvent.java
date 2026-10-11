package com.limelight.binding.input.virtual_controller.universal.action;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * A single input that an on-screen element sends to the host: one key of one device.
 *
 * A device is identified by a small kind value instead of a class, so an event can be
 * stored in a layout file and rebuilt on any device.
 */
public class InputEvent {
    public static final int KIND_KEYBOARD = 0;
    public static final int KIND_MOUSE = 1;
    public static final int KIND_GAMEPAD = 2;

    private static final String KEY_KIND = "kind";
    private static final String KEY_CODE = "code";

    private final int kind;

    /**
     * For the keyboard this is a GFE keycode, for the mouse a MouseButtonPacket button,
     * and for the gamepad a GamepadButtons code.
     */
    private final int code;

    public InputEvent(int kind, int code) {
        this.kind = kind;
        this.code = code;
    }

    public int getKind() {
        return kind;
    }

    public int getCode() {
        return code;
    }

    public boolean isKeyboard() {
        return kind == KIND_KEYBOARD;
    }

    public boolean isMouse() {
        return kind == KIND_MOUSE;
    }

    public boolean isGamepad() {
        return kind == KIND_GAMEPAD;
    }

    /**
     * Returns the gamepad packet flag of this event, or 0 when it is not a gamepad event.
     */
    public int getGamepadFlag() {
        return isGamepad() ? GamepadButtons.flagForCode(code) : 0;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(KEY_KIND, kind);
        json.put(KEY_CODE, code);
        return json;
    }

    public static InputEvent fromJson(JSONObject json) throws JSONException {
        return new InputEvent(json.getInt(KEY_KIND), json.getInt(KEY_CODE));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InputEvent)) {
            return false;
        }

        InputEvent event = (InputEvent) other;
        return kind == event.kind && code == event.code;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, code);
    }

    @Override
    public String toString() {
        return InputEventNames.describe(this);
    }
}
