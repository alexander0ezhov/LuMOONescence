package com.limelight.binding.input.virtual_controller.universal.action;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * What an on-screen element sends to the host: an ordered list of steps.
 *
 * A press plays the steps in order, waiting each step's delay before the next one. A
 * release undoes them in reverse order, so every input that was pressed is released.
 */
public class ActionSequence {
    private static final String KEY_STEPS = "steps";

    private final List<ActionStep> steps = new ArrayList<>();

    public static ActionSequence empty() {
        return new ActionSequence();
    }

    /**
     * A sequence that holds one input down for as long as the element is held.
     */
    public static ActionSequence single(InputEvent event) {
        ActionSequence sequence = new ActionSequence();
        sequence.addStep(new ActionStep(event));
        return sequence;
    }

    /**
     * A sequence that presses several inputs together and releases them together.
     */
    public static ActionSequence combo(InputEvent... events) {
        ActionSequence sequence = new ActionSequence();
        sequence.addStep(new ActionStep(events));
        return sequence;
    }

    /**
     * A sequence that sends each step after the delay the step carries, which is how a
     * pattern like "right button, wait 200 ms, left button" is expressed.
     */
    public static ActionSequence delayed(ActionStep... steps) {
        ActionSequence sequence = new ActionSequence();
        for (ActionStep step : steps) {
            sequence.addStep(step);
        }
        return sequence;
    }

    public ActionSequence addStep(ActionStep step) {
        steps.add(step);
        return this;
    }

    public List<ActionStep> getSteps() {
        return steps;
    }

    public boolean isEmpty() {
        for (ActionStep step : steps) {
            if (!step.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public ActionSequence copy() {
        ActionSequence copy = new ActionSequence();

        for (ActionStep step : steps) {
            ActionStep stepCopy = new ActionStep();
            stepCopy.setDelayMs(step.getDelayMs());

            for (InputEvent event : step.getEvents()) {
                stepCopy.addEvent(new InputEvent(event.getKind(), event.getCode()));
            }

            copy.addStep(stepCopy);
        }

        return copy;
    }

    /**
     * True when this sequence describes the same inputs with the same delays.
     */
    public boolean describesSameMovementAs(ActionSequence other) {
        if (other == null || steps.size() != other.steps.size()) {
            return false;
        }

        for (int i = 0; i < steps.size(); i++) {
            ActionStep first = steps.get(i);
            ActionStep second = other.steps.get(i);

            if (first.getDelayMs() != second.getDelayMs()) {
                return false;
            }

            if (first.getEvents().size() != second.getEvents().size()) {
                return false;
            }

            // Order inside a step does not matter, because the inputs start together
            for (InputEvent event : first.getEvents()) {
                if (!second.getEvents().contains(event)) {
                    return false;
                }
            }
        }

        return true;
    }

    public JSONObject toJson() throws JSONException {
        JSONArray stepArray = new JSONArray();
        for (ActionStep step : steps) {
            stepArray.put(step.toJson());
        }

        JSONObject json = new JSONObject();
        json.put(KEY_STEPS, stepArray);
        return json;
    }

    public static ActionSequence fromJson(JSONObject json) throws JSONException {
        ActionSequence sequence = new ActionSequence();

        JSONArray stepArray = json.optJSONArray(KEY_STEPS);
        if (stepArray != null) {
            for (int i = 0; i < stepArray.length(); i++) {
                sequence.addStep(ActionStep.fromJson(stepArray.getJSONObject(i)));
            }
        }

        return sequence;
    }
}
