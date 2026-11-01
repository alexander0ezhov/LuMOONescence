package com.limelight.binding.input.virtual_controller.universal.action;

import com.limelight.nvstream.input.ControllerPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * The gamepad buttons an on-screen element can send.
 *
 * Every entry is addressed by its index, not by its packet flag, so an index stays
 * meaningful even if the flags are ever widened. Index 0 is "no button", which is what
 * an unassigned binding and a "nothing" entry in the editor use.
 */
public final class GamepadButtons {
    public static final int CODE_NONE = 0;

    public static final class Button {
        public final int code;
        public final String name;
        public final int flag;

        Button(int code, String name, int flag) {
            this.code = code;
            this.name = name;
            this.flag = flag;
        }
    }

    private static final List<Button> BUTTONS = new ArrayList<>();

    static {
        BUTTONS.add(new Button(CODE_NONE, "None", 0));
        BUTTONS.add(new Button(1, "A", ControllerPacket.A_FLAG));
        BUTTONS.add(new Button(2, "B", ControllerPacket.B_FLAG));
        BUTTONS.add(new Button(3, "X", ControllerPacket.X_FLAG));
        BUTTONS.add(new Button(4, "Y", ControllerPacket.Y_FLAG));
        BUTTONS.add(new Button(5, "D-pad Up", ControllerPacket.UP_FLAG));
        BUTTONS.add(new Button(6, "D-pad Down", ControllerPacket.DOWN_FLAG));
        BUTTONS.add(new Button(7, "D-pad Left", ControllerPacket.LEFT_FLAG));
        BUTTONS.add(new Button(8, "D-pad Right", ControllerPacket.RIGHT_FLAG));
        BUTTONS.add(new Button(9, "LB", ControllerPacket.LB_FLAG));
        BUTTONS.add(new Button(10, "RB", ControllerPacket.RB_FLAG));
        BUTTONS.add(new Button(11, "Left stick click", ControllerPacket.LS_CLK_FLAG));
        BUTTONS.add(new Button(12, "Right stick click", ControllerPacket.RS_CLK_FLAG));
        BUTTONS.add(new Button(13, "Start", ControllerPacket.PLAY_FLAG));
        BUTTONS.add(new Button(14, "Back", ControllerPacket.BACK_FLAG));
        BUTTONS.add(new Button(15, "Guide", ControllerPacket.SPECIAL_BUTTON_FLAG));
        BUTTONS.add(new Button(16, "Touchpad", ControllerPacket.TOUCHPAD_FLAG));
        BUTTONS.add(new Button(17, "Paddle 1", ControllerPacket.PADDLE1_FLAG));
        BUTTONS.add(new Button(18, "Paddle 2", ControllerPacket.PADDLE2_FLAG));
        BUTTONS.add(new Button(19, "Paddle 3", ControllerPacket.PADDLE3_FLAG));
        BUTTONS.add(new Button(20, "Paddle 4", ControllerPacket.PADDLE4_FLAG));
        BUTTONS.add(new Button(21, "Share / Capture", ControllerPacket.MISC_FLAG));
    }

    private GamepadButtons() {
    }

    public static List<Button> all() {
        return BUTTONS;
    }

    public static String nameForCode(int code) {
        for (Button button : BUTTONS) {
            if (button.code == code) {
                return button.name;
            }
        }

        return "None";
    }

    /**
     * Returns the gamepad packet flag of a button code, or 0 for CODE_NONE and unknown
     * codes.
     */
    public static int flagForCode(int code) {
        for (Button button : BUTTONS) {
            if (button.code == code) {
                return button.flag;
            }
        }

        return 0;
    }

    /**
     * Returns the button code of a gamepad packet flag, which is how the built-in layout
     * states what a button sends.
     */
    public static int codeOfFlag(int flag) {
        for (Button button : BUTTONS) {
            if (button.flag != 0 && button.flag == flag) {
                return button.code;
            }
        }

        return CODE_NONE;
    }

    public static int indexForCode(int code) {        for (int i = 0; i < BUTTONS.size(); i++) {
            if (BUTTONS.get(i).code == code) {
                return i;
            }
        }

        return 0;
    }

    public static int codeForIndex(int index) {
        if (index < 0 || index >= BUTTONS.size()) {
            return CODE_NONE;
        }

        return BUTTONS.get(index).code;
    }
}
