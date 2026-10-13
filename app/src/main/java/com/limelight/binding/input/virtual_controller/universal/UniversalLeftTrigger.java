/**
 * Universal layout: left trigger, reported as an analog trigger axis.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;

public class UniversalLeftTrigger extends UniversalDigitalButton {
    public UniversalLeftTrigger(final UniversalVirtualController controller, final int elementId,
                                final int layer, final Context context) {
        super(controller, elementId, layer, context);
    }

    @Override
    public String getElementTypeName() {
        return UniversalElementTypes.ELEMENT_TRIGGER;
    }

    /**
     * A trigger is an analog axis on the host rather than a button, so its press is reported
     * through the trigger byte of the controller packet instead of a button flag.
     *
     * The byte follows the control's own press and release, exactly as the built-in on-screen
     * trigger does, so a trigger works the moment it is on screen and does not need anything
     * configured for it. Its configuration is still free to send other inputs as well.
     */
    @Override
    protected void onPressedStateChanged(boolean pressed) {
        controller.getControllerInputContext().leftTrigger = (byte) (pressed ? 0xFF : 0);
        controller.sendControllerInputContext();
    }

    /**
     * A trigger driving its own axis directly, which is what a trigger event bound to any
     * control means.
     */
    @Override
    public void setTrigger(int trigger, int value) {
        if (trigger != com.limelight.binding.input.virtual_controller.universal.action.InputEvent.TRIGGER_LEFT) {
            return;
        }

        controller.getControllerInputContext().leftTrigger = (byte) value;
        controller.sendControllerInputContext();
    }
}
