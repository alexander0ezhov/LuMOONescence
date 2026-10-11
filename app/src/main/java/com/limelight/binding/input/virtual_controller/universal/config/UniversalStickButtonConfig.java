package com.limelight.binding.input.virtual_controller.universal.config;

import com.limelight.binding.input.virtual_controller.universal.action.ActionSequence;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One gamepad button a stick drives, together with what sending that button involves.
 *
 * A stick can be bound to plain buttons instead of axes, which is how an on-screen stick
 * emulates a D-pad or the face buttons for games that ignore the analog sticks.
 */
public class UniversalStickButtonConfig {
    private static final String KEY_BUTTON_CODE = "buttonCode";
    private static final String KEY_SEQUENCE = "sequence";

    private int buttonCode;
    private ActionSequence actionSequence = ActionSequence.empty();

    public int getButtonCode() {
        return buttonCode;
    }

    public void setButtonCode(int buttonCode) {
        this.buttonCode = buttonCode;
    }

    public ActionSequence getActionSequence() {
        return actionSequence;
    }

    public void setActionSequence(ActionSequence actionSequence) {
        this.actionSequence = actionSequence == null ? ActionSequence.empty() : actionSequence;
    }

    public boolean isAssigned() {
        return buttonCode != 0;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(KEY_BUTTON_CODE, buttonCode);
        json.put(KEY_SEQUENCE, actionSequence.toJson());
        return json;
    }

    public static UniversalStickButtonConfig fromJson(JSONObject json) throws JSONException {
        UniversalStickButtonConfig config = new UniversalStickButtonConfig();
        config.buttonCode = json.optInt(KEY_BUTTON_CODE, 0);

        JSONObject sequence = json.optJSONObject(KEY_SEQUENCE);
        if (sequence != null) {
            config.actionSequence = ActionSequence.fromJson(sequence);
        }

        return config;
    }

    public UniversalStickButtonConfig copy() {
        UniversalStickButtonConfig copy = new UniversalStickButtonConfig();
        copy.buttonCode = buttonCode;
        copy.actionSequence = actionSequence.copy();
        return copy;
    }
}
