/**
 * Universal layout: builds the built-in layout and persists the user's arrangement.
 *
 * A layout is described as data. Every element gets a configuration that says what it
 * sends and where it sits; the built-in layout creates the starting set, and the whole
 * set is then saved into the layout's own preferences file. Saving the whole set is what
 * lets a layout contain copies and elements the user added, and it means a layout saved
 * by a newer version stays intact even if this version's built-in layout differs.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;

import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.input.virtual_controller.universal.action.ActionSequence;
import com.limelight.binding.input.virtual_controller.universal.action.GamepadButtons;
import com.limelight.binding.input.virtual_controller.universal.action.InputEvent;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalButtonConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalElementConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalGeometry;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalHostAxis;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalPadConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalStickConfig;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class UniversalLayoutConfigurationLoader {
    /**
     * Prefix of the preferences file that holds one universal layout. The layout ID is
     * appended to it, so every layout keeps its own arrangement of controls.
     */
    private static final String LAYOUT_PREFERENCE_PREFIX = "OSC_UNIVERSAL_";

    /**
     * Key of the saved list of elements inside a layout's preferences file.
     */
    private static final String KEY_ELEMENTS = "elements";

    /**
     * Key of the ID counter, so element IDs stay unique across sessions.
     */
    private static final String KEY_NEXT_ELEMENT_ID = "nextElementId";

    /**
     * Keys of the size of the area the geometry was arranged in.
     *
     * A layout is a set of pixel positions, so the same numbers mean something different
     * on a screen of a different size. Storing the size they were made for is what lets a
     * layout be brought back into view instead of ending up off screen.
     */
    private static final String KEY_SAVED_WIDTH = "savedWidth";
    private static final String KEY_SAVED_HEIGHT = "savedHeight";

    /**
     * Set once a layout has been brought into view, so the geometry that was saved for
     * another screen size is only adjusted the first time it is opened.
     */
    private static final String KEY_RESCALED = "rescaledToFit";

    /**
     * Key of an element ID inside a saved element.
     */
    private static final String KEY_ELEMENT_ID = "elementId";

    // Used to translate the Android keycodes of on-screen keyboard keys into the GFE
    // keycodes the host expects. The virtual device that delivers OSC key events has no
    // key layout, so this only ever performs the fixed QWERTY mapping.
    //
    // Created on first use rather than when the class loads: building it enumerates the
    // keyboards of the device.
    private static KeyboardTranslator keyboardTranslator;

    private static synchronized KeyboardTranslator getKeyboardTranslator() {
        if (keyboardTranslator == null) {
            keyboardTranslator = new KeyboardTranslator();
        }

        return keyboardTranslator;
    }

    /**
     * Returns the name of the preferences file that holds the given layout.
     */
    public static String getLayoutPreferenceFileName(String layoutId) {
        return LAYOUT_PREFERENCE_PREFIX + layoutId;
    }

    private static SharedPreferences getLayoutPreferences(Context context, String layoutId) {
        return context.getSharedPreferences(getLayoutPreferenceFileName(layoutId),
                Activity.MODE_PRIVATE);
    }

    // The built-in controls are specified using a grid of 128*72 cells at 16:9
    private static int screenScale(int units, int height) {
        return (int) (((float) height / (float) 72) * (float) units);
    }

    /**
     * One control of the built-in layout. Everything the built-in layout needs to say
     * about an element is here, so a saved layout and the built-in one are built by the
     * same code path.
     */
    private static final class ElementDefinition {
        String type;
        int elementId;
        String name;
        int gridX;
        int gridY;
        int gridWidth;
        int gridHeight;

        /**
         * Gamepad button code this element sends, or GamepadButtons.CODE_NONE.
         */
        int gamepadButton = GamepadButtons.CODE_NONE;

        /**
         * Keyboard character this element sends, or 0.
         */
        char character;

        /**
         * Analog trigger this element drives, or -1.
         */
        int trigger = -1;

        int axisXCode = UniversalHostAxis.codeOf(UniversalHostAxis.KIND_NONE, 0);
        int axisYCode = UniversalHostAxis.codeOf(UniversalHostAxis.KIND_NONE, 0);

        ElementDefinition(String type, int elementId, String name,
                          int gridX, int gridY, int gridWidth, int gridHeight) {
            this.type = type;
            this.elementId = elementId;
            this.name = name;
            this.gridX = gridX;
            this.gridY = gridY;
            this.gridWidth = gridWidth;
            this.gridHeight = gridHeight;
        }

        ElementDefinition button(int gamepadButton) {
            this.gamepadButton = gamepadButton;
            return this;
        }

        ElementDefinition trigger(int trigger) {
            this.trigger = trigger;
            return this;
        }

        ElementDefinition key(char character) {
            this.character = character;
            return this;
        }

        ElementDefinition axes(int xKind, int xTarget, int yKind, int yTarget) {
            this.axisXCode = UniversalHostAxis.codeOf(xKind, xTarget);
            this.axisYCode = UniversalHostAxis.codeOf(yKind, yTarget);
            return this;
        }
    }

    private static final int TRIGGER_L_BASE_X = 1;
    private static final int TRIGGER_R_BASE_X = 92;
    private static final int TRIGGER_DISTANCE = 23;
    private static final int TRIGGER_BASE_Y = 31;
    private static final int TRIGGER_WIDTH = 12;
    private static final int TRIGGER_HEIGHT = 9;

    private static final int BUTTON_BASE_X = 106;
    private static final int BUTTON_BASE_Y = 1;
    private static final int BUTTON_SIZE = 10;

    private static final int DPAD_BASE_X = 4;
    private static final int DPAD_BASE_Y = 41;
    private static final int DPAD_SIZE = 30;

    private static final int ANALOG_L_BASE_X = 6;
    private static final int ANALOG_L_BASE_Y = 4;
    private static final int ANALOG_R_BASE_X = 98;
    private static final int ANALOG_R_BASE_Y = 42;
    private static final int ANALOG_SIZE = 26;

    private static final int L3_R3_BASE_Y = 60;

    private static final int START_X = 83;
    private static final int BACK_X = 34;
    private static final int START_BACK_Y = 64;
    private static final int START_BACK_WIDTH = 12;
    private static final int START_BACK_HEIGHT = 7;

    private static final int GUIDE_X = START_X - BACK_X;
    private static final int GUIDE_Y = START_BACK_Y;

    private static final int KEYBOARD_KEY_SIZE = 7;
    private static final int KEYBOARD_BASE_X = 118;
    private static final int KEYBOARD_BASE_Y = 56;

    /**
     * Keyboard keys of the built-in layout. The IDs are stable because a saved layout
     * matches its elements by ID.
     */
    private static final char[] DEFAULT_KEYBOARD_KEYS = new char[]{'R'};

    /**
     * Builds the built-in arrangement of controls into the given layout.
     */
    public static void createDefaultLayout(final UniversalVirtualController controller, final Context context) {
        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);

        // Displace controls on the right by this amount of pixels to account for different aspect ratios
        int rightDisplacement = screen.widthPixels - screen.heightPixels * 16 / 9;
        int height = screen.heightPixels;

        controller.setNextElementId(UniversalVirtualController.FIRST_DYNAMIC_ELEMENT_ID);

        List<ElementDefinition> definitions = new ArrayList<>();

        definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_DPAD,
                UniversalVirtualControllerElement.EID_DPAD, "D-pad",
                DPAD_BASE_X, DPAD_BASE_Y, DPAD_SIZE, DPAD_SIZE));

        definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_BUTTON,
                UniversalVirtualControllerElement.EID_A,
                !config.flipFaceButtons ? "A" : "B",
                BUTTON_BASE_X, BUTTON_BASE_Y + 2 * BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE)
                .button(GamepadButtons.codeOfFlag(!config.flipFaceButtons
                        ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG)));

        definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_BUTTON,
                UniversalVirtualControllerElement.EID_B,
                config.flipFaceButtons ? "A" : "B",
                BUTTON_BASE_X + BUTTON_SIZE, BUTTON_BASE_Y + BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE)
                .button(GamepadButtons.codeOfFlag(config.flipFaceButtons
                        ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG)));

        definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_BUTTON,
                UniversalVirtualControllerElement.EID_X,
                !config.flipFaceButtons ? "X" : "Y",
                BUTTON_BASE_X - BUTTON_SIZE, BUTTON_BASE_Y + BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE)
                .button(GamepadButtons.codeOfFlag(!config.flipFaceButtons
                        ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG)));

        definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_BUTTON,
                UniversalVirtualControllerElement.EID_Y,
                config.flipFaceButtons ? "X" : "Y",
                BUTTON_BASE_X, BUTTON_BASE_Y, BUTTON_SIZE, BUTTON_SIZE)
                .button(GamepadButtons.codeOfFlag(config.flipFaceButtons
                        ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG)));

        definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_BUTTON,
                UniversalVirtualControllerElement.EID_LT, "LT",
                TRIGGER_L_BASE_X, TRIGGER_BASE_Y, TRIGGER_WIDTH, TRIGGER_HEIGHT)
                .trigger(InputEvent.TRIGGER_LEFT));

        definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_BUTTON,
                UniversalVirtualControllerElement.EID_RT, "RT",
                TRIGGER_R_BASE_X + TRIGGER_DISTANCE, TRIGGER_BASE_Y,
                TRIGGER_WIDTH, TRIGGER_HEIGHT)
                .trigger(InputEvent.TRIGGER_RIGHT));

        definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_BUTTON,
                UniversalVirtualControllerElement.EID_LB, "LB",
                TRIGGER_L_BASE_X + TRIGGER_DISTANCE, TRIGGER_BASE_Y,
                TRIGGER_WIDTH, TRIGGER_HEIGHT)
                .button(GamepadButtons.codeOfFlag(ControllerPacket.LB_FLAG)));

        definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_BUTTON,
                UniversalVirtualControllerElement.EID_RB, "RB",
                TRIGGER_R_BASE_X, TRIGGER_BASE_Y, TRIGGER_WIDTH, TRIGGER_HEIGHT)
                .button(GamepadButtons.codeOfFlag(ControllerPacket.RB_FLAG)));

        definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_STICK,
                UniversalVirtualControllerElement.EID_LS, "Left stick",
                ANALOG_L_BASE_X, ANALOG_L_BASE_Y, ANALOG_SIZE, ANALOG_SIZE)
                .axes(UniversalHostAxis.KIND_GAMEPAD, UniversalHostAxis.TARGET_LEFT_X,
                        UniversalHostAxis.KIND_GAMEPAD, UniversalHostAxis.TARGET_LEFT_Y));

        definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_STICK,
                UniversalVirtualControllerElement.EID_RS, "Right stick",
                ANALOG_R_BASE_X, ANALOG_R_BASE_Y, ANALOG_SIZE, ANALOG_SIZE)
                .axes(UniversalHostAxis.KIND_GAMEPAD, UniversalHostAxis.TARGET_RIGHT_X,
                        UniversalHostAxis.KIND_GAMEPAD, UniversalHostAxis.TARGET_RIGHT_Y));

        definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_BUTTON,
                UniversalVirtualControllerElement.EID_BACK, "BACK",
                BACK_X, START_BACK_Y, START_BACK_WIDTH, START_BACK_HEIGHT)
                .button(GamepadButtons.codeOfFlag(ControllerPacket.BACK_FLAG)));

        definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_BUTTON,
                UniversalVirtualControllerElement.EID_START, "START",
                START_X, START_BACK_Y, START_BACK_WIDTH, START_BACK_HEIGHT)
                .button(GamepadButtons.codeOfFlag(ControllerPacket.PLAY_FLAG)));

        definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_BUTTON,
                UniversalVirtualControllerElement.EID_LSB, "L3",
                TRIGGER_L_BASE_X, L3_R3_BASE_Y, TRIGGER_WIDTH, TRIGGER_HEIGHT)
                .button(GamepadButtons.codeOfFlag(ControllerPacket.LS_CLK_FLAG)));

        definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_BUTTON,
                UniversalVirtualControllerElement.EID_RSB, "R3",
                TRIGGER_R_BASE_X + TRIGGER_DISTANCE, L3_R3_BASE_Y,
                TRIGGER_WIDTH, TRIGGER_HEIGHT)
                .button(GamepadButtons.codeOfFlag(ControllerPacket.RS_CLK_FLAG)));

        definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_BUTTON,
                UniversalVirtualControllerElement.EID_GDB, "GUIDE",
                GUIDE_X, GUIDE_Y, START_BACK_WIDTH, START_BACK_HEIGHT)
                .button(GamepadButtons.codeOfFlag(ControllerPacket.SPECIAL_BUTTON_FLAG)));

        for (int i = 0; i < DEFAULT_KEYBOARD_KEYS.length; i++) {
            definitions.add(new ElementDefinition(UniversalElementTypes.ELEMENT_BUTTON,
                    UniversalVirtualControllerElement.EID_KEYBOARD_BASE + i,
                    String.valueOf(DEFAULT_KEYBOARD_KEYS[i]),
                    KEYBOARD_BASE_X, KEYBOARD_BASE_Y, KEYBOARD_KEY_SIZE, KEYBOARD_KEY_SIZE)
                    .key(DEFAULT_KEYBOARD_KEYS[i]));
        }

        for (ElementDefinition definition : definitions) {
            UniversalVirtualControllerElement element = createElement(controller, context,
                    definition.type, definition.elementId);
            if (element == null) {
                continue;
            }

            applyDefinition(element, definition);
            element.setElementName(definition.name);

            controller.addElement(element,
                    screenScale(definition.gridX, height) + rightDisplacement,
                    screenScale(definition.gridY, height),
                    screenScale(definition.gridWidth, height),
                    screenScale(definition.gridHeight, height));
        }

        // The transparency of the layout is applied by the controller once the whole layout
        // is in place, which is also what a control added later adopts.
    }

    /**
     * Writes the built-in action of one element into its configuration.
     */
    private static void applyDefinition(UniversalVirtualControllerElement element,
                                        ElementDefinition definition) {
        UniversalElementConfig config = element.getConfig();

        if (config instanceof UniversalStickConfig) {
            UniversalStickConfig stickConfig = (UniversalStickConfig) config;
            stickConfig.setAxisX(definition.axisXCode);
            stickConfig.setAxisY(definition.axisYCode);
            return;
        }

        if (!(config instanceof UniversalButtonConfig)) {
            return;
        }

        UniversalButtonConfig buttonConfig = (UniversalButtonConfig) config;

        if (definition.character != 0) {
            buttonConfig.setActionSequence(ActionSequence.single(new InputEvent(
                    InputEvent.KIND_KEYBOARD, keyboardKeyCode(definition.character))));
        }
        else if (definition.trigger >= 0) {
            buttonConfig.setActionSequence(ActionSequence.single(new InputEvent(
                    InputEvent.KIND_TRIGGER, definition.trigger)));
        }
        else if (definition.gamepadButton != GamepadButtons.CODE_NONE) {
            buttonConfig.setActionSequence(ActionSequence.single(new InputEvent(
                    InputEvent.KIND_GAMEPAD, definition.gamepadButton)));
        }
    }

    private static int keyboardKeyCode(char character) {
        short translated = getKeyboardTranslator().translate(
                androidKeyCodeForCharacter(character), -1);

        if (translated == 0) {
            throw new IllegalArgumentException(
                    "On-screen key " + character + " has no GFE keycode mapping");
        }

        return translated & 0xFFFF;
    }

    /**
     * Maps a layout definition character onto an Android keycode. Only A-Z and 0-9 are
     * supported, since those are the only keycodes whose Android values are contiguous.
     */
    private static int androidKeyCodeForCharacter(char character) {
        char key = Character.toUpperCase(character);

        if (key >= 'A' && key <= 'Z') {
            return KeyEvent.KEYCODE_A + (key - 'A');
        }
        else if (key >= '0' && key <= '9') {
            return KeyEvent.KEYCODE_0 + (key - '0');
        }

        throw new IllegalArgumentException("Unsupported on-screen key character: " + character);
    }

    /**
     * Creates an element of the given kind.
     *
     * This is the only place that knows how a type name maps onto a class, so the
     * built-in layout and a layout saved by the user are built the same way.
     *
     * @return the element, or null if the type is unknown
     */
    public static UniversalVirtualControllerElement createElement(UniversalVirtualController controller,
                                                                  Context context,
                                                                  String type,
                                                                  int elementId) {
        if (UniversalElementTypes.ELEMENT_STICK.equals(type)) {
            // The built-in layout has one stick on each side, told apart by their ID. A
            // stick the user adds starts as a left stick and can be re-pointed in the
            // editor.
            if (elementId == UniversalVirtualControllerElement.EID_RS) {
                return new UniversalRightAnalogStick(controller, context);
            }

            return new UniversalLeftAnalogStick(controller, context);
        }

        if (UniversalElementTypes.ELEMENT_DPAD.equals(type)) {
            UniversalDigitalPad pad = new UniversalDigitalPad(controller, context, elementId);

            // Each direction starts on the matching D-pad button of the host controller
            for (int direction = 0; direction < UniversalDigitalPad.DIRECTION_COUNT; direction++) {
                pad.getPadConfig().setDirectionSequence(direction,
                        UniversalDigitalPad.defaultSequenceForDirection(direction));
            }

            return pad;
        }

        // "trigger" is what an older layout calls a button that drove an analog trigger, so the
        // name is still understood and the element it makes is an ordinary button
        if ("trigger".equals(type)) {
            UniversalDigitalButton button = new UniversalDigitalButton(controller, elementId, 1, context);

            button.getButtonConfig().setActionSequence(ActionSequence.single(new InputEvent(
                    InputEvent.KIND_TRIGGER,
                    elementId == UniversalVirtualControllerElement.EID_LT
                            ? InputEvent.TRIGGER_LEFT : InputEvent.TRIGGER_RIGHT)));

            return button;
        }

        if (UniversalElementTypes.ELEMENT_BUTTON.equals(type) ||
                UniversalElementTypes.ELEMENT_BASE.equals(type)) {
            // A plain button starts with no action: the built-in layout gives it one right
            // after creating it, and the editor lets the user choose one for buttons it
            // adds.
            return new UniversalDigitalButton(controller, elementId, 1, context);
        }

        return null;
    }

    /**
     * Saves the whole set of elements of a layout.
     *
     * @param arrangedWidth  width of the area the controls were arranged in
     * @param arrangedHeight height of the area the controls were arranged in
     */
    public static void saveProfile(final UniversalVirtualController controller,
                                   final Context context,
                                   final String layoutId,
                                   int arrangedWidth,
                                   int arrangedHeight) {
        JSONArray elementArray = new JSONArray();

        for (UniversalVirtualControllerElement element : controller.getElements()) {
            try {
                JSONObject elementJson = element.getConfiguration();
                elementJson.put(UniversalElementTypes.KEY_TYPE, element.getElementTypeName());
                elementJson.put(KEY_ELEMENT_ID, element.elementId);
                elementArray.put(elementJson);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        getLayoutPreferences(context, layoutId).edit()
                .putString(KEY_ELEMENTS, elementArray.toString())
                .putInt(KEY_NEXT_ELEMENT_ID, controller.peekNextElementId())
                .putInt(KEY_SAVED_WIDTH, arrangedWidth)
                .putInt(KEY_SAVED_HEIGHT, arrangedHeight)
                .apply();
    }

    /**
     * Rebuilds a layout from what the user saved.
     *
     * A layout that was never saved keeps the built-in arrangement. A layout saved by an
     * earlier version, which only stored the geometry of each built-in element, is read
     * through the same keys it used.
     */
    public static void loadFromPreferences(final UniversalVirtualController controller,
                                           final Context context,
                                           final String layoutId) {
        SharedPreferences preferences = getLayoutPreferences(context, layoutId);

        String savedElements = preferences.getString(KEY_ELEMENTS, null);
        if (savedElements != null && loadElementSet(controller, context, savedElements)) {
            controller.setNextElementId(preferences.getInt(KEY_NEXT_ELEMENT_ID,
                    UniversalVirtualController.FIRST_DYNAMIC_ELEMENT_ID));
        }
        else {
            applyLegacyGeometry(controller, preferences);
        }

        scheduleBringIntoView(controller, preferences);
    }

    /**
     * Brings a layout that was arranged on a different screen size back into view.
     *
     * The size of the area is not known while the layout is still being built, so the check
     * is deferred to the first layout pass that reports a size. Adjusting the layout is also
     * what marks it as having unsaved changes: the adjustment only becomes permanent when it
     * is saved, and it must not be silently lost.
     */
    private static void scheduleBringIntoView(final UniversalVirtualController controller,
                                              final SharedPreferences preferences) {
        final View area = controller.getLayoutArea();
        if (area == null) {
            return;
        }

        area.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            private int lastWidth = -1;
            private int lastHeight = -1;

            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int width = right - left;
                int height = bottom - top;

                // The listener fires for every layout pass, so it only acts on a real change
                // of size
                if (width == lastWidth && height == lastHeight) {
                    return;
                }

                lastWidth = width;
                lastHeight = height;

                bringIntoView(preferences, controller, width, height);
            }
        });

        // A layout that is already on screen had its layout pass before this listener was
        // added, so it is checked once directly as well.
        if (area.getWidth() > 0 && area.getHeight() > 0) {
            bringIntoView(preferences, controller, area.getWidth(), area.getHeight());
        }
    }

    /**
     * Rescales a layout that was arranged on a screen of a different size, so its controls
     * do not end up outside the area.
     *
     * A layout is a set of pixel positions, so the same numbers land in different places on
     * a different screen: a layout made in portrait would otherwise put most of its controls
     * off the side of a landscape screen. This runs once per layout.
     *
     * The size the layout was made for is used when it is known. A layout saved before that
     * size was recorded is judged by whether it actually fits, which is what catches the
     * layouts that are already broken.
     */
    /**
     * Fits the loaded layout to an area of the given size, for the debug smoke test. The real
     * call happens on the first layout pass that reports a size, which a test cannot wait for
     * reliably.
     */
    public static void fitToAreaForTesting(UniversalVirtualController controller, Context context,
                                           String layoutId, int width, int height) {
        bringIntoView(getLayoutPreferences(context, layoutId), controller, width, height);
    }

    private static void bringIntoView(SharedPreferences preferences,
                                      UniversalVirtualController controller,
                                      int currentWidth,
                                      int currentHeight) {
        if (preferences.getBoolean(KEY_RESCALED, false) ||
                currentWidth <= 0 || currentHeight <= 0) {
            return;
        }

        int savedWidth = preferences.getInt(KEY_SAVED_WIDTH, 0);
        int savedHeight = preferences.getInt(KEY_SAVED_HEIGHT, 0);

        if (savedWidth > 0 && savedHeight > 0) {
            // The layout was made for the size it recorded, so it only needs adjusting when
            // that size differs from the one it is being opened on. Both dimensions have to
            // be close: a layout made for a portrait screen differs in width alone, and
            // treating that as "the same" would leave its controls off the side.
            boolean sameSize = Math.abs(savedWidth - currentWidth) <= RESCALE_TOLERANCE_PX &&
                    Math.abs(savedHeight - currentHeight) <= RESCALE_TOLERANCE_PX;

            if (sameSize) {
                return;
            }
        }
        else {
            // Without a recorded size there is nothing to scale by, so the layout is only
            // rescaled when it does not fit in the area at all
            if (fitsWithin(controller, currentWidth, currentHeight)) {
                return;
            }

            savedWidth = measureReachX(controller, currentWidth);
            savedHeight = measureReachY(controller, currentHeight);
        }

        for (UniversalVirtualControllerElement element : controller.getElements()) {
            UniversalGeometry geometry = element.getConfig().getGeometry();

            // The same rule an imported layout is moved by, so a layout ends up in the same
            // place whether it was opened from here or arrived as a file
            UniversalGeometry moved = UniversalLayoutExchange.rescale(geometry,
                    savedWidth, savedHeight, currentWidth, currentHeight);

            geometry.setLeft(moved.getLeft());
            geometry.setTop(moved.getTop());
            geometry.setWidth(moved.getWidth());
            geometry.setHeight(moved.getHeight());
            geometry.applyTo(element);
        }

        preferences.edit().putBoolean(KEY_RESCALED, true).apply();
        controller.markConfigurationDirty();
        controller.notifyLayoutAdjustedToFit();
    }

    /**
     * True when every control of the layout is inside the area, give or take the margin a
     * layout is allowed to hang over the edge.
     */
    private static boolean fitsWithin(UniversalVirtualController controller,
                                      int currentWidth,
                                      int currentHeight) {
        for (UniversalVirtualControllerElement element : controller.getElements()) {
            UniversalGeometry geometry = element.getConfig().getGeometry();

            // A control that starts outside the area can never be reached
            if (geometry.getLeft() < -OUT_OF_VIEW_MARGIN_PX ||
                    geometry.getTop() < -OUT_OF_VIEW_MARGIN_PX) {
                return false;
            }

            // A control that ends well outside the area is mostly unreachable as well
            if (geometry.getLeft() + geometry.getWidth() > currentWidth + OUT_OF_VIEW_MARGIN_PX ||
                    geometry.getTop() + geometry.getHeight() > currentHeight + OUT_OF_VIEW_MARGIN_PX) {
                return false;
            }
        }

        return true;
    }

    /**
     * How far right the controls of a layout reach, which is the width the layout was
     * effectively made for when its own size was not recorded.
     */
    private static int measureReachX(UniversalVirtualController controller, int minimum) {
        int reach = 0;

        for (UniversalVirtualControllerElement element : controller.getElements()) {
            UniversalGeometry geometry = element.getConfig().getGeometry();
            reach = Math.max(reach, geometry.getLeft() + geometry.getWidth());
        }

        return Math.max(reach, minimum / 4);
    }

    /**
     * How far down the controls of a layout reach, see measureReachX.
     */
    private static int measureReachY(UniversalVirtualController controller, int minimum) {
        int reach = 0;

        for (UniversalVirtualControllerElement element : controller.getElements()) {
            UniversalGeometry geometry = element.getConfig().getGeometry();
            reach = Math.max(reach, geometry.getTop() + geometry.getHeight());
        }

        return Math.max(reach, minimum / 4);
    }

    /**
     * How far a control may hang over the edge before the layout counts as not fitting.
     */
    private static final int OUT_OF_VIEW_MARGIN_PX = 8;

    private static final int RESCALE_TOLERANCE_PX = 4;

    private static boolean loadElementSet(UniversalVirtualController controller,
                                          Context context,
                                          String savedElements) {
        try {
            JSONArray elementArray = new JSONArray(savedElements);

            // Build the whole set before touching the layout, so a corrupt file cannot
            // leave the layout half built.
            List<UniversalVirtualControllerElement> loaded = new ArrayList<>();

            for (int i = 0; i < elementArray.length(); i++) {
                JSONObject elementJson = elementArray.getJSONObject(i);

                String type = elementJson.optString(UniversalElementTypes.KEY_TYPE,
                        UniversalElementTypes.ELEMENT_BUTTON);
                int elementId = elementJson.optInt(KEY_ELEMENT_ID, -1);
                if (elementId < 0) {
                    continue;
                }

                UniversalVirtualControllerElement element =
                        createElement(controller, context, type, elementId);
                if (element == null) {
                    continue;
                }

                element.loadConfiguration(elementJson);
                loaded.add(element);
            }

            if (loaded.isEmpty()) {
                return false;
            }

            controller.replaceElements(loaded);
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Writes a whole set of controls for a layout, and the screen it was arranged on.
     *
     * This is how an imported layout is stored. The screen is the one the file was written on
     * rather than the one it is being read on, so the layout is fitted when it is opened, where
     * the size of the area the controls live in is known.
     */
    public static void writeStoredElements(Context context, String layoutId, String elementsJson,
                                           int arrangedWidth, int arrangedHeight) {
        context.getSharedPreferences(getLayoutPreferenceFileName(layoutId), Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ELEMENTS, elementsJson)
                .putInt(KEY_NEXT_ELEMENT_ID, nextElementIdIn(elementsJson))
                .putInt(KEY_SAVED_WIDTH, arrangedWidth)
                .putInt(KEY_SAVED_HEIGHT, arrangedHeight)
                .apply();
    }

    /**
     * The arrangement the app ships with, as a file in the app itself.
     */
    private static final String SHIPPED_LAYOUT_ASSET = "universal_default_layout.json";

    /**
     * Puts the arrangement the app ships with into the built-in layout.
     *
     * A fresh install has nothing stored for the built-in layout, so the shipped arrangement is
     * written in as its own, together with the screen it was arranged on. From there it is
     * treated like any other layout: fitted to whatever screen the app is first run on, by the
     * same rule an imported layout is fitted by.
     *
     * @return whether the built-in layout has an arrangement to show
     */
    public static boolean seedShippedLayout(Context context) {
        SharedPreferences preferences = getLayoutPreferences(context, UniversalLayoutStore.DEFAULT_LAYOUT_ID);

        if (preferences.getString(KEY_ELEMENTS, null) != null) {
            // It already has an arrangement, which may be the user's own
            return true;
        }

        try {
            UniversalLayoutExchange.Imported shipped = UniversalLayoutExchange.fromJson(
                    readAsset(context, SHIPPED_LAYOUT_ASSET));

            writeStoredElements(context, UniversalLayoutStore.DEFAULT_LAYOUT_ID,
                    shipped.getElementsJson(), shipped.getSourceWidth(), shipped.getSourceHeight());

            return !shipped.getElementsJson().isEmpty();
        } catch (java.io.IOException e) {
            return false;
        } catch (JSONException e) {
            return false;
        }
    }

    /**
     * Writes the shipped arrangement into a layout of the caller's choosing, so the debug smoke
     * test can edit a copy of it instead of the layout the user made.
     */
    public static boolean seedShippedArrangementInto(Context context, String layoutId) {
        try {
            UniversalLayoutExchange.Imported shipped = UniversalLayoutExchange.fromJson(
                    readAsset(context, SHIPPED_LAYOUT_ASSET));

            writeStoredElements(context, layoutId, shipped.getElementsJson(),
                    shipped.getSourceWidth(), shipped.getSourceHeight());

            return !shipped.getElementsJson().isEmpty();
        } catch (java.io.IOException e) {
            return false;
        } catch (JSONException e) {
            return false;
        }
    }

    private static String readAsset(Context context, String name) throws java.io.IOException {
        java.io.InputStream in = context.getAssets().open(name);

        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;

            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }

            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    /**
     * The id a set of stored controls needs next, which is one past the highest it holds.
     */
    public static int nextElementIdIn(String elementsJson) {
        int highest = 0;

        try {
            JSONArray elements = new JSONArray(elementsJson);

            for (int i = 0; i < elements.length(); i++) {
                JSONObject element = elements.optJSONObject(i);

                if (element != null) {
                    highest = Math.max(highest, element.optInt(KEY_ELEMENT_ID, 0));
                }
            }
        } catch (JSONException e) {
            return 1;
        }

        return highest + 1;
    }

    /**
     * Reads the geometry a layout saved before the element model existed. Those files
     * stored one entry per element ID, so they are simply ignored once a layout has been
     * saved in the new form.
     */
    private static void applyLegacyGeometry(UniversalVirtualController controller,
                                            SharedPreferences preferences) {
        for (UniversalVirtualControllerElement element : controller.getElements()) {
            String jsonConfig = preferences.getString(element.getPreferenceKey(), null);
            if (jsonConfig == null) {
                continue;
            }

            try {
                element.loadConfiguration(new JSONObject(jsonConfig));
            } catch (JSONException e) {
                e.printStackTrace();

                // Remove the corrupt element from the preferences
                preferences.edit().remove(element.getPreferenceKey()).apply();
            }
        }
    }

    /**
     * Drops the saved configuration of an element the user deleted, so the layout file
     * does not keep entries for elements that are gone.
     */
    public static void forgetElement(final Context context, final String layoutId, int elementId) {
        SharedPreferences preferences = getLayoutPreferences(context, layoutId);

        String savedElements = preferences.getString(KEY_ELEMENTS, null);
        if (savedElements == null) {
            // A layout that only has the legacy geometry keys keeps them per element ID
            preferences.edit().remove("UNIVERSAL_" + elementId).apply();
            return;
        }

        try {
            JSONArray elementArray = new JSONArray(savedElements);
            JSONArray remaining = new JSONArray();

            for (int i = 0; i < elementArray.length(); i++) {
                JSONObject elementJson = elementArray.getJSONObject(i);
                if (elementJson.optInt(KEY_ELEMENT_ID, -1) != elementId) {
                    remaining.put(elementJson);
                }
            }

            preferences.edit().putString(KEY_ELEMENTS, remaining.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Deletes everything that was saved for a layout, which puts its controls back to the
     * built-in arrangement.
     */
    public static void resetLayout(final Context context, final String layoutId) {
        context.deleteSharedPreferences(getLayoutPreferenceFileName(layoutId));
    }
}
