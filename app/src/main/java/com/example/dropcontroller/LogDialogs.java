package com.example.dropcontroller;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.dropcontroller.logic.ProtocolFileLog;
import com.example.dropcontroller.logic.ProtocolLog;
import com.example.dropcontroller.services.BluetoothHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The two log dialogs of the app: the live in-memory protocol log (debug builds) and the persistent communication
 * log file with Share and Clear (every build). Owned by {@link MainActivity}, which calls {@link #release()} from
 * {@code onDestroy()}.
 */
public final class LogDialogs {

    private static final String TAG = "LogDialogs";
    private static final float MAX_HEIGHT_SHARE = 0.6f;

    private final AppCompatActivity activity;
    private final BluetoothHelper bluetoothHelper;
    @Nullable
    private AlertDialog protocolLogDialog;
    @Nullable
    private AlertDialog communicationLogDialog;
    // The communication log file is read and cleared here, so a slow filesystem does not stall the UI thread
    private final ExecutorService logIoExecutor = Executors.newSingleThreadExecutor();

    public LogDialogs(@NonNull AppCompatActivity activity, @NonNull BluetoothHelper bluetoothHelper) {
        this.activity = activity;
        this.bluetoothHelper = bluetoothHelper;
    }

    public boolean isProtocolLogShowing() {
        return protocolLogDialog != null && protocolLogDialog.isShowing();
    }

    public boolean isCommunicationLogShowing() {
        return communicationLogDialog != null && communicationLogDialog.isShowing();
    }

    /** Dismisses both dialogs and stops the background executor. Call before the activity is destroyed. */
    public void release() {
        if (protocolLogDialog != null) {
            protocolLogDialog.dismiss();
            protocolLogDialog = null;
        }
        if (communicationLogDialog != null) {
            communicationLogDialog.dismiss();
            communicationLogDialog = null;
        }
        logIoExecutor.shutdown();
    }

    /**
     * Shows the protocol log in a dialog that updates while it is open. The log is filled by BluetoothHelper, so
     * it also shows the messages of the built-in simulator. Opened from the debug-only overflow menu item.
     */
    public void showProtocolLog() {
        if (isProtocolLogShowing()) {
            return;
        }
        ProtocolLog protocolLog = bluetoothHelper.getProtocolLog();
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_protocol_log, null);
        TextView textView = content.findViewById(R.id.tv_protocol_log);
        ScrollView scrollView = content.findViewById(R.id.scroll_protocol_log);

        renderProtocolLog(textView, protocolLog);
        ProtocolLog.Listener listener = () -> activity.runOnUiThread(() -> {
            boolean wasAtBottom = isScrolledToBottom(scrollView);
            renderProtocolLog(textView, protocolLog);
            // Do not fight the user while they read older entries
            if (wasAtBottom) {
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.protocol_log_title)
                .setView(content)
                .setPositiveButton(R.string.log_clear, null)
                .setNegativeButton(R.string.log_close, null)
                .create();
        // Clear keeps the dialog open, so replace the default dismiss listener of the button
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> protocolLog.clear()));
        dialog.setOnDismissListener(dismissed -> {
            protocolLog.removeListener(listener);
            protocolLogDialog = null;
        });

        protocolLog.addListener(listener);
        protocolLogDialog = dialog;
        dialog.show();
        limitHeight(scrollView);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    /**
     * Shows the persistent communication log file with Share and Clear. Opened from the About tab in every build,
     * so a user can send the file when the controller misbehaves.
     */
    public void showCommunicationLog() {
        if (isCommunicationLogShowing()) {
            return;
        }
        ProtocolFileLog fileLog = bluetoothHelper.getProtocolFileLog();
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_protocol_log, null);
        TextView textView = content.findViewById(R.id.tv_protocol_log);
        ScrollView scrollView = content.findViewById(R.id.scroll_protocol_log);
        loadFileLogText(textView, scrollView, fileLog);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.communication_log_title)
                .setView(content)
                .setPositiveButton(R.string.communication_log_share, null)
                .setNeutralButton(R.string.log_clear, null)
                .setNegativeButton(R.string.log_close, null)
                .create();
        // Share and Clear keep the dialog open, so replace the default dismiss listeners of the buttons
        dialog.setOnShowListener(shown -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> shareCommunicationLog(fileLog));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(v -> clearFileLog(textView, fileLog));
        });
        dialog.setOnDismissListener(dismissed -> communicationLogDialog = null);
        communicationLogDialog = dialog;
        dialog.show();
        limitHeight(scrollView);
    }

    /**
     * Limits the log dialog to a share of the screen height, so it also fits in landscape and on small screens.
     * Must run after {@code dialog.show()}: the view inflated with a {@code null} root has no layout params until
     * the dialog attaches it.
     */
    private void limitHeight(@NonNull ScrollView scrollView) {
        int maxHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * MAX_HEIGHT_SHARE);
        ViewGroup.LayoutParams params = scrollView.getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
        } else {
            params.height = maxHeight;
        }
        scrollView.setLayoutParams(params);
    }

    private static boolean isScrolledToBottom(@NonNull ScrollView scrollView) {
        View child = scrollView.getChildAt(0);
        return child == null
                || scrollView.getScrollY() + scrollView.getHeight() >= child.getBottom();
    }

    /** Reads the log file in the background and shows it, scrolling to the newest entries. */
    private void loadFileLogText(@NonNull TextView textView, @NonNull ScrollView scrollView,
                                 @NonNull ProtocolFileLog fileLog) {
        runInBackground(() -> {
            String text = fileLog.readAll();
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed()) {
                    return;
                }
                if (text == null) {
                    textView.setText(R.string.communication_log_read_failed);
                } else {
                    textView.setText(text.isEmpty()
                            ? activity.getString(R.string.communication_log_empty) : text);
                }
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            });
        });
    }

    /** Empties the log file in the background and updates the dialog when it is done. */
    private void clearFileLog(@NonNull TextView textView, @NonNull ProtocolFileLog fileLog) {
        runInBackground(() -> {
            boolean cleared = fileLog.clear();
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed()) {
                    return;
                }
                if (!cleared) {
                    Toast.makeText(activity, R.string.communication_log_clear_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                textView.setText(R.string.communication_log_empty);
            });
        });
    }

    private void runInBackground(@NonNull Runnable task) {
        try {
            logIoExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            // The activity is being destroyed and the executor is shut down
            Log.w(TAG, "Log executor rejected the task", e);
        }
    }

    private void shareCommunicationLog(@NonNull ProtocolFileLog fileLog) {
        File file = fileLog.getFile();
        if (!file.exists() || file.length() == 0) {
            Toast.makeText(activity, R.string.communication_log_nothing_to_share, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(intent,
                activity.getString(R.string.communication_log_share_title)));
    }

    private void renderProtocolLog(@NonNull TextView textView, @NonNull ProtocolLog protocolLog) {
        List<ProtocolLog.Entry> entries = protocolLog.getEntries();
        if (entries.isEmpty()) {
            textView.setText(R.string.protocol_log_empty);
            return;
        }
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
        StringBuilder builder = new StringBuilder();
        for (ProtocolLog.Entry entry : entries) {
            builder.append(timeFormat.format(new Date(entry.getTimestampMs())))
                    .append(entry.isSent() ? "  > " : "  < ")
                    .append(entry.getMessage())
                    .append('\n');
        }
        textView.setText(builder.toString());
    }
}
