/**
 * Universal layout: digital button that is sent to the host as a gamepad button.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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
    private final RectF rect = new RectF();

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
                this.setPressed(movingButton.isPressed());
            }
        }
        // check if the movement is outside of the range and the movement button
        // is the saved moving button
        else if (movingButton == this.movingButton) {
            this.setPressed(false);
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

        paint.setTextSize(getPercent(getWidth(), 25));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());

        paint.setColor(isPressed() ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);

        rect.left = rect.top = paint.getStrokeWidth();
        rect.right = getWidth() - rect.left;
        rect.bottom = getHeight() - rect.top;

        canvas.drawOval(rect, paint);

        if (icon != -1) {
            Drawable d = getResources().getDrawable(icon);
            d.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
            d.draw(canvas);
        } else {
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeWidth(getDefaultStrokeWidth()/2);
            canvas.drawText(text, getPercent(getWidth(), 50), getPercent(getHeight(), 63), paint);
        }
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
                setPressed(true);

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
                setPressed(false);

                boolean wasHold = endPressWatch(action == MotionEvent.ACTION_UP);

                if (!wasHold) {
                    releaseConfiguredAction();
                }

                onReleaseCallback();

                checkMovementForAllButtons(x, y);

                invalidate();

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
     * True while this button is toggled on, which is only possible when it is configured
     * as a toggle.
     */
    public boolean isToggledOn() {
        return sequencePlayer.isHolding();
    }

    /**
     * One tap of a toggle button presses, the next one releases. A plain button is held
     * for as long as the finger is on it.
     */
    private void pressConfiguredAction() {
        UniversalButtonConfig config = getButtonConfig();

        if (config.isToggle()) {
            if (isToggledOn()) {
                sequencePlayer.release();
            }
            else {
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
        // A latched button must stay visibly pressed, otherwise the user cannot tell that
        // it is still sending input.
        setPressed(getButtonConfig().isToggle() && isToggledOn());
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
        setPressed(false);
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
    public void sendMouseButton(int button, boolean down) {
        controller.getHostInput().sendMouseButton(button, down);
    }

    @Override
    public void sendKeyboardKey(short keyCode, boolean down) {
        controller.getHostInput().sendKeyboardKey(keyCode, down);
    }
}
