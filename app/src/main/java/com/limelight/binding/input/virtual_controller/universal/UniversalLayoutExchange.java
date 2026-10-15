package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;

import com.limelight.binding.input.virtual_controller.universal.config.UniversalGeometry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A layout written as a file, so an arrangement can be carried to another device.
 *
 * The file records the screen the layout was arranged on, because the numbers in it only mean
 * something next to the screen they were chosen for. Reading one back on a different screen
 * moves every control to the same place on the screen and grows it by the smaller of the two
 * changes, so a control keeps its place and its shape instead of being stretched.
 */
public final class UniversalLayoutExchange {
    private UniversalLayoutExchange() {
    }

    /**
     * Marks a file as ours, so importing something else says so instead of failing strangely.
     */
    public static final String FORMAT = "moonlight-universal-layout";

    public static final int VERSION = 1;

    private static final String KEY_FORMAT = "format";
    private static final String KEY_VERSION = "version";
    private static final String KEY_NAME = "name";
    private static final String KEY_SCREEN = "screen";
    private static final String KEY_SCREEN_WIDTH = "width";
    private static final String KEY_SCREEN_HEIGHT = "height";
    private static final String KEY_ELEMENTS = "elements";

    /**
     * The file name an exported layout is shared under.
     */
    public static String fileNameFor(String name) {
        StringBuilder safe = new StringBuilder();

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);

            // Anything that a file system could object to becomes a dash
            safe.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '-');
        }

        String trimmed = safe.toString();

        while (trimmed.contains("--")) {
            trimmed = trimmed.replace("--", "-");
        }

        if (trimmed.isEmpty()) {
            trimmed = "layout";
        }

        return trimmed + ".json";
    }

    /**
     * Writes a whole layout as text.
     *
     * @param elementsJson the list of controls, in the form the layout is stored in
     * @param screenWidth  the screen the arrangement was made for
     */
    public static String toJson(String name, int screenWidth, int screenHeight,
                                String elementsJson) throws JSONException {
        return new JSONObject()
                .put(KEY_FORMAT, FORMAT)
                .put(KEY_VERSION, VERSION)
                .put(KEY_NAME, name)
                .put(KEY_SCREEN, new JSONObject()
                        .put(KEY_SCREEN_WIDTH, screenWidth)
                        .put(KEY_SCREEN_HEIGHT, screenHeight))
                .put(KEY_ELEMENTS, new JSONArray(elementsJson))
                .toString(2);
    }

    /**
     * Reads a layout from text.
     *
     * The controls come back exactly as the file has them. Fitting them to a screen is
     * deliberately not done here, because the screen a layout is read on is not the screen it
     * will be used on: importing happens on the layouts screen, which is a portrait list, while
     * the controls belong to the streaming area, which is landscape. Measuring them against the
     * list shrank every control to a fraction of itself and moved it across the screen.
     *
     * What the file says about the screen it was arranged on is kept and handed on instead, and
     * the layout is fitted when it is opened, where the size of the real area is known.
     */
    public static Imported fromJson(String text) throws JSONException {
        JSONObject json = new JSONObject(text);

        String format = json.optString(KEY_FORMAT, "");
        if (!FORMAT.equals(format)) {
            throw new JSONException("not a layout file");
        }

        if (json.optInt(KEY_VERSION, 0) > VERSION) {
            throw new JSONException("this file was written by a newer version");
        }

        JSONArray elements = json.optJSONArray(KEY_ELEMENTS);
        if (elements == null) {
            throw new JSONException("no controls in the file");
        }

        JSONObject screen = json.optJSONObject(KEY_SCREEN);
        int fromWidth = screen == null ? 0 : screen.optInt(KEY_SCREEN_WIDTH, 0);
        int fromHeight = screen == null ? 0 : screen.optInt(KEY_SCREEN_HEIGHT, 0);

        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.optJSONObject(i);

            if (element == null || element.optJSONObject("geometry") == null) {
                // A control with no place is not something that can be imported
                throw new JSONException("a control in the file has no place");
            }
        }

        return new Imported(json.optString(KEY_NAME, ""), elements.toString(),
                fromWidth, fromHeight);
    }

    /**
     * Whether a layout was arranged for a screen this device also has, in the orientation
     * controls are used in.
     */
    public static boolean fitsThisDevice(Context context, int fromWidth, int fromHeight) {
        if (fromWidth <= 0 || fromHeight <= 0) {
            return false;
        }

        android.util.DisplayMetrics screen = context.getResources().getDisplayMetrics();

        // Controls are arranged in landscape, so the longer side of the display is the width
        int landscapeWidth = Math.max(screen.widthPixels, screen.heightPixels);
        int landscapeHeight = Math.min(screen.widthPixels, screen.heightPixels);

        return Math.abs(landscapeWidth - fromWidth) <= SAME_SCREEN_TOLERANCE_PX
                && Math.abs(landscapeHeight - fromHeight) <= SAME_SCREEN_TOLERANCE_PX;
    }

    private static final int SAME_SCREEN_TOLERANCE_PX = 8;

    /**
     * The geometry of one control, as a layout file writes it.
     *
     * The keys are read here rather than through UniversalGeometry.fromJson, which expects
     * either a wrapper around the geometry or the flat capitalised form of the very first
     * version. Handed the geometry object itself it finds neither and answers with zeroes,
     * which would silently pile every imported control into the corner of the screen.
     */
    public static UniversalGeometry readGeometry(JSONObject geometry) {
        return new UniversalGeometry(
                geometry.optInt("left"),
                geometry.optInt("top"),
                Math.max(UniversalGeometry.MIN_SIZE, geometry.optInt("width")),
                Math.max(UniversalGeometry.MIN_SIZE, geometry.optInt("height")));
    }

    /**
     * Moves one control from the screen it was placed on to another.
     *
     * The place of a control is kept as a share of the screen, so one that sat in a corner still
     * sits in the corner. Its size changes by the smaller of the two changes, so a control gets
     * no wider than it gets taller and its shape survives a screen of a different proportion.
     */
    public static UniversalGeometry rescale(UniversalGeometry geometry,
                                            int fromWidth, int fromHeight,
                                            int toWidth, int toHeight) {
        float horizontal = (float) toWidth / fromWidth;
        float vertical = (float) toHeight / fromHeight;
        float size = Math.min(horizontal, vertical);

        return new UniversalGeometry(
                Math.round(geometry.getLeft() * horizontal),
                Math.round(geometry.getTop() * vertical),
                Math.max(UniversalGeometry.MIN_SIZE, Math.round(geometry.getWidth() * size)),
                Math.max(UniversalGeometry.MIN_SIZE, Math.round(geometry.getHeight() * size)));
    }

    /**
     * A layout that was read from a file.
     */
    public static final class Imported {
        private final String name;
        private final String elementsJson;
        private final int screenWidth;
        private final int screenHeight;

        Imported(String name, String elementsJson, int screenWidth, int screenHeight) {
            this.name = name;
            this.elementsJson = elementsJson;
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
        }

        public String getName() {
            return name;
        }

        /**
         * The controls, already placed for the screen they are being read onto.
         */
        public String getElementsJson() {
            return elementsJson;
        }

        /**
         * The screen the file was written on, or 0 when it did not say.
         */
        public int getSourceWidth() {
            return screenWidth;
        }

        public int getSourceHeight() {
            return screenHeight;
        }

        /**
         * Whether the file recorded the screen it was arranged on.
         */
        public boolean hasSourceScreen() {
            return screenWidth > 0 && screenHeight > 0;
        }
    }

    /**
     * Stores a layout that was read from a file as a layout of its own.
     *
     * A layout is always added rather than replaced, so importing can never overwrite work that
     * is already there, and the name is made unique for the same reason.
     */
    public static UniversalLayoutDefinition store(Context context,
                                                  Imported imported) {
        String name = imported.getName() == null || imported.getName().trim().isEmpty()
                ? context.getString(com.limelight.R.string.title_add_layout)
                : imported.getName().trim();

        UniversalLayoutStore store = UniversalLayoutStore.getInstance(context);
        UniversalLayoutDefinition created = store.addLayout(store.makeUniqueName(name));

        UniversalLayoutConfigurationLoader.writeStoredElements(context, created.getId(),
                imported.getElementsJson(), imported.getSourceWidth(), imported.getSourceHeight());

        return created;
    }

    /**
     * The file an exported layout is shared as, written into the app's own cache. Nothing
     * outside the app can read it except the app the user shares it with.
     */
    public static java.io.File writeShareFile(Context context, String layoutName, String json)
            throws java.io.IOException {
        java.io.File directory = new java.io.File(context.getCacheDir(), "shared-layouts");

        if (!directory.exists() && !directory.mkdirs()) {
            throw new java.io.IOException("could not make " + directory);
        }

        java.io.File file = new java.io.File(directory, fileNameFor(layoutName));

        java.io.FileOutputStream out = new java.io.FileOutputStream(file);

        try {
            out.write(json.getBytes("UTF-8"));
        } finally {
            out.close();
        }

        return file;
    }
}
