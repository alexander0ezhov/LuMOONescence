/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.input.KeyboardPacket;

import java.util.ArrayList;
import java.util.List;

public class VirtualController {
    /**
     * Maximum number of on-screen keyboard keys that can be held down at once.
     */
    public static final int KEYBOARD_KEYS_MAX = 4;

    public static class ControllerInputContext {
        public short inputMap = 0x0000;
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

    private static final boolean _PRINT_DEBUG_INFORMATION = false;

    private final ControllerHandler controllerHandler;
    private final Context context;
    private final Handler handler;

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

    private Button buttonConfigure = null;

    private List<VirtualControllerElement> elements = new ArrayList<>();

    public VirtualController(final ControllerHandler controllerHandler, FrameLayout layout, final Context context) {
        this.controllerHandler = controllerHandler;
        this.frame_layout = layout;
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());

        buttonConfigure = new Button(context);
        buttonConfigure.setAlpha(0.25f);
        buttonConfigure.setFocusable(false);
        buttonConfigure.setBackgroundResource(R.drawable.ic_settings);
        buttonConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message;

                if (currentMode == ControllerMode.Active){
                    currentMode = ControllerMode.MoveButtons;
                    message = "Entering configuration mode (Move buttons)";
                } else if (currentMode == ControllerMode.MoveButtons) {
                    currentMode = ControllerMode.ResizeButtons;
                    message = "Entering configuration mode (Resize buttons)";
                } else {
                    currentMode = ControllerMode.Active;
                    VirtualControllerConfigurationLoader.saveProfile(VirtualController.this, context);
                    message = "Exiting configuration mode";
                }

                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();

                buttonConfigure.invalidate();

                for (VirtualControllerElement element : elements) {
                    element.invalidate();
                }
            }
        });

    }

    Handler getHandler() {
        return handler;
    }

    public void hide() {
        for (VirtualControllerElement element : elements) {
            element.setVisibility(View.INVISIBLE);
        }

        buttonConfigure.setVisibility(View.INVISIBLE);
    }

    public void show() {
        for (VirtualControllerElement element : elements) {
            element.setVisibility(View.VISIBLE);
        }

        buttonConfigure.setVisibility(View.VISIBLE);
    }

    public void removeElements() {
        for (VirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();

        frame_layout.removeView(buttonConfigure);
    }

    public void setOpacity(int opacity) {
        for (VirtualControllerElement element : elements) {
            element.setOpacity(opacity);
        }
    }


    public void addElement(VirtualControllerElement element, int x, int y, int width, int height) {
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);
    }

    public List<VirtualControllerElement> getElements() {
        return elements;
    }

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            LimeLog.info("VirtualController: " + text);
        }
    }

    public void refreshLayout() {
        removeElements();

        DisplayMetrics screen = context.getResources().getDisplayMetrics();

        int buttonSize = (int)(screen.heightPixels*0.06f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        params.leftMargin = 15;
        params.topMargin = 15;
        frame_layout.addView(buttonConfigure, params);

        // Start with the default layout
        VirtualControllerConfigurationLoader.createDefaultLayout(this, context);

        // Apply user preferences onto the default layout
        VirtualControllerConfigurationLoader.loadFromPreferences(this, context);
    }

    public ControllerMode getControllerMode() {
        return currentMode;
    }

    public ControllerInputContext getControllerInputContext() {
        return inputContext;
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
