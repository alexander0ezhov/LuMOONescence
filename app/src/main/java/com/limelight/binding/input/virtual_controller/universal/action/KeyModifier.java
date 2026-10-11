package com.limelight.binding.input.virtual_controller.universal.action;

import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.input.KeyboardPacket;

/**
 * Maps a translated keyboard keycode onto the modifier bit that must accompany it.
 *
 * KeyboardTranslator maps the modifier keys to their own left/right keycodes rather than
 * to VK_SHIFT/VK_CONTROL, so those are the values matched here. A modifier key is only
 * meaningful in the packet that carries it, which is why it is derived per event.
 */
public final class KeyModifier {
    private KeyModifier() {
    }

    public static byte forKeyCode(short keyCode) {
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
     * Convenience for callers that only have an Android keycode.
     */
    public static byte forAndroidKeyCode(int androidKeyCode) {
        short translated = new KeyboardTranslator().translate(androidKeyCode, -1);
        return translated == 0 ? 0 : forKeyCode(translated);
    }
}
