/**
 * Universal layout: left analog stick.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;

import com.limelight.nvstream.input.ControllerPacket;

public class UniversalLeftAnalogStick extends UniversalAnalogStick {
    public UniversalLeftAnalogStick(final UniversalVirtualController controller, final Context context) {
        super(controller, context, EID_LS);

        addAnalogStickListener(new AnalogStickListener() {
            @Override
            public void onMovement(float x, float y) {
                UniversalVirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.leftStickX = (short) (x * 0x7FFE);
                inputContext.leftStickY = (short) (y * 0x7FFE);

                controller.sendControllerInputContext();
            }

            @Override
            public void onClick() {
            }

            @Override
            public void onDoubleClick() {
                UniversalVirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= ControllerPacket.LS_CLK_FLAG;

                controller.sendControllerInputContext();
            }

            @Override
            public void onRevoke() {
                UniversalVirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap &= ~ControllerPacket.LS_CLK_FLAG;

                controller.sendControllerInputContext();
            }
        });
    }
}
