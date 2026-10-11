import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.input.virtual_controller.universal.action.ActionSequence;
import com.limelight.binding.input.virtual_controller.universal.action.ActionStep;
import com.limelight.binding.input.virtual_controller.universal.action.GamepadButtons;
import com.limelight.binding.input.virtual_controller.universal.action.InputEvent;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalButtonConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalGeometry;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalHostAxis;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalPadConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalStickConfig;
import com.limelight.nvstream.input.MouseButtonPacket;

import android.view.KeyEvent;

import org.json.JSONObject;

import java.util.List;

/**
 * Off-device check of the layout data model: the part of the feature that decides what a
 * layout means, and the part that would be most annoying to debug through the UI.
 */
public class LayoutModelHarness {
    private static int failures;

    public static void main(String[] args) throws Exception {
        checkActionSequenceRoundTrip();
        checkCombinationAndDelays();
        checkCopyIsDeep();
        checkButtonConfigRoundTrip();
        checkStickConfigRoundTrip();
        checkPadConfigRoundTrip();
        checkLegacyGeometry();
        checkKeyboardCodeForR();
        checkGamepadFlags();

        if (failures == 0) {
            System.out.println("ALL CHECKS PASSED");
        }
        else {
            System.out.println(failures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ checks

    private static void checkActionSequenceRoundTrip() throws Exception {
        // The example from the feature request: right button, wait 200 ms, left button
        ActionSequence sequence = ActionSequence.delayed(
                new ActionStep(new InputEvent(InputEvent.KIND_MOUSE, MouseButtonPacket.BUTTON_RIGHT)),
                new ActionStep(new InputEvent(InputEvent.KIND_MOUSE, MouseButtonPacket.BUTTON_LEFT))
                        .withDelay(200));

        ActionSequence restored = ActionSequence.fromJson(
                new JSONObject(sequence.toJson().toString()));

        assertEquals("steps survive a round trip", 2, restored.getSteps().size());
        assertEquals("first step has one event", 1, restored.getSteps().get(0).getEvents().size());
        assertEquals("delay survives a round trip", 200, (int) restored.getSteps().get(1).getDelayMs());
        assertTrue("the same movement is recognised", sequence.describesSameMovementAs(restored));
    }

    private static void checkCombinationAndDelays() throws Exception {
        // Two inputs in one step start together, which is what a combination is
        ActionSequence combo = ActionSequence.combo(
                new InputEvent(InputEvent.KIND_KEYBOARD, 0x8052),
                new InputEvent(InputEvent.KIND_GAMEPAD, 1));

        assertEquals("a combination is one step", 1, combo.getSteps().size());
        assertEquals("a combination holds both events", 2, combo.getSteps().get(0).getEvents().size());

        ActionSequence different = ActionSequence.combo(
                new InputEvent(InputEvent.KIND_KEYBOARD, 0x8052),
                new InputEvent(InputEvent.KIND_GAMEPAD, 2));
        assertTrue("a different combination is not the same movement",
                !combo.describesSameMovementAs(different));
    }

    private static void checkCopyIsDeep() throws Exception {
        UniversalButtonConfig original = new UniversalButtonConfig();
        original.getActionSequence().addStep(new ActionStep(
                new InputEvent(InputEvent.KIND_MOUSE, MouseButtonPacket.BUTTON_LEFT)));
        original.setToggle(true);
        original.setName("Fire");

        UniversalButtonConfig copy = (UniversalButtonConfig) original.copy();

        // Changing the copy must not change the original, otherwise duplicating an element
        // would edit both
        copy.getActionSequence().getSteps().get(0).getEvents().clear();
        copy.getActionSequence().addStep(new ActionStep(
                new InputEvent(InputEvent.KIND_MOUSE, MouseButtonPacket.BUTTON_RIGHT)));

        assertEquals("the original keeps its step", 1, original.getActionSequence().getSteps().size());
        assertEquals("the original keeps its event", 1,
                original.getActionSequence().getSteps().get(0).getEvents().size());
        assertEquals("the copy keeps the name", "Fire", copy.getName());
        assertTrue("the copy keeps the toggle flag", copy.isToggle());
    }

    private static void checkButtonConfigRoundTrip() throws Exception {
        UniversalButtonConfig config = new UniversalButtonConfig();
        config.setName("Reload");
        config.setToggle(true);
        config.getGeometry().setLeft(11);
        config.getGeometry().setTop(22);
        config.getGeometry().setWidth(33);
        config.getGeometry().setHeight(44);
        config.getActionSequence().addStep(new ActionStep(
                new InputEvent(InputEvent.KIND_KEYBOARD, 0x8052)));

        UniversalButtonConfig restored = new UniversalButtonConfig();
        restored.loadFromJson(new JSONObject(config.toJson().toString()));

        assertEquals("name", "Reload", restored.getName());
        assertTrue("toggle", restored.isToggle());
        assertEquals("left", 11, restored.getGeometry().getLeft());
        assertEquals("top", 22, restored.getGeometry().getTop());
        assertEquals("width", 33, restored.getGeometry().getWidth());
        assertEquals("height", 44, restored.getGeometry().getHeight());
        assertEquals("action", 1, restored.getActionSequence().getSteps().size());
    }

    private static void checkStickConfigRoundTrip() throws Exception {
        UniversalStickConfig config = new UniversalStickConfig();
        config.setMode(UniversalStickConfig.MODE_BUTTONS);
        config.setAxisX(UniversalHostAxis.codeOf(UniversalHostAxis.KIND_MOUSE, 0));
        config.setAxisY(UniversalHostAxis.codeOf(UniversalHostAxis.KIND_GAMEPAD,
                UniversalHostAxis.TARGET_RIGHT_Y));
        config.getButton(UniversalStickConfig.DIRECTION_LEFT).setButtonCode(7);
        config.getButton(UniversalStickConfig.DIRECTION_LEFT).getActionSequence().addStep(
                new ActionStep(new InputEvent(InputEvent.KIND_GAMEPAD, 7)));

        UniversalStickConfig restored = new UniversalStickConfig();
        restored.loadFromJson(new JSONObject(config.toJson().toString()));

        assertEquals("mode", UniversalStickConfig.MODE_BUTTONS, restored.getMode());
        assertEquals("axis X", config.getAxisX(), restored.getAxisX());
        assertEquals("axis Y", config.getAxisY(), restored.getAxisY());
        assertEquals("bound button", 7, restored.getButton(UniversalStickConfig.DIRECTION_LEFT).getButtonCode());
        assertTrue("unbound direction stays unassigned",
                !restored.getButton(UniversalStickConfig.DIRECTION_UP).isAssigned());
    }

    private static void checkPadConfigRoundTrip() throws Exception {
        UniversalPadConfig config = new UniversalPadConfig();
        config.setDirectionSequence(UniversalPadConfig.DIRECTION_COUNT - 1,
                ActionSequence.single(new InputEvent(InputEvent.KIND_KEYBOARD, 0x8042)));

        UniversalPadConfig restored = new UniversalPadConfig();
        restored.loadFromJson(new JSONObject(config.toJson().toString()));

        assertTrue("the last direction survives",
                !restored.getDirectionSequence(UniversalPadConfig.DIRECTION_COUNT - 1).isEmpty());
        assertTrue("the first direction stays empty",
                restored.getDirectionSequence(0).isEmpty());
    }

    private static void checkLegacyGeometry() throws Exception {
        // A layout saved before the element model existed only carried geometry
        JSONObject legacy = new JSONObject();
        legacy.put("LEFT", 100);
        legacy.put("TOP", 200);
        legacy.put("WIDTH", 50);
        legacy.put("HEIGHT", 60);

        UniversalButtonConfig config = new UniversalButtonConfig();
        config.loadFromJson(legacy);

        assertEquals("legacy left", 100, config.getGeometry().getLeft());
        assertEquals("legacy top", 200, config.getGeometry().getTop());
        assertEquals("legacy width", 50, config.getGeometry().getWidth());
        assertEquals("legacy height", 60, config.getGeometry().getHeight());
        assertTrue("legacy config has no action", config.getActionSequence().isEmpty());

        UniversalGeometry geometry = UniversalGeometry.fromJson(legacy);
        assertEquals("geometry helper reads the legacy form", 100, geometry.getLeft());
    }

    private static void checkKeyboardCodeForR() throws Exception {
        // The R key the feature started with. The name catalogue needs a real KeyEvent, so
        // this checks the translation itself, which is the part that decides what a key
        // actually sends to the host.
        KeyboardTranslator translator = new KeyboardTranslator();

        short rKeyCode = translator.translate(KeyEvent.KEYCODE_R, -1);
        assertEquals("R translates to the GFE code of VK_R", 0x8052, rKeyCode & 0xFFFF);

        short aKeyCode = translator.translate(KeyEvent.KEYCODE_A, -1);
        assertEquals("A translates to the GFE code of VK_A", 0x8041, aKeyCode & 0xFFFF);

        assertEquals("an unmapped key translates to nothing",
                0, translator.translate(KeyEvent.KEYCODE_UNKNOWN, -1) & 0xFFFF);

        // A key event for R must survive the model as a keyboard event
        ActionSequence sequence = ActionSequence.single(
                new InputEvent(InputEvent.KIND_KEYBOARD, rKeyCode & 0xFFFF));
        ActionSequence restored = ActionSequence.fromJson(
                new JSONObject(sequence.toJson().toString()));

        InputEvent event = restored.getSteps().get(0).getEvents().get(0);
        assertTrue("the R event is a keyboard event", event.isKeyboard());
        assertEquals("the R event keeps its code", 0x8052, event.getCode());
    }

    private static void checkGamepadFlags() throws Exception {
        assertEquals("A resolves", 0x1000, GamepadButtons.flagForCode(1));
        assertEquals("A round trips", 1, GamepadButtons.codeOfFlag(0x1000));

        // A code the editor never offers must not resolve to a flag
        assertEquals("unknown code has no flag", 0, GamepadButtons.flagForCode(999));

        List<GamepadButtons.Button> buttons = GamepadButtons.all();
        assertTrue("the catalog has more than just None", buttons.size() > 5);
        assertEquals("None is first", GamepadButtons.CODE_NONE, buttons.get(0).code);

        // Every offered button must be able to send something
        for (GamepadButtons.Button button : buttons) {
            if (button.code != GamepadButtons.CODE_NONE) {
                assertTrue("button " + button.name + " has a flag", button.flag != 0);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void assertEquals(String what, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            System.out.println("ok   " + what);
            return;
        }

        failures++;
        System.out.println("FAIL " + what + ": expected <" + expected + "> but was <" + actual + ">");
    }

    private static void assertTrue(String what, boolean condition) {
        if (condition) {
            System.out.println("ok   " + what);
            return;
        }

        failures++;
        System.out.println("FAIL " + what);
    }
}
