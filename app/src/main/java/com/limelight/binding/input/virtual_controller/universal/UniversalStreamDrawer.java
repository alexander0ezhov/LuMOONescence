package com.limelight.binding.input.virtual_controller.universal;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.List;

/**
 * The menu of a running stream, which slides in from the right.
 *
 * It takes the place of the back button ending the stream outright. The first press of back
 * opens it, a press while it is open leaves the stream, and a tap outside it closes it again.
 *
 * While it is open a fully transparent sheet covers the whole screen. The sheet is what makes a
 * tap outside close the menu: it swallows the touch, so the press cannot also reach the game or
 * the on-screen controls behind it.
 */
public class UniversalStreamDrawer {
    /**
     * What the drawer needs from the stream. Everything it can do is something the stream owns.
     */
    public interface Host {
        void onExitStream();

        void onShowKeyboard();

        /**
         * What the touchscreen does now, one of the PreferenceConfiguration.TOUCH_MODE_ values.
         */
        int getTouchMode();

        void onTouchModeChanged(int mode);

        /**
         * The layouts the user has made, or null when there are none.
         */
        List<UniversalLayoutDefinition> getLayouts();

        String getSelectedLayoutId();

        void onLayoutChosen(String layoutId);

        /**
         * Whether the on-screen layout is currently hidden, which is how the screen is seen
         * without any controls on it.
         */
        boolean isLayoutHidden();

        void onLayoutHidden();

        /**
         * The transparency of the on-screen layout, as a percentage.
         */
        int getLayoutOpacity();

        void onLayoutOpacityChanged(int percent);
    }

    private final Activity activity;
    private final Host host;

    private final FrameLayout root;
    private final LinearLayout panel;
    private final LinearLayout content;

    private boolean open;

    /**
     * The row that is unfolded, or null when only the rows themselves are showing.
     */
    private LinearLayout expandedBody;

    private ImageView expandedArrow;

    /**
     * How wide the panel is: a quarter of the screen, so the game stays visible beside it.
     */
    private static final float WIDTH_SHARE = 0.25f;

    private static final int PANEL_WIDTH_MIN_DP = 280;

    /**
     * What sits between the title of a row and what is chosen in it.
     */
    private static final String ROW_SEPARATOR = "   \u00b7   ";

    /**
     * The lowest transparency the slider offers. Below this the controls are hard to see at all,
     * which makes them look broken rather than faint.
     */
    private static final int OPACITY_MIN = 20;

    public UniversalStreamDrawer(Activity activity, FrameLayout parent, Host host) {
        this.activity = activity;
        this.host = host;

        root = new FrameLayout(activity);
        root.setVisibility(View.GONE);

        // A sheet over everything, transparent and yet touchable: taps land here instead of on
        // the game, and a tap here closes the menu
        root.setBackgroundColor(0x00000000);
        root.setClickable(true);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hide();
            }
        });

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xF21A1A20);
        panel.setClickable(true);

        // The panel swallows its own taps, so pressing it does not close it through the sheet
        panel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            }
        });

        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        panel.addView(buildTitle(), matchWidth());
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.addView(buildExitButton(), matchWidth());

        buildRows();

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth(), ViewGroup.LayoutParams.MATCH_PARENT);
        panelParams.gravity = Gravity.END;

        root.addView(panel, panelParams);
        parent.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * Builds the rows again, so the marks and the values follow what was chosen.
     */
    private void buildRows() {
        content.removeAllViews();
        expandedBody = null;
        expandedArrow = null;

        content.addView(buildKeyboardRow(), matchWidth());
        content.addView(buildTouchModeRow(), matchWidth());
        content.addView(buildOpacityRow(), matchWidth());
        content.addView(buildLayoutRow(), matchWidth());
    }

    private int panelWidth() {
        int screen = activity.getResources().getDisplayMetrics().widthPixels;
        int minimum = dp(PANEL_WIDTH_MIN_DP);

        return Math.max(minimum, Math.round(screen * WIDTH_SHARE));
    }

    private View buildTitle() {
        TextView title = new TextView(activity);

        title.setText(R.string.stream_menu_title);
        title.setTextColor(0xFFF2F4F8);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        title.setPadding(dp(16), dp(16), dp(16), dp(10));

        return title;
    }

    /**
     * The row that brings up the software keyboard. It is not a choice, so it has no arrow.
     */
    private View buildKeyboardRow() {
        TextView row = makePlainRow(getString(R.string.stream_menu_keyboard));

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hide();
                host.onShowKeyboard();
            }
        });

        return row;
    }

    /**
     * A row that unfolds into what the touchscreen can do.
     */
    private View buildTouchModeRow() {
        ExpandableRow row = makeExpandableRow(getString(R.string.stream_menu_touch), touchModeName());
        RadioGroup choices = makeChoiceGroup();

        addChoice(choices, getString(R.string.stream_menu_touch_off),
                host.getTouchMode() == PreferenceConfiguration.TOUCH_MODE_OFF,
                PreferenceConfiguration.TOUCH_MODE_OFF);

        addChoice(choices, getString(R.string.stream_menu_touch_trackpad),
                host.getTouchMode() == PreferenceConfiguration.TOUCH_MODE_TRACKPAD,
                PreferenceConfiguration.TOUCH_MODE_TRACKPAD);

        addChoice(choices, getString(R.string.stream_menu_touch_screen),
                host.getTouchMode() == PreferenceConfiguration.TOUCH_MODE_TOUCHSCREEN,
                PreferenceConfiguration.TOUCH_MODE_TOUCHSCREEN);

        addChoice(choices, getString(R.string.stream_menu_touch_right_stick),
                host.getTouchMode() == PreferenceConfiguration.TOUCH_MODE_RIGHT_STICK,
                PreferenceConfiguration.TOUCH_MODE_RIGHT_STICK);

        // Real touch events, the way a Windows tablet sends them. Marked as an experiment
        // because it only does anything on a host that takes touch input.
        addChoice(choices, getString(R.string.stream_menu_touch_events),
                host.getTouchMode() == PreferenceConfiguration.TOUCH_MODE_TOUCH_EVENTS,
                PreferenceConfiguration.TOUCH_MODE_TOUCH_EVENTS);

        return row.withChoices(choices);
    }

    /**
     * The transparency of the on-screen layout, which is the one setting worth having at hand
     * while playing: what reads well over one game is invisible over the next.
     */
    private View buildOpacityRow() {
        LinearLayout row = new LinearLayout(activity);

        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(rowBackground());

        TextView title = new TextView(activity);
        title.setText(R.string.stream_menu_opacity);
        title.setTextColor(0xFFE8EAF0);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);

        final TextView value = new TextView(activity);
        value.setTextColor(0xFFE8EAF0);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        value.setText(String.valueOf(host.getLayoutOpacity()));
        value.setMinWidth(dp(38));
        value.setGravity(Gravity.END);

        final SeekBar slider = new SeekBar(activity);
        slider.setMax(100 - OPACITY_MIN);
        slider.setProgress(Math.max(0, Math.min(100 - OPACITY_MIN,
                host.getLayoutOpacity() - OPACITY_MIN)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = OPACITY_MIN + progress;

                value.setText(String.valueOf(percent));

                if (fromUser) {
                    host.onLayoutOpacityChanged(percent);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sliderParams.setMarginStart(dp(10));

        row.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(slider, sliderParams);
        row.addView(value);

        return row;
    }

    /**
     * A row that unfolds into the layouts the user has made, with the choice to have none at all.
     */
    private View buildLayoutRow() {
        ExpandableRow row = makeExpandableRow(getString(R.string.stream_menu_layout),
                host.isLayoutHidden()
                        ? getString(R.string.stream_menu_layout_none)
                        : selectedLayoutName());
        RadioGroup choices = makeChoiceGroup();

        // A screen with no controls on it at all, for when the layout is in the way
        RadioButton none = addChoice(choices, getString(R.string.stream_menu_layout_none),
                host.isLayoutHidden(), -1);

        none.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                host.onLayoutHidden();
                close();
            }
        });

        List<UniversalLayoutDefinition> layouts = host.getLayouts();
        String selectedId = host.getSelectedLayoutId();

        if (layouts != null) {
            for (final UniversalLayoutDefinition layout : layouts) {
                RadioButton choice = addChoice(choices, layout.getName(),
                        !host.isLayoutHidden() && layout.getId().equals(selectedId), -1);

                choice.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        host.onLayoutChosen(layout.getId());
                        close();
                    }
                });
            }
        }

        return row.withChoices(choices);
    }

    private View buildExitButton() {
        Button exit = new Button(activity);

        exit.setText(R.string.stream_menu_exit);
        exit.setAllCaps(false);
        exit.setTextColor(0xFFFF7B72);
        exit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        exit.setPadding(dp(8), dp(12), dp(8), dp(12));

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0x2EFF5A50);
        shape.setCornerRadius(dp(8));
        shape.setStroke(Math.max(1, dp(1) / 2), 0x66FF6B60);

        LinearLayout.LayoutParams params = matchWidth();
        params.setMargins(dp(12), dp(10), dp(12), dp(14));
        exit.setBackground(shape);

        exit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                host.onExitStream();
            }
        });

        LinearLayout holder = new LinearLayout(activity);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.addView(exit, params);

        return holder;
    }

    /**
     * A row with nothing to unfold.
     */
    private TextView makePlainRow(String title) {
        TextView row = new TextView(activity);

        row.setText(title);
        row.setTextColor(0xFFE8EAF0);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(rowBackground());

        return row;
    }

    /**
     * A row with the chosen value and an arrow at its right edge.
     */
    private ExpandableRow makeExpandableRow(String title, String value) {
        return new ExpandableRow(title, value);
    }

    /**
     * A row that can be unfolded, and the list that belongs to it.
     */
    private final class ExpandableRow {
        private final LinearLayout row;
        private final ImageView arrow;

        ExpandableRow(String title, String value) {
            row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(14), dp(14), dp(14));
            row.setBackground(rowBackground());

            TextView titleView = new TextView(activity);
            titleView.setText(value == null || value.isEmpty()
                    ? title : title + ROW_SEPARATOR + value);
            titleView.setTextColor(0xFFE8EAF0);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);

            arrow = new ImageView(activity);
            arrow.setImageResource(R.drawable.ic_expand_more);
            arrow.setAlpha(0.75f);

            LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(18), dp(18));
            arrowParams.setMarginStart(dp(8));

            row.addView(titleView, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(arrow, arrowParams);
        }

        /**
         * Returns the row together with its list, and arranges for the arrow to unfold it.
         */
        View withChoices(final RadioGroup choices) {
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    unfold(choices, arrow);
                }
            });

            LinearLayout holder = new LinearLayout(activity);
            holder.setOrientation(LinearLayout.VERTICAL);
            holder.addView(row, matchWidth());
            holder.addView(choices, matchWidth());

            return holder;
        }
    }

    private Drawable rowBackground() {
        StateListDrawable states = new StateListDrawable();

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(0x3DFFFFFF);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0x14FFFFFF);

        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);

        return states;
    }

    /**
     * The list of choices of one row.
     *
     * All the choices of a row go into one group, which is what makes them exclusive: a group of
     * one per choice would leave every choice the user has ever pressed looking as if it were
     * still on.
     */
    private RadioGroup makeChoiceGroup() {
        RadioGroup group = new RadioGroup(activity);

        group.setOrientation(RadioGroup.VERTICAL);
        group.setVisibility(View.GONE);

        return group;
    }

    private RadioButton addChoice(RadioGroup group, String label, boolean checked, int touchMode) {
        RadioButton choice = new RadioButton(activity);

        choice.setText(label);
        choice.setTextColor(0xFFD8DBE2);
        choice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        choice.setPadding(dp(24), dp(4), dp(12), dp(4));
        choice.setTag(touchMode);

        // The mark is set after the listener would fire, so setting it does not report a choice
        // the user did not make
        choice.setChecked(checked);

        if (touchMode >= 0) {
            choice.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Object tag = view.getTag();

                    if (tag instanceof Integer) {
                        host.onTouchModeChanged((Integer) tag);
                    }

                    close();
                }
            });
        }

        group.addView(choice, matchWidth());
        return choice;
    }

    /**
     * Opens one of the rows and closes whichever other row was open, so only one list is out at
     * a time. The arrow turns over while its list is showing.
     */
    private void unfold(RadioGroup body, ImageView arrow) {
        boolean wasOpen = body.getVisibility() == View.VISIBLE;

        if (expandedBody != null && expandedBody != body) {
            expandedBody.setVisibility(View.GONE);

            if (expandedArrow != null) {
                expandedArrow.setRotation(0f);
            }
        }

        body.setVisibility(wasOpen ? View.GONE : View.VISIBLE);
        arrow.setRotation(wasOpen ? 0f : 180f);

        expandedBody = wasOpen ? null : body;
        expandedArrow = wasOpen ? null : arrow;
    }

    private String touchModeName() {
        int mode = host.getTouchMode();

        if (mode == PreferenceConfiguration.TOUCH_MODE_OFF) {
            return getString(R.string.stream_menu_touch_off);
        }

        if (mode == PreferenceConfiguration.TOUCH_MODE_TRACKPAD) {
            return getString(R.string.stream_menu_touch_trackpad);
        }

        if (mode == PreferenceConfiguration.TOUCH_MODE_TOUCHSCREEN) {
            return getString(R.string.stream_menu_touch_screen);
        }

        if (mode == PreferenceConfiguration.TOUCH_MODE_TOUCH_EVENTS) {
            return getString(R.string.stream_menu_touch_events);
        }

        return getString(R.string.stream_menu_touch_right_stick);
    }

    private String selectedLayoutName() {
        List<UniversalLayoutDefinition> layouts = host.getLayouts();
        String selectedId = host.getSelectedLayoutId();

        if (layouts != null) {
            for (UniversalLayoutDefinition layout : layouts) {
                if (layout.getId().equals(selectedId)) {
                    return layout.getName();
                }
            }
        }

        return getString(R.string.stream_menu_layout_none);
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private String getString(int id) {
        return activity.getString(id);
    }

    public boolean isOpen() {
        return open;
    }

    public void show() {
        if (open) {
            return;
        }

        // The rows are rebuilt on every opening, so the marks and the values are what they are
        // now, whatever changed them in between
        buildRows();

        open = true;
        root.setVisibility(View.VISIBLE);

        // The on-screen controls live in the same layout and are added again whenever the layout
        // is rebuilt, so the menu asks to be on top rather than assuming it still is
        root.bringToFront();

        // Slides in from the edge of the screen
        panel.setTranslationX(panelWidth());
        panel.animate().translationX(0f).setDuration(180).start();
    }

    /**
     * Puts the menu above everything else in its layout.
     */
    public void bringToFront() {
        root.bringToFront();
    }

    public void hide() {
        if (!open) {
            return;
        }

        open = false;
        collapse();

        panel.animate().translationX(panelWidth()).setDuration(150)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        root.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    /**
     * Handles the back button.
     *
     * @return true when the drawer used the press
     */
    public boolean handleBack() {
        if (open) {
            // The menu is already open, so back means what it says at the bottom of it
            host.onExitStream();
            return true;
        }

        show();
        return true;
    }

    private void close() {
        collapse();
        hide();
    }

    private void collapse() {
        if (expandedBody != null) {
            expandedBody.setVisibility(View.GONE);
            expandedBody = null;
        }

        if (expandedArrow != null) {
            expandedArrow.setRotation(0f);
            expandedArrow = null;
        }
    }

    /**
     * Puts the menu in place with no animation, for the debug smoke test, which draws it into a
     * picture rather than onto the screen.
     */
    public void settleForTesting() {
        open = true;
        root.setVisibility(View.VISIBLE);
        panel.setTranslationX(0f);
    }

    /**
     * Drags the opacity slider the way a finger does, for the debug smoke test.
     *
     * Setting the progress of a slider reports a change that came from the code rather than
     * from the user, which is deliberately not passed on, so a check has to touch it.
     */
    public boolean dragOpacityToForTesting(int percent) {
        SeekBar slider = findOpacitySlider(content);

        if (slider == null || slider.getWidth() <= 0) {
            return false;
        }

        int wanted = Math.max(OPACITY_MIN, Math.min(100, percent));
        float x = slider.getWidth() * (wanted - OPACITY_MIN) / (float)(100 - OPACITY_MIN);
        float y = slider.getHeight() / 2f;
        long now = android.os.SystemClock.uptimeMillis();

        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent move = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0);

        slider.dispatchTouchEvent(down);
        slider.dispatchTouchEvent(move);
        slider.dispatchTouchEvent(up);

        down.recycle();
        move.recycle();
        up.recycle();

        return true;
    }

    private SeekBar findOpacitySlider(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);

            if (child instanceof SeekBar) {
                return (SeekBar) child;
            }

            if (child instanceof ViewGroup) {
                SeekBar found = findOpacitySlider((ViewGroup) child);

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * Presses the row with this title, for the debug smoke test.
     */
    public boolean clickRowForTesting(String title) {
        View row = findRow(content, title, true);

        if (row == null) {
            return false;
        }

        row.performClick();
        return true;
    }

    /**
     * Presses the choice with this label, for the debug smoke test.
     */
    public boolean clickChoiceForTesting(String label) {
        View choice = findRow(content, label, false);

        if (choice == null) {
            return false;
        }

        choice.performClick();
        return true;
    }

    private View findRow(ViewGroup group, String label, boolean row) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);

            if (child instanceof TextView) {
                String text = ((TextView) child).getText().toString();

                if (row && text.startsWith(label + ROW_SEPARATOR)) {
                    return (View) child.getParent();
                }

                if (row && text.equals(label) && child.isClickable()
                        && !(child instanceof RadioButton)) {
                    // A row with nothing unfolded and no value: the keyboard row
                    return child;
                }

                if (!row && child instanceof RadioButton && text.equals(label)) {
                    return child;
                }
            }

            if (child instanceof ViewGroup) {
                View found = findRow((ViewGroup) child, label, row);

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * The button that leaves the stream, for the debug smoke test.
     */
    public Button getExitButtonForTesting() {
        return findButton(panel);
    }

    private Button findButton(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);

            // A radio button is a button as far as Java is concerned, so the choices of a row
            // would be mistaken for this one without the second test
            if (child instanceof Button && !(child instanceof android.widget.CompoundButton)) {
                return (Button) child;
            }

            if (child instanceof ViewGroup) {
                Button found = findButton((ViewGroup) child);

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * The arrow of a row, for the debug smoke test.
     */
    public ImageView getArrowForTesting(String title) {
        View row = findRow(content, title, true);

        if (!(row instanceof ViewGroup)) {
            return null;
        }

        ViewGroup group = (ViewGroup) row;

        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            if (group.getChildAt(i) instanceof ImageView) {
                return (ImageView) group.getChildAt(i);
            }
        }

        return null;
    }

    /**
     * How many groups the choices of a row are spread over. One is what makes them exclusive.
     */
    public int getChoiceGroupsForTesting(String title) {
        View row = findRow(content, title, true);

        if (!(row instanceof ViewGroup) || !(((ViewGroup) row).getParent() instanceof ViewGroup)) {
            return 0;
        }

        ViewGroup holder = (ViewGroup) ((ViewGroup) row).getParent();
        int groups = 0;

        for (int i = 0; i < holder.getChildCount(); i++) {
            if (holder.getChildAt(i) instanceof RadioGroup) {
                groups++;
            }
        }

        return groups;
    }

    /**
     * How many choices of a row are marked. Only ever one.
     */
    public int getMarkedChoicesForTesting(String title) {
        View row = findRow(content, title, true);

        if (!(row instanceof ViewGroup) || !(((ViewGroup) row).getParent() instanceof ViewGroup)) {
            return -1;
        }

        return countChecked((ViewGroup) ((ViewGroup) row).getParent());
    }

    private int countChecked(ViewGroup group) {
        int checked = 0;

        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);

            if (child instanceof RadioButton && ((RadioButton) child).isChecked()) {
                checked++;
            }
            else if (child instanceof ViewGroup) {
                checked += countChecked((ViewGroup) child);
            }
        }

        return checked;
    }

    /**
     * The rows of the menu, for the debug smoke test and for checking that nothing was missed.
     */
    public List<String> getRowTitlesForTesting() {
        List<String> titles = new java.util.ArrayList<>();

        collectRows(content, titles);
        return titles;
    }

    private void collectRows(ViewGroup group, List<String> titles) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);

            if (child instanceof TextView && !(child instanceof RadioButton)
                    && !(child instanceof Button)) {
                titles.add(((TextView) child).getText().toString());
            }
            else if (child instanceof ViewGroup) {
                collectRows((ViewGroup) child, titles);
            }
        }
    }

    /**
     * The view the menu is built on, so a test can look at it on screen.
     */
    public View getView() {
        return root;
    }

    public View getSheetForTesting() {
        return root;
    }

    public Context getContext() {
        return activity;
    }
}
