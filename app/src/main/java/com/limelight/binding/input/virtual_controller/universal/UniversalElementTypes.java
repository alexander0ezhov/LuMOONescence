package com.limelight.binding.input.virtual_controller.universal;

/**
 * Names of the kinds of elements a universal layout can hold.
 *
 * The name is stored with an element's configuration so a saved layout can be rebuilt
 * even when the built-in layout of a newer version no longer defines that element.
 */
public final class UniversalElementTypes {
    /**
     * JSON key that carries the element type inside a saved configuration.
     */
    public static final String KEY_TYPE = "type";

    public static final String ELEMENT_BASE = "element";
    public static final String ELEMENT_BUTTON = "button";
    public static final String ELEMENT_STICK = "stick";
    public static final String ELEMENT_DPAD = "dpad";

    private UniversalElementTypes() {
    }
}
