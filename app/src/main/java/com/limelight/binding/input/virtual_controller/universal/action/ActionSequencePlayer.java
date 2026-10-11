package com.limelight.binding.input.virtual_controller.universal.action;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns an action sequence into host input.
 *
 * A press walks the steps in order, and a release undoes them in reverse order so no
 * input is left held. Because a step can carry a delay, the steps after the first are
 * scheduled on the main looper; each scheduled run belongs to a generation, so a release
 * that arrives while a sequence is still running cancels what has not happened yet
 * instead of letting it fire after the element was let go.
 *
 * Only the inputs this player pressed are ever released, so two elements can hold
 * different inputs at the same time without disturbing each other.
 */
public class ActionSequencePlayer {
    /**
     * What the player needs from its owner to reach the host.
     */
    public interface HostInput {
        /**
         * Adds or removes gamepad button flags and lets the owner report the new state.
         */
        void setGamepadButtons(int flagsToAdd, int flagsToRemove);

        void sendMouseButton(int button, boolean down);

        void sendKeyboardKey(short keyCode, boolean down);
    }

    private final HostInput host;

    /**
     * Runs the delayed steps. A layout is built on the main thread, but a caller that
     * builds one elsewhere still gets a working player instead of an exception.
     */
    private final Handler handler;

    private ActionSequence activeSequence;
    private final List<InputEvent> pressedEvents = new ArrayList<>();
    private int generation;

    public ActionSequencePlayer(HostInput host) {
        this.host = host;

        Looper looper = Looper.myLooper();
        this.handler = looper != null ? new Handler(looper) : new Handler(Looper.getMainLooper());
    }

    /**
     * True while a press is running or while something it pressed is still held. A
     * toggle element uses this to decide whether the next tap presses or releases.
     */
    public boolean isHolding() {
        return !pressedEvents.isEmpty();
    }

    /**
     * Presses the sequence. A sequence that is already held is replaced, so a repeated
     * press event cannot leave duplicate inputs behind.
     */
    public void press(ActionSequence sequence) {
        if (sequence == null || sequence.isEmpty()) {
            return;
        }

        // Invalidate anything still scheduled before starting a new run
        generation++;

        handler.removeCallbacksAndMessages(null);

        releasePressed();

        activeSequence = sequence;
        playFrom(0, generation);
    }

    /**
     * Releases everything the last press sent, in reverse order.
     */
    public void release() {
        // Invalidate anything still scheduled, then undo what already happened
        generation++;
        handler.removeCallbacksAndMessages(null);

        releasePressed();
        activeSequence = null;
    }

    /**
     * Stops the sequence and releases what is held. The owner calls this before it goes
     * away, so the host is never left with a key that stays down.
     */
    public void cancel() {
        release();
    }

    private void playFrom(final int stepIndex, final int runGeneration) {
        if (runGeneration != generation || activeSequence == null) {
            // A release happened while this run was scheduled
            return;
        }

        if (stepIndex >= activeSequence.getSteps().size()) {
            return;
        }

        ActionStep step = activeSequence.getSteps().get(stepIndex);

        for (InputEvent event : step.getEvents()) {
            // Only an input that is not already down is pressed, so a sequence that lists
            // the same input twice cannot press it twice.
            if (!pressedEvents.contains(event) && applyEvent(event, true)) {
                pressedEvents.add(event);
            }
        }

        if (stepIndex + 1 < activeSequence.getSteps().size()) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    playFrom(stepIndex + 1, runGeneration);
                }
            }, Math.max(0, step.getDelayMs()));
        }
    }

    private void releasePressed() {
        // Undo the presses in reverse order, which is what a user expects from a
        // combination: the last thing that went down comes up first.
        for (int i = pressedEvents.size() - 1; i >= 0; i--) {
            applyEvent(pressedEvents.get(i), false);
        }

        pressedEvents.clear();
    }

    /**
     * Sends one input to the host.
     *
     * @return true when the input actually reached the host, so a sequence does not record
     *         an event it never sent
     */
    private boolean applyEvent(InputEvent event, boolean down) {
        if (event.isGamepad()) {
            int flag = event.getGamepadFlag();
            if (flag == 0) {
                // An unassigned button, which the editor allows as a placeholder
                return false;
            }

            host.setGamepadButtons(down ? flag : 0, down ? 0 : flag);
            return true;
        }

        if (event.isMouse()) {
            host.sendMouseButton(event.getCode(), down);
            return true;
        }

        if (event.isKeyboard()) {
            host.sendKeyboardKey((short) event.getCode(), down);
            return true;
        }

        return false;
    }
}
