/**
 * Universal layout: right trigger, reported as an analog trigger axis.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;

public class UniversalRightTrigger extends UniversalDigitalButton {
    public UniversalRightTrigger(final UniversalVirtualController controller, final int elementId,
                                 final int layer, final Context context) {
        super(controller, elementId, layer, context);
    }

    @Override
    public String getElementTypeName() {
        return UniversalElementTypes.ELEMENT_TRIGGER;
    }

    /**
     * A trigger is an analog axis on the host rather than a button, so its press is
     * reported through the trigger byte of the controller packet instead of a button
     * flag. What triggers it is described by its configuration like any other button.
     */
    @Override
    public void setGamepadButtons(int flagsToAdd, int flagsToRemove) {
        if (flagsToAdd != 0) {
            controller.getControllerInputContext().rightTrigger = (byte) 0xFF;
        }
        else if (flagsToRemove != 0) {
            controller.getControllerInputContext().rightTrigger = 0;
        }

        controller.sendControllerInputContext();
    }
}
