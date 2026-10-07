package com.limelight.preferences;

import android.content.Context;
import android.util.AttributeSet;

import com.limelight.R;

import static com.limelight.binding.input.virtual_controller.universal.UniversalLayoutConfigurationLoader.UNIVERSAL_LAYOUT_PREFERENCE;

/**
 * Clears the saved geometry of the universal layout without touching the layout saved
 * by the legacy OSC layout.
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
    protected String getPreferencesName() {
        return UNIVERSAL_LAYOUT_PREFERENCE;
    }

    @Override
    protected int getResetSuccessMessage() {
        return R.string.toast_reset_universal_layout_success;
    }
}
