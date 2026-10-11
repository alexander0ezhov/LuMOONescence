package org.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal stand-in for org.json, used only by the off-device harness in
 * build-diagnostics. It exists because android.jar ships stubs that throw, and the
 * harness needs a real implementation to exercise the layout model.
 */
public class JSONObject {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public JSONObject() {
    }

    /**
     * Parses a JSON object from its text form.
     */
    public JSONObject(String source) throws JSONException {
        Parser parser = new Parser(source);
        JSONObject parsed = parser.parseObject();
        values.putAll(parsed.values);
    }

    private static List<String> stringKeys(Object key) {
        List<String> keys = new ArrayList<>();

        if (key instanceof String) {
            String text = (String) key;
            int start = text.indexOf('"');
            while (start >= 0) {
                int end = text.indexOf('"', start + 1);
                if (end < 0) {
                    break;
                }
                keys.add(text.substring(start + 1, end));
                start = text.indexOf('"', end + 1);
            }
        }
        else if (key instanceof String[]) {
            for (String text : (String[]) key) {
                keys.add(text);
            }
        }

        return keys;
    }

    public JSONObject put(String name, Object value) {
        if (value != null) {
            values.put(name, value);
        }

        return this;
    }

    public JSONObject put(String name, long value) {
        values.put(name, value);
        return this;
    }

    public JSONObject put(String name, int value) {
        values.put(name, value);
        return this;
    }

    public JSONObject put(String name, boolean value) {
        values.put(name, value);
        return this;
    }

    /**
     * Accepts a single name or a list of names, so has() can report whether any of them
     * is present.
     */
    public boolean has(String key) {
        return values.containsKey(key);
    }

    public boolean has(Object key) {
        for (String name : stringKeys(key)) {
            if (values.containsKey(name)) {
                return true;
            }
        }

        return false;
    }

    public Object opt(String name) {
        return values.get(name);
    }

    public String optString(String name, String fallback) {
        Object value = values.get(name);
        return value == null ? fallback : String.valueOf(value);
    }

    public int optInt(String name, int fallback) {
        Object value = values.get(name);

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        return fallback;
    }

    public int optInt(String name) {
        return optInt(name, 0);
    }

    public long optLong(String name, long fallback) {
        Object value = values.get(name);

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        return fallback;
    }

    public boolean optBoolean(String name, boolean fallback) {
        Object value = values.get(name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    public JSONArray optJSONArray(String name) {
        Object value = values.get(name);
        return value instanceof JSONArray ? (JSONArray) value : null;
    }

    public JSONObject optJSONObject(String name) {
        Object value = values.get(name);
        return value instanceof JSONObject ? (JSONObject) value : null;
    }

    public String getString(String name) throws JSONException {
        Object value = values.get(name);

        if (value == null) {
            throw new JSONException("no value for " + name);
        }

        return String.valueOf(value);
    }

    public int getInt(String name) throws JSONException {
        Object value = values.get(name);

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        throw new JSONException("not a number: " + name);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;

            builder.append('"').append(entry.getKey()).append("\":").append(format(entry.getValue()));
        }

        return builder.append('}').toString();
    }

    private static String format(Object value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }

        if (value instanceof JSONObject || value instanceof JSONArray) {
            return value.toString();
        }

        return '"' + String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    /**
     * A small recursive descent parser, only as complete as the harness needs.
     */
    private static final class Parser {
        private final String source;
        private int position;

        Parser(String source) {
            this.source = source;
        }

        JSONObject parseObject() throws JSONException {
            skipWhitespace();

            if (peek() != '{') {
                throw new JSONException("expected an object at " + position);
            }

            position++;
            JSONObject object = new JSONObject();

            skipWhitespace();
            if (peek() == '}') {
                position++;
                return object;
            }

            while (true) {
                skipWhitespace();

                String name = parseString();
                skipWhitespace();

                if (peek() != ':') {
                    throw new JSONException("expected a colon at " + position);
                }
                position++;

                skipWhitespace();
                object.values.put(name, parseValue());

                skipWhitespace();

                char next = peek();
                if (next == ',') {
                    position++;
                    continue;
                }

                if (next == '}') {
                    position++;
                    return object;
                }

                throw new JSONException("unexpected " + next + " at " + position);
            }
        }

        JSONArray parseArray() throws JSONException {
            skipWhitespace();

            if (peek() != '[') {
                throw new JSONException("expected an array at " + position);
            }

            position++;
            JSONArray array = new JSONArray();

            skipWhitespace();
            if (peek() == ']') {
                position++;
                return array;
            }

            while (true) {
                skipWhitespace();
                array.put(parseValue());
                skipWhitespace();

                char next = peek();
                if (next == ',') {
                    position++;
                    continue;
                }

                if (next == ']') {
                    position++;
                    return array;
                }

                throw new JSONException("unexpected " + next + " at " + position);
            }
        }

        Object parseValue() throws JSONException {
            skipWhitespace();

            char next = peek();

            if (next == '{') {
                return parseObject();
            }

            if (next == '[') {
                return parseArray();
            }

            if (next == '"') {
                return parseString();
            }

            if (source.startsWith("true", position)) {
                position += 4;
                return Boolean.TRUE;
            }

            if (source.startsWith("false", position)) {
                position += 5;
                return Boolean.FALSE;
            }

            if (source.startsWith("null", position)) {
                position += 4;
                return null;
            }

            return parseNumber();
        }

        private Object parseNumber() throws JSONException {
            int start = position;

            while (position < source.length()) {
                char c = source.charAt(position);
                if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || (c >= '0' && c <= '9')) {
                    position++;
                }
                else {
                    break;
                }
            }

            String text = source.substring(start, position);
            if (text.isEmpty()) {
                throw new JSONException("expected a value at " + start);
            }

            if (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0) {
                return Double.valueOf(text);
            }

            try {
                return Long.valueOf(text);
            } catch (NumberFormatException e) {
                throw new JSONException("bad number " + text);
            }
        }

        private String parseString() throws JSONException {
            if (peek() != '"') {
                throw new JSONException("expected a string at " + position);
            }

            position++;
            StringBuilder builder = new StringBuilder();

            while (position < source.length()) {
                char c = source.charAt(position++);

                if (c == '"') {
                    return builder.toString();
                }

                if (c == '\\') {
                    char escaped = source.charAt(position++);
                    switch (escaped) {
                        case 'n':
                            builder.append('\n');
                            break;

                        case 't':
                            builder.append('\t');
                            break;

                        case 'r':
                            builder.append('\r');
                            break;

                        case 'b':
                            builder.append('\b');
                            break;

                        case 'f':
                            builder.append('\f');
                            break;

                        case 'u':
                            builder.append((char) Integer.parseInt(source.substring(position, position + 4), 16));
                            position += 4;
                            break;

                        default:
                            builder.append(escaped);
                            break;
                    }
                    continue;
                }

                builder.append(c);
            }

            throw new JSONException("unterminated string");
        }

        private char peek() {
            return position < source.length() ? source.charAt(position) : '\0';
        }

        private void skipWhitespace() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
                position++;
            }
        }
    }
}
