package com.limelight.binding.input.virtual_controller.universal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_universal_layouts);

        // Compensates drawing under the status bar, so the toolbar is not positioned
        // under it. Every normal activity in this app does this.
        UiHelper.notifyNewRootView(this);

        layoutStore = UniversalLayoutStore.getInstance(this);

        layoutsGrid = findViewById(R.id.layoutsGrid);
        enabledSwitch = findViewById(R.id.layoutsEnabledSwitch);
        emptyText = findViewById(R.id.layoutsEmptyText);

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

    private boolean isUniversalLayoutEnabled() {
        return getPreferences().getBoolean(PreferenceConfiguration.UNIVERSAL_LAYOUT_PREF_STRING, false);
    }

    private void setUniversalLayoutEnabled(boolean enabled) {
        getPreferences().edit()
                .putBoolean(PreferenceConfiguration.UNIVERSAL_LAYOUT_PREF_STRING, enabled)
                .apply();
    }

    private void refresh() {
        updatingSwitch = true;
        enabledSwitch.setChecked(isUniversalLayoutEnabled());
        updatingSwitch = false;

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
                .setTitle(R.string.dialog_title_delete_layout)
                .setMessage(getString(R.string.dialog_text_delete_layout, layout.getName()))
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
