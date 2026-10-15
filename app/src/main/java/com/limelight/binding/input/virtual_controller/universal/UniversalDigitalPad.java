/**
 * Universal layout: digital pad element.
 *
 * Each direction sends whatever its configuration says, so a pad can drive gamepad
 * buttons, keyboard keys or a mix of them, just like a plain button can.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

import com.limelight.binding.input.virtual_controller.universal.action.ActionSequence;
import com.limelight.binding.input.virtual_controller.universal.action.ActionSequencePlayer;
import com.limelight.binding.input.virtual_controller.universal.action.GamepadButtons;
import com.limelight.binding.input.virtual_controller.universal.action.InputEvent;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalElementConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalPadConfig;
import com.limelight.nvstream.input.ControllerPacket;

import java.util.ArrayList;
import java.util.List;

public class UniversalDigitalPad extends UniversalVirtualControllerElement
        implements ActionSequencePlayer.HostInput {

    public final static int DIGITAL_PAD_DIRECTION_NO_DIRECTION = 0;
    int direction = DIGITAL_PAD_DIRECTION_NO_DIRECTION;
    public final static int DIGITAL_PAD_DIRECTION_LEFT = 1;
    public final static int DIGITAL_PAD_DIRECTION_UP = 2;
    public final static int DIGITAL_PAD_DIRECTION_RIGHT = 4;
    public final static int DIGITAL_PAD_DIRECTION_DOWN = 8;

    /**
     * Number of independently bound directions of the pad.
     */
    public static final int DIRECTION_COUNT = UniversalPadConfig.DIRECTION_COUNT;

    /**
     * Flag of each direction, in the order the configuration stores them, so the drawing
     * code and the configuration agree on which direction is which.
     */
    private static final int[] DIRECTION_FLAGS = new int[]{
            DIGITAL_PAD_DIRECTION_UP,
            DIGITAL_PAD_DIRECTION_DOWN,
            DIGITAL_PAD_DIRECTION_LEFT,
            DIGITAL_PAD_DIRECTION_RIGHT,
    };

    private static final int[] DIRECTION_GAMEPAD_FLAGS = new int[]{
            ControllerPacket.UP_FLAG,
            ControllerPacket.DOWN_FLAG,
            ControllerPacket.LEFT_FLAG,
            ControllerPacket.RIGHT_FLAG,
    };

    List<DigitalPadListener> listeners = new ArrayList<>();


    private final Paint paint = new Paint();

    /**
     * One player per direction, so every direction releases exactly what it pressed.
     */
    private final ActionSequencePlayer[] directionPlayers = new ActionSequencePlayer[UniversalPadConfig.DIRECTION_COUNT];
    private final boolean[] directionPressed = new boolean[UniversalPadConfig.DIRECTION_COUNT];

    public UniversalDigitalPad(UniversalVirtualController controller, Context context, int elementId) {
        super(controller, context, elementId);

        for (int i = 0; i < directionPlayers.length; i++) {
            directionPlayers[i] = new ActionSequencePlayer(this);
        }
    }

    @Override
    public String getElementTypeName() {
        return UniversalElementTypes.ELEMENT_DPAD;
    }

    @Override
    protected UniversalElementConfig createDefaultConfig() {
        return new UniversalPadConfig();
    }

    public UniversalPadConfig getPadConfig() {
        return (UniversalPadConfig) getConfig();
    }

    /**
     * Builds the default action of a direction, which is the matching D-pad button.
     */
    public static ActionSequence defaultSequenceForDirection(int direction) {
        return ActionSequence.single(new InputEvent(InputEvent.KIND_GAMEPAD,
                GamepadButtons.codeOfFlag(DIRECTION_GAMEPAD_FLAGS[direction])));
    }

    public void addDigitalPadListener(DigitalPadListener listener) {
        listeners.add(listener);
    }

    /**
     * Which directions a point activates.
     *
     * The direction a press belongs to is the one it leans towards most, which is what keeps a
     * press on one arm from also pressing the arm beside it. A second direction is added only
     * when the press leans almost equally towards both, which is the corner where a diagonal
     * belongs, and the middle of the pad is left empty because there is no button there.
     */
    private int directionAt(float x, float y) {
        float unit = Math.min(getWidth(), getHeight());

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        float dx = x - centerX;
        float dy = y - centerY;

        // The empty middle does not press anything
        if (Math.hypot(dx, dy) <= unit * TIP_GAP_SHARE) {
            return 0;
        }

        // A press that is not on the cross at all presses nothing. The bands are the two bars
        // of the cross, each widened by the reach that makes the pad comfortable to use.
        float halfWidth = unit * ARM_HALF_WIDTH_SHARE;
        float armEnd = unit * (TIP_GAP_SHARE + TAPER_SHARE + ARM_SHARE);
        float reach = unit * TOUCH_REACH_SHARE;

        boolean onVerticalBar = Math.abs(dx) <= halfWidth + reach
                && Math.abs(dy) <= armEnd + reach;
        boolean onHorizontalBar = Math.abs(dy) <= halfWidth + reach
                && Math.abs(dx) <= armEnd + reach;

        if (!onVerticalBar && !onHorizontalBar) {
            return 0;
        }

        float major = Math.max(Math.abs(dx), Math.abs(dy));
        float minor = Math.min(Math.abs(dx), Math.abs(dy));

        boolean leaningSideways = Math.abs(dx) > Math.abs(dy);

        int direction = leaningSideways
                ? (dx > 0 ? DIGITAL_PAD_DIRECTION_RIGHT : DIGITAL_PAD_DIRECTION_LEFT)
                : (dy > 0 ? DIGITAL_PAD_DIRECTION_DOWN : DIGITAL_PAD_DIRECTION_UP);

        // The corner between two directions, where a diagonal is meant to be
        if (minor >= major * DIAGONAL_SHARE) {
            direction |= leaningSideways
                    ? (dy > 0 ? DIGITAL_PAD_DIRECTION_DOWN : DIGITAL_PAD_DIRECTION_UP)
                    : (dx > 0 ? DIGITAL_PAD_DIRECTION_RIGHT : DIGITAL_PAD_DIRECTION_LEFT);
        }

        return direction;
    }

    /**
     * How equal the two offsets have to be for a press to count as a diagonal, as a share of
     * the larger one. A larger share means a narrower wedge around the diagonal.
     */
    private static final float DIAGONAL_SHARE = 0.55f;

    private static boolean between(float value, float from, float to) {
        return value >= from && value <= to;
    }

    /**
     * Which directions a point on the pad activates. Used by the debug smoke test, because
     * the zones cannot be reached by injecting a touch on every device.
     */
    public int getDirectionAtForTesting(float x, float y) {
        return directionAt(x, y);
    }

    /**
     * Lights up the given directions without a touch, so a preview can show the look of a
     * pressed pad.
     */
    public void setDirectionForTesting(int direction) {
        this.direction = direction;
        invalidate();
    }

    /**
     * The middle of the drawn shape of one direction, in the order of DIRECTION_FLAGS. Used by
     * the debug smoke test to check that each button is drawn on the side it belongs to.
     */
    public float getShapeCenterXForTesting(int directionIndex) {
        ensureDirectionPaths();

        RectF bounds = new RectF();
        directionPaths[directionIndex].computeBounds(bounds, true);
        return bounds.centerX();
    }

    public float getShapeCenterYForTesting(int directionIndex) {
        ensureDirectionPaths();

        RectF bounds = new RectF();
        directionPaths[directionIndex].computeBounds(bounds, true);
        return bounds.centerY();
    }

    /**
     * The bounds of one drawn button, for the debug smoke test.
     */
    public float[] getShapeBoundsForTesting(int directionIndex) {
        ensureDirectionPaths();

        RectF bounds = new RectF();
        directionPaths[directionIndex].computeBounds(bounds, true);

        return new float[]{bounds.left, bounds.top, bounds.right, bounds.bottom};
    }

    /**
     * The order the shapes are built in, which is the order of DIRECTION_FLAGS: up, down,
     * left, right.
     */
    public static final int SHAPE_UP = 0;
    public static final int SHAPE_DOWN = 1;
    public static final int SHAPE_LEFT = 2;
    public static final int SHAPE_RIGHT = 3;

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        ensureDirectionPaths();

        // Every button is drawn with its own state: a plain one unlit, a pushed one lit. The
        // shapes do not touch, so the light of one cannot spill onto its neighbour.
        for (int i = 0; i < directionPaths.length; i++) {
            if ((direction & DIRECTION_FLAGS[i]) == 0) {
                drawButtonPath(canvas, directionPaths[i], false, getCornerRadius() * 0.5f);
            }
        }

        for (int i = 0; i < directionPaths.length; i++) {
            if ((direction & DIRECTION_FLAGS[i]) != 0) {
                drawButtonPath(canvas, directionPaths[i], true, getCornerRadius() * 0.5f);
            }
        }
    }

    /**
     * Length of the rectangular part of one button, as a share of the shorter side of the pad.
     */
    private static final float ARM_SHARE = 0.36f;

    /**
     * Half the width of one button, as a share of the shorter side of the pad.
     */
    private static final float ARM_HALF_WIDTH_SHARE = 0.13f;

    /**
     * How far the tip of a button stays away from the middle, as a share of the shorter side.
     * The four tips do not meet, which leaves the middle of the pad empty.
     */
    private static final float TIP_GAP_SHARE = 0.04f;

    /**
     * Length of the tapering part of one button, as a share of the shorter side. This is what
     * turns the inner end of a button into a cone pointing at the middle.
     */
    private static final float TAPER_SHARE = 0.10f;

    /**
     * How far past a drawn button its touch zone reaches along its own direction, as a share
     * of the shorter side.
     */
    private static final float TOUCH_REACH_SHARE = 0.10f;

    /**
     * How far a touch zone reaches across its own direction, past the drawn width of a button.
     * This is what makes the pad easy to hit without letting a press land on the neighbouring
     * directions.
     */
    private static final float TOUCH_ACROSS_SHARE = 0.10f;

    private final Path[] directionPaths = new Path[DIRECTION_COUNT];
    private int pathsBuiltForWidth = -1;
    private int pathsBuiltForHeight = -1;

    /**
     * Builds the shape of every button: a rectangle whose inner end narrows to a point at the
     * middle of the pad, which is what a D-pad looks like.
     */
    private void ensureDirectionPaths() {
        if (pathsBuiltForWidth == getWidth() && pathsBuiltForHeight == getHeight()) {
            return;
        }

        pathsBuiltForWidth = getWidth();
        pathsBuiltForHeight = getHeight();

        float unit = Math.min(getWidth(), getHeight());
        float arm = unit * ARM_SHARE;
        float halfWidth = unit * ARM_HALF_WIDTH_SHARE;
        float tip = unit * TIP_GAP_SHARE;
        float taper = unit * TAPER_SHARE;

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        // The shape of the up button, which the other three are rotations of
        Path up = new Path();
        up.moveTo(centerX - halfWidth, centerY - tip - taper - arm);
        up.lineTo(centerX + halfWidth, centerY - tip - taper - arm);
        up.lineTo(centerX + halfWidth, centerY - tip - taper);
        up.lineTo(centerX, centerY - tip);
        up.lineTo(centerX - halfWidth, centerY - tip - taper);
        up.close();

        directionPaths[0] = up;
        directionPaths[1] = rotate(up, centerX, centerY, 180f);

        // A quarter turn clockwise points right, so the left button is the turn the other
        // way. The order here follows DIRECTION_FLAGS.
        directionPaths[2] = rotate(up, centerX, centerY, 270f);
        directionPaths[3] = rotate(up, centerX, centerY, 90f);
    }

    private static Path rotate(Path source, float pivotX, float pivotY, float degrees) {
        Matrix matrix = new Matrix();
        matrix.setRotate(degrees, pivotX, pivotY);

        Path rotated = new Path(source);
        rotated.transform(matrix);

        return rotated;
    }

    private void newDirectionCallback(int direction) {
        _DBG("direction: " + direction);

        applyConfiguredDirections(direction);

        // notify listeners
        for (DigitalPadListener listener : listeners) {
            listener.onDirectionChange(direction);
        }
    }

    /**
     * Presses or releases the action bound to every direction of the pad.
     */
    private void applyConfiguredDirections(int direction) {
        UniversalPadConfig config = getPadConfig();

        for (int i = 0; i < directionPlayers.length; i++) {
            boolean pressed = (direction & DIRECTION_FLAGS[i]) != 0;

            if (directionPressed[i] == pressed) {
                continue;
            }
            directionPressed[i] = pressed;

            if (pressed) {
                directionPlayers[i].press(config.getDirectionSequence(i));
            }
            else {
                directionPlayers[i].release();
            }
        }
    }

    @Override
    public void releaseConfiguredActions() {
        for (int i = 0; i < directionPlayers.length; i++) {
            directionPlayers[i].release();
            directionPressed[i] = false;
        }
    }


    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                direction = directionAt(event.getX(), event.getY());

                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    beginPressWatch();
                }

                // A hold is the editor's context menu gesture, so the direction is not
                // sent while it is being watched.
                if (!isLongPressHandled()) {
                    newDirectionCallback(direction);
                }

                invalidate();

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                direction = 0;

                boolean wasHold = endPressWatch(event.getActionMasked() == MotionEvent.ACTION_UP);

                if (!wasHold) {
                    newDirectionCallback(direction);
                }

                invalidate();

                return true;
            }
            default: {
            }
        }

        return true;
    }

    public interface DigitalPadListener {
        void onDirectionChange(int direction);
    }

    @Override
    public void setGamepadButtons(int flagsToAdd, int flagsToRemove) {
        controller.getHostInput().setGamepadButtons(flagsToAdd, flagsToRemove);
    }

    @Override
    public void setTrigger(int trigger, int value) {
        controller.getHostInput().setTrigger(trigger, value);
    }

    @Override
    public void sendMouseButton(int button, boolean down) {
        controller.getHostInput().sendMouseButton(button, down);
    }

    @Override
    public void sendKeyboardKey(short keyCode, boolean down) {
        controller.getHostInput().sendKeyboardKey(keyCode, down);
    }
}
