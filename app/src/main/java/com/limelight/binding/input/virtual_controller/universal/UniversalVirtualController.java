/**
 * Universal layout: the orchestrator of the customizable on-screen layout.
 *
 * Unlike the legacy OSC layout, the universal layout mixes gamepad and keyboard
 * controls in a single layout and manages its own visibility.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.input.virtual_controller.universal.action.ActionSequencePlayer;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalElementConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalGeometry;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

public class UniversalVirtualController {
    /**
     * Maximum number of on-screen keyboard keys that can be held down at once.
     */
    public static final int KEYBOARD_KEYS_MAX = 4;

    public static class ControllerInputContext {
        // Widened to int: the extended controller flags (paddles, touchpad, share) live
        // above bit 15, and a short would silently drop them.
        public int inputMap = 0x0000;
        public byte leftTrigger = 0x00;
        public byte rightTrigger = 0x00;
        public short rightStickX = 0x0000;
        public short rightStickY = 0x0000;
        public short leftStickX = 0x0000;
        public short leftStickY = 0x0000;

        // On-screen keyboard keys. These are sent to the host as keyboard packets
        // rather than controller packets, but they are kept here so they can be
        // re-sent together with the gamepad state when a packet is dropped.
        public short keyboardKeyCodes[] = new short[KEYBOARD_KEYS_MAX];
        public boolean keyboardKeysDown[] = new boolean[KEYBOARD_KEYS_MAX];
    }

    public enum ControllerMode {
        Active,
        MoveButtons,
        ResizeButtons
    }

    /**
     * Notified whenever the layout is shown or hidden, so the activity can react
     * (for example by hiding other overlays when the layout becomes visible).
     */
    public interface VisibilityListener {
        void onVisibilityChanged(boolean visible);
    }

    /**
     * Notified about gestures on the controls, which the layout editor uses to pick an
     * element to edit.
     */
    public interface ElementInteractionListener {
        /**
         * A control was tapped. The editor selects it; a stream ignores this.
         */
        void onElementTapped(UniversalVirtualControllerElement element);

        /**
         * A control was pressed and held. The editor opens the element's context menu and
         * returns true, which stops the control from also sending its action.
         */
        boolean onElementLongPressed(UniversalVirtualControllerElement element);

        /**
         * A control finished being moved or resized, so anything showing its geometry can
         * refresh.
         */
        void onElementGeometrySettled(UniversalVirtualControllerElement element);

        /**
         * The layout was adjusted because it had been arranged for a different screen size.
         */
        void onLayoutAdjustedToFit();
    }

    private static final boolean _PRINT_DEBUG_INFORMATION = false;

    private final ControllerHandler controllerHandler;
    private final Context context;
    private final Handler handler;
    private String layoutId;

    private final Runnable delayedRetransmitRunnable = new Runnable() {
        @Override
        public void run() {
            sendControllerInputContextInternal();

            // Repeat any on-screen keys that are still held down, since a dropped
            // key down packet would leave the key stuck on the host until it is
            // pressed again.
            sendHeldKeysInternal();
        }
    };

    private FrameLayout frame_layout = null;

    ControllerMode currentMode = ControllerMode.Active;
    ControllerInputContext inputContext = new ControllerInputContext();


    private List<UniversalVirtualControllerElement> elements = new ArrayList<>();

    // Layouts are visible as soon as they are created, so that a newly enabled layout
    // doesn't require an explicit show() call.
    private boolean visible = true;
    private boolean destroyed = false;
    private boolean configurationButtonEnabled = true;
    private VisibilityListener visibilityListener;
    private ElementInteractionListener elementInteractionListener;

    /**
     * Next element ID to hand out. Elements the editor adds get an ID from here, so they
     * never collide with the built-in layout or with an element added earlier.
     */
    private int nextElementId = FIRST_DYNAMIC_ELEMENT_ID;

    /**
     * True when the layout has changes that are not saved yet, for example because it had
     * to be brought into view after being arranged on a different screen.
     */
    private boolean layoutDirty;

    /**
     * IDs at or above this belong to elements the user added, which keeps them out of the
     * way of the built-in layout's fixed IDs.
     */
    public static final int FIRST_DYNAMIC_ELEMENT_ID = 1000;

    public int allocateElementId() {
        return nextElementId++;
    }

    public int peekNextElementId() {
        return nextElementId;
    }

    /**
     * Restores the ID counter from a saved layout, so IDs stay unique across sessions.
     */
    public void setNextElementId(int nextElementId) {
        this.nextElementId = Math.max(FIRST_DYNAMIC_ELEMENT_ID, nextElementId);
    }

    /**
     * @param layoutId ID of the universal layout this controller shows. The geometry of
     *                 the controls is stored per layout, so this decides which saved
     *                 arrangement is loaded and where edits are saved.
     */
    public UniversalVirtualController(final ControllerHandler controllerHandler,
                                      FrameLayout layout,
                                      final Context context,
                                      final String layoutId) {
        this.controllerHandler = controllerHandler;
        this.frame_layout = layout;
        this.context = context;
        this.layoutId = layoutId;
        this.handler = new Handler(Looper.getMainLooper());

    }

    public ControllerHandler getControllerHandler() {
        return controllerHandler;
    }

    /**
     * Returns the Android handler of the main looper, which elements use to schedule
     * their own callbacks (for example the long-press timer of a button).
     */
    public Handler getUiHandler() {
        return handler;
    }

    public Context getContext() {
        return context;
    }

    public void setVisibilityListener(VisibilityListener listener) {
        this.visibilityListener = listener;
    }

    /**
     * Registers the listener that receives gestures on the controls. Only the layout
     * editor uses this.
     */
    /**
     * Where the movement of a finger that is pressing a pass-through control goes.
     */
    public interface PassThroughListener {
        /**
         * The finger moved, in pixels of the layout area.
         */
        void onPassThroughMoved(int x, int y);

        /**
         * The finger that was passing movement on let go, or the press was cancelled.
         */
        void onPassThroughEnded();
    }

    private PassThroughListener passThroughListener;

    public void setPassThroughListener(PassThroughListener listener) {
        this.passThroughListener = listener;
    }

    void notifyPassThroughMoved(int x, int y) {
        if (passThroughListener != null) {
            passThroughListener.onPassThroughMoved(x, y);
        }
    }

    void notifyPassThroughEnded() {
        if (passThroughListener != null) {
            passThroughListener.onPassThroughEnded();
        }
    }

    public void setElementInteractionListener(ElementInteractionListener listener) {
        this.elementInteractionListener = listener;
    }

    public ElementInteractionListener getElementInteractionListener() {
        return elementInteractionListener;
    }

    /**
     * Tells the listener that a control was tapped, if there is one.
     */
    public void notifyElementTapped(UniversalVirtualControllerElement element) {
        if (elementInteractionListener != null) {
            elementInteractionListener.onElementTapped(element);
        }
    }

    /**
     * Tells the listener that a control was held, if there is one.
     *
     * @return true when the listener handled it, in which case the control must not send
     *         its configured action
     */
    public boolean notifyElementLongPressed(UniversalVirtualControllerElement element) {
        return elementInteractionListener != null
                && elementInteractionListener.onElementLongPressed(element);
    }

    /**
     * Tells the listener that a control finished being moved or resized.
     */
    public void notifyElementGeometrySettled(UniversalVirtualControllerElement element) {
        if (elementInteractionListener != null) {
            elementInteractionListener.onElementGeometrySettled(element);
        }
    }

    /**
     * Tells the listener that the layout had to be adjusted to fit the screen.
     */
    public void notifyLayoutAdjustedToFit() {
        if (elementInteractionListener != null) {
            elementInteractionListener.onLayoutAdjustedToFit();
        }
    }

    /**
     * Shows or hides the floating configuration button. The layout editor provides its
     * own controls, so it hides this button to avoid two ways of doing the same thing.
     */
    public void setConfigurationButtonVisible(boolean visible) {
        configurationButtonEnabled = visible;
    }

    /**
     * Hides the layout if it is visible, shows it otherwise.
     */
    public void toggleVisibility() {
        if (isVisible()) {
            hide();
        }
        else {
            show();
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void hide() {
        setVisible(false);
    }

    public void show() {
        setVisible(true);
    }

    /**
     * Hides the layout and removes it from its parent. A destroyed layout must not be
     * shown again, because its elements are no longer attached to the view hierarchy.
     */
    public void destroy() {
        if (destroyed) {
            return;
        }

        handler.removeCallbacks(delayedRetransmitRunnable);

        hide();

        for (UniversalVirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();


        destroyed = true;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    /**
     * Returns the ID of the layout this controller shows.
     */
    public String getLayoutId() {
        return layoutId;
    }

    /**
     * Swaps the whole set of elements at once, used when a saved layout is loaded or the
     * layout is rebuilt. What the old elements were sending is released first, so nothing
     * stays held on the host.
     */
    public void replaceElements(List<UniversalVirtualControllerElement> newElements) {
        releaseAllInputs();

        for (UniversalVirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();

        for (UniversalVirtualControllerElement element : newElements) {
            UniversalGeometry geometry = element.getConfig().getGeometry();
            addElement(element, geometry.getLeft(), geometry.getTop(),
                    geometry.getWidth(), geometry.getHeight());
        }
    }

    /**
     * Releases everything the on-screen elements are sending. Called before the layout is
     * saved, rebuilt or torn down, so a latched button or a pushed stick cannot leave a
     * key or a button held on the host.
     */
    public void releaseAllInputs() {
        for (UniversalVirtualControllerElement element : elements) {
            element.releaseConfiguredActions();
        }
    }

    /**
     * Writes the current geometry of every element to the layout preferences. This is
     * what the configuration button does when leaving configuration mode; callers that
     * edit a layout outside a stream (the layout editor) use it as well.
     */
    public void saveLayoutConfiguration() {
        releaseAllInputs();

        UniversalLayoutConfigurationLoader.saveProfile(this, context, layoutId,
                frame_layout.getWidth(), frame_layout.getHeight());

        layoutDirty = false;
    }

    /**
     * Marks the layout as having unsaved changes. Used by the loader when it had to adjust
     * a layout that was arranged on a screen of a different size.
     */
    public void markConfigurationDirty() {
        layoutDirty = true;
    }

    public boolean isLayoutDirty() {
        return layoutDirty;
    }

    public void clearLayoutDirty() {
        layoutDirty = false;
    }

    private void setVisible(boolean visible) {
        if (destroyed || this.visible == visible) {
            return;
        }

        this.visible = visible;


        for (UniversalVirtualControllerElement element : elements) {
            element.setVisibility(getViewVisibility());
        }

        if (visibilityListener != null) {
            visibilityListener.onVisibilityChanged(visible);
        }
    }

    private int getViewVisibility() {
        return visible ? View.VISIBLE : View.INVISIBLE;
    }


    public void removeElements() {
        for (UniversalVirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();

    }

    public void addElement(UniversalVirtualControllerElement element, int x, int y, int width, int height) {
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);

        // A layout is rebuilt while it is on screen (for example after a rotation), so
        // new elements must adopt the current visibility instead of defaulting to it.
        element.setVisibility(getViewVisibility());

        // A control added while the layout is on screen must adopt the transparency that is
        // in force as well, otherwise a new one stands out as the only fully opaque control
        // until the layout is reloaded.
        element.setElementOpacity(getElementOpacity());
    }

    public List<UniversalVirtualControllerElement> getElements() {
        return elements;
    }

    /**
     * Adds an element the user asked for, from the editor's "add button" or "add stick"
     * actions.
     *
     * @param type one of UniversalElementTypes.ELEMENT_*
     * @return the element that was added
     */
    public UniversalVirtualControllerElement addUserElement(String type, int x, int y, int width, int height) {
        int elementId = allocateElementId();

        UniversalVirtualControllerElement element =
                UniversalLayoutConfigurationLoader.createElement(this, context, type, elementId);
        if (element == null) {
            return null;
        }

        element.setElementName(defaultNameForType(type) + " " + elementId);
        element.getConfig().getGeometry().setLeft(x);
        element.getConfig().getGeometry().setTop(y);
        element.getConfig().getGeometry().setWidth(width);
        element.getConfig().getGeometry().setHeight(height);

        addElement(element, x, y, width, height);

        return element;
    }

    /**
     * Copies an element, giving the copy its own ID and configuration and nudging it so it
     * is visible next to the original.
     */
    public UniversalVirtualControllerElement duplicateElement(UniversalVirtualControllerElement original) {
        if (original == null) {
            return null;
        }

        int elementId = allocateElementId();

        UniversalVirtualControllerElement copy =
                UniversalLayoutConfigurationLoader.createElement(this, context,
                        original.getElementTypeName(), elementId);
        if (copy == null) {
            return null;
        }

        // The copy starts from the original's configuration, including its geometry, and
        // is then moved so it does not land exactly on top of it.
        UniversalElementConfig config = original.createConfigCopy();
        copy.applyConfig(config);

        UniversalGeometry geometry = config.getGeometry();
        geometry.setLeft(geometry.getLeft() + DUPLICATE_OFFSET_PX);
        geometry.setTop(geometry.getTop() + DUPLICATE_OFFSET_PX);
        geometry.applyTo(copy);

        copy.setElementName(original.getElementName());

        addElement(copy, geometry.getLeft(), geometry.getTop(),
                geometry.getWidth(), geometry.getHeight());

        return copy;
    }

    private static final int DUPLICATE_OFFSET_PX = 24;

    private static String defaultNameForType(String type) {
        if (UniversalElementTypes.ELEMENT_STICK.equals(type)) {
            return "Stick";
        }

        return "Button";
    }

    /**
     * Removes an element from the layout. Its saved configuration is dropped as well, so
     * the layout file does not grow with entries for elements that are gone.
     */
    public void removeElement(UniversalVirtualControllerElement element) {
        if (element == null) {
            return;
        }

        element.releaseConfiguredActions();

        elements.remove(element);
        frame_layout.removeView(element);

        UniversalLayoutConfigurationLoader.forgetElement(context, layoutId, element.elementId);
    }

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            LimeLog.info("UniversalLayout: " + text);
        }
    }

    /**
     * Rebuilds the layout from scratch: the built-in default layout first, then the
     * geometry the user saved on top of it. The visibility of the layout is preserved.
     */
    /**
     * Shows another layout in place of this one, without interrupting the stream.
     *
     * The controls of the old layout are taken away by rebuilding, so nothing of it is left
     * behind and the new one is placed, made visible and announced to the host exactly as it
     * would have been at the start of the stream.
     */
    public void loadLayout(String layoutId) {
        this.layoutId = layoutId;

        refreshLayout();
    }

    public void refreshLayout() {
        if (destroyed) {
            return;
        }

        removeElements();

        UniversalLayoutConfigurationLoader.createDefaultLayout(this, context);
        UniversalLayoutConfigurationLoader.loadFromPreferences(this, context, layoutId);

        applyElementOpacity();
        announceToHost();
    }

    /**
     * Tells the host that this layout is a gamepad, without waiting for the user to press
     * something.
     *
     * The host decides which players have a gamepad from the mask carried by every input
     * packet, so one empty packet is what registers the gamepad as soon as the layout is on
     * screen. Nothing is sent from the layout editor, where there is no host to talk to.
     */
    public void announceToHost() {
        if (controllerHandler == null) {
            return;
        }

        sendControllerInputContextInternal();
    }

    /**
     * Applies the transparency the user chose for the layout.
     *
     * It is one setting for the whole layout rather than a per-control value, because it is
     * about how much of the game shows through, and it covers the body, the edges and the
     * labels together. This is the only place the transparency of a layout is decided: the
     * built-in on-screen controls have a separate setting of their own.
     */
    public void applyElementOpacity() {
        float opacity = getElementOpacity();

        for (UniversalVirtualControllerElement element : elements) {
            element.setElementOpacity(opacity);
        }
    }

    /**
     * The transparency of the layout, 0..1. One means fully opaque.
     */
    public float getElementOpacity() {
        int percent = PreferenceConfiguration.getUniversalLayoutOpacity(context);

        return Math.max(0.05f, Math.min(1f, percent / 100f));
    }

    /**
     * The view the controls are laid out in. Its size is what a layout has to fit.
     */
    public View getLayoutArea() {
        return frame_layout;
    }

    public ControllerMode getControllerMode() {
        return currentMode;
    }

    /**
     * Switches between normal operation and the configuration modes. The layout editor
     * uses this to enter and leave editing, just as the configuration button does.
     */
    public void setControllerMode(ControllerMode mode) {
        currentMode = mode;

        for (UniversalVirtualControllerElement element : elements) {
            element.invalidate();
        }
    }

    public ControllerInputContext getControllerInputContext() {
        return inputContext;
    }

    /**
     * Entry point for the action model: an on-screen element asks the controller to send
     * whatever its configuration describes, and the controller owns the packet state.
     *
     * Gamepad buttons go into the shared button mask, keyboard keys are repeated while
     * they are held (the host can drop a packet that arrives too soon after another one),
     * and mouse buttons are sent straight through.
     */
    private final ActionSequencePlayer.HostInput hostInput = new ActionSequencePlayer.HostInput() {
        @Override
        public void setGamepadButtons(int flagsToAdd, int flagsToRemove) {
            if (flagsToAdd != 0) {
                inputContext.inputMap |= flagsToAdd;
            }

            if (flagsToRemove != 0) {
                inputContext.inputMap &= ~flagsToRemove;
            }

            sendControllerInputContext();
        }

        @Override
        public void setTrigger(int trigger, int value) {
            if (trigger == com.limelight.binding.input.virtual_controller.universal.action.InputEvent.TRIGGER_LEFT) {
                inputContext.leftTrigger = (byte) value;
            }
            else {
                inputContext.rightTrigger = (byte) value;
            }

            sendControllerInputContext();
        }

        @Override
        public void sendMouseButton(int button, boolean down) {
            if (controllerHandler != null) {
                controllerHandler.reportOscMouseButton((byte) button, down);
            }
        }

        @Override
        public void sendKeyboardKey(short keyCode, boolean down) {
            updateKeyboardKeyState(keyCode, down);
        }
    };

    public ActionSequencePlayer.HostInput getHostInput() {
        return hostInput;
    }

    /**
     * Mouse movement driven by an on-screen stick.
     *
     * The mouse takes deltas rather than absolute positions, so the stick's current value
     * is walked forward on a short timer instead of being sent once per touch event. That
     * keeps the pointer moving smoothly while the stick is held and stops the moment the
     * stick is centred.
     */
    private static final long MOUSE_STICK_REPORT_PERIOD_MS = 16;

    /**
     * Pixels the pointer travels per second when a stick that drives the mouse is pushed
     * all the way.
     */
    private static final float MOUSE_STICK_SPEED_PIXELS_PER_SECOND = 900.f;

    private float mouseStickX;
    private float mouseStickY;
    private boolean mouseStickReporting;

    private final Runnable mouseStickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mouseStickReporting) {
                return;
            }

            short deltaX = mouseStickX == 0 ? 0 : (short) Math.round(
                    mouseStickX * MOUSE_STICK_SPEED_PIXELS_PER_SECOND * MOUSE_STICK_REPORT_PERIOD_MS / 1000.f);
            short deltaY = mouseStickY == 0 ? 0 : (short) Math.round(
                    -mouseStickY * MOUSE_STICK_SPEED_PIXELS_PER_SECOND * MOUSE_STICK_REPORT_PERIOD_MS / 1000.f);

            if (deltaX != 0 || deltaY != 0) {
                if (controllerHandler != null) {
                    controllerHandler.reportOscMouseMove(deltaX, deltaY);
                }
            }

            handler.postDelayed(this, MOUSE_STICK_REPORT_PERIOD_MS);
        }
    };

    /**
     * Updates the share of the mouse movement that one stick contributes.
     *
     * @param target UniversalHostAxis.TARGET_* of the mouse axis this stick drives
     * @param value stick position, -1..1
     */
    public void moveMouseByStick(UniversalVirtualControllerElement element, int target, float value) {
        // A stick that drives the mouse contributes to the horizontal share when its own
        // horizontal axis is bound to the mouse, and to the vertical share when its
        // vertical axis is. The single "Mouse" entry in the editor covers both.
        boolean vertical = false;

        if (element instanceof UniversalAnalogStick) {
            com.limelight.binding.input.virtual_controller.universal.config.UniversalStickConfig stickConfig =
                    ((UniversalAnalogStick) element).getStickConfig();
            vertical = stickConfig.getAxisY() == target && stickConfig.getAxisX() != target;
        }

        if (vertical) {
            mouseStickY = value;
        }
        else {
            mouseStickX = value;
        }

        boolean shouldReport = mouseStickX != 0 || mouseStickY != 0;

        if (shouldReport && !mouseStickReporting) {
            mouseStickReporting = true;
            handler.post(mouseStickRunnable);
        }
        else if (!shouldReport) {
            mouseStickReporting = false;
            handler.removeCallbacks(mouseStickRunnable);
        }
    }

    /**
     * Stops the pointer movement a stick is driving, so a stick that is let go or a layout
     * that goes away cannot leave the pointer drifting.
     */
    public void stopStickMouseMovement() {
        mouseStickX = 0;
        mouseStickY = 0;
        mouseStickReporting = false;
        handler.removeCallbacks(mouseStickRunnable);
    }

    /**
     * Returns the modifier byte that matches the given GFE keycode. On-screen keys are
     * sent one at a time, so a modifier bit can only refer to the key being sent.
     *
     * Note that KeyboardTranslator maps the modifier keys to distinct left/right codes
     * (for example 0xA0/0xA1 for shift) rather than to the VK_SHIFT/VK_CONTROL values,
     * so those are the values that must be matched here.
     */
    private static byte getKeyModifier(short keyCode) {
        switch (keyCode & 0xFF) {
            case 0xA0: // Shift left
            case 0xA1: // Shift right
                return KeyboardPacket.MODIFIER_SHIFT;

            case 0xA2: // Control left
            case 0xA3: // Control right
                return KeyboardPacket.MODIFIER_CTRL;

            case 0xA4: // Alt left
            case 0xA5: // Alt right
                return KeyboardPacket.MODIFIER_ALT;

            case 0x5B: // Meta left (Windows key)
            case 0x5C: // Meta right (Windows key)
                return KeyboardPacket.MODIFIER_META;

            default:
                return 0;
        }
    }

    /**
     * Reports every on-screen key that is currently held down to the host. Key up events
     * are never sent here, because repeating them could unstick a key press that arrived
     * later, while repeating key down events is harmless.
     */
    private void sendHeldKeysInternal() {
        for (int i = 0; i < inputContext.keyboardKeyCodes.length; i++) {
            if (inputContext.keyboardKeysDown[i]) {
                _DBG("KEYBOARD " + Integer.toHexString(inputContext.keyboardKeyCodes[i] & 0xFFFF) + " DOWN");
                controllerHandler.reportOscKeyboardState(inputContext.keyboardKeyCodes[i],
                        KeyboardPacket.KEY_DOWN, getKeyModifier(inputContext.keyboardKeyCodes[i]), (byte) 0);
            }
        }
    }

    private void sendControllerInputContextInternal() {
        _DBG("INPUT_MAP + " + inputContext.inputMap);
        _DBG("LEFT_TRIGGER " + inputContext.leftTrigger);
        _DBG("RIGHT_TRIGGER " + inputContext.rightTrigger);
        _DBG("LEFT STICK X: " + inputContext.leftStickX + " Y: " + inputContext.leftStickY);
        _DBG("RIGHT STICK X: " + inputContext.rightStickX + " Y: " + inputContext.rightStickY);

        if (controllerHandler != null) {
            controllerHandler.reportOscState(
                    inputContext.inputMap,
                    inputContext.leftStickX,
                    inputContext.leftStickY,
                    inputContext.rightStickX,
                    inputContext.rightStickY,
                    inputContext.leftTrigger,
                    inputContext.rightTrigger
            );
        }
    }

    void sendControllerInputContext() {
        // Cancel retransmissions of prior gamepad inputs
        handler.removeCallbacks(delayedRetransmitRunnable);

        sendControllerInputContextInternal();

        // HACK: GFE sometimes discards gamepad packets when they are received
        // very shortly after another. This can be critical if an axis zeroing packet
        // is lost and causes an analog stick to get stuck. To avoid this, we retransmit
        // the gamepad state a few times unless another input event happens before then.
        //
        // Any on-screen keys that are held down are repeated along with the gamepad
        // state, otherwise a dropped key down packet could leave a key stuck on the
        // host until the user presses it again.
        handler.postDelayed(delayedRetransmitRunnable, 25);
        handler.postDelayed(delayedRetransmitRunnable, 50);
        handler.postDelayed(delayedRetransmitRunnable, 75);
    }

    /**
     * Called by on-screen keyboard elements when their pressed state changes.
     *
     * @param keyCode the GFE keycode produced by KeyboardTranslator.translate(int, int)
     * @param pressed true if the on-screen key was just pressed, false if released
     */
    void updateKeyboardKeyState(short keyCode, boolean pressed) {
        if (controllerHandler == null) {
            return;
        }

        // Keyboard packets must not be batched with the gamepad state, so cancel the
        // pending retransmissions before sending an event of our own.
        handler.removeCallbacks(delayedRetransmitRunnable);

        // Track the state of this key so it can be repeated with the gamepad state
        // while it is held down.
        int keyIndex = -1;
        for (int i = 0; i < inputContext.keyboardKeyCodes.length; i++) {
            if (inputContext.keyboardKeyCodes[i] == keyCode) {
                keyIndex = i;
                break;
            }
        }

        if (keyIndex == -1 && pressed) {
            // Start tracking a key that we don't know about yet
            for (int i = 0; i < inputContext.keyboardKeyCodes.length; i++) {
                if (inputContext.keyboardKeyCodes[i] == 0) {
                    inputContext.keyboardKeyCodes[i] = keyCode;
                    keyIndex = i;
                    break;
                }
            }
        }

        if (keyIndex == -1) {
            // The key was never tracked (a key up event with no matching key down event)
            return;
        }

        boolean keyWasDown = inputContext.keyboardKeysDown[keyIndex];
        if (pressed == keyWasDown) {
            // Nothing to report (a key down event for a key that is already down or a
            // key up event for a key that the host already believes is up)
            return;
        }

        if (!pressed) {
            inputContext.keyboardKeyCodes[keyIndex] = 0;
        }
        inputContext.keyboardKeysDown[keyIndex] = pressed;

        _DBG("KEYBOARD " + Integer.toHexString(keyCode & 0xFFFF) + (pressed ? " DOWN" : " UP"));

        controllerHandler.reportOscKeyboardState(keyCode,
                pressed ? KeyboardPacket.KEY_DOWN : KeyboardPacket.KEY_UP,
                getKeyModifier(keyCode), (byte) 0);

        // Re-schedule the gamepad retransmission that was cancelled above
        handler.postDelayed(delayedRetransmitRunnable, 25);
        handler.postDelayed(delayedRetransmitRunnable, 50);
        handler.postDelayed(delayedRetransmitRunnable, 75);
    }
}
