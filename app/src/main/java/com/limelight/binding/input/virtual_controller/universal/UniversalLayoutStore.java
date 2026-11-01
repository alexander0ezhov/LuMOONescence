package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Registry of the universal layouts the user has created, kept in a single preferences
 * entry as a JSON document.
 *
 * A layout that the user deletes loses its geometry as well, so the geometry file of a
 * removed layout is deleted together with its entry.
 */
public class UniversalLayoutStore {
    private static final String PREFERENCES_NAME = "UNIVERSAL_LAYOUTS";
    private static final String KEY_LAYOUTS = "layouts";
    private static final String KEY_SELECTED_LAYOUT_ID = "selectedLayoutId";

    /**
     * ID of the layout that is created on first use. It is a fixed name so that the
     * built-in layout survives a reinstall of the app data while still being editable
     * and deletable like any other layout.
     */
    public static final String DEFAULT_LAYOUT_ID = "default";

    /**
     * What the built-in layout is called. It says what it is for, since the app brings more
     * than one arrangement.
     */
    public static final String DEFAULT_LAYOUT_NAME = "Default (Controller)";

    private static UniversalLayoutStore instance;

    private final Context context;

    private final List<UniversalLayoutDefinition> layouts = new ArrayList<>();
    private String selectedLayoutId = "";

    private UniversalLayoutStore(Context context) {
        this.context = context.getApplicationContext();
        load();
    }

    public static synchronized UniversalLayoutStore getInstance(Context context) {
        if (instance == null) {
            instance = new UniversalLayoutStore(context);
        }
        return instance;
    }

    private SharedPreferences getPreferences() {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private void load() {
        layouts.clear();

        String raw = getPreferences().getString(KEY_LAYOUTS, null);
        if (raw != null) {
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    layouts.add(UniversalLayoutDefinition.fromJson(array.getJSONObject(i)));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        sortLayouts();

        selectedLayoutId = getPreferences().getString(KEY_SELECTED_LAYOUT_ID, "");

        // The built-in layout is what everything falls back to, so it is put back on the way in
        // rather than hoped for
        boolean restoredDefault = ensureDefaultLayout();

        // The user may have deleted the layout that was selected
        if (getLayout(selectedLayoutId) == null) {
            selectedLayoutId = layouts.isEmpty() ? "" : layouts.get(0).getId();
            restoredDefault = true;
        }

        if (restoredDefault) {
            persist();
        }
    }

    /**
     * Puts the built-in layout back when it is missing.
     *
     * @return whether it had to be added
     */
    private boolean ensureDefaultLayout() {
        UniversalLayoutDefinition existing = getLayout(DEFAULT_LAYOUT_ID);

        if (existing != null) {
            // The built-in layout used to be called just "Default", before the app brought more
            // than one arrangement and the name had to say which one it is
            if ("Default".equals(existing.getName())) {
                existing.setName(DEFAULT_LAYOUT_NAME);
                persist();
            }

            return false;
        }

        layouts.add(new UniversalLayoutDefinition(DEFAULT_LAYOUT_ID, DEFAULT_LAYOUT_NAME,
                UniversalLayoutDefinition.DEFAULT_IMAGE_PATH, System.currentTimeMillis()));
        sortLayouts();
        return true;
    }

    private void sortLayouts() {
        // The built-in layout always comes first, the rest in creation order
        Collections.sort(layouts, new Comparator<UniversalLayoutDefinition>() {
            @Override
            public int compare(UniversalLayoutDefinition first, UniversalLayoutDefinition second) {
                boolean firstIsDefault = DEFAULT_LAYOUT_ID.equals(first.getId());
                boolean secondIsDefault = DEFAULT_LAYOUT_ID.equals(second.getId());

                if (firstIsDefault != secondIsDefault) {
                    return firstIsDefault ? -1 : 1;
                }

                return Long.compare(first.getCreatedAt(), second.getCreatedAt());
            }
        });
    }

    private void persist() {
        JSONArray array = new JSONArray();
        for (UniversalLayoutDefinition layout : layouts) {
            try {
                array.put(layout.toJson());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        getPreferences().edit()
                .putString(KEY_LAYOUTS, array.toString())
                .putString(KEY_SELECTED_LAYOUT_ID, selectedLayoutId)
                .apply();
    }

    /**
     * Returns all layouts, making sure the built-in one is among them.
     *
     * The built-in layout is what the app falls back to when nothing else is left, so it is put
     * back whenever it is missing. Losing it would leave the app with no arrangement to show,
     * and would take the controls away from anyone who had not made a layout of their own.
     */
    public synchronized List<UniversalLayoutDefinition> getLayouts() {
        return new ArrayList<>(layouts);
    }

    public synchronized UniversalLayoutDefinition getLayout(String id) {
        if (id == null) {
            return null;
        }

        for (UniversalLayoutDefinition layout : layouts) {
            if (layout.getId().equals(id)) {
                return layout;
            }
        }

        return null;
    }

    /**
     * Returns the layout that the stream should use, or null if the user has no layouts.
     */
    public synchronized UniversalLayoutDefinition getSelectedLayout() {
        // Make sure the built-in layout exists before answering
        getLayouts();
        return getLayout(selectedLayoutId);
    }

    public synchronized String getSelectedLayoutId() {
        return selectedLayoutId;
    }

    public synchronized void setSelectedLayoutId(String id) {
        if (getLayout(id) == null) {
            return;
        }

        selectedLayoutId = id;
        persist();
    }

    /**
     * Adds a new layout with the given name and selects it, so the user can start
     * editing what they just created.
     */
    public synchronized UniversalLayoutDefinition addLayout(String name) {
        UniversalLayoutDefinition layout = new UniversalLayoutDefinition(
                UUID.randomUUID().toString(), name,
                UniversalLayoutDefinition.DEFAULT_IMAGE_PATH, System.currentTimeMillis());

        layouts.add(layout);
        sortLayouts();

        selectedLayoutId = layout.getId();
        persist();

        return layout;
    }

    public synchronized void updateLayout(UniversalLayoutDefinition layout) {
        persist();
    }

    /**
     * Removes the layout together with the geometry the user saved for it.
     */
    public synchronized void deleteLayout(String id) {
        UniversalLayoutDefinition layout = getLayout(id);
        if (layout == null) {
            return;
        }

        // The built-in layout is the one the app falls back to, so it stays even when the user
        // asks for it to go. Its own arrangement can be reset instead.
        if (DEFAULT_LAYOUT_ID.equals(layout.getId())) {
            return;
        }

        // Drop the saved geometry, otherwise a future layout that reuses this ID would
        // inherit the controls of the deleted one.
        context.deleteSharedPreferences(layout.getPreferenceFileName());

        layouts.remove(layout);

        if (selectedLayoutId.equals(id)) {
            selectedLayoutId = layouts.isEmpty() ? "" : layouts.get(0).getId();
        }

        persist();
    }

    /**
     * Returns true if the name is not used by another layout yet.
     */
    public synchronized boolean isNameAvailable(String name, String ignoredLayoutId) {
        for (UniversalLayoutDefinition layout : layouts) {
            if (layout.getId().equals(ignoredLayoutId)) {
                continue;
            }

            if (layout.getName().equalsIgnoreCase(name)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns a "name (2)" style name that is free to use.
     */
    public synchronized String makeUniqueName(String desiredName) {
        if (isNameAvailable(desiredName, null)) {
            return desiredName;
        }

        for (int suffix = 2; suffix < 1000; suffix++) {
            String candidate = desiredName + " (" + suffix + ")";
            if (isNameAvailable(candidate, null)) {
                return candidate;
            }
        }

        return desiredName + " " + System.currentTimeMillis();
    }
}
