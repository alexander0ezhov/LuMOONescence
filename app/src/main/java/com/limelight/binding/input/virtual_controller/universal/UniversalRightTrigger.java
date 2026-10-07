/**
 * Universal layout: right trigger, reported as an analog trigger axis.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;

public class UniversalRightTrigger extends UniversalDigitalButton {
    public UniversalRightTrigger(final UniversalVirtualController controller, final int layer, final Context context) {
        super(controller, EID_RT, layer, context);
        addDigitalButtonListener(new DigitalButtonListener() {
            @Override
            public void onClick() {
                UniversalVirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.rightTrigger = (byte) 0xFF;

                controller.sendControllerInputContext();
            }

            @Override
            public void onLongClick() {
            }

            @Override
            public void onRelease() {
                UniversalVirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.rightTrigger = (byte) 0x00;

                controller.sendControllerInputContext();
            }
        });
    }
}
