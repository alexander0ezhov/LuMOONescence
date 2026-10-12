package com.limelight.binding.input.virtual_controller.universal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.limelight.R;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalElementConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalGeometry;
import com.limelight.binding.input.virtual_controller.universal.editor.UniversalEditorPanel;
import com.limelight.binding.input.virtual_controller.universal.editor.UniversalElementEditorBuilder;

/**
 * The layout editor.
 *
 * It is built like a tablet tool: the arrangement of controls takes the middle of the
 * screen with the editing panel docked on the left, so a control can never be hidden
 * behind the panel. The controls are the real ones, created by the same controller the
 * stream uses, so what is arranged here is exactly what appears while playing.
 */
public class UniversalLayoutEditorActivity extends Activity
        implements UniversalVirtualController.ElementInteractionListener {
    public static final String EXTRA_LAYOUT_ID = "layoutId";

    /**
     * The editor that was opened last. The debug smoke test uses this to reach the editor it
     * started, because the device forbids injecting the touches that would otherwise select
     * a control.
     */
    private static UniversalLayoutEditorActivity lastCreatedInstance;

    public static UniversalLayoutEditorActivity getLastCreatedInstance() {
        return lastCreatedInstance;
    }

    /**
     * Returns the control the panel on the left currently describes.
     */
    public UniversalVirtualControllerElement getSelectedElementForTesting() {
        return selectedElement;
    }

    /**
     * Notified whenever this editor shows a dialog.
     *
     * The debug smoke test uses this to look at the dialogs the editor offers. A dialog is a
     * window of its own, so it cannot be found by walking this activity's view tree.
     */
    public interface DialogListener {
        void onDialogShown(AlertDialog dialog);
    }

    public void setDialogListener(DialogListener listener) {
        this.dialogListener = listener;
    }

    private DialogListener dialogListener;
    private AlertDialog lastShownDialog;

    /**
     * Shows a dialog and tells the listener about it, so the one place that puts a dialog on
     * screen is also the one place that reports it.
     */
    private void showDialog(AlertDialog dialog) {
        lastShownDialog = dialog;

        if (dialogListener != null) {
            dialogListener.onDialogShown(dialog);
        }

        dialog.show();
    }

    /**
     * The dialog this editor showed last, while it is still on screen.
     */
    public AlertDialog getVisibleDialogForTesting() {
        if (lastShownDialog != null && lastShownDialog.isShowing()) {
            return lastShownDialog;
        }

        return null;
    }

    /**
     * Asks the editor to leave, which is what the back button does. Used by the debug smoke
     * test, which cannot inject the back press itself.
     */
    public void requestExitForTesting() {
        requestExit();
    }

    /**
     * True while the question about unsaved changes is on screen.
     */
    public boolean isSaveDialogShowingForTesting() {
        return saveDialogShowing;
    }

    /**
     * Where the editing drawer was left, so it comes back where the user put it.
     */
    private static final String KEY_PANEL_POSITION = "panelPosition";

    /**
     * Where the floating settings button was left, so it comes back where the user put it.
     */
    private static final String KEY_SETTINGS_BUTTON_POSITION = "settingsButtonPosition";

    /**
     * An element smaller than this would be unusable, and the editor never creates one
     * that small.
     */
    private static final int MIN_ELEMENT_SIZE_DP = 32;

    private UniversalLayoutStore layoutStore;
    private UniversalLayoutDefinition layout;
    private UniversalVirtualController controller;

    private FrameLayout overlay;
    private View panelContainer;

    private UniversalEditorPanel panel;
    private UniversalElementEditorBuilder editorBuilder;

    private UniversalVirtualControllerElement selectedElement;

    /**
     * Size copied from one control and waiting to be pasted onto another.
     */
    private UniversalGeometry copiedSize;

    private boolean saveDialogShowing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String layoutId = getIntent().getStringExtra(EXTRA_LAYOUT_ID);
        if (layoutId == null) {
            finish();
            return;
        }

        layoutStore = UniversalLayoutStore.getInstance(this);
        layout = layoutStore.getLayout(layoutId);
        if (layout == null) {
            finish();
            return;
        }

        setContentView(R.layout.activity_universal_layout_editor);

        lastCreatedInstance = this;

        // The editor is full screen like the stream, so a control sits exactly where it will
        // sit while playing and nothing shifts it around.
        enterFullScreen();

        overlay = findViewById(R.id.layoutEditorOverlay);
        panelContainer = findViewById(R.id.layoutEditorPanel);

        // The drawer and its header are in the layout itself. The panel only fills the
        // scrolling area below the header, so the header survives a rebuild of the rows.
        panel = new UniversalEditorPanel(this,
                findViewById(R.id.layoutEditorPanelScroll),
                findViewById(R.id.layoutEditorPanelContent),
                panelContainer.findViewById(R.id.layoutEditorPanelHeader));
        panel.applyScreenBounds(panelContainer);

        panel.setOnCloseRequested(new Runnable() {
            @Override
            public void run() {
                // Tapping the header puts the drawer away, which frees the whole screen
                panelContainer.setVisibility(View.GONE);
            }
        });

        panel.setOnDragFinished(new Runnable() {
            @Override
            public void run() {
                savePanelPosition();
            }
        });

        editorBuilder = new UniversalElementEditorBuilder(this, panel);
        editorBuilder.setOnRebuildRequested(new Runnable() {
            @Override
            public void run() {
                // Adding or removing a step or an event changes how many rows the panel
                // has, so the panel is rebuilt from the element's new state.
                refreshPanel();
            }
        });

        // The header carries the type of the control being edited and the way to stop
        // editing it. Everything that acts on the whole layout is asked for by holding a
        // control, or from the settings button that floats over the arrangement.
        findViewById(R.id.layoutEditorPanelClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectElement(null);
            }
        });

        setUpSettingsButton();

        registerBackCallback();

        createController();

        // The drawer only exists while a control is being edited, so it starts hidden
        setPanelVisible(false);

        refreshPanel();

        // The panel's position is restored once the layout has been measured, because it is
        // clamped against the screen size
        panelContainer.post(new Runnable() {
            @Override
            public void run() {
                restorePanelPosition();
            }
        });

        setUpEmptySpaceHandling();
    }

    /**
     * Makes a tap on an empty part of the arrangement put the drawer away and clear the
     * selection.
     *
     * The overlay only receives a touch when no control took it, which is exactly the case
     * that should close the drawer.
     */
    private void setUpEmptySpaceHandling() {
        // The arrangement itself is looked at: it only receives a touch when no control took
        // it, which is exactly the "empty space" case that should close the drawer. The
        // drawer is a sibling over it, so a touch on the drawer never reaches this.
        overlay.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                android.util.Log.i("LayoutSmoke", "overlay touch action=" + event.getActionMasked()
                        + " at " + event.getX() + "," + event.getY()
                        + " selected=" + (selectedElement != null));

                if (event.getActionMasked() != MotionEvent.ACTION_UP) {
                    return false;
                }

                // A hold opened the control menu, so it must not also close the drawer
                boolean wasHold = longPressHandled;
                longPressHandled = false;

                if (!wasHold) {
                    selectElement(null);
                }

                return true;
            }
        });
    }

    private boolean longPressHandled;

    /**
     * Shows or hides the drawer. It is only on screen while a control is being edited.
     */
    private void setPanelVisible(boolean visible) {
        panelContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Hides the system bars the same way the stream does, so the editor covers the whole
     * screen and a control can be placed right up to the edge.
     */
    @SuppressLint("InlinedApi")
    private void enterFullScreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);

        // Using the decor view here rather than android.R.id.content is deliberate: the
        // content view is what notifyNewRootView() pads, and this activity must not be padded
        getWindow().getDecorView().setSystemUiVisibility(hideSystemUiFlags());

        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(
                new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        // The bars come back after a gesture or a dialog; put them away again
                        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                            rehideSystemUi(2000);
                        }
                    }
                });
    }

    @SuppressLint("InlinedApi")
    private static int hideSystemUiFlags() {
        // In multi-window mode the layout flags must be dropped, otherwise the activity would
        // draw underneath the system UI of the window it shares
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindow()) {
            return View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        }

        return View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    }

    private static boolean isInMultiWindow() {
        return multiWindowState;
    }

    /**
     * Multi-window state, kept because the flag helper is static and this is only used to
     * decide how the bars are hidden.
     */
    private static boolean multiWindowState;

    private void rehideSystemUi(int delayMs) {
        Handler handler = getWindow().getDecorView().getHandler();
        if (handler != null) {
            handler.removeCallbacks(rehideSystemUiRunnable);
            handler.postDelayed(rehideSystemUiRunnable, delayMs);
        }
    }

    private final Runnable rehideSystemUiRunnable = new Runnable() {
        @Override
        public void run() {
            getWindow().getDecorView().setSystemUiVisibility(hideSystemUiFlags());
        }
    };

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);

        multiWindowState = isInMultiWindowMode;
        getWindow().getDecorView().setSystemUiVisibility(hideSystemUiFlags());
    }

    /**
     * The general settings button: a tap opens the menu for the whole layout, a drag moves it
     * out of the way of what is being arranged.
     */
    private void setUpSettingsButton() {
        final View button = findViewById(R.id.layoutEditorSettingsButton);
        if (button == null) {
            return;
        }

        button.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private float startX;
            private float startY;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startX = view.getX();
                        startY = view.getY();
                        moved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - downRawX;
                        float deltaY = event.getRawY() - downRawY;

                        // A few pixels of slop, so a tap is not read as a drag
                        if (Math.abs(deltaX) > 8 || Math.abs(deltaY) > 8) {
                            moved = true;
                        }

                        if (moved) {
                            moveWithinRoot(view, startX + deltaX, startY + deltaY);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            saveSettingsButtonPosition();
                        }
                        else {
                            showGeneralMenu();
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        if (moved) {
                            saveSettingsButtonPosition();
                        }
                        return true;

                    default:
                        return false;
                }
            }
        });

        // The position is known only once the layout has been measured
        button.post(new Runnable() {
            @Override
            public void run() {
                restoreSettingsButtonPosition();
            }
        });
    }

    /**
     * Keeps a floating view inside the editor, so it cannot be dragged off screen.
     */
    private void moveWithinRoot(View view, float x, float y) {
        View root = findViewById(R.id.layoutEditorFrame);
        if (root == null) {
            return;
        }

        float maxX = Math.max(0, root.getWidth() - view.getWidth());
        float maxY = Math.max(0, root.getHeight() - view.getHeight());

        view.setX(Math.max(0, Math.min(x, maxX)));
        view.setY(Math.max(0, Math.min(y, maxY)));
    }

    private void saveSettingsButtonPosition() {
        View button = findViewById(R.id.layoutEditorSettingsButton);
        if (button == null) {
            return;
        }

        getEditorPreferences().edit()
                .putString(KEY_SETTINGS_BUTTON_POSITION,
                        button.getX() + ":" + button.getY())
                .apply();
    }

    private void restoreSettingsButtonPosition() {
        View button = findViewById(R.id.layoutEditorSettingsButton);
        if (button == null) {
            return;
        }

        View root = findViewById(R.id.layoutEditorFrame);

        String saved = getEditorPreferences().getString(KEY_SETTINGS_BUTTON_POSITION, null);
        if (saved == null) {
            // Park it against the bottom left corner, where it is out of the way of a layout
            // that is usually arranged from the left
            moveWithinRoot(button, 0, root == null ? 0 : root.getHeight() - button.getHeight());
            return;
        }

        int separator = saved.indexOf(':');
        if (separator <= 0) {
            return;
        }

        try {
            moveWithinRoot(button, Float.parseFloat(saved.substring(0, separator)),
                    Float.parseFloat(saved.substring(separator + 1)));
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    private void createController() {
        // A null controller handler turns this into a preview: the controls respond to
        // touch exactly as they do in a stream, but nothing is sent to a host.
        controller = new UniversalVirtualController(null, overlay, this, layout.getId());
        controller.setConfigurationButtonVisible(false);
        controller.setElementInteractionListener(this);
        controller.setControllerMode(UniversalVirtualController.ControllerMode.MoveButtons);
        controller.refreshLayout();
    }

    private void savePanelPosition() {
        getEditorPreferences().edit()
                .putString(KEY_PANEL_POSITION, panelContainer.getX() + ":" + panelContainer.getY())
                .apply();
    }

    private void restorePanelPosition() {
        String saved = getEditorPreferences().getString(KEY_PANEL_POSITION, null);
        if (saved == null) {
            // Out of the way of nothing in particular: the top left corner, where the drawer
            // covers the least of what is usually arranged
            panelContainer.setX(0);
            panelContainer.setY(0);
            return;
        }

        int separator = saved.indexOf(':');
        if (separator <= 0) {
            return;
        }

        try {
            panelContainer.setX(Float.parseFloat(saved.substring(0, separator)));
            panelContainer.setY(Float.parseFloat(saved.substring(separator + 1)));
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    /**
     * Picks up a rename made elsewhere, which is what happens when the user comes back from
     * a screen that changed the layout.
     */
    private void updateTitle() {
        layout = layoutStore.getLayout(layout.getId());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // The artwork or the name of this layout may have changed elsewhere
        updateTitle();
    }

    // ---------------------------------------------------------------- selection

    @Override
    public void onElementTapped(UniversalVirtualControllerElement element) {
        selectElement(element);
    }

    @Override
    public boolean onElementLongPressed(UniversalVirtualControllerElement element) {
        // Recorded so the release that follows a hold does not also close the drawer
        longPressHandled = true;

        selectElement(element);
        showElementMenu(element);
        return true;
    }

    /**
     * Returns the view the editor is built on, so a test can look for what is on screen
     * without having to guess at the editor's own resource identifiers.
     */
    public View getEditorRootView() {
        return findViewById(R.id.layoutEditorFrame);
    }

    /**
     * The name of the kind of control, shown in the drawer header.
     */
    private String controlTypeName(UniversalVirtualControllerElement element) {
        String type = element.getElementTypeName();

        if (UniversalElementTypes.ELEMENT_STICK.equals(type)) {
            return getString(R.string.control_type_stick);
        }

        if (UniversalElementTypes.ELEMENT_DPAD.equals(type)) {
            return getString(R.string.control_type_dpad);
        }

        if (UniversalElementTypes.ELEMENT_TRIGGER.equals(type)) {
            return getString(R.string.control_type_trigger);
        }

        if (UniversalElementTypes.ELEMENT_BUTTON.equals(type)) {
            return getString(R.string.control_type_button);
        }

        return getString(R.string.control_type_element);
    }

    private void selectElement(UniversalVirtualControllerElement element) {
        if (selectedElement == element) {
            return;
        }

        if (selectedElement != null) {
            selectedElement.setSelected(false);
        }

        selectedElement = element;

        if (selectedElement != null) {
            selectedElement.setSelected(true);
        }

        // The drawer belongs to the control being edited: no control, no drawer
        setPanelVisible(selectedElement != null);

        refreshPanel();
    }

    /**
     * Rebuilds the editing panel for the selected control. Nothing is shown when no control
     * is selected: the drawer is there to describe a control, and an explanation of how to
     * use the editor would only cover the arrangement.
     */
    private void refreshPanel() {
        panel.clear();

        if (selectedElement == null) {
            return;
        }

        // The header says what kind of control is being edited, which is what tells the
        // drawer apart from the layout behind it
        ((TextView) panelContainer.findViewById(R.id.layoutEditorPanelTitle))
                .setText(controlTypeName(selectedElement));

        editorBuilder.build(selectedElement, new UniversalElementEditorBuilder.Callbacks() {
            @Override
            public void onElementChanged() {
                markDirty();
                selectedElement.invalidate();
            }

            @Override
            public void onGeometryChanged(int left, int top, int width, int height) {
                applyGeometry(left, top, width, height);
            }

            @Override
            public void onNameChanged(String name) {
                selectedElement.setElementName(name);
                markDirty();
            }
        });
    }

    /**
     * Applies one change the way a slider reports it, for the debug smoke test.
     */
    public void applyGeometryForTesting(int left, int top, int width, int height) {
        if (selectedElement == null) {
            return;
        }

        applyGeometry(left, top, width, height);
    }

    /**
     * The size the selected control currently has on screen.
     */
    public UniversalGeometry getSelectedGeometryForTesting() {
        return selectedElement == null ? null : UniversalGeometry.of(selectedElement);
    }

    /**
     * The drawer and its builder, so the debug smoke test can rebuild the rows the way the
     * editor does and check what a fresh set of rows reports.
     */
    public UniversalEditorPanel getPanelForTesting() {
        return panel;
    }

    public UniversalElementEditorBuilder getEditorBuilderForTesting() {
        return editorBuilder;
    }

    /**
     * Applies a position or size change.
     *
     * All four values are read from the sliders that are on screen right now rather than
     * taken from the caller's arguments. A slider reports its change together with the values
     * its rows were built with, so applying those would put back the values of every other
     * slider as they were when the drawer was built, undoing whatever the user changed
     * before. Reading the live sliders means a change moves only what the user moved.
     */
    private void applyGeometry(int left, int top, int width, int height) {
        UniversalGeometry geometry = selectedElement.getConfig().getGeometry();
        UniversalGeometry onScreen = UniversalGeometry.of(selectedElement);

        geometry.setLeft(readSlider(R.string.editor_label_x, onScreen.getLeft()));
        geometry.setTop(readSlider(R.string.editor_label_y, onScreen.getTop()));
        geometry.setWidth(readSlider(R.string.editor_label_width, onScreen.getWidth()));
        geometry.setHeight(readSlider(R.string.editor_label_height, onScreen.getHeight()));

        geometry.applyTo(selectedElement);
        panel.showGeometry(geometry);

        markDirty();
    }

    /**
     * The value a slider currently shows, falling back to what is on screen while the slider
     * is not part of the drawer.
     */
    private int readSlider(int labelId, int fallback) {
        UniversalEditorPanel.SliderRow row = panel.getSliderRow(getString(labelId));
        return row == null ? fallback : row.getValue();
    }

    private void markDirty() {
        // The controller owns the flag, so a change it makes on its own (bringing a layout
        // arranged on another screen back into view) counts as unsaved work too.
        controller.markConfigurationDirty();
    }

    // ---------------------------------------------------------------- element menu

    private void showElementMenu(final UniversalVirtualControllerElement element) {
        final java.util.List<String> entries = new java.util.ArrayList<>();
        final java.util.List<Runnable> actions = new java.util.ArrayList<>();

        entries.add(getString(R.string.menu_duplicate));
        actions.add(new Runnable() {
            @Override
            public void run() {
                UniversalVirtualControllerElement copy = controller.duplicateElement(element);
                if (copy != null) {
                    markDirty();
                    selectElement(copy);
                }
            }
        });

        entries.add(getString(R.string.menu_copy_size));
        actions.add(new Runnable() {
            @Override
            public void run() {
                copiedSize = UniversalGeometry.of(element).copy();
                Toast.makeText(UniversalLayoutEditorActivity.this,
                        R.string.menu_copy_size, Toast.LENGTH_SHORT).show();
            }
        });

        // "Edit" simply opens this control in the panel on the left, which is what the
        // selected control is already shown in; it is offered so the way to edit a control
        // is discoverable from the control itself.
        entries.add(getString(R.string.menu_edit));
        actions.add(new Runnable() {
            @Override
            public void run() {
                selectElement(element);
            }
        });

        if (copiedSize != null) {
            entries.add(getString(R.string.menu_paste_size));
            actions.add(new Runnable() {
                @Override
                public void run() {
                    UniversalGeometry geometry = copiedSize.copy();
                    element.getConfig().setGeometry(geometry);
                    geometry.applyTo(element);

                    markDirty();
                    refreshPanel();
                }
            });
        }

        entries.add(getString(R.string.menu_delete));
        actions.add(new Runnable() {
            @Override
            public void run() {
                showDeleteElementDialog(element);
            }
        });

        showDialog(new AlertDialog.Builder(this)
                .setTitle(element.getElementName())
                .setItems(entries.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        actions.get(which).run();
                    }
                })
                .create());
    }

    private void showDeleteElementDialog(final UniversalVirtualControllerElement element) {
        showDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.menu_delete)
                .setMessage(getString(R.string.dialog_text_delete_layout, element.getElementName()))
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (selectedElement == element) {
                            selectedElement = null;
                        }

                        controller.removeElement(element);
                        markDirty();
                        refreshPanel();
                    }
                })
                .setNegativeButton(R.string.no, null)
                .create());
    }

    // ---------------------------------------------------------------- general menu

    private SharedPreferences getEditorPreferences() {
        return getSharedPreferences("OSC_UNIVERSAL_EDITOR", MODE_PRIVATE);
    }

    public void showGeneralMenu() {
        String[] entries = new String[]{
                getString(R.string.menu_add_button),
                getString(R.string.menu_add_stick),
                getString(R.string.title_reset_controls),
                getString(R.string.menu_save),
                getString(R.string.menu_save_as),
                getString(R.string.menu_exit),
        };

        showDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.title_layout_settings)
                .setItems(entries, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                addElement(UniversalElementTypes.ELEMENT_BUTTON);
                                break;

                            case 1:
                                addElement(UniversalElementTypes.ELEMENT_STICK);
                                break;

                            case 2:
                                // The reset used to be a button of its own; the menu is where
                                // it lives now
                                showResetDialog();
                                break;

                            case 3:
                                saveLayout();
                                break;

                            case 4:
                                showSaveAsDialog();
                                break;

                            default:
                                requestExit();
                                break;
                        }
                    }
                })
                .create());
    }

    /**
     * Adds a control in the middle of the arrangement, where the user can see it and drag
     * it into place.
     */
    private void addElement(String type) {
        int minSize = (int) (MIN_ELEMENT_SIZE_DP * getResources().getDisplayMetrics().density);

        // The overlay may not have been measured yet when the menu is used, so fall back to
        // the screen size to still place the control somewhere sensible.
        int availableWidth = overlay.getWidth() > 0
                ? overlay.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int availableHeight = overlay.getHeight() > 0
                ? overlay.getHeight() : getResources().getDisplayMetrics().heightPixels;

        int width = Math.max(minSize, availableWidth / 8);
        int height = Math.max(minSize, availableHeight / 8);

        int x = Math.max(0, (availableWidth - width) / 2);
        int y = Math.max(0, (availableHeight - height) / 2);

        UniversalVirtualControllerElement element =
                controller.addUserElement(type, x, y, width, height);
        if (element == null) {
            return;
        }

        markDirty();
        selectElement(element);

        Toast.makeText(this, R.string.toast_element_added, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- saving

    private void saveLayout() {
        controller.saveLayoutConfiguration();

        Toast.makeText(this, R.string.toast_layout_saved, Toast.LENGTH_SHORT).show();
    }

    private void showSaveAsDialog() {
        final EditText nameInput = new EditText(this);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nameInput.setHint(R.string.hint_layout_name_new);

        // The field starts with the current name so it can be adjusted. Nothing else is put
        // in front of it: whatever is in the field becomes the name of the new layout.
        nameInput.setText(layout.getName());
        nameInput.setSelection(nameInput.getText().length());

        showDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_save_layout_as)
                .setView(nameInput)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = nameInput.getText().toString().trim();
                        if (name.isEmpty()) {
                            return;
                        }

                        saveLayoutAs(name);
                    }
                })
                .setNegativeButton(R.string.no, null)
                .create());
    }

    /**
     * Saves the arrangement that is on screen as a new layout, leaving the original
     * untouched, and continues editing the copy.
     */
    private void saveLayoutAs(String name) {
        UniversalLayoutDefinition newLayout =
                layoutStore.addLayout(layoutStore.makeUniqueName(name));

        // The arrangement currently on screen belongs to the original layout, so it is
        // written under the new layout's file. Nothing was saved for the new layout yet,
        // which is why the elements are written directly, together with the area they were
        // arranged in.
        UniversalLayoutConfigurationLoader.saveProfile(controller, this, newLayout.getId(),
                overlay.getWidth(), overlay.getHeight());

        layout = newLayout;
        layoutStore.setSelectedLayoutId(newLayout.getId());
        controller.clearLayoutDirty();

        updateTitle();
        selectElement(null);

        Toast.makeText(this, getString(R.string.toast_layout_saved_as, newLayout.getName()),
                Toast.LENGTH_SHORT).show();
    }

    private void showResetDialog() {
        showDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.title_reset_controls)
                .setMessage(R.string.dialog_text_reset_layout_controls)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        resetControls();
                    }
                })
                .setNegativeButton(R.string.no, null)
                .create());
    }

    private void resetControls() {
        // Forget everything that was saved for this layout, then rebuild it so the
        // built-in arrangement comes back. The controller is reused, which keeps its
        // element bookkeeping in step with what is inside the overlay.
        UniversalLayoutConfigurationLoader.resetLayout(this, layout.getId());

        selectElement(null);
        controller.refreshLayout();

        markDirty();

        Toast.makeText(this, R.string.toast_layout_controls_reset, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- leaving

    /**
     * Leaves the editor when the system back gesture is used.
     *
     * This is what the "Exit" entry of the settings menu does as well, so both ways out ask
     * about unsaved changes.
     *
     * NOTE: from Android 13 the back gesture is delivered through the registered callback
     * below instead of here, because this activity asks for the newer dispatch by declaring
     * enableOnBackInvokedCallback in the manifest.
     */
    @Override
    public void onBackPressed() {
        requestExit();
    }

    /**
     * Registers the back gesture handler for Android 13 and newer.
     */
    private void registerBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        backCallback = new OnBackInvokedCallback() {
            @Override
            public void onBackInvoked() {
                requestExit();
            }
        };

        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
    }

    private OnBackInvokedCallback backCallback;

    /**
     * Invokes the back handling that the system would invoke, so the debug smoke test can
     * check it without injecting a key event.
     *
     * @return true when the newer back dispatch is in use, so the callback is what answers
     */
    public boolean invokeBackForTesting() {
        if (backCallback == null) {
            return false;
        }

        backCallback.onBackInvoked();
        return true;
    }

    /**
     * Asks about unsaved changes before leaving. Asking again while the question is on
     * screen does nothing, so a second press cannot dismiss it by accident.
     */
    private void requestExit() {
        if (saveDialogShowing) {
            return;
        }

        if (!controller.isLayoutDirty()) {
            finish();
            return;
        }

        saveDialogShowing = true;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_save_changes)
                .setMessage(R.string.dialog_text_save_changes)
                .setPositiveButton(R.string.menu_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saveDialogShowing = false;
                        saveLayout();
                        finish();
                    }
                })
                .setNeutralButton(R.string.menu_save_as, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saveDialogShowing = false;
                        showSaveAsDialog();
                    }
                })
                .setNegativeButton(R.string.menu_dont_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saveDialogShowing = false;
                        finish();
                    }
                })
                .create();

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                // Back closed the question, so the editor stays open
                saveDialogShowing = false;
            }
        });

        showDialog(dialog);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Save whenever the editor leaves the foreground, so an arrangement is never lost
        // because the user left with the home button or a notification.
        if (controller.isLayoutDirty()) {
            controller.saveLayoutConfiguration();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (controller != null) {
            controller.destroy();
        }
    }

    @Override
    public void onElementGeometrySettled(UniversalVirtualControllerElement element) {
        // A drag changed the position or the size, so the sliders are brought back in step
        if (element == selectedElement) {
            markDirty();
            refreshPanel();
        }
    }

    @Override
    public void onLayoutAdjustedToFit() {
        // The controls were arranged for a different screen size, so they were scaled to fit.
        // The user is told, because the result keeps the arrangement but not the proportions
        // and resetting is the way to get a layout made for this screen.
        Toast.makeText(this, R.string.toast_layout_adjusted_to_fit, Toast.LENGTH_LONG).show();
    }
}
