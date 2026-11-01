package com.limelight.binding.input.virtual_controller.universal.action;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One step of an action sequence: a set of inputs that start together, followed by a
 * pause before the next step.
 *
 * A step holds a set rather than a single input so that combinations such as
 * "left mouse button and right mouse button together" are expressible. A sequence is
 * then a list of steps, which is what allows a pattern like
 * right button, wait 200 ms, left button.
 */
public class ActionStep {
    private static final String KEY_DELAY = "delayMs";
    private static final String KEY_EVENTS = "events";

    /**
     * Pause after this step's inputs have been sent.
     */
    private long delayMs;

    private final List<InputEvent> events = new ArrayList<>();

    public ActionStep() {
    }

    public ActionStep(InputEvent... events) {
        Collections.addAll(this.events, events);
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = Math.max(0, delayMs);
    }

    public List<InputEvent> getEvents() {
        return events;
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    public ActionStep addEvent(InputEvent event) {
        if (event != null && event.getKind() >= 0 && !(event.isGamepad() && event.getCode() == GamepadButtons.CODE_NONE)) {
            events.add(event);
        }

        return this;
    }

    public ActionStep withDelay(long delayMs) {
        setDelayMs(delayMs);
        return this;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(KEY_DELAY, delayMs);

        JSONArray eventArray = new JSONArray();
        for (InputEvent event : events) {
            eventArray.put(event.toJson());
        }
        json.put(KEY_EVENTS, eventArray);

        return json;
    }

    public static ActionStep fromJson(JSONObject json) throws JSONException {
        ActionStep step = new ActionStep();
        step.setDelayMs(json.optLong(KEY_DELAY, 0));

        JSONArray eventArray = json.optJSONArray(KEY_EVENTS);
        if (eventArray != null) {
            for (int i = 0; i < eventArray.length(); i++) {
                step.events.add(InputEvent.fromJson(eventArray.getJSONObject(i)));
            }
        }

        return step;
    }
}
