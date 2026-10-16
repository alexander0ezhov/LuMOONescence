package com.limelight.binding.input.virtual_controller.universal;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;

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
         * The names and ids of the layouts the user has made, or null when there are none to
         * choose from.
         */
        List<UniversalLayoutDefinition> getLayouts();

        String getSelectedLayoutId();

        void onLayoutChosen(String layoutId);
    }

    private final Activity activity;
    private final Host host;

    private final FrameLayout root;
    private final LinearLayout panel;

    private boolean open;

    /**
     * The row that is unfolded, or null when the menu is showing only its rows.
     */
    private LinearLayout expandedBody;

    /**
     * How wide the panel is: a quarter of the screen, so the game stays visible beside it.
     */
    private static final float WIDTH_SHARE = 0.25f;

    private static final int PANEL_WIDTH_MIN_DP = 260;

    /**
     * What sits between the title of a row and what is chosen in it.
     */
    private static final String ROW_SEPARATOR = "   \u00b7   ";

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

        panel.addView(buildTitle(), matchWidth());
        panel.addView(buildScroll(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.addView(buildExitButton(), matchWidth());

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth(), ViewGroup.LayoutParams.MATCH_PARENT);
        panelParams.gravity = Gravity.END;

        root.addView(panel, panelParams);
        parent.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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

    private View buildScroll() {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);

        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(buildKeyboardRow(), matchWidth());
        content.addView(buildTouchModeRow(), matchWidth());
        content.addView(buildLayoutRow(), matchWidth());

        scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return scroll;
    }

    /**
     * The row that brings up the software keyboard. It is not a choice, so it has no arrow.
     */
    private View buildKeyboardRow() {
        TextView row = makeRow(getString(R.string.stream_menu_keyboard), null);

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
     * A row that unfolds into a list of choices, with the chosen one marked.
     */
    private View buildTouchModeRow() {
        final TextView row = makeRow(getString(R.string.stream_menu_touch), touchModeName());
        final LinearLayout body = makeChoiceBody();

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                unfold(body);
            }
        });

        addChoice(body, getString(R.string.stream_menu_touch_off),
                host.getTouchMode() == com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_OFF,
                new Runnable() {
                    @Override
                    public void run() {
                        chooseTouchMode(com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_OFF);
                    }
                });

        addChoice(body, getString(R.string.stream_menu_touch_trackpad),
                host.getTouchMode() == com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_TRACKPAD,
                new Runnable() {
                    @Override
                    public void run() {
                        chooseTouchMode(com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_TRACKPAD);
                    }
                });

        addChoice(body, getString(R.string.stream_menu_touch_screen),
                host.getTouchMode() == com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_TOUCHSCREEN,
                new Runnable() {
                    @Override
                    public void run() {
                        chooseTouchMode(com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_TOUCHSCREEN);
                    }
                });

        addChoice(body, getString(R.string.stream_menu_touch_right_stick),
                host.getTouchMode() == com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_RIGHT_STICK,
                new Runnable() {
                    @Override
                    public void run() {
                        chooseTouchMode(com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_RIGHT_STICK);
                    }
                });

        LinearLayout holder = new LinearLayout(activity);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.addView(row, matchWidth());
        holder.addView(body, matchWidth());

        return holder;
    }

    /**
     * A row that unfolds into the layouts the user has made.
     */
    private View buildLayoutRow() {
        final TextView row = makeRow(getString(R.string.stream_menu_layout), selectedLayoutName());
        final LinearLayout body = makeChoiceBody();

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                unfold(body);
            }
        });

        List<UniversalLayoutDefinition> layouts = host.getLayouts();
        String selectedId = host.getSelectedLayoutId();

        if (layouts == null || layouts.isEmpty()) {
            addChoice(body, getString(R.string.stream_menu_no_layouts), true, null);
        }
        else {
            for (final UniversalLayoutDefinition layout : layouts) {
                addChoice(body, layout.getName(), layout.getId().equals(selectedId),
                        new Runnable() {
                            @Override
                            public void run() {
                                host.onLayoutChosen(layout.getId());
                                close();
                            }
                        });
            }
        }

        LinearLayout holder = new LinearLayout(activity);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.addView(row, matchWidth());
        holder.addView(body, matchWidth());

        return holder;
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
        exit.setLayoutParams(params);

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

    private TextView makeRow(String title, String value) {
        TextView row = new TextView(activity);

        row.setText(value == null ? title : title + "   ·   " + value);
        row.setTextColor(0xFFE8EAF0);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(rowBackground());

        return row;
    }

    private android.graphics.drawable.Drawable rowBackground() {
        StateListDrawable states = new StateListDrawable();

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(0x3DFFFFFF);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0x14FFFFFF);

        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);

        return states;
    }

    private LinearLayout makeChoiceBody() {
        LinearLayout body = new LinearLayout(activity);

        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(View.GONE);

        return body;
    }

    private void addChoice(LinearLayout body, String label, boolean checked, final Runnable action) {
        RadioButton choice = new RadioButton(activity);

        choice.setText(label);
        choice.setTextColor(0xFFD8DBE2);
        choice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        choice.setChecked(checked);
        choice.setPadding(dp(24), dp(4), dp(12), dp(4));

        if (action != null) {
            choice.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    action.run();
                }
            });
        }
        else {
            choice.setEnabled(false);
        }

        // A radio button in a group of its own is only a mark: the choice is applied at once,
        // so nothing has to be read back from a group
        RadioGroup group = new RadioGroup(activity);
        group.addView(choice, matchWidth());
        body.addView(group, matchWidth());
    }

    /**
     * Opens one of the rows and closes whichever other row was open, so only one list is out at
     * a time.
     */
    private void unfold(LinearLayout body) {
        if (expandedBody != null && expandedBody != body) {
            expandedBody.setVisibility(View.GONE);
        }

        body.setVisibility(body.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        expandedBody = body.getVisibility() == View.VISIBLE ? body : null;
    }

    private void chooseTouchMode(int mode) {
        host.onTouchModeChanged(mode);
        close();
    }

    private String touchModeName() {
        int mode = host.getTouchMode();

        if (mode == com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_OFF) {
            return getString(R.string.stream_menu_touch_off);
        }

        if (mode == com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_TRACKPAD) {
            return getString(R.string.stream_menu_touch_trackpad);
        }

        if (mode == com.limelight.preferences.PreferenceConfiguration.TOUCH_MODE_TOUCHSCREEN) {
            return getString(R.string.stream_menu_touch_screen);
        }

        return getString(R.string.stream_menu_touch_right_stick);
    }

    private String selectedLayoutName() {
        List<UniversalLayoutDefinition> layouts = host.getLayouts();
        String selectedId = host.getSelectedLayoutId();

        if (layouts == null) {
            return getString(R.string.stream_menu_no_layouts);
        }

        for (UniversalLayoutDefinition layout : layouts) {
            if (layout.getId().equals(selectedId)) {
                return layout.getName();
            }
        }

        return getString(R.string.stream_menu_no_layouts);
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

        open = true;
        root.setVisibility(View.VISIBLE);

        // Slides in from the edge of the screen
        panel.setTranslationX(panelWidth());
        panel.animate().translationX(0f).setDuration(180).start();
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

    /**
     * Puts the menu back to its rows, with no list unfolded.
     */
    private void close() {
        collapse();
        hide();
    }

    private void collapse() {
        if (expandedBody != null) {
            expandedBody.setVisibility(View.GONE);
            expandedBody = null;
        }
    }

    /**
     * Rebuilds the rows, so the marked choices follow what was chosen.
     */
    public void refresh() {
        // The rows are cheap to rebuild, and rebuilding is what keeps the marks right when the
        // layout or the touch mode is changed from here
        View scroll = panel.getChildAt(1);

        if (scroll instanceof ScrollView) {
            LinearLayout content = (LinearLayout) ((ScrollView) scroll).getChildAt(0);

            if (content != null) {
                content.removeAllViews();
                content.addView(buildKeyboardRow(), matchWidth());
                content.addView(buildTouchModeRow(), matchWidth());
                content.addView(buildLayoutRow(), matchWidth());
            }
        }

        expandedBody = null;
    }

    /**
     * The rows of the menu, for the debug smoke test and for checking that nothing was missed.
     */
    public List<String> getRowTitlesForTesting() {
        List<String> titles = new java.util.ArrayList<>();

        collectRows(panel, titles);
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
     * Presses the row with this title, for the debug smoke test.
     */
    public boolean clickRowForTesting(String title) {
        View row = findRow(panel, title, true);

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
        View choice = findRow(panel, label, false);

        if (choice == null) {
            return false;
        }

        choice.performClick();
        return true;
    }

    /**
     * The label of a row is its title followed by what is chosen, so a row and a choice can be
     * told apart by whether the separator is there.
     */
    private View findRow(ViewGroup group, String label, boolean row) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);

            if (child instanceof TextView) {
                String text = ((TextView) child).getText().toString();
                boolean isRow = text.startsWith(label + ROW_SEPARATOR);

                if (row ? isRow : (child instanceof RadioButton && text.equals(label))) {
                    return child;
                }

                // A row with nothing chosen yet is just its title
                if (row && text.equals(label) && child.isClickable()
                        && !(child instanceof RadioButton)) {
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
     * Puts the menu in place with no animation, for the debug smoke test, which draws it into a
     * picture rather than onto the screen.
     */
    public void settleForTesting() {
        open = true;
        root.setVisibility(View.VISIBLE);
        panel.setTranslationX(0f);
    }

    /**
     * The view the menu is built on, so a test can look at it on screen.
     */
    public View getView() {
        return root;
    }

    /**
     * Whether the sheet that closes the menu covers the whole screen.
     */
    public View getSheetForTesting() {
        return root;
    }

    public Context getContext() {
        return activity;
    }
}
