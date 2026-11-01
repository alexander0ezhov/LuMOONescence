package com.limelight.binding.input.touch;

/**
 * Which fingers of a multi-touch gesture belong to the stream.
 *
 * The on-screen layout is a view of its own, so a finger that is holding a control is never
 * dispatched to the stream. The touch events the stream does receive still describe every finger
 * on the screen, though, which made a single finger on the background look like two fingers to
 * the touch input: one holding a control, one looking around. That turned a one finger look into
 * a two finger scroll, and the view did not move.
 *
 * This keeps track of the fingers the stream is actually following, so the touch input is told
 * about those alone.
 */
public class TouchPointerSlots {
    private final int[] pointerIds;

    public TouchPointerSlots(int slots) {
        pointerIds = new int[Math.max(1, slots)];

        clear();
    }

    /**
     * Notes that the finger with this pointer id is being followed in this slot.
     *
     * @return how many fingers the stream is following now
     */
    public int record(int slot, int pointerId) {
        if (slot < 0 || slot >= pointerIds.length) {
            return ownedCount();
        }

        pointerIds[slot] = pointerId;

        return ownedCount();
    }

    /**
     * Notes that the finger in this slot is gone.
     *
     * @return how many fingers the stream is following now
     */
    public int forget(int slot) {
        if (slot < 0 || slot >= pointerIds.length) {
            return ownedCount();
        }

        pointerIds[slot] = -1;

        return ownedCount();
    }

    public int ownedCount() {
        int count = 0;

        for (int pointerId : pointerIds) {
            if (pointerId != -1) {
                count++;
            }
        }

        return count;
    }

    /**
     * The pointer id followed in this slot, or -1.
     */
    public int idAt(int slot) {
        if (slot < 0 || slot >= pointerIds.length) {
            return -1;
        }

        return pointerIds[slot];
    }

    public int slotCount() {
        return pointerIds.length;
    }

    public void clear() {
        for (int i = 0; i < pointerIds.length; i++) {
            pointerIds[i] = -1;
        }
    }
}
