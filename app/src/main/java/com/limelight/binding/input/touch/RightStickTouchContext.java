package com.limelight.binding.input.touch;

import android.view.View;

import com.limelight.binding.input.ControllerHandler;

/**
 * The touchscreen drives the right analog stick.
 *
 * The stick appears wherever the finger lands rather than at a fixed place on the screen, which
 * is what makes it usable without looking: the finger that started the touch is the centre of the
 * stick, and dragging away from that centre pushes the stick in that direction. Letting go
 * returns the stick to the middle, so a character stops turning.
 */
public class RightStickTouchContext implements TouchContext {
    /**
     * How far the finger has to travel from where it landed for the stick to be pushed all the
     * way over, as a share of the shorter side of the screen.
     */
    private static final float FULL_TRAVEL_SHARE = 0.18f;

    private static final int AXIS_MAX = 32767;

    private final ControllerHandler controllerHandler;
    private final int actionIndex;
    private final float fullTravel;

    private int originX;
    private int originY;

    private boolean cancelled;

    public RightStickTouchContext(ControllerHandler controllerHandler, int actionIndex,
                                  View targetView) {
        this.controllerHandler = controllerHandler;
        this.actionIndex = actionIndex;

        int shorter = Math.min(targetView.getWidth(), targetView.getHeight());

        // Before the view has been measured there is nothing to measure against, so the size of
        // the screen stands in until the first touch, which is when it will have been laid out
        if (shorter <= 0) {
            shorter = Math.min(targetView.getResources().getDisplayMetrics().widthPixels,
                    targetView.getResources().getDisplayMetrics().heightPixels);
        }

        this.fullTravel = Math.max(1f, shorter * FULL_TRAVEL_SHARE);
    }

    @Override
    public int getActionIndex() {
        return actionIndex;
    }

    @Override
    public void setPointerCount(int pointerCount) {
    }

    @Override
    public boolean touchDownEvent(int eventX, int eventY, long eventTime, boolean isNewFinger) {
        cancelled = false;

        originX = eventX;
        originY = eventY;

        // A new touch starts at the middle, so the stick does not jump to wherever the previous
        // finger left it
        reportStick((short) 0, (short) 0);
        return true;
    }

    @Override
    public boolean touchMoveEvent(int eventX, int eventY, long eventTime) {
        float dx = eventX - originX;
        float dy = eventY - originY;

        float distance = (float) Math.hypot(dx, dy);

        if (distance <= 0) {
            reportStick((short) 0, (short) 0);
            return true;
        }

        // The stick reaches all the way over once the finger is far enough away, and no further
        float reach = Math.min(1f, distance / fullTravel);

        // Screen coordinates grow downwards while the stick reports upwards as positive, so the
        // vertical value is turned around
        short axisX = (short) Math.round(dx / distance * reach * AXIS_MAX);
        short axisY = (short) Math.round(-dy / distance * reach * AXIS_MAX);

        reportStick(axisX, axisY);
        return true;
    }

    @Override
    public void touchUpEvent(int eventX, int eventY, long eventTime) {
        reportStick((short) 0, (short) 0);
    }

    @Override
    public void cancelTouch() {
        cancelled = true;
        reportStick((short) 0, (short) 0);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    private void reportStick(short x, short y) {
        // Only the right stick is claimed; the buttons and triggers the on-screen layout is
        // sending are left alone, so a held control keeps firing while the view turns
        controllerHandler.reportOscRightStick(x, y);
    }
}
