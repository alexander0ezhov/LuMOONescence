/**
 * Universal layout: digital button that is sent to the host as a gamepad button.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

import com.limelight.binding.input.virtual_controller.universal.action.ActionSequencePlayer;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalButtonConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalElementConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a digital button on screen element. It is used to get click and double click user input.
 *
 * What a press sends is described by the button's configuration, so the same element
 * covers a plain gamepad button, a keyboard key, a mouse click and a timed combination
 * of them.
 */
public class UniversalDigitalButton extends UniversalVirtualControllerElement
        implements ActionSequencePlayer.HostInput {

    /**
     * Gamepad buttons this element currently holds down. The player reports additions and
     * removals separately, so keeping the set here is what knows the truth.
     */
    private int gamepadButtonsDown;

    private final ActionSequencePlayer sequencePlayer = new ActionSequencePlayer(this);

    /**
     * Listener interface to update registered observers.
     */
    public interface DigitalButtonListener {

        /**
         * onClick event will be fired on button click.
         */
        void onClick();

        /**
         * onLongClick event will be fired on button long click.
         */
        void onLongClick();

        /**
         * onRelease event will be fired on button unpress.
         */
        void onRelease();
    }

    private List<DigitalButtonListener> listeners = new ArrayList<>();
    private String text = "";
    private int icon = -1;
    private long timerLongClickTimeout = 3000;
    private final Runnable longClickRunnable = new Runnable() {
        @Override
        public void run() {
            onLongClickCallback();
        }
    };

    private final Paint paint = new Paint();

    private int layer;
    private UniversalDigitalButton movingButton = null;

    boolean inRange(float x, float y) {
        return (this.getX() < x && this.getX() + this.getWidth() > x) &&
                (this.getY() < y && this.getY() + this.getHeight() > y);
    }

    public boolean checkMovement(float x, float y, UniversalDigitalButton movingButton) {
        // check if the movement happened in the same layer
        if (movingButton.layer != this.layer) {
            return false;
        }

        // save current pressed state
        boolean wasPressed = isPressed();

        // check if the movement directly happened on the button
        if ((this.movingButton == null || movingButton == this.movingButton)
                && this.inRange(x, y)) {
            // set button pressed state depending on moving button pressed state
            if (this.isPressed() != movingButton.isPressed()) {
                applyPressedState(movingButton.isPressed());
            }
        }
        // check if the movement is outside of the range and the movement button
        // is the saved moving button
        else if (movingButton == this.movingButton) {
            applyPressedState(false);
        }

        // check if a change occurred
        if (wasPressed != isPressed()) {
            if (isPressed()) {
                // is pressed set moving button and emit click event
                this.movingButton = movingButton;
                onClickCallback();
            } else {
                // no longer pressed reset moving button and emit release event
                this.movingButton = null;
                onReleaseCallback();
            }

            invalidate();

            return true;
        }

        return false;
    }

    private void checkMovementForAllButtons(float x, float y) {
        for (UniversalVirtualControllerElement element : controller.getElements()) {
            if (element != this && element instanceof UniversalDigitalButton) {
                ((UniversalDigitalButton) element).checkMovement(x, y, this);
            }
        }
    }

    public UniversalDigitalButton(UniversalVirtualController controller, int elementId, int layer, Context context) {
        super(controller, context, elementId);
        this.layer = layer;
    }

    public void addDigitalButtonListener(DigitalButtonListener listener) {
        listeners.add(listener);
    }

    public void setText(String text) {
        this.text = text;
        invalidate();
    }

    public void setIcon(int id) {
        this.icon = id;
        invalidate();
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        // The body is inset so the glow of a pressed button still fits inside the element
        float stroke = Math.max(2f, getDefaultStrokeWidth());
        float radius = getCornerRadius();

        drawButtonShape(canvas, stroke, stroke, getWidth() - stroke, getHeight() - stroke,
                radius, isPressed());

        paint.setTextSize(getPercent(getWidth(), 25));
        paint.setTextAlign(Paint.Align.CENTER);

        if (icon != -1) {
            Drawable d = getResources().getDrawable(icon);
            d.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
            d.draw(canvas);
        } else {
            // The label of a control is its name, which the drawer edits, unless it was
            // given a text of its own
            String label = text != null && !text.isEmpty() ? text : getElementName();

            if (label != null && !label.isEmpty()) {
                drawFittedLabel(canvas, label, isPressed());
            }
        }
    }

    /**
     * Draws the label in the middle of the button, shrunk until it fits, so a long name stays
     * readable instead of running past the edge.
     */
    private void drawFittedLabel(Canvas canvas, String label, boolean active) {
        float size = getPercent(getWidth(), 25);
        float available = getWidth() * 0.82f;

        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(0);
        paint.setTextAlign(Paint.Align.CENTER);

        paint.setTextSize(size);

        float measured = paint.measureText(label);
        if (measured > available && measured > 0) {
            size = size * available / measured;
            paint.setTextSize(size);
        }

        paint.setColor(active ? 0xFFEAF9FF : 0xFFEDEFF3);
        canvas.drawText(label, getPercent(getWidth(), 50), getPercent(getHeight(), 63), paint);
    }

    private void onClickCallback() {
        _DBG("clicked");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onClick();
        }

        controller.getUiHandler().removeCallbacks(longClickRunnable);
        controller.getUiHandler().postDelayed(longClickRunnable, timerLongClickTimeout);
    }

    private void onLongClickCallback() {
        _DBG("long click");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onLongClick();
        }
    }

    private void onReleaseCallback() {
        _DBG("released");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onRelease();
        }

        // We may be called for a release without a prior click
        controller.getUiHandler().removeCallbacks(longClickRunnable);
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        float x = getX() + event.getX();
        float y = getY() + event.getY();
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                movingButton = null;
                applyPressedState(true);

                // The editor uses a hold to open the context menu; during a stream nothing
                // listens, so the action is sent as usual.
                beginPressWatch();

                if (!isLongPressHandled()) {
                    // Pressing also drives the configured action, so a button can send a
                    // key, a mouse click or a gamepad button without any listener of its own.
                    pressConfiguredAction();
                }

                onClickCallback();

                invalidate();

                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                checkMovementForAllButtons(x, y);

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                applyPressedState(false);

                boolean wasHold = endPressWatch(action == MotionEvent.ACTION_UP);

                if (!wasHold) {
                    releaseConfiguredAction();
                }

                onReleaseCallback();

                checkMovementForAllButtons(x, y);

                // A latched button is on again straight away, because it is still sending
                if (getButtonConfig().isToggle()) {
                    updateToggledAppearance();
                }
                else {
                    invalidate();
                }

                return true;
            }
            default: {
            }
        }
        return true;
    }

    @Override
    public String getElementTypeName() {
        return UniversalElementTypes.ELEMENT_BUTTON;
    }

    @Override
    protected UniversalElementConfig createDefaultConfig() {
        return new UniversalButtonConfig();
    }

    public UniversalButtonConfig getButtonConfig() {
        return (UniversalButtonConfig) getConfig();
    }

    /**
     * True while a latching button is on.
     *
     * This is the button's own memory rather than the player's: a button with nothing
     * assigned yet holds no input, and it still has to show that it is latched, otherwise
     * the switch looks broken while it is being set up.
     */
    private boolean toggledOn;

    /**
     * True while this button is toggled on, which is only possible when it is configured
     * as a toggle.
     */
    public boolean isToggledOn() {
        return toggledOn;
    }

    /**
     * Sets the control's own pressed state and tells the subclass about it.
     *
     * A control whose host state is not a button flag needs to know when it goes down and up,
     * because the configured sequence is not what drives it: a trigger writes an analog value
     * instead of a flag, and it must do so even when nothing is configured for it.
     */
    private void applyPressedState(boolean pressed) {
        setPressed(pressed);
        onPressedStateChanged(pressed);
    }

    /**
     * Called whenever this control's own pressed state changes, whatever its configured
     * action does. A latched button reports the latched state as well.
     */
    protected void onPressedStateChanged(boolean pressed) {
    }

    /**
     * One tap of a toggle button presses, the next one releases. A plain button is held
     * for as long as the finger is on it.
     */
    private void pressConfiguredAction() {
        UniversalButtonConfig config = getButtonConfig();

        if (config.isToggle()) {
            if (isToggledOn()) {
                toggledOn = false;
                sequencePlayer.release();
            }
            else {
                toggledOn = true;
                sequencePlayer.press(config.getActionSequence());
            }
        }
        else {
            sequencePlayer.press(config.getActionSequence());
        }

        updateToggledAppearance();
    }

    private void releaseConfiguredAction() {
        // A toggle keeps holding after the finger is lifted
        if (getButtonConfig().isToggle()) {
            return;
        }

        sequencePlayer.release();
    }

    private void updateToggledAppearance() {
        UniversalButtonConfig config = getButtonConfig();

        // Only a latching button shows a state of its own. A plain button must not be touched
        // here: it looks pressed exactly while the finger is on it, which is what the press
        // and release handlers already set, and clearing that here would make its highlight
        // flash for a moment and disappear.
        if (!config.isToggle()) {
            return;
        }

        // A latched button stays visibly pressed, otherwise the user cannot tell that it is
        // still sending input.
        applyPressedState(isToggledOn());
        invalidate();
    }

    /**
     * Releases everything this button sends. Called by the layout before it goes away, so
     * a latched button cannot leave a key held on the host.
     */
    @Override
    public void releaseConfiguredActions() {
        sequencePlayer.cancel();
        gamepadButtonsDown = 0;

        // Nothing is being sent any more, so the latch is off as well
        toggledOn = false;
        applyPressedState(false);
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
}
