package com.limelight.binding.input.virtual_controller.universal.editor;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalGeometry;

import java.util.List;

/**
 * The editing panel of the layout editor.
 *
 * The panel is built in code rather than in XML because its content depends entirely on
 * what is selected: a button and a stick need different rows, and the event sequence of a
 * button can grow and shrink. The helper methods here are what the editor uses to compose
 * that content, and they keep the styling in one place.
 */
public class UniversalEditorPanel {
    private final Context context;
    private final LinearLayout content;
    private final ScrollView scrollView;

    private Runnable onCloseRequested;
    private Runnable onDragFinished;

    /**
     * Height of the drawer as a share of the screen, so it can never cover the arrangement
     * completely.
     */
    private static final float MAX_HEIGHT_FRACTION = 0.75f;

    /**
     * Share of the screen width the drawer may take.
     */
    private static final float MAX_WIDTH_FRACTION = 0.45f;

    /**
     * Movement in pixels before a press on the header counts as a drag rather than a tap.
     */
    private static final int DRAG_SLOP_PX = 12;

    /**
     * Called when a slider or its input field changes the geometry.
     */
    public interface OnGeometryChanged {
        void onGeometryChanged(int left, int top, int width, int height);
    }

    /**
     * Called when the name field changes.
     */
    public interface OnNameChanged {
        void onNameChanged(String name);
    }

    /**
     * @param scrollView the scrolling area the rows are added into
     * @param content    the view inside it that holds the rows, which is what a rebuild clears
     * @param dragHandle the view that drags the panel around, which is its header
     */
    public UniversalEditorPanel(Context context, ScrollView scrollView, LinearLayout content,
                                View dragHandle) {
        this.context = context;
        this.scrollView = scrollView;
        this.content = content;

        int padding = context.getResources().getDimensionPixelSize(R.dimen.layout_editor_panel_padding);
        content.setPadding(padding, padding, padding, padding);

        attachDragHandle(dragHandle);
    }

    public View getView() {
        return scrollView;
    }

    /**
     * Sets what happens when the header is tapped rather than dragged.
     */
    public void setOnCloseRequested(Runnable onCloseRequested) {
        this.onCloseRequested = onCloseRequested;
    }

    /**
     * Notified after the panel has been dragged, so the caller can remember where it is.
     */
    public void setOnDragFinished(Runnable onDragFinished) {
        this.onDragFinished = onDragFinished;
    }

    /**
     * Sizes the panel to fit the screen, so it stays usable on a phone and can never cover
     * the arrangement completely.
     */
    public void applyScreenBounds(View panel) {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;

        ViewGroup.LayoutParams params = panel.getLayoutParams();
        params.width = Math.min(
                context.getResources().getDimensionPixelSize(R.dimen.layout_editor_panel_width),
                (int) (screenWidth * MAX_WIDTH_FRACTION));
        params.height = (int) (screenHeight * MAX_HEIGHT_FRACTION);
        panel.setLayoutParams(params);
    }

    /**
     * Makes the header a handle that drags the panel around the screen.
     */
    private void attachDragHandle(View handle) {
        if (handle == null) {
            return;
        }

        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private float startX;
            private float startY;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                View panel = (View) view.getParent();

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startX = panel.getX();
                        startY = panel.getY();
                        moved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - downRawX;
                        float deltaY = event.getRawY() - downRawY;

                        if (Math.abs(deltaX) > DRAG_SLOP_PX || Math.abs(deltaY) > DRAG_SLOP_PX) {
                            moved = true;
                        }

                        if (moved) {
                            moveWithin(panel, startX + deltaX, startY + deltaY);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            if (onDragFinished != null) {
                                onDragFinished.run();
                            }
                        }
                        else if (onCloseRequested != null) {
                            // A tap on the header puts the drawer away, which frees the screen
                            // for arranging
                            onCloseRequested.run();
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        if (moved && onDragFinished != null) {
                            onDragFinished.run();
                        }
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private void moveWithin(View panel, float x, float y) {
        if (!(panel.getParent() instanceof ViewGroup)) {
            return;
        }

        ViewGroup bounds = (ViewGroup) panel.getParent();

        float maxX = Math.max(0, bounds.getWidth() - panel.getWidth());
        float maxY = Math.max(0, bounds.getHeight() - panel.getHeight());

        panel.setX(Math.max(0, Math.min(x, maxX)));
        panel.setY(Math.max(0, Math.min(y, maxY)));
    }

    /**
     * Removes everything that was previously added.
     */
    public void clear() {
        content.removeAllViews();
        sliderRows.clear();
    }

    public void addSection(String title) {
        TextView view = new TextView(context);
        view.setText(title);
        view.setTextColor(0xFF9E9EA8);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setPadding(0, dp(12), 0, dp(4));
        content.addView(view);
    }

    public void addText(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(0xFFE0E0E6);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setPadding(0, dp(4), 0, dp(4));
        content.addView(view);
    }

    public void addStatus(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(0xFF9E9EA8);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setPadding(0, dp(2), 0, dp(6));
        content.addView(view);
    }

    /**
     * Adds a labelled text field.
     *
     * @return the field, so the caller can read it back
     */
    public EditText addTextField(String label, String value, final OnTextChangedListener listener) {
        addFieldLabel(label);

        final EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setTextColor(0xFFE0E0E6);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);

        // The text is set before the listener is attached, so showing an existing value is
        // not reported as the user typing it.
        input.setText(value);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                listener.onTextChanged(s.toString());
            }
        });

        content.addView(input, matchWidth());
        return input;
    }

    /**
     * Adds a checkbox with a label.
     */
    public CheckBox addCheckBox(String label, boolean checked, final OnCheckedListener listener) {
        final CheckBox checkBox = new CheckBox(context);
        checkBox.setText(label);
        checkBox.setTextColor(0xFFE0E0E6);
        checkBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        checkBox.setChecked(checked);
        checkBox.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                listener.onCheckedChanged(isChecked);
            }
        });

        content.addView(checkBox, matchWidth());
        return checkBox;
    }

    /**
     * Adds a dropdown.
     *
     * @return the spinner, so the caller can replace its adapter later
     */
    public Spinner addSpinner(String label, List<String> options, int selectedIndex,
                              final OnItemSelectedListener listener) {
        addFieldLabel(label);

        final Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(selectedIndex, options.size() - 1)), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                listener.onItemSelected(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        content.addView(spinner, matchWidth());
        return spinner;
    }

    /**
     * Adds a row with a slider and a field next to it, so a value can be dragged roughly
     * and then typed exactly.
     *
     * @return the row, so the caller can refresh it when the element is dragged on screen
     */
    public SliderRow addSliderRow(String label, int value, int max,
                                 final OnValueChangedListener listener) {
        SliderRow row = new SliderRow(label, value, max, listener);
        sliderRows.put(label, row);
        return row;
    }

    public void addButton(String text, View.OnClickListener listener) {
        Button button = new Button(context);
        button.setText(text);
        button.setMinWidth(0);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        content.addView(button, matchWidth());
    }

    /**
     * Adds a row of buttons that share the available width.
     */
    public void addButtonRow(String[] texts, View.OnClickListener[] listeners) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        for (int i = 0; i < texts.length; i++) {
            Button button = new Button(context);
            button.setText(texts[i]);
            button.setMinWidth(0);
            button.setAllCaps(false);
            button.setOnClickListener(listeners[i]);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(button, params);
        }

        content.addView(row, matchWidth());
    }

    private void addFieldLabel(String label) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextColor(0xFFB0B0BA);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setPadding(0, dp(8), 0, 0);
        content.addView(view);
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    public interface OnTextChangedListener {
        void onTextChanged(String text);
    }

    public interface OnCheckedListener {
        void onCheckedChanged(boolean checked);
    }

    public interface OnItemSelectedListener {
        void onItemSelected(int position);
    }

    public interface OnValueChangedListener {
        void onValueChanged(int value);
    }

    /**
     * A slider with an input field beside it.
     *
     * The two stay in sync in both directions, and the caller is told about a change only
     * when the value actually differs, so a drag does not fight with the field it updates.
     */
    public class SliderRow {
        private final SeekBar seekBar;
        private final EditText input;

        /**
         * Kept so a test can run the slider exactly as a drag does; the platform does not
         * hand the listener back.
         */
        private final SeekBar.OnSeekBarChangeListener changeListener;

        /**
         * The value that was last applied.
         *
         * This is not always the position of the handle: a number typed into the field may be
         * larger than the slider can show, and the number that was typed is the one that
         * counts.
         */
        private int value;

        private boolean updating;

        SliderRow(String label, int initialValue, int max, final OnValueChangedListener listener) {
            this.value = initialValue;

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView labelView = new TextView(context);
            labelView.setText(label);
            labelView.setTextColor(0xFFB0B0BA);
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            labelView.setMinWidth(dp(72));
            row.addView(labelView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            seekBar = new SeekBar(context);
            seekBar.setMax(Math.max(1, max));
            seekBar.setProgress(clamp(initialValue, max));
            row.addView(seekBar, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            input = new EditText(context);
            input.setSingleLine(true);
            input.setText(String.valueOf(initialValue));
            input.setTextColor(0xFFE0E0E6);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setImeOptions(EditorInfo.IME_ACTION_DONE);
            input.setGravity(Gravity.END);
            row.addView(input, new LinearLayout.LayoutParams(
                    context.getResources().getDimensionPixelSize(R.dimen.layout_editor_slider_input_width),
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            changeListener = new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (!fromUser || updating) {
                        return;
                    }

                    updating = true;
                    input.setText(String.valueOf(progress));
                    updating = false;

                    SliderRow.this.value = progress;
                    listener.onValueChanged(progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                }
            };

            seekBar.setOnSeekBarChangeListener(changeListener);

            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (updating) {
                        return;
                    }

                    int parsed;
                    try {
                        parsed = Integer.parseInt(s.toString().trim());
                    } catch (NumberFormatException e) {
                        // A half typed number is not a value yet
                        return;
                    }

                    updating = true;
                    seekBar.setProgress(clamp(parsed, seekBar.getMax()));
                    updating = false;

                    // The number that was typed is the value, even when the slider cannot
                    // show it, so a large typed size is not quietly cut down
                    value = parsed;
                    listener.onValueChanged(parsed);
                }
            });

            content.addView(row, matchWidth());
        }

        private int clamp(int value, int max) {
            if (value < 0) {
                return 0;
            }

            return Math.min(value, max);
        }

        /**
         * Shows a value that changed elsewhere, for example because the control was
         * dragged on screen. Nothing is reported back to the caller, so this cannot loop.
         */
        public void setValue(int value) {
            this.value = value;

            updating = true;
            seekBar.setProgress(clamp(value, seekBar.getMax()));
            input.setText(String.valueOf(value));
            updating = false;
        }

        /**
         * The value the row currently stands for, which is what the editor applies. It is the
         * typed number for a typed value and the handle position for a drag.
         */
        public int getValue() {
            return value;
        }

        /**
         * Moves the slider the way a finger does, so the listener runs exactly as it does
         * during a drag. Used by the debug smoke test.
         */
        public void dragTo(int value) {
            int clamped = clamp(value, seekBar.getMax());
            seekBar.setProgress(clamped);
            changeListener.onProgressChanged(seekBar, clamped, true);
        }    }

    /**
     * The rows that were added, by their label. Used by the debug smoke test to check that a
     * change to one row does not disturb another.
     */
    private final java.util.Map<String, SliderRow> sliderRows = new java.util.HashMap<>();

    public SliderRow getSliderRow(String label) {
        return sliderRows.get(label);
    }

    /**
     * Shows the current geometry on the position and size sliders.
     *
     * A change made on one slider must not rebuild the drawer: rebuilding it would replace
     * the control the user is holding, and the rows of the new drawer would report the size
     * they were built with, silently undoing the change just made. Updating the values in
     * place is what keeps a width and a height change from overwriting each other.
     */
    public void showGeometry(UniversalGeometry geometry) {
        setSliderValue(R.string.editor_label_x, geometry.getLeft());
        setSliderValue(R.string.editor_label_y, geometry.getTop());
        setSliderValue(R.string.editor_label_width, geometry.getWidth());
        setSliderValue(R.string.editor_label_height, geometry.getHeight());
    }

    private void setSliderValue(int labelId, int value) {
        SliderRow row = sliderRows.get(context.getString(labelId));
        if (row != null) {
            row.setValue(value);
        }
    }
}
