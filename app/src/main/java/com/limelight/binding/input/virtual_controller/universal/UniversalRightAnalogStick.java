/**
 * Universal layout: right analog stick.
 *
 * The stick drives whatever its configuration says; the default is the Xbox right stick.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;

import com.limelight.binding.input.virtual_controller.universal.config.UniversalHostAxis;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalStickConfig;

public class UniversalRightAnalogStick extends UniversalAnalogStick {
    public UniversalRightAnalogStick(final UniversalVirtualController controller, final Context context) {
        super(controller, context, EID_RS);

        UniversalStickConfig config = getStickConfig();
        config.setAxisX(UniversalHostAxis.codeOf(UniversalHostAxis.KIND_GAMEPAD,
                UniversalHostAxis.TARGET_RIGHT_X));
        config.setAxisY(UniversalHostAxis.codeOf(UniversalHostAxis.KIND_GAMEPAD,
                UniversalHostAxis.TARGET_RIGHT_Y));
    }
}
