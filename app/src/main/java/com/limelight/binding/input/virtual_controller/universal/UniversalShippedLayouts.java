package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;

/**
 * The arrangements the app brings besides the built-in one: a layout for playing with a keyboard
 * and the mouse on the screen, and a full PC keyboard.
 *
 * Each is kept as a file of its own in the app's assets, so what a fresh install starts with is
 * exactly what was arranged and looked at, not something built again from numbers in code. They
 * are put in the list of layouts once per version, and they are ordinary layouts after that: they
 * can be changed, renamed or deleted like any other, and a layout that is no longer shipped is
 * replaced rather than left beside the new one.
 */
public final class UniversalShippedLayouts {
    /**
     * Which version of the shipped layouts is in place. Bumping it replaces the copies of the
     * layouts this build brings, which is how they are improved without the user having to make
     * them again.
     */
    private static final int VERSION = 7;

    private static final String VERSION_PREF_STRING = "shipped_extra_layouts_version";

    /**
     * The layouts this build brings, apart from the built-in one: the name the list shows
     * and the file the arrangement is kept in.
     */
    private static final String[][] SHIPPED = new String[][]{
            {"Default (kbd+mouse)", "universal_kbd_mouse_layout.json"},
            {"PC Keyboard", "universal_pc_keyboard_layout.json"},
    };

    /**
     * What earlier versions called them, so an arrangement under one of those names is
     * replaced instead of being left beside the new one.
     */
    private static final String[] RETIRED_NAMES = new String[]{"Keyboard", "Shooter"};

    private UniversalShippedLayouts() {
    }

    /**
     * Adds the layouts the first time this build runs.
     */
    public static void seedIfNeeded(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (preferences.getInt(VERSION_PREF_STRING, 0) >= VERSION) {
            return;
        }

        UniversalLayoutStore store = UniversalLayoutStore.getInstance(context);

        // Adding a layout makes it the selected one, and the layouts this build brings are not
        // what the user chose, so the choice is put back afterwards. A layout that is replaced
        // keeps its place in the selection as well.
        String selected = store.getSelectedLayoutId();
        String selectedName = null;

        for (UniversalLayoutDefinition layout : new ArrayList<>(store.getLayouts())) {
            if (isShippedName(layout.getName())) {
                if (layout.getId().equals(selected)) {
                    selectedName = layout.getName();
                }

                store.deleteLayout(layout.getId());
            }
        }

        for (String[] shipped : SHIPPED) {
            addLayout(context, store, shipped[0], shipped[1]);
        }

        if (selectedName != null) {
            String restored = replacementNameFor(selectedName);

            for (UniversalLayoutDefinition layout : store.getLayouts()) {
                if (layout.getName().equals(restored)) {
                    store.setSelectedLayoutId(layout.getId());
                    break;
                }
            }
        }
        else if (selected != null) {
            store.setSelectedLayoutId(selected);
        }

        preferences.edit().putInt(VERSION_PREF_STRING, VERSION).apply();
    }

    private static boolean isShippedName(String name) {
        for (String[] shipped : SHIPPED) {
            if (shipped[0].equals(name)) {
                return true;
            }
        }

        for (String retired : RETIRED_NAMES) {
            if (retired.equals(name)) {
                return true;
            }
        }

        return false;
    }

    /**
     * What a layout is called now, for one that was called something else before.
     */
    private static String replacementNameFor(String oldName) {
        if ("Keyboard".equals(oldName)) {
            return "PC Keyboard";
        }

        if ("Shooter".equals(oldName)) {
            return "Default (kbd+mouse)";
        }

        return oldName;
    }

    private static void addLayout(Context context, UniversalLayoutStore store, String name,
                                  String assetName) {
        try {
            UniversalLayoutExchange.Imported shipped = UniversalLayoutExchange.fromJson(
                    UniversalLayoutConfigurationLoader.readShippedAsset(context, assetName));

            UniversalLayoutDefinition layout = store.addLayout(name);

            UniversalLayoutConfigurationLoader.writeStoredElements(context, layout.getId(),
                    shipped.getElementsJson(), shipped.getSourceWidth(),
                    shipped.getSourceHeight());
        } catch (Exception e) {
            // A layout that cannot be read is simply not offered, which is better than a list of
            // layouts that fail to open
        }
    }
}
