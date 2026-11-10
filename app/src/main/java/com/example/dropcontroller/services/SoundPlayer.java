package com.example.dropcontroller.services;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.dropcontroller.data.AppSettings;

/**
 * Plays the short signals of the app: sequence start, sequence end and the end of the Loop Mode countdown.
 * <p>
 * Nothing is played when "Sounds" is off in the settings. The tones come from {@link ToneGenerator}, so the app
 * needs no audio files. It is owned by DropControllerApp and listens to the shared {@link SequenceRunner}, so the
 * signals are the same on the Drops tab and on the Loop Page. Must be used on the main thread.
 */
public class SoundPlayer {

    private static final String TAG = "SoundPlayer";

    private static final int VOLUME = 80; // 0..100 of the stream volume
    private static final int TONE_DURATION_MS = 200;

    /**
     * In TIMER mode the next sequence starts right after the countdown (or right after the previous sequence when
     * the wait time is 0). A new tone would cut the one that is still playing, so the start tone is skipped then.
     */
    private static final long START_SUPPRESS_AFTER_TONE_MS = 500;

    private final AppSettings settings;
    private ToneGenerator toneGenerator;
    private long lastToneTimeMs = -START_SUPPRESS_AFTER_TONE_MS;

    private final SequenceRunner.Listener runnerListener = new SequenceRunner.Listener() {
        @Override
        public void onActiveChanged(boolean active) {
            if (active) {
                playSequenceStart();
            }
        }

        @Override
        public void onDeviceFinished() {
            playSequenceEnd();
        }
    };

    public SoundPlayer(@NonNull AppSettings settings, @NonNull SequenceRunner sequenceRunner) {
        this.settings = settings;
        // Lives as long as the app, like the SequenceRunner itself
        sequenceRunner.addListener(runnerListener);
    }

    /** A sequence was sent to the device. */
    public void playSequenceStart() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastToneTimeMs < START_SUPPRESS_AFTER_TONE_MS) {
            return;
        }
        play(ToneGenerator.TONE_PROP_BEEP);
    }

    /** The device reported the end of the sequence. */
    public void playSequenceEnd() {
        play(ToneGenerator.TONE_PROP_ACK);
    }

    /** The Loop Mode countdown reached zero. */
    public void playCountdownEnd() {
        play(ToneGenerator.TONE_PROP_BEEP2);
    }

    private void play(int tone) {
        if (!settings.isSoundsOn()) {
            return;
        }
        if (toneGenerator == null) {
            try {
                toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME);
            } catch (RuntimeException e) {
                // ToneGenerator throws a bare RuntimeException when the audio system is not available
                Log.w(TAG, "Cannot create ToneGenerator", e);
                return;
            }
        }
        lastToneTimeMs = SystemClock.elapsedRealtime();
        toneGenerator.startTone(tone, TONE_DURATION_MS);
    }
}
