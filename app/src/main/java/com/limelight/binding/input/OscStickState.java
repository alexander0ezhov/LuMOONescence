package com.limelight.binding.input;

/**
 * The sticks of one packet, filled from two sources.
 *
 * The on-screen layout sends its whole state on every press, and the touch input sends only the
 * stick it is pushing. Sending one source as if it were the whole packet made each of them wipe
 * the other: a finger looking around stopped the moment another control was touched, and a stick
 * of the layout jumped back to the middle whenever the touch input reported.
 *
 * So each source keeps its own values, and the packet is put together from both here. The touch
 * input wins for the stick it is pushing, because that is the one the finger is on; the layout
 * speaks for the sticks nothing else is claiming.
 */
public class OscStickState {
    private short touchLeftX, touchLeftY, touchRightX, touchRightY;
    private boolean touchLeftActive, touchRightActive;

    public void setTouchLeft(short x, short y) {
        touchLeftX = x;
        touchLeftY = y;
        touchLeftActive = x != 0 || y != 0;
    }

    public void setTouchRight(short x, short y) {
        touchRightX = x;
        touchRightY = y;
        touchRightActive = x != 0 || y != 0;
    }

    public short mergeLeftX(short fromLayout) {
        return touchLeftActive ? touchLeftX : fromLayout;
    }

    public short mergeLeftY(short fromLayout) {
        return touchLeftActive ? touchLeftY : fromLayout;
    }

    public short mergeRightX(short fromLayout) {
        return touchRightActive ? touchRightX : fromLayout;
    }

    public short mergeRightY(short fromLayout) {
        return touchRightActive ? touchRightY : fromLayout;
    }

    /**
     * Whether the touch input is pushing a stick at all.
     */
    public boolean isTouchLeftActive() {
        return touchLeftActive;
    }

    public boolean isTouchRightActive() {
        return touchRightActive;
    }
}
