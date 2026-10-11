package com.limelight.binding.input.virtual_controller.universal.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Something an on-screen control can drive on the host that is not a button: a gamepad
 * stick axis or the mouse pointer.
 */
public final class UniversalHostAxis {
    public static final int KIND_NONE = 0;
    public static final int KIND_GAMEPAD = 1;
    public static final int KIND_MOUSE = 2;

    public static final int TARGET_LEFT_X = 1;
    public static final int TARGET_LEFT_Y = 2;
    public static final int TARGET_RIGHT_X = 3;
    public static final int TARGET_RIGHT_Y = 4;

    public static final class Option {
        public final int kind;
        public final int target;
        public final String name;
        private final int code;

        Option(int code, int kind, int target, String name) {
            this.code = code;
            this.kind = kind;
            this.target = target;
            this.name = name;
        }
    }

    private static final List<Option> OPTIONS = new ArrayList<>();

    static {
        OPTIONS.add(new Option(0, KIND_NONE, 0, "Off"));
        OPTIONS.add(new Option(1, KIND_GAMEPAD, TARGET_LEFT_X, "Xbox left stick X"));
        OPTIONS.add(new Option(2, KIND_GAMEPAD, TARGET_LEFT_Y, "Xbox left stick Y"));
        OPTIONS.add(new Option(3, KIND_GAMEPAD, TARGET_RIGHT_X, "Xbox right stick X"));
        OPTIONS.add(new Option(4, KIND_GAMEPAD, TARGET_RIGHT_Y, "Xbox right stick Y"));
        OPTIONS.add(new Option(5, KIND_MOUSE, 0, "Mouse"));
    }

    private UniversalHostAxis() {
    }

    public static List<Option> options() {
        return OPTIONS;
    }

    public static int codeOf(int kind, int target) {
        for (Option option : OPTIONS) {
            if (option.kind == kind && option.target == target) {
                return option.code;
            }
        }

        return 0;
    }

    public static Option optionForCode(int code) {
        for (Option option : OPTIONS) {
            if (option.code == code) {
                return option;
            }
        }

        return OPTIONS.get(0);
    }

    public static int indexForCode(int code) {
        for (int i = 0; i < OPTIONS.size(); i++) {
            if (OPTIONS.get(i).code == code) {
                return i;
            }
        }

        return 0;
    }

    public static int codeForIndex(int index) {
        if (index < 0 || index >= OPTIONS.size()) {
            return 0;
        }

        return OPTIONS.get(index).code;
    }

    public static String nameForCode(int code) {
        return optionForCode(code).name;
    }
}
