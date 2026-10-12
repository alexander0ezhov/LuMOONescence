/**
 * Universal layout: analog stick element.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.MotionEvent;

import com.limelight.binding.input.virtual_controller.universal.action.ActionSequence;
import com.limelight.binding.input.virtual_controller.universal.action.ActionSequencePlayer;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalElementConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalHostAxis;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalStickButtonConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalStickConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a analog stick on screen element. It is used to get 2-Axis user input.
 *
 * What the stick drives comes from its configuration: two host axes in the axes mode, or
 * four gamepad buttons in the buttons mode.
 */
public class UniversalAnalogStick extends UniversalVirtualControllerElement
        implements ActionSequencePlayer.HostInput {

    /**
     * Fraction of the travel that counts as a deliberate push in the buttons mode. It
     * keeps a stick that rests slightly off centre from pressing anything.
     */
    private static final float DIRECTION_THRESHOLD = 0.5f;

    /**
     * Gamepad buttons this stick currently holds down through its direction bindings.
     */
    private int gamepadButtonsDown;

    /**
     * One player per direction, so each binding keeps its own held inputs and can release
     * exactly what it pressed.
     */
    private final ActionSequencePlayer[] directionPlayers = new ActionSequencePlayer[UniversalStickConfig.DIRECTION_COUNT];

    /**
     * Plays whatever the middle of the stick is bound to, which is the stick click on a real
     * controller.
     */
    private ActionSequencePlayer centerPlayer;

    /**
     * The directions that are currently pushed, used to avoid resending the same
     * transition on every movement event.
     */
    private final boolean[] directionPressed = new boolean[UniversalStickConfig.DIRECTION_COUNT];

    /**
     * True from the moment a touch lands in the middle of the stick until it is lifted. A
     * touch that only ever stays in the middle is a press of the middle button rather than
     * a movement.
     */
    private boolean centerTouchActive;

    /**
     * Set once the touch has moved out of the middle, which turns the touch into a movement
     * and stops it from also acting as a button press.
     */
    private boolean centerTouchMoved;

    /**
     * outer radius size in percent of the ui element
     */
    public static final int SIZE_RADIUS_COMPLETE = 90;
    /**
     * analog stick size in percent of the ui element
     */
    public static final int SIZE_RADIUS_ANALOG_STICK = 90;
    /**
     * dead zone size in percent of the ui element
     */
    public static final int SIZE_RADIUS_DEADZONE = 90;
    /**
     * time frame for a double click
     */
    public final static long timeoutDoubleClick = 350;

    /**
     * touch down time until the deadzone is lifted to allow precise movements with the analog sticks
     */
    public final static long timeoutDeadzone = 150;

    /**
     * Listener interface to update registered observers.
     */
    public interface AnalogStickListener {

        /**
         * onMovement event will be fired on real analog stick movement (outside of the deadzone).
         *
         * @param x horizontal position, value from -1.0 ... 0 .. 1.0
         * @param y vertical position, value from -1.0 ... 0 .. 1.0
         */
        void onMovement(float x, float y);

        /**
         * onClick event will be fired on click on the analog stick
         */
        void onClick();

        /**
         * onDoubleClick event will be fired on a double click in a short time frame on the analog
         * stick.
         */
        void onDoubleClick();

        /**
         * onRevoke event will be fired on unpress of the analog stick.
         */
        void onRevoke();
    }

    /**
     * Movement states of the analog sick.
     */
    private enum STICK_STATE {
        NO_MOVEMENT,
        MOVED_IN_DEAD_ZONE,
        MOVED_ACTIVE
    }

    /**
     * Click type states.
     */
    private enum CLICK_STATE {
        SINGLE,
        DOUBLE
    }

    /**
     * outer radius, this size will be automatically updated on resize
     */
    private float radius_complete = 0;
    /**
     * analog stick radius, this size will be automatically updated on resize
     */
    private float radius_analog_stick = 0;
    /**
     * dead zone radius, this size will be automatically updated on resize
     */
    private float radius_dead_zone = 0;

    /**
     * horizontal position in relation to the center of the element
     */
    private float relative_x = 0;
    /**
     * vertical position in relation to the center of the element
     */
    private float relative_y = 0;


    private double movement_radius = 0;
    private double movement_angle = 0;

    private float position_stick_x = 0;
    private float position_stick_y = 0;

    private final Paint paint = new Paint();

    private STICK_STATE stick_state = STICK_STATE.NO_MOVEMENT;
    private CLICK_STATE click_state = CLICK_STATE.SINGLE;

    private List<AnalogStickListener> listeners = new ArrayList<>();
    private long timeLastClick = 0;

    private static double getMovementRadius(float x, float y) {
        return Math.sqrt(x * x + y * y);
    }

    private static double getAngle(float way_x, float way_y) {
        // prevent divisions by zero for corner cases
        if (way_x == 0) {
            return way_y < 0 ? Math.PI : 0;
        } else if (way_y == 0) {
            if (way_x > 0) {
                return Math.PI * 3 / 2;
            } else if (way_x < 0) {
                return Math.PI * 1 / 2;
            }
        }
        // return correct calculated angle for each quadrant
        if (way_x > 0) {
            if (way_y < 0) {
                // first quadrant
                return 3 * Math.PI / 2 + Math.atan((double) (-way_y / way_x));
            } else {
                // second quadrant
                return Math.PI + Math.atan((double) (way_x / way_y));
            }
        } else {
            if (way_y > 0) {
                // third quadrant
                return Math.PI / 2 + Math.atan((double) (way_y / -way_x));
            } else {
                // fourth quadrant
                return 0 + Math.atan((double) (-way_x / -way_y));
            }
        }
    }

    public UniversalAnalogStick(UniversalVirtualController controller, Context context, int elementId) {
        super(controller, context, elementId);
        // reset stick position
        position_stick_x = getWidth() / 2;
        position_stick_y = getHeight() / 2;

        for (int i = 0; i < directionPlayers.length; i++) {
            directionPlayers[i] = new ActionSequencePlayer(this);
        }

        centerPlayer = new ActionSequencePlayer(this);
    }

    @Override
    public String getElementTypeName() {
        return UniversalElementTypes.ELEMENT_STICK;
    }

    @Override
    protected UniversalElementConfig createDefaultConfig() {
        return new UniversalStickConfig();
    }

    public UniversalStickConfig getStickConfig() {
        return (UniversalStickConfig) getConfig();
    }

    public void addAnalogStickListener(AnalogStickListener listener) {
        listeners.add(listener);
    }

    private void notifyOnMovement(float x, float y) {
        _DBG("movement x: " + x + " movement y: " + y);

        // The configured action is what the host actually receives
        applyConfiguredMovement(x, y);

        // notify listeners
        for (AnalogStickListener listener : listeners) {
            listener.onMovement(x, y);
        }
    }

    /**
     * Routes a stick position to whatever the configuration describes.
     *
     * @param x horizontal position, -1..1 with the origin in the middle
     * @param y vertical position, -1..1 with the origin in the middle
     */
    protected void applyConfiguredMovement(float x, float y) {
        UniversalStickConfig config = getStickConfig();

        if (config.isAxesMode()) {
            applyHostAxis(config.getAxisX(), x);
            applyHostAxis(config.getAxisY(), y);

            controller.sendControllerInputContext();
        }
        else {
            applyButtonMovement(config, x, y);
        }
    }

    private void applyHostAxis(int axisCode, float value) {
        UniversalHostAxis.Option option = UniversalHostAxis.optionForCode(axisCode);

        if (option.kind == UniversalHostAxis.KIND_NONE) {
            return;
        }

        if (option.kind == UniversalHostAxis.KIND_MOUSE) {
            // A vertical mouse axis reports its own sign, and the stick's Y already grows
            // upwards like the mouse does.
            controller.moveMouseByStick(this, option.target, value);
            return;
        }

        UniversalVirtualController.ControllerInputContext inputContext = controller.getControllerInputContext();
        short scaled = (short) (value * 0x7FFE);

        switch (option.target) {
            case UniversalHostAxis.TARGET_LEFT_X:
                inputContext.leftStickX = scaled;
                break;

            case UniversalHostAxis.TARGET_LEFT_Y:
                inputContext.leftStickY = scaled;
                break;

            case UniversalHostAxis.TARGET_RIGHT_X:
                inputContext.rightStickX = scaled;
                break;

            case UniversalHostAxis.TARGET_RIGHT_Y:
                inputContext.rightStickY = scaled;
                break;

            default:
                break;
        }
    }

    private void applyButtonMovement(UniversalStickConfig config, float x, float y) {
        setDirectionPressed(config, UniversalStickConfig.DIRECTION_LEFT, x <= -DIRECTION_THRESHOLD);
        setDirectionPressed(config, UniversalStickConfig.DIRECTION_RIGHT, x >= DIRECTION_THRESHOLD);
        setDirectionPressed(config, UniversalStickConfig.DIRECTION_UP, y >= DIRECTION_THRESHOLD);
        setDirectionPressed(config, UniversalStickConfig.DIRECTION_DOWN, y <= -DIRECTION_THRESHOLD);
    }

    private void setDirectionPressed(UniversalStickConfig config, int direction, boolean pressed) {
        UniversalStickButtonConfig button = config.getButton(direction);

        if (!button.isAssigned()) {
            return;
        }

        // Movement events arrive continuously, so only a change of direction is a press
        // or a release.
        if (directionPressed[direction] == pressed) {
            return;
        }
        directionPressed[direction] = pressed;

        if (pressed) {
            directionPlayers[direction].press(ActionSequence.single(
                    new com.limelight.binding.input.virtual_controller.universal.action.InputEvent(
                            com.limelight.binding.input.virtual_controller.universal.action.InputEvent.KIND_GAMEPAD,
                            button.getButtonCode())));
        }
        else {
            directionPlayers[direction].release();
        }
    }

    /**
     * Releases everything the stick sends. Called by the layout before it goes away, so a
     * pushed stick cannot leave a button held on the host.
     */
    @Override
    public void releaseConfiguredActions() {
        for (int i = 0; i < directionPlayers.length; i++) {
            directionPlayers[i].release();
            directionPressed[i] = false;
        }

        centerPlayer.release();
        centerTouchActive = false;
        centerTouchMoved = false;

        gamepadButtonsDown = 0;

        // Stop the pointer as well, otherwise a stick that drives the mouse keeps moving
        controller.stopStickMouseMovement();
    }

    /**
     * True while the touch is inside the dead zone, which is the middle of the stick.
     */
    private boolean isInCenter() {
        return movement_radius <= radius_dead_zone;
    }

    /**
     * Sends the binding of the middle of the stick, when one is enabled and assigned.
     */
    private void pressCenterButton() {
        UniversalStickButtonConfig binding = getStickConfig().getCenterButton();

        centerPressedForTesting = true;

        centerPlayer.press(ActionSequence.single(
                new com.limelight.binding.input.virtual_controller.universal.action.InputEvent(
                        com.limelight.binding.input.virtual_controller.universal.action.InputEvent.KIND_GAMEPAD,
                        binding.getButtonCode())));
    }

    /**
     * How many times the middle of the stick has sent its binding. Used by the debug smoke
     * test, because the press and its release are a single burst that leaves no trace on the
     * host state to look at afterwards.
     */
    private boolean centerPressedForTesting;

    public boolean wasCenterPressedForTesting() {
        return centerPressedForTesting;
    }

    public void forgetCenterPressForTesting() {
        centerPressedForTesting = false;
    }

    @Override
    public void setGamepadButtons(int flagsToAdd, int flagsToRemove) {
        if (flagsToAdd != 0) {
            gamepadButtonsDown |= flagsToAdd;
        }

        if (flagsToRemove != 0) {
            gamepadButtonsDown &= ~flagsToRemove;
        }

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

    private void notifyOnClick() {
        _DBG("click");
        // notify listeners
        for (AnalogStickListener listener : listeners) {
            listener.onClick();
        }
    }

    private void notifyOnDoubleClick() {
        _DBG("double click");
        // notify listeners
        for (AnalogStickListener listener : listeners) {
            listener.onDoubleClick();
        }
    }

    private void notifyOnRevoke() {
        _DBG("revoke");
        // notify listeners
        for (AnalogStickListener listener : listeners) {
            listener.onRevoke();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // calculate new radius sizes depending
        radius_complete = getPercent(getCorrectWidth() / 2, 100) - 2 * getDefaultStrokeWidth();
        radius_dead_zone = getPercent(getCorrectWidth() / 2, 30);
        radius_analog_stick = getPercent(getCorrectWidth() / 2, 20);

        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        // A stick is round, so its own shape decides the corner radius rather than the
        // configured one
        float radius = Math.min(getWidth(), getHeight()) / 2f - 2f;
        boolean active = stick_state != STICK_STATE.NO_MOVEMENT || isPressed();

        // A stick is a disc with the thumb on it, so a filled circle is what reads as one
        drawButtonShape(canvas, getWidth() / 2f - radius, getHeight() / 2f - radius,
                getWidth() / 2f + radius, getHeight() / 2f + radius, radius, active);

        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, getDefaultStrokeWidth() * 0.4f));

        // The dead zone is marked so the middle of the stick is visible, which is where its
        // middle button is pressed
        paint.setColor(0x5AC8CDD6);
        canvas.drawCircle(getWidth() / 2, getHeight() / 2, radius_dead_zone, paint);

        // The thumb rests in the middle and follows the touch while the stick is pushed
        float thumbRadius = radius_analog_stick;
        float thumbX = stick_state == STICK_STATE.NO_MOVEMENT ? getWidth() / 2f : position_stick_x;
        float thumbY = stick_state == STICK_STATE.NO_MOVEMENT ? getHeight() / 2f : position_stick_y;

        paint.setShader(active
                ? new LinearGradient(0, thumbY - thumbRadius, 0, thumbY + thumbRadius,
                        0xFF7FE6FF, 0xFF2E9FC4, Shader.TileMode.CLAMP)
                : new LinearGradient(0, thumbY - thumbRadius, 0, thumbY + thumbRadius,
                        0xFFC9CFD8, 0xFF8A9099, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(0);
        canvas.drawCircle(thumbX, thumbY, thumbRadius, paint);
        paint.setShader(null);
    }

    private void updatePosition(long eventTime) {
        // get 100% way
        float complete = radius_complete - radius_analog_stick;

        // calculate relative way
        float correlated_y = (float) (Math.sin(Math.PI / 2 - movement_angle) * (movement_radius));
        float correlated_x = (float) (Math.cos(Math.PI / 2 - movement_angle) * (movement_radius));

        // update positions
        position_stick_x = getWidth() / 2 - correlated_x;
        position_stick_y = getHeight() / 2 - correlated_y;

        // Stay active even if we're back in the deadzone because we know the user is actively
        // giving analog stick input and we don't want to snap back into the deadzone.
        // We also release the deadzone if the user keeps the stick pressed for a bit to allow
        // them to make precise movements.
        stick_state = (stick_state == STICK_STATE.MOVED_ACTIVE ||
                eventTime - timeLastClick > timeoutDeadzone ||
                movement_radius > radius_dead_zone) ?
                STICK_STATE.MOVED_ACTIVE : STICK_STATE.MOVED_IN_DEAD_ZONE;

        //  trigger move event if state active
        if (stick_state == STICK_STATE.MOVED_ACTIVE) {
            notifyOnMovement(-correlated_x / complete, correlated_y / complete);
        }
    }


    public boolean onElementTouchEvent(MotionEvent event) {
        // save last click state
        CLICK_STATE lastClickState = click_state;

        // get absolute way for each axis
        relative_x = -(getWidth() / 2 - event.getX());
        relative_y = -(getHeight() / 2 - event.getY());

        // get radius and angel of movement from center
        movement_radius = getMovementRadius(relative_x, relative_y);
        movement_angle = getAngle(relative_x, relative_y);

        // pass touch event to parent if out of outer circle
        if (movement_radius > radius_complete && !isPressed())
            return false;

        // chop radius if out of outer circle or near the edge
        if (movement_radius > (radius_complete - radius_analog_stick)) {
            movement_radius = radius_complete - radius_analog_stick;
        }

        // handle event depending on action
        switch (event.getActionMasked()) {
            // down event (touch event)
            case MotionEvent.ACTION_DOWN: {
                // set to dead zoned, will be corrected in update position if necessary
                stick_state = STICK_STATE.MOVED_IN_DEAD_ZONE;

                // A touch that starts in the middle may be a press of the middle button.
                // Whether it is one is decided when the finger is lifted, because a touch
                // that moves out of the middle is a movement instead.
                centerTouchActive = getStickConfig().isCenterButtonActive() && isInCenter();
                centerTouchMoved = false;

                // check for double click
                if (lastClickState == CLICK_STATE.SINGLE &&
                        event.getEventTime() - timeLastClick <= timeoutDoubleClick) {
                    click_state = CLICK_STATE.DOUBLE;
                    notifyOnDoubleClick();
                } else {
                    click_state = CLICK_STATE.SINGLE;
                    notifyOnClick();
                }
                // reset last click timestamp
                timeLastClick = event.getEventTime();
                // set item pressed and update
                setPressed(true);
                break;
            }
            // up event (revoke touch)
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                setPressed(false);
                break;
            }
        }

        // A finger that leaves the middle is moving the stick, not pressing its button
        if (centerTouchActive && !isInCenter()) {
            centerTouchMoved = true;
        }

        if (isPressed()) {
            // when is pressed calculate new positions (will trigger movement if necessary)
            updatePosition(event.getEventTime());
        } else {
            stick_state = STICK_STATE.NO_MOVEMENT;
            notifyOnRevoke();

            // A touch that stayed in the middle throughout is a press of the middle button
            if (centerTouchActive && !centerTouchMoved) {
                pressCenterButton();
                centerPlayer.release();
            }
            centerTouchActive = false;

            // not longer pressed reset analog stick
            notifyOnMovement(0, 0);
        }
        // refresh view
        invalidate();
        // accept the touch event
        return true;
    }
}
