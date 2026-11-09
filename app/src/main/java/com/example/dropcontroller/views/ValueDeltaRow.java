package com.example.dropcontroller.views;

import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.dropcontroller.R;
import com.example.dropcontroller.data.DeltaDirection;
import com.example.dropcontroller.data.DeltaValue;
import com.example.dropcontroller.data.Limits;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Binds a {@code view_value_delta} row (label, value, INC/DEC button, delta) to a model value and its
 * {@link DeltaValue}. Number fields are committed when they lose focus or Done is pressed; the model setter clamps
 * the value and the clamped value is shown back. Empty input keeps the previous value.
 */
public class ValueDeltaRow {

    private final EditText valueField;
    private final Button directionButton;
    private final EditText deltaField;
    private final TextView label;
    private final DeltaValue delta;
    private final IntSupplier getter;
    private final IntConsumer setter;
    private final Runnable onChanged;

    /**
     * @param root      the inflated {@code view_value_delta} layout (or the {@code <include>} that replaced it)
     * @param getter    reads the value from the model
     * @param setter    writes the value to the model (must clamp)
     * @param onChanged called after the model changed, to save it
     */
    public ValueDeltaRow(@NonNull View root, @NonNull DeltaValue delta, @NonNull IntSupplier getter,
                         @NonNull IntConsumer setter, @NonNull Runnable onChanged) {
        this.label = root.findViewById(R.id.tv_label);
        this.valueField = root.findViewById(R.id.et_value);
        this.directionButton = root.findViewById(R.id.btn_direction);
        this.deltaField = root.findViewById(R.id.et_delta);
        this.delta = delta;
        this.getter = getter;
        this.setter = setter;
        this.onChanged = onChanged;

        // Several rows share the same child ids, so their saved state would overwrite each other
        valueField.setSaveEnabled(false);
        deltaField.setSaveEnabled(false);
        directionButton.setSaveEnabled(false);

        setupNumberField(valueField);
        setupNumberField(deltaField);
        directionButton.setOnClickListener(v -> {
            commit();
            delta.setDirection(delta.getDirection() == DeltaDirection.INC ? DeltaDirection.DEC : DeltaDirection.INC);
            updateDirectionButton();
            onChanged.run();
        });
    }

    public void setLabel(@StringRes int resId, Object... formatArgs) {
        label.setText(label.getContext().getString(resId, formatArgs));
    }

    /**
     * Shows the model values.
     */
    public void bind() {
        valueField.setText(String.valueOf(getter.getAsInt()));
        deltaField.setText(String.valueOf(delta.getAmount()));
        updateDirectionButton();
    }

    public void setEnabled(boolean enabled) {
        valueField.setEnabled(enabled);
        directionButton.setEnabled(enabled);
        deltaField.setEnabled(enabled);
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
        directionButton.setText(delta.getDirection() == DeltaDirection.INC ? R.string.delta_inc : R.string.delta_dec);
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
