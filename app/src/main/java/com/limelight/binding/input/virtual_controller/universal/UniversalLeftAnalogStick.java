/**
 * Universal layout: left analog stick.
 *
 * The stick drives whatever its configuration says; the default is the Xbox left stick.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;

import com.limelight.binding.input.virtual_controller.universal.config.UniversalHostAxis;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalStickConfig;

public class UniversalLeftAnalogStick extends UniversalAnalogStick {
    public UniversalLeftAnalogStick(final UniversalVirtualController controller, final Context context) {
        super(controller, context, EID_LS);

        UniversalStickConfig config = getStickConfig();
        config.setAxisX(UniversalHostAxis.codeOf(UniversalHostAxis.KIND_GAMEPAD,
                UniversalHostAxis.TARGET_LEFT_X));
        config.setAxisY(UniversalHostAxis.codeOf(UniversalHostAxis.KIND_GAMEPAD,
                UniversalHostAxis.TARGET_LEFT_Y));
    }
}
