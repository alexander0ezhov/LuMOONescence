package com.limelight.debug;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import android.graphics.Canvas;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.virtual_controller.universal.UniversalAnalogStick;
import com.limelight.binding.input.virtual_controller.universal.UniversalDigitalButton;
import com.limelight.binding.input.virtual_controller.universal.UniversalDigitalPad;
import com.limelight.binding.input.virtual_controller.universal.UniversalElementTypes;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutConfigurationLoader;
import com.limelight.binding.input.virtual_controller.universal.UniversalLeftTrigger;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutDefinition;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutEditorActivity;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutStore;
import com.limelight.binding.input.virtual_controller.universal.UniversalVirtualController;
import com.limelight.binding.input.virtual_controller.universal.UniversalVirtualControllerElement;
import com.limelight.binding.input.virtual_controller.universal.action.ActionSequence;
import com.limelight.binding.input.virtual_controller.universal.action.ActionSequencePlayer;
import com.limelight.binding.input.virtual_controller.universal.action.InputEvent;
import com.limelight.binding.input.virtual_controller.universal.action.InputEventNames;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalButtonConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalElementConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalGeometry;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalStickButtonConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalStickConfig;
import com.limelight.binding.input.virtual_controller.universal.editor.UniversalEditorPanel;
import com.limelight.binding.input.virtual_controller.universal.editor.UniversalElementEditorBuilder;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Runs a set of checks against the layout editor on a device and prints the result.
 *
 * This exists because the device forbids injecting input events, so the editor cannot be
 * driven through adb. Two kinds of check are done here:
 *
 * - calls to the same operations the UI calls: duplicate, delete, save, rebuild a layout;
 * - synthetic touch events dispatched into a control, which is the path a real finger takes,
 *   so pressing, dragging and holding a control are actually exercised.
 *
 * It lives in the debug source set, so it is not part of a release build.
 */
public class LayoutSmokeActivity extends Activity {
    public static final String TAG = "LayoutSmoke";

    /**
     * How long to wait for the editor to open and lay its controls out before checking.
     */
    private static final long EDITOR_SETTLE_MS = 2500;

    /**
     * A hold longer than the editor's own long press timeout, plus a margin so the check
     * cannot fail because two delays landed close together.
     */
    private static final long LONG_PRESS_HOLD_MS = 900;

    /**
     * How often the drag check sends a move event. A drag arrives as a stream of moves, and
     * a single one can be treated as a stray jitter by the gesture handling.
     */
    private static final int DRAG_STEPS = 6;

    /**
     * How long to wait for a dialog to appear after it was asked for.
     */
    private static final long DIALOG_SETTLE_MS = 800;

    private int failures;
    private int checks;

    private UniversalVirtualController universalController;
    private UniversalLayoutEditorActivity editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(this, UniversalLayoutEditorActivity.class);
        intent.putExtra(UniversalLayoutEditorActivity.EXTRA_LAYOUT_ID, "default");
        startActivity(intent);

        postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    runChecks();
                } catch (Throwable t) {
                    Log.e(TAG, "the checks themselves failed", t);
                    failures++;
                    report();
                }
            }
        }, EDITOR_SETTLE_MS);
    }

    private void postDelayed(Runnable runnable, long delayMs) {
        new Handler(Looper.getMainLooper()).postDelayed(runnable, delayMs);
    }

    private void runChecks() {
        UniversalLayoutStore store = UniversalLayoutStore.getInstance(this);

        checkStore(store);

        if (!checkEditor(store)) {
            report();
            return;
        }

        checkPersistence(store);
        checkGestures();

        store.setSelectedLayoutId("default");
    }

    // ------------------------------------------------------------------ store

    private void checkStore(UniversalLayoutStore store) {
        UniversalLayoutDefinition defaultLayout = store.getLayout("default");
        check("the built-in layout exists", defaultLayout != null);

        UniversalLayoutDefinition created = store.addLayout(store.makeUniqueName("smoke layout"));
        check("a layout can be created", created != null && store.getLayout(created.getId()) != null);
        check("a new layout becomes the selected one",
                created != null && created.getId().equals(store.getSelectedLayoutId()));

        if (created == null) {
            return;
        }

        String name = created.getName();
        check("a duplicate name is made unique", !store.makeUniqueName(name).equals(name));

        store.setSelectedLayoutId(created.getId());
        check("the selected layout can be changed",
                created.getId().equals(store.getSelectedLayoutId()));

        String id = created.getId();
        store.deleteLayout(id);
        check("a layout can be deleted", store.getLayout(id) == null);
        check("deleting the selected layout selects another",
                !id.equals(store.getSelectedLayoutId()));
    }

    // ------------------------------------------------------------------ editor

    private boolean checkEditor(UniversalLayoutStore store) {
        editor = UniversalLayoutEditorActivity.getLastCreatedInstance();
        check("the editor is open", editor != null);

        if (editor == null) {
            return false;
        }

        Object controllerField = getField(editor, "controller");
        check("the editor built a controller", controllerField instanceof UniversalVirtualController);

        if (!(controllerField instanceof UniversalVirtualController)) {
            return false;
        }

        universalController = (UniversalVirtualController) controllerField;

        List<UniversalVirtualControllerElement> elements = universalController.getElements();
        check("the layout has controls", !elements.isEmpty());

        // Nothing is selected yet, so the drawer must not be on screen
        final View drawer = editor.findViewById(drawerId());
        check("the drawer is hidden while nothing is selected",
                drawer != null && drawer.getVisibility() != View.VISIBLE);

        boolean everyElementHasConfig = true;
        for (UniversalVirtualControllerElement element : elements) {
            if (element.getConfig() == null || element.getElementTypeName() == null) {
                everyElementHasConfig = false;
                break;
            }
        }
        check("every control has a configuration", everyElementHasConfig);

        UniversalVirtualControllerElement first = elements.get(0);
        editor.onElementTapped(first);
        check("a tapped control becomes the selected one",
                editor.getSelectedElementForTesting() == first);
        check("the selected control is marked", first.isSelected());
        check("the panel has rows for the selected control", countPanelRows() > 3);

        int before = universalController.getElements().size();
        UniversalVirtualControllerElement copy = universalController.duplicateElement(first);
        check("a control can be duplicated", copy != null && copy != first);
        check("the duplicate is added", universalController.getElements().size() == before + 1);

        if (copy != null) {
            check("the duplicate has its own ID", copy.getElementId() != first.getElementId());

            UniversalGeometry copiedSize = UniversalGeometry.of(first).copy();
            copiedSize.applyTo(copy);
            check("a size can be pasted onto another control",
                    UniversalGeometry.of(copy).getWidth() == UniversalGeometry.of(first).getWidth());

            int withCopy = universalController.getElements().size();
            universalController.removeElement(copy);
            check("a control can be deleted",
                    universalController.getElements().size() == withCopy - 1);
        }

        int beforeAdd = universalController.getElements().size();
        UniversalVirtualControllerElement addedButton =
                universalController.addUserElement("button", 10, 10, 120, 120);
        check("a button can be added", addedButton != null);
        check("the added button has a button configuration",
                addedButton != null && addedButton.getButtonConfigIfAny() != null);

        UniversalVirtualControllerElement addedStick =
                universalController.addUserElement("stick", 40, 40, 200, 200);
        check("a stick can be added", addedStick != null);
        check("the added stick has a stick configuration",
                addedStick != null && addedStick.getStickConfigIfAny() != null);
        check("two controls were added",
                universalController.getElements().size() == beforeAdd + 2);

        if (addedStick != null) {
            editor.onElementTapped(addedStick);
            check("the stick becomes the selected one",
                    editor.getSelectedElementForTesting() == addedStick);
            check("the panel has rows for a stick", countPanelRows() > 3);
        }

        if (addedButton != null) {
            editor.onElementTapped(addedButton);
            check("the button becomes the selected one again",
                    editor.getSelectedElementForTesting() == addedButton);
        }

        universalController.markConfigurationDirty();
        check("the layout can be marked as having unsaved changes",
                universalController.isLayoutDirty());

        universalController.clearLayoutDirty();
        check("the unsaved marker can be cleared", !universalController.isLayoutDirty());

        return true;
    }

    // ------------------------------------------------------------------ persistence

    private void checkPersistence(UniversalLayoutStore store) {
        UniversalLayoutDefinition layout = store.addLayout(store.makeUniqueName("smoke persistence"));

        try {
            int elementCount = universalController.getElements().size();

            UniversalLayoutConfigurationLoader.saveProfile(universalController, this,
                    layout.getId(), 1920, 1080);

            String fileName = UniversalLayoutConfigurationLoader
                    .getLayoutPreferenceFileName(layout.getId());
            String saved = getSharedPreferences(fileName, Context.MODE_PRIVATE)
                    .getString("elements", null);
            int savedWidth = getSharedPreferences(fileName, Context.MODE_PRIVATE)
                    .getInt("savedWidth", 0);

            check("the layout was saved", saved != null && !saved.isEmpty());
            check("the saved layout records its size", savedWidth == 1920);

            if (saved != null) {
                check("every control was saved",
                        countOccurrences(saved, "\"type\"") == elementCount);
                check("the saved form carries element IDs", saved.contains("\"elementId\""));
                check("the saved form carries actions", saved.contains("\"sequence\""));

                UniversalVirtualController restored = new UniversalVirtualController(
                        null, new android.widget.FrameLayout(this), this, layout.getId());
                restored.refreshLayout();

                check("the saved layout can be rebuilt",
                        restored.getElements().size() == elementCount);
            }
        } finally {
            store.deleteLayout(layout.getId());
        }
    }

    // ------------------------------------------------------------------ gestures

    /**
     * Presses, drags and holds a control with synthetic touch events.
     *
     * The events are dispatched to the control itself, which is the same path a real touch
     * takes once it reaches the control, so the editor's own press watching, drag handling
     * and long press run for real.
     */
    private void checkGestures() {
        List<UniversalVirtualControllerElement> elements = universalController.getElements();
        if (elements.isEmpty()) {
            check("there is a control to touch", false);
            report();
            return;
        }

        final UniversalVirtualControllerElement target = elements.get(0);
        final UniversalGeometry originalGeometry = UniversalGeometry.of(target).copy();

        // A listener that records what the editor is told, so a hold can be observed rather
        // than only its effect on the arrangement.
        holdHandled = false;
        universalController.setElementInteractionListener(
                new UniversalVirtualController.ElementInteractionListener() {
                    @Override
                    public void onElementTapped(UniversalVirtualControllerElement element) {
                        editor.onElementTapped(element);
                    }

                    @Override
                    public boolean onElementLongPressed(UniversalVirtualControllerElement element) {
                        holdHandled = true;
                        return editor.onElementLongPressed(element);
                    }

                    @Override
                    public void onElementGeometrySettled(UniversalVirtualControllerElement element) {
                        editor.onElementGeometrySettled(element);
                    }

                    @Override
                    public void onLayoutAdjustedToFit() {
                    }
                });

        // The drawer follows the selection
        final View drawer = editor.findViewById(drawerId());

        dispatchTouch(target, MotionEvent.ACTION_DOWN, 0.5f, 0.5f);
        dispatchTouch(target, MotionEvent.ACTION_UP, 0.5f, 0.5f);

        check("a tap on a control selects it", editor.getSelectedElementForTesting() == target);
        check("selecting a control shows the drawer",
                drawer != null && drawer.getVisibility() == View.VISIBLE);

        // Tapping the empty part of the arrangement puts the drawer away; the overlay reports
        // that case when no control took the touch
        editor.onElementTapped(null);
        check("tapping empty space clears the selection",
                editor.getSelectedElementForTesting() == null);
        check("tapping empty space hides the drawer",
                drawer != null && drawer.getVisibility() != View.VISIBLE);

        // The same, but through the touch handling the overlay uses, so the handler that
        // closes the drawer is exercised rather than only the method it calls
        editor.onElementTapped(target);
        dispatchOverlayTouch(MotionEvent.ACTION_DOWN, 4000, 900);
        dispatchOverlayTouch(MotionEvent.ACTION_UP, 4000, 900);

        check("a touch on empty space clears the selection",
                editor.getSelectedElementForTesting() == null);
        check("a touch on empty space hides the drawer",
                drawer != null && drawer.getVisibility() != View.VISIBLE);

        // A drag must move the control
        int startLeft = UniversalGeometry.of(target).getLeft();
        int startTop = UniversalGeometry.of(target).getTop();

        dispatchTouch(target, MotionEvent.ACTION_DOWN, 0.1f, 0.1f);

        for (int step = 1; step <= DRAG_STEPS; step++) {
            float fraction = 0.1f + (0.7f * step / DRAG_STEPS);
            dispatchTouch(target, MotionEvent.ACTION_MOVE, fraction, fraction);
        }

        dispatchTouch(target, MotionEvent.ACTION_UP, 0.8f, 0.8f);

        int movedLeft = UniversalGeometry.of(target).getLeft();
        int movedTop = UniversalGeometry.of(target).getTop();

        check("dragging a control moves it", movedLeft != startLeft || movedTop != startTop);
        check("dragging keeps the control at or after the origin", movedLeft >= 0 && movedTop >= 0);

        // Put the control back, so the check does not disturb the layout being looked at
        originalGeometry.applyTo(target);
        check("the control can be put back", UniversalGeometry.of(target).getLeft() == startLeft);

        // A hold must reach the editor as a hold, which is what opens the context menu
        holdTarget = target;
        holdStartLeft = UniversalGeometry.of(target).getLeft();

        dispatchTouch(target, MotionEvent.ACTION_DOWN, 0.5f, 0.5f);

        postDelayed(new Runnable() {
            @Override
            public void run() {
                dispatchTouch(holdTarget, MotionEvent.ACTION_UP, 0.5f, 0.5f);

                check("holding a control reaches the editor as a hold", holdHandled);
                check("holding a control does not drag it",
                        UniversalGeometry.of(holdTarget).getLeft() == holdStartLeft);

                checkSettingsButton();
            }
        }, LONG_PRESS_HOLD_MS);
    }

    /**
     * The floating settings button is always available, whatever is selected, and its menu
     * carries the operations that act on the whole layout.
     */
    private void checkSettingsButton() {
        final View button = editor.getEditorRootView().findViewById(settingsButtonId());
        check("the floating settings button is on screen", button != null);

        if (button == null) {
            report();
            return;
        }

        check("the floating settings button is visible", button.getVisibility() == View.VISIBLE);

        dispatchTap(button);

        postDelayed(new Runnable() {
            @Override
            public void run() {
                AlertDialog menu = editor.getVisibleDialogForTesting();
                check("the settings button opens the layout menu", menu != null);

                if (menu != null) {
                    String[] labels = listItemLabels(menu);
                    check("the layout menu offers adding a button", contains(labels, "button"));
                    check("the layout menu offers adding a stick", contains(labels, "stick"));
                    check("the layout menu offers adding a D-pad", contains(labels, "d-pad"));
                    check("the layout menu offers saving", contains(labels, "save"));
                    check("the layout menu offers saving as", contains(labels, "save as"));
                    check("the layout menu offers leaving", contains(labels, "exit"));

                    // "Save as" must actually put a dialog on screen, which it did not while
                    // the dialog was built without being shown
                    clickMenuItem("save as");
                }

                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog saveAs = editor.getVisibleDialogForTesting();
                        check("choosing save as opens a dialog", saveAs != null);

                        if (saveAs != null) {
                            check("the save as dialog asks for a name",
                                    saveAs.getButton(DialogInterface.BUTTON_POSITIVE) != null);
                            saveAs.dismiss();
                        }

                        checkBackHandling();
                        report();
                    }
                }, DIALOG_SETTLE_MS);
            }
        }, DIALOG_SETTLE_MS);
    }

    /**
     * Answers the dialog that is on screen by the label of one of its entries.
     */
    private void clickMenuItem(String label) {
        AlertDialog menu = editor.getVisibleDialogForTesting();
        if (menu == null || menu.getListView() == null || menu.getListView().getAdapter() == null) {
            return;
        }

        android.widget.ListView list = menu.getListView();
        for (int i = 0; i < list.getAdapter().getCount(); i++) {
            if (String.valueOf(list.getAdapter().getItem(i)).toLowerCase().contains(label)) {
                menu.getListView().performItemClick(list.getChildAt(i), i,
                        list.getAdapter().getItemId(i));
                return;
            }
        }
    }

    /**
     * Sends a tap to a view, which is how a button with a touch listener rather than a click
     * listener is pressed.
     */
    private void dispatchTap(View view) {
        long now = SystemClock.uptimeMillis();
        float x = Math.max(1, view.getWidth() / 2f);
        float y = Math.max(1, view.getHeight() / 2f);

        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        view.dispatchTouchEvent(down);
        down.recycle();

        MotionEvent up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, x, y, 0);
        view.dispatchTouchEvent(up);
        up.recycle();
    }

    private int settingsButtonId() {
        return getResources().getIdentifier("layoutEditorSettingsButton", "id", getPackageName());
    }

    /**
     * Checks that changing one of the size sliders does not disturb the other.
     *
     * The editor applies a change by writing all four values at once, so a stale value for
     * any of them would silently undo the change that was just made.
     */
    private void checkSliderIndependence() {
        Object panelField = getField(editor, "panel");
        if (!(panelField instanceof UniversalEditorPanel)) {
            check("the drawer panel was found", false);
            return;
        }

        UniversalEditorPanel drawer = (UniversalEditorPanel) panelField;

        String widthLabel = getString(com.limelight.R.string.editor_label_width);
        String heightLabel = getString(com.limelight.R.string.editor_label_height);

        UniversalEditorPanel.SliderRow widthRow = drawer.getSliderRow(widthLabel);
        UniversalEditorPanel.SliderRow heightRow = drawer.getSliderRow(heightLabel);

        check("the width slider is on screen", widthRow != null);
        check("the height slider is on screen", heightRow != null);

        if (widthRow == null || heightRow == null) {
            return;
        }

        int originalWidth = widthRow.getValue();
        int originalHeight = heightRow.getValue();

        int newWidth = Math.max(24, originalWidth - 40);
        int newHeight = Math.max(24, originalHeight - 30);

        // Both changes stay inside the slider range, so the check cannot be confused by a
        // value the slider refuses to show.
        newWidth = Math.min(newWidth, 900);
        newHeight = Math.min(newHeight, 700);

        int startHeight = heightRow.getValue();

        // The width slider is dragged the way a finger drags it
        widthRow.dragTo(newWidth);

        UniversalGeometry afterWidth = editor.getSelectedGeometryForTesting();
        check("dragging the width slider applies the width",
                afterWidth != null && afterWidth.getWidth() == newWidth);
        check("dragging the width slider leaves the height alone",
                afterWidth != null && afterWidth.getHeight() == startHeight);

        // Then the height slider, whose rows were built before the width was dragged
        heightRow.dragTo(newHeight);

        UniversalGeometry afterHeight = editor.getSelectedGeometryForTesting();
        check("dragging the height slider applies the height",
                afterHeight != null && afterHeight.getHeight() == newHeight);
        check("dragging the height slider keeps the dragged width",
                afterHeight != null && afterHeight.getWidth() == newWidth);

        // Put the original size back, using the rows the drawer now has
        UniversalEditorPanel.SliderRow widthNow =
                drawer.getSliderRow(getString(com.limelight.R.string.editor_label_width));
        UniversalEditorPanel.SliderRow heightNow =
                drawer.getSliderRow(getString(com.limelight.R.string.editor_label_height));

        if (widthNow != null) {
            widthNow.dragTo(originalWidth);
        }
        if (heightNow != null) {
            heightNow.dragTo(originalHeight);
        }

        checkRebuiltRows();
        checkStickCenterButton();
        checkTriggerEvents();
        checkDpadCanBeAdded();
        checkBorderRadiusAndCopyPaste();
        checkButtonHighlightReturns();
        checkLayoutOpacity();
        checkDpadTouchZones();
        checkDraggingMarksLayoutDirty();
        checkDraggedGeometryIsSaved();
        checkOnScreenLayoutRegistersGamepad();
        checkTriggersWorkOnTheirOwn();
        checkAxisCombiningKeepsTheStrongerValue();
        checkStickDirectionCanSendAKey();
        checkNewControlAdoptsTheOpacity();
        checkDpadArmsLookTheSame();
        checkToggleOffLooksLikeUntouched();
        checkDpadArmsAreIdentical();
        checkDpadGlowMatchesAButton();
    }

    /**
     * Moving a control counts as an unsaved change even when it is not the one being edited,
     * which is the case a plain drag makes.
     */
    private void checkDraggingMarksLayoutDirty() {
        // A control that is not the selected one, because a drag does not select
        UniversalVirtualControllerElement other = null;
        for (UniversalVirtualControllerElement element : universalController.getElements()) {
            if (element != editor.getSelectedElementForTesting()) {
                other = element;
                break;
            }
        }

        check("there is a control that is not being edited", other != null);
        if (other == null) {
            return;
        }

        editor.setControllerModeForTesting(UniversalVirtualController.ControllerMode.MoveButtons);

        // A drag of its own, which is what the user does before leaving the editor
        dispatchTouch(other, MotionEvent.ACTION_DOWN, 0.5f, 0.5f);
        dispatchTouch(other, MotionEvent.ACTION_MOVE, 0.75f, 0.75f);
        dispatchTouch(other, MotionEvent.ACTION_MOVE, 0.85f, 0.85f);
        dispatchTouch(other, MotionEvent.ACTION_UP, 0.85f, 0.85f);

        check("moving a control that is not being edited is an unsaved change",
                universalController.isLayoutDirty());
    }

    /**
     * An analog trigger is a kind of its own, because the host takes it as a byte of the
     * controller packet rather than as a button flag.
     */
    private void checkTriggerEvents() {
        UniversalVirtualControllerElement element = editor.getSelectedElementForTesting();
        UniversalVirtualController controller = universalController;

        if (element == null) {
            check("a control is selected for the trigger check", false);
            return;
        }

        final int[] lastTrigger = new int[]{-1, -1};
        final ActionSequencePlayer.HostInput real = controller.getHostInput();

        // Guards the real host input and records what the sequence sends, so the value that
        // would reach the host is what is looked at
        ActionSequencePlayer.HostInput recorder = new ActionSequencePlayer.HostInput() {
            @Override
            public void setGamepadButtons(int flagsToAdd, int flagsToRemove) {
                real.setGamepadButtons(flagsToAdd, flagsToRemove);
            }

            @Override
            public void setTrigger(int trigger, int value) {
                lastTrigger[trigger] = value;
                real.setTrigger(trigger, value);
            }

            @Override
            public void sendMouseButton(int button, boolean down) {
                real.sendMouseButton(button, down);
            }

            @Override
            public void sendKeyboardKey(short keyCode, boolean down) {
                real.sendKeyboardKey(keyCode, down);
            }
        };

        ActionSequencePlayer player = new ActionSequencePlayer(recorder);

        ActionSequence sequence = ActionSequence.single(
                new InputEvent(InputEvent.KIND_TRIGGER, InputEvent.TRIGGER_LEFT));
        sequence.getSteps().get(0).addEvent(
                new InputEvent(InputEvent.KIND_TRIGGER, InputEvent.TRIGGER_RIGHT));

        player.press(sequence);

        check("a trigger event pulls the left trigger",
                lastTrigger[InputEvent.TRIGGER_LEFT] == InputEvent.TRIGGER_FULLY_PULLED);
        check("a trigger event pulls the right trigger",
                lastTrigger[InputEvent.TRIGGER_RIGHT] == InputEvent.TRIGGER_FULLY_PULLED);

        player.release();

        check("releasing a trigger event lets the left trigger out",
                lastTrigger[InputEvent.TRIGGER_LEFT] == 0);
        check("releasing a trigger event lets the right trigger out",
                lastTrigger[InputEvent.TRIGGER_RIGHT] == 0);

        // The triggers the check pulled are put back, so the layout is not left with them on
        universalController.getControllerInputContext().leftTrigger = 0;
        universalController.getControllerInputContext().rightTrigger = 0;
    }

    /**
     * The middle of a stick acts as a button when it is switched on and bound, and a touch
     * that stays in the middle is what presses it.
     */
    private void checkStickCenterButton() {
        UniversalAnalogStick stick = null;
        for (UniversalVirtualControllerElement element : universalController.getElements()) {
            if (element instanceof UniversalAnalogStick) {
                stick = (UniversalAnalogStick) element;
                break;
            }
        }

        check("the layout has a stick", stick != null);
        if (stick == null) {
            return;
        }

        UniversalStickConfig config = stick.getStickConfig();
        boolean originalEnabled = config.isCenterButtonEnabled();
        UniversalStickButtonConfig originalBinding = config.getCenterButton().copy();

        // A touch in the middle while the switch is off must not press anything
        config.setCenterButtonEnabled(false);
        stick.forgetCenterPressForTesting();
        tapStickCenter(stick);
        check("the middle of the stick does nothing while it is switched off",
                !stick.wasCenterPressedForTesting());

        // Switched on and bound to the stick click, a touch in the middle presses it
        config.setCenterButtonEnabled(true);
        config.getCenterButton().setButtonCode(11);
        stick.forgetCenterPressForTesting();
        tapStickCenter(stick);
        check("the middle of the stick presses its binding once switched on",
                stick.wasCenterPressedForTesting());

        // A touch that moves out of the middle is a movement, not a button press
        stick.forgetCenterPressForTesting();
        dragStickOutOfCenter(stick);
        check("moving the stick does not also press its middle button",
                !stick.wasCenterPressedForTesting());

        // Put the original binding back
        config.setCenterButtonEnabled(originalEnabled);
        config.getCenterButton().setButtonCode(originalBinding.getButtonCode());
    }

    /**
     * Sends one touch to a control, at a point given as a fraction of its size.
     */
    private void tapStickCenter(UniversalAnalogStick stick) {
        stick.onElementTouchEvent(motionEventFor(stick, MotionEvent.ACTION_DOWN, 0.5f, 0.5f));
        stick.onElementTouchEvent(motionEventFor(stick, MotionEvent.ACTION_UP, 0.5f, 0.5f));
    }

    private void dragStickOutOfCenter(UniversalAnalogStick stick) {
        stick.onElementTouchEvent(motionEventFor(stick, MotionEvent.ACTION_DOWN, 0.5f, 0.5f));
        stick.onElementTouchEvent(motionEventFor(stick, MotionEvent.ACTION_MOVE, 0.95f, 0.5f));
        stick.onElementTouchEvent(motionEventFor(stick, MotionEvent.ACTION_UP, 0.95f, 0.5f));
    }

    private MotionEvent motionEventFor(View view, int action, float fractionX, float fractionY) {
        long now = SystemClock.uptimeMillis();
        return MotionEvent.obtain(now, now, action,
                view.getWidth() * fractionX, view.getHeight() * fractionY, 0);
    }

    /**
     * Rebuilds the rows from the control's saved state, which is what the drawer does when it
     * is opened again, and checks that the values a fresh set of rows reports match what was
     * set. This is what catches a row that reports a size it was built with rather than the
     * size the control now has.
     */
    private void checkRebuiltRows() {
        final UniversalVirtualControllerElement element = editor.getSelectedElementForTesting();
        UniversalEditorPanel drawer = editor.getPanelForTesting();
        UniversalElementEditorBuilder builder = editor.getEditorBuilderForTesting();

        if (element == null || drawer == null || builder == null) {
            check("the drawer can be rebuilt", false);
            return;
        }

        int width = editor.getSelectedGeometryForTesting().getWidth();
        int height = editor.getSelectedGeometryForTesting().getHeight();

        // A fresh build of the rows, exactly as opening the drawer on this control does
        drawer.clear();
        builder.build(element, new UniversalElementEditorBuilder.Callbacks() {
            @Override
            public void onElementChanged() {
            }

            @Override
            public void onGeometryChanged(int left, int top, int newWidth, int newHeight) {
            }

            @Override
            public void onNameChanged(String name) {
            }

            @Override
            public void onBorderRadiusChanged(int percent) {
            }
        });

        UniversalEditorPanel.SliderRow widthRow =
                drawer.getSliderRow(getString(com.limelight.R.string.editor_label_width));
        UniversalEditorPanel.SliderRow heightRow =
                drawer.getSliderRow(getString(com.limelight.R.string.editor_label_height));

        check("a rebuilt width row shows the size the control has",
                widthRow != null && widthRow.getValue() == width);
        check("a rebuilt height row shows the size the control has",
                heightRow != null && heightRow.getValue() == height);

        // Leave the drawer showing the selection again
        editor.onElementTapped(element);
    }

    /**
     * The back gesture must reach the same handling the "Exit" entry of the settings menu
     * uses, which is what asks about unsaved changes.
     */
    private void checkBackHandling() {
        // Unsaved work is what makes the question appear, so the check sets that state first
        universalController.markConfigurationDirty();

        boolean newerDispatch = editor.invokeBackForTesting();

        AlertDialog question = editor.getVisibleDialogForTesting();
        check("the back gesture is handled"
                + (newerDispatch ? " through the registered callback" : " through onBackPressed"),
                question != null);
        check("the back gesture asks about unsaved changes like the Exit entry does",
                question != null);

        if (question != null) {
            question.dismiss();
            universalController.clearLayoutDirty();
        }

        checkSliderIndependence();
    }

    private static String[] listItemLabels(AlertDialog dialog) {
        android.widget.ListView list = dialog.getListView();
        if (list == null || list.getAdapter() == null) {
            return new String[0];
        }

        String[] labels = new String[list.getAdapter().getCount()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = String.valueOf(list.getAdapter().getItem(i)).toLowerCase();
        }

        return labels;
    }

    /**
     * A D-pad offered by the layout menu must be a real D-pad: the type the menu adds is the
     * one the layout builder knows, and it comes with the configuration its editor needs.
     */
    private void checkDpadCanBeAdded() {
        int before = universalController.getElements().size();

        UniversalVirtualControllerElement dpad =
                universalController.addUserElement(UniversalElementTypes.ELEMENT_DPAD, 60, 60, 220, 220);

        check("a D-pad can be added", dpad != null);
        check("the added D-pad has a D-pad configuration",
                dpad != null && dpad.getPadConfigIfAny() != null);
        check("adding a D-pad adds one control",
                universalController.getElements().size() == before + 1);

        if (dpad == null) {
            return;
        }

        editor.onElementTapped(dpad);
        check("the D-pad becomes the selected one",
                editor.getSelectedElementForTesting() == dpad);
        check("the panel has rows for a D-pad", countPanelRows() > 3);

        // Put the layout back the way it was
        universalController.removeElement(dpad);
    }

    /**
     * The corner radius is part of what a control looks like, so it must survive a save and
     * load, and it must be the radius that a copied size carries.
     */
    private void checkBorderRadiusAndCopyPaste() {
        final UniversalVirtualControllerElement element = editor.getSelectedElementForTesting();
        if (element == null) {
            check("a control is selected for the corner radius check", false);
            return;
        }

        UniversalEditorPanel drawer = editor.getPanelForTesting();
        int originalRadius = element.getConfig().getBorderRadius();

        try {
            // A save and load round trip
            UniversalElementConfig config = element.getConfig().copy();
            config.setBorderRadius(37);

            UniversalElementConfig reloaded = new UniversalButtonConfig();
            boolean reloadedOk = false;

            try {
                reloaded.loadFromJson(config.toJson());
                reloadedOk = true;
            } catch (org.json.JSONException e) {
                reloadedOk = false;
            }

            check("a corner radius survives being saved and loaded",
                    reloadedOk && reloaded.getBorderRadius() == 37);

            // The panel must offer the radius and report a change of it
            UniversalEditorPanel.SliderRow radiusRow = drawer.getSliderRow(
                    getString(com.limelight.R.string.editor_label_corner_radius));

            check("the panel offers a corner radius slider", radiusRow != null);

            if (radiusRow != null) {
                radiusRow.dragTo(42);
                check("the corner radius reaches the control",
                        element.getConfig().getBorderRadius() == 42);
            }

            // A copied size carries the width, the height and the radius, and never the
            // position
            int beforeLeft = UniversalGeometry.of(element).getLeft();
            int beforeTop = UniversalGeometry.of(element).getTop();
            int wantWidth = UniversalGeometry.of(element).getWidth() + 17;
            int wantHeight = UniversalGeometry.of(element).getHeight() + 23;

            editor.copySizeOf(element);
            check("a size can be copied", editor.hasCopiedSize());

            editor.setCopiedSizeForTesting(wantWidth, wantHeight, 33);
            editor.pasteSizeOnto(element);

            UniversalGeometry pasted = UniversalGeometry.of(element);
            check("a pasted size brings the width", pasted.getWidth() == wantWidth);
            check("a pasted size brings the height", pasted.getHeight() == wantHeight);
            check("a pasted size brings the corner radius",
                    element.getConfig().getBorderRadius() == 33);
            check("a pasted size leaves the position alone",
                    pasted.getLeft() == beforeLeft && pasted.getTop() == beforeTop);
        } finally {
            // Put the control back the way it was found
            element.getConfig().setBorderRadius(originalRadius);
            editor.onElementTapped(element);
        }
    }

    /**
     * A plain button lights up while it is held and goes back to normal when the finger
     * comes off. Only a latched button stays lit, which is what tells the user it is still
     * sending.
     */
    private void checkButtonHighlightReturns() {
        // A button of its own, so the check does not depend on what happens to be selected
        UniversalVirtualControllerElement created = universalController.addUserElement(
                UniversalElementTypes.ELEMENT_BUTTON, 30, 30, 160, 160);

        if (!(created instanceof UniversalDigitalButton)) {
            check("a button is available for the highlight check", false);
            return;
        }

        UniversalDigitalButton button = (UniversalDigitalButton) created;
        UniversalButtonConfig config = button.getButtonConfig();

        boolean originalToggle = config.isToggle();
        UniversalVirtualController.ControllerMode originalMode =
                universalController.getControllerMode();

        try {
            // In a stream a press of a button sends its action, which is the case this looks at
            universalController.setControllerMode(UniversalVirtualController.ControllerMode.Active);

            config.setToggle(false);
            button.releaseConfiguredActions();

            button.onElementTouchEvent(motionEventFor(button, MotionEvent.ACTION_DOWN, 0.5f, 0.5f));
            check("a held button looks pressed", button.isPressed());

            button.onElementTouchEvent(motionEventFor(button, MotionEvent.ACTION_UP, 0.5f, 0.5f));
            check("a released button stops looking pressed", !button.isPressed());
            check("a released button is not left holding its action", !button.isToggledOn());

            // A latching button is the one case that stays lit
            config.setToggle(true);
            button.releaseConfiguredActions();

            button.onElementTouchEvent(motionEventFor(button, MotionEvent.ACTION_DOWN, 0.5f, 0.5f));
            button.onElementTouchEvent(motionEventFor(button, MotionEvent.ACTION_UP, 0.5f, 0.5f));
            check("a latched button stays lit after the finger is lifted", button.isPressed());

            // The next tap lets it go
            button.onElementTouchEvent(motionEventFor(button, MotionEvent.ACTION_DOWN, 0.5f, 0.5f));
            button.onElementTouchEvent(motionEventFor(button, MotionEvent.ACTION_UP, 0.5f, 0.5f));
            check("a latched button goes out on the next tap", !button.isPressed());

            // The button is left latched for a moment, so the glow of a pressed control can
            // be looked at on screen. It is let go and taken away afterwards.
            config.setToggle(true);
            button.releaseConfiguredActions();
            button.onElementTouchEvent(motionEventFor(button, MotionEvent.ACTION_DOWN, 0.5f, 0.5f));
            button.onElementTouchEvent(motionEventFor(button, MotionEvent.ACTION_UP, 0.5f, 0.5f));

            final UniversalDigitalButton latched = button;

            postDelayed(new Runnable() {
                @Override
                public void run() {
                    latched.releaseConfiguredActions();
                    universalController.removeElement(latched);
                }
            }, LATCHED_VISIBLE_MS);

            // The button stays on screen until the delayed run takes it away
            keepButtonOnScreen = true;
        } finally {
            config.setToggle(originalToggle);
            universalController.setControllerMode(originalMode);

            // The button is left on screen when it is showing a latched state, so the glow
            // can be seen; the delayed run above takes it away
            if (!keepButtonOnScreen) {
                button.releaseConfiguredActions();
                universalController.removeElement(button);
            }
        }
    }

    /**
     * How long a latched button is left on screen, so its glow can be looked at.
     */
    private static final long LATCHED_VISIBLE_MS = 4000;

    private boolean keepButtonOnScreen;

    /**
     * The transparency of the layout comes from one setting, is applied to every control, and
     * really reaches the pixels.
     */
    private void checkLayoutOpacity() {
        UniversalVirtualController controller = universalController;

        int originalPercent = android.preference.PreferenceManager
                .getDefaultSharedPreferences(this)
                .getInt(PreferenceConfiguration.UNIVERSAL_LAYOUT_OPACITY_PREF_STRING,
                        PreferenceConfiguration.UNIVERSAL_LAYOUT_OPACITY_DEFAULT);

        try {
            setOpacityPercent(45);
            check("the layout opacity is read from the settings",
                    Math.abs(controller.getElementOpacity() - 0.45f) < 0.01f);

            controller.applyElementOpacity();

            boolean allApplied = !controller.getElements().isEmpty();
            for (UniversalVirtualControllerElement element : controller.getElements()) {
                if (Math.abs(element.getElementOpacity() - 0.45f) > 0.01f) {
                    allApplied = false;
                    break;
                }
            }
            check("the layout opacity reaches every control", allApplied);

            // And it reaches the picture: a faded control is drawn lighter than an opaque one
            UniversalDigitalButton button = new UniversalDigitalButton(controller, 4242, 0, this);
            button.getConfig().setName("Opacity");
            button.getConfig().setBorderRadius(18);
            button.setElementOpacity(1f);
            button.measure(View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY));
            button.layout(0, 0, 120, 120);

            int opaque = averageBodyPixel(button);
            button.setElementOpacity(0.45f);
            int faded = averageBodyPixel(button);

            check("drawing honours the layout opacity", opaque != faded);
        } finally {
            setOpacityPercent(originalPercent);
            controller.applyElementOpacity();
        }
    }

    /**
     * Draws a control into a bitmap and returns how bright its body is, which is enough to
     * tell an opaque control from a faded one.
     */
    private int averageBodyPixel(View control) {
        int size = Math.max(1, Math.min(control.getWidth(), control.getHeight()));
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                size, size, android.graphics.Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        control.draw(canvas);

        long total = 0;
        long count = 0;

        // The middle of the control is its body, away from the edge and the label
        int from = size / 3;
        int to = size * 2 / 3;

        for (int x = from; x < to; x++) {
            for (int y = from; y < to; y++) {
                int pixel = bitmap.getPixel(x, y);

                // Weighted by its own transparency, so a faded body counts as lighter
                total += (android.graphics.Color.red(pixel)
                        + android.graphics.Color.green(pixel)
                        + android.graphics.Color.blue(pixel)) / 3
                        * android.graphics.Color.alpha(pixel) / 255;
                count++;
            }
        }

        bitmap.recycle();
        return count == 0 ? 0 : (int) (total / count);
    }

    private void setOpacityPercent(int percent) {
        android.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putInt(PreferenceConfiguration.UNIVERSAL_LAYOUT_OPACITY_PREF_STRING, percent)
                .apply();
    }

    /**
     * The D-pad is drawn as four buttons around an empty middle, and each of them answers to
     * a touch that reaches a little past what is drawn, so the pad is comfortable to use.
     */
    private void checkDpadTouchZones() {
        UniversalDigitalPad pad = null;
        for (UniversalVirtualControllerElement element : universalController.getElements()) {
            if (element instanceof UniversalDigitalPad) {
                pad = (UniversalDigitalPad) element;
                break;
            }
        }

        if (pad == null) {
            pad = new UniversalDigitalPad(universalController, this, 4343);
        }

        pad.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        pad.layout(0, 0, 300, 300);

        int up = pad.getDirectionAtForTesting(150, 50);
        check("the D-pad answers to the up button", up == UniversalDigitalPad.DIGITAL_PAD_DIRECTION_UP);
        check("the D-pad answers to the down button",
                pad.getDirectionAtForTesting(150, 250) == UniversalDigitalPad.DIGITAL_PAD_DIRECTION_DOWN);
        check("the D-pad answers to the left button",
                pad.getDirectionAtForTesting(50, 150) == UniversalDigitalPad.DIGITAL_PAD_DIRECTION_LEFT);
        check("the D-pad answers to the right button",
                pad.getDirectionAtForTesting(250, 150) == UniversalDigitalPad.DIGITAL_PAD_DIRECTION_RIGHT);

        // A diagonal needs both directions, and the place for it is the corner between two
        // buttons. It is deliberately narrow, so a press on one arm does not also press the
        // arm beside it.
        check("a diagonal between two arms presses two directions",
                pad.getDirectionAtForTesting(100, 100)
                        == (UniversalDigitalPad.DIGITAL_PAD_DIRECTION_UP
                        | UniversalDigitalPad.DIGITAL_PAD_DIRECTION_LEFT));

        // A press on one arm near the middle, but off to one side, must press only that arm
        check("a press on the up arm off to one side presses only up",
                pad.getDirectionAtForTesting(170, 60) == UniversalDigitalPad.DIGITAL_PAD_DIRECTION_UP);
        check("a press on the left arm off to one side presses only left",
                pad.getDirectionAtForTesting(60, 170) == UniversalDigitalPad.DIGITAL_PAD_DIRECTION_LEFT);

        // Away from the pad there is nothing to press
        check("a press outside the D-pad presses nothing",
                pad.getDirectionAtForTesting(10, 10) == 0);

        // The zone is wider than the drawn button, which is the part that makes the pad
        // comfortable: the drawn button is only about a third of the pad across
        check("the up zone reaches wider than the drawn button",
                (pad.getDirectionAtForTesting(90, 60) & UniversalDigitalPad.DIGITAL_PAD_DIRECTION_UP) != 0);
        check("the left zone reaches taller than the drawn button",
                (pad.getDirectionAtForTesting(60, 90) & UniversalDigitalPad.DIGITAL_PAD_DIRECTION_LEFT) != 0);

        // Each button must be drawn on the side it belongs to, which is a turn of the same
        // shape: the left button lit the right one while the turns were the wrong way round
        float centerX = 150;
        float centerY = 150;

        check("the up button is drawn above the middle",
                pad.getShapeCenterYForTesting(UniversalDigitalPad.SHAPE_UP) < centerY);
        check("the down button is drawn below the middle",
                pad.getShapeCenterYForTesting(UniversalDigitalPad.SHAPE_DOWN) > centerY);
        check("the left button is drawn left of the middle",
                pad.getShapeCenterXForTesting(UniversalDigitalPad.SHAPE_LEFT) < centerX);
        check("the right button is drawn right of the middle",
                pad.getShapeCenterXForTesting(UniversalDigitalPad.SHAPE_RIGHT) > centerX);

        // A press in the middle of an edge belongs to one direction only, so the neighbouring
        // directions are not caught by a press near the edge
        check("pressing near the top edge does not also press left or right",
                pad.getDirectionAtForTesting(150, 40) == UniversalDigitalPad.DIGITAL_PAD_DIRECTION_UP);
        check("pressing near the left edge does not also press up or down",
                pad.getDirectionAtForTesting(40, 150) == UniversalDigitalPad.DIGITAL_PAD_DIRECTION_LEFT);

        // The middle is where the shape is empty, so it maps to no direction
        check("the middle of the D-pad presses nothing",
                pad.getDirectionAtForTesting(150, 150) == 0);
    }

    /**
     * What a save writes must be where the controls are, not where they were before they were
     * dragged: the configuration only follows the screen when something asks it to.
     */
    private void checkDraggedGeometryIsSaved() {
        UniversalVirtualControllerElement element = editor.getSelectedElementForTesting();
        if (element == null) {
            check("a control is selected for the save check", false);
            return;
        }

        UniversalGeometry before = UniversalGeometry.of(element).copy();

        // A drag, and nothing else
        dispatchTouch(element, MotionEvent.ACTION_DOWN, 0.5f, 0.5f);
        dispatchTouch(element, MotionEvent.ACTION_MOVE, 0.8f, 0.8f);
        dispatchTouch(element, MotionEvent.ACTION_UP, 0.8f, 0.8f);

        UniversalGeometry onScreen = UniversalGeometry.of(element).copy();
        check("the drag moved the control on screen",
                onScreen.getLeft() != before.getLeft() || onScreen.getTop() != before.getTop());

        try {
            // Exactly what a save writes, and what reading it back produces
            org.json.JSONObject written = element.getConfiguration();
            UniversalGeometry saved = UniversalGeometry.fromJson(written);

            check("a dragged control is written where it is on screen",
                    saved.getLeft() == onScreen.getLeft() && saved.getTop() == onScreen.getTop());
            check("a dragged control keeps its size when it is written",
                    saved.getWidth() == onScreen.getWidth()
                            && saved.getHeight() == onScreen.getHeight());

            // And a control read back from that lives where it was saved
            UniversalDigitalButton reloaded =
                    new UniversalDigitalButton(universalController, 5454, 0, this);
            reloaded.loadConfiguration(written);

            UniversalGeometry restored = reloaded.getConfig().getGeometry();
            check("a control read back from a save is where it was saved",
                    restored.getLeft() == onScreen.getLeft()
                            && restored.getTop() == onScreen.getTop());

            checkSavedProfileHoldsTheDrag(element, onScreen);
        } catch (org.json.JSONException e) {
            check("a dragged control is written where it is on screen", false);
        }
    }

    /**
     * The whole way a save goes: written into the layout's own store, read back from there.
     *
     * A profile of its own is used, so the check never disturbs a layout the user made.
     */
    private void checkSavedProfileHoldsTheDrag(UniversalVirtualControllerElement element,
                                               UniversalGeometry onScreen) {
        UniversalLayoutConfigurationLoader.saveProfile(universalController, this,
                SCRATCH_LAYOUT_ID, 1000, 1000);

        int savedLeft = -1;
        int savedTop = -1;

        try {
            String stored = getSharedPreferences("OSC_UNIVERSAL_" + SCRATCH_LAYOUT_ID, MODE_PRIVATE)
                    .getString("elements", null);

            org.json.JSONArray elements = new org.json.JSONArray(stored == null ? "[]" : stored);

            for (int i = 0; i < elements.length(); i++) {
                org.json.JSONObject entry = elements.getJSONObject(i);

                if (entry.optInt("elementId", -1) == element.getElementId()) {
                    UniversalGeometry storedGeometry = UniversalGeometry.fromJson(entry);
                    savedLeft = storedGeometry.getLeft();
                    savedTop = storedGeometry.getTop();
                    break;
                }
            }
        } catch (org.json.JSONException e) {
            savedLeft = -1;
        }

        check("the saved profile holds the position the control was dragged to",
                savedLeft == onScreen.getLeft() && savedTop == onScreen.getTop());

        // The scratch profile is not left behind
        UniversalLayoutConfigurationLoader.resetLayout(this, SCRATCH_LAYOUT_ID);
    }

    /**
     * A layout id used only by the smoke test, so a check that saves never overwrites a layout
     * the user made.
     */
    private static final String SCRATCH_LAYOUT_ID = "smoke-test-scratch";

    /**
     * The host only registers a gamepad for the players named in the mask of an input packet,
     * so a layout that is not in the mask has all of its input ignored.
     */
    private void checkOnScreenLayoutRegistersGamepad() {
        check("the built-in on-screen layout names player one",
                ControllerHandler.onScreenPlayerMask(true, false) == 1);
        check("the universal layout names player one on its own",
                ControllerHandler.onScreenPlayerMask(false, true) == 1);
        check("both on-screen layouts together still name player one",
                ControllerHandler.onScreenPlayerMask(true, true) == 1);
        check("no on-screen layout leaves player one out",
                ControllerHandler.onScreenPlayerMask(false, false) == 0);
    }

    /**
     * A trigger drives an analog byte, not a button flag, and it must do so with nothing
     * configured for it, exactly as the built-in on-screen trigger does.
     */
    private void checkTriggersWorkOnTheirOwn() {
        UniversalVirtualController controller = universalController;

        // The left trigger of the layout that is on screen, which is how the built-in one is
        // made: it carries the left trigger's own element id
        UniversalVirtualControllerElement lt = null;
        for (UniversalVirtualControllerElement element : controller.getElements()) {
            if (element instanceof UniversalLeftTrigger) {
                lt = element;
                break;
            }
        }

        check("the layout has a left trigger", lt != null);

        if (lt == null) {
            return;
        }

        UniversalVirtualController.ControllerMode originalMode = controller.getControllerMode();
        controller.setControllerMode(UniversalVirtualController.ControllerMode.Active);

        try {
            // Nothing is configured for it, which is how the built-in layout makes one
            ((UniversalLeftTrigger) lt).getButtonConfig().setActionSequence(ActionSequence.empty());

            controller.getControllerInputContext().leftTrigger = 0;

            lt.onElementTouchEvent(motionEventFor(lt, MotionEvent.ACTION_DOWN, 0.5f, 0.5f));
            check("a trigger pulls its axis without anything configured for it",
                    controller.getControllerInputContext().leftTrigger == (byte) 0xFF);

            lt.onElementTouchEvent(motionEventFor(lt, MotionEvent.ACTION_UP, 0.5f, 0.5f));
            check("a trigger lets its axis out again",
                    controller.getControllerInputContext().leftTrigger == 0);
        } finally {
            lt.releaseConfiguredActions();
            controller.getControllerInputContext().leftTrigger = 0;
            controller.setControllerMode(originalMode);
        }
    }

    /**
     * Two controllers' readings of one axis are combined by keeping the stronger one.
     *
     * Combining them with a bitwise OR mixes the signs and turns a pushed stick into a large
     * value of the opposite sign as soon as anything else reports even a tiny drift, which is
     * what made a character briefly run the other way.
     */
    private void checkAxisCombiningKeepsTheStrongerValue() {
        short pushedForward = 20000;
        short drifting = -5000;

        check("a weak reading on its own is kept",
                ControllerHandler.combineAxisValue((short) 0, drifting) == drifting);
        check("a pushed axis is not flipped by another controller's drift",
                ControllerHandler.combineAxisValue(pushedForward, drifting) == pushedForward);
        check("the stronger reading wins whichever way it points",
                ControllerHandler.combineAxisValue((short) -20000, (short) 3000) == (short) -20000);

        // What combining by bits did, so the check above cannot pass by accident
        check("combining by bits would have flipped the sign",
                (short) (pushedForward | drifting) < 0);
    }

    /**
     * A stick direction can be bound to a key of a keyboard, not only to a gamepad button.
     */
    private void checkStickDirectionCanSendAKey() {
        UniversalStickButtonConfig binding = new UniversalStickButtonConfig();

        // A gamepad button is kept as a code
        binding.setBinding(new InputEvent(InputEvent.KIND_GAMEPAD, 1));
        check("a direction bound to a pad button says so", binding.isAssigned());
        check("a pad binding is recognised again", binding.isBoundTo(new InputEvent(InputEvent.KIND_GAMEPAD, 1)));
        check("a pad binding sends one gamepad event",
                binding.getEffectiveSequence().getSteps().get(0).getEvents().get(0).isGamepad());

        // A key is kept as a sequence instead, because it is not a button code
        InputEvent key = InputEventNames.keyboardCatalog().get(0);
        binding.setBinding(key);

        check("a direction bound to a key counts as assigned", binding.isAssigned());
        check("a key binding is recognised again", binding.isBoundTo(key));
        check("a key binding does not leave a pad button behind", binding.getButtonCode() == 0);
        check("a key binding sends one keyboard event",
                binding.getEffectiveSequence().getSteps().get(0).getEvents().get(0).isKeyboard());

        // And it survives being saved and loaded, which is what a layout file does
        boolean roundTripped = false;
        try {
            UniversalStickButtonConfig reloaded =
                    UniversalStickButtonConfig.fromJson(binding.toJson());
            roundTripped = reloaded.isBoundTo(key) && reloaded.isAssigned();
        } catch (org.json.JSONException e) {
            roundTripped = false;
        }

        check("a key binding can be read back after being written", roundTripped);

        // Binding a pad button again must clear the key, not leave both
        binding.setBinding(new InputEvent(InputEvent.KIND_GAMEPAD, 2));
        check("binding a pad button again clears the key",
                binding.getButtonCode() == 2 && binding.getActionSequence().isEmpty());
    }

    /**
     * The transparency of a layout is applied to a control the moment it is added, so a new
     * control does not stand out as the only opaque one until the layout is reloaded.
     */
    private void checkNewControlAdoptsTheOpacity() {
        int originalPercent = android.preference.PreferenceManager
                .getDefaultSharedPreferences(this)
                .getInt(PreferenceConfiguration.UNIVERSAL_LAYOUT_OPACITY_PREF_STRING,
                        PreferenceConfiguration.UNIVERSAL_LAYOUT_OPACITY_DEFAULT);

        try {
            setOpacityPercent(40);

            UniversalVirtualControllerElement added = universalController.addUserElement(
                    UniversalElementTypes.ELEMENT_BUTTON, 20, 20, 120, 120);

            check("a control added to a faded layout is faded as well",
                    added != null && Math.abs(added.getElementOpacity() - 0.40f) < 0.01f);

            if (added != null) {
                universalController.removeElement(added);
            }
        } finally {
            setOpacityPercent(originalPercent);
            universalController.applyElementOpacity();
        }
    }

    /**
     * Every arm of a D-pad must look the same when nothing is pressed, and only the arm that is
     * pressed may light up.
     */
    private void checkDpadArmsLookTheSame() {
        UniversalDigitalPad pad = null;
        for (UniversalVirtualControllerElement element : universalController.getElements()) {
            if (element instanceof UniversalDigitalPad) {
                pad = (UniversalDigitalPad) element;
                break;
            }
        }

        if (pad == null) {
            pad = new UniversalDigitalPad(universalController, this, 7474);
        }

        pad.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        pad.layout(0, 0, 300, 300);

        pad.setDirectionForTesting(UniversalDigitalPad.DIGITAL_PAD_DIRECTION_NO_DIRECTION);
        int[] atRest = measureArms(pad);

        android.util.Log.i("LayoutSmoke", "dpad at rest: up=" + atRest[0] + " down=" + atRest[1]
                + " left=" + atRest[2] + " right=" + atRest[3]);

        int brightest = Math.max(Math.max(atRest[0], atRest[1]), Math.max(atRest[2], atRest[3]));
        int darkest = Math.min(Math.min(atRest[0], atRest[1]), Math.min(atRest[2], atRest[3]));

        check("the arms of a D-pad look the same when nothing is pressed",
                brightest - darkest <= 12);

        pad.setDirectionForTesting(UniversalDigitalPad.DIGITAL_PAD_DIRECTION_LEFT);
        int[] leftPressed = measureArms(pad);

        android.util.Log.i("LayoutSmoke", "dpad left pressed: up=" + leftPressed[0]
                + " down=" + leftPressed[1] + " left=" + leftPressed[2]
                + " right=" + leftPressed[3]);

        check("pressing left lights the left arm", leftPressed[2] > atRest[2]);
        check("pressing left does not light the up arm", leftPressed[0] <= atRest[0] + 2);
        check("pressing left does not light the down arm", leftPressed[1] <= atRest[1] + 2);
        check("pressing left does not light the right arm", leftPressed[3] <= atRest[3] + 2);

        pad.setDirectionForTesting(UniversalDigitalPad.DIGITAL_PAD_DIRECTION_NO_DIRECTION);
    }

    /**
     * The brightness of the middle of each arm, in the order up, down, left, right.
     */
    private int[] measureArms(UniversalDigitalPad pad) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                300, 300, android.graphics.Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        pad.draw(canvas);

        int[] brightness = new int[4];

        // The middle of each arm, which is where the body of it is
        brightness[0] = brightnessAt(bitmap, 150, 55);
        brightness[1] = brightnessAt(bitmap, 150, 245);
        brightness[2] = brightnessAt(bitmap, 55, 150);
        brightness[3] = brightnessAt(bitmap, 245, 150);

        bitmap.recycle();
        return brightness;
    }

    private int brightnessAt(android.graphics.Bitmap bitmap, int x, int y) {
        int pixel = bitmap.getPixel(x, y);

        return (android.graphics.Color.red(pixel)
                + android.graphics.Color.green(pixel)
                + android.graphics.Color.blue(pixel)) / 3
                * android.graphics.Color.alpha(pixel) / 255;
    }

    /**
     * A toggle button that was switched on and then off again must look exactly like a button
     * that was never pressed.
     */
    /**
     * A toggle button that was switched on and then off again must look exactly like the same
     * button before it was ever touched.
     *
     * The button is compared with itself, so no other difference between two controls can
     * explain the result.
     */
    private void checkToggleOffLooksLikeUntouched() {
        UniversalVirtualController.ControllerMode originalMode = universalController.getControllerMode();
        universalController.setControllerMode(UniversalVirtualController.ControllerMode.Active);

        UniversalDigitalButton toggle = new UniversalDigitalButton(universalController, 8802, 0, this);

        try {
            toggle.getConfig().setName("Same");
            toggle.getButtonConfig().setToggle(true);

            toggle.measure(View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY));
            toggle.layout(0, 0, 120, 120);

            check("an untouched button is not pressed", !toggle.isPressed());

            // The button as it looks before anything happens to it
            android.graphics.Bitmap before = renderElement(toggle);

            // Two taps: on, then off again
            tapElement(toggle);
            check("one tap latches the toggle on", toggle.isPressed());

            tapElement(toggle);
            check("the second tap lets the toggle go", !toggle.isPressed());

            // A tap in the editor also selects the control, which draws the editor's own
            // highlight. That is not part of how a control looks while playing, so it is
            // cleared before the two pictures are compared.
            toggle.setSelected(false);

            android.graphics.Bitmap after = renderElement(toggle);

            int difference = 0;
            int firstDifferenceAt = -1;

            for (int x = 0; x < 120; x++) {
                for (int y = 0; y < 120; y++) {
                    if (before.getPixel(x, y) != after.getPixel(x, y)) {
                        difference++;

                        if (firstDifferenceAt < 0) {
                            firstDifferenceAt = y * 120 + x;
                        }
                    }
                }
            }

            android.util.Log.i("LayoutSmoke", "toggle off differs from its untouched self in "
                    + difference + " pixels, first at " + (firstDifferenceAt % 120) + ","
                    + (firstDifferenceAt / 120));

            if (difference > 0 && firstDifferenceAt >= 0) {
                int x = firstDifferenceAt % 120;
                int y = firstDifferenceAt / 120;

                android.util.Log.i("LayoutSmoke", "before=" + Integer.toHexString(before.getPixel(x, y))
                        + " after=" + Integer.toHexString(after.getPixel(x, y))
                        + " pressed=" + toggle.isPressed());
            }

            check("a toggle switched off looks like it did before it was pressed",
                    difference == 0);

            saveBitmapForInspection(before, "toggle-before.png");
            saveBitmapForInspection(after, "toggle-after.png");

            before.recycle();
            after.recycle();
        } finally {
            universalController.setControllerMode(originalMode);
        }
    }

    private void tapElement(final UniversalVirtualControllerElement element) {
        element.onElementTouchEvent(motionEventFor(element, MotionEvent.ACTION_DOWN, 0.5f, 0.5f));
        element.onElementTouchEvent(motionEventFor(element, MotionEvent.ACTION_UP, 0.5f, 0.5f));
    }

    private android.graphics.Bitmap renderElement(View element) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                element.getWidth(), element.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);

        element.draw(new Canvas(bitmap));
        return bitmap;
    }

    /**
     * The four arms of a D-pad must be drawn with the same colour and the same transparency.
     */
    private void checkDpadArmsAreIdentical() {
        UniversalDigitalPad pad = null;
        for (UniversalVirtualControllerElement element : universalController.getElements()) {
            if (element instanceof UniversalDigitalPad) {
                pad = (UniversalDigitalPad) element;
                break;
            }
        }

        if (pad == null) {
            pad = new UniversalDigitalPad(universalController, this, 8811);
        }

        pad.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        pad.layout(0, 0, 300, 300);

        pad.setDirectionForTesting(UniversalDigitalPad.DIGITAL_PAD_DIRECTION_NO_DIRECTION);

        android.graphics.Bitmap bitmap = renderElement(pad);

        int[] arms = new int[]{
                bitmap.getPixel(150, 55),
                bitmap.getPixel(150, 245),
                bitmap.getPixel(55, 150),
                bitmap.getPixel(245, 150),
        };

        String[] names = new String[]{"up", "down", "left", "right"};

        for (int i = 0; i < arms.length; i++) {
            float[] bounds = pad.getShapeBoundsForTesting(i);

            android.util.Log.i("LayoutSmoke", "arm " + names[i] + ": rgb="
                    + android.graphics.Color.red(arms[i]) + ","
                    + android.graphics.Color.green(arms[i]) + ","
                    + android.graphics.Color.blue(arms[i])
                    + " alpha=" + android.graphics.Color.alpha(arms[i])
                    + " bounds=" + bounds[0] + "," + bounds[1] + " " + bounds[2] + "," + bounds[3]);
        }

        int minAlpha = 255;
        int maxAlpha = 0;
        for (int arm : arms) {
            minAlpha = Math.min(minAlpha, android.graphics.Color.alpha(arm));
            maxAlpha = Math.max(maxAlpha, android.graphics.Color.alpha(arm));
        }

        check("every arm of a D-pad has the same transparency", maxAlpha - minAlpha <= 3);

        int minRed = 255;
        int maxRed = 0;
        for (int arm : arms) {
            minRed = Math.min(minRed, android.graphics.Color.red(arm));
            maxRed = Math.max(maxRed, android.graphics.Color.red(arm));
        }

        // The shade differs a little by design: the body is shaded from top to bottom, and the
        // arms are sampled at different points of it
        check("every arm of a D-pad has the same shade", maxRed - minRed <= 6);

        saveBitmapForInspection(bitmap, "dpad-at-rest.png");
        bitmap.recycle();
    }

    /**
     * Writes a rendered control to the app's files, so the picture itself can be pulled off the
     * device and looked at when the numbers alone do not explain what is drawn.
     */
    private void saveBitmapForInspection(android.graphics.Bitmap bitmap, String name) {
        try {
            // The external files directory is used so the picture can be taken off the device
            // with an ordinary adb pull
            java.io.File directory = getExternalFilesDir(null);
            java.io.File file = new java.io.File(directory == null ? getFilesDir() : directory, name);
            java.io.FileOutputStream out = new java.io.FileOutputStream(file);

            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            out.close();

            android.util.Log.i("LayoutSmoke", "wrote " + file.getAbsolutePath());
        } catch (java.io.IOException e) {
            android.util.Log.e("LayoutSmoke", "could not write " + name, e);
        }
    }

    /**
     * A pressed button and a pressed arm of a D-pad must be lit to about the same strength, and
     * the light of an arm must stay inside its rounded shape.
     */
    private void checkDpadGlowMatchesAButton() {
        UniversalDigitalButton button = new UniversalDigitalButton(universalController, 8821, 0, this);
        button.getConfig().setName("");
        button.measure(View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY));
        button.layout(0, 0, 160, 160);
        button.setPressed(true);

        UniversalDigitalPad pad = new UniversalDigitalPad(universalController, this, 8822);
        pad.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        pad.layout(0, 0, 300, 300);
        pad.setDirectionForTesting(UniversalDigitalPad.DIGITAL_PAD_DIRECTION_UP);

        android.graphics.Bitmap buttonImage = renderElement(button);
        android.graphics.Bitmap padImage = renderElement(pad);

        // Just inside the drawn edge of each, which is where the light pools. The body of a
        // button is inset by its stroke, while an arm of a pad reaches the edge of the pad, so
        // both are sampled the same distance inside their own outline.
        int buttonEdge = android.graphics.Color.alpha(buttonImage.getPixel(80, 16));
        int armEdge = android.graphics.Color.alpha(padImage.getPixel(150, 6));

        android.util.Log.i("LayoutSmoke", "glow at the edge: button=" + buttonEdge
                + " dpad arm=" + armEdge);

        check("a pressed arm of a D-pad is lit like a pressed button",
                armEdge >= buttonEdge * 0.75 && armEdge <= buttonEdge * 1.25);

        // The corner of the arm's outline, which the rounded shape does not reach, must be left
        // alone: this is the light that used to stick out with sharp corners
        int cornerAlpha = android.graphics.Color.alpha(padImage.getPixel(113, 3));

        android.util.Log.i("LayoutSmoke", "alpha at the sharp corner of an arm: " + cornerAlpha);

        check("the light of an arm does not stick out past its rounded corner", cornerAlpha <= 8);

        saveBitmapForInspection(padImage, "dpad-pressed.png");

        buttonImage.recycle();
        padImage.recycle();
    }

    private static boolean contains(String[] labels, String needle) {
        for (String label : labels) {
            if (label.contains(needle)) {
                return true;
            }
        }

        return false;
    }

    private UniversalVirtualControllerElement holdTarget;
    private int holdStartLeft;
    private boolean holdHandled;

    /**
     * The drawer the editor puts on screen, found by name so the test does not depend on the
     * editor's own identifiers.
     */
    private int drawerId() {
        return getResources().getIdentifier("layoutEditorPanel", "id", getPackageName());
    }

    /**
     * Sends a touch to the arrangement rather than to a control, which is the case a tap on
     * empty space makes.
     */
    private void dispatchOverlayTouch(int action, float x, float y) {
        View overlay = editor.getEditorRootView() == null
                ? null
                : editor.getEditorRootView().findViewById(overlayId());

        if (overlay == null) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        overlay.dispatchTouchEvent(event);
        event.recycle();
    }

    private int overlayId() {
        return getResources().getIdentifier("layoutEditorOverlay", "id", getPackageName());
    }

    /**
     * Sends one touch event to a control, at a point inside it given as a fraction of its
     * size.
     */
    private void dispatchTouch(UniversalVirtualControllerElement element,
                               int action, float fractionX, float fractionY) {
        long now = SystemClock.uptimeMillis();

        MotionEvent event = MotionEvent.obtain(now, now, action,
                element.getWidth() * fractionX, element.getHeight() * fractionY, 0);
        element.dispatchTouchEvent(event);
        event.recycle();
    }

    // ------------------------------------------------------------------ helpers

    private void report() {
        Log.i(TAG, "RESULT " + (failures == 0 ? "PASSED" : "FAILED")
                + " checks=" + checks + " failures=" + failures);
    }

    /**
     * Counts the rows the editing panel built, which is how a check can tell that the panel
     * described the selected control rather than staying empty.
     */
    private int countPanelRows() {
        Object panel = getField(editor, "panel");
        if (!(panel instanceof UniversalEditorPanel)) {
            return 0;
        }

        Object view = ((UniversalEditorPanel) panel).getView();
        if (!(view instanceof ScrollView)) {
            return 0;
        }

        ViewGroup content = (ViewGroup) ((ScrollView) view).getChildAt(0);
        return content == null ? 0 : content.getChildCount();
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);

        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }

        return count;
    }

    private static Object getField(Object target, String name) {
        if (target == null) {
            return null;
        }

        Class<?> type = target.getClass();

        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Exception e) {
                Log.e(TAG, "could not read " + name, e);
                return null;
            }
        }

        return null;
    }

    private void check(String what, boolean passed) {
        checks++;

        if (passed) {
            Log.i(TAG, "ok   " + what);
            return;
        }

        failures++;
        Log.e(TAG, "FAIL " + what);
    }
}
