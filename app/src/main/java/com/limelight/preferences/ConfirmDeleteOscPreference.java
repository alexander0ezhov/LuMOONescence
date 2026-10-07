package com.limelight.preferences;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.widget.Toast;

import com.limelight.R;

import static com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader.OSC_PREFERENCE;

/**
 * Clears the saved geometry of a layout. Subclasses only need to override
 * {@link #getPreferencesName()} to target a different layout.
 */
public class ConfirmDeleteOscPreference extends DialogPreference {
    public ConfirmDeleteOscPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ConfirmDeleteOscPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ConfirmDeleteOscPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ConfirmDeleteOscPreference(Context context) {
        super(context);
    }

    /**
     * Name of the preferences file that holds the geometry to clear.
     */
    protected String getPreferencesName() {
        return OSC_PREFERENCE;
    }

    /**
     * Message shown after the geometry has been cleared.
     */
    protected int getResetSuccessMessage() {
        return R.string.toast_reset_osc_success;
    }

    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            getContext().getSharedPreferences(getPreferencesName(), Context.MODE_PRIVATE).edit().clear().apply();
            Toast.makeText(getContext(), getResetSuccessMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
