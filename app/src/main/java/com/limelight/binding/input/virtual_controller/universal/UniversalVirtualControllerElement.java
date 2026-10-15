/**
 * Universal layout: a customizable on-screen layout that can hold both gamepad and
 * keyboard controls.
 *
 * This class is the base of every element of the universal layout. It owns the
 * geometry, the configuration modes (move/resize) and the layout persistence of a
 * single element.
 */

package com.limelight.binding.input.virtual_controller.universal;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.limelight.binding.input.virtual_controller.universal.config.UniversalButtonConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalElementConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalGeometry;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalPadConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalStickConfig;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class UniversalVirtualControllerElement extends View {
    protected static boolean _PRINT_DEBUG_INFORMATION = false;

    /**
     * Element IDs identify an element inside the universal layout. They are also used
     * as the localStorage key of the element geometry, so an ID must never be reused
     * for a different element.
     *
     * Elements are grouped by ID range to keep the categories apart:
     *   1..99    gamepad controls
     *   100..   keyboard keys
     */
    public static final int EID_DPAD = 1;
    public static final int EID_LT = 2;
    public static final int EID_RT = 3;
    public static final int EID_LB = 4;
    public static final int EID_RB = 5;
    public static final int EID_A = 6;
    public static final int EID_B = 7;
    public static final int EID_X = 8;
    public static final int EID_Y = 9;
    public static final int EID_BACK = 10;
    public static final int EID_START = 11;
    public static final int EID_LS = 12;
    public static final int EID_RS = 13;
    public static final int EID_LSB = 14;
    public static final int EID_RSB = 15;
    public static final int EID_GDB = 16;

    /**
     * Keyboard keys start here. The element that owns this ID is decided at runtime by
     * the layout definition, so this ID is not hardcoded to a single key.
     */
    public static final int EID_KEYBOARD_BASE = 100;

    protected UniversalVirtualController controller;
    protected final int elementId;

    /**
     * What this element is and does. Created on first use so subclasses can decide the
     * default that fits them.
     */
    private UniversalElementConfig config;

    private final Paint paint = new Paint();

    private int normalColor = 0xF0888888;
    protected int pressedColor = 0xF00000FF;
    private int configMoveColor = 0xF0FF0000;
    private int configResizeColor = 0xF0FF00FF;
    private int configSelectedColor = 0xF000FF00;
    private int selectedColor = 0xF03D9BFF;

    /**
     * True while the editor is editing this element.
     */
    private boolean selected;

    protected int startSize_x;
    protected int startSize_y;

    float position_pressed_x = 0;
    float position_pressed_y = 0;

    private enum Mode {
        Normal,
        Resize,
        Move
    }

    private Mode currentMode = Mode.Normal;

    protected UniversalVirtualControllerElement(UniversalVirtualController controller, Context context, int elementId) {
        super(context);

        this.controller = controller;
        this.elementId = elementId;
    }

    protected void moveElement(int pressed_x, int pressed_y, int x, int y) {
        int newPos_x = (int) getX() + x - pressed_x;
        int newPos_y = (int) getY() + y - pressed_y;

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        layoutParams.leftMargin = newPos_x > 0 ? newPos_x : 0;
        layoutParams.topMargin = newPos_y > 0 ? newPos_y : 0;
        layoutParams.rightMargin = 0;
        layoutParams.bottomMargin = 0;

        requestLayout();

        // The configuration follows every step of the drag, so anything that reads it while
        // the control is being moved sees where it is
        syncConfigGeometry();
    }

    protected void resizeElement(int pressed_x, int pressed_y, int width, int height) {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        int newHeight = height + (startSize_y - pressed_y);
        int newWidth = width + (startSize_x - pressed_x);

        layoutParams.height = newHeight > 20 ? newHeight : 20;
        layoutParams.width = newWidth > 20 ? newWidth : 20;

        requestLayout();

        syncConfigGeometry();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // The transparency of the layout applies to everything a control draws: its body, its
        // edges, its glow and its label. The layer is skipped when the layout is opaque, which
        // is what avoids paying for it in the common case.
        int alpha = Math.round(elementOpacity * 255f);
        int checkpoint = alpha >= 255 ? -1 : canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), alpha);

        onElementDraw(canvas);

        if (selected) {
            // The editor marks the control it is editing, so it is obvious which one the
            // panel on the left belongs to.
            paint.setColor(selectedColor);
            paint.setStrokeWidth(getDefaultStrokeWidth());
            paint.setStyle(Paint.Style.STROKE);

            canvas.drawRect(paint.getStrokeWidth(), paint.getStrokeWidth(),
                    getWidth()-paint.getStrokeWidth(), getHeight()-paint.getStrokeWidth(),
                    paint);
        }

        if (currentMode != Mode.Normal) {
            paint.setColor(configSelectedColor);
            paint.setStrokeWidth(getDefaultStrokeWidth());
            paint.setStyle(Paint.Style.STROKE);

            canvas.drawRect(paint.getStrokeWidth(), paint.getStrokeWidth(),
                    getWidth()-paint.getStrokeWidth(), getHeight()-paint.getStrokeWidth(),
                    paint);
        }

        if (checkpoint >= 0) {
            canvas.restoreToCount(checkpoint);
        }

        super.onDraw(canvas);
    }

    /**
     * The transparency of this control, 0..1. It comes from one setting for the whole layout.
     */
    public void setElementOpacity(float opacity) {
        float clamped = Math.max(0.05f, Math.min(1f, opacity));

        if (Math.abs(clamped - elementOpacity) < 0.001f) {
            return;
        }

        elementOpacity = clamped;
        invalidate();
    }

    public float getElementOpacity() {
        return elementOpacity;
    }

    private float elementOpacity = 1f;

    /**
     * Marks this element as the one the editor is editing.
     */
    public void setSelected(boolean selected) {
        if (this.selected == selected) {
            return;
        }

        this.selected = selected;
        invalidate();
    }

    public boolean isSelected() {
        return selected;
    }

    /**
     * A hold that is being watched. A control that is held down long enough asks the
     * editor for its context menu; a shorter hold is just a tap.
     */
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!longPressPending) {
                // The press ended before the hold was long enough
                return;
            }

            longPressPending = false;
            longPressHandled = notifyLongPressed();
        }
    };

    private boolean longPressPending;
    private boolean longPressHandled;

    public static final long LONG_PRESS_TIMEOUT_MS = 500;

    /**
     * Starts watching a press, so holding a control opens its context menu.
     */
    protected void beginPressWatch() {
        longPressPending = true;
        longPressHandled = false;

        controller.getUiHandler().postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS);
    }

    /**
     * Ends a press and returns whether it lasted long enough to be handled as a hold.
     *
     * The element is selected here rather than when the press starts, so a control can
     * still be pressed and released to try it out without the selection moving under the
     * user's finger.
     *
     * @param tap true when the press ended normally, false when the gesture was cancelled
     */
    protected boolean endPressWatch(boolean tap) {
        controller.getUiHandler().removeCallbacks(longPressRunnable);

        boolean wasPending = longPressPending;
        longPressPending = false;

        boolean handled = longPressHandled;
        longPressHandled = false;

        if (tap && wasPending && !handled) {
            // A short press is a tap, which selects the control for editing
            notifyTapped();
        }

        return handled;
    }

    /**
     * True while a hold has already been handled by the editor and the control should not
     * send anything for it.
     */
    protected boolean isLongPressHandled() {
        return longPressHandled;
    }

    /**
     * Tells the controller that this element was tapped, so the editor can select it.
     * Nothing happens during a stream.
     */
    protected void notifyTapped() {
        if (controller != null) {
            controller.notifyElementTapped(this);
        }
    }

    /**
     * Tells the controller that this element was held.
     *
     * @return true when the editor handled the hold, in which case the element must not
     *         send its configured action
     */
    protected boolean notifyLongPressed() {
        return controller != null && controller.notifyElementLongPressed(this);
    }

    protected void actionEnableMove() {
        currentMode = Mode.Move;
    }

    protected void actionEnableResize() {
        currentMode = Mode.Resize;
    }

    protected void actionCancel() {
        boolean wasConfiguring = currentMode != Mode.Normal;

        currentMode = Mode.Normal;
        invalidate();

        if (wasConfiguring && controller != null) {
            // The element was just moved or resized, so anything showing its geometry can
            // bring itself back in step.
            controller.notifyElementGeometrySettled(this);
        }
    }

    protected int getDefaultColor() {
        if (controller.getControllerMode() == UniversalVirtualController.ControllerMode.MoveButtons)
            return configMoveColor;
        else if (controller.getControllerMode() == UniversalVirtualController.ControllerMode.ResizeButtons)
            return configResizeColor;
        else
            return normalColor;
    }

    // ---------------------------------------------------------------- appearance

    /**
     * The look of a control: a dark body under a light edge, with a soft white glow inside
     * when it is active.
     *
     * The same colours are used by every kind of control, so a layout looks like one set
     * rather than a collection of different widgets.
     */
    protected static final int BODY_TOP = 0xC0333840;
    protected static final int BODY_BOTTOM = 0xD01A1D23;
    protected static final int EDGE_NORMAL = 0x8AC8CDD6;

    /**
     * An active control keeps exactly the transparency of an inactive one.
     *
     * The body of a control is translucent, so the game shows through it. Making an active
     * body more opaque would make it look darker over a bright game, which is the opposite of
     * what being pressed should look like; only the shade, the edge and the glow change.
     */
    protected static final int BODY_TOP_ACTIVE = 0xC03E4450;
    protected static final int BODY_BOTTOM_ACTIVE = 0xD022262E;
    protected static final int EDGE_ACTIVE = 0xE0FFFFFF;

    /**
     * The glow is a pool of light just inside the edge of an active control.
     *
     * It is clear in the middle and lit at the edge, which is what makes it read as light
     * rather than as a thicker border. It is kept strong enough to be obvious while playing,
     * where a control is under a thumb and is seen out of the corner of an eye.
     */
    /**
     * Each band is faint, and there are many of them, because a band is a step: few strong
     * bands would show as rings. Stacked up they reach about half opacity at the edge and fade
     * to a single band inwards.
     */
    private static final int GLOW_BAND = 0x0CFFFFFF;

    /**
     * How many bands the light is made of. More bands make a smoother fade; the bands are what
     * lets the light follow the outline of a shape of any form.
     */
    private static final int GLOW_STEPS = 16;

    /**
     * How far the light reaches into a control, as a share of its shorter side. About a half
     * lights the whole control rather than only its border.
     */
    private static final float GLOW_DEPTH_SHARE = 0.46f;

    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shapeRect = new RectF();

    /**
     * Draws one part of a control: a rounded body with a light edge, and a soft glow inside
     * while it is active.
     *
     * @param active whether the part is pressed, toggled on or currently pushed
     */
    protected void drawButtonShape(Canvas canvas, float left, float top, float right,
                                   float bottom, float radius, boolean active) {
        if (right - left <= 0 || bottom - top <= 0) {
            return;
        }

        shapeRect.set(left, top, right, bottom);

        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setStrokeWidth(0);
        shapePaint.setAlpha(255);
        shapePaint.setShader(new LinearGradient(0, top, 0, bottom,
                active ? BODY_TOP_ACTIVE : BODY_TOP,
                active ? BODY_BOTTOM_ACTIVE : BODY_BOTTOM,
                Shader.TileMode.CLAMP));

        canvas.drawRoundRect(shapeRect, radius, radius, shapePaint);

        // The light is laid over the body, so it fades inwards from the edge
        if (active) {
            glowShape.reset();
            glowShape.addRoundRect(shapeRect, radius, radius, Path.Direction.CW);
            drawInnerGlow(canvas, glowShape, radius, shapeRect);
        }

        shapePaint.setShader(null);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(Math.max(1.5f, getDefaultStrokeWidth() * 0.45f));
        shapePaint.setColor(active ? EDGE_ACTIVE : EDGE_NORMAL);

        canvas.drawRoundRect(shapeRect, radius, radius, shapePaint);
    }

    /**
     * Draws one part of a control that is not a rectangle, for example one arm of a D-pad.
     *
     * The colours, the glow and the rounding are the same as for a rectangular part, so a
     * control made of several shapes still looks like one thing.
     */
    protected void drawButtonPath(Canvas canvas, Path path, boolean active, float cornerRadius) {
        pathBounds.setEmpty();
        path.computeBounds(pathBounds, true);

        if (pathBounds.width() <= 0 || pathBounds.height() <= 0) {
            return;
        }

        // The corners of a path are sharp, so they are rounded by a path effect for the
        // duration of this shape
        CornerPathEffect corners = cornerRadius > 0
                ? new CornerPathEffect(cornerRadius) : null;

        if (active) {
            drawInnerGlow(canvas, path, cornerRadius, pathBounds);
        }

        shapePaint.setPathEffect(corners);
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setStrokeWidth(0);
        shapePaint.setAlpha(255);
        shapePaint.setShader(new LinearGradient(0, pathBounds.top, 0, pathBounds.bottom,
                active ? BODY_TOP_ACTIVE : BODY_TOP,
                active ? BODY_BOTTOM_ACTIVE : BODY_BOTTOM,
                Shader.TileMode.CLAMP));

        canvas.drawPath(path, shapePaint);

        shapePaint.setShader(null);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(Math.max(1.5f, getDefaultStrokeWidth() * 0.45f));
        shapePaint.setColor(active ? EDGE_ACTIVE : EDGE_NORMAL);

        canvas.drawPath(path, shapePaint);
        shapePaint.setPathEffect(null);
    }

    private final RectF pathBounds = new RectF();

    private final Path glowShape = new Path();

    /**
     * Draws the light of an active control over its body.
     *
     * The light follows the outline of the shape: it is a stack of bands along the edge, each
     * one a little wider and fainter than the last, so it pools against the border and fades
     * inwards. The bands are clipped to the shape, so no light spills outside it.
     *
     * A radial gradient cannot do this. It is a function of the distance from the middle, so
     * it paints a circle whatever the shape is, which on a wide button leaves the ends of the
     * button unlit.
     */
    private void drawInnerGlow(Canvas canvas, Path shape, float cornerRadius, RectF bounds) {
        // The light reaches most of the way to the middle of the shape, so it covers the whole
        // control rather than sitting on its edge as a rim
        float depth = Math.min(bounds.width(), bounds.height()) * GLOW_DEPTH_SHARE;

        canvas.save();
        canvas.clipPath(shape);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setShader(null);
        glowPaint.setAlpha(255);
        glowPaint.setColor(GLOW_BAND);
        glowPaint.setPathEffect(cornerRadius > 0 ? new CornerPathEffect(cornerRadius) : null);

        // Widest band first: every one of them is centred on the outline and clipped to the
        // shape, so only the inner half of each shows and the bands stack up against the edge
        for (int step = GLOW_STEPS; step >= 1; step--) {
            glowPaint.setStrokeWidth(depth * 2f * step / GLOW_STEPS);
            canvas.drawPath(shape, glowPaint);
        }

        glowPaint.setPathEffect(null);
        canvas.restore();
    }

    /**
     * The corner radius of a control, as a share of its shorter side. Half the shorter side
     * is a full circle or a pill, which is what the control's own shape defaults to.
     */
    protected float getCornerRadius() {
        float share = getConfig().getBorderRadius();
        int shorter = Math.min(getWidth(), getHeight());
        return shorter * share / 100f;
    }

    protected int getDefaultStrokeWidth() {
        DisplayMetrics screen = getResources().getDisplayMetrics();
        return (int)(screen.heightPixels*0.004f);
    }

    protected void showConfigurationDialog() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getContext());

        alertBuilder.setTitle("Configuration");

        CharSequence functions[] = new CharSequence[]{
                "Move",
                "Resize",
                "Cancel"
        };

        alertBuilder.setItems(functions, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: { // move
                        actionEnableMove();
                        break;
                    }
                    case 1: { // resize
                        actionEnableResize();
                        break;
                    }
                    default: { // cancel
                        actionCancel();
                        break;
                    }
                }
            }
        });
        AlertDialog alert = alertBuilder.create();
        // show menu
        alert.show();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Ignore secondary touches on controls
        //
        // NB: We can get an additional pointer down if the user touches a non-StreamView area
        // while also touching an OSC control, even if that pointer down doesn't correspond to
        // an area of the OSC control.
        if (event.getActionIndex() != 0) {
            return true;
        }

        // The press is watched around the element's own gesture handling, whichever mode the
        // layout is in: in the editor a tap selects the control and a hold opens its context
        // menu, and neither may be skipped just because the layout is also being arranged.
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            beginPressWatch();
            pressWatchActive = true;
        }
        else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && pressWatchActive) {
            // A press that has moved is a drag, not a tap, so it must not select
            cancelPressWatch();
            pressWatchActive = false;
        }

        boolean handled;

        if (controller.getControllerMode() == UniversalVirtualController.ControllerMode.Active) {
            handled = onElementTouchEvent(event);
        }
        else {
            handled = handleArrangementTouchEvent(event);
        }

        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL ||
                event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (pressWatchActive) {
                endPressWatch(event.getActionMasked() == MotionEvent.ACTION_UP);
            }
            pressWatchActive = false;
        }

        return handled;
    }

    /**
     * True while this element is watching a press that it started itself.
     */
    private boolean pressWatchActive;

    /**
     * Handles a touch as an arrangement gesture: moving the control, or resizing it.
     */
    private boolean handleArrangementTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                position_pressed_x = event.getX();
                position_pressed_y = event.getY();
                startSize_x = getWidth();
                startSize_y = getHeight();

                if (controller.getControllerMode() == UniversalVirtualController.ControllerMode.MoveButtons)
                    actionEnableMove();
                else if (controller.getControllerMode() == UniversalVirtualController.ControllerMode.ResizeButtons)
                    actionEnableResize();

                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                switch (currentMode) {
                    case Move: {
                        moveElement(
                                (int) position_pressed_x,
                                (int) position_pressed_y,
                                (int) event.getX(),
                                (int) event.getY());
                        break;
                    }
                    case Resize: {
                        resizeElement(
                                (int) position_pressed_x,
                                (int) position_pressed_y,
                                (int) event.getX(),
                                (int) event.getY());
                        break;
                    }
                    case Normal: {
                        break;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                actionCancel();
                return true;
            }
            default: {
            }
        }

        return true;
    }

    /**
     * Cancels a watched press without reporting it as a tap.
     */
    protected void cancelPressWatch() {
        controller.getUiHandler().removeCallbacks(longPressRunnable);
        longPressPending = false;
        longPressHandled = false;
    }

    abstract protected void onElementDraw(Canvas canvas);

    abstract public boolean onElementTouchEvent(MotionEvent event);

    protected static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            System.out.println(text);
        }
    }

    public void setColors(int normalColor, int pressedColor) {
        this.normalColor = normalColor;
        this.pressedColor = pressedColor;

        invalidate();
    }


    protected final float getPercent(float value, float percent) {
        return value / 100 * percent;
    }

    protected final int getCorrectWidth() {
        return getWidth() > getHeight() ? getHeight() : getWidth();
    }

    /**
     * Returns the localStorage key of this element. The universal layout keeps its
     * geometry in its own preferences file, so element IDs don't collide with the
     * geometry saved by the legacy OSC layout.
     */
    public String getPreferenceKey() {
        return "UNIVERSAL_" + elementId;
    }

    /**
     * Kind of element, stored with its configuration so a layout can be rebuilt from the
     * saved file even when the built-in layout no longer defines the element.
     */
    public String getElementTypeName() {
        return UniversalElementTypes.ELEMENT_BASE;
    }

    /**
     * Stable ID of this element, which is also what its saved configuration is keyed by.
     */
    public int getElementId() {
        return elementId;
    }

    /**
     * Returns this element's configuration, creating the default one on first use.
     */
    public UniversalElementConfig getConfig() {
        if (config == null) {
            config = createDefaultConfig();
            config.setName(getElementTypeName() + " " + elementId);
        }

        return config;
    }

    protected UniversalElementConfig createDefaultConfig() {
        return new UniversalElementConfig();
    }

    /**
     * Returns a copy of this element's configuration, used when it is duplicated.
     */
    public UniversalElementConfig createConfigCopy() {
        return getConfig().copy();
    }

    /**
     * The configuration of this element seen as a button action, or null when this element
     * is not a button. Triggers are buttons that report through a trigger axis, so they
     * answer here as well.
     */
    public UniversalButtonConfig getButtonConfigIfAny() {
        UniversalElementConfig config = getConfig();
        return config instanceof UniversalButtonConfig ? (UniversalButtonConfig) config : null;
    }

    /**
     * The configuration of this element seen as a stick, or null when this element is not
     * a stick.
     */
    public UniversalStickConfig getStickConfigIfAny() {
        UniversalElementConfig config = getConfig();
        return config instanceof UniversalStickConfig ? (UniversalStickConfig) config : null;
    }

    /**
     * The configuration of this element seen as a D-pad, or null when this element is not
     * a pad.
     */
    public UniversalPadConfig getPadConfigIfAny() {
        UniversalElementConfig config = getConfig();
        return config instanceof UniversalPadConfig ? (UniversalPadConfig) config : null;
    }

    /**
     * Replaces the configuration and writes its geometry onto the view.
     */
    public void applyConfig(UniversalElementConfig newConfig) {
        if (newConfig == null) {
            return;
        }

        this.config = newConfig;
        newConfig.getGeometry().applyTo(this);
        invalidate();
    }

    public String getElementName() {
        return getConfig().getName();
    }

    public void setElementName(String name) {
        getConfig().setName(name);
    }

    /**
     * Releases anything this element is currently sending to the host.
     *
     * Elements that can hold input (a latching button, a pushed stick) override this, so
     * that saving or tearing the layout down never leaves a key held on the host.
     */
    public void releaseConfiguredActions() {
    }

    public JSONObject getConfiguration() throws JSONException {
        // A save writes what the layout looks like, so the configuration is brought in step
        // with the screen first. Moving or resizing a control changes the view, and the
        // configuration is what gets written, so without this a moved control would be saved
        // back at the place it was dragged away from.
        syncConfigGeometry();

        JSONObject configuration = getConfig().toJson();
        configuration.put(UniversalElementTypes.KEY_TYPE, getElementTypeName());
        return configuration;
    }

    /**
     * Makes the configuration agree with where the control actually is on screen.
     */
    public void syncConfigGeometry() {
        if (!(getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return;
        }

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        if (params.width <= 0 || params.height <= 0) {
            return;
        }

        // The geometry object is changed rather than replaced, because the editor keeps a
        // reference to it while a control is being edited
        UniversalGeometry geometry = getConfig().getGeometry();
        geometry.setLeft(params.leftMargin);
        geometry.setTop(params.topMargin);
        geometry.setWidth(params.width);
        geometry.setHeight(params.height);
    }

    /**
     * Applies a saved configuration. Configs written before the configuration model
     * existed only carry geometry, which {@link UniversalElementConfig#loadFromJson}
     * still understands.
     */
    public void loadConfiguration(JSONObject configuration) throws JSONException {
        UniversalElementConfig loaded = createDefaultConfig();
        loaded.loadFromJson(configuration);

        applyConfig(loaded);
    }
}
