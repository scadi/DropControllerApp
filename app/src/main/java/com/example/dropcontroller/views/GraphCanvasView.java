package com.example.dropcontroller.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.dropcontroller.R;
import com.example.dropcontroller.logic.GraphModel;
import com.google.android.material.color.MaterialColors;

import java.util.List;

/**
 * Timing chart (Analogous to the App Inventor Canvas): one horizontal bar per drop, flash trigger and camera
 * shutter on a common millisecond axis. The view sizes its height from the number of bars.
 */
public class GraphCanvasView extends View {

    private static final float ROW_HEIGHT_DP = 32f;
    private static final float BAR_HEIGHT_DP = 18f;
    private static final float LABEL_WIDTH_DP = 44f;
    private static final float AXIS_HEIGHT_DP = 32f;
    private static final float PADDING_DP = 8f;
    private static final float TEXT_SIZE_SP = 12f;
    private static final float MIN_BAR_WIDTH_PX = 3f;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density;
    private GraphModel model;

    @ColorInt
    private int dropColor;
    @ColorInt
    private int flashColor;
    @ColorInt
    private int cameraColor;

    public GraphCanvasView(Context context) {
        super(context);
        init();
    }

    public GraphCanvasView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        // Series colors come from res/values(-night)/colors.xml, the rest from the theme (light and dark)
        dropColor = ContextCompat.getColor(getContext(), R.color.graph_drop);
        flashColor = ContextCompat.getColor(getContext(), R.color.graph_flash);
        cameraColor = ContextCompat.getColor(getContext(), R.color.graph_camera);

        @ColorInt int axisColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant,
                Color.DKGRAY);
        @ColorInt int textColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface,
                Color.DKGRAY);
        @ColorInt int gridColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant,
                Color.LTGRAY);

        axisPaint.setColor(axisColor);
        axisPaint.setStrokeWidth(Math.max(1f, density));

        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(1f);

        textPaint.setColor(textColor);
        textPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP, getResources().getDisplayMetrics()));
    }

    /** Sets the data to draw and redraws. */
    public void setModel(@Nullable GraphModel model) {
        this.model = model;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int rows = model != null ? model.getBars().size() : 0;
        int desired = (int) ((PADDING_DP * 2 + AXIS_HEIGHT_DP + rows * ROW_HEIGHT_DP) * density);
        setMeasuredDimension(
                resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(desired, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (model == null || model.getBars().isEmpty()) {
            return;
        }

        float padding = PADDING_DP * density;
        float rowHeight = ROW_HEIGHT_DP * density;
        float barHeight = BAR_HEIGHT_DP * density;
        float left = padding + LABEL_WIDTH_DP * density;
        float right = getWidth() - padding;
        float top = padding;

        List<GraphModel.Bar> bars = model.getBars();
        float plotBottom = top + bars.size() * rowHeight;
        float scale = (right - left) / model.getAxisMaxMs();

        // Vertical grid lines and tick labels
        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int t = 0; t <= model.getAxisMaxMs(); t += model.getTickStepMs()) {
            float x = left + t * scale;
            canvas.drawLine(x, top, x, plotBottom, gridPaint);
            canvas.drawText(String.valueOf(t), x, plotBottom + 16 * density, textPaint);
        }
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(getContext().getString(R.string.graph_axis_unit), right, plotBottom + 30 * density, textPaint);

        // Axes
        canvas.drawLine(left, top, left, plotBottom, axisPaint);
        canvas.drawLine(left, plotBottom, right, plotBottom, axisPaint);

        // Bars and row labels
        textPaint.setTextAlign(Paint.Align.LEFT);
        float textOffset = (textPaint.descent() - textPaint.ascent()) / 2f - textPaint.descent();
        for (int i = 0; i < bars.size(); i++) {
            GraphModel.Bar bar = bars.get(i);
            float rowTop = top + i * rowHeight;
            float centerY = rowTop + rowHeight / 2f;

            canvas.drawText(labelFor(bar), padding, centerY + textOffset, textPaint);

            float barLeft = left + bar.getStartMs() * scale;
            float barRight = Math.max(left + bar.getStopMs() * scale, barLeft + MIN_BAR_WIDTH_PX);
            barPaint.setColor(colorFor(bar));
            canvas.drawRect(barLeft, centerY - barHeight / 2f, barRight, centerY + barHeight / 2f, barPaint);
        }
    }

    @NonNull
    private String labelFor(@NonNull GraphModel.Bar bar) {
        switch (bar.getType()) {
            case DROP:
                return getContext().getString(R.string.graph_label_drop, bar.getIndex());
            case FLASH:
                return getContext().getString(R.string.graph_label_flash, bar.getIndex());
            default:
                return getContext().getString(R.string.graph_label_camera);
        }
    }

    @ColorInt
    private int colorFor(@NonNull GraphModel.Bar bar) {
        switch (bar.getType()) {
            case DROP:
                return dropColor;
            case FLASH:
                return flashColor;
            default:
                return cameraColor;
        }
    }
}
