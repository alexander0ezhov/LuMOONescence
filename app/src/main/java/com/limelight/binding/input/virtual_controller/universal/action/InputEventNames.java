package com.limelight.binding.input.virtual_controller.universal.action;

import android.view.KeyEvent;

import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.input.MouseButtonPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Human readable names for the inputs an on-screen element can send, and the catalogs
 * the editor offers.
 */
public final class InputEventNames {
    /**
     * Created on first use rather than when the class loads: building it enumerates the
     * keyboards of the device, which is work that a run that never names a key should not
     * pay for.
     */
    private static KeyboardTranslator translator;

    private static Map<Integer, Integer> gfeToAndroidKeyCode;
    private static Map<Integer, String> androidKeyCodeNames;
    private static List<InputEvent> keyboardCatalog;

    private InputEventNames() {
    }

    private static synchronized KeyboardTranslator getTranslator() {
        if (translator == null) {
            translator = new KeyboardTranslator();
        }

        return translator;
    }

    /**
     * Builds the reverse of what KeyboardTranslator does, so a stored GFE keycode can be
     * shown under the name of the key the user actually pressed.
     */
    private static synchronized Map<Integer, Integer> getGfeToAndroidKeyCode() {
        if (gfeToAndroidKeyCode != null) {
            return gfeToAndroidKeyCode;
        }

        Map<Integer, Integer> mapping = new HashMap<>();
        Map<Integer, String> names = new HashMap<>();

        KeyboardTranslator keyboardTranslator = getTranslator();

        int maxKeyCode = KeyEvent.getMaxKeyCode();
        for (int keyCode = 0; keyCode <= maxKeyCode; keyCode++) {
            short translated = keyboardTranslator.translate(keyCode, -1);
            if (translated == 0) {
                continue;
            }

            int gfe = translated & 0xFFFF;

            // The first Android keycode that reaches a GFE code wins, which keeps the
            // plain key ahead of its "left"/"right" variant.
            if (!mapping.containsKey(gfe)) {
                mapping.put(gfe, keyCode);
                names.put(gfe, KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", ""));
            }
        }

        androidKeyCodeNames = names;
        gfeToAndroidKeyCode = mapping;
        return gfeToAndroidKeyCode;
    }

    public static String keyboardKeyName(int gfeKeyCode) {
        getGfeToAndroidKeyCode();

        String name = androidKeyCodeNames.get(gfeKeyCode);
        return name != null ? name : ("Key 0x" + Integer.toHexString(gfeKeyCode).toUpperCase());
    }

    public static String mouseButtonName(int button) {
        switch (button) {
            case MouseButtonPacket.BUTTON_LEFT:
                return "Left button";

            case MouseButtonPacket.BUTTON_MIDDLE:
                return "Middle button";

            case MouseButtonPacket.BUTTON_RIGHT:
                return "Right button";

            case MouseButtonPacket.BUTTON_X1:
                return "Back button";

            case MouseButtonPacket.BUTTON_X2:
                return "Forward button";

            default:
                return "Mouse " + button;
        }
    }

    public static String describe(InputEvent event) {
        if (event.isKeyboard()) {
            return keyboardKeyName(event.getCode());
        }

        if (event.isMouse()) {
            return "Mouse: " + mouseButtonName(event.getCode());
        }

        return "Pad: " + GamepadButtons.nameForCode(event.getCode());
    }

    /**
     * The mouse buttons offered by the editor, in the order the host numbers them.
     */
    public static List<InputEvent> mouseCatalog() {
        List<InputEvent> events = new ArrayList<>();
        events.add(new InputEvent(InputEvent.KIND_MOUSE, MouseButtonPacket.BUTTON_LEFT));
        events.add(new InputEvent(InputEvent.KIND_MOUSE, MouseButtonPacket.BUTTON_RIGHT));
        events.add(new InputEvent(InputEvent.KIND_MOUSE, MouseButtonPacket.BUTTON_MIDDLE));
        events.add(new InputEvent(InputEvent.KIND_MOUSE, MouseButtonPacket.BUTTON_X1));
        events.add(new InputEvent(InputEvent.KIND_MOUSE, MouseButtonPacket.BUTTON_X2));
        return events;
    }

    /**
     * The keyboard keys offered by the editor. The list is built from the keys that
     * actually have a host mapping, so the editor can never offer a key it cannot send.
     */
    public static synchronized List<InputEvent> keyboardCatalog() {
        if (keyboardCatalog != null) {
            return keyboardCatalog;
        }

        int[] androidKeyCodes = new int[]{
                KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_D,
                KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
                KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L,
                KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
                KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_T,
                KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_X,
                KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_Z,
                KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
                KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7,
                KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9,
                KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4,
                KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8,
                KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_F10, KeyEvent.KEYCODE_F11, KeyEvent.KEYCODE_F12,
                KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB,
                KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL,
                KeyEvent.KEYCODE_INSERT, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END,
                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
                KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
                KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
                KeyEvent.KEYCODE_META_LEFT,
                KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS,
                KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET,
                KeyEvent.KEYCODE_BACKSLASH, KeyEvent.KEYCODE_SEMICOLON,
                KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_COMMA,
                KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_GRAVE,
                KeyEvent.KEYCODE_CAPS_LOCK, KeyEvent.KEYCODE_NUM_LOCK,
                KeyEvent.KEYCODE_SCROLL_LOCK, KeyEvent.KEYCODE_SYSRQ,
                KeyEvent.KEYCODE_NUMPAD_0, KeyEvent.KEYCODE_NUMPAD_1, KeyEvent.KEYCODE_NUMPAD_2,
                KeyEvent.KEYCODE_NUMPAD_3, KeyEvent.KEYCODE_NUMPAD_4, KeyEvent.KEYCODE_NUMPAD_5,
                KeyEvent.KEYCODE_NUMPAD_6, KeyEvent.KEYCODE_NUMPAD_7, KeyEvent.KEYCODE_NUMPAD_8,
                KeyEvent.KEYCODE_NUMPAD_9, KeyEvent.KEYCODE_NUMPAD_DIVIDE,
                KeyEvent.KEYCODE_NUMPAD_MULTIPLY, KeyEvent.KEYCODE_NUMPAD_SUBTRACT,
                KeyEvent.KEYCODE_NUMPAD_ADD, KeyEvent.KEYCODE_NUMPAD_DOT,
        };

        List<InputEvent> events = new ArrayList<>();
        KeyboardTranslator keyboardTranslator = getTranslator();

        for (int androidKeyCode : androidKeyCodes) {
            short translated = keyboardTranslator.translate(androidKeyCode, -1);
            if (translated != 0) {
                events.add(new InputEvent(InputEvent.KIND_KEYBOARD, translated & 0xFFFF));
            }
        }

        keyboardCatalog = events;
        return keyboardCatalog;
    }
}
