package com.example.dropcontroller.views;

import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.dropcontroller.R;
import com.example.dropcontroller.data.DeltaDirection;
import com.example.dropcontroller.data.DeltaValue;
import com.example.dropcontroller.data.Limits;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Binds a {@code view_value_delta} row (label, value, INC/DEC segmented button, delta) to a model value and its
 * {@link DeltaValue}. Number fields are committed when they lose focus or Done is pressed; the model setter clamps
 * the value and the clamped value is shown back. Empty input keeps the previous value.
 */
public class ValueDeltaRow {

    private final View root;
    private final EditText valueField;
    private final MaterialButtonToggleGroup directionGroup;
    private final EditText deltaField;
    private final TextInputLayout valueLayout;
    private final TextInputLayout deltaLayout;
    private final TextView label;
    private final MaterialSwitch extraSwitch;
    private final DeltaValue delta;
    private final IntSupplier getter;
    private final IntConsumer setter;
    private final Runnable onChanged;

    // True while the toggle group is changed by the code, so its listener does not write back
    private boolean binding;

    /**
     * @param root      the inflated {@code view_value_delta} layout (or the {@code <include>} that replaced it)
     * @param getter    reads the value from the model
     * @param setter    writes the value to the model (must clamp)
     * @param onChanged called after the model changed, to save it
     */
    public ValueDeltaRow(@NonNull View root, @NonNull DeltaValue delta, @NonNull IntSupplier getter,
                         @NonNull IntConsumer setter, @NonNull Runnable onChanged) {
        this.root = root;
        this.label = root.findViewById(R.id.tv_label);
        this.extraSwitch = root.findViewById(R.id.sw_extra);
        this.valueLayout = root.findViewById(R.id.til_value);
        this.valueField = root.findViewById(R.id.et_value);
        this.directionGroup = root.findViewById(R.id.tg_direction);
        this.deltaLayout = root.findViewById(R.id.til_delta);
        this.deltaField = root.findViewById(R.id.et_delta);
        this.delta = delta;
        this.getter = getter;
        this.setter = setter;
        this.onChanged = onChanged;

        // Several rows share the same child ids, so their saved state would overwrite each other
        valueField.setSaveEnabled(false);
        deltaField.setSaveEnabled(false);
        valueLayout.setSaveEnabled(false);
        deltaLayout.setSaveEnabled(false);
        directionGroup.setSaveEnabled(false);
        extraSwitch.setSaveEnabled(false);
        root.findViewById(R.id.btn_inc).setSaveEnabled(false);
        root.findViewById(R.id.btn_dec).setSaveEnabled(false);

        setupNumberField(valueField);
        setupNumberField(deltaField);
        directionGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (binding || !isChecked) {
                return;
            }
            commit();
            delta.setDirection(checkedId == R.id.btn_inc ? DeltaDirection.INC : DeltaDirection.DEC);
            onChanged.run();
        });
    }

    public void setLabel(@StringRes int resId, Object... formatArgs) {
        label.setText(label.getContext().getString(resId, formatArgs));
    }

    /**
     * Labels the row with an icon and a short text (may be empty); the description is read by screen readers.
     */
    public void setIconLabel(@DrawableRes int iconResId, @NonNull CharSequence text,
                             @NonNull CharSequence description) {
        label.setText(text);
        label.setContentDescription(description);
        label.setCompoundDrawablePadding((int) (6 * label.getResources().getDisplayMetrics().density));
        label.setCompoundDrawablesRelativeWithIntrinsicBounds(iconResId, 0, 0, 0);
    }

    /**
     * The optional switch at the end of the label line. It is hidden until the caller makes it visible, and it
     * stays usable when the rest of the row is disabled by {@link #setEnabled(boolean)}.
     */
    @NonNull
    public MaterialSwitch getExtraSwitch() {
        return extraSwitch;
    }

    /**
     * Shows the model values.
     */
    public void bind() {
        valueField.setText(String.valueOf(getter.getAsInt()));
        deltaField.setText(String.valueOf(delta.getAmount()));
        updateDirectionButton();
    }

    /**
     * Shows or hides the whole row. A hidden row keeps its values in the model.
     */
    public void setVisible(boolean visible) {
        root.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setEnabled(boolean enabled) {
        valueLayout.setEnabled(enabled);
        valueField.setEnabled(enabled);
        deltaLayout.setEnabled(enabled);
        deltaField.setEnabled(enabled);
        directionGroup.setEnabled(enabled);
        for (int i = 0; i < directionGroup.getChildCount(); i++) {
            directionGroup.getChildAt(i).setEnabled(enabled);
        }
        label.setEnabled(enabled);
    }

    /**
     * Stores the text of the fields in the model without saving. Use before several rows are saved at once.
     */
    public void apply() {
        setter.accept(parse(valueField, getter.getAsInt()));
        delta.setAmount(parse(deltaField, delta.getAmount()));
        valueField.setText(String.valueOf(getter.getAsInt()));
        deltaField.setText(String.valueOf(delta.getAmount()));
    }

    /**
     * Stores the text of the fields in the model and saves it.
     */
    public void commit() {
        apply();
        onChanged.run();
    }

    private void updateDirectionButton() {
        binding = true;
        directionGroup.check(delta.getDirection() == DeltaDirection.INC ? R.id.btn_inc : R.id.btn_dec);
        binding = false;
    }

    private void setupNumberField(@NonNull EditText field) {
        field.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                commit();
            }
        });
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                v.clearFocus();
            }
            return false;
        });
    }

    private static int parse(@NonNull EditText field, int fallback) {
        try {
            return Integer.parseInt(field.getText().toString().trim());
        } catch (NumberFormatException e) {
            // Empty or invalid input keeps the previous value
            return Math.max(fallback, Limits.MIN_TIME_MS);
        }
    }
}
