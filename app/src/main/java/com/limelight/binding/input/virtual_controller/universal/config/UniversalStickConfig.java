package com.limelight.binding.input.virtual_controller.universal.config;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * What an analog stick drives.
 *
 * There are two modes. In the axes mode the stick drives two host axes, each of which is
 * either a gamepad stick axis or the mouse pointer. In the buttons mode the stick drives
 * four gamepad buttons instead, one per direction, which is what a game that only reads
 * the D-pad needs.
 */
public class UniversalStickConfig extends UniversalElementConfig {
    public static final int MODE_AXES = 0;
    public static final int MODE_BUTTONS = 1;

    public static final int DIRECTION_UP = 0;
    public static final int DIRECTION_DOWN = 1;
    public static final int DIRECTION_LEFT = 2;
    public static final int DIRECTION_RIGHT = 3;

    public static final int DIRECTION_COUNT = 4;

    private static final String KEY_MODE = "mode";
    private static final String KEY_AXIS_X = "axisX";
    private static final String KEY_AXIS_Y = "axisY";
    private static final String KEY_BUTTONS = "buttons";

    private static final String[] DIRECTION_NAMES = new String[]{"Up", "Down", "Left", "Right"};

    private int mode = MODE_AXES;

    /**
     * Host axis codes, see UniversalHostAxis.
     */
    private int axisX = UniversalHostAxis.codeOf(UniversalHostAxis.KIND_GAMEPAD, UniversalHostAxis.TARGET_LEFT_X);
    private int axisY = UniversalHostAxis.codeOf(UniversalHostAxis.KIND_GAMEPAD, UniversalHostAxis.TARGET_LEFT_Y);

    private final UniversalStickButtonConfig[] buttons = new UniversalStickButtonConfig[DIRECTION_COUNT];

    public UniversalStickConfig() {
        for (int i = 0; i < DIRECTION_COUNT; i++) {
            buttons[i] = new UniversalStickButtonConfig();
        }
    }

    public static String directionName(int direction) {
        if (direction < 0 || direction >= DIRECTION_COUNT) {
            return "";
        }

        return DIRECTION_NAMES[direction];
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode == MODE_BUTTONS ? MODE_BUTTONS : MODE_AXES;
    }

    public int getAxisX() {
        return axisX;
    }

    public void setAxisX(int axisX) {
        this.axisX = axisX;
    }

    public int getAxisY() {
        return axisY;
    }

    public void setAxisY(int axisY) {
        this.axisY = axisY;
    }

    /**
     * Returns the config of one direction, see DIRECTION_*.
     */
    public UniversalStickButtonConfig getButton(int direction) {
        return buttons[direction];
    }

    public boolean isAxesMode() {
        return mode == MODE_AXES;
    }

    @Override
    protected JSONObject createOptionsJson() throws JSONException {
        JSONObject options = new JSONObject();
        options.put(KEY_MODE, mode);
        options.put(KEY_AXIS_X, axisX);
        options.put(KEY_AXIS_Y, axisY);

        JSONArray buttonArray = new JSONArray();
        for (UniversalStickButtonConfig button : buttons) {
            buttonArray.put(button.toJson());
        }
        options.put(KEY_BUTTONS, buttonArray);

        return options;
    }

    @Override
    protected void readOptionsJson(JSONObject options) throws JSONException {
        mode = options.optInt(KEY_MODE, MODE_AXES);
        axisX = options.optInt(KEY_AXIS_X, axisX);
        axisY = options.optInt(KEY_AXIS_Y, axisY);

        JSONArray buttonArray = options.optJSONArray(KEY_BUTTONS);
        if (buttonArray != null) {
            for (int i = 0; i < DIRECTION_COUNT && i < buttonArray.length(); i++) {
                buttons[i] = UniversalStickButtonConfig.fromJson(buttonArray.getJSONObject(i));
            }
        }
    }

    @Override
    protected UniversalElementConfig createEmptyCopy() {
        return new UniversalStickConfig();
    }

    @Override
    protected void copyOptionsFrom(UniversalElementConfig source) {
        if (!(source instanceof UniversalStickConfig)) {
            return;
        }

        UniversalStickConfig other = (UniversalStickConfig) source;
        mode = other.mode;
        axisX = other.axisX;
        axisY = other.axisY;

        for (int i = 0; i < DIRECTION_COUNT; i++) {
            buttons[i] = other.buttons[i].copy();
        }
    }
}
