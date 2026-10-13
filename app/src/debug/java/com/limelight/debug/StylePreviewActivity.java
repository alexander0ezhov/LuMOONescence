package com.limelight.debug;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.limelight.binding.input.virtual_controller.universal.UniversalAnalogStick;
import com.limelight.binding.input.virtual_controller.universal.UniversalDigitalButton;
import com.limelight.binding.input.virtual_controller.universal.UniversalLeftAnalogStick;
import com.limelight.binding.input.virtual_controller.universal.UniversalVirtualController;

/**
 * A screen that shows the controls in each of their states, so the look of a layout can be
 * looked at without a stream.
 *
 * A press cannot be injected on this device, so the states are forced here instead and one
 * screenshot shows all of them: a plain button, a pressed button, a latched button, a round
 * button and a pressed stick.
 */
public class StylePreviewActivity extends Activity {
    private int nextElementId = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // A controller with no handler: the controls draw and respond, but nothing is sent
        UniversalVirtualController controller =
                new UniversalVirtualController(null, root, this, "style-preview");

        int cell = Math.min(height / 4, width / 6);
        int margin = cell / 6;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        UniversalDigitalButton normal = addButton(controller, row, "Normal", cell, margin, 18);
        final UniversalDigitalButton pressed = addButton(controller, row, "Pressed", cell, margin, 18);
        final UniversalDigitalButton latched = addButton(controller, row, "Toggled", cell, margin, 18);
        final UniversalDigitalButton round = addButton(controller, row, "Round", cell, margin, 50);

        root.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout second = new LinearLayout(this);
        second.setOrientation(LinearLayout.HORIZONTAL);
        second.setGravity(Gravity.CENTER_VERTICAL);

        final UniversalAnalogStick stick = addStick(controller, second, cell, margin);

        FrameLayout.LayoutParams secondParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        secondParams.topMargin = cell + margin * 4;
        root.addView(second, secondParams);

        setContentView(root);

        // The states are forced once the controls have been measured, because a control draws
        // itself from its own size
        root.postDelayed(new Runnable() {
            @Override
            public void run() {
                pressed.setPressed(true);
                pressed.invalidate();

                // A latched button is on until it is tapped again
                latched.getButtonConfig().setToggle(true);
                latched.setPressed(true);
                latched.invalidate();

                round.setPressed(true);
                round.invalidate();

                stick.setPressed(true);
                stick.invalidate();
            }
        }, 300);
    }

    private UniversalDigitalButton addButton(UniversalVirtualController controller,
                                             LinearLayout row, String label, int size,
                                             int margin, int radiusPercent) {
        UniversalDigitalButton button = new UniversalDigitalButton(controller, nextElementId++, 0, this);

        button.getConfig().setName(label);
        button.getConfig().setBorderRadius(radiusPercent);

        addToRow(row, button, size, margin);
        return button;
    }

    private UniversalAnalogStick addStick(UniversalVirtualController controller, LinearLayout row,
                                          int size, int margin) {
        UniversalAnalogStick stick = new UniversalLeftAnalogStick(controller, this);

        addToRow(row, stick, size, margin);
        return stick;
    }

    /**
     * Puts a control of a fixed size inside the row, so the preview does not depend on the
     * layout rules of a stream.
     */
    private void addToRow(LinearLayout row, View control, int size, int margin) {
        FrameLayout box = new FrameLayout(this);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.leftMargin = margin;
        params.topMargin = margin;
        box.addView(control, params);

        row.addView(box, new LinearLayout.LayoutParams(size + margin * 2, size + margin * 2));
    }
}
