package org.json;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal stand-in for org.json, used only by the off-device harness in
 * build-diagnostics. See JSONObject for why it exists.
 */
public class JSONArray {
    private final List<Object> values = new ArrayList<>();

    public JSONArray put(Object value) {
        if (value != null) {
            values.add(value);
        }

        return this;
    }

    public int length() {
        return values.size();
    }

    public Object opt(int index) {
        return index >= 0 && index < values.size() ? values.get(index) : null;
    }

    public JSONObject optJSONObject(int index) {
        Object value = opt(index);
        return value instanceof JSONObject ? (JSONObject) value : null;
    }

    public JSONObject getJSONObject(int index) throws JSONException {
        Object value = opt(index);

        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }

        throw new JSONException("not an object at " + index);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;

        for (Object value : values) {
            if (!first) {
                builder.append(',');
            }
            first = false;

            if (value instanceof JSONObject || value instanceof JSONArray) {
                builder.append(value.toString());
            }
            else if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            }
            else {
                builder.append('"').append(String.valueOf(value)).append('"');
            }
        }

        return builder.append(']').toString();
    }
}
