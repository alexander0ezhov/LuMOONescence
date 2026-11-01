package com.limelight.binding.input.virtual_controller.universal;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A single named universal layout: a set of on-screen controls that the user can
 * rearrange, plus the artwork that identifies it in the layout list.
 *
 * The geometry of the controls is not stored here. Each layout owns a preferences file
 * named after its ID, which is what {@link UniversalLayoutConfigurationLoader} reads
 * and writes.
 */
public class UniversalLayoutDefinition {
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_IMAGE_PATH = "imagePath";
    private static final String KEY_CREATED_AT = "createdAt";

    public static final String DEFAULT_IMAGE_PATH = "";

    private final String id;
    private String name;
    private String imagePath;
    private final long createdAt;

    public UniversalLayoutDefinition(String id, String name, String imagePath, long createdAt) {
        this.id = id;
        this.name = name;
        this.imagePath = imagePath;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Absolute path of the user's artwork, or an empty string to use the default image.
     */
    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean hasCustomImage() {
        return imagePath != null && !imagePath.isEmpty();
    }

    /**
     * Returns the preferences file that holds the geometry of this layout.
     */
    public String getPreferenceFileName() {
        return UniversalLayoutConfigurationLoader.getLayoutPreferenceFileName(id);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(KEY_ID, id);
        json.put(KEY_NAME, name);
        json.put(KEY_IMAGE_PATH, imagePath == null ? DEFAULT_IMAGE_PATH : imagePath);
        json.put(KEY_CREATED_AT, createdAt);
        return json;
    }

    public static UniversalLayoutDefinition fromJson(JSONObject json) throws JSONException {
        String id = json.getString(KEY_ID);
        String name = json.optString(KEY_NAME, id);
        String imagePath = json.optString(KEY_IMAGE_PATH, DEFAULT_IMAGE_PATH);
        long createdAt = json.optLong(KEY_CREATED_AT, 0);

        return new UniversalLayoutDefinition(id, name, imagePath, createdAt);
    }
}
