package com.limelight.binding.input.virtual_controller.universal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.utils.UiHelper;

/**
 * Edits one universal layout: shows the real controls over a plain background and lets
 * the user move and resize them.
 *
 * The layout is built by the same code that draws it over the stream, so what the user
 * arranges here is exactly what appears while playing.
 */
public class UniversalLayoutEditorActivity extends Activity {
    public static final String EXTRA_LAYOUT_ID = "layoutId";

    private UniversalLayoutStore layoutStore;
    private UniversalLayoutDefinition layout;
    private UniversalVirtualController controller;

    private FrameLayout overlay;
    private Button moveButton;
    private TextView hintText;

    private boolean editing;

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

        // Compensates drawing under the status bar, so the toolbar is not positioned
        // under it. Every normal activity in this app does this.
        UiHelper.notifyNewRootView(this);

        overlay = findViewById(R.id.layoutEditorOverlay);
        moveButton = findViewById(R.id.layoutEditorMoveButton);
        hintText = findViewById(R.id.layoutEditorHint);

        ((TextView) findViewById(R.id.layoutEditorTitle)).setText(layout.getName());

        findViewById(R.id.layoutEditorBackButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        moveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setEditing(!editing);
            }
        });

        findViewById(R.id.layoutEditorResetButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showResetDialog();
            }
        });

        findViewById(R.id.layoutEditorImageButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(UniversalLayoutEditorActivity.this,
                        UniversalLayoutImagePickerActivity.class);
                intent.putExtra(UniversalLayoutImagePickerActivity.EXTRA_LAYOUT_ID, layout.getId());
                startActivity(intent);
            }
        });

        createController();

        // Opening a layout to edit it means the user wants to arrange it, so start in
        // editing mode instead of making them press the button first.
        setEditing(true);
    }

    private void updateTitle() {
        layout = layoutStore.getLayout(layout.getId());
        if (layout != null) {
            ((TextView) findViewById(R.id.layoutEditorTitle)).setText(layout.getName());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // The artwork of this layout may have changed in the picker
        updateTitle();
    }

    private void createController() {
        // A null controller handler turns this into a preview: the controls respond to
        // touch exactly as they do in a stream, but nothing is sent to a host.
        controller = new UniversalVirtualController(null, overlay, this, layout.getId());
        controller.setConfigurationButtonVisible(false);
        controller.refreshLayout();
    }

    private void setEditing(boolean editing) {
        this.editing = editing;

        controller.setControllerMode(editing
                ? UniversalVirtualController.ControllerMode.MoveButtons
                : UniversalVirtualController.ControllerMode.Active);

        moveButton.setText(editing ? R.string.title_finish_editing : R.string.title_edit_controls);
        hintText.setText(editing ? R.string.msg_editor_hint_editing : R.string.msg_editor_hint_idle);
    }

    private void showResetDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.title_reset_controls)
                .setMessage(R.string.dialog_text_reset_layout_controls)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        resetControls();
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void resetControls() {
        // Forget the saved geometry, then rebuild the layout so the default arrangement
        // comes back. The controller is reused, which keeps its element bookkeeping in
        // step with what is inside the overlay.
        deleteSharedPreferences(layout.getPreferenceFileName());

        controller.refreshLayout();

        setEditing(editing);

        Toast.makeText(this, R.string.toast_layout_controls_reset, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Save whenever the editor leaves the foreground, so an arrangement is never
        // lost because the user left with the home button or a notification.
        controller.saveLayoutConfiguration();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (controller != null) {
            controller.destroy();
        }
    }
}
