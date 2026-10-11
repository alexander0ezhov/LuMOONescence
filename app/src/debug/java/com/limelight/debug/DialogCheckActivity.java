package com.limelight.debug;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.limelight.binding.input.virtual_controller.universal.UniversalLayoutEditorActivity;
import com.limelight.binding.input.virtual_controller.universal.UniversalVirtualController;
import com.limelight.binding.input.virtual_controller.universal.UniversalVirtualControllerElement;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Opens the layout editor and checks the dialogs it offers.
 *
 * The device forbids injecting input events, so a dialog cannot be opened with a finger from
 * adb. This activity asks the editor for the same dialogs through the same code paths the UI
 * uses, and then inspects what is on screen: that a dialog appeared, which buttons it offers,
 * and that closing it leaves the editor in a consistent state.
 *
 * It lives in the debug source set, so it is not part of a release build.
 */
public class DialogCheckActivity extends Activity {
    public static final String TAG = "LayoutSmoke";

    private static final long EDITOR_SETTLE_MS = 2500;
    private static final long DIALOG_SETTLE_MS = 1200;

    private int failures;
    private int checks;

    private UniversalLayoutEditorActivity editor;
    private UniversalVirtualController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(this, UniversalLayoutEditorActivity.class);
        intent.putExtra(UniversalLayoutEditorActivity.EXTRA_LAYOUT_ID, "default");
        startActivity(intent);

        post(new Runnable() {
            @Override
            public void run() {
                try {
                    start();
                } catch (Throwable t) {
                    Log.e(TAG, "the dialog checks failed", t);
                    failures++;
                    report();
                }
            }
        }, EDITOR_SETTLE_MS);
    }

    private void post(Runnable runnable, long delayMs) {
        new Handler(Looper.getMainLooper()).postDelayed(runnable, delayMs);
    }

    private void start() {
        editor = UniversalLayoutEditorActivity.getLastCreatedInstance();
        check("the editor is open", editor != null);

        if (editor == null) {
            report();
            return;
        }

        Object controllerField = getField(editor, "controller");
        if (!(controllerField instanceof UniversalVirtualController)) {
            check("the editor has a controller", false);
            report();
            return;
        }

        controller = (UniversalVirtualController) controllerField;

        checkElementMenu();
    }

    /**
     * Holds a control, which is what opens its context menu, and then inspects that menu.
     */
    private void checkElementMenu() {
        List<UniversalVirtualControllerElement> elements = controller.getElements();
        check("there is a control to hold", !elements.isEmpty());

        if (elements.isEmpty()) {
            report();
            return;
        }

        final UniversalVirtualControllerElement target = elements.get(0);

        // The hold is reported to the editor exactly as a real hold reports it
        controller.setElementInteractionListener(
                new UniversalVirtualController.ElementInteractionListener() {
                    @Override
                    public void onElementTapped(UniversalVirtualControllerElement element) {
                        editor.onElementTapped(element);
                    }

                    @Override
                    public boolean onElementLongPressed(UniversalVirtualControllerElement element) {
                        return editor.onElementLongPressed(element);
                    }

                    @Override
                    public void onElementGeometrySettled(UniversalVirtualControllerElement element) {
                    }

                    @Override
                    public void onLayoutAdjustedToFit() {
                    }
                });

        controller.notifyElementLongPressed(target);

        post(new Runnable() {
            @Override
            public void run() {
                AlertDialog menu = editor.getVisibleDialogForTesting();
                check("a hold opens a menu", menu != null);

                if (menu != null) {
                    String[] labels = listItemLabels(menu);
                    check("the menu offers at least four operations", labels.length >= 4);
                    check("the menu offers duplicating", contains(labels, "duplicate"));
                    check("the menu offers copying the size", contains(labels, "copy"));
                    check("the menu offers editing", contains(labels, "edit"));
                    check("the menu offers deleting", contains(labels, "delete"));

                    menu.dismiss();
                }

                post(new Runnable() {
                    @Override
                    public void run() {
                        checkGeneralMenu();
                    }
                }, DIALOG_SETTLE_MS);
            }
        }, DIALOG_SETTLE_MS);
    }

    /**
     * Checks that the editor is on screen full screen, then goes on to the question about
     * unsaved changes.
     */
    private void checkGeneralMenu() {
        check("the element menu is closed", editor.getVisibleDialogForTesting() == null);

        View root = editor.getEditorRootView();
        check("the editor has a root view", root != null);
        check("the editor covers the whole screen",
                root != null && root.getWidth() == getResources().getDisplayMetrics().widthPixels);

        checkUnsavedQuestion();
    }

    /**
     * Makes the layout dirty and asks to leave, which is what the back button does, then
     * inspects the question that must appear and answers it.
     */
    private void checkUnsavedQuestion() {
        check("no menu is left open", editor.getVisibleDialogForTesting() == null);

        controller.markConfigurationDirty();
        editor.requestExitForTesting();

        post(new Runnable() {
            @Override
            public void run() {
                AlertDialog dialog = editor.getVisibleDialogForTesting();
                check("leaving with unsaved changes asks a question", dialog != null);
                check("the editor knows a question is on screen",
                        editor.isSaveDialogShowingForTesting());

                if (dialog != null) {
                    check("the question offers saving",
                            dialog.getButton(DialogInterface.BUTTON_POSITIVE) != null);
                    check("the question offers saving as",
                            dialog.getButton(DialogInterface.BUTTON_NEUTRAL) != null);
                    check("the question offers not saving",
                            dialog.getButton(DialogInterface.BUTTON_NEGATIVE) != null);

                    ButtonText(dialog);
                }

                if (dialog != null) {
                    dialog.dismiss();
                }

                post(new Runnable() {
                    @Override
                    public void run() {
                        check("closing the question clears its state",
                                !editor.isSaveDialogShowingForTesting());
                        report();
                    }
                }, 300);
            }
        }, DIALOG_SETTLE_MS);
    }

    private void ButtonText(AlertDialog dialog) {
        android.widget.Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (positive != null) {
            check("the first answer is about saving",
                    String.valueOf(positive.getText()).toLowerCase().contains("save"));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String[] listItemLabels(AlertDialog dialog) {
        ListView list = dialog.getListView();
        if (list == null || list.getAdapter() == null) {
            return new String[0];
        }

        String[] labels = new String[list.getAdapter().getCount()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = String.valueOf(list.getAdapter().getItem(i)).toLowerCase();
        }

        return labels;
    }

    private static boolean contains(String[] labels, String needle) {
        for (String label : labels) {
            if (label.contains(needle)) {
                return true;
            }
        }

        return false;
    }

    private void report() {
        Log.i(TAG, "DIALOGS " + (failures == 0 ? "PASSED" : "FAILED")
                + " checks=" + checks + " failures=" + failures);
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
}
