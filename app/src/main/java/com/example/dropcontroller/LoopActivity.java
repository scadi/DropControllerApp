package com.example.dropcontroller;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.CameraTrigger;
import com.example.dropcontroller.data.DropData;
import com.example.dropcontroller.data.FlashTrigger;
import com.example.dropcontroller.data.LoopDelayMethod;
import com.example.dropcontroller.data.SequenceData;
import com.example.dropcontroller.logic.DeltaFormat;
import com.example.dropcontroller.logic.LoopController;
import com.example.dropcontroller.services.BluetoothHelper;
import com.example.dropcontroller.services.SequenceRunner;

/**
 * Loop Page (Loop Mode). Opened from the Drops tab; closing it returns to the Drops tab.
 * <p>
 * Shows per drop SOL, START with its delta, SIZE with its delta and STOP, plus the CT and FT1-FT3 lines.
 * The loop state machine is {@link LoopController}; this class feeds it the clock ticks and the end of each sequence
 * and sends the sequences through the shared {@link SequenceRunner}. The drop data of the Drops tab is not modified.
 */
public class LoopActivity extends AppCompatActivity {

    private static final long TICK_MS = 1000; // One tick per second (the wait time is in seconds)

    private DropControllerApp app;
    private BluetoothHelper bluetoothHelper;
    private SequenceRunner sequenceRunner;
    private LoopController controller;

    private final Handler tickHandler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            // Analogous to Clock_LoopDelayTimer.Timer
            controller.onSecondTick();
            if (controller.getState() == LoopController.State.COUNTDOWN) {
                tickHandler.postDelayed(this, TICK_MS);
            }
        }
    };

    private TextView sequenceText;
    private TextView statusText;
    private LinearLayout linesLayout;
    private Button startButton;
    private Button stopButton;
    private Button resetButton;

    private final LoopController.Listener controllerListener = new LoopController.Listener() {
        @Override
        public void onSendSequence(int sequenceNumber, @NonNull SequenceData data) {
            if (!sequenceRunner.start(sequenceNumber, data)) {
                Toast.makeText(LoopActivity.this, R.string.bt_not_connected, Toast.LENGTH_SHORT).show();
                controller.onConnectionLost();
            }
        }

        @Override
        public void onStateChanged(@NonNull LoopController.State state) {
            tickHandler.removeCallbacks(tickRunnable);
            if (state == LoopController.State.COUNTDOWN) {
                tickHandler.postDelayed(tickRunnable, TICK_MS);
            }
            updateStatus();
        }

        @Override
        public void onCountdown(int secondsRemaining) {
            updateStatus();
        }

        @Override
        public void onDataChanged(int sequenceNumber, @NonNull SequenceData data) {
            render();
        }
    };

    private final SequenceRunner.Listener runnerListener = new SequenceRunner.Listener() {
        @Override
        public void onActiveChanged(boolean active) {
            // The controller already knows: it started the sequence
        }

        @Override
        public void onFinished(boolean timedOut) {
            if (!bluetoothHelper.isConnected()) {
                // Finished because the connection dropped, not because the device reported [FTS]
                controller.onConnectionLost();
            } else {
                controller.onSequenceFinished();
            }
        }
    };

    private final BluetoothHelper.Listener bluetoothListener = new BluetoothHelper.Listener() {
        @Override
        public void onStateChanged(@NonNull BluetoothHelper.State state) {
            if (state != BluetoothHelper.State.CONNECTED) {
                controller.onConnectionLost();
            }
            updateStatus();
        }

        @Override
        public void onError(int messageResId) {
            Toast.makeText(LoopActivity.this, messageResId, Toast.LENGTH_LONG).show();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loop);
        setTitle(R.string.loop_title);

        app = (DropControllerApp) getApplication();
        bluetoothHelper = app.getBluetoothHelper();
        sequenceRunner = app.getSequenceRunner();

        sequenceText = findViewById(R.id.tv_loop_sequence);
        statusText = findViewById(R.id.tv_loop_status);
        linesLayout = findViewById(R.id.ll_loop_lines);
        startButton = findViewById(R.id.btn_loop_start);
        stopButton = findViewById(R.id.btn_loop_stop);
        resetButton = findViewById(R.id.btn_loop_reset);

        controller = new LoopController(app.getSettings(), app.getSequence(), controllerListener);

        startButton.setOnClickListener(v -> controller.start());
        stopButton.setOnClickListener(v -> controller.stop());
        resetButton.setOnClickListener(v -> controller.reset());

        // Registered for the whole life of the page: the loop must go on while the screen is off
        bluetoothHelper.addListener(bluetoothListener);
        sequenceRunner.addListener(runnerListener);

        render();
        updateStatus();

        // TIMER: the first sequence starts at once. BUTTON: wait for the START button.
        // savedInstanceState is only set after the system recreated the page; never restart a loop then.
        if (savedInstanceState == null && app.getSettings().getDelayMethod() == LoopDelayMethod.TIMER) {
            controller.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tickHandler.removeCallbacks(tickRunnable);
        bluetoothHelper.removeListener(bluetoothListener);
        sequenceRunner.removeListener(runnerListener);
    }

    // ----------------------------------------------------
    // Rendering
    // ----------------------------------------------------

    /**
     * Shows the values of the next sequence: one block per drop, then CT and FT1-FT3.
     */
    private void render() {
        AppSettings settings = app.getSettings();
        SequenceData data = controller.getData();

        sequenceText.setText(getString(R.string.loop_sequence, controller.getSequenceNumber()));

        linesLayout.removeAllViews();
        for (int i = 0; i < settings.getNumDrops(); i++) {
            DropData drop = data.getDrop(i);
            addLine(getString(R.string.loop_drop_header, i + 1, drop.getSolenoid(), drop.getStopTime()), true);
            addLine(getString(R.string.loop_drop_values,
                    drop.getStartTime(), DeltaFormat.format(drop.getStartDelta()),
                    drop.getSize(), DeltaFormat.format(drop.getSizeDelta())), false);
        }

        CameraTrigger camera = data.getCamera();
        if (!camera.isEnabled()) {
            addLine(getString(R.string.loop_camera_off), true);
        } else if (camera.isBulb()) {
            addLine(getString(R.string.loop_camera_bulb), true);
        } else {
            addLine(getString(R.string.loop_camera_on, camera.getTime(), DeltaFormat.format(camera.getDelta())), true);
        }

        for (int i = 0; i < SequenceData.FLASH_COUNT; i++) {
            FlashTrigger flash = data.getFlash(i);
            if (flash.isEnabled()) {
                addLine(getString(R.string.loop_flash_on, i + 1, flash.getTime(),
                        DeltaFormat.format(flash.getDelta())), true);
            } else {
                addLine(getString(R.string.loop_flash_off, i + 1), true);
            }
        }
    }

    private void addLine(@NonNull String text, boolean bold) {
        TextView line = new TextView(this);
        line.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        line.setText(text);
        line.setTextSize(16);
        if (bold) {
            line.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
            line.setTypeface(line.getTypeface(), Typeface.BOLD);
        }
        linesLayout.addView(line);
    }

    private void updateStatus() {
        LoopController.State state = controller.getState();
        switch (state) {
            case SEQUENCE_ACTIVE:
                statusText.setText(getString(R.string.loop_status_active, controller.getSequenceNumber()));
                break;
            case COUNTDOWN:
                statusText.setText(getString(R.string.loop_status_countdown, controller.getSecondsRemaining()));
                break;
            case WAITING_FOR_BUTTON:
                statusText.setText(R.string.loop_status_button);
                break;
            default:
                statusText.setText(R.string.loop_status_idle);
                break;
        }

        boolean canStart = state == LoopController.State.IDLE || state == LoopController.State.WAITING_FOR_BUTTON;
        startButton.setEnabled(canStart && bluetoothHelper.isConnected());
        stopButton.setEnabled(state != LoopController.State.IDLE);
        resetButton.setEnabled(state != LoopController.State.SEQUENCE_ACTIVE);
    }
}
