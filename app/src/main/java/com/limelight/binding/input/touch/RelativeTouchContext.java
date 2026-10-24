package com.limelight.binding.input.touch;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.preferences.PreferenceConfiguration;

public class RelativeTouchContext implements TouchContext {
    private int lastTouchX = 0;
    private int lastTouchY = 0;
    private int originalTouchX = 0;
    private int originalTouchY = 0;
    private long originalTouchTime = 0;
    private boolean cancelled;
    private boolean confirmedMove;
    private boolean confirmedDrag;
    private boolean confirmedScroll;
    private double distanceMoved;
    private double xFactor, yFactor;
    private int pointerCount;
    private int maxPointerCountInGesture;

    private final NvConnection conn;
    private final int actionIndex;
    private final int referenceWidth;
    private final int referenceHeight;
    private final View targetView;
    private final PreferenceConfiguration prefConfig;
    private final Handler handler;

    private final Runnable dragTimerRunnable = new Runnable() {
        @Override
        public void run() {
            // Check if someone already set move
            if (confirmedMove) {
                return;
            }

            // The drag should only be processed for the primary finger
            if (actionIndex != maxPointerCountInGesture - 1) {
                return;
            }

            // We haven't been cancelled before the timer expired so begin dragging
            confirmedDrag = true;
            cancelPendingReleases();
            conn.sendMouseButtonDown(getMouseButtonIndex());

            android.util.Log.i("Trackpad", "held still starts a drag");
        }
    };

    // Indexed by MouseButtonPacket.BUTTON_XXX - 1
    private final Runnable[] buttonUpRunnables = new Runnable[] {
            new Runnable() {
                @Override
                public void run() {
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                }
            },
            new Runnable() {
                @Override
                public void run() {
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE);
                }
            },
            new Runnable() {
                @Override
                public void run() {
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
                }
            },
            new Runnable() {
                @Override
                public void run() {
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X1);
                }
            },
            new Runnable() {
                @Override
                public void run() {
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X2);
                }
            }
    };

    /**
     * How long a finger has to stay still before holding becomes a drag, from the shared rules.
     */
    private static final int DRAG_TIME_THRESHOLD = TouchGestureRules.HOLD_TO_DRAG_MS;

    private static final int SCROLL_SPEED_FACTOR = 5;

    /**
     * Whether a touch may press a mouse button. Aiming with a finger that is already holding a
     * control must not fire, so that case turns this off.
     */
    private final boolean clicksEnabled;

    /**
     * When the last tap of this finger finished, so a touch right behind it can drag.
     */
    private long lastTapTime = -1;

    public RelativeTouchContext(NvConnection conn, int actionIndex,
                                int referenceWidth, int referenceHeight,
                                View view, PreferenceConfiguration prefConfig)
    {
        this(conn, actionIndex, referenceWidth, referenceHeight, view, prefConfig, true);
    }

    public RelativeTouchContext(NvConnection conn, int actionIndex,
                                int referenceWidth, int referenceHeight,
                                View view, PreferenceConfiguration prefConfig,
                                boolean clicksEnabled)
    {
        this.conn = conn;
        this.actionIndex = actionIndex;
        this.referenceWidth = referenceWidth;
        this.referenceHeight = referenceHeight;
        this.targetView = view;
        this.prefConfig = prefConfig;
        this.clicksEnabled = clicksEnabled;
        this.handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int getActionIndex()
    {
        return actionIndex;
    }

    private boolean isWithinTapBounds(int touchX, int touchY)
    {
        int slack = TouchGestureRules.slackPx(maxPointerCountInGesture);
        int xDelta = Math.abs(touchX - originalTouchX);
        int yDelta = Math.abs(touchY - originalTouchY);

        return xDelta <= TouchGestureRules.TAP_MOVEMENT_THRESHOLD_PX + slack &&
                yDelta <= TouchGestureRules.TAP_MOVEMENT_THRESHOLD_PX + slack;
    }

    private boolean isTap(long eventTime)
    {
        // The finger that lifts last is the one that ends the gesture. Deciding the tap that
        // way, rather than by which finger went down last, is what makes a two-finger tap one
        // right click however the fingers are lifted.
        return TouchGestureRules.isTap(maxPointerCountInGesture, pointerCount,
                confirmedMove, confirmedDrag, confirmedScroll,
                Math.max(Math.abs(lastTouchX - originalTouchX),
                        Math.abs(lastTouchY - originalTouchY)),
                (int) Math.round(distanceMoved),
                eventTime - originalTouchTime);
    }

    private byte getMouseButtonIndex()
    {
        if (TouchGestureRules.isRightButton(maxPointerCountInGesture, actionIndex)) {
            return MouseButtonPacket.BUTTON_RIGHT;
        }
        else {
            return MouseButtonPacket.BUTTON_LEFT;
        }
    }

    @Override
    public boolean touchDownEvent(int eventX, int eventY, long eventTime, boolean isNewFinger)
    {
        // Get the view dimensions to scale inputs on this touch
        xFactor = referenceWidth / (double)targetView.getWidth();
        yFactor = referenceHeight / (double)targetView.getHeight();

        originalTouchX = lastTouchX = eventX;
        originalTouchY = lastTouchY = eventY;

        if (isNewFinger) {
            maxPointerCountInGesture = pointerCount;
            originalTouchTime = eventTime;
            cancelled = confirmedDrag = confirmedMove = confirmedScroll = false;
            distanceMoved = 0;

            if (actionIndex == 0) {
                if (clicksEnabled && TouchGestureRules.startsDragWithSecondTap(
                        eventTime - lastTapTime)) {
                    // A second tap close behind the first starts dragging at once: the button
                    // goes down and the finger carries it, so tap-then-drag selects and drags
                    // rather than needing a long hold first
                    confirmedDrag = true;

                    // Nothing may let go of the button that is being pressed now
                    cancelPendingReleases();
                    conn.sendMouseButtonDown(getMouseButtonIndex());

                    android.util.Log.i("Trackpad", "second tap after " + (eventTime - lastTapTime)
                            + "ms starts a drag");
                }
                else {
                    // Start the timer for engaging a drag
                    startDragTimer();
                }
            }
        }

        return true;
    }

    @Override
    public void touchUpEvent(int eventX, int eventY, long eventTime)
    {
        if (cancelled) {
            return;
        }

        // Cancel the drag timer
        cancelDragTimer();

        byte buttonIndex = getMouseButtonIndex();

        if (confirmedDrag) {
            // Raise the button after a drag
            if (clicksEnabled) {
                conn.sendMouseButtonUp(buttonIndex);
            }
        }
        else if (clicksEnabled && isTap(eventTime))
        {
            lastTapTime = eventTime;

            android.util.Log.i("Trackpad", "tap becomes a click");

            // Lower the mouse button
            conn.sendMouseButtonDown(buttonIndex);

            // Release the mouse button in 100ms to allow for apps that use polling
            // to detect mouse button presses.
            Runnable buttonUpRunnable = buttonUpRunnables[buttonIndex - 1];
            handler.removeCallbacks(buttonUpRunnable);
            handler.postDelayed(buttonUpRunnable, 100);
        }
    }

    /**
     * Drops the release that follows a tap.
     *
     * A tap lets go of the button a moment later, so that a host which polls for presses still
     * sees one. If the next touch begins inside that moment, the release would otherwise arrive
     * in the middle of it and end the drag as soon as it started.
     */
    private void cancelPendingReleases() {
        for (Runnable buttonUp : buttonUpRunnables) {
            handler.removeCallbacks(buttonUp);
        }
    }

    private void startDragTimer() {
        cancelDragTimer();

        if (!clicksEnabled) {
            return;
        }

        handler.postDelayed(dragTimerRunnable, DRAG_TIME_THRESHOLD);
    }

    private void cancelDragTimer() {
        handler.removeCallbacks(dragTimerRunnable);
    }

    private void checkForConfirmedMove(int eventX, int eventY) {
        // If we've already confirmed something, get out now
        if (confirmedMove || confirmedDrag) {
            return;
        }

        // If it leaves the tap bounds before the drag time expires, it's a move.
        if (!isWithinTapBounds(eventX, eventY)) {
            confirmedMove = true;
            cancelDragTimer();
            return;
        }

        // Check if we've exceeded the maximum distance moved
        distanceMoved += Math.sqrt(Math.pow(eventX - lastTouchX, 2) + Math.pow(eventY - lastTouchY, 2));
        if (distanceMoved >= TouchGestureRules.TAP_DISTANCE_THRESHOLD_PX
                + TouchGestureRules.slackPx(maxPointerCountInGesture)) {
            confirmedMove = true;
            cancelDragTimer();
            return;
        }
    }

    private void checkForConfirmedScroll() {
        // Enter scrolling mode if we've already left the tap zone
        // and we have 2 fingers on screen. Leave scroll mode if
        // we no longer have 2 fingers on screen
        confirmedScroll = (actionIndex == 0 && pointerCount == 2 && confirmedMove);
    }

    @Override
    public boolean touchMoveEvent(int eventX, int eventY, long eventTime)
    {
        if (cancelled) {
            return true;
        }

        if (eventX != lastTouchX || eventY != lastTouchY)
        {
            checkForConfirmedMove(eventX, eventY);
            checkForConfirmedScroll();

            // We only send moves and drags for the primary touch point
            if (actionIndex == 0) {
                int deltaX = eventX - lastTouchX;
                int deltaY = eventY - lastTouchY;

                // Scale the deltas based on the factors passed to our constructor
                deltaX = (int) Math.round((double) Math.abs(deltaX) * xFactor);
                deltaY = (int) Math.round((double) Math.abs(deltaY) * yFactor);

                // Fix up the signs
                if (eventX < lastTouchX) {
                    deltaX = -deltaX;
                }
                if (eventY < lastTouchY) {
                    deltaY = -deltaY;
                }

                if (pointerCount == 2) {
                    if (confirmedScroll) {
                        conn.sendMouseHighResScroll((short)(deltaY * SCROLL_SPEED_FACTOR));
                    }
                } else {
                    if (prefConfig.absoluteMouseMode) {
                        conn.sendMouseMoveAsMousePosition(
                                (short) deltaX,
                                (short) deltaY,
                                (short) targetView.getWidth(),
                                (short) targetView.getHeight());
                    }
                    else {
                        conn.sendMouseMove((short) deltaX, (short) deltaY);
                    }
                }

                // If the scaling factor ended up rounding deltas to zero, wait until they are
                // non-zero to update lastTouch that way devices that report small touch events often
                // will work correctly
                if (deltaX != 0) {
                    lastTouchX = eventX;
                }
                if (deltaY != 0) {
                    lastTouchY = eventY;
                }
            }
            else {
                lastTouchX = eventX;
                lastTouchY = eventY;
            }
        }

        return true;
    }

    @Override
    public void cancelTouch() {
        cancelled = true;

        // Cancel the drag timer
        cancelDragTimer();

        // If it was a confirmed drag, we'll need to raise the button now
        if (confirmedDrag && clicksEnabled) {
            conn.sendMouseButtonUp(getMouseButtonIndex());
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setPointerCount(int pointerCount) {
        this.pointerCount = pointerCount;

        if (pointerCount > maxPointerCountInGesture) {
            maxPointerCountInGesture = pointerCount;
        }
    }
}
