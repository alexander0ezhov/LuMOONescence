/**
 * Universal layout: builds the default layout and persists the user's geometry.
 *
 * The universal layout stores its geometry in its own preferences file, so it can be
 * rearranged without touching the layout saved by the legacy OSC layout.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.view.KeyEvent;

import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

public class UniversalLayoutConfigurationLoader {
    /**
     * Preferences file that holds the geometry of the universal layout.
     */
    public static final String UNIVERSAL_LAYOUT_PREFERENCE = "OSC_UNIVERSAL";

    // The default controls are specified using a grid of 128*72 cells at 16:9
    private static int screenScale(int units, int height) {
        return (int) (((float) height / (float) 72) * (float) units);
    }

    private static UniversalDigitalPad createDigitalPad(
            final UniversalVirtualController controller,
            final Context context) {

        UniversalDigitalPad digitalPad = new UniversalDigitalPad(controller, context);
        digitalPad.addDigitalPadListener(new UniversalDigitalPad.DigitalPadListener() {
            @Override
            public void onDirectionChange(int direction) {
                UniversalVirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();

                if ((direction & UniversalDigitalPad.DIGITAL_PAD_DIRECTION_LEFT) != 0) {
                    inputContext.inputMap |= ControllerPacket.LEFT_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.LEFT_FLAG;
                }
                if ((direction & UniversalDigitalPad.DIGITAL_PAD_DIRECTION_RIGHT) != 0) {
                    inputContext.inputMap |= ControllerPacket.RIGHT_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.RIGHT_FLAG;
                }
                if ((direction & UniversalDigitalPad.DIGITAL_PAD_DIRECTION_UP) != 0) {
                    inputContext.inputMap |= ControllerPacket.UP_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.UP_FLAG;
                }
                if ((direction & UniversalDigitalPad.DIGITAL_PAD_DIRECTION_DOWN) != 0) {
                    inputContext.inputMap |= ControllerPacket.DOWN_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.DOWN_FLAG;
                }

                controller.sendControllerInputContext();
            }
        });

        return digitalPad;
    }

    private static UniversalDigitalButton createDigitalButton(
            final int elementId,
            final int keyShort,
            final int keyLong,
            final int layer,
            final String text,
            final int icon,
            final UniversalVirtualController controller,
            final Context context) {
        UniversalDigitalButton button = new UniversalDigitalButton(controller, elementId, layer, context);
        button.setText(text);
        button.setIcon(icon);

        button.addDigitalButtonListener(new UniversalDigitalButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                UniversalVirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= keyShort;

                controller.sendControllerInputContext();
            }

            @Override
            public void onLongClick() {
                UniversalVirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= keyLong;

                controller.sendControllerInputContext();
            }

            @Override
            public void onRelease() {
                UniversalVirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap &= ~keyShort;
                inputContext.inputMap &= ~keyLong;

                controller.sendControllerInputContext();
            }
        });

        return button;
    }

    /**
     * One on-screen keyboard key of the default layout. Declaring keys as data keeps
     * the layout definition readable when more keys are added.
     *
     * @param elementId element ID, see UniversalVirtualControllerElement.EID_KEYBOARD_BASE
     * @param character the character this key sends, A-Z or 0-9
     * @param gridX horizontal position in the 128*72 layout grid
     * @param gridY vertical position in the 128*72 layout grid
     */
    private static final class KeyboardKeyDefinition {
        final int elementId;
        final char character;
        final int gridX;
        final int gridY;

        KeyboardKeyDefinition(int elementId, char character, int gridX, int gridY) {
            this.elementId = elementId;
            this.character = character;
            this.gridX = gridX;
            this.gridY = gridY;
        }
    }

    /**
     * Keyboard keys of the default universal layout.
     *
     * Add a new entry here to place another key: the element ID must be unique and must
     * stay stable, because it is the localStorage key of the saved geometry.
     */
    private static final KeyboardKeyDefinition[] DEFAULT_KEYBOARD_KEYS = new KeyboardKeyDefinition[] {
            new KeyboardKeyDefinition(UniversalVirtualControllerElement.EID_KEYBOARD_BASE, 'R', 118, 56),
    };

    private static UniversalDigitalButton createLeftTrigger(
            final int layer,
            final String text,
            final int icon,
            final UniversalVirtualController controller,
            final Context context) {
        UniversalLeftTrigger button = new UniversalLeftTrigger(controller, layer, context);
        button.setText(text);
        button.setIcon(icon);
        return button;
    }

    private static UniversalDigitalButton createRightTrigger(
            final int layer,
            final String text,
            final int icon,
            final UniversalVirtualController controller,
            final Context context) {
        UniversalRightTrigger button = new UniversalRightTrigger(controller, layer, context);
        button.setText(text);
        button.setIcon(icon);
        return button;
    }

    private static UniversalAnalogStick createLeftStick(
            final UniversalVirtualController controller,
            final Context context) {
        return new UniversalLeftAnalogStick(controller, context);
    }

    private static UniversalAnalogStick createRightStick(
            final UniversalVirtualController controller,
            final Context context) {
        return new UniversalRightAnalogStick(controller, context);
    }


    private static final int TRIGGER_L_BASE_X = 1;
    private static final int TRIGGER_R_BASE_X = 92;
    private static final int TRIGGER_DISTANCE = 23;
    private static final int TRIGGER_BASE_Y = 31;
    private static final int TRIGGER_WIDTH = 12;
    private static final int TRIGGER_HEIGHT = 9;

    // Face buttons are defined based on the Y button (button number 9)
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

    // Make the Guide Menu be in the center of START and BACK menu
    private static final int GUIDE_X = START_X-BACK_X;
    private static final int GUIDE_Y = START_BACK_Y;

    private static final int KEYBOARD_KEY_SIZE = 7;

    public static void createDefaultLayout(final UniversalVirtualController controller, final Context context) {

        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);

        // Displace controls on the right by this amount of pixels to account for different aspect ratios
        int rightDisplacement = screen.widthPixels - screen.heightPixels * 16 / 9;

        int height = screen.heightPixels;

        // NOTE: Some of these getPercent() expressions seem like they can be combined
        // into a single call. Due to floating point rounding, this isn't actually possible.

        if (!config.onlyL3R3)
        {
            controller.addElement(createDigitalPad(controller, context),
                    screenScale(DPAD_BASE_X, height),
                    screenScale(DPAD_BASE_Y, height),
                    screenScale(DPAD_SIZE, height),
                    screenScale(DPAD_SIZE, height)
            );

            controller.addElement(createDigitalButton(
                    UniversalVirtualControllerElement.EID_A,
                    !config.flipFaceButtons ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG, 0, 1,
                    !config.flipFaceButtons ? "A" : "B", -1, controller, context),
                    screenScale(BUTTON_BASE_X, height) + rightDisplacement,
                    screenScale(BUTTON_BASE_Y + 2 * BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height)
            );

            controller.addElement(createDigitalButton(
                    UniversalVirtualControllerElement.EID_B,
                    config.flipFaceButtons ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG, 0, 1,
                    config.flipFaceButtons ? "A" : "B", -1, controller, context),
                    screenScale(BUTTON_BASE_X + BUTTON_SIZE, height) + rightDisplacement,
                    screenScale(BUTTON_BASE_Y + BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height)
            );

            controller.addElement(createDigitalButton(
                    UniversalVirtualControllerElement.EID_X,
                    !config.flipFaceButtons ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG, 0, 1,
                    !config.flipFaceButtons ? "X" : "Y", -1, controller, context),
                    screenScale(BUTTON_BASE_X - BUTTON_SIZE, height) + rightDisplacement,
                    screenScale(BUTTON_BASE_Y + BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height)
            );

            controller.addElement(createDigitalButton(
                    UniversalVirtualControllerElement.EID_Y,
                    config.flipFaceButtons ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG, 0, 1,
                    config.flipFaceButtons ? "X" : "Y", -1, controller, context),
                    screenScale(BUTTON_BASE_X, height) + rightDisplacement,
                    screenScale(BUTTON_BASE_Y, height),
                    screenScale(BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height)
            );

            controller.addElement(createLeftTrigger(
                    1, "LT", -1, controller, context),
                    screenScale(TRIGGER_L_BASE_X, height),
                    screenScale(TRIGGER_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            controller.addElement(createRightTrigger(
                    1, "RT", -1, controller, context),
                    screenScale(TRIGGER_R_BASE_X + TRIGGER_DISTANCE, height) + rightDisplacement,
                    screenScale(TRIGGER_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            controller.addElement(createDigitalButton(
                    UniversalVirtualControllerElement.EID_LB,
                    ControllerPacket.LB_FLAG, 0, 1, "LB", -1, controller, context),
                    screenScale(TRIGGER_L_BASE_X + TRIGGER_DISTANCE, height),
                    screenScale(TRIGGER_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            controller.addElement(createDigitalButton(
                    UniversalVirtualControllerElement.EID_RB,
                    ControllerPacket.RB_FLAG, 0, 1, "RB", -1, controller, context),
                    screenScale(TRIGGER_R_BASE_X, height) + rightDisplacement,
                    screenScale(TRIGGER_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            controller.addElement(createLeftStick(controller, context),
                    screenScale(ANALOG_L_BASE_X, height),
                    screenScale(ANALOG_L_BASE_Y, height),
                    screenScale(ANALOG_SIZE, height),
                    screenScale(ANALOG_SIZE, height)
            );

            controller.addElement(createRightStick(controller, context),
                    screenScale(ANALOG_R_BASE_X, height) + rightDisplacement,
                    screenScale(ANALOG_R_BASE_Y, height),
                    screenScale(ANALOG_SIZE, height),
                    screenScale(ANALOG_SIZE, height)
            );

            controller.addElement(createDigitalButton(
                    UniversalVirtualControllerElement.EID_BACK,
                    ControllerPacket.BACK_FLAG, 0, 2, "BACK", -1, controller, context),
                    screenScale(BACK_X, height),
                    screenScale(START_BACK_Y, height),
                    screenScale(START_BACK_WIDTH, height),
                    screenScale(START_BACK_HEIGHT, height)
            );

            controller.addElement(createDigitalButton(
                    UniversalVirtualControllerElement.EID_START,
                    ControllerPacket.PLAY_FLAG, 0, 3, "START", -1, controller, context),
                    screenScale(START_X, height) + rightDisplacement,
                    screenScale(START_BACK_Y, height),
                    screenScale(START_BACK_WIDTH, height),
                    screenScale(START_BACK_HEIGHT, height)
            );
        }
        else {
            controller.addElement(createDigitalButton(
                    UniversalVirtualControllerElement.EID_LSB,
                    ControllerPacket.LS_CLK_FLAG, 0, 1, "L3", -1, controller, context),
                    screenScale(TRIGGER_L_BASE_X, height),
                    screenScale(L3_R3_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            controller.addElement(createDigitalButton(
                    UniversalVirtualControllerElement.EID_RSB,
                    ControllerPacket.RS_CLK_FLAG, 0, 1, "R3", -1, controller, context),
                    screenScale(TRIGGER_R_BASE_X + TRIGGER_DISTANCE, height) + rightDisplacement,
                    screenScale(L3_R3_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );
        }

        if(config.showGuideButton){
            controller.addElement(createDigitalButton(UniversalVirtualControllerElement.EID_GDB,
                            ControllerPacket.SPECIAL_BUTTON_FLAG, 0, 1, "GUIDE", -1, controller, context),
                    screenScale(GUIDE_X, height)+ rightDisplacement,
                    screenScale(GUIDE_Y, height),
                    screenScale(START_BACK_WIDTH, height),
                    screenScale(START_BACK_HEIGHT, height)
            );
        }

        // On-screen keyboard keys. These are sent to the host as keyboard events, so
        // they work even for games that ignore gamepad input.
        for (KeyboardKeyDefinition key : DEFAULT_KEYBOARD_KEYS) {
            controller.addElement(new UniversalKeyboardButton(controller, key.elementId,
                            keyCodeForCharacter(key.character), 1, String.valueOf(key.character), -1, context),
                    screenScale(key.gridX, height) + rightDisplacement,
                    screenScale(key.gridY, height),
                    screenScale(KEYBOARD_KEY_SIZE, height),
                    screenScale(KEYBOARD_KEY_SIZE, height)
            );
        }

        controller.setOpacity(config.oscOpacity);
    }

    /**
     * Maps a layout definition character onto an Android keycode. Only A-Z and 0-9 are
     * supported, since those are the only keycodes whose Android values are contiguous.
     */
    private static int keyCodeForCharacter(char character) {
        char key = Character.toUpperCase(character);

        if (key >= 'A' && key <= 'Z') {
            return KeyEvent.KEYCODE_A + (key - 'A');
        }
        else if (key >= '0' && key <= '9') {
            return KeyEvent.KEYCODE_0 + (key - '0');
        }

        throw new IllegalArgumentException("Unsupported on-screen key character: " + character);
    }

    public static void saveProfile(final UniversalVirtualController controller,
                                   final Context context) {
        SharedPreferences.Editor prefEditor = context.getSharedPreferences(UNIVERSAL_LAYOUT_PREFERENCE, Activity.MODE_PRIVATE).edit();

        for (UniversalVirtualControllerElement element : controller.getElements()) {
            try {
                prefEditor.putString(element.getPreferenceKey(), element.getConfiguration().toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        prefEditor.apply();
    }

    public static void loadFromPreferences(final UniversalVirtualController controller, final Context context) {
        SharedPreferences pref = context.getSharedPreferences(UNIVERSAL_LAYOUT_PREFERENCE, Activity.MODE_PRIVATE);

        for (UniversalVirtualControllerElement element : controller.getElements()) {
            String prefKey = element.getPreferenceKey();

            String jsonConfig = pref.getString(prefKey, null);
            if (jsonConfig != null) {
                try {
                    element.loadConfiguration(new JSONObject(jsonConfig));
                } catch (JSONException e) {
                    e.printStackTrace();

                    // Remove the corrupt element from the preferences
                    pref.edit().remove(prefKey).apply();
                }
            }
        }
    }
}
