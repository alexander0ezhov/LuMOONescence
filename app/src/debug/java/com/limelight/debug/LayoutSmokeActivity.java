package com.limelight.debug;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
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
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.touch.TouchGestureRules;
import com.limelight.binding.input.virtual_controller.universal.UniversalAnalogStick;
import com.limelight.binding.input.virtual_controller.universal.UniversalDigitalButton;
import com.limelight.binding.input.virtual_controller.universal.UniversalDigitalPad;
import com.limelight.binding.input.virtual_controller.universal.UniversalElementTypes;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutConfigurationLoader;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutArea;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutDefinition;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutEditorActivity;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutExchange;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutShareProvider;
import com.limelight.binding.input.virtual_controller.universal.UniversalStreamDrawer;
import android.widget.Button;
import android.widget.FrameLayout;
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

        // Starting this activity with --ez reseedLayouts true replaces the layouts this build
        // brings with fresh copies, which is how their arrangement is looked at while it is
        // being worked on
        if (getIntent().getBooleanExtra("reseedLayouts", false)) {
            for (UniversalLayoutDefinition shipped :
                    new java.util.ArrayList<>(UniversalLayoutStore.getInstance(this).getLayouts())) {
                if ("PC Keyboard".equals(shipped.getName())
                        || "Default (kbd+mouse)".equals(shipped.getName())
                        || "Keyboard".equals(shipped.getName())
                        || "Shooter".equals(shipped.getName())) {
                    UniversalLayoutStore.getInstance(this).deleteLayout(shipped.getId());
                }
            }

            android.preference.PreferenceManager.getDefaultSharedPreferences(this)
                    .edit().remove("shipped_extra_layouts_version").apply();

            com.limelight.binding.input.virtual_controller.universal.UniversalShippedLayouts
                    .seedIfNeeded(this);
        }

        // Copies left behind by a run that was interrupted are cleared first, so the list of
        // layouts does not fill up with them
        for (UniversalLayoutDefinition stale :
                new java.util.ArrayList<>(UniversalLayoutStore.getInstance(this).getLayouts())) {
            if ("Smoke test copy".equals(stale.getName())) {
                UniversalLayoutStore.getInstance(this).deleteLayout(stale.getId());
            }
        }

        // The editor is opened on a copy of the shipped arrangement, so the checks that delete,
        // duplicate and drag controls never touch a layout the user made. The copy has to be a
        // layout the store knows about, because the editor closes on one it cannot find.
        UniversalLayoutDefinition copy =
                UniversalLayoutStore.getInstance(this).addLayout("Smoke test copy");

        editorLayoutId = copy.getId();
        UniversalLayoutConfigurationLoader.seedShippedArrangementInto(this, editorLayoutId);

        Intent intent = new Intent(this, UniversalLayoutEditorActivity.class);
        intent.putExtra(UniversalLayoutEditorActivity.EXTRA_LAYOUT_ID, editorLayoutId);
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

        // It is the arrangement the app falls back to, so it is put back when it is missing and
        // cannot be deleted
        store.deleteLayout("default");
        check("the built-in layout cannot be deleted", store.getLayout("default") != null);

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
            android.util.Log.i("LayoutSmoke", "duplicate: first=" + first.getElementId()
                    + " copy=" + copy.getElementId()
                    + " next=" + universalController.peekNextElementId());

            check("the duplicate has its own ID", copy.getElementId() != first.getElementId());

            // Its own ID is only useful if no other control already has it, which is what a
            // saved layout relies on to tell them apart
            int sharingTheId = 0;

            for (UniversalVirtualControllerElement element : universalController.getElements()) {
                if (element != copy && element.getElementId() == copy.getElementId()) {
                    sharingTheId++;
                }
            }

            android.util.Log.i("LayoutSmoke", "controls sharing the duplicate's ID: " + sharingTheId);

            check("no other control shares the duplicate's ID", sharingTheId == 0);

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
        checkDontSaveLeavesTheLayoutAlone();
        checkPositionStopsBeforeTheEdge();
        checkTheDrawerOffersTheControlActions();
        checkDraggingAControlShowsItsDrawer();
        checkDrawerActionRows();
        checkLayoutsMoveBetweenScreens();
        checkALayoutFromAnotherScreenIsFitted();
        checkLayoutsSurviveBeingWrittenOut();
        checkImportingDoesNotShrinkALayout();
        checkExportThenImportKeepsEveryControl();
        checkASharedLayoutCanBeReadBack();
        checkStreamMenu();
        checkDeletingClosesTheDrawer();
        checkChoosingALayoutShowsItAgain();
        checkPassThrough();
        checkTrackpadGestures();
        checkTypingAppliesWhenFinished();
        checkTheSettingsScreenIsTidy();
        checkStreamMenuExtras();
        checkEditorPassThroughOrder();
        checkLayoutsScreenIsTidy();
        checkTriggerEventNames();
        checkStickAxisSigns();
        checkSensitivityRow();
        checkOnlyTheStreamsOwnFingersAreCounted();
        checkTheTouchStickSurvivesTheLayout();
        checkShippedExtraLayouts();
        checkLabelSizes();
        checkImportIsBounded();
        checkDelayedSequence();
        checkANewLayoutStartsEmpty();
        checkTheShippedArrangementIsPreinstalled();
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
     * The layout the editor under test is opened with: a copy of the shipped arrangement, made
     * for the run and removed afterwards.
     */
    private String editorLayoutId;

    /**
     * The host only registers a gamepad for the players named in the mask of an input packet,
     * so a layout that is not in the mask has all of its input ignored.
     */
    private void checkOnScreenLayoutRegistersGamepad() {
        check("the on-screen layout names player one",
                ControllerHandler.onScreenPlayerMask(true) == 1);
        check("no on-screen layout leaves player one out",
                ControllerHandler.onScreenPlayerMask(false) == 0);
    }

    /**
     * A trigger drives an analog byte, not a button flag, and it must do so with nothing
     * configured for it, exactly as the built-in on-screen trigger does.
     */
    private void checkTriggersWorkOnTheirOwn() {
        // The arrangement the app ships with belongs to the built-in layout and shows only while
        // nothing is stored for it. The check borrows that state and puts back whatever was there,
        // so it measures the built-in arrangement without disturbing the user's own.
        String savedElements = readStoredElements(UniversalLayoutStore.DEFAULT_LAYOUT_ID);

        SharedPreferences prefs = getSharedPreferences(
                "OSC_UNIVERSAL_" + UniversalLayoutStore.DEFAULT_LAYOUT_ID, MODE_PRIVATE);

        int savedWidth = prefs.getInt("savedWidth", 0);
        int savedHeight = prefs.getInt("savedHeight", 0);

        prefs.edit().clear().apply();

        UniversalVirtualController controller = new UniversalVirtualController(
                universalController.getControllerHandler(),
                (FrameLayout) universalController.getLayoutArea(), this,
                UniversalLayoutStore.DEFAULT_LAYOUT_ID);

        controller.refreshLayout();

        try {
            UniversalVirtualControllerElement lt = null;

            // The trigger is an ordinary button whose action is the left trigger of the host
            for (UniversalVirtualControllerElement element : controller.getElements()) {
                if (element.getButtonConfigIfAny() != null
                        && element.getElementId() == UniversalVirtualControllerElement.EID_LT) {
                    lt = element;
                    break;
                }
            }

            check("the built-in layout has a left trigger", lt != null);
            check("the left trigger is an ordinary button",
                    lt != null && UniversalElementTypes.ELEMENT_BUTTON.equals(lt.getElementTypeName()));

            boolean anyTriggerLeft = false;
            for (UniversalVirtualControllerElement element : controller.getElements()) {
                if ("trigger".equals(element.getElementTypeName())) {
                    anyTriggerLeft = true;
                    break;
                }
            }

            check("no control is of a trigger kind any more", !anyTriggerLeft);

            if (lt == null) {
                return;
            }

            UniversalVirtualController.ControllerMode originalMode = controller.getControllerMode();
            controller.setControllerMode(UniversalVirtualController.ControllerMode.Active);

            try {
                controller.getControllerInputContext().leftTrigger = 0;

                lt.onElementTouchEvent(motionEventFor(lt, MotionEvent.ACTION_DOWN, 0.5f, 0.5f));
                check("a trigger pulls its axis with the action the layout gives it",
                        controller.getControllerInputContext().leftTrigger == (byte) 0xFF);

                lt.onElementTouchEvent(motionEventFor(lt, MotionEvent.ACTION_UP, 0.5f, 0.5f));
                check("a trigger lets its axis out again",
                        controller.getControllerInputContext().leftTrigger == 0);
            } finally {
                lt.releaseConfiguredActions();
                controller.getControllerInputContext().leftTrigger = 0;
                controller.setControllerMode(originalMode);
            }
        } finally {
            // The borrowed controller takes its controls with it
            controller.destroy();

            // And the layout is exactly as it was before the check looked at it
            if (savedElements == null || savedElements.isEmpty()) {
                prefs.edit().clear().apply();
            }
            else {
                UniversalLayoutConfigurationLoader.writeStoredElements(this,
                        UniversalLayoutStore.DEFAULT_LAYOUT_ID, savedElements, savedWidth,
                        savedHeight);
            }
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

        // Measuring at full strength keeps the numbers the same whatever the layout's own
        // transparency happens to be set to
        pad.setElementOpacity(1f);
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

        // The arms are shaded from top to bottom, so the points they are sampled at differ a
        // little by design. What this catches is an arm that is off by tens, which is what a
        // leftover transparency on the shared paint used to do.
        check("every arm of a D-pad has the same transparency", maxAlpha - minAlpha <= 6);

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

    /**
     * Editing must not write the stored layout by itself, so that answering "don't save" on the
     * way out really does throw the changes away.
     */
    private void checkDontSaveLeavesTheLayoutAlone() {
        String before = readStoredElements(editorLayoutId);

        // A control that is really in the layout, so dragging it is a real edit rather than a
        // gesture on something that was taken out of the layout by an earlier check
        UniversalVirtualControllerElement element = null;
        for (UniversalVirtualControllerElement candidate : universalController.getElements()) {
            if (candidate.getLayoutParams() != null) {
                element = candidate;
                break;
            }
        }

        check("a control is in the layout for the leaving check", element != null);

        if (element == null) {
            return;
        }

        // A change of its own
        dispatchTouch(element, MotionEvent.ACTION_DOWN, 0.5f, 0.5f);
        dispatchTouch(element, MotionEvent.ACTION_MOVE, 0.8f, 0.8f);
        dispatchTouch(element, MotionEvent.ACTION_UP, 0.8f, 0.8f);

        check("editing marks the layout as having unsaved changes",
                universalController.isLayoutDirty());
        check("editing does not write the stored layout",
                readStoredElements(editorLayoutId).equals(before));

        // The user answers "don't save" on the way out
        editor.discardChanges();

        check("answering don't save clears the unsaved marker",
                !universalController.isLayoutDirty());
        check("answering don't save leaves the stored layout as it was",
                readStoredElements(editorLayoutId).equals(before));
    }

    /**
     * The stored set of controls of a layout, which is what the editor writes when it saves.
     */
    private String readStoredElements(String layoutId) {
        return getSharedPreferences("OSC_UNIVERSAL_" + layoutId, MODE_PRIVATE)
                .getString("elements", "");
    }


    /**
     * The position sliders must stop before a control leaves the screen, so it can always be
     * grabbed again and pulled back.
     */
    private void checkPositionStopsBeforeTheEdge() {
        UniversalVirtualControllerElement element = firstElementInLayout();
        check("a control is in the layout for the position check", element != null);

        if (element == null) {
            return;
        }

        editor.onElementTapped(element);

        int areaWidth = editor.getEditorRootView().getWidth();
        int areaHeight = editor.getEditorRootView().getHeight();

        check("the editor knows how big the layout area is", areaWidth > 0 && areaHeight > 0);

        // The panel itself is not a view tree a slider can be found in, so the editor's own
        // content is searched
        View editorContent = editor.findViewById(android.R.id.content);

        android.widget.SeekBar xSlider = findSeekBar(editorContent, R.string.editor_label_x);
        android.widget.SeekBar ySlider = findSeekBar(editorContent, R.string.editor_label_y);

        check("the drawer has a position slider for x", xSlider != null);
        check("the drawer has a position slider for y", ySlider != null);

        if (xSlider == null || ySlider == null) {
            return;
        }

        android.util.Log.i("LayoutSmoke", "area=" + areaWidth + "x" + areaHeight
                + " x slider max=" + xSlider.getMax() + " y slider max=" + ySlider.getMax());

        // A slider cannot reach past the area, so a dragged control always keeps a piece on
        // screen
        check("the x slider stops before the right edge", xSlider.getMax() < areaWidth);
        check("the y slider stops before the bottom edge", ySlider.getMax() < areaHeight);

        // And a position typed by hand is held back too
        int width = element.getWidth() > 0 ? element.getWidth() : 160;
        int height = element.getHeight() > 0 ? element.getHeight() : 160;

        // A typed number is kept by the row even when the slider cannot show it, which is the
        // path a value typed by hand takes into the editor
        UniversalEditorPanel.SliderRow xRow =
                editor.getPanelForTesting().getSliderRow(getString(R.string.editor_label_x));
        UniversalEditorPanel.SliderRow yRow =
                editor.getPanelForTesting().getSliderRow(getString(R.string.editor_label_y));

        xRow.setValue(9999);
        yRow.setValue(9999);

        check("the row keeps a typed value the slider cannot show", xRow.getValue() == 9999);

        editor.applyGeometryForTesting(9999, 9999, width, height);

        // The placed position comes from the configuration, which is what the edit wrote. The
        // view's own left edge only catches up on the next layout pass, so reading it here
        // would read the position from before the change.
        UniversalGeometry placed = element.getConfig().getGeometry();

        android.util.Log.i("LayoutSmoke", "typed 9999,9999 placed at "
                + placed.getLeft() + "," + placed.getTop() + " within "
                + areaWidth + "x" + areaHeight);

        check("a typed position keeps the control inside the layout",
                placed.getLeft() < areaWidth && placed.getTop() < areaHeight);
        check("a typed position keeps a piece of the control on screen",
                placed.getLeft() + width > 0 && placed.getLeft() < areaWidth);
        check("a typed position is held back rather than thrown to zero",
                placed.getLeft() > 0 && placed.getTop() > 0);

        // The piece that is left is what makes it reachable again: the control can be dragged
        // back from there
        check("the piece left on screen is wide enough to press", areaWidth - placed.getLeft() >= 24);

        // Put it back where it can be seen
        editor.applyGeometryForTesting(100, 100, width, height);

        // Dragging is the other way a control can be pushed out: grabbing its left edge and
        // pulling right would carry it off the screen entirely
        editor.applyGeometryForTesting(100, 100, width, height);

        dispatchTouch(element, MotionEvent.ACTION_DOWN, 0.0f, 0.5f);
        dispatchTouch(element, MotionEvent.ACTION_MOVE, 1.0f, 0.5f);

        int draggedTo = element.getConfig().getGeometry().getLeft();

        // The pointer cannot report a position past the screen, but a control follows it there,
        // so the limit has to hold on its own. The control is asked to go far past the edge.
        element.moveElementForTesting(0, 0, areaWidth * 3, areaHeight * 3);

        UniversalGeometry afterDrag = element.getConfig().getGeometry();

        android.util.Log.i("LayoutSmoke", "dragged to left=" + draggedTo
                + " forced to " + afterDrag.getLeft() + "," + afterDrag.getTop()
                + " within " + areaWidth + "x" + areaHeight);

        check("dragging a control to the right edge keeps a piece on screen",
                afterDrag.getLeft() < areaWidth);
        check("dragging a control to the bottom edge keeps a piece on screen",
                afterDrag.getTop() < areaHeight);
        check("a control dragged to the limit can still be pressed",
                areaWidth - afterDrag.getLeft() >= 24 && areaHeight - afterDrag.getTop() >= 24);

        dispatchTouch(element, MotionEvent.ACTION_UP, 1.0f, 0.5f);
        editor.applyGeometryForTesting(100, 100, width, height);
    }

    private android.widget.SeekBar findSeekBar(View root, int labelId) {
        String label = getString(labelId);
        return findSeekBar(root, label);
    }

    private android.widget.SeekBar findSeekBar(View root, String label) {
        if (!(root instanceof ViewGroup)) {
            return null;
        }

        ViewGroup group = (ViewGroup) root;

        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);

            if (child instanceof android.widget.SeekBar) {
                // A slider row is the label followed by the slider, so the label of a row is
                // looked up through its own children
                ViewGroup row = (ViewGroup) child.getParent();
                String rowLabel = labelOfRow(row);

                if (label.equals(rowLabel)) {
                    return (android.widget.SeekBar) child;
                }
            }
        }

        for (int i = 0; i < group.getChildCount(); i++) {
            android.widget.SeekBar found = findSeekBar(group.getChildAt(i), label);

            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private String labelOfRow(ViewGroup row) {
        if (row == null) {
            return "";
        }

        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);

            if (child instanceof android.widget.TextView
                    && !(child instanceof android.widget.SeekBar)) {
                return ((android.widget.TextView) child).getText().toString();
            }
        }

        return "";
    }

    /**
     * Everything that can be done to a control is a row of the drawer, and deleting is the one
     * row that is drawn differently.
     */
    private void checkTheDrawerOffersTheControlActions() {
        UniversalVirtualControllerElement element = firstElementInLayout();
        check("a control is in the layout for the actions check", element != null);

        if (element == null) {
            return;
        }

        editor.onElementTapped(element);

        java.util.List<String> labels = editor.getPanelForTesting().getButtonLabelsForTesting();

        android.util.Log.i("LayoutSmoke", "drawer buttons: " + labels);

        check("the drawer offers duplicating a control",
                labels.contains(getString(R.string.menu_duplicate)));
        check("the drawer offers copying the size of a control",
                labels.contains(getString(R.string.menu_copy_size)));
        check("the drawer offers deleting a control",
                labels.contains(getString(R.string.menu_delete)));

        // Pasting appears exactly when there is a size to paste, which a check that ran earlier
        // in this run may already have copied
        check("pasting is offered exactly when a size has been copied",
                labels.contains(getString(R.string.menu_paste_size)) == editor.hasCopiedSize());

        editor.copySizeOf(element);
        editor.onElementTapped(element);

        labels = editor.getPanelForTesting().getButtonLabelsForTesting();

        check("pasting is offered once a size is copied",
                labels.contains(getString(R.string.menu_paste_size)));

        // A long press must not open a menu any more
        editor.onElementTapped(null);
        editor.onElementLongPressed(element);

        check("holding a control selects it instead of opening a menu",
                editor.getSelectedElementForTesting() == element);
        check("holding a control leaves no dialog behind",
                editor.getVisibleDialogForTesting() == null);
    }

    /**
     * Dragging a control brings its settings up, the same way tapping it does.
     */
    private void checkDraggingAControlShowsItsDrawer() {
        UniversalVirtualControllerElement element = null;

        for (UniversalVirtualControllerElement candidate : universalController.getElements()) {
            if (candidate.getLayoutParams() != null) {
                element = candidate;
                break;
            }
        }

        check("a control is in the layout for the drag check", element != null);

        if (element == null) {
            return;
        }

        // Nothing is selected, so the drawer is closed
        editor.onElementTapped(null);
        check("no control is selected to begin with", editor.getSelectedElementForTesting() == null);
        check("the drawer is closed with nothing selected", !editor.isPanelVisibleForTesting());

        // Drag the control
        dispatchTouch(element, MotionEvent.ACTION_DOWN, 0.5f, 0.5f);
        dispatchTouch(element, MotionEvent.ACTION_MOVE, 0.6f, 0.6f);
        dispatchTouch(element, MotionEvent.ACTION_UP, 0.6f, 0.6f);

        check("dragging a control selects it", editor.getSelectedElementForTesting() == element);
        check("dragging a control opens its drawer", editor.isPanelVisibleForTesting());
    }

    private UniversalVirtualControllerElement firstElementInLayout() {
        for (UniversalVirtualControllerElement candidate : universalController.getElements()) {
            if (candidate.getLayoutParams() != null) {
                return candidate;
            }
        }

        return null;
    }


    /**
     * A layout made for one screen has to arrive correctly on another: a control keeps its
     * place on the screen, and grows by the smaller of the two changes so it is not stretched.
     */
    private void checkLayoutsMoveBetweenScreens() {
        // The example the export and import are built around: a control at a quarter of the way
        // across and halfway down a 200x100 screen, 25x25 big
        UniversalGeometry onSmall = new UniversalGeometry(100, 50, 25, 25);

        UniversalGeometry onBig = UniversalLayoutExchange.rescale(onSmall, 200, 100, 400, 400);

        android.util.Log.i("LayoutSmoke", "200x100 (100,50) 25x25 -> 400x400 ("
                + onBig.getLeft() + "," + onBig.getTop() + ") "
                + onBig.getWidth() + "x" + onBig.getHeight());

        // Half way across and half way down stays half way across and half way down
        check("a moved control keeps its place across the screen", onBig.getLeft() == 200);
        check("a moved control keeps its place down the screen", onBig.getTop() == 200);

        // The shorter of the two changes is 400/400 = 1, the taller is 400/100 = 4
        check("a moved control is not stretched", onBig.getWidth() == onBig.getHeight());

        // Twice as wide and the same height: the width doubles and the height stays put
        UniversalGeometry wider = UniversalLayoutExchange.rescale(
                new UniversalGeometry(100, 50, 25, 25), 200, 100, 400, 100);

        check("a control on a wider screen doubles where it sat", wider.getLeft() == 200);
        check("a control on a wider screen keeps its height", wider.getTop() == 50);
        check("a control on a wider screen is not stretched sideways",
                wider.getWidth() == wider.getHeight() && wider.getWidth() == 25);

        // The same screen leaves everything exactly as it was
        UniversalGeometry unchanged = UniversalLayoutExchange.rescale(onSmall, 200, 100, 200, 100);

        check("a layout opened on its own screen is left alone",
                unchanged.getLeft() == 100 && unchanged.getTop() == 50
                        && unchanged.getWidth() == 25 && unchanged.getHeight() == 25);
    }

    /**
     * A layout written out and read back on the same screen must come back exactly as it was.
     */
    private void checkLayoutsSurviveBeingWrittenOut() {
        String elements = "[{\"elementId\":7,\"type\":\"button\",\"name\":\"A\","
                + "\"geometry\":{\"left\":300,\"top\":200,\"width\":120,\"height\":90}}]";

        UniversalLayoutExchange.Imported imported = null;
        String text = null;

        try {
            text = UniversalLayoutExchange.toJson("My layout", 2510, 1156, elements);
            imported = UniversalLayoutExchange.fromJson(text);
        } catch (org.json.JSONException e) {
            android.util.Log.e("LayoutSmoke", "could not round trip a layout", e);
        }

        check("a layout can be written out and read back", imported != null);

        if (imported == null) {
            return;
        }

        check("the name of a layout survives being written out",
                "My layout".equals(imported.getName()));
        check("a layout file says which screen it was arranged on",
                imported.getSourceWidth() == 2510 && imported.getSourceHeight() == 1156);

        UniversalGeometry kept = geometryOf(imported.getElementsJson());

        check("a control comes back where it was",
                kept != null && kept.getLeft() == 300 && kept.getTop() == 200);
        check("a control comes back the size it was",
                kept != null && kept.getWidth() == 120 && kept.getHeight() == 90);

        // A file that is not ours is refused rather than half read
        boolean refused = false;

        try {
            UniversalLayoutExchange.fromJson("{\"hello\":\"world\"}");
        } catch (org.json.JSONException e) {
            refused = true;
        }

        check("a file that is not a layout is refused", refused);

        boolean refusedNewer = false;

        try {
            UniversalLayoutExchange.fromJson(
                    "{\"format\":\"moonlight-universal-layout\",\"version\":99,"
                            + "\"elements\":[]}");
        } catch (org.json.JSONException e) {
            refusedNewer = true;
        }

        check("a layout from a newer version of the app is refused", refusedNewer);

        boolean refusedPlaceless = false;

        try {
            UniversalLayoutExchange.fromJson(
                    "{\"format\":\"moonlight-universal-layout\",\"version\":1,"
                            + "\"elements\":[{\"elementId\":1}]}");
        } catch (org.json.JSONException e) {
            refusedPlaceless = true;
        }

        check("a control with no place is refused", refusedPlaceless);

        check("a shared layout is given a file name a file system accepts",
                "My-layout.json".equals(UniversalLayoutExchange.fileNameFor("My layout")));
        check("a file name cannot escape its folder",
                !UniversalLayoutExchange.fileNameFor("../../etc/passwd").contains("/")
                        && !UniversalLayoutExchange.fileNameFor("../../etc/passwd").contains(".."));
    }

    /**
     * Importing must not fit a layout to the screen the import happens on.
     *
     * The layouts screen is a portrait list while the controls belong to the landscape streaming
     * area, so measuring a layout against the list shrank every control to a fraction of itself
     * and moved them about, some off the screen altogether.
     */
    private void checkImportingDoesNotShrinkALayout() {
        int areaWidth = Math.max(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels);
        int areaHeight = Math.min(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels);

        // The control that came out at 74x74 in the corner of the screen instead of 160x160
        String elements = "[{\"elementId\":6,\"type\":\"button\",\"name\":\"A\","
                + "\"geometry\":{\"left\":2156,\"top\":337,\"width\":160,\"height\":160}}]";

        UniversalLayoutDefinition created = null;

        try {
            String text = UniversalLayoutExchange.toJson("Smoke import", areaWidth, areaHeight,
                    elements);
            created = UniversalLayoutExchange.store(this, UniversalLayoutExchange.fromJson(text));
        } catch (org.json.JSONException e) {
            android.util.Log.e("LayoutSmoke", "could not import a layout", e);
        }

        check("an imported layout is created", created != null);

        if (created == null) {
            return;
        }

        SharedPreferences preferences = getSharedPreferences(
                "OSC_UNIVERSAL_" + created.getId(), MODE_PRIVATE);

        String stored = preferences.getString("elements", "");
        UniversalGeometry geometry = geometryOf(stored);

        android.util.Log.i("LayoutSmoke", "imported control: " + (geometry == null ? "nothing"
                : geometry.getLeft() + "," + geometry.getTop() + " "
                + geometry.getWidth() + "x" + geometry.getHeight())
                + " saved screen " + preferences.getInt("savedWidth", 0) + "x"
                + preferences.getInt("savedHeight", 0));

        check("importing does not shrink a control",
                geometry != null && geometry.getWidth() == 160 && geometry.getHeight() == 160);
        check("importing does not move a control",
                geometry != null && geometry.getLeft() == 2156 && geometry.getTop() == 337);

        // The layout remembers the screen the file was arranged on, so it is fitted when it is
        // opened, where the size of the streaming area is known
        check("an imported layout remembers the screen it was arranged on",
                preferences.getInt("savedWidth", 0) == areaWidth
                        && preferences.getInt("savedHeight", 0) == areaHeight);
        check("an imported layout is not marked as already fitted",
                !preferences.getBoolean("rescaledToFit", false));

        // Importing twice makes two layouts rather than overwriting the first
        UniversalLayoutDefinition second = null;

        try {
            String text = UniversalLayoutExchange.toJson("Smoke import", areaWidth, areaHeight,
                    elements);
            second = UniversalLayoutExchange.store(this, UniversalLayoutExchange.fromJson(text));
        } catch (org.json.JSONException e) {
            android.util.Log.e("LayoutSmoke", "could not import a layout twice", e);
        }

        check("importing the same layout twice makes a second layout",
                second != null && !second.getId().equals(created.getId()));

        if (second != null) {
            getSharedPreferences("OSC_UNIVERSAL_" + second.getId(), MODE_PRIVATE)
                    .edit().clear().apply();
            UniversalLayoutStore.getInstance(this).deleteLayout(second.getId());
        }

        getSharedPreferences("OSC_UNIVERSAL_" + created.getId(), MODE_PRIVATE)
                .edit().clear().apply();
        UniversalLayoutStore.getInstance(this).deleteLayout(created.getId());
    }

    /**
     * The geometry of the first control in a written out layout.
     */
    private UniversalGeometry geometryOf(String elementsJson) {
        try {
            org.json.JSONArray elements = new org.json.JSONArray(elementsJson);

            if (elements.length() == 0) {
                return null;
            }

            return UniversalLayoutExchange.readGeometry(
                    elements.getJSONObject(0).getJSONObject("geometry"));
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    /**
     * The drawer's actions are laid out the way they are meant to be, with pasting out of reach
     * until there is a size to paste.
     */
    private void checkDrawerActionRows() {
        UniversalVirtualControllerElement element = firstElementInLayout();

        if (element == null) {
            return;
        }

        // Start from a drawer with something copied, so the order can be read in full
        editor.copySizeOf(element);
        editor.onElementTapped(null);
        editor.onElementTapped(element);

        java.util.List<String> labels = editor.getPanelForTesting().getButtonLabelsForTesting();
        java.util.List<String> disabled =
                editor.getPanelForTesting().getDisabledButtonLabelsForTesting();

        int copyAt = labels.indexOf(getString(R.string.menu_copy_size));
        int pasteAt = labels.indexOf(getString(R.string.menu_paste_size));
        int duplicateAt = labels.indexOf(getString(R.string.menu_duplicate));
        int deleteAt = labels.indexOf(getString(R.string.menu_delete));

        android.util.Log.i("LayoutSmoke", "actions in order: copy=" + copyAt
                + " paste=" + pasteAt + " duplicate=" + duplicateAt + " delete=" + deleteAt);

        check("copying and pasting share a row", copyAt >= 0 && pasteAt == copyAt + 1);
        check("duplicating comes after copying and pasting",
                duplicateAt == pasteAt + 1);
        check("deleting is the last of the actions", deleteAt == duplicateAt + 1);
        check("nothing is out of reach once a size is copied", disabled.isEmpty());

        // With nothing copied, pasting is shown but cannot be pressed
        editor.setCopiedSizeForTesting(0, 0, 0);
        editor.onElementTapped(null);
        editor.onElementTapped(element);

        disabled = editor.getPanelForTesting().getDisabledButtonLabelsForTesting();

        android.util.Log.i("LayoutSmoke", "out of reach: " + disabled);

        check("pasting is out of reach before a size is copied",
                disabled.contains(getString(R.string.menu_paste_size)));
        check("pasting is still shown, so the row does not move",
                editor.getPanelForTesting().getButtonLabelsForTesting()
                        .contains(getString(R.string.menu_paste_size)));

        editor.setCopiedSizeForTesting(120, 90, 22);
    }


    /**
     * A shared layout has to come back through the address that is handed to the other app,
     * which is what makes sharing work at all.
     */
    private void checkASharedLayoutCanBeReadBack() {
        String json = null;

        try {
            json = UniversalLayoutExchange.toJson("Smoke layout", 100, 100, "[]");
        } catch (org.json.JSONException e) {
            android.util.Log.e("LayoutSmoke", "could not write a layout", e);
        }

        check("a layout can be written out for sharing", json != null);

        if (json == null) {
            return;
        }

        java.io.File file = null;

        try {
            file = UniversalLayoutExchange.writeShareFile(this, "Smoke layout", json);
        } catch (java.io.IOException e) {
            android.util.Log.e("LayoutSmoke", "could not write a share file", e);
        }

        check("a layout can be written to a share file", file != null && file.exists());

        if (file == null) {
            return;
        }

        Uri address = Uri.parse("content://" + UniversalLayoutShareProvider.authority(this)
                + "/" + file.getName());

        android.util.Log.i("LayoutSmoke", "shared as " + address);

        // The name and size a receiving app asks for
        android.database.Cursor cursor = getContentResolver()
                .query(address, null, null, null, null);

        String name = null;

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);

                if (column >= 0) {
                    name = cursor.getString(column);
                }
            }

            cursor.close();
        }

        check("the shared layout is offered under its file name",
                "Smoke-layout.json".equals(name));

        // And its contents
        String readBack = null;

        try {
            java.io.InputStream in = getContentResolver().openInputStream(address);

            if (in != null) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int count;

                while ((count = in.read(buffer)) > 0) {
                    out.write(buffer, 0, count);
                }

                in.close();
                readBack = out.toString("UTF-8");
            }
        } catch (java.io.IOException e) {
            android.util.Log.e("LayoutSmoke", "could not read the shared layout", e);
        }

        check("the shared layout can be read back through its address", json.equals(readBack));

        // An address pointing outside the folder is refused rather than served
        boolean refused = false;

        try {
            getContentResolver().openInputStream(Uri.parse(
                    "content://" + UniversalLayoutShareProvider.authority(this)
                            + "/" + Uri.encode("../databases")));
        } catch (java.io.FileNotFoundException e) {
            refused = true;
        } catch (IllegalArgumentException e) {
            refused = true;
        } catch (SecurityException e) {
            refused = true;
        }

        check("a shared layout address cannot reach outside its folder", refused);
    }


    /**
     * A layout arranged on another screen is fitted when it is opened, which is where the size
     * of the area the controls live in is known. This is what makes a layout from a phone work
     * on a tablet, whether it was opened from the list or imported from a file.
     */
    private void checkALayoutFromAnotherScreenIsFitted() {
        UniversalVirtualControllerElement element = firstElementInLayout();

        check("a control is in the layout for the fitting check", element != null);

        if (element == null) {
            return;
        }

        // A layout arranged on a screen of 200x100, which is nothing like this one.
        //
        // The place is set on the control's own configuration and then applied to it: the
        // fitting reads the configuration, so setting only the view would leave it fitting
        // whatever was there before.
        UniversalGeometry arranged = element.getConfig().getGeometry();
        arranged.setLeft(100);
        arranged.setTop(50);
        arranged.setWidth(25);
        arranged.setHeight(25);
        arranged.applyTo(element);

        android.util.Log.i("LayoutSmoke", "before fitting: config has "
                + element.getConfig().getGeometry().getLeft() + ","
                + element.getConfig().getGeometry().getTop() + " "
                + element.getConfig().getGeometry().getWidth() + "x"
                + element.getConfig().getGeometry().getHeight());

        String file = UniversalLayoutConfigurationLoader.getLayoutPreferenceFileName(
                SCRATCH_LAYOUT_ID);

        SharedPreferences preferences = getSharedPreferences(file, Context.MODE_PRIVATE);
        preferences.edit()
                .putInt("savedWidth", 200)
                .putInt("savedHeight", 100)
                .putBoolean("rescaledToFit", false)
                .apply();

        android.util.Log.i("LayoutSmoke", "layout says it was arranged on "
                + preferences.getInt("savedWidth", 0) + "x"
                + preferences.getInt("savedHeight", 0));

        int areaWidth = UniversalLayoutArea.areaWidth(element);
        int areaHeight = UniversalLayoutArea.areaHeight(element);

        UniversalLayoutConfigurationLoader.fitToAreaForTesting(universalController, this,
                SCRATCH_LAYOUT_ID, areaWidth, areaHeight);

        UniversalGeometry fitted = element.getConfig().getGeometry();

        // Worked out by hand: the place keeps its share of the screen, the size grows by the
        // smaller of the two changes
        int expectedLeft = Math.round(100f * areaWidth / 200);
        int expectedTop = Math.round(50f * areaHeight / 100);
        int expectedSize = Math.round(25f * Math.min((float) areaWidth / 200,
                (float) areaHeight / 100));

        android.util.Log.i("LayoutSmoke", "fitted from 200x100 to " + areaWidth + "x" + areaHeight
                + ": " + fitted.getLeft() + "," + fitted.getTop() + " "
                + fitted.getWidth() + "x" + fitted.getHeight()
                + " expected " + expectedLeft + "," + expectedTop + " "
                + expectedSize + "x" + expectedSize);

        check("a control keeps its share of the screen across", fitted.getLeft() == expectedLeft);
        check("a control keeps its share of the screen down", fitted.getTop() == expectedTop);
        check("a control is not stretched by the fitting",
                fitted.getWidth() == expectedSize && fitted.getHeight() == expectedSize);

        // The fitting is a change the user has not saved, and it happens once
        check("fitting a layout marks it as having unsaved changes",
                universalController.isLayoutDirty());
        check("a fitted layout is remembered as fitted",
                preferences.getBoolean("rescaledToFit", false));

        // Opening it again on the same screen changes nothing
        UniversalGeometry afterFirstFit = new UniversalGeometry(fitted.getLeft(), fitted.getTop(),
                fitted.getWidth(), fitted.getHeight());

        UniversalLayoutConfigurationLoader.fitToAreaForTesting(universalController, this,
                SCRATCH_LAYOUT_ID, areaWidth, areaHeight);

        UniversalGeometry afterSecondFit = element.getConfig().getGeometry();

        check("fitting a layout twice changes nothing the second time",
                afterSecondFit.getLeft() == afterFirstFit.getLeft()
                        && afterSecondFit.getTop() == afterFirstFit.getTop()
                        && afterSecondFit.getWidth() == afterFirstFit.getWidth()
                        && afterSecondFit.getHeight() == afterFirstFit.getHeight());

        preferences.edit().clear().apply();
        universalController.clearLayoutDirty();

        // Put the control somewhere ordinary again
        UniversalGeometry ordinary = element.getConfig().getGeometry();
        ordinary.setLeft(100);
        ordinary.setTop(100);
        ordinary.setWidth(160);
        ordinary.setHeight(160);
        ordinary.applyTo(element);
    }


    /**
     * The whole journey the user makes: a layout of many controls is written out and read back,
     * and every control has to end up in the same place and the same size.
     *
     * This is the check that would have caught controls shrinking to a fraction of themselves
     * when a layout was exported and imported again on the same device.
     */
    private void checkExportThenImportKeepsEveryControl() {
        UniversalLayoutStore store = UniversalLayoutStore.getInstance(this);

        UniversalLayoutDefinition source = store.addLayout(
                store.makeUniqueName("smoke export"));

        // Saved the way the editor saves it, on this device's screen in landscape
        int areaWidth = Math.max(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels);
        int areaHeight = Math.min(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels);

        UniversalLayoutConfigurationLoader.saveProfile(universalController, this, source.getId(),
                areaWidth, areaHeight);

        String stored = readStoredElements(source.getId());
        UniversalLayoutDefinition copy = null;

        try {
            String text = UniversalLayoutExchange.toJson(source.getName(), areaWidth, areaHeight,
                    stored);

            // Importing happens on the layouts screen, which is portrait, so the copy is made
            // in exactly the conditions the user hit
            copy = UniversalLayoutExchange.store(this, UniversalLayoutExchange.fromJson(text));
        } catch (org.json.JSONException e) {
            android.util.Log.e("LayoutSmoke", "could not export and import a layout", e);
        }

        check("a layout can be exported and imported", copy != null);

        if (copy == null) {
            return;
        }

        String importedStored = readStoredElements(copy.getId());

        int compared = 0;
        int movedOrResized = 0;
        String firstDifference = null;

        try {
            org.json.JSONArray before = new org.json.JSONArray(stored);
            org.json.JSONArray after = new org.json.JSONArray(importedStored);

            check("the layout comes back with the same number of controls",
                    before.length() == after.length());

            for (int i = 0; i < Math.min(before.length(), after.length()); i++) {
                org.json.JSONObject a = before.getJSONObject(i);
                org.json.JSONObject b = after.getJSONObject(i);

                compared++;

                UniversalGeometry was = UniversalLayoutExchange.readGeometry(
                        a.getJSONObject("geometry"));
                UniversalGeometry is = UniversalLayoutExchange.readGeometry(
                        b.getJSONObject("geometry"));

                boolean same = was.getLeft() == is.getLeft() && was.getTop() == is.getTop()
                        && was.getWidth() == is.getWidth() && was.getHeight() == is.getHeight();

                if (!same) {
                    movedOrResized++;

                    if (firstDifference == null) {
                        firstDifference = "control " + a.optInt("elementId")
                                + " was " + was.getLeft() + "," + was.getTop() + " "
                                + was.getWidth() + "x" + was.getHeight()
                                + " came back " + is.getLeft() + "," + is.getTop() + " "
                                + is.getWidth() + "x" + is.getHeight();
                    }
                }

                if (a.optInt("elementId") != b.optInt("elementId")) {
                    firstDifference = "control " + a.optInt("elementId")
                            + " came back as " + b.optInt("elementId");
                    movedOrResized++;
                }
            }
        } catch (org.json.JSONException e) {
            android.util.Log.e("LayoutSmoke", "could not compare the layouts", e);
            movedOrResized++;
        }

        android.util.Log.i("LayoutSmoke", "export and import compared " + compared
                + " controls, " + movedOrResized + " changed"
                + (firstDifference == null ? "" : (", first: " + firstDifference)));

        check("every control of an exported layout comes back",
                compared > 0 && compared == universalController.getElements().size());
        check("no control moves or changes size in an export and an import",
                movedOrResized == 0);

        getSharedPreferences("OSC_UNIVERSAL_" + copy.getId(), MODE_PRIVATE).edit().clear().apply();
        store.deleteLayout(copy.getId());
        getSharedPreferences("OSC_UNIVERSAL_" + source.getId(), MODE_PRIVATE).edit().clear().apply();
        store.deleteLayout(source.getId());
    }


    /**
     * The menu that takes the place of the back button ending the stream.
     *
     * It is checked on its own with a stand-in stream, because a real one needs a host to
     * connect to.
     */
    private void checkStreamMenu() {
        final int[] chosenTouchMode = new int[]{-1};
        final String[] chosenLayout = new String[]{null};
        final boolean[] keyboardAsked = new boolean[]{false};
        final boolean[] exited = new boolean[]{false};
        final int[] sensitivity = new int[]{100};
        final boolean[] controlsShown = new boolean[]{true};
        final int[] opacity = new int[]{100};
        final boolean[] vibration = new boolean[]{true};

        final java.util.List<UniversalLayoutDefinition> layouts = new java.util.ArrayList<>();
        layouts.add(new UniversalLayoutDefinition("one", "First layout", "", 0));
        layouts.add(new UniversalLayoutDefinition("two", "Second layout", "", 0));

        FrameLayout area = new FrameLayout(this);
        UniversalStreamDrawer drawer = new UniversalStreamDrawer(this, area,
                new UniversalStreamDrawer.Host() {
                    @Override
                    public void onExitStream() {
                        exited[0] = true;
                    }

                    @Override
                    public void onShowKeyboard() {
                        keyboardAsked[0] = true;
                    }

                    @Override
                    public int getTouchMode() {
                        return chosenTouchMode[0] < 0
                                ? com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_TRACKPAD
                                : chosenTouchMode[0];
                    }

                    @Override
                    public void onTouchModeChanged(int mode) {
                        chosenTouchMode[0] = mode;
                    }

                    @Override
                    public java.util.List<UniversalLayoutDefinition> getLayouts() {
                        return layouts;
                    }

                    @Override
                    public String getSelectedLayoutId() {
                        return "one";
                    }

                    @Override
                    public void onLayoutChosen(String layoutId) {
                        chosenLayout[0] = layoutId;
                    }

                    @Override
                    public boolean isOnScreenControlsEnabled() {
                        return controlsShown[0];
                    }

                    @Override
                    public void onOnScreenControlsToggled(boolean enabled) {
                        controlsShown[0] = enabled;
                    }

                    @Override
                    public int getLayoutOpacity() {
                        return opacity[0];
                    }

                    @Override
                    public void onLayoutOpacityChanged(int percent) {
                        opacity[0] = percent;
                    }

                    @Override
                    public int getSensitivity() {
                        return sensitivity[0];
                    }

                    @Override
                    public void onSensitivityChanged(int percent) {
                        sensitivity[0] = percent;
                    }

                    @Override
                    public boolean isVibrationEnabled() {
                        return vibration[0];
                    }

                    @Override
                    public void onVibrationChanged(boolean enabled) {
                        vibration[0] = enabled;
                    }
                });

        check("the stream menu starts closed", !drawer.isOpen());

        // The sheet that closes the menu has to be on screen and touchable, or a tap outside
        // would fall through to the game
        check("the stream menu has a sheet over the screen",
                drawer.getSheetForTesting() != null && drawer.getSheetForTesting().isClickable());

        check("the first press of back opens the stream menu", drawer.handleBack());
        check("the stream menu is open after back", drawer.isOpen());

        java.util.List<String> rows = drawer.getRowTitlesForTesting();

        android.util.Log.i("LayoutSmoke", "stream menu rows: " + rows);

        check("the stream menu offers the keyboard",
                containsLabel(rows, getString(R.string.stream_menu_keyboard)));
        check("the stream menu offers the touch setting",
                containsLabel(rows, getString(R.string.stream_menu_touch)));
        check("the stream menu offers the layout",
                containsLabel(rows, getString(R.string.stream_menu_layout)));

        Button exit = drawer.getExitButtonForTesting();

        android.util.Log.i("LayoutSmoke", "exit button: " + exit + " text="
                + (exit == null ? "none" : exit.getText()) + " expected="
                + getString(R.string.stream_menu_exit) + " tree=" + describeTree(drawer.getView()));

        check("the transparency row is under the layout row",
                indexOfRow(rows, getString(R.string.stream_menu_opacity))
                        > indexOfRow(rows, getString(R.string.stream_menu_layout)));

        java.util.List<String> touchChoices =
                drawer.getChoiceLabelsForTesting(getString(R.string.stream_menu_touch));

        android.util.Log.i("LayoutSmoke", "touch choices: " + touchChoices);

        java.util.List<String> wantedOrder = new java.util.ArrayList<>();
        wantedOrder.add(getString(R.string.stream_menu_touch_off));
        wantedOrder.add(getString(R.string.stream_menu_touch_trackpad));
        wantedOrder.add(getString(R.string.stream_menu_touch_events));
        wantedOrder.add(getString(R.string.stream_menu_touch_right_stick));
        wantedOrder.add(getString(R.string.stream_menu_touch_left_stick));

        check("the touch modes are offered in the order the user asked for",
                touchChoices.equals(wantedOrder));

        // What the touchpad does is a row of its own, offered only with the touchpad
        check("the touchpad's own row appears with the touchpad",
                containsLabel(rows, getString(R.string.stream_menu_touchpad_mode)));

        java.util.List<String> touchpadChoices =
                drawer.getChoiceLabelsForTesting(getString(R.string.stream_menu_touchpad_mode));

        android.util.Log.i("LayoutSmoke", "touchpad choices: " + touchpadChoices);

        java.util.List<String> wantedTouchpad = new java.util.ArrayList<>();
        wantedTouchpad.add(getString(R.string.stream_menu_touchpad_standard));
        wantedTouchpad.add(getString(R.string.stream_menu_touchpad_direct));
        wantedTouchpad.add(getString(R.string.stream_menu_touchpad_no_clicks));

        check("the touchpad offers its three kinds in order",
                touchpadChoices.equals(wantedTouchpad));

        // Choosing the kind of touchpad keeps the menu open and folds only its own list away
        drawer.show();
        drawer.clickRowForTesting(getString(R.string.stream_menu_touchpad_mode));

        check("choosing the kind of touchpad keeps the menu open",
                drawer.clickChoiceForTesting(getString(R.string.stream_menu_touchpad_no_clicks))
                        && drawer.isOpen());
        check("the kind of touchpad is reported",
                chosenTouchMode[0]
                        == com.limelight.preferences.PreferenceConfiguration
                        .TOUCH_MODE_TRACKPAD_NO_CLICKS);

        // Choosing a layout keeps the menu open as well
        drawer.show();
        drawer.clickRowForTesting(getString(R.string.stream_menu_layout));
        drawer.clickChoiceForTesting("Second layout");

        check("choosing a layout keeps the menu open", drawer.isOpen());

        // With a touchpad kind chosen, the row still says touchpad in the main list
        check("the main list reports the touchpad",
                containsLabel(drawer.getRowTitlesForTesting(),
                        getString(R.string.stream_menu_touch) + "   \u00b7   "
                                + getString(R.string.stream_menu_touch_trackpad)));

        check("the menu names the native touch mode as the user asked",
                touchChoices.contains(getString(R.string.stream_menu_touch_events)));
        check("the native touch mode is called what the user asked for",
                "Touch native (experimental)".equals(getString(R.string.stream_menu_touch_events)));
        check("the cursor-following touchpad is called what it is",
                "Direct cursor".equals(getString(R.string.stream_menu_touchpad_direct)));

        check("the stream menu has a button to leave the stream",
                exit != null && getString(R.string.stream_menu_exit).contentEquals(exit.getText()));

        // A press of back while the menu is open leaves the stream
        check("a press of back while the menu is open is used", drawer.handleBack());
        check("a press of back while the menu is open leaves the stream", exited[0]);

        exited[0] = false;

        // Tapping outside closes the menu and does nothing else
        drawer.show();
        drawer.getSheetForTesting().performClick();

        check("tapping outside the stream menu closes it", !drawer.isOpen());
        check("tapping outside the stream menu does not leave the stream", !exited[0]);

        // The rows unfold, and a choice is applied at once
        drawer.show();

        check("the touch setting unfolds",
                drawer.clickRowForTesting(getString(R.string.stream_menu_touch)));
        check("the touch setting offers turning the touchscreen off",
                drawer.clickChoiceForTesting(getString(R.string.stream_menu_touch_off)));

        check("choosing a touch mode reports it",
                chosenTouchMode[0] == com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_OFF);

        // The menu stays open, so a second setting can be changed without opening it again
        check("choosing a touch mode keeps the menu open", drawer.isOpen());

        drawer.show();

        check("the touch setting unfolds again",
                drawer.clickRowForTesting(getString(R.string.stream_menu_touch)));
        check("the touch setting offers the right stick",
                drawer.clickChoiceForTesting(getString(R.string.stream_menu_touch_right_stick)));

        check("the right stick can be chosen",
                chosenTouchMode[0] == com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_RIGHT_STICK);

        drawer.show();

        check("the layout row unfolds",
                drawer.clickRowForTesting(getString(R.string.stream_menu_layout)));
        check("a layout can be chosen",
                drawer.clickChoiceForTesting("Second layout"));

        check("choosing a layout reports it", "two".equals(chosenLayout[0]));
        check("choosing a layout keeps the menu open", drawer.isOpen());

        // The keyboard row closes the menu and asks for the keyboard, rather than toggling it
        drawer.show();

        check("the keyboard row answers a press",
                drawer.clickRowForTesting(getString(R.string.stream_menu_keyboard)));
        check("the keyboard row asks for the keyboard", keyboardAsked[0]);
        check("the keyboard row closes the menu", !drawer.isOpen());

        // And the button at the bottom is the other way out
        drawer.show();
        exit.performClick();

        check("the button at the bottom leaves the stream", exited[0]);

        // Every row that unfolds has an arrow at its right edge, which turns over while its
        // list is showing
        drawer.show();

        android.widget.ImageView touchArrow =
                drawer.getArrowForTesting(getString(R.string.stream_menu_touch));

        check("a row that unfolds has an arrow", touchArrow != null);
        check("the arrow of a closed row points down", touchArrow != null
                && touchArrow.getRotation() == 0f);

        drawer.clickRowForTesting(getString(R.string.stream_menu_touch));

        check("the arrow turns over while the row is open",
                touchArrow != null && touchArrow.getRotation() == 180f);

        drawer.clickRowForTesting(getString(R.string.stream_menu_touch));

        check("the arrow points down again once the row is closed",
                touchArrow != null && touchArrow.getRotation() == 0f);

        check("a row that unfolds has an arrow on the layout row too",
                drawer.getArrowForTesting(getString(R.string.stream_menu_layout)) != null);
        check("a row that does not unfold has no arrow",
                drawer.getArrowForTesting(getString(R.string.stream_menu_keyboard)) == null);

        // The choices of a row share one group, which is what stops every choice the user has
        // pressed from staying marked
        check("the choices of a row are exclusive",
                drawer.getChoiceGroupsForTesting(getString(R.string.stream_menu_touch)) == 1);

        drawer.clickRowForTesting(getString(R.string.stream_menu_touch));

        check("only one touch mode is marked at a time",
                drawer.getMarkedChoicesForTesting(getString(R.string.stream_menu_touch)) == 1);

        drawer.clickChoiceForTesting(getString(R.string.stream_menu_touch_off));
        drawer.show();
        drawer.clickRowForTesting(getString(R.string.stream_menu_touch));

        check("choosing another mode leaves one marked",
                drawer.getMarkedChoicesForTesting(getString(R.string.stream_menu_touch)) == 1);

        // The experimental mode is offered like any other
        check("the experimental real touch mode can be chosen",
                drawer.clickChoiceForTesting(getString(R.string.stream_menu_touch_events)));
        check("the experimental mode is reported",
                chosenTouchMode[0]
                        == com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_TOUCH_EVENTS);

        // The switch for the on-screen controls sits between the touch setting and the layout
        check("the switch for the on-screen controls is its own row",
                containsLabel(rows, getString(R.string.stream_menu_controls)));

        check("the controls are shown to begin with", controlsShown[0]);

        drawer.clickRowForTesting(getString(R.string.stream_menu_controls));
        check("the switch turns the controls off", !controlsShown[0]);

        drawer.clickRowForTesting(getString(R.string.stream_menu_controls));
        check("the switch turns the controls back on", controlsShown[0]);

        // The layout row no longer offers a screen with no controls, the switch does that
        drawer.show();
        drawer.clickRowForTesting(getString(R.string.stream_menu_layout));

        java.util.List<String> layoutChoices =
                drawer.getChoiceLabelsForTesting(getString(R.string.stream_menu_layout));

        android.util.Log.i("LayoutSmoke", "layout choices: " + layoutChoices);

        check("the layout list holds only layouts", layoutChoices.size() == 2
                && layoutChoices.contains("First layout") && layoutChoices.contains("Second layout"));

        // The transparency slider is there and reports what it is moved to
        drawer.show();

        android.widget.SeekBar opacitySlider = findOpacitySlider(drawer.getView());

        check("the menu has an opacity slider", opacitySlider != null);

        // The slider has to be laid out before a touch on it means anything
        int menuWidth = getResources().getDisplayMetrics().widthPixels;
        int menuHeight = getResources().getDisplayMetrics().heightPixels;
        View menu = drawer.getView();

        menu.measure(View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(menuHeight, View.MeasureSpec.EXACTLY));
        menu.layout(0, 0, menuWidth, menuHeight);

        check("the opacity slider can be dragged",
                drawer.dragOpacityToForTesting(45));

        android.util.Log.i("LayoutSmoke", "opacity from the menu slider: " + opacity[0]);

        // The thumb takes up part of the width of a slider, so where a touch lands is near the
        // value it means rather than exactly on it
        check("dragging the opacity slider reports a new value",
                opacity[0] >= 40 && opacity[0] <= 50);
    }

    /**
     * The slider of the opacity row, which is the only one in the menu.
     */
    private android.widget.SeekBar findOpacitySlider(View view) {
        if (view instanceof android.widget.SeekBar) {
            return (android.widget.SeekBar) view;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;

            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.SeekBar found = findOpacitySlider(group.getChildAt(i));

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * What is in a view tree, which is how a check can tell that something was never added.
     */
    private String describeTree(View view) {
        StringBuilder text = new StringBuilder(view.getClass().getSimpleName());

        if (view instanceof android.widget.TextView) {
            text.append("(").append(((android.widget.TextView) view).getText()).append(")");
        }

        if (view instanceof ViewGroup) {
            text.append("[");

            ViewGroup group = (ViewGroup) view;

            for (int i = 0; i < group.getChildCount(); i++) {
                if (i > 0) {
                    text.append(", ");
                }

                text.append(describeTree(group.getChildAt(i)));
            }

            text.append("]");
        }

        return text.toString();
    }

    /**
     * Where a row sits in the list of rows, found by the title it starts with.
     */
    private static int indexOfRow(java.util.List<String> rows, String title) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).startsWith(title)) {
                return i;
            }
        }

        return -1;
    }

    private static boolean containsLabel(java.util.List<String> labels, String label) {
        for (String text : labels) {
            if (text.startsWith(label)) {
                return true;
            }
        }

        return false;
    }


    /**
     * Deleting the control that is being edited has to close its drawer, or the editor is left
     * showing the settings of something that is no longer there.
     */
    private void checkDeletingClosesTheDrawer() {
        UniversalVirtualControllerElement element = firstElementInLayout();

        check("a control is in the layout for the delete check", element != null);

        if (element == null) {
            return;
        }

        editor.onElementTapped(element);

        check("editing a control opens its drawer", editor.isPanelVisibleForTesting());
        check("the control is the selected one", editor.getSelectedElementForTesting() == element);

        // The control is taken out the way the drawer's own Delete row does it, through the
        // editor's question, so nothing about the path is skipped
        editor.deleteSelectedForTesting();

        check("deleting the edited control clears the selection",
                editor.getSelectedElementForTesting() == null);
        check("deleting the edited control closes its drawer",
                !editor.isPanelVisibleForTesting());
    }

    /**
     * Choosing a layout after a clean screen has to bring the controls back.
     */
    private void checkChoosingALayoutShowsItAgain() {
        UniversalLayoutDefinition layout = UniversalLayoutStore.getInstance(this)
                .addLayout(UniversalLayoutStore.getInstance(this).makeUniqueName("smoke visible"));

        UniversalLayoutConfigurationLoader.saveProfile(universalController, this, layout.getId(),
                1920, 1080);

        // The menu hides the layout...
        universalController.hide();

        check("hiding the layout hides its controls",
                !universalController.getElements().isEmpty()
                        && universalController.getElements().get(0).getVisibility() != View.VISIBLE
                        || !universalController.isVisible());

        check("the controller says the layout is hidden", !universalController.isVisible());

        // ...and choosing a layout has to show it again, which is what the menu does
        universalController.loadLayout(layout.getId());

        check("a layout that was rebuilt while hidden is still hidden",
                !universalController.isVisible());

        universalController.show();

        int visibleControls = 0;

        for (UniversalVirtualControllerElement control : universalController.getElements()) {
            if (control.getVisibility() == View.VISIBLE) {
                visibleControls++;
            }
        }

        android.util.Log.i("LayoutSmoke", "after showing again: " + visibleControls + " of "
                + universalController.getElements().size() + " controls are visible");

        check("showing the layout again shows every control",
                visibleControls == universalController.getElements().size()
                        && visibleControls > 0);

        getSharedPreferences("OSC_UNIVERSAL_" + layout.getId(), MODE_PRIVATE).edit().clear().apply();
        UniversalLayoutStore.getInstance(this).deleteLayout(layout.getId());
    }


    /**
     * A control can be told to pass a held finger's movement on, which is what lets one finger
     * fire and aim at the same time.
     */
    private void checkPassThrough() {
        UniversalDigitalButton button = new UniversalDigitalButton(universalController, 8901, 0, this);

        check("a control does not pass movement on unless it is told to",
                !button.getConfig().isPassThrough());

        button.getConfig().setPassThrough(true);
        check("a control can be told to pass movement on", button.getConfig().isPassThrough());

        // It survives a save and a load, which is what a layout does between streams
        boolean kept = false;

        try {
            UniversalElementConfig reloaded = new UniversalElementConfig();
            reloaded.loadFromJson(button.getConfig().toJson());
            kept = reloaded.isPassThrough();
        } catch (org.json.JSONException e) {
            kept = false;
        }

        check("passing movement on survives being saved", kept);

        // And it is copied along with the rest of a control
        check("copying a control copies the setting",
                button.getConfig().copy().isPassThrough());

        // The layout only reports movement for a control that has it
        final int[] moved = new int[]{0};

        universalController.setPassThroughListener(
                new UniversalVirtualController.PassThroughListener() {
                    @Override
                    public void onPassThroughMoved(int x, int y) {
                        moved[0]++;
                    }

                    @Override
                    public void onPassThroughEnded() {
                    }
                });

        button.measure(View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY));
        button.layout(0, 0, 120, 120);

        UniversalVirtualController.ControllerMode originalMode = universalController.getControllerMode();
        universalController.setControllerMode(UniversalVirtualController.ControllerMode.Active);

        try {
            // A press, then a drag with the same finger
            dispatchTouch(button, MotionEvent.ACTION_DOWN, 0.5f, 0.5f);
            dispatchTouch(button, MotionEvent.ACTION_MOVE, 0.6f, 0.5f);
            dispatchTouch(button, MotionEvent.ACTION_MOVE, 0.7f, 0.5f);

            android.util.Log.i("LayoutSmoke", "movements passed on: " + moved[0]);

            check("a held control passes its finger's movement on", moved[0] >= 2);

            // With the setting off, nothing is passed on
            button.getConfig().setPassThrough(false);
            moved[0] = 0;

            dispatchTouch(button, MotionEvent.ACTION_DOWN, 0.5f, 0.5f);
            dispatchTouch(button, MotionEvent.ACTION_MOVE, 0.6f, 0.5f);
            dispatchTouch(button, MotionEvent.ACTION_MOVE, 0.7f, 0.5f);

            check("a control without the setting passes nothing on", moved[0] == 0);

            dispatchTouch(button, MotionEvent.ACTION_UP, 0.7f, 0.5f);
        } finally {
            universalController.setControllerMode(originalMode);
            universalController.setPassThroughListener(null);
        }
    }


    /**
     * What a touch on the trackpad means: a tap is a click, two fingers are the right button,
     * and a second tap that is held drags.
     *
     * The numbers here are the ones that used to fail: two fingers never stay as still or lift
     * as quickly as one, and a check that asked the same of them is why the right click only
     * sometimes happened.
     */
    private void checkTrackpadGestures() {
        // One finger, quick and still: a click
        check("a quick still tap with one finger is a click",
                TouchGestureRules.isTap(1, 1, false, false, false, 5, 5, 200));

        check("a tap that is held too long is not a click",
                !TouchGestureRules.isTap(1, 1, false, false, false, 5, 5, 400));

        check("a tap that moved too far is not a click",
                !TouchGestureRules.isTap(1, 1, false, false, false, 30, 30, 200));

        check("a finger that has already dragged does not also click",
                !TouchGestureRules.isTap(1, 1, false, true, false, 5, 5, 200));

        // Two fingers, the second one lifting last
        check("two fingers lifting within the wider limits is a click",
                TouchGestureRules.isTap(2, 1, false, false, false, 25, 25, 300));

        check("two fingers are given more room than one",
                TouchGestureRules.slackPx(2) > TouchGestureRules.slackPx(1));

        check("two fingers are given longer than one",
                TouchGestureRules.tapTimeLimitMs(2) > TouchGestureRules.tapTimeLimitMs(1));

        // The finger that lifts last ends the gesture, so the first one to go does not click
        check("a finger lifting while another is still down is not a click",
                !TouchGestureRules.isTap(2, 2, false, false, false, 5, 5, 200));

        // Which button a tap means
        check("a tap with two fingers is the right button",
                TouchGestureRules.isRightButton(2, 1));
        check("the right button is kept when the first finger lifts last",
                TouchGestureRules.isRightButton(2, 0));
        check("a tap with one finger is the left button",
                !TouchGestureRules.isRightButton(1, 0));

        // Tap, then tap again and hold: the second touch drags at once
        check("a touch right behind a tap starts dragging",
                TouchGestureRules.startsDragWithSecondTap(150));
        check("a tap long after the first one does not start dragging",
                !TouchGestureRules.startsDragWithSecondTap(1500));
        check("a touch with no tap before it does not start dragging",
                !TouchGestureRules.startsDragWithSecondTap(Long.MAX_VALUE));

        android.util.Log.i("LayoutSmoke", "trackpad: two-finger tap limit "
                + TouchGestureRules.tapTimeLimitMs(2) + "ms within "
                + (TouchGestureRules.TAP_MOVEMENT_THRESHOLD_PX + TouchGestureRules.slackPx(2))
                + "px, drag after a tap within "
                + TouchGestureRules.SECOND_TAP_DRAG_WINDOW_MS + "ms");
    }


    /**
     * A number typed into a field must not be acted on letter by letter.
     *
     * It used to be: typing 1024 moved the control to 1, then 10, then 102, each of them held
     * inside the layout, so the field fought the person using it.
     */
    private void checkTypingAppliesWhenFinished() {
        UniversalVirtualControllerElement element = firstElementInLayout();

        check("a control is in the layout for the typing check", element != null);

        if (element == null) {
            return;
        }

        editor.onElementTapped(element);

        UniversalEditorPanel panel = editor.getPanelForTesting();
        UniversalEditorPanel.SliderRow xRow = panel.getSliderRow(getString(R.string.editor_label_x));

        check("the drawer has a field for the position", xRow != null);

        if (xRow == null) {
            return;
        }

        int before = element.getConfig().getGeometry().getLeft();

        // Typed one character at a time, as a person does
        xRow.typeForTesting(1);
        check("a half typed number is not acted on",
                element.getConfig().getGeometry().getLeft() == before);

        xRow.typeForTesting(10);
        check("a number typed so far is not acted on",
                element.getConfig().getGeometry().getLeft() == before);

        xRow.typeForTesting(102);
        xRow.typeForTesting(1024);

        check("nothing was acted on while typing",
                element.getConfig().getGeometry().getLeft() == before);
        check("the field remembers what was typed", xRow.getValue() == 1024);
        check("the field knows the entry is unfinished", panel.hasPendingInputs());

        // Finishing the entry applies it
        panel.applyPendingInputs();

        int after = element.getConfig().getGeometry().getLeft();

        android.util.Log.i("LayoutSmoke", "typed 1024: left was " + before
                + ", came out " + after);

        check("finishing the entry applies the number", after == 1024);
        check("the entry is no longer waiting", !panel.hasPendingInputs());

        // An empty or half typed field is not a value
        xRow.typeForTesting(7);
        check("a newly typed number waits again", panel.hasPendingInputs());

        panel.applyPendingInputs();
        editor.applyGeometryForTesting(100, 100, element.getWidth() > 0 ? element.getWidth() : 160,
                element.getHeight() > 0 ? element.getHeight() : 160);
    }


    /**
     * A layout that is made now starts from a clean screen, and the built-in one keeps its
     * built-in arrangement.
     */
    private void checkANewLayoutStartsEmpty() {
        UniversalLayoutStore store = UniversalLayoutStore.getInstance(this);
        UniversalLayoutDefinition created = store.addLayout(store.makeUniqueName("smoke empty"));

        UniversalVirtualController controller = new UniversalVirtualController(
                universalController.getControllerHandler(),
                (FrameLayout) universalController.getLayoutArea(), this, created.getId());

        try {
            controller.refreshLayout();

            android.util.Log.i("LayoutSmoke", "a new layout has " + controller.getElements().size()
                    + " controls");

            check("a new layout has no controls at all", controller.getElements().isEmpty());
        } finally {
            controller.destroy();
            store.deleteLayout(created.getId());
        }

        // The built-in layout still comes with the arrangement the app starts with
        UniversalVirtualController builtIn = new UniversalVirtualController(
                universalController.getControllerHandler(),
                (FrameLayout) universalController.getLayoutArea(), this,
                UniversalLayoutStore.DEFAULT_LAYOUT_ID);

        try {
            builtIn.refreshLayout();

            StringBuilder names = new StringBuilder();
            for (UniversalVirtualControllerElement element : builtIn.getElements()) {
                names.append(element.getElementName()).append("/")
                        .append(element.getElementTypeName()).append(" ");
            }

            android.util.Log.i("LayoutSmoke", "the built-in layout has "
                    + builtIn.getElements().size() + " controls: " + names);

            check("the built-in layout still has its controls",
                    builtIn.getElements().size() > 10);
        } finally {
            builtIn.destroy();
        }
    }

    /**
     * A fresh install starts from the arrangement the app ships with, and the built-in layout
     * cannot be removed or lost.
     */
    private void checkTheShippedArrangementIsPreinstalled() {
        SharedPreferences prefs = getSharedPreferences(
                "OSC_UNIVERSAL_" + UniversalLayoutStore.DEFAULT_LAYOUT_ID, MODE_PRIVATE);

        // Whatever this device has stored is put back afterwards, so the check leaves no trace
        String savedElements = prefs.getString("elements", null);
        int savedWidth = prefs.getInt("savedWidth", 0);
        int savedHeight = prefs.getInt("savedHeight", 0);

        // A fresh install: nothing stored for the built-in layout
        prefs.edit().clear().apply();

        try {
            check("a fresh install has the shipped arrangement put in",
                    UniversalLayoutConfigurationLoader.seedShippedLayout(this));

            String seeded = readStoredElements(UniversalLayoutStore.DEFAULT_LAYOUT_ID);

            check("the shipped arrangement is written into the built-in layout",
                    seeded != null && !seeded.isEmpty());

            UniversalVirtualController controller = new UniversalVirtualController(
                    universalController.getControllerHandler(),
                    (FrameLayout) universalController.getLayoutArea(), this,
                    UniversalLayoutStore.DEFAULT_LAYOUT_ID);

            try {
                controller.refreshLayout();

                int shipped = countOccurrences(seeded, "\"type\"");

                android.util.Log.i("LayoutSmoke", "the shipped arrangement has " + shipped
                        + " controls, the built-in layout shows " + controller.getElements().size());

                check("the built-in layout shows the shipped arrangement",
                        controller.getElements().size() == shipped && shipped > 10);

                // The screen it was arranged on is recorded, so it is fitted to this one
                check("the shipped arrangement says which screen it was arranged on",
                        prefs.getInt("savedWidth", 0) > 0 && prefs.getInt("savedHeight", 0) > 0);
            } finally {
                controller.destroy();
            }
        } finally {
            if (savedElements == null || savedElements.isEmpty()) {
                prefs.edit().clear().apply();
            }
            else {
                UniversalLayoutConfigurationLoader.writeStoredElements(this,
                        UniversalLayoutStore.DEFAULT_LAYOUT_ID, savedElements, savedWidth,
                        savedHeight);
            }
        }

        // And it still cannot be deleted, whatever is in it
        UniversalLayoutStore.getInstance(this).deleteLayout(UniversalLayoutStore.DEFAULT_LAYOUT_ID);

        check("the built-in layout is still there after asking to delete it",
                UniversalLayoutStore.getInstance(this).getLayout(
                        UniversalLayoutStore.DEFAULT_LAYOUT_ID) != null);
    }


    /**
     * The settings screen holds only what is still a setting.
     */
    private void checkTheSettingsScreenIsTidy() {
        java.util.List<String> keys = new java.util.ArrayList<>();

        try {
            org.xmlpull.v1.XmlPullParser parser = getResources().getXml(R.xml.preferences);
            int event;

            while ((event = parser.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (event != org.xmlpull.v1.XmlPullParser.START_TAG) {
                    continue;
                }

                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    if ("key".equals(parser.getAttributeName(i))) {
                        keys.add(parser.getAttributeValue(i));
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("LayoutSmoke", "could not read the settings", e);
        }

        android.util.Log.i("LayoutSmoke", "settings keys: " + keys.size() + " " + keys);

        // Everything that moved to the stream menu or the layouts screen
        String[] gone = new String[]{
                "checkbox_touchscreen_trackpad",
                "checkbox_show_onscreen_controls",
                "checkbox_vibrate_osc",
                "checkbox_only_show_L3R3",
                "checkbox_show_guide_button",
                "seekbar_osc_opacity",
                "checkbox_show_universal_layout",
        };

        for (String key : gone) {
            check("the settings no longer offer " + key, !keys.contains(key));
        }

        // The settings that are still worth having stay
        check("the settings still offer the video options", keys.contains("list_resolution"));
        check("the settings still offer the mouse options",
                keys.contains("checkbox_mouse_nav_buttons"));
        check("the settings still offer the advanced options",
                keys.contains("checkbox_unlock_fps"));
    }

    /**
     * The stream menu has a place for options that are not about a control or a layout.
     */
    private void checkStreamMenuExtras() {
        final boolean[] vibration = new boolean[]{true};
        final int[] sensitivity = new int[]{100};
        final java.util.List<UniversalLayoutDefinition> layouts = new java.util.ArrayList<>();

        FrameLayout area = new FrameLayout(this);
        UniversalStreamDrawer drawer = new UniversalStreamDrawer(this, area,
                new UniversalStreamDrawer.Host() {
                    @Override
                    public void onExitStream() {
                    }

                    @Override
                    public void onShowKeyboard() {
                    }

                    @Override
                    public int getTouchMode() {
                        return com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_TRACKPAD;
                    }

                    @Override
                    public void onTouchModeChanged(int mode) {
                    }

                    @Override
                    public java.util.List<UniversalLayoutDefinition> getLayouts() {
                        return layouts;
                    }

                    @Override
                    public String getSelectedLayoutId() {
                        return "";
                    }

                    @Override
                    public void onLayoutChosen(String layoutId) {
                    }

                    @Override
                    public boolean isOnScreenControlsEnabled() {
                        return true;
                    }

                    @Override
                    public void onOnScreenControlsToggled(boolean enabled) {
                    }

                    @Override
                    public int getLayoutOpacity() {
                        return 100;
                    }

                    @Override
                    public void onLayoutOpacityChanged(int percent) {
                    }

                    @Override
                    public int getSensitivity() {
                        return sensitivity[0];
                    }

                    @Override
                    public void onSensitivityChanged(int percent) {
                        sensitivity[0] = percent;
                    }

                    @Override
                    public boolean isVibrationEnabled() {
                        return vibration[0];
                    }

                    @Override
                    public void onVibrationChanged(boolean enabled) {
                        vibration[0] = enabled;
                    }
                });

        drawer.show();

        java.util.List<String> rows = drawer.getRowTitlesForTesting();

        android.util.Log.i("LayoutSmoke", "menu rows: " + rows);

        check("the stream menu has a row for the extra options",
                containsLabel(rows, getString(R.string.stream_menu_extras)));

        // It sits under everything else, above the way out
        check("the extra options come last",
                indexOfRow(rows, getString(R.string.stream_menu_extras))
                        > indexOfRow(rows, getString(R.string.stream_menu_opacity)));

        check("the extra options unfold",
                drawer.clickRowForTesting(getString(R.string.stream_menu_extras)));

        check("the extra options hold the vibration switch",
                drawer.getRowTitlesForTesting().contains(getString(R.string.stream_menu_vibration)));

        check("the vibration switch can be pressed",
                drawer.clickSwitchInRowForTesting(getString(R.string.stream_menu_vibration)));
        check("the vibration switch turns vibration off", !vibration[0]);

        check("the vibration switch can be pressed again",
                drawer.clickSwitchInRowForTesting(getString(R.string.stream_menu_vibration)));
        check("the vibration switch turns vibration back on", vibration[0]);

        drawer.hide();
    }


    /**
     * The editor offers pass through under the toggle, and the two cannot both be on.
     */
    private void checkEditorPassThroughOrder() {
        final UniversalVirtualController controller = universalController;
        UniversalDigitalButton button = new UniversalDigitalButton(controller, 4343, 0, this);
        com.limelight.binding.input.virtual_controller.universal.config.UniversalButtonConfig config =
                button.getButtonConfigIfAny();

        config.setName("Pass through");
        config.setToggle(false);
        config.setPassThrough(false);

        com.limelight.binding.input.virtual_controller.universal.editor.UniversalEditorPanel panel =
                newPanelForTesting();
        com.limelight.binding.input.virtual_controller.universal.editor.UniversalElementEditorBuilder builder =
                new com.limelight.binding.input.virtual_controller.universal.editor
                        .UniversalElementEditorBuilder(this, panel);

        String toggleLabel = getString(com.limelight.R.string.editor_label_toggle);
        String passThroughLabel = getString(com.limelight.R.string.editor_label_pass_through);

        buildPanelForTesting(builder, panel, button);

        java.util.List<String> boxes = panel.getCheckBoxLabelsForTesting();

        android.util.Log.i("LayoutSmoke", "editor checkboxes: " + boxes);

        int toggleAt = boxes.indexOf(toggleLabel);
        int passThroughAt = boxes.indexOf(passThroughLabel);

        check("the editor offers the toggle", toggleAt >= 0);
        check("the editor offers pass through", passThroughAt >= 0);
        check("pass through sits under the toggle", passThroughAt > toggleAt);

        // Nothing else is in the way: the two are neighbours
        check("pass through follows the toggle directly", passThroughAt == toggleAt + 1);

        check("the toggle is available while pass through is off",
                panel.isCheckBoxEnabledForTesting(toggleLabel));

        // Turning pass through on drops the toggle and puts it out of reach
        check("pass through can be turned on",
                panel.clickCheckBoxForTesting(passThroughLabel));
        check("the control now passes movement on", config.isPassThrough());
        check("pass through is marked as on",
                panel.isCheckBoxCheckedForTesting(passThroughLabel));

        buildPanelForTesting(builder, panel, button);

        check("the toggle is out of reach while pass through is on",
                !panel.isCheckBoxEnabledForTesting(toggleLabel));
        check("the toggle is off while pass through is on",
                !panel.isCheckBoxCheckedForTesting(toggleLabel));

        // ... and a control that had both is left with pass through only
        config.setToggle(true);
        config.setPassThrough(true);
        buildPanelForTesting(builder, panel, button);

        check("pass through wins over a toggle set beforehand", !config.isToggle());

        // Turning pass through off gives the toggle back
        config.setPassThrough(false);
        buildPanelForTesting(builder, panel, button);

        check("the toggle comes back once pass through is off",
                panel.isCheckBoxEnabledForTesting(toggleLabel));
    }

    private com.limelight.binding.input.virtual_controller.universal.editor.UniversalEditorPanel
            newPanelForTesting() {
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        scroll.addView(content);

        return new com.limelight.binding.input.virtual_controller.universal.editor
                .UniversalEditorPanel(this, scroll, content, null);
    }

    private void buildPanelForTesting(
            com.limelight.binding.input.virtual_controller.universal.editor.UniversalElementEditorBuilder builder,
            com.limelight.binding.input.virtual_controller.universal.editor.UniversalEditorPanel panel,
            final UniversalVirtualControllerElement element) {
        panel.clear();

        builder.build(element, new com.limelight.binding.input.virtual_controller.universal.editor
                .UniversalElementEditorBuilder.Callbacks() {
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
    }

    /**
     * The layouts screen lists layouts and nothing else: the switch and the transparency are in
     * the stream menu now.
     */
    private void checkLayoutsScreenIsTidy() {
        check("the layouts screen no longer offers the master switch",
                getResources().getIdentifier("layoutsEnabledSwitch", "id", getPackageName()) == 0);
        check("the layouts screen no longer offers the transparency slider",
                getResources().getIdentifier("layoutsOpacitySlider", "id", getPackageName()) == 0);
        check("the layouts screen no longer labels the transparency",
                getResources().getIdentifier("layoutsOpacityLabel", "id", getPackageName()) == 0);

        // The screen itself still builds and still has its grid
        View screen = getLayoutInflater().inflate(R.layout.activity_universal_layouts, null);

        check("the layouts screen still builds", screen != null);
        check("the layouts screen still has its grid",
                screen != null && screen.findViewById(R.id.layoutsGrid) != null);
    }


    /**
     * The triggers are named and placed like the shoulder buttons, since they are part of the
     * same pad.
     */
    private void checkTriggerEventNames() {
        java.util.List<com.limelight.binding.input.virtual_controller.universal.action.InputEvent> catalog =
                com.limelight.binding.input.virtual_controller.universal.action.InputEventNames
                        .padCatalog();

        java.util.List<String> names = new java.util.ArrayList<>();
        for (com.limelight.binding.input.virtual_controller.universal.action.InputEvent event : catalog) {
            names.add(com.limelight.binding.input.virtual_controller.universal.action.InputEventNames
                    .describe(event));
        }

        android.util.Log.i("LayoutSmoke", "pad events: " + names);

        String lb = "Pad: LB";
        String rb = "Pad: RB";
        String lt = "Pad: LT";
        String rt = "Pad: RT";

        check("the shoulder buttons are offered", names.contains(lb) && names.contains(rb));
        check("the triggers are named like the other pad buttons", names.contains(lt) && names.contains(rt));

        int lbAt = names.indexOf(lb);
        int rbAt = names.indexOf(rb);
        int ltAt = names.indexOf(lt);
        int rtAt = names.indexOf(rt);

        check("the triggers sit next to the shoulder buttons",
                ltAt > rbAt && rtAt > rbAt && Math.abs(ltAt - rbAt) <= 2 && Math.abs(rtAt - ltAt) == 1);

        // Nothing is named after a trigger in the old style any more
        boolean anyOldName = false;
        for (String name : names) {
            if (name.contains("trigger (LT)") || name.contains("trigger (RT)")) {
                anyOldName = true;
                break;
            }
        }

        check("no event is named the old way", !anyOldName);

        // The left trigger of the built-in arrangement says what it sends
        com.limelight.binding.input.virtual_controller.universal.action.InputEvent leftTrigger =
                new com.limelight.binding.input.virtual_controller.universal.action.InputEvent(
                        com.limelight.binding.input.virtual_controller.universal.action.InputEvent.KIND_TRIGGER,
                        com.limelight.binding.input.virtual_controller.universal.action.InputEvent.TRIGGER_LEFT);

        check("a trigger event says which trigger it is",
                "Pad: LT".equals(com.limelight.binding.input.virtual_controller.universal.action
                        .InputEventNames.describe(leftTrigger)));
    }


    /**
     * The stick reports upwards as positive, which is what the host expects and what the
     * touch modes had wrong.
     */
    private void checkStickAxisSigns() {
        UniversalVirtualController controller = universalController;
        UniversalVirtualControllerElement rs = null;

        for (UniversalVirtualControllerElement element : controller.getElements()) {
            if (element.getElementId() == UniversalVirtualControllerElement.EID_RS) {
                rs = element;
                break;
            }
        }

        check("the layout has a right stick", rs != null);

        if (rs == null) {
            return;
        }

        // The stick needs a size before it has a centre or a range to move in
        rs.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        rs.layout(0, 0, 300, 300);

        UniversalVirtualController.ControllerMode original = controller.getControllerMode();
        controller.setControllerMode(UniversalVirtualController.ControllerMode.Active);

        try {
            // Press at the middle, then push the finger upwards
            rs.onElementTouchEvent(motionEventFor(rs, MotionEvent.ACTION_DOWN, 0.5f, 0.5f));
            rs.onElementTouchEvent(motionEventFor(rs, MotionEvent.ACTION_MOVE, 0.5f, 0.15f));

            short up = controller.getControllerInputContext().rightStickY;

            rs.onElementTouchEvent(motionEventFor(rs, MotionEvent.ACTION_MOVE, 0.5f, 0.85f));
            short down = controller.getControllerInputContext().rightStickY;

            rs.onElementTouchEvent(motionEventFor(rs, MotionEvent.ACTION_UP, 0.5f, 0.5f));

            android.util.Log.i("LayoutSmoke", "stick axes: up=" + up + " down=" + down);

            check("the on-screen right stick reports up as positive", up > 0);
            check("the on-screen right stick reports down as negative", down < 0);

            // The thumb has to be drawn on the side the finger is on, or the stick looks
            // inverted even when the value it sends is right
            rs.onElementTouchEvent(motionEventFor(rs, MotionEvent.ACTION_DOWN, 0.5f, 0.5f));
            rs.onElementTouchEvent(motionEventFor(rs, MotionEvent.ACTION_MOVE, 0.5f, 0.15f));

            float thumbUp = ((UniversalAnalogStick) rs).getThumbYForTesting();

            rs.onElementTouchEvent(motionEventFor(rs, MotionEvent.ACTION_MOVE, 0.5f, 0.85f));

            float thumbDown = ((UniversalAnalogStick) rs).getThumbYForTesting();
            rs.onElementTouchEvent(motionEventFor(rs, MotionEvent.ACTION_UP, 0.5f, 0.5f));

            android.util.Log.i("LayoutSmoke", "stick thumb: up=" + thumbUp + " down=" + thumbDown);

            check("the thumb is drawn above the middle when the finger is above it",
                    thumbUp < rs.getHeight() / 2f);
            check("the thumb is drawn below the middle when the finger is below it",
                    thumbDown > rs.getHeight() / 2f);

            // A stick can be told to send the opposite direction, which is what a game that
            // reads the sign the other way needs. The thumb keeps following the finger.
            com.limelight.binding.input.virtual_controller.universal.config.UniversalStickConfig
                    stickConfig = ((UniversalAnalogStick) rs).getStickConfig();

            stickConfig.setInvertY(true);

            rs.onElementTouchEvent(motionEventFor(rs, MotionEvent.ACTION_DOWN, 0.5f, 0.5f));
            rs.onElementTouchEvent(motionEventFor(rs, MotionEvent.ACTION_MOVE, 0.5f, 0.15f));

            short flippedUp = controller.getControllerInputContext().rightStickY;
            float flippedThumb = ((UniversalAnalogStick) rs).getThumbYForTesting();

            rs.onElementTouchEvent(motionEventFor(rs, MotionEvent.ACTION_UP, 0.5f, 0.5f));
            stickConfig.setInvertY(false);

            check("a flipped stick sends the opposite direction", flippedUp < 0);
            check("a flipped stick still draws the thumb under the finger",
                    flippedThumb < rs.getHeight() / 2f);

            // And the setting survives being saved, through the whole element rather than the
            // options on their own
            String before;
            String flipped;

            try {
                before = rs.getConfig().toJson().toString();

                stickConfig.setInvertY(true);
                flipped = rs.getConfig().toJson().toString();
            } catch (org.json.JSONException e) {
                before = "";
                flipped = "";
            } finally {
                stickConfig.setInvertY(false);
            }

            check("a stick that is not flipped saves without the flip",
                    !before.contains("\"invertY\":true"));
            check("the flip is saved with the stick", flipped.contains("\"invertY\":true"));
        } finally {
            rs.releaseConfiguredActions();
            controller.setControllerMode(original);
            controller.getControllerInputContext().rightStickX = 0;
            controller.getControllerInputContext().rightStickY = 0;
        }
    }


    /**
     * The sensitivity row appears only where it means something, and what it reports is what the
     * stream is told.
     */
    private void checkSensitivityRow() {
        int[] modes = new int[]{
                com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_OFF,
                com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_TRACKPAD,
                com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_TRACKPAD_NO_CLICKS,
                com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_TOUCHSCREEN,
                com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_TOUCH_EVENTS,
                com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_RIGHT_STICK,
                com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_LEFT_STICK,
        };

        String label = getString(R.string.stream_menu_sensitivity);

        for (int mode : modes) {
            final int[] sensitivity = new int[]{100};
            final int[] chosenMode = new int[]{mode};

            FrameLayout area = new FrameLayout(this);
            UniversalStreamDrawer drawer = new UniversalStreamDrawer(this, area,
                    new UniversalStreamDrawer.Host() {
                        @Override
                        public void onExitStream() {
                        }

                        @Override
                        public void onShowKeyboard() {
                        }

                        @Override
                        public int getTouchMode() {
                            return chosenMode[0];
                        }

                        @Override
                        public void onTouchModeChanged(int newMode) {
                            chosenMode[0] = newMode;
                        }

                        @Override
                        public java.util.List<UniversalLayoutDefinition> getLayouts() {
                            return new java.util.ArrayList<>();
                        }

                        @Override
                        public String getSelectedLayoutId() {
                            return "";
                        }

                        @Override
                        public void onLayoutChosen(String layoutId) {
                        }

                        @Override
                        public boolean isOnScreenControlsEnabled() {
                            return true;
                        }

                        @Override
                        public void onOnScreenControlsToggled(boolean enabled) {
                        }

                        @Override
                        public int getLayoutOpacity() {
                            return 100;
                        }

                        @Override
                        public void onLayoutOpacityChanged(int percent) {
                        }

                        @Override
                        public int getSensitivity() {
                            return sensitivity[0];
                        }

                        @Override
                        public void onSensitivityChanged(int percent) {
                            sensitivity[0] = percent;
                        }

                        @Override
                        public boolean isVibrationEnabled() {
                            return true;
                        }

                        @Override
                        public void onVibrationChanged(boolean enabled) {
                        }
                    });

            drawer.show();

            boolean shown = containsLabel(drawer.getRowTitlesForTesting(), label);
            boolean wanted = mode != com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_OFF
                    && mode != com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_TOUCH_EVENTS;

            check("the sensitivity row is offered for touch mode " + mode, shown == wanted);

            if (shown) {
                drawer.getView().measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY));
                drawer.getView().layout(0, 0, 600, 1800);

                check("the sensitivity slider can be dragged, touch mode " + mode,
                        drawer.dragSensitivityToForTesting(150));
                android.util.Log.i("LayoutSmoke", "sensitivity for mode " + mode + ": "
                        + sensitivity[0]);

                // A drag lands on a pixel, so the value it reports is near the one asked for
                check("the sensitivity is reported to the stream, touch mode " + mode,
                        Math.abs(sensitivity[0] - 150) <= 3);
            }

            drawer.hide();
        }
    }


    /**
     * A finger holding an on-screen control is not counted by the touch input, so one finger on
     * the background still looks around instead of turning into a two finger scroll.
     */
    private void checkOnlyTheStreamsOwnFingersAreCounted() {
        com.limelight.binding.input.touch.TouchPointerSlots slots =
                new com.limelight.binding.input.touch.TouchPointerSlots(2);

        check("nothing is followed at first", slots.ownedCount() == 0);

        // A finger lands on the background
        check("a finger on the background is followed", slots.record(0, 3) == 1);

        // ... and a second one on an on-screen control, which never reaches the stream
        check("a finger on a control is not counted", slots.ownedCount() == 1);

        // A second finger on the background is a real second finger
        check("two fingers on the background are two", slots.record(1, 7) == 2);

        check("the first finger is still the first", slots.idAt(0) == 3);
        check("the second finger is the second", slots.idAt(1) == 7);

        // The first one lifts, leaving one finger, which must not read as two
        check("lifting one finger leaves one", slots.forget(0) == 1);

        check("the remaining finger is found by its id", slots.idAt(1) == 7);
        check("a slot that is done holds no finger", slots.idAt(0) == -1);

        slots.clear();

        check("a cancelled gesture follows nothing", slots.ownedCount() == 0);
    }


    /**
     * A stick the touch input is pushing survives the on-screen layout sending its own state,
     * which is what makes looking around work while another control is held or a stick is
     * pushed.
     */
    private void checkTheTouchStickSurvivesTheLayout() {
        com.limelight.binding.input.OscStickState sticks =
                new com.limelight.binding.input.OscStickState();

        // Nothing is pushing anything, so the layout speaks for every stick
        check("a stick nothing is pushing comes from the layout",
                sticks.mergeRightY((short) 700) == 700);

        // The touch input pushes the right stick
        sticks.setTouchRight((short) 100, (short) 2000);

        check("a pushed stick is taken from the touch input",
                sticks.mergeRightY((short) 0) == 2000);

        // The layout reporting its own state, with the stick in the middle, does not move it
        check("a stick pushed by the touch input survives a layout report",
                sticks.mergeRightY((short) 0) == 2000);

        // The other stick is still the layout's
        check("the other stick still comes from the layout",
                sticks.mergeLeftY((short) -400) == -400);

        // Letting go hands the stick back to the layout
        sticks.setTouchRight((short) 0, (short) 0);

        check("a released stick gives the layout its stick back",
                sticks.mergeRightY((short) 500) == 500);
        check("a released stick is no longer the touch input's",
                !sticks.isTouchRightActive());
    }


    /**
     * The two layouts this build brings are in the list, carry the keys they are meant to, and
     * are not added twice.
     */
    private void checkShippedExtraLayouts() {
        UniversalLayoutStore store = UniversalLayoutStore.getInstance(this);

        UniversalLayoutDefinition keyboard = null;
        UniversalLayoutDefinition shooter = null;

        for (UniversalLayoutDefinition layout : store.getLayouts()) {
            if ("PC Keyboard".equals(layout.getName())) {
                keyboard = layout;
            }
            else if ("Default (kbd+mouse)".equals(layout.getName())) {
                shooter = layout;
            }
        }

        check("the PC keyboard layout is offered", keyboard != null);
        check("the key and mouse layout is offered", shooter != null);

        if (keyboard == null || shooter == null) {
            return;
        }

        java.util.List<String> keys = elementNamesIn(keyboard.getId());
        java.util.List<String> shooterKeys = elementNamesIn(shooter.getId());

        android.util.Log.i("LayoutSmoke", "keyboard layout has " + keys.size() + " controls: " + keys);
        android.util.Log.i("LayoutSmoke", "keys layout has " + shooterKeys.size()
                + " controls: " + shooterKeys);

        // The keyboard: the keys of a PC keyboard, and the mouse under the thumb
        check("the keyboard has a space bar", keys.contains("Space"));
        check("the keyboard has its letters", keys.containsAll(
                java.util.Arrays.asList("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")));
        check("the keyboard has its function row",
                keys.contains("F1") && keys.contains("F12"));
        check("the keyboard has the grave key", keys.contains("`"));
        check("the keyboard has the mouse buttons",
                keys.contains("LMB") && keys.contains("RMB") && keys.contains("MMB"));
        check("the keyboard has its modifiers",
                keys.contains("Shift") && keys.contains("Ctrl") && keys.contains("Win"));

        // The key and mouse layout: the keys a shooter expects, and the mouse buttons
        check("the key and mouse layout has its keys", shooterKeys.containsAll(
                java.util.Arrays.asList("Space", "E", "F", "C", "Q", "R")));
        check("the key and mouse layout shoots with the mouse",
                shooterKeys.contains("MB1") && shooterKeys.contains("MB2"));
        check("the key and mouse layout has Esc and Enter",
                shooterKeys.contains("Esc") && shooterKeys.contains("Enter"));
        check("the key and mouse layout keeps a stick", shooterKeys.contains("Left stick"));

        // Seeding again changes nothing
        int before = store.getLayouts().size();
        com.limelight.binding.input.virtual_controller.universal.UniversalShippedLayouts
                .seedIfNeeded(this);

        check("the layouts are only added once", store.getLayouts().size() == before);
    }

    private java.util.List<String> elementNamesIn(String layoutId) {
        java.util.List<String> names = new java.util.ArrayList<>();

        try {
            org.json.JSONArray elements = new org.json.JSONArray(readStoredElements(layoutId));

            for (int i = 0; i < elements.length(); i++) {
                names.add(elements.getJSONObject(i).optString("name", ""));
            }
        } catch (org.json.JSONException e) {
            android.util.Log.e("LayoutSmoke", "could not read the layout " + layoutId, e);
        }

        return names;
    }


    /**
     * A wide, low key such as the space bar must not be drawn in giant letters, and every key of
     * one size carries the same size of letter.
     */
    private void checkLabelSizes() {
        UniversalVirtualController controller = universalController;

        UniversalDigitalButton square = new UniversalDigitalButton(controller, 5001, 0, this);
        UniversalDigitalButton wide = new UniversalDigitalButton(controller, 5002, 0, this);

        square.getConfig().setName("Space");
        wide.getConfig().setName("Space");

        // One is as tall as it is wide, the other is the space bar
        square.measure(View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY));
        square.layout(0, 0, 120, 120);

        wide.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY));
        wide.layout(0, 0, 600, 120);

        // A short label never has to be shrunk, so it shows the size the control asks for
        float squareSize = square.labelSizeForTesting("W");
        float wideSize = wide.labelSizeForTesting("Space");

        android.util.Log.i("LayoutSmoke", "label size: square=" + squareSize + " wide=" + wideSize);

        // The space bar is six times as wide, and its letters are the same size as the ones on
        // a square key of the same height
        check("a wide key is not drawn in larger letters than a square one",
                wideSize <= squareSize + 0.5f);
        check("a wide key still has readable letters", wideSize > 10f);
        check("the letters fit inside the key", wideSize <= 120 * 0.62f);

        // And a long label is shrunk rather than run past the edge
        float longLabel = square.labelSizeForTesting("some long name that will not fit");

        check("a long name is shrunk to fit", longLabel < squareSize);

        // A label that is too wide for its control is shrunk as well, which is why the space bar
        // on a square key would come out smaller than the key's own size
        check("a label too wide for its control is shrunk",
                square.labelSizeForTesting("Space") < squareSize);
    }

    /**
     * A layout file with an absurd number of controls is refused instead of being built.
     */
    private void checkImportIsBounded() {
        StringBuilder elements = new StringBuilder("[");

        for (int i = 0; i < com.limelight.binding.input.virtual_controller.universal
                .UniversalLayoutExchange.MAX_ELEMENTS + 5; i++) {
            if (i > 0) {
                elements.append(",");
            }

            elements.append("{\"type\":\"button\",\"elementId\":").append(1000 + i)
                    .append(",\"name\":\"b\",\"geometry\":{\"left\":0,\"top\":0,"
                            + "\"width\":10,\"height\":10}}");
        }

        elements.append("]");

        String file = "{\"format\":\"moonlight-universal-layout\",\"version\":1,"
                + "\"name\":\"Hostile\",\"screen\":{\"width\":1920,\"height\":1080},"
                + "\"elements\":" + elements + "}";

        boolean refused;

        try {
            com.limelight.binding.input.virtual_controller.universal.UniversalLayoutExchange
                    .fromJson(file);
            refused = false;
        } catch (org.json.JSONException e) {
            refused = true;
        }

        check("a layout file with too many controls is refused", refused);
    }


    /**
     * A sequence with a delay between its steps plays the second step while the control is still
     * held. The player runs on a looper of its own here, so the check does not have to wait on
     * the main thread it is running on.
     */
    private void checkDelayedSequence() {
        final java.util.List<String> sent =
                java.util.Collections.synchronizedList(new java.util.ArrayList<String>());

        final com.limelight.binding.input.virtual_controller.universal.action.ActionSequence sequence =
                new com.limelight.binding.input.virtual_controller.universal.action.ActionSequence();

        sequence.addStep(new com.limelight.binding.input.virtual_controller.universal.action.ActionStep(
                        new com.limelight.binding.input.virtual_controller.universal.action.InputEvent(
                                com.limelight.binding.input.virtual_controller.universal.action.InputEvent.KIND_KEYBOARD,
                                65))
                .withDelay(150));

        sequence.addStep(new com.limelight.binding.input.virtual_controller.universal.action.ActionStep(
                new com.limelight.binding.input.virtual_controller.universal.action.InputEvent(
                        com.limelight.binding.input.virtual_controller.universal.action.InputEvent.KIND_KEYBOARD,
                        66)));

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Looper.prepare();

                com.limelight.binding.input.virtual_controller.universal.action.ActionSequencePlayer player =
                        new com.limelight.binding.input.virtual_controller.universal.action.ActionSequencePlayer(
                                new com.limelight.binding.input.virtual_controller.universal.action
                                        .ActionSequencePlayer.HostInput() {
                                    @Override
                                    public void setGamepadButtons(int flagsToAdd, int flagsToRemove) {
                                    }

                                    @Override
                                    public void setTrigger(int trigger, int value) {
                                    }

                                    @Override
                                    public void sendMouseButton(int button, boolean down) {
                                    }

                                    @Override
                                    public void sendKeyboardKey(short keyCode, boolean down) {
                                        sent.add("key " + keyCode + " down=" + down);
                                    }
                                });

                player.press(sequence);

                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        android.os.Looper.myLooper().quitSafely();
                    }
                }, 400);

                android.os.Looper.loop();
            }
        });

        worker.start();

        try {
            worker.join(3000);
        } catch (InterruptedException e) {
            check("a delayed sequence can be waited for", false);
            return;
        }

        android.util.Log.i("LayoutSmoke", "delayed sequence sent: " + sent);

        check("a sequence sends its first step", sent.contains("key 65 down=true"));
        check("a sequence with a delay sends its second step", sent.contains("key 66 down=true"));

        // A tap ends the press at once, and the rest of the sequence still plays
        final java.util.List<String> tapped =
                java.util.Collections.synchronizedList(new java.util.ArrayList<String>());

        Thread tapWorker = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Looper.prepare();

                com.limelight.binding.input.virtual_controller.universal.action.ActionSequencePlayer player =
                        new com.limelight.binding.input.virtual_controller.universal.action.ActionSequencePlayer(
                                new com.limelight.binding.input.virtual_controller.universal.action
                                        .ActionSequencePlayer.HostInput() {
                                    @Override
                                    public void setGamepadButtons(int flagsToAdd, int flagsToRemove) {
                                    }

                                    @Override
                                    public void setTrigger(int trigger, int value) {
                                    }

                                    @Override
                                    public void sendMouseButton(int button, boolean down) {
                                    }

                                    @Override
                                    public void sendKeyboardKey(short keyCode, boolean down) {
                                        tapped.add("key " + keyCode + " down=" + down);
                                    }
                                });

                player.press(sequence);
                player.finish();

                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        android.os.Looper.myLooper().quitSafely();
                    }
                }, 400);

                android.os.Looper.loop();
            }
        });

        tapWorker.start();

        try {
            tapWorker.join(3000);
        } catch (InterruptedException e) {
            check("a tapped sequence can be waited for", false);
            return;
        }

        android.util.Log.i("LayoutSmoke", "tapped sequence sent: " + tapped);

        check("a tap lets go of the first step at once", tapped.contains("key 65 down=false"));
        check("a tap still plays the step after the delay", tapped.contains("key 66 down=true"));
        check("a tap lets go of the last step", tapped.contains("key 66 down=false"));
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

    /**
     * The copy the editor worked on is removed, so a run leaves nothing behind.
     */
    private void clearEditorCopy() {
        if (editorLayoutId == null) {
            return;
        }

        UniversalLayoutConfigurationLoader.resetLayout(this, editorLayoutId);
        UniversalLayoutStore.getInstance(this).deleteLayout(editorLayoutId);
        editorLayoutId = null;
    }

    private void report() {
        Log.i(TAG, "RESULT " + (failures == 0 ? "PASSED" : "FAILED")
                + " checks=" + checks + " failures=" + failures);

        clearEditorCopy();

        // Starting this activity with --ez showDrawer true leaves the editor on screen with a
        // control's drawer open, so the drawer itself can be looked at. Touch cannot be
        // injected on every device, so this is how the editor's own look is inspected.
        // Starting this activity with --ez showStreamMenu true leaves the stream menu on screen,
        // so its look can be inspected. A real stream needs a host to connect to.
        if (getIntent().getBooleanExtra("showStreamMenu", false)) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    showStreamMenuForInspection();
                }
            }, 1500);
        }

        // Opens the editor on a control with pass through turned on, in memory only, so the
        // rows can be looked at on a real screen
        if (getIntent().getBooleanExtra("showPassThroughOnScreen", false)) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    UniversalVirtualControllerElement element = firstElementInLayout();

                    // A button is what these rows belong to, so the first one is picked
                    for (UniversalVirtualControllerElement candidate : universalController.getElements()) {
                        if (candidate.getButtonConfigIfAny() != null) {
                            element = candidate;
                            break;
                        }
                    }

                    if (element == null) {
                        return;
                    }

                    element.getConfig().setPassThrough(true);

                    if (element.getButtonConfigIfAny() != null) {
                        element.getButtonConfigIfAny().setToggle(true);
                    }

                    editor.onElementTapped(element);
                }
            }, 2500);
        }

        if (getIntent().getBooleanExtra("showPassThroughPanel", false)) {
            showPassThroughPanelForInspection();
        }

        if (getIntent().getBooleanExtra("showDrawer", false)) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    editor.onElementTapped(firstElementInLayout());

                    // The actions are the last rows of the drawer, so it is scrolled to them
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            View panel = editor.getPanelForTesting().getView();

                            if (panel instanceof ScrollView) {
                                ((ScrollView) panel).fullScroll(View.FOCUS_DOWN);
                            }

                            finish();
                        }
                    }, 1500);
                }
            }, 2000);
        }
    }

    /**
     * Draws the part of the editor that holds the toggle and pass through, with pass through
     * turned on, so the state of the toggle can be looked at without a stream.
     */
    private void showPassThroughPanelForInspection() {
        UniversalDigitalButton button = new UniversalDigitalButton(universalController, 4545, 0, this);
        com.limelight.binding.input.virtual_controller.universal.config.UniversalButtonConfig config =
                button.getButtonConfigIfAny();

        config.setName("Fire");
        config.setToggle(true);
        config.setPassThrough(true);

        com.limelight.binding.input.virtual_controller.universal.editor.UniversalEditorPanel panel =
                newPanelForTesting();
        com.limelight.binding.input.virtual_controller.universal.editor.UniversalElementEditorBuilder builder =
                new com.limelight.binding.input.virtual_controller.universal.editor
                        .UniversalElementEditorBuilder(this, panel);

        buildPanelForTesting(builder, panel, button);

        String passThroughLabel = getString(com.limelight.R.string.editor_label_pass_through);
        String toggleLabel = getString(com.limelight.R.string.editor_label_toggle);

        android.util.Log.i("LayoutSmoke", "picture: passThrough=" + config.isPassThrough()
                + " toggle=" + config.isToggle()
                + " boxOn=" + panel.isCheckBoxCheckedForTesting(passThroughLabel)
                + " boxOff=" + panel.isCheckBoxCheckedForTesting(toggleLabel));

        View view = panel.getView();

        // A platform checkbox reveals its mark with an animation, and an unattached view never
        // runs one, so the drawables are jumped to their finished state before drawing
        settleDrawables(view);

        int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.86f);

        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, width, Math.max(1, view.getMeasuredHeight()));

        int height = Math.min(view.getMeasuredHeight(),
                getResources().getDisplayMetrics().heightPixels);

        android.graphics.Bitmap picture = android.graphics.Bitmap.createBitmap(width, height,
                android.graphics.Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(picture);
        canvas.drawColor(0xFF101418);
        view.draw(canvas);

        saveBitmapForInspection(picture, "editor-pass-through.png");
        picture.recycle();
    }

    /**
     * Moves every drawable in the tree to the state it would come to rest in, which is what
     * makes a picture of an unattached view match what a screen shows.
     */
    private void settleDrawables(View view) {
        view.jumpDrawablesToCurrentState();

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;

            for (int i = 0; i < group.getChildCount(); i++) {
                settleDrawables(group.getChildAt(i));
            }
        }
    }

    /**
     * Puts the stream menu on screen with a stand-in stream behind it.
     */
    private void showStreamMenuForInspection() {
        java.util.List<UniversalLayoutDefinition> demo = new java.util.ArrayList<>();
        demo.add(new UniversalLayoutDefinition("one", "Phone", "", 0));
        demo.add(new UniversalLayoutDefinition("two", "Tablet", "", 0));

        View content = findViewById(android.R.id.content);

        if (!(content instanceof FrameLayout)) {
            return;
        }

        UniversalStreamDrawer drawer = new UniversalStreamDrawer(this, (FrameLayout) content,
                new UniversalStreamDrawer.Host() {
                    @Override
                    public void onExitStream() {
                    }

                    @Override
                    public void onShowKeyboard() {
                    }

                    @Override
                    public int getTouchMode() {
                        return com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_TOUCHSCREEN;
                    }

                    @Override
                    public void onTouchModeChanged(int mode) {
                    }

                    @Override
                    public java.util.List<UniversalLayoutDefinition> getLayouts() {
                        return demo;
                    }

                    @Override
                    public String getSelectedLayoutId() {
                        return "one";
                    }

                    @Override
                    public void onLayoutChosen(String layoutId) {
                    }

                    @Override
                    public boolean isOnScreenControlsEnabled() {
                        return true;
                    }

                    @Override
                    public void onOnScreenControlsToggled(boolean enabled) {
                    }

                    @Override
                    public int getLayoutOpacity() {
                        return 60;
                    }

                    @Override
                    public void onLayoutOpacityChanged(int percent) {
                    }

                    @Override
                    public int getSensitivity() {
                        return 100;
                    }

                    @Override
                    public void onSensitivityChanged(int percent) {
                    }

                    @Override
                    public boolean isVibrationEnabled() {
                        return true;
                    }

                    @Override
                    public void onVibrationChanged(boolean enabled) {
                    }
                });

        drawer.settleForTesting();

        // The touch row is left open, so the choices are in the picture as well
        drawer.clickRowForTesting(getString(R.string.stream_menu_touch));

        // ... and the last row as well, so the switch inside it is visible
        drawer.clickRowForTesting(getString(R.string.stream_menu_extras));

        // Drawn into a picture rather than left on the screen: what is on the screen belongs to
        // whichever activity is in front, and this one may already have stepped back
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;

        View view = drawer.getView();
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);

        android.graphics.Bitmap picture = android.graphics.Bitmap.createBitmap(width, height,
                android.graphics.Bitmap.Config.ARGB_8888);

        // A dark backdrop stands in for the game behind the menu
        Canvas canvas = new Canvas(picture);
        canvas.drawColor(0xFF202020);
        view.draw(canvas);

        saveBitmapForInspection(picture, "stream-menu.png");
        picture.recycle();
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
