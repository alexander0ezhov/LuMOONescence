/**
 * Universal layout: digital pad element.
 *
 * Each direction sends whatever its configuration says, so a pad can drive gamepad
 * buttons, keyboard keys or a mix of them, just like a plain button can.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;
import android.graphics.Canvas;
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

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        // The pad is one cross made of four arms. The body is drawn whole first, so the arms
        // join without seams, and the arm that is pushed is drawn over it lit up.
        float unit = Math.min(getWidth(), getHeight());
        float arm = unit * ARM_SHARE;
        float half = unit * GAP_SHARE / 2f;
        float radius = getCornerRadius();

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        boolean up = (direction & DIGITAL_PAD_DIRECTION_UP) > 0;
        boolean down = (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0;
        boolean left = (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0;
        boolean right = (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0;

        // The whole cross, unlit
        drawButtonShape(canvas, centerX - half, centerY - half - arm,
                centerX + half, centerY + half + arm, radius, false);
        drawButtonShape(canvas, centerX - half - arm, centerY - half,
                centerX + half + arm, centerY + half, radius, false);

        // Then the pushed arms, each drawn on its own so only it lights up
        if (up) {
            drawButtonShape(canvas, centerX - half, centerY - half - arm,
                    centerX + half, centerY - half, radius, true);
        }

        if (down) {
            drawButtonShape(canvas, centerX - half, centerY + half,
                    centerX + half, centerY + half + arm, radius, true);
        }

        if (left) {
            drawButtonShape(canvas, centerX - half - arm, centerY - half,
                    centerX - half, centerY + half, radius, true);
        }

        if (right) {
            drawButtonShape(canvas, centerX + half, centerY - half,
                    centerX + half + arm, centerY + half, radius, true);
        }
    }

    /**
     * Length of one arm of the cross, as a share of the shorter side of the pad.
     */
    private static final float ARM_SHARE = 0.30f;

    /**
     * Width of the middle the arms meet at, as a share of the shorter side of the pad.
     */
    private static final float GAP_SHARE = 0.20f;

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
                direction = 0;

                if (event.getX() < getPercent(getWidth(), 33)) {
                    direction |= DIGITAL_PAD_DIRECTION_LEFT;
                }
                if (event.getX() > getPercent(getWidth(), 66)) {
                    direction |= DIGITAL_PAD_DIRECTION_RIGHT;
                }
                if (event.getY() > getPercent(getHeight(), 66)) {
                    direction |= DIGITAL_PAD_DIRECTION_DOWN;
                }
                if (event.getY() < getPercent(getHeight(), 33)) {
                    direction |= DIGITAL_PAD_DIRECTION_UP;
                }

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
