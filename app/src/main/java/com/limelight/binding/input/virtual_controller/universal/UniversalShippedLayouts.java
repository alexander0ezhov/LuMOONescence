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
    /**
     * Which version of the shipped layouts is in place. Bumping it replaces the copies of the
     * layouts this build brings, which is how they are improved without the user having to make
     * them again.
     */
    private static final int VERSION = 6;

    private static final String VERSION_PREF_STRING = "shipped_extra_layouts_version";

    private static final String KEYBOARD_NAME = "Keyboard";
    private static final String SHOOTER_NAME = "Shooter";

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

        if (preferences.getInt(VERSION_PREF_STRING, 0) >= VERSION) {
            return;
        }

        int width = Math.max(context.getResources().getDisplayMetrics().widthPixels,
                context.getResources().getDisplayMetrics().heightPixels);
        int height = Math.min(context.getResources().getDisplayMetrics().widthPixels,
                context.getResources().getDisplayMetrics().heightPixels);

        UniversalLayoutStore store = UniversalLayoutStore.getInstance(context);

        // Adding a layout makes it the selected one, and the layouts this build brings are not
        // what the user chose, so the choice is put back afterwards. A layout that is replaced
        // keeps its place in the selection as well.
        String selected = store.getSelectedLayoutId();
        String selectedShippedName = null;

        for (UniversalLayoutDefinition layout : new ArrayList<>(store.getLayouts())) {
            if (KEYBOARD_NAME.equals(layout.getName()) || SHOOTER_NAME.equals(layout.getName())) {
                if (layout.getId().equals(selected)) {
                    selectedShippedName = layout.getName();
                }

                store.deleteLayout(layout.getId());
            }
        }

        addLayout(context, store, KEYBOARD_NAME, keyboardElements(width, height), width, height);
        addLayout(context, store, SHOOTER_NAME, shooterElements(width, height), width, height);

        if (selectedShippedName != null) {
            for (UniversalLayoutDefinition layout : store.getLayouts()) {
                if (layout.getName().equals(selectedShippedName)) {
                    store.setSelectedLayoutId(layout.getId());
                    break;
                }
            }
        }
        else if (selected != null) {
            store.setSelectedLayoutId(selected);
        }

        preferences.edit().putInt(VERSION_PREF_STRING, VERSION).apply();
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
    private static List<UniversalElementConfig> keyboardElements(int width, int height) {
        List<UniversalElementConfig> elements = new ArrayList<>();

        // Everything is placed in hundredths of the shorter side, so the same numbers work on
        // any screen. The right hand column is measured from the right edge instead, which is
        // what keeps it against the edge whatever the shape of the screen is.
        int right = width * UNITS / height;

        int key = 8;
        int gap = 1;

        // Function row: Esc, then F1 to F12
        int x = row(elements, height, 31, 2, 9, new Object[][]{
                {"Esc", 10, KeyEvent.KEYCODE_ESCAPE},
        });

        for (int i = 1; i <= 12; i++) {
            x = row(elements, height, 31, x, 9, new Object[][]{
                    {"F" + i, key, KeyEvent.KEYCODE_F1 + (i - 1)},
            });
        }

        // The grave key, which sits in the corner before the digits
        x = row(elements, height, 42, 2, 10, new Object[][]{
                {"`", key, KeyEvent.KEYCODE_GRAVE},
        });

        // Digits, with the keys a keyboard has beside them
        String digits = "1234567890";
        for (int i = 0; i < digits.length(); i++) {
            x = row(elements, height, 42, x, 10, new Object[][]{
                    {digits.charAt(i) + "", key, KeyEvent.KEYCODE_1 + i},
            });
        }

        x = row(elements, height, 42, x, 10, new Object[][]{
                {"-", key, KeyEvent.KEYCODE_MINUS},
                {"=", key, KeyEvent.KEYCODE_EQUALS},
                {"Del", 16, KeyEvent.KEYCODE_DEL},
        });

        // Letters, in the rows a keyboard has them
        x = row(elements, height, 53, 2, 10, new Object[][]{
                {"Tab", 12, KeyEvent.KEYCODE_TAB},
        });

        String top = "qwertyuiop";
        for (int i = 0; i < top.length(); i++) {
            x = row(elements, height, 53, x, 10, new Object[][]{
                    {top.charAt(i) + "", key, KeyEvent.KEYCODE_A + (top.charAt(i) - 'a')},
            });
        }

        row(elements, height, 53, x, 10, new Object[][]{
                {"[", key, KeyEvent.KEYCODE_LEFT_BRACKET},
                {"]", key, KeyEvent.KEYCODE_RIGHT_BRACKET},
                {"\\", key, KeyEvent.KEYCODE_BACKSLASH},
        });

        x = row(elements, height, 64, 2, 10, new Object[][]{
                {"Caps", 14, KeyEvent.KEYCODE_CAPS_LOCK},
        });

        String middle = "asdfghjkl";
        for (int i = 0; i < middle.length(); i++) {
            x = row(elements, height, 64, x, 10, new Object[][]{
                    {middle.charAt(i) + "", key, KeyEvent.KEYCODE_A + (middle.charAt(i) - 'a')},
            });
        }

        row(elements, height, 64, x, 10, new Object[][]{
                {";", key, KeyEvent.KEYCODE_SEMICOLON},
                {"'", key, KeyEvent.KEYCODE_APOSTROPHE},
                {"Enter", 18, KeyEvent.KEYCODE_ENTER},
        });

        x = row(elements, height, 75, 2, 10, new Object[][]{
                {"Shift", 18, KeyEvent.KEYCODE_SHIFT_LEFT},
        });

        String bottom = "zxcvbnm";
        for (int i = 0; i < bottom.length(); i++) {
            x = row(elements, height, 75, x, 10, new Object[][]{
                    {bottom.charAt(i) + "", key, KeyEvent.KEYCODE_A + (bottom.charAt(i) - 'a')},
            });
        }

        row(elements, height, 75, x, 10, new Object[][]{
                {",", key, KeyEvent.KEYCODE_COMMA},
                {".", key, KeyEvent.KEYCODE_PERIOD},
                {"/", key, KeyEvent.KEYCODE_SLASH},
                {"Shift", 22, KeyEvent.KEYCODE_SHIFT_RIGHT},
        });

        // The bottom row sits a small margin above the edge, the same as the arrows
        row(elements, height, 86, 2, 10, new Object[][]{
                {"Ctrl", 11, KeyEvent.KEYCODE_CTRL_LEFT},
                {"Win", 11, KeyEvent.KEYCODE_META_LEFT},
                {"Alt", 11, KeyEvent.KEYCODE_ALT_LEFT},
                {"Space", 50, KeyEvent.KEYCODE_SPACE},
                {"Alt", 11, KeyEvent.KEYCODE_ALT_RIGHT},
                {"Win", 11, KeyEvent.KEYCODE_META_LEFT},
                {"Ctrl", 12, KeyEvent.KEYCODE_CTRL_RIGHT},
        });

        // The right hand side is a column of square keys against the right edge: the mouse at the
        // top, the six keys in the middle, the arrows in the corner
        int square = 10;
        int spacing = square + 1;
        int rightColumn = right - 2 - square;
        int leftColumn = rightColumn - spacing;

        elements.add(mouseButton(height, "M4", leftColumn, 3, square, square,
                MouseButtonPacket.BUTTON_X1));
        elements.add(mouseButton(height, "M5", leftColumn, 3 + spacing, square, square,
                MouseButtonPacket.BUTTON_X2));
        elements.add(key(height, "Alt+F4", leftColumn, 3 + 2 * spacing, square, square,
                KeyEvent.KEYCODE_F4, KeyEvent.KEYCODE_ALT_LEFT));

        elements.add(mouseButton(height, "LMB", rightColumn, 3, square, square,
                MouseButtonPacket.BUTTON_LEFT));
        elements.add(mouseButton(height, "RMB", rightColumn, 3 + spacing, square, square,
                MouseButtonPacket.BUTTON_RIGHT));
        elements.add(mouseButton(height, "MMB", rightColumn, 3 + 2 * spacing, square, square,
                MouseButtonPacket.BUTTON_MIDDLE));

        // The six keys above the arrows
        int navTop = 52;
        int navStart = leftColumn - spacing;

        row(elements, height, navTop, navStart, square, new Object[][]{
                {"Ins", square, KeyEvent.KEYCODE_INSERT},
                {"Del", square, KeyEvent.KEYCODE_FORWARD_DEL},
                {"Home", square, KeyEvent.KEYCODE_MOVE_HOME},
        });

        row(elements, height, navTop + spacing, navStart, square, new Object[][]{
                {"End", square, KeyEvent.KEYCODE_MOVE_END},
                {"PgUp", square, KeyEvent.KEYCODE_PAGE_UP},
                {"PgDn", square, KeyEvent.KEYCODE_PAGE_DOWN},
        });

        // The arrows, in the bottom right corner
        row(elements, height, 74, leftColumn, square, new Object[][]{
                {"Up", square, KeyEvent.KEYCODE_DPAD_UP},
        });

        row(elements, height, 74 + spacing, leftColumn - spacing, square, new Object[][]{
                {"Left", square, KeyEvent.KEYCODE_DPAD_LEFT},
                {"Down", square, KeyEvent.KEYCODE_DPAD_DOWN},
                {"Right", square, KeyEvent.KEYCODE_DPAD_RIGHT},
        });

        return elements;
    }

    /**
     * Adds a row of keys, one after another, and returns where the next one would start.
     */
    private static int row(List<UniversalElementConfig> elements, int height,
                           int y, int x, int keyHeight, Object[][] keys) {
        for (Object[] entry : keys) {
            String label = (String) entry[0];
            int width = (Integer) entry[1];
            int keyCode = (Integer) entry[2];

            elements.add(key(height, label, x, y, width, keyHeight, keyCode));
            x += width + 1;
        }

        return x;
    }

    // ---------------------------------------------------------------- the shooter

    /**
     * The pad of a shooter: the left thumb walks, the right one shoots, and everything else is
     * the key the game expects in that place.
     */
    private static List<UniversalElementConfig> shooterElements(int width, int height) {
        // The right hand keys follow the right edge of the screen, so the arrangement keeps
        // its shape whatever the shape of the display is
        int rightShift = (width * UNITS / height - 2) - 215;
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
        elements.add(key(height, "F", 156 + rightShift, 52, 12, 12, KeyEvent.KEYCODE_F));
        elements.add(key(height, "E", 170 + rightShift, 52, 12, 12, KeyEvent.KEYCODE_E));
        elements.add(key(height, "C", 184 + rightShift, 52, 12, 12, KeyEvent.KEYCODE_C));
        elements.add(key(height, "Space", 168 + rightShift, 66, 28, 12, KeyEvent.KEYCODE_SPACE));

        // Mouse buttons where the triggers are
        elements.add(mouseButton(height, "LMB", 196 + rightShift, 4, 14, 10,
                MouseButtonPacket.BUTTON_LEFT));
        elements.add(mouseButton(height, "RMB", 5, 4, 14, 10, MouseButtonPacket.BUTTON_RIGHT));

        // Shoulder buttons
        elements.add(key(height, "Q", 5, 16, 14, 9, KeyEvent.KEYCODE_Q));
        elements.add(key(height, "R", 196 + rightShift, 16, 14, 9, KeyEvent.KEYCODE_R));

        // The sticks and the small keys around them
        elements.add(key(height, "Shift", 41, 79, 18, 9, KeyEvent.KEYCODE_SHIFT_LEFT));
        elements.add(key(height, "G", 61, 79, 10, 9, KeyEvent.KEYCODE_G));
        elements.add(key(height, "Ctrl", 150 + rightShift, 79, 18, 9, KeyEvent.KEYCODE_CTRL_LEFT));
        elements.add(key(height, "Tab", 170 + rightShift, 79, 12, 9, KeyEvent.KEYCODE_TAB));

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
