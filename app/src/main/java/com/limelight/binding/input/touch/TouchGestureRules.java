package com.limelight.binding.input.touch;

/**
 * What a touch on the trackpad means.
 *
 * The rules are here rather than inside the touch handler so they can be read and checked on
 * their own: a tap is a click, two fingers are the right button, and a second tap that is held
 * drags.
 *
 * The numbers are deliberately generous for more than one finger. Two fingers never land as
 * still as one, and a check that demanded the same of them is why a two-finger tap only
 * sometimes produced a right click.
 */
public final class TouchGestureRules {
    private TouchGestureRules() {
    }

    /**
     * How far a finger may wander and still count as a tap.
     */
    public static final int TAP_MOVEMENT_THRESHOLD_PX = 20;

    /**
     * How far a finger may travel in total and still count as a tap.
     */
    public static final int TAP_DISTANCE_THRESHOLD_PX = 25;

    public static final int TAP_TIME_THRESHOLD_MS = 250;

    /**
     * A tap with more than one finger is allowed to take a little longer, because the fingers
     * are lifted one after the other.
     */
    public static final int MULTI_FINGER_TAP_TIME_THRESHOLD_MS = 320;

    /**
     * The extra room a two-finger tap is given, since two fingers cannot be as still as one.
     */
    public static final int MULTI_FINGER_SLACK_PX = 12;

    /**
     * How long a finger has to stay still before holding becomes a drag with the button down.
     */
    public static final int HOLD_TO_DRAG_MS = 450;

    /**
     * How soon after a tap a second touch counts as the start of a drag.
     *
     * Tap, then tap again and move: the second touch drags straight away, rather than the user
     * having to hold still first. A quick second tap that does not move is still just a click,
     * because the button goes down and straight back up again.
     */
    public static final int SECOND_TAP_DRAG_WINDOW_MS = 600;

    /**
     * The time a gesture of this many fingers has to complete in to be a tap.
     */
    public static int tapTimeLimitMs(int maxFingersSeen) {
        return maxFingersSeen >= 2 ? MULTI_FINGER_TAP_TIME_THRESHOLD_MS : TAP_TIME_THRESHOLD_MS;
    }

    /**
     * The extra room a gesture of this many fingers is given.
     */
    public static int slackPx(int maxFingersSeen) {
        return maxFingersSeen >= 2 ? MULTI_FINGER_SLACK_PX : 0;
    }

    /**
     * Whether letting go now ends a gesture that counts as a tap.
     *
     * @param fingersStillDown how many fingers are on the screen, counting the one letting go.
     *                         A tap is only decided by the finger that lifts last, so a gesture
     *                         of several fingers is one click rather than one per finger.
     */
    public static boolean isTap(int maxFingersSeen, int fingersStillDown,
                                boolean moved, boolean dragged, boolean scrolled,
                                int movementPx, int distancePx, long timeMs) {
        if (moved || dragged || scrolled) {
            return false;
        }

        // Another finger is still down, so this is not the end of the gesture
        if (fingersStillDown > 1) {
            return false;
        }

        int slack = slackPx(maxFingersSeen);

        return movementPx <= TAP_MOVEMENT_THRESHOLD_PX + slack
                && distancePx <= TAP_DISTANCE_THRESHOLD_PX + slack
                && timeMs <= tapTimeLimitMs(maxFingersSeen);
    }

    /**
     * Whether a tap of this gesture is the right button.
     */
    public static boolean isRightButton(int maxFingersSeen, int actionIndex) {
        return maxFingersSeen >= 2 || actionIndex == 1;
    }

    /**
     * Whether a touch that comes this soon after a tap should begin dragging at once.
     */
    public static boolean startsDragWithSecondTap(long sinceLastTapMs) {
        return sinceLastTapMs >= 0 && sinceLastTapMs <= SECOND_TAP_DRAG_WINDOW_MS;
    }
}
