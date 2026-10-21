package com.limelight.binding.input.virtual_controller.universal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.UiHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists the universal layouts, lets the user pick the one used while streaming, and
 * creates, edits and deletes them.
 */
public class UniversalLayoutsActivity extends Activity {
    private UniversalLayoutStore layoutStore;

    private GridView layoutsGrid;
    private Switch enabledSwitch;
    private TextView emptyText;
    private LayoutGridAdapter adapter;

    private boolean updatingSwitch;

    private SeekBar opacitySlider;
    private TextView opacityLabel;
    private boolean updatingOpacity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_universal_layouts);

        // Compensates drawing under the status bar, so the toolbar is not positioned
        // under it. Every normal activity in this app does this.
        UiHelper.notifyNewRootView(this);

        layoutStore = UniversalLayoutStore.getInstance(this);

        findViewById(R.id.layoutsImportButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                importLayout();
            }
        });

        layoutsGrid = findViewById(R.id.layoutsGrid);
        enabledSwitch = findViewById(R.id.layoutsEnabledSwitch);
        emptyText = findViewById(R.id.layoutsEmptyText);
        opacitySlider = findViewById(R.id.layoutsOpacitySlider);
        opacityLabel = findViewById(R.id.layoutsOpacityLabel);

        adapter = new LayoutGridAdapter();
        layoutsGrid.setAdapter(adapter);
        layoutsGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showLayoutMenu(adapter.getItem(position));
            }
        });

        // Keep the last row of cards above the navigation bar
        UiHelper.applyStatusBarPadding(layoutsGrid);

        findViewById(R.id.layoutsBackButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.layoutsAddButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddLayoutDialog();
            }
        });

        enabledSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                if (updatingSwitch) {
                    return;
                }

                setUniversalLayoutEnabled(isChecked);
            }
        });

        setUpOpacitySlider();
    }

    /**
     * The transparency of the layout, as one slider for all of it.
     */
    private void setUpOpacitySlider() {
        opacitySlider.setMax(100 - PreferenceConfiguration.UNIVERSAL_LAYOUT_OPACITY_MIN);

        opacitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = PreferenceConfiguration.UNIVERSAL_LAYOUT_OPACITY_MIN + progress;
                showOpacity(percent);

                if (fromUser && !updatingOpacity) {
                    setLayoutOpacity(percent);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void showOpacity(int percent) {
        opacityLabel.setText(getString(R.string.title_layout_opacity, percent));
    }

    private int getLayoutOpacity() {
        return getPreferences().getInt(
                PreferenceConfiguration.UNIVERSAL_LAYOUT_OPACITY_PREF_STRING,
                PreferenceConfiguration.UNIVERSAL_LAYOUT_OPACITY_DEFAULT);
    }

    private void setLayoutOpacity(int percent) {
        getPreferences().edit()
                .putInt(PreferenceConfiguration.UNIVERSAL_LAYOUT_OPACITY_PREF_STRING, percent)
                .apply();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // The editor may have renamed a layout, changed its artwork or moved its controls
        refresh();
    }

    private SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    /**
     * The one switch for the whole feature, in the same place the stream menu writes it.
     */
    private boolean isUniversalLayoutEnabled() {
        return PreferenceConfiguration.isUniversalLayoutEnabled(this);
    }

    private void setUniversalLayoutEnabled(boolean enabled) {
        PreferenceConfiguration.setUniversalLayoutEnabled(this, enabled);
    }

    private void refresh() {
        updatingSwitch = true;
        enabledSwitch.setChecked(isUniversalLayoutEnabled());
        updatingSwitch = false;

        int percent = getLayoutOpacity();

        updatingOpacity = true;
        opacitySlider.setProgress(percent - PreferenceConfiguration.UNIVERSAL_LAYOUT_OPACITY_MIN);
        updatingOpacity = false;
        showOpacity(percent);

        List<UniversalLayoutDefinition> layouts = layoutStore.getLayouts();
        adapter.setLayouts(layouts);

        emptyText.setVisibility(layouts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddLayoutDialog() {
        final EditText nameInput = new EditText(this);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nameInput.setHint(R.string.hint_layout_name);

        new AlertDialog.Builder(this)
                .setTitle(R.string.title_add_layout)
                .setView(nameInput)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = nameInput.getText().toString().trim();
                        if (name.isEmpty()) {
                            name = getString(R.string.title_add_layout);
                        }

                        UniversalLayoutDefinition layout =
                                layoutStore.addLayout(layoutStore.makeUniqueName(name));

                        // Creating a layout is only useful if the user can arrange it, so
                        // go straight to the editor.
                        openEditor(layout);
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void showRenameDialog(final UniversalLayoutDefinition layout) {
        final EditText nameInput = new EditText(this);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nameInput.setText(layout.getName());
        nameInput.setSelection(nameInput.getText().length());

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_layout_name)
                .setView(nameInput)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = nameInput.getText().toString().trim();
                        if (name.isEmpty()) {
                            return;
                        }

                        layout.setName(layoutStore.makeUniqueName(name));
                        layoutStore.updateLayout(layout);
                        refresh();
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void showDeleteDialog(final UniversalLayoutDefinition layout) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_delete_control, layout.getName()))
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        layoutStore.deleteLayout(layout.getId());
                        refresh();
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void showLayoutMenu(final UniversalLayoutDefinition layout) {
        final boolean isSelected = layout.getId().equals(layoutStore.getSelectedLayoutId());

        final List<CharSequence> entries = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        entries.add(getString(R.string.menu_edit_layout));
        actions.add(new Runnable() {
            @Override
            public void run() {
                openEditor(layout);
            }
        });

        entries.add(getString(R.string.menu_choose_image));
        actions.add(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(UniversalLayoutsActivity.this,
                        UniversalLayoutImagePickerActivity.class);
                intent.putExtra(UniversalLayoutImagePickerActivity.EXTRA_LAYOUT_ID, layout.getId());
                startActivity(intent);
            }
        });

        entries.add(getString(R.string.dialog_title_layout_name));
        actions.add(new Runnable() {
            @Override
            public void run() {
                showRenameDialog(layout);
            }
        });

        if (!isSelected) {
            entries.add(getString(R.string.menu_use_layout));
            actions.add(new Runnable() {
                @Override
                public void run() {
                    layoutStore.setSelectedLayoutId(layout.getId());
                    refresh();
                }
            });
        }

        entries.add(getString(R.string.menu_share_layout));
        actions.add(new Runnable() {
            @Override
            public void run() {
                shareLayout(layout);
            }
        });

        entries.add(getString(R.string.menu_delete_layout));
        actions.add(new Runnable() {
            @Override
            public void run() {
                showDeleteDialog(layout);
            }
        });

        new AlertDialog.Builder(this)
                .setTitle(layout.getName())
                .setItems(entries.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        actions.get(which).run();
                    }
                })
                .show();
    }

    /**
     * Hands a layout to another app as a file, which is how a layout moves to another device.
     *
     * The file is written into the app's own cache and offered through a provider, so no
     * storage permission is asked for and nothing is left lying about in shared storage.
     */
    private void shareLayout(final UniversalLayoutDefinition layout) {
        String json;

        try {
            json = layoutAsExchangeJson(layout);
        } catch (org.json.JSONException e) {
            showMessage(getString(R.string.import_failed));
            return;
        }

        try {
            java.io.File file = UniversalLayoutExchange.writeShareFile(this, layout.getName(), json);
            Uri address = Uri.parse("content://" + UniversalLayoutShareProvider.authority(this)
                    + "/" + file.getName());

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/json");
            share.putExtra(Intent.EXTRA_STREAM, address);
            share.putExtra(Intent.EXTRA_SUBJECT, layout.getName());
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(share, getString(R.string.menu_share_layout)));
        } catch (java.io.IOException e) {
            showMessage(getString(R.string.import_failed));
        }
    }

    /**
     * A stored layout written as a file, with the screen it was arranged on.
     */
    private String layoutAsExchangeJson(UniversalLayoutDefinition layout) throws org.json.JSONException {
        SharedPreferences preferences = getSharedPreferences(
                UniversalLayoutConfigurationLoader.getLayoutPreferenceFileName(layout.getId()),
                MODE_PRIVATE);

        String elements = preferences.getString("elements", "[]");
        int width = preferences.getInt("savedWidth", 0);
        int height = preferences.getInt("savedHeight", 0);

        if (width <= 0 || height <= 0) {
            // A layout from before the size was recorded is sent as the screen it is on now,
            // which is the best answer available and keeps it usable on the other device
            width = getLayoutAreaWidth();
            height = getLayoutAreaHeight();
        }

        return UniversalLayoutExchange.toJson(layout.getName(), width, height, elements);
    }

    /**
     * Asks the user for a layout file. Reading one needs no permission, because the user picks
     * the file themselves and the picker hands back a readable address.
     */
    private void importLayout() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/json", "text/plain", "application/octet-stream"});

        try {
            startActivityForResult(pick, REQUEST_IMPORT_LAYOUT);
        } catch (android.content.ActivityNotFoundException e) {
            showMessage(getString(R.string.import_failed));
        }
    }

    private static final int REQUEST_IMPORT_LAYOUT = 4001;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_IMPORT_LAYOUT || resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri source = data.getData();

        if (source == null) {
            return;
        }

        String text;

        try {
            text = readText(source);
        } catch (java.io.IOException e) {
            showMessage(getString(R.string.import_failed));
            return;
        }

        UniversalLayoutDefinition created = importForTesting(text);

        if (created == null) {
            showMessage(getString(R.string.import_failed));
            return;
        }

        refresh();

        // A layout made for another screen is not fitted here: it is fitted when it is opened,
        // where the size of the streaming area is known. Saying so is still worth it.
        if (importedWasForAnotherScreen) {
            showMessage(getString(R.string.import_done_rescaled, created.getName()));
        }
        else {
            showMessage(getString(R.string.import_done, created.getName()));
        }
    }

    /**
     * Whether the last import read a layout arranged for a screen other than this device's.
     */
    private boolean importedWasForAnotherScreen;

    /**
     * Reads a layout file and stores it as a layout of its own. Returns null when the text is
     * not a layout.
     */
    public UniversalLayoutDefinition importForTesting(String text) {
        UniversalLayoutExchange.Imported imported;

        try {
            imported = UniversalLayoutExchange.fromJson(text);
        } catch (org.json.JSONException e) {
            android.util.Log.w("UniversalLayouts", "could not read the layout file", e);
            importedWasForAnotherScreen = false;
            return null;
        }

        importedSourceWidth = imported.getSourceWidth();
        importedSourceHeight = imported.getSourceHeight();

        importedWasForAnotherScreen = imported.hasSourceScreen()
                && !UniversalLayoutExchange.fitsThisDevice(this, imported.getSourceWidth(),
                        imported.getSourceHeight());

        return UniversalLayoutExchange.store(this, imported);
    }

    /**
     * The size the last imported layout was arranged for, or 0 when the file did not say.
     */
    public int getImportedSourceWidthForTesting() {
        return importedSourceWidth;
    }

    public int getImportedSourceHeightForTesting() {
        return importedSourceHeight;
    }

    private int importedSourceWidth;
    private int importedSourceHeight;

    /**
     * Reads a picked file, refusing anything far larger than a layout can be.
     */
    private String readText(Uri source) throws java.io.IOException {
        java.io.InputStream in = getContentResolver().openInputStream(source);

        if (in == null) {
            throw new java.io.IOException("no stream for " + source);
        }

        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            int total = 0;

            while ((read = in.read(buffer)) > 0) {
                total += read;

                if (total > MAX_LAYOUT_FILE_BYTES) {
                    throw new java.io.IOException("layout file is too big");
                }

                out.write(buffer, 0, read);
            }

            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    private static final int MAX_LAYOUT_FILE_BYTES = 4 * 1024 * 1024;

    private void showMessage(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }

    private void showMessage(int messageId, Object... arguments) {
        showMessage(getString(messageId, arguments));
    }

    /**
     * The size of the area controls are placed in, which is the screen they were arranged on.
     */
    private int getLayoutAreaWidth() {
        View root = findViewById(R.id.layoutsRoot);
        return root != null && root.getWidth() > 0
                ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
    }

    private int getLayoutAreaHeight() {
        View root = findViewById(R.id.layoutsRoot);
        return root != null && root.getHeight() > 0
                ? root.getHeight() : getResources().getDisplayMetrics().heightPixels;
    }

    private void openEditor(UniversalLayoutDefinition layout) {
        // Selecting the layout the user is about to edit matches the expectation that
        // editing a layout means using it.
        layoutStore.setSelectedLayoutId(layout.getId());

        Intent intent = new Intent(this, UniversalLayoutEditorActivity.class);
        intent.putExtra(UniversalLayoutEditorActivity.EXTRA_LAYOUT_ID, layout.getId());
        startActivity(intent);

        refresh();
    }

    /**
     * Grid adapter for the layout list. Artwork is decoded in the background, because a
     * user-chosen image can be much larger than the thumbnail.
     */
    private class LayoutGridAdapter extends UniversalImageAdapter {
        private final List<UniversalLayoutDefinition> layouts = new ArrayList<>();

        LayoutGridAdapter() {
            super(R.drawable.ic_layout_default,
                    (int) getResources().getDimension(R.dimen.layout_item_image_width),
                    (int) getResources().getDimension(R.dimen.layout_item_image_height));
        }

        void setLayouts(List<UniversalLayoutDefinition> newLayouts) {
            layouts.clear();
            layouts.addAll(newLayouts);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return layouts.size();
        }

        @Override
        public UniversalLayoutDefinition getItem(int position) {
            return layouts.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public String getImagePath(int position) {
            return getItem(position).getImagePath();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(UniversalLayoutsActivity.this)
                        .inflate(R.layout.universal_layout_grid_item, parent, false);
            }

            UniversalLayoutDefinition layout = getItem(position);

            TextView nameText = view.findViewById(R.id.layoutItemName);
            nameText.setText(layout.getName());

            ImageView badge = view.findViewById(R.id.layoutItemSelectedBadge);
            boolean isSelected = layout.getId().equals(layoutStore.getSelectedLayoutId());
            badge.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            bindImage(view.findViewById(R.id.layoutItemImage), position);

            return view;
        }
    }
}
