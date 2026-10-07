/**
 * Universal layout: left trigger, reported as an analog trigger axis.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;

public class UniversalLeftTrigger extends UniversalDigitalButton {
    public UniversalLeftTrigger(final UniversalVirtualController controller, final int layer, final Context context) {
        super(controller, EID_LT, layer, context);
        addDigitalButtonListener(new DigitalButtonListener() {
            @Override
            public void onClick() {
                UniversalVirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.leftTrigger = (byte) 0xFF;

                controller.sendControllerInputContext();
            }

            @Override
            public void onLongClick() {
            }

            @Override
            public void onRelease() {
                UniversalVirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.leftTrigger = (byte) 0x00;

                controller.sendControllerInputContext();
            }
        });
    }
}
