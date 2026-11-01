package com.limelight.binding.input.virtual_controller.universal.config;

import com.limelight.binding.input.virtual_controller.universal.action.ActionSequence;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * What each direction of a D-pad sends.
 *
 * Every direction has its own action, so a pad can drive the D-pad buttons of a gamepad,
 * arrow keys, or a combination, and the "up and right together" case still works because
 * each direction is pressed and released on its own.
 */
public class UniversalPadConfig extends UniversalElementConfig {
    private static final String KEY_DIRECTIONS = "directions";

    /**
     * Order of the directions in the saved file and in the editor: up, down, left, right.
     */
    public static final int DIRECTION_COUNT = 4;

    private final ActionSequence[] directions = new ActionSequence[DIRECTION_COUNT];

    public UniversalPadConfig() {
        for (int i = 0; i < DIRECTION_COUNT; i++) {
            directions[i] = ActionSequence.empty();
        }
    }

    public ActionSequence getDirectionSequence(int direction) {
        if (direction < 0 || direction >= DIRECTION_COUNT) {
            return ActionSequence.empty();
        }

        return directions[direction];
    }

    public void setDirectionSequence(int direction, ActionSequence sequence) {
        if (direction < 0 || direction >= DIRECTION_COUNT) {
            return;
        }

        directions[direction] = sequence == null ? ActionSequence.empty() : sequence;
    }

    @Override
    protected JSONObject createOptionsJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (ActionSequence sequence : directions) {
            array.put(sequence.toJson());
        }

        JSONObject options = new JSONObject();
        options.put(KEY_DIRECTIONS, array);
        return options;
    }

    @Override
    protected void readOptionsJson(JSONObject options) throws JSONException {
        JSONArray array = options.optJSONArray(KEY_DIRECTIONS);
        if (array == null) {
            return;
        }

        for (int i = 0; i < DIRECTION_COUNT && i < array.length(); i++) {
            directions[i] = ActionSequence.fromJson(array.getJSONObject(i));
        }
    }

    @Override
    protected UniversalElementConfig createEmptyCopy() {
        return new UniversalPadConfig();
    }

    @Override
    protected void copyOptionsFrom(UniversalElementConfig source) {
        if (!(source instanceof UniversalPadConfig)) {
            return;
        }

        UniversalPadConfig other = (UniversalPadConfig) source;
        for (int i = 0; i < DIRECTION_COUNT; i++) {
            directions[i] = other.directions[i].copy();
        }
    }
}
