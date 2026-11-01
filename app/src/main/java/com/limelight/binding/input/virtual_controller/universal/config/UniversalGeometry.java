package com.limelight.binding.input.virtual_controller.universal.config;

import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Position and size of an element inside its layout.
 *
 * Geometry is measured in the pixels of the layout grid, exactly like the values the
 * move and resize gestures already produce, so the editor's sliders can read and write
 * the same numbers the gestures do.
 */
public class UniversalGeometry {
    private int left;
    private int top;
    private int width;
    private int height;

    public UniversalGeometry() {
    }

    public UniversalGeometry(int left, int top, int width, int height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
    }

    public int getLeft() {
        return left;
    }

    public int getTop() {
        return top;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setLeft(int left) {
        this.left = Math.max(0, left);
    }

    public void setTop(int top) {
        this.top = Math.max(0, top);
    }

    /**
     * Elements are never smaller than the size the resize gesture allows.
     */
    public void setWidth(int width) {
        this.width = Math.max(MIN_SIZE, width);
    }

    public void setHeight(int height) {
        this.height = Math.max(MIN_SIZE, height);
    }

    public UniversalGeometry copy() {
        return new UniversalGeometry(left, top, width, height);
    }

    public static final int MIN_SIZE = 20;

    /**
     * Reads the geometry of a view that is already laid out.
     */
    public static UniversalGeometry of(View view) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        return new UniversalGeometry(params.leftMargin, params.topMargin,
                params.width, params.height);
    }

    /**
     * Writes the geometry onto a view, replacing whatever it had.
     */
    public void applyTo(View view) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        if (params == null) {
            params = new FrameLayout.LayoutParams(width, height);
        }

        params.leftMargin = left;
        params.topMargin = top;
        params.rightMargin = 0;
        params.bottomMargin = 0;
        params.width = width;
        params.height = height;

        view.setLayoutParams(params);
        view.requestLayout();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("left", left);
        json.put("top", top);
        json.put("width", width);
        json.put("height", height);
        return json;
    }

    /**
     * Reads geometry from either the new nested form or the flat form the first version
     * of the universal layout wrote, so layouts saved before this model still load.
     */
    public static UniversalGeometry fromJson(JSONObject json) throws JSONException {
        JSONObject source = json.optJSONObject("geometry");

        if (source != null) {
            return new UniversalGeometry(source.optInt("left"), source.optInt("top"),
                    Math.max(MIN_SIZE, source.optInt("width")),
                    Math.max(MIN_SIZE, source.optInt("height")));
        }

        // Legacy flat form, which stored the FrameLayout margins directly
        return new UniversalGeometry(
                json.optInt("LEFT"), json.optInt("TOP"),
                Math.max(MIN_SIZE, json.optInt("WIDTH")),
                Math.max(MIN_SIZE, json.optInt("HEIGHT")));
    }
}
