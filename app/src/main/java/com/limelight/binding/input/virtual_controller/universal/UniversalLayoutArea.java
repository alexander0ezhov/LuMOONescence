package com.limelight.binding.input.virtual_controller.universal;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

/**
 * How far a control may be placed inside the layout.
 *
 * A control that is pushed past the edge of the screen cannot be pressed any more, so it cannot
 * be dragged back either: the editor would have lost it for good. Every way of placing a
 * control - dragging it, a slider, a typed value - is held back the same amount, which is
 * enough of the control to put a finger on.
 */
public final class UniversalLayoutArea {
    private UniversalLayoutArea() {
    }

    /**
     * How much of a control has to stay inside the layout.
     *
     * It is a finger's worth for a big control and the whole control for one smaller than that,
     * so what is left is always something that can actually be pressed.
     */
    public static int visibleShare(View element, int size) {
        int finger = (int)(element.getResources().getDisplayMetrics().density * MIN_VISIBLE_DP);

        return Math.max(1, Math.min(size, finger));
    }

    /**
     * A finger is about this wide, in density independent pixels.
     */
    private static final float MIN_VISIBLE_DP = 28f;

    /**
     * The furthest right a control of this width may be placed.
     */
    public static int maxLeft(View element, int width) {
        return Math.max(0, areaWidth(element) - visibleShare(element, width));
    }

    public static int maxTop(View element, int height) {
        return Math.max(0, areaHeight(element) - visibleShare(element, height));
    }

    /**
     * The width of the area controls are placed in, which is the layout they are children of.
     * It is read from the control's own parent, so it is whatever the screen actually is.
     */
    public static int areaWidth(View element) {
        int fromParent = sizeOfParent(element, true);

        if (fromParent > 0) {
            return fromParent;
        }

        return element.getResources().getDisplayMetrics().widthPixels;
    }

    public static int areaHeight(View element) {
        int fromParent = sizeOfParent(element, false);

        if (fromParent > 0) {
            return fromParent;
        }

        return element.getResources().getDisplayMetrics().heightPixels;
    }

    private static int sizeOfParent(View element, boolean width) {
        ViewParent parent = element.getParent();

        if (!(parent instanceof View)) {
            return 0;
        }

        View view = (View) parent;

        // The padding is where controls may not go, so it comes off the area
        return width
                ? view.getWidth() - view.getPaddingLeft() - view.getPaddingRight()
                : view.getHeight() - view.getPaddingTop() - view.getPaddingBottom();
    }

    /**
     * Keeps a control inside the layout, whichever way its position was set.
     */
    public static int clampLeft(View element, int left, int width) {
        return Math.max(0, Math.min(left, maxLeft(element, width)));
    }

    public static int clampTop(View element, int top, int height) {
        return Math.max(0, Math.min(top, maxTop(element, height)));
    }

    /**
     * A view group that lays controls out by their margins, which is the layout area.
     */
    public static boolean isLayoutArea(ViewParent parent) {
        return parent instanceof ViewGroup;
    }
}
