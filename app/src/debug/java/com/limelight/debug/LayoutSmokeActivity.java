package com.limelight.debug;

import android.app.Activity;
import android.content.Context;
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

import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutConfigurationLoader;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutDefinition;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutEditorActivity;
import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutStore;
import com.limelight.binding.input.virtual_controller.universal.UniversalVirtualController;
import com.limelight.binding.input.virtual_controller.universal.UniversalVirtualControllerElement;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalGeometry;
import com.limelight.binding.input.virtual_controller.universal.editor.UniversalEditorPanel;

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

                report();
            }
        }, LONG_PRESS_HOLD_MS);
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
