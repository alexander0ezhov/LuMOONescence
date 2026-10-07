/**
 * Universal layout: right analog stick.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;

import com.limelight.nvstream.input.ControllerPacket;

public class UniversalRightAnalogStick extends UniversalAnalogStick {
    public UniversalRightAnalogStick(final UniversalVirtualController controller, final Context context) {
        super(controller, context, EID_RS);

        addAnalogStickListener(new AnalogStickListener() {
            @Override
            public void onMovement(float x, float y) {
                UniversalVirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.rightStickX = (short) (x * 0x7FFE);
                inputContext.rightStickY = (short) (y * 0x7FFE);

                controller.sendControllerInputContext();
            }

            @Override
            public void onClick() {
            }

            @Override
            public void onDoubleClick() {
                UniversalVirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= ControllerPacket.RS_CLK_FLAG;

                controller.sendControllerInputContext();
            }

            @Override
            public void onRevoke() {
                UniversalVirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap &= ~ControllerPacket.RS_CLK_FLAG;

                controller.sendControllerInputContext();
            }
        });
    }
}
