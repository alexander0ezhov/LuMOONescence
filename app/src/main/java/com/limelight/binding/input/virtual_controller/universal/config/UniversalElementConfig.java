package com.limelight.binding.input.virtual_controller.universal.config;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Everything that is configurable about one on-screen element.
 *
 * The geometry is shared by every element. What an element does with a press differs by
 * kind, so the behaviour lives in the subclasses and is stored under the "options" key,
 * which keeps a layout file readable and lets a new kind of element be added without
 * changing how the existing ones are read.
 */
public class UniversalElementConfig {
    private static final String KEY_NAME = "name";
    private static final String KEY_GEOMETRY = "geometry";
    private static final String KEY_OPTIONS = "options";
    private static final String KEY_BORDER_RADIUS = "borderRadius";

    /**
     * Whether a press on this control passes the finger's movement on to the touch input.
     *
     * It is what makes a control usable in a shooter: holding it keeps firing while the same
     * finger still looks around, instead of the finger being spent on the button.
     */
    private static final String KEY_PASS_THROUGH = "passThrough";

    /**
     * Corner radius of a control that has not been given one, as a share of its shorter
     * side. A share rather than a number of pixels, so the shape survives every resize.
     */
    public static final int DEFAULT_BORDER_RADIUS_PERCENT = 22;

    private String name = "";
    private UniversalGeometry geometry = new UniversalGeometry();

    /**
     * How round the control is drawn, as a share of its shorter side: 0 is a rectangle and
     * 50 is a circle or a pill.
     */
    private int borderRadius = DEFAULT_BORDER_RADIUS_PERCENT;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public boolean isPassThrough() {
        return passThrough;
    }

    public void setPassThrough(boolean passThrough) {
        this.passThrough = passThrough;
    }

    private boolean passThrough;

    public int getBorderRadius() {
        return borderRadius;
    }

    public void setBorderRadius(int borderRadius) {
        this.borderRadius = Math.max(0, Math.min(MAX_BORDER_RADIUS_PERCENT, borderRadius));
    }

    public static final int MAX_BORDER_RADIUS_PERCENT = 50;

    public UniversalGeometry getGeometry() {
        return geometry;
    }

    public void setGeometry(UniversalGeometry geometry) {
        this.geometry = geometry;
    }

    /**
     * Returns the "options" object of this config, which subclasses serialise into.
     */
    protected JSONObject createOptionsJson() throws JSONException {
        return new JSONObject();
    }

    /**
     * Reads this config's own fields out of the "options" object.
     */
    protected void readOptionsJson(JSONObject options) throws JSONException {
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(KEY_NAME, name);
        json.put(KEY_GEOMETRY, geometry.toJson());
        json.put(KEY_BORDER_RADIUS, borderRadius);
        json.put(KEY_PASS_THROUGH, passThrough);
        json.put(KEY_OPTIONS, createOptionsJson());
        return json;
    }

    public final void loadFromJson(JSONObject json) throws JSONException {
        name = json.optString(KEY_NAME, "");

        // A layout written before the corner radius existed keeps the default
        borderRadius = json.optInt(KEY_BORDER_RADIUS, DEFAULT_BORDER_RADIUS_PERCENT);
        passThrough = json.optBoolean(KEY_PASS_THROUGH, false);

        // Geometry can be missing in a config that was written before this model existed
        if (json.has(KEY_GEOMETRY) || json.has("LEFT")) {
            geometry = UniversalGeometry.fromJson(json);
        }

        JSONObject options = json.optJSONObject(KEY_OPTIONS);
        if (options != null) {
            readOptionsJson(options);
        }
    }

    /**
     * Returns a fresh config of this config's own kind. Subclasses override it so a
     * duplicated element starts from the right defaults.
     */
    protected UniversalElementConfig createEmptyCopy() {
        return new UniversalElementConfig();
    }

    /**
     * Returns an independent copy, used when an element is duplicated or when the editor
     * keeps a snapshot of a configuration.
     */
    public UniversalElementConfig copy() {
        UniversalElementConfig copy = createEmptyCopy();
        copy.name = name;
        copy.geometry = geometry.copy();
        copy.borderRadius = borderRadius;
        copy.passThrough = passThrough;
        copy.copyOptionsFrom(this);
        return copy;
    }

    /**
     * Copies the subclass specific fields from another config of the same kind.
     */
    protected void copyOptionsFrom(UniversalElementConfig source) {
    }
}
