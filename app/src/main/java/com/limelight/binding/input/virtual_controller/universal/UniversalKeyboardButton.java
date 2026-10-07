/**
 * Universal layout: on-screen keyboard key.
 *
 * Unlike UniversalDigitalButton, this element is delivered to the host as a keyboard
 * packet instead of a gamepad button flag.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.content.Context;
import android.view.KeyEvent;

import com.limelight.binding.input.KeyboardTranslator;

public class UniversalKeyboardButton extends UniversalDigitalButton {

    // Translating here keeps the keycode in one place: the button draws the text, but
    // the host receives the GFE keycode that KeyboardTranslator produces for the
    // equivalent hardware key event. The virtual device that delivers OSC key events
    // has no key layout, so this only ever performs the fixed QWERTY mapping.
    private static final KeyboardTranslator keyboardTranslator = new KeyboardTranslator();

    private final short keyCode;

    /**
     * @param elementId the unique element ID used to persist the geometry of this key
     * @param androidKeyCode an Android KeyEvent.KEYCODE_* value to translate and send
     */
    public UniversalKeyboardButton(final UniversalVirtualController controller,
                                   final int elementId,
                                   final int androidKeyCode,
                                   final int layer,
                                   final String text,
                                   final int icon,
                                   final Context context) {
        super(controller, elementId, layer, context);

        // setText() and setIcon() must be called explicitly: setIcon(-1) is what tells
        // the button to render the text instead of a drawable.
        setText(text);
        setIcon(icon);

        short translated = keyboardTranslator.translate(androidKeyCode, -1);

        // A keycode that can't be translated would silently send nothing to the host,
        // so fail loudly instead of adding a dead key to the layout.
        if (translated == 0) {
            throw new IllegalArgumentException(
                    "Android keycode " + androidKeyCode + " has no GFE keycode mapping");
        }

        this.keyCode = translated;

        addDigitalButtonListener(new DigitalButtonListener() {
            @Override
            public void onClick() {
                // The gamepad bitmask is deliberately left alone here. Its bits are
                // gamepad buttons only, so setting an unrelated bit would make the
                // host see a gamepad button that is never released.
                controller.updateKeyboardKeyState(keyCode, true);
            }

            @Override
            public void onLongClick() {
                // Keyboard keys have no long press behaviour
            }

            @Override
            public void onRelease() {
                controller.updateKeyboardKeyState(keyCode, false);
            }
        });
    }

    /**
     * Returns the GFE keycode that this key sends to the host.
     */
    public short getKeyCode() {
        return keyCode;
    }

    /**
     * Convenience factory for an alphanumeric key, for example 'R' or '5'. Only
     * A-Z and 0-9 are supported, since those are the only keycodes whose Android
     * values are contiguous.
     */
    public static UniversalKeyboardButton createForCharacter(final UniversalVirtualController controller,
                                                             final int elementId,
                                                             final char character,
                                                             final int layer,
                                                             final Context context) {
        char key = Character.toUpperCase(character);

        final int androidKeyCode;
        if (key >= 'A' && key <= 'Z') {
            androidKeyCode = KeyEvent.KEYCODE_A + (key - 'A');
        }
        else if (key >= '0' && key <= '9') {
            androidKeyCode = KeyEvent.KEYCODE_0 + (key - '0');
        }
        else {
            throw new IllegalArgumentException("Unsupported on-screen key character: " + character);
        }

        return new UniversalKeyboardButton(controller, elementId, androidKeyCode, layer,
                String.valueOf(key), -1, context);
    }
}
