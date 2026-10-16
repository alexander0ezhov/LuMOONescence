package com.limelight.binding.input.touch;

/**
 * The touchscreen does nothing.
 *
 * Touches are still swallowed rather than passed on, so a press that lands on the screen instead
 * of on a control of the on-screen layout has no effect at all. The three finger tap that brings
 * up the software keyboard is handled before the touch reaches here, so it keeps working.
 */
public class OffTouchContext implements TouchContext {
    private final int actionIndex;

    private boolean cancelled;

    public OffTouchContext(int actionIndex) {
        this.actionIndex = actionIndex;
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
        return true;
    }

    @Override
    public boolean touchMoveEvent(int eventX, int eventY, long eventTime) {
        return true;
    }

    @Override
    public void touchUpEvent(int eventX, int eventY, long eventTime) {
    }

    @Override
    public void cancelTouch() {
        cancelled = true;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }
}
