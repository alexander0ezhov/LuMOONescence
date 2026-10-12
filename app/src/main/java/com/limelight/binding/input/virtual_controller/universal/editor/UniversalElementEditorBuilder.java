package com.limelight.binding.input.virtual_controller.universal.editor;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;

import com.limelight.R;
import com.limelight.binding.input.virtual_controller.universal.UniversalDigitalPad;
import com.limelight.binding.input.virtual_controller.universal.UniversalVirtualControllerElement;
import com.limelight.binding.input.virtual_controller.universal.action.ActionSequence;
import com.limelight.binding.input.virtual_controller.universal.action.ActionStep;
import com.limelight.binding.input.virtual_controller.universal.action.GamepadButtons;
import com.limelight.binding.input.virtual_controller.universal.action.InputEvent;
import com.limelight.binding.input.virtual_controller.universal.action.InputEventNames;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalButtonConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalGeometry;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalHostAxis;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalPadConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalStickButtonConfig;
import com.limelight.binding.input.virtual_controller.universal.config.UniversalStickConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Fills the editing panel with the fields that fit the selected control.
 *
 * A button and a stick are configured in completely different terms, so each kind gets
 * its own set of rows here. The geometry rows are shared, because every control has a
 * position and a size.
 */
public class UniversalElementEditorBuilder {
    /**
     * Largest position a slider offers. A control beyond this is off any screen, and the
     * input field still accepts any value, so this is only the slider's range.
     */
    private static final int MAX_POSITION = 4096;

    private static final int MAX_SIZE = 1024;

    /**
     * Position of the delay row in the layout order: the panel reports a delay before the
     * first step is added, so it needs a step to attach it to.
     */
    public interface Callbacks {
        /**
         * The control's configuration changed and the change was already applied to it.
         */
        void onElementChanged();

        void onGeometryChanged(int left, int top, int width, int height);

        void onNameChanged(String name);

        /**
         * The corner radius changed, as a share of the shorter side.
         */
        void onBorderRadiusChanged(int percent);
    }

    private final Context context;
    private final UniversalEditorPanel panel;

    /**
     * Rebuilds the whole panel. Adding or removing a step or an event changes how many
     * rows the panel has, so the editor rebuilds it rather than trying to patch it.
     */
    private Runnable onRebuildRequested = new Runnable() {
        @Override
        public void run() {
        }
    };

    public UniversalElementEditorBuilder(Context context, UniversalEditorPanel panel) {
        this.context = context;
        this.panel = panel;
    }

    public void setOnRebuildRequested(Runnable runnable) {
        this.onRebuildRequested = runnable;
    }

    public void build(UniversalVirtualControllerElement element, final Callbacks callbacks) {
        buildControlSection(element, callbacks);
        buildGeometrySection(element, callbacks);

        if (element.getPadConfigIfAny() != null) {
            buildPadSection(element, callbacks);
            return;
        }

        if (element.getStickConfigIfAny() != null) {
            buildStickSection(element, callbacks);
            return;
        }

        if (element.getButtonConfigIfAny() != null) {
            buildButtonSection(element, callbacks);
        }
    }

    private void buildControlSection(UniversalVirtualControllerElement element,
                                     final Callbacks callbacks) {
        panel.addSection(context.getString(R.string.editor_section_element));

        panel.addTextField(context.getString(R.string.editor_label_name), element.getElementName(),
                new UniversalEditorPanel.OnTextChangedListener() {
                    @Override
                    public void onTextChanged(String text) {
                        callbacks.onNameChanged(text);
                    }
                });

        // How round the control is drawn. It is a share of the shorter side, so the shape
        // stays the same whatever the control is resized to.
        panel.addSliderRow(context.getString(R.string.editor_label_corner_radius),
                element.getConfig().getBorderRadius(), MAX_BORDER_RADIUS,
                new UniversalEditorPanel.OnValueChangedListener() {
                    @Override
                    public void onValueChanged(int value) {
                        callbacks.onBorderRadiusChanged(value);
                    }
                });
    }

    /**
     * Half of the shorter side is a full circle, so that is the top of the range.
     */
    private static final int MAX_BORDER_RADIUS = 50;

    private void buildGeometrySection(UniversalVirtualControllerElement element,
                                      final Callbacks callbacks) {
        panel.addSection(context.getString(R.string.editor_section_position));

        // The position comes from what is on screen rather than from the configuration,
        // because dragging a control changes the view first. Reading the view is what keeps
        // the sliders from undoing a drag with a stale value.
        final UniversalGeometry geometry = readGeometry(element);

        panel.addSliderRow(context.getString(R.string.editor_label_x), geometry.getLeft(),
                MAX_POSITION, new UniversalEditorPanel.OnValueChangedListener() {
                    @Override
                    public void onValueChanged(int value) {
                        callbacks.onGeometryChanged(value, geometry.getTop(),
                                geometry.getWidth(), geometry.getHeight());
                    }
                });

        panel.addSliderRow(context.getString(R.string.editor_label_y), geometry.getTop(),
                MAX_POSITION, new UniversalEditorPanel.OnValueChangedListener() {
                    @Override
                    public void onValueChanged(int value) {
                        callbacks.onGeometryChanged(geometry.getLeft(), value,
                                geometry.getWidth(), geometry.getHeight());
                    }
                });

        panel.addSliderRow(context.getString(R.string.editor_label_width), geometry.getWidth(),
                MAX_SIZE, new UniversalEditorPanel.OnValueChangedListener() {
                    @Override
                    public void onValueChanged(int value) {
                        callbacks.onGeometryChanged(geometry.getLeft(), geometry.getTop(),
                                value, geometry.getHeight());
                    }
                });

        panel.addSliderRow(context.getString(R.string.editor_label_height), geometry.getHeight(),
                MAX_SIZE, new UniversalEditorPanel.OnValueChangedListener() {
                    @Override
                    public void onValueChanged(int value) {
                        callbacks.onGeometryChanged(geometry.getLeft(), geometry.getTop(),
                                geometry.getWidth(), value);
                    }
                });
    }

    /**
     * Returns the geometry of a control as it currently is on screen, falling back to the
     * configured value while the control has not been laid out yet.
     */
    private static UniversalGeometry readGeometry(UniversalVirtualControllerElement element) {
        android.view.ViewGroup.LayoutParams params = element.getLayoutParams();

        if (params != null && params.width > 0 && params.height > 0) {
            return UniversalGeometry.of(element);
        }

        return element.getConfig().getGeometry().copy();
    }

    // ---------------------------------------------------------------- buttons

    private void buildButtonSection(final UniversalVirtualControllerElement element,
                                    final Callbacks callbacks) {
        panel.addSection(context.getString(R.string.editor_section_action));

        final UniversalButtonConfig config = element.getButtonConfigIfAny();

        panel.addCheckBox(context.getString(R.string.editor_label_toggle), config.isToggle(),
                new UniversalEditorPanel.OnCheckedListener() {
                    @Override
                    public void onCheckedChanged(boolean checked) {
                        // Rebuilding the panel re-creates this row, so only a real change is
                        // reported, otherwise a rebuild would mark the layout as edited.
                        if (config.isToggle() == checked) {
                            return;
                        }

                        config.setToggle(checked);
                        callbacks.onElementChanged();
                    }
                });

        buildSequenceEditor(config.getActionSequence(), callbacks);
    }

    /**
     * Builds the event sequence editor for a button or for one direction of a pad.
     */
    private void buildSequenceEditor(final ActionSequence sequence, final Callbacks callbacks) {
        panel.addText(context.getString(R.string.editor_label_sequence));

        if (sequence.getSteps().isEmpty()) {
            panel.addStatus(context.getString(R.string.editor_label_sequence_empty));
        }

        for (int stepIndex = 0; stepIndex < sequence.getSteps().size(); stepIndex++) {
            final int index = stepIndex;
            final ActionStep step = sequence.getSteps().get(stepIndex);

            panel.addText(context.getString(R.string.editor_step_title, stepIndex + 1));

            // Delay after this step, which is what makes a pattern like
            // "right button, wait 200 ms, left button" possible.
            panel.addSliderRow(context.getString(R.string.editor_label_step_delay),
                    (int) step.getDelayMs(), 2000,
                    new UniversalEditorPanel.OnValueChangedListener() {
                        @Override
                        public void onValueChanged(int value) {
                            step.setDelayMs(value);
                            callbacks.onElementChanged();
                        }
                    });

            for (int eventIndex = 0; eventIndex < step.getEvents().size(); eventIndex++) {
                final int removeIndex = eventIndex;
                final InputEvent event = step.getEvents().get(eventIndex);

                panel.addButtonRow(
                        new String[]{InputEventNames.describe(event),
                                context.getString(R.string.editor_remove_event)},
                        new View.OnClickListener[]{
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        editEvent(step, removeIndex, callbacks);
                                    }
                                },
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        step.getEvents().remove(removeIndex);
                                        callbacks.onElementChanged();
                                        onRebuildRequested.run();
                                    }
                                },
                        });
            }

            panel.addButtonRow(
                    new String[]{context.getString(R.string.editor_add_event),
                            context.getString(R.string.editor_remove_step)},
                    new View.OnClickListener[]{
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    addEvent(step, callbacks);
                                }
                            },
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    sequence.getSteps().remove(index);
                                    callbacks.onElementChanged();
                                    onRebuildRequested.run();
                                }
                            },
                    });
        }

        panel.addButton(context.getString(R.string.editor_add_step), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sequence.addStep(new ActionStep());
                callbacks.onElementChanged();
                onRebuildRequested.run();
            }
        });
    }

    private void addEvent(final ActionStep step, final Callbacks callbacks) {
        final String[] kinds = new String[]{
                context.getString(R.string.editor_device_keyboard),
                context.getString(R.string.editor_device_mouse),
                context.getString(R.string.editor_device_gamepad),
        };

        new AlertDialog.Builder(context)
                .setTitle(R.string.editor_pick_device)
                .setItems(kinds, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                pickKeyboardKey(step, callbacks);
                                break;

                            case 1:
                                pickFromList(context.getString(R.string.editor_pick_button),
                                        describeAll(InputEventNames.mouseCatalog()),
                                        InputEventNames.mouseCatalog(),
                                        new EventConsumer() {
                                            @Override
                                            public void accept(InputEvent event) {
                                                step.addEvent(event);
                                                callbacks.onElementChanged();
                                                onRebuildRequested.run();
                                            }
                                        }, callbacks);
                                break;

                            default:
                                pickGamepadButton(step, callbacks);
                                break;
                        }
                    }
                })
                .show();
    }

    private void editEvent(final ActionStep step, final int eventIndex, final Callbacks callbacks) {
        final InputEvent existing = step.getEvents().get(eventIndex);

        if (existing.isKeyboard()) {
            List<InputEvent> catalog = InputEventNames.keyboardCatalog();
            pickFromList(context.getString(R.string.editor_pick_key), describeAll(catalog), catalog,
                    new EventConsumer() {
                        @Override
                        public void accept(InputEvent event) {
                            step.getEvents().set(eventIndex, event);
                            callbacks.onElementChanged();
                        }
                    }, callbacks);
            return;
        }

        if (existing.isMouse()) {
            List<InputEvent> catalog = InputEventNames.mouseCatalog();
            pickFromList(context.getString(R.string.editor_pick_button), describeAll(catalog), catalog,
                    new EventConsumer() {
                        @Override
                        public void accept(InputEvent event) {
                            step.getEvents().set(eventIndex, event);
                            callbacks.onElementChanged();
                        }
                    }, callbacks);
            return;
        }

        pickGamepadButton(new EventConsumer() {
            @Override
            public void accept(InputEvent event) {
                step.getEvents().set(eventIndex, event);
                callbacks.onElementChanged();
            }
        }, callbacks);
    }

    private interface EventConsumer {
        void accept(InputEvent event);
    }

    private void pickKeyboardKey(final ActionStep step, final Callbacks callbacks) {
        List<InputEvent> catalog = InputEventNames.keyboardCatalog();
        pickFromList(context.getString(R.string.editor_pick_key), describeAll(catalog), catalog,
                new EventConsumer() {
                    @Override
                    public void accept(InputEvent event) {
                        step.addEvent(event);
                        callbacks.onElementChanged();
                        onRebuildRequested.run();
                    }
                }, callbacks);
    }

    private void pickGamepadButton(final ActionStep step, final Callbacks callbacks) {
        pickGamepadButton(new EventConsumer() {
            @Override
            public void accept(InputEvent event) {
                step.addEvent(event);
                callbacks.onElementChanged();
                onRebuildRequested.run();
            }
        }, callbacks);
    }

    private void pickGamepadButton(final EventConsumer consumer, final Callbacks callbacks) {
        List<InputEvent> catalog = gamepadCatalog();
        pickFromList(context.getString(R.string.editor_pick_button), describeAll(catalog), catalog,
                new EventConsumer() {
                    @Override
                    public void accept(InputEvent event) {
                        consumer.accept(event);
                        onRebuildRequested.run();
                    }
                }, callbacks);
    }

    /**
     * Everything a press can send: the gamepad buttons and the analog triggers.
     */
    private static List<InputEvent> gamepadCatalog() {
        List<InputEvent> events = new ArrayList<>(buttonCodeCatalog());

        // The analog triggers are part of the same controller, so they are offered alongside
        // its buttons. They cannot be a button flag, which is why they are their own event
        // kind.
        events.addAll(InputEventNames.triggerCatalog());

        return events;
    }

    /**
     * The gamepad buttons alone, for the places that store a button code rather than an
     * event: a stick direction and the middle of a stick press a button, not a trigger.
     */
    private static List<InputEvent> buttonCodeCatalog() {
        List<InputEvent> events = new ArrayList<>();
        for (GamepadButtons.Button button : GamepadButtons.all()) {
            if (button.code != GamepadButtons.CODE_NONE) {
                events.add(new InputEvent(InputEvent.KIND_GAMEPAD, button.code));
            }
        }

        return events;
    }

    private static List<String> describeAll(List<InputEvent> events) {
        List<String> names = new ArrayList<>();
        for (InputEvent event : events) {
            names.add(InputEventNames.describe(event));
        }
        return names;
    }

    private void pickFromList(String title, List<String> names, final List<InputEvent> events,
                              final EventConsumer consumer, final Callbacks callbacks) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setItems(names.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        consumer.accept(events.get(which));
                    }
                })
                .show();
    }

    // ---------------------------------------------------------------- sticks

    private void buildStickSection(UniversalVirtualControllerElement element,
                                   final Callbacks callbacks) {
        panel.addSection(context.getString(R.string.editor_section_action));

        final UniversalStickConfig config = element.getStickConfigIfAny();

        List<String> modes = new ArrayList<>();
        modes.add(context.getString(R.string.editor_mode_axes));
        modes.add(context.getString(R.string.editor_mode_buttons));

        panel.addSpinner(context.getString(R.string.editor_label_control_mode), modes,
                config.isAxesMode() ? 0 : 1, new UniversalEditorPanel.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(int position) {
                        int newMode = position == 0
                                ? UniversalStickConfig.MODE_AXES : UniversalStickConfig.MODE_BUTTONS;

                        // A spinner reports its initial selection as well, so a rebuild must
                        // not be mistaken for the user switching the mode.
                        if (config.getMode() == newMode) {
                            return;
                        }

                        config.setMode(newMode);
                        callbacks.onElementChanged();
                        onRebuildRequested.run();
                    }
                });

        if (config.isAxesMode()) {
            buildAxisSpinner(context.getString(R.string.editor_label_axis_x), config.getAxisX(),
                    new AxisConsumer() {
                        @Override
                        public void accept(int axisCode) {
                            if (config.getAxisX() == axisCode) {
                                return;
                            }

                            config.setAxisX(axisCode);
                            callbacks.onElementChanged();
                        }
                    });

            buildAxisSpinner(context.getString(R.string.editor_label_axis_y), config.getAxisY(),
                    new AxisConsumer() {
                        @Override
                        public void accept(int axisCode) {
                            if (config.getAxisY() == axisCode) {
                                return;
                            }

                            config.setAxisY(axisCode);
                            callbacks.onElementChanged();
                        }
                    });

            buildCenterButtonSection(config, callbacks);
            return;
        }

        // Buttons mode: one gamepad button per direction, so a stick can act as a D-pad
        int[] labels = new int[]{
                R.string.editor_label_direction_up,
                R.string.editor_label_direction_down,
                R.string.editor_label_direction_left,
                R.string.editor_label_direction_right,
        };

        for (int direction = 0; direction < UniversalStickConfig.DIRECTION_COUNT; direction++) {
            final UniversalStickButtonConfig button = config.getButton(direction);
            final int directionIndex = direction;

            List<InputEvent> catalog = buttonCodeCatalog();
            List<String> names = describeAll(catalog);

            // The stored value is a button code, so the selected entry is looked up by code
            int selected = 0;
            for (int i = 0; i < catalog.size(); i++) {
                if (catalog.get(i).getCode() == button.getButtonCode()) {
                    selected = i;
                    break;
                }
            }

            panel.addSpinner(context.getString(labels[direction]), names, selected,
                    new UniversalEditorPanel.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(int position) {
                            int newCode = buttonCodeCatalog().get(position).getCode();

                            // A spinner reports its initial selection as well
                            if (button.getButtonCode() == newCode) {
                                return;
                            }

                            button.setButtonCode(newCode);
                            callbacks.onElementChanged();

                            // Assigning a button adds its action editor to the panel
                            onRebuildRequested.run();
                        }
                    });

            if (button.isAssigned()) {
                buildSequenceEditor(button.getActionSequence(), callbacks);
            }
        }

        buildCenterButtonSection(config, callbacks);
    }

    /**
     * The middle of the stick, which is the stick click on a real controller.
     *
     * The switch turns the button on or off, and once it is on the binding is chosen and
     * edited exactly like a direction binding, so what a press of the middle sends is set up
     * the same way as everything else on the stick.
     */
    private void buildCenterButtonSection(final UniversalStickConfig config,
                                         final Callbacks callbacks) {
        panel.addSection(context.getString(R.string.editor_section_center_button));

        panel.addCheckBox(context.getString(R.string.editor_label_center_button_enabled),
                config.isCenterButtonEnabled(), new UniversalEditorPanel.OnCheckedListener() {
                    @Override
                    public void onCheckedChanged(boolean checked) {
                        // A checkbox reports its initial state as well
                        if (config.isCenterButtonEnabled() == checked) {
                            return;
                        }

                        config.setCenterButtonEnabled(checked);
                        callbacks.onElementChanged();

                        // Turning it on or off adds or removes the binding rows
                        onRebuildRequested.run();
                    }
                });

        if (!config.isCenterButtonEnabled()) {
            return;
        }

        final UniversalStickButtonConfig binding = config.getCenterButton();

        // A press of the middle sends a button, so only the buttons are offered here
        List<InputEvent> catalog = buttonCodeCatalog();
        List<String> names = describeAll(catalog);

        int selected = 0;
        for (int i = 0; i < catalog.size(); i++) {
            if (catalog.get(i).getCode() == binding.getButtonCode()) {
                selected = i;
                break;
            }
        }

        panel.addSpinner(context.getString(R.string.editor_label_center_button_action), names,
                selected, new UniversalEditorPanel.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(int position) {
                        int newCode = buttonCodeCatalog().get(position).getCode();

                        if (binding.getButtonCode() == newCode) {
                            return;
                        }

                        binding.setButtonCode(newCode);
                        callbacks.onElementChanged();
                        onRebuildRequested.run();
                    }
                });

        if (binding.isAssigned()) {
            buildSequenceEditor(binding.getActionSequence(), callbacks);
        }
    }

    private interface AxisConsumer {
        void accept(int axisCode);
    }

    private void buildAxisSpinner(String label, int axisCode, final AxisConsumer consumer) {
        List<UniversalHostAxis.Option> options = UniversalHostAxis.options();
        List<String> names = new ArrayList<>();
        for (UniversalHostAxis.Option option : options) {
            names.add(option.name);
        }

        panel.addSpinner(label, names, UniversalHostAxis.indexForCode(axisCode),
                new UniversalEditorPanel.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(int position) {
                        consumer.accept(UniversalHostAxis.codeForIndex(position));
                    }
                });
    }

    // ---------------------------------------------------------------- pads

    private void buildPadSection(UniversalVirtualControllerElement element,
                                 final Callbacks callbacks) {
        panel.addSection(context.getString(R.string.editor_section_action));

        final UniversalPadConfig config = element.getPadConfigIfAny();

        for (int direction = 0; direction < UniversalPadConfig.DIRECTION_COUNT; direction++) {
            panel.addText(context.getString(R.string.editor_label_direction_action,
                    directionName(direction)));
            buildSequenceEditor(config.getDirectionSequence(direction), callbacks);
        }
    }

    private String directionName(int direction) {
        int[] labels = new int[]{
                R.string.editor_label_direction_up,
                R.string.editor_label_direction_down,
                R.string.editor_label_direction_left,
                R.string.editor_label_direction_right,
        };

        return context.getString(labels[direction]);
    }
}
