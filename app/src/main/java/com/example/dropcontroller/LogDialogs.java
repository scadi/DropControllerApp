package com.example.dropcontroller;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
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
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.dropcontroller.logic.LogLineKind;
import com.example.dropcontroller.logic.ProtocolFileLog;
import com.example.dropcontroller.services.BluetoothHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The communication log dialog: the persistent log file of the messages exchanged with the device, with Share and
 * Clear. It updates while it is open. Owned by {@link MainActivity}, which calls {@link #release()} from
 * {@code onDestroy()}.
 */
public final class LogDialogs {

    private static final String TAG = "LogDialogs";
    private static final float MAX_HEIGHT_SHARE = 0.6f;
    // During a sequence several messages arrive within a few milliseconds; one refresh covers all of them
    private static final long REFRESH_DELAY_MS = 300;

    private final AppCompatActivity activity;
    private final BluetoothHelper bluetoothHelper;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private AlertDialog communicationLogDialog;
    // True while a refresh is waiting in mainHandler; main thread only
    private boolean refreshScheduled;
    // The communication log file is read and cleared here, so a slow filesystem does not stall the UI thread
    private final ExecutorService logIoExecutor = Executors.newSingleThreadExecutor();

    public LogDialogs(@NonNull AppCompatActivity activity, @NonNull BluetoothHelper bluetoothHelper) {
        this.activity = activity;
        this.bluetoothHelper = bluetoothHelper;
    }

    public boolean isCommunicationLogShowing() {
        return communicationLogDialog != null && communicationLogDialog.isShowing();
    }

    /** Dismisses the dialog and stops the background executor. Call before the activity is destroyed. */
    public void release() {
        if (communicationLogDialog != null) {
            communicationLogDialog.dismiss();
            communicationLogDialog = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
        refreshScheduled = false;
        logIoExecutor.shutdown();
    }

    /**
     * Shows the persistent communication log file with Share and Clear. Opened from the About tab in every build,
     * so a user can send the file when the controller misbehaves. New entries appear while the dialog is open.
     */
    public void showCommunicationLog() {
        if (isCommunicationLogShowing()) {
            return;
        }
        ProtocolFileLog fileLog = bluetoothHelper.getProtocolFileLog();
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_communication_log, null);
        TextView textView = content.findViewById(R.id.tv_communication_log);
        ScrollView scrollView = content.findViewById(R.id.scroll_communication_log);
        loadFileLogText(textView, scrollView, fileLog, true);

        // Called on the file log executor thread: only hop to the main thread here
        ProtocolFileLog.Listener listener =
                () -> activity.runOnUiThread(() -> scheduleRefresh(textView, scrollView, fileLog));
        fileLog.addListener(listener);

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
        dialog.setOnDismissListener(dismissed -> {
            fileLog.removeListener(listener);
            mainHandler.removeCallbacksAndMessages(null);
            refreshScheduled = false;
            communicationLogDialog = null;
        });
        communicationLogDialog = dialog;
        dialog.show();
        limitHeight(scrollView);
    }

    /** Limits the number of file reads: the first change starts a timer, the changes before it ends are merged. */
    private void scheduleRefresh(@NonNull TextView textView, @NonNull ScrollView scrollView,
                                 @NonNull ProtocolFileLog fileLog) {
        if (refreshScheduled || !isCommunicationLogShowing()) {
            return;
        }
        refreshScheduled = true;
        mainHandler.postDelayed(() -> {
            refreshScheduled = false;
            // Reloading the text would drop a selection the user is making to copy a part of the log
            if (!textView.hasSelection()) {
                loadFileLogText(textView, scrollView, fileLog, false);
            }
        }, REFRESH_DELAY_MS);
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

    /**
     * Reads the log file in the background and shows it.
     *
     * @param scrollToEnd true to scroll to the newest entries; otherwise the view only follows the log when it was
     *                    already at the bottom, so it does not fight the user while they read older entries
     */
    private void loadFileLogText(@NonNull TextView textView, @NonNull ScrollView scrollView,
                                 @NonNull ProtocolFileLog fileLog, boolean scrollToEnd) {
        // The colors follow the current day/night configuration of the activity
        int receivedColor = ContextCompat.getColor(activity, R.color.log_received);
        int sentColor = ContextCompat.getColor(activity, R.color.log_sent);
        int errorColor = ContextCompat.getColor(activity, R.color.log_error);
        runInBackground(() -> {
            String text = fileLog.readAll();
            CharSequence colored = text == null || text.isEmpty()
                    ? text : colorize(text, receivedColor, sentColor, errorColor);
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed()) {
                    return;
                }
                boolean follow = scrollToEnd || isScrolledToBottom(scrollView);
                if (colored == null) {
                    textView.setText(R.string.communication_log_read_failed);
                } else {
                    textView.setText(colored.length() == 0
                            ? activity.getString(R.string.communication_log_empty) : colored);
                }
                if (follow) {
                    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                }
            });
        });
    }

    /**
     * Colors the message of every log line (received, sent, error events); the timestamp and the other lines keep
     * the default text color. Only the display is colored: the file and the shared text stay plain.
     */
    @NonNull
    private static CharSequence colorize(@NonNull String text, int receivedColor, int sentColor, int errorColor) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        int lineStart = 0;
        while (lineStart < text.length()) {
            int newline = text.indexOf('\n', lineStart);
            int lineEnd = newline < 0 ? text.length() : newline;
            LogLineKind.Result result = LogLineKind.parse(text.substring(lineStart, lineEnd));
            int color;
            switch (result.getKind()) {
                case RECEIVED:
                    color = receivedColor;
                    break;
                case SENT:
                    color = sentColor;
                    break;
                case ERROR:
                    color = errorColor;
                    break;
                default:
                    color = 0;
                    break;
            }
            if (color != 0) {
                builder.setSpan(new ForegroundColorSpan(color), lineStart + result.getMessageStart(), lineEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            lineStart = lineEnd + 1;
        }
        return builder;
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
}
