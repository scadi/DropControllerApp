package com.example.dropcontroller.views;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.dropcontroller.R;
import com.example.dropcontroller.logic.BluetoothDeviceKind;
import com.google.android.material.color.MaterialColors;

import java.util.List;

/**
 * Rows of the Bluetooth device picker: an icon for the kind of the device, its name and address, a hint for devices
 * that look like a dropController and a highlighted row with a check mark for the device used last time.
 */
public class DeviceListAdapter extends ArrayAdapter<DeviceListAdapter.Entry> {

    private static final float HIGHLIGHT_RADIUS_DP = 16f;
    private static final float HIGHLIGHT_INSET_DP = 8f;
    private static final float VERTICAL_INSET_DP = 2f;

    /** One row: a paired Bluetooth device or, in debug builds, the simulator. */
    public static final class Entry {

        @Nullable
        private final BluetoothDevice device;
        @NonNull
        private final String key;
        @NonNull
        private final String name;
        @NonNull
        private final String subtitle;
        @NonNull
        private final BluetoothDeviceKind kind;
        private final boolean lastUsed;

        /**
         * @param device   the paired device, {@code null} for the simulator
         * @param key      identifies the entry in the saved "used last time" value (the address of the device)
         * @param subtitle the second line, normally the address
         * @param lastUsed true for the device that was picked last time
         */
        public Entry(@Nullable BluetoothDevice device, @NonNull String key, @NonNull String name,
                     @NonNull String subtitle, @NonNull BluetoothDeviceKind kind, boolean lastUsed) {
            this.device = device;
            this.key = key;
            this.name = name;
            this.subtitle = subtitle;
            this.kind = kind;
            this.lastUsed = lastUsed;
        }

        /** @return the paired device, or {@code null} for the simulator entry */
        @Nullable
        public BluetoothDevice getDevice() {
            return device;
        }

        @NonNull
        public String getKey() {
            return key;
        }

        @NonNull
        public String getName() {
            return name;
        }

        @NonNull
        public String getSubtitle() {
            return subtitle;
        }

        @NonNull
        public BluetoothDeviceKind getKind() {
            return kind;
        }

        public boolean isLastUsed() {
            return lastUsed;
        }
    }

    public DeviceListAdapter(@NonNull Context context, @NonNull List<Entry> entries) {
        super(context, 0, entries);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.item_bluetooth_device, parent, false);
        }
        Entry entry = getItem(position);
        if (entry == null) {
            return view;
        }

        ImageView icon = view.findViewById(R.id.iv_device_kind);
        TextView name = view.findViewById(R.id.tv_device_name);
        TextView address = view.findViewById(R.id.tv_device_address);
        TextView hint = view.findViewById(R.id.tv_device_hint);
        ImageView selected = view.findViewById(R.id.iv_device_selected);

        icon.setImageResource(iconFor(entry.getKind()));
        name.setText(entry.getName());
        address.setText(entry.getSubtitle());
        if (entry.getKind() == BluetoothDeviceKind.LIKELY_DROP_CONTROLLER) {
            hint.setText(R.string.bt_hint_likely);
            hint.setVisibility(View.VISIBLE);
        } else {
            hint.setVisibility(View.GONE);
        }
        selected.setVisibility(entry.isLastUsed() ? View.VISIBLE : View.GONE);
        view.setBackground(entry.isLastUsed() ? createHighlight(view) : null);
        return view;
    }

    @DrawableRes
    private static int iconFor(@NonNull BluetoothDeviceKind kind) {
        switch (kind) {
            case SERIAL_MODULE:
                return R.drawable.ic_device_serial_black_24dp;
            case PHONE:
                return R.drawable.ic_device_phone_black_24dp;
            case COMPUTER:
                return R.drawable.ic_device_computer_black_24dp;
            case AUDIO:
                return R.drawable.ic_device_audio_black_24dp;
            default:
                return R.drawable.ic_bluetooth_black_24dp;
        }
    }

    @NonNull
    private static InsetDrawable createHighlight(@NonNull View row) {
        float density = row.getResources().getDisplayMetrics().density;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(MaterialColors.getColor(row,
                com.google.android.material.R.attr.colorSecondaryContainer));
        drawable.setCornerRadius(HIGHLIGHT_RADIUS_DP * density);
        // The row is as wide as the dialog list; the inset keeps the highlight off the dialog edge
        int horizontal = Math.round(HIGHLIGHT_INSET_DP * density);
        int vertical = Math.round(VERTICAL_INSET_DP * density);
        return new InsetDrawable(drawable, horizontal, vertical, horizontal, vertical);
    }
}
