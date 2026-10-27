package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.KeyEvent;

import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.input.virtual_controller.universal.action.ActionSequence;
import com.limelight.binding.input.virtual_controller.universal.action.InputEvent;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalButtonConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalElementConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalGeometry;
import com.limelight.nvstream.input.MouseButtonPacket;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Two ready made layouts that a stream of a desktop game wants: a keyboard with the mouse buttons
 * where the right thumb is, and a shooter layout that turns the pad into the keys such a game
 * expects.
 *
 * They are put in the list of layouts once, on the first run of a build that has them. They are
 * ordinary layouts after that: they can be changed, renamed or deleted like any other, and a
 * deletion is not undone on the next start.
 */
public final class UniversalShippedLayouts {
    private static final String SEEDED_PREF_STRING = "shipped_extra_layouts_seeded";

    /**
     * The screens the layouts are arranged for. The positions below are given in hundredths of
     * the shorter side, which is how the built-in arrangement works as well, so a layout made on
     * one screen lands in the same places on another and is fitted when it does not.
     */
    private static final int UNITS = 100;

    private UniversalShippedLayouts() {
    }

    /**
     * Adds the layouts the first time this build runs.
     */
    public static void seedIfNeeded(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (preferences.getBoolean(SEEDED_PREF_STRING, false)) {
            return;
        }

        int width = Math.max(context.getResources().getDisplayMetrics().widthPixels,
                context.getResources().getDisplayMetrics().heightPixels);
        int height = Math.min(context.getResources().getDisplayMetrics().widthPixels,
                context.getResources().getDisplayMetrics().heightPixels);

        UniversalLayoutStore store = UniversalLayoutStore.getInstance(context);

        // Adding a layout makes it the selected one, and the layouts this build brings are not
        // what the user chose, so the choice is put back afterwards
        String selected = store.getSelectedLayoutId();

        addLayout(context, store, "Keyboard", keyboardElements(height), width, height);
        addLayout(context, store, "Shooter", shooterElements(height), width, height);

        if (selected != null) {
            store.setSelectedLayoutId(selected);
        }

        preferences.edit().putBoolean(SEEDED_PREF_STRING, true).apply();
    }

    private static void addLayout(Context context, UniversalLayoutStore store, String name,
                                  List<UniversalElementConfig> elements,
                                  int screenWidth, int screenHeight) {
        try {
            UniversalLayoutDefinition layout = store.addLayout(name);

            // The screen is recorded in the same orientation the arrangement was made for,
            // otherwise the first open squeezes the whole layout to fit
            UniversalLayoutConfigurationLoader.writeStoredElements(context, layout.getId(),
                    toElementsJson(elements), screenWidth, screenHeight);
        } catch (JSONException e) {
            // A layout that cannot be written is simply not offered, which is better than a list
            // of layouts that fail to open
        }
    }

    /**
     * The elements of the layouts as one array, which is the form the layouts are stored in.
     */
    private static String toElementsJson(List<UniversalElementConfig> elements)
            throws JSONException {
        JSONArray array = new JSONArray();
        int id = 2000;

        for (UniversalElementConfig config : elements) {
            org.json.JSONObject json = config.toJson();

            json.put("type", typeNameOf(config));
            json.put("elementId", id++);

            array.put(json);
        }

        return array.toString();
    }

    private static String typeNameOf(UniversalElementConfig config) {
        if (config instanceof com.limelight.binding.input.virtual_controller.universal.config
                .UniversalStickConfig) {
            return UniversalElementTypes.ELEMENT_STICK;
        }

        if (config instanceof com.limelight.binding.input.virtual_controller.universal.config
                .UniversalPadConfig) {
            return UniversalElementTypes.ELEMENT_DPAD;
        }

        return UniversalElementTypes.ELEMENT_BUTTON;
    }

    // ---------------------------------------------------------------- the keyboard

    /**
     * A full on-screen keyboard, with the mouse buttons on the right where the thumb rests.
     */
    private static List<UniversalElementConfig> keyboardElements(int height) {
        List<UniversalElementConfig> elements = new ArrayList<>();

        int key = 10;
        int gap = 1;

        // Digits
        String digits = "1234567890";
        for (int i = 0; i < digits.length(); i++) {
            elements.add(key(height, digits.charAt(i) + "",
                    3 + i * (key + gap), 40, key, key, KeyEvent.KEYCODE_1 + i));
        }

        // Letters, in three rows like a keyboard has them
        String top = "qwertyuiop";
        String middle = "asdfghjkl";
        String bottom = "zxcvbnm";

        for (int i = 0; i < top.length(); i++) {
            elements.add(key(height, top.charAt(i) + "",
                    3 + i * (key + gap), 51, key, key,
                    KeyEvent.KEYCODE_A + (top.charAt(i) - 'a')));
        }

        for (int i = 0; i < middle.length(); i++) {
            elements.add(key(height, middle.charAt(i) + "",
                    8 + i * (key + gap), 62, key, key,
                    KeyEvent.KEYCODE_A + (middle.charAt(i) - 'a')));
        }

        for (int i = 0; i < bottom.length(); i++) {
            elements.add(key(height, bottom.charAt(i) + "",
                    13 + i * (key + gap), 73, key, key,
                    KeyEvent.KEYCODE_A + (bottom.charAt(i) - 'a')));
        }

        // Backspace at the end of the letters, where a keyboard has it
        elements.add(key(height, "Del", 13 + bottom.length() * (key + gap), 73,
                18, key, KeyEvent.KEYCODE_DEL));

        // The row that does the work
        int y = 84;
        int x = 3;

        elements.add(key(height, "Esc", x, y, key, key, KeyEvent.KEYCODE_ESCAPE));
        x += key + gap;

        elements.add(key(height, "Tab", x, y, key, key, KeyEvent.KEYCODE_TAB));
        x += key + gap;

        elements.add(key(height, "Shift", x, y, 14, key, KeyEvent.KEYCODE_SHIFT_LEFT));
        x += 14 + gap;

        elements.add(key(height, "Space", x, y, 40, key, KeyEvent.KEYCODE_SPACE));
        x += 40 + gap;

        elements.add(key(height, "Enter", x, y, 14, key, KeyEvent.KEYCODE_ENTER));
        x += 14 + gap;

        elements.add(key(height, "Ctrl", x, y, 12, key, KeyEvent.KEYCODE_CTRL_LEFT));

        // The mouse, under the right thumb
        elements.add(mouseButton(height, "LMB", 150, 40, 30, 14, MouseButtonPacket.BUTTON_LEFT));
        elements.add(mouseButton(height, "RMB", 150, 56, 30, 14, MouseButtonPacket.BUTTON_RIGHT));
        elements.add(mouseButton(height, "MMB", 150, 72, 30, 12, MouseButtonPacket.BUTTON_MIDDLE));

        // The side buttons, which games bind to whatever the thumb reaches for
        elements.add(mouseButton(height, "M4", 185, 40, 24, 12, MouseButtonPacket.BUTTON_X1));
        elements.add(mouseButton(height, "M5", 185, 54, 24, 12, MouseButtonPacket.BUTTON_X2));

        // A way out of the game without reaching for the keyboard out of the case
        elements.add(key(height, "Alt+F4", 185, 68, 26, 10, KeyEvent.KEYCODE_F4,
                KeyEvent.KEYCODE_ALT_LEFT));

        return elements;
    }

    // ---------------------------------------------------------------- the shooter

    /**
     * The pad of a shooter: the left thumb walks, the right one shoots, and everything else is
     * the key the game expects in that place.
     */
    private static List<UniversalElementConfig> shooterElements(int height) {
        List<UniversalElementConfig> elements = new ArrayList<>();

        // Walking, where the left stick would be: one key for each way
        elements.add(key(height, "W", 16, 67, 10, 10, KeyEvent.KEYCODE_W));
        elements.add(key(height, "A", 5, 78, 10, 10, KeyEvent.KEYCODE_A));
        elements.add(key(height, "S", 16, 78, 10, 10, KeyEvent.KEYCODE_S));
        elements.add(key(height, "D", 27, 78, 10, 10, KeyEvent.KEYCODE_D));

        // The number row as the D-pad, for weapons
        elements.add(key(height, "1", 16, 30, 10, 10, KeyEvent.KEYCODE_1));
        elements.add(key(height, "2", 5, 41, 10, 10, KeyEvent.KEYCODE_2));
        elements.add(key(height, "3", 27, 41, 10, 10, KeyEvent.KEYCODE_3));
        elements.add(key(height, "4", 16, 52, 10, 10, KeyEvent.KEYCODE_4));

        // Looking and shooting, under the right thumb
        elements.add(key(height, "F", 156, 52, 12, 12, KeyEvent.KEYCODE_F));
        elements.add(key(height, "E", 170, 52, 12, 12, KeyEvent.KEYCODE_E));
        elements.add(key(height, "C", 184, 52, 12, 12, KeyEvent.KEYCODE_C));
        elements.add(key(height, "Space", 168, 66, 28, 12, KeyEvent.KEYCODE_SPACE));

        // Mouse buttons where the triggers are
        elements.add(mouseButton(height, "LMB", 196, 4, 14, 10, MouseButtonPacket.BUTTON_LEFT));
        elements.add(mouseButton(height, "RMB", 5, 4, 14, 10, MouseButtonPacket.BUTTON_RIGHT));

        // Shoulder buttons
        elements.add(key(height, "Q", 5, 16, 14, 9, KeyEvent.KEYCODE_Q));
        elements.add(key(height, "R", 196, 16, 14, 9, KeyEvent.KEYCODE_R));

        // The sticks and the small keys around them
        elements.add(key(height, "Shift", 41, 79, 18, 9, KeyEvent.KEYCODE_SHIFT_LEFT));
        elements.add(key(height, "G", 61, 79, 10, 9, KeyEvent.KEYCODE_G));
        elements.add(key(height, "Ctrl", 150, 79, 18, 9, KeyEvent.KEYCODE_CTRL_LEFT));
        elements.add(key(height, "Tab", 170, 79, 12, 9, KeyEvent.KEYCODE_TAB));

        // Back and Start become Esc and Enter, in the same place
        elements.add(key(height, "Esc", 92, 4, 12, 8, KeyEvent.KEYCODE_ESCAPE));
        elements.add(key(height, "Enter", 106, 4, 16, 8, KeyEvent.KEYCODE_ENTER));

        return elements;
    }

    // ---------------------------------------------------------------- the pieces

    /**
     * A control that sends one key of the keyboard.
     */
    private static UniversalButtonConfig key(int height, String name,
                                             int unitX, int unitY, int unitWidth, int unitHeight,
                                             int keyCode) {
        UniversalButtonConfig config = button(height, name, unitX, unitY, unitWidth, unitHeight);

        config.setActionSequence(ActionSequence.single(new InputEvent(
                InputEvent.KIND_KEYBOARD, gfeKeyCode(keyCode))));

        return config;
    }

    /**
     * A control that sends a key together with a modifier, such as Alt+F4.
     */
    private static UniversalButtonConfig key(int height, String name,
                                             int unitX, int unitY, int unitWidth, int unitHeight,
                                             int keyCode, int modifierKeyCode) {
        UniversalButtonConfig config = button(height, name, unitX, unitY, unitWidth, unitHeight);

        config.setActionSequence(ActionSequence.combo(
                new InputEvent(InputEvent.KIND_KEYBOARD, gfeKeyCode(modifierKeyCode)),
                new InputEvent(InputEvent.KIND_KEYBOARD, gfeKeyCode(keyCode))));

        return config;
    }

    /**
     * A control that presses a button of the mouse.
     */
    private static UniversalButtonConfig mouseButton(int height, String name,
                                                     int unitX, int unitY,
                                                     int unitWidth, int unitHeight,
                                                     int mouseButton) {
        UniversalButtonConfig config = button(height, name, unitX, unitY, unitWidth, unitHeight);

        config.setActionSequence(ActionSequence.single(new InputEvent(
                InputEvent.KIND_MOUSE, mouseButton)));

        return config;
    }

    private static UniversalButtonConfig button(int height, String name,
                                                int unitX, int unitY,
                                                int unitWidth, int unitHeight) {
        UniversalButtonConfig config = new UniversalButtonConfig();

        config.setName(name);
        config.setGeometry(new UniversalGeometry(
                units(unitX, height), units(unitY, height),
                units(unitWidth, height), units(unitHeight, height)));

        return config;
    }

    private static int units(int value, int height) {
        return Math.max(1, value * height / UNITS);
    }

    /**
     * The code the host expects for a key, which is the same translation the editor uses when a
     * key is bound by hand.
     */
    private static int gfeKeyCode(int androidKeyCode) {
        short translated = new KeyboardTranslator().translate(androidKeyCode, -1);

        return translated & 0xFFFF;
    }
}
