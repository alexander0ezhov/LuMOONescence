package com.limelight.preferences;

import android.content.Context;
import android.util.AttributeSet;

import com.limelight.R;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutDefinition;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutStore;

/**
 * Resets the geometry of every universal layout back to its default arrangement,
 * without touching the layout saved by the legacy OSC layout.
 */
public class ConfirmDeleteUniversalLayoutPreference extends ConfirmDeleteOscPreference {
    public ConfirmDeleteUniversalLayoutPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ConfirmDeleteUniversalLayoutPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ConfirmDeleteUniversalLayoutPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ConfirmDeleteUniversalLayoutPreference(Context context) {
        super(context);
    }

    @Override
    protected int getResetSuccessMessage() {
        return R.string.toast_reset_universal_layout_success;
    }

    @Override
    protected void clearSavedGeometry() {
        // Every layout keeps its geometry in its own file, so all of them are cleared.
        // The layouts themselves and their artwork are left alone.
        for (UniversalLayoutDefinition layout : UniversalLayoutStore.getInstance(getContext()).getLayouts()) {
            getContext().deleteSharedPreferences(layout.getPreferenceFileName());
        }
    }
}
