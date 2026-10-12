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

import com.limelight.binding.input.virtual_controller.universal.UniversalAnalogStick;
import com.limelight.binding.input.virtual_controller.universal.UniversalElementTypes;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutConfigurationLoader;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutDefinition;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutEditorActivity;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutStore;
import com.limelight.binding.input.virtual_controller.universal.UniversalVirtualController;
import com.limelight.binding.input.virtual_controller.universal.UniversalVirtualControllerElement;
import com.limelight.binding.input.virtual_controller.universal.action.ActionSequence;
import com.limelight.binding.input.virtual_controller.universal.action.ActionSequencePlayer;
import com.limelight.binding.input.virtual_controller.universal.action.InputEvent;
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

    private static boolean contains(String[] labels, String needle) {        for (String label : labels) {
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
