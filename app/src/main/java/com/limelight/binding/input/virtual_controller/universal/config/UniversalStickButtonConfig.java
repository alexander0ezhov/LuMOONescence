package com.limelight.binding.input.virtual_controller.universal.config;

import com.limelight.binding.input.virtual_controller.universal.action.ActionSequence;
import com.limelight.binding.input.virtual_controller.universal.action.ActionStep;
import com.limelight.binding.input.virtual_controller.universal.action.InputEvent;

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

    /**
     * Points this binding at one input.
     *
     * A gamepad button is kept as a code, because that is what the key of a stick click and
     * a direction used to be. Anything else, a key of a keyboard for example, is kept as a one
     * step sequence, which is what lets a stick direction send a key.
     */
    public void setBinding(InputEvent event) {
        if (event == null) {
            buttonCode = 0;
            actionSequence = ActionSequence.empty();
            return;
        }

        if (event.isGamepad()) {
            buttonCode = event.getCode();
            actionSequence = ActionSequence.empty();
            return;
        }

        buttonCode = 0;
        actionSequence = ActionSequence.single(event);
    }

    /**
     * True when this binding is exactly the given input, which is what the editor uses to show
     * what is selected.
     */
    public boolean isBoundTo(InputEvent event) {
        if (event.isGamepad()) {
            return buttonCode == event.getCode() && actionSequence.isEmpty();
        }

        if (actionSequence.getSteps().size() != 1) {
            return false;
        }

        ActionStep step = actionSequence.getSteps().get(0);

        return step.getEvents().size() == 1 && step.getEvents().get(0).equals(event);
    }

    /**
     * What pushing this direction or click sends.
     */
    public ActionSequence getEffectiveSequence() {
        if (!actionSequence.isEmpty()) {
            return actionSequence;
        }

        if (buttonCode == 0) {
            return ActionSequence.empty();
        }

        return ActionSequence.single(new InputEvent(InputEvent.KIND_GAMEPAD, buttonCode));
    }

    public boolean isAssigned() {
        return buttonCode != 0 || !actionSequence.isEmpty();
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
