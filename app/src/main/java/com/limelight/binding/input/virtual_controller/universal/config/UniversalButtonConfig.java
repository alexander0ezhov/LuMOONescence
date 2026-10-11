package com.limelight.binding.input.virtual_controller.universal.config;

import com.limelight.binding.input.virtual_controller.universal.action.ActionSequence;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * What a button does when it is pressed.
 *
 * "toggle" turns the button into a latch: the first tap presses the sequence and holds
 * it, the second tap releases it. Without it the sequence is held only while the finger
 * is on the button.
 */
public class UniversalButtonConfig extends UniversalElementConfig {
    private static final String KEY_SEQUENCE = "sequence";
    private static final String KEY_TOGGLE = "toggle";

    private ActionSequence actionSequence = ActionSequence.empty();
    private boolean toggle;

    public ActionSequence getActionSequence() {
        return actionSequence;
    }

    public void setActionSequence(ActionSequence actionSequence) {
        this.actionSequence = actionSequence == null ? ActionSequence.empty() : actionSequence;
    }

    public boolean isToggle() {
        return toggle;
    }

    public void setToggle(boolean toggle) {
        this.toggle = toggle;
    }

    @Override
    protected JSONObject createOptionsJson() throws JSONException {
        JSONObject options = new JSONObject();
        options.put(KEY_SEQUENCE, actionSequence.toJson());
        options.put(KEY_TOGGLE, toggle);
        return options;
    }

    @Override
    protected void readOptionsJson(JSONObject options) throws JSONException {
        JSONObject sequence = options.optJSONObject(KEY_SEQUENCE);
        if (sequence != null) {
            actionSequence = ActionSequence.fromJson(sequence);
        }

        toggle = options.optBoolean(KEY_TOGGLE, false);
    }

    @Override
    protected UniversalElementConfig createEmptyCopy() {
        return new UniversalButtonConfig();
    }

    @Override
    protected void copyOptionsFrom(UniversalElementConfig source) {
        if (!(source instanceof UniversalButtonConfig)) {
            return;
        }

        UniversalButtonConfig other = (UniversalButtonConfig) source;
        actionSequence = other.actionSequence.copy();
        toggle = other.toggle;
    }
}
