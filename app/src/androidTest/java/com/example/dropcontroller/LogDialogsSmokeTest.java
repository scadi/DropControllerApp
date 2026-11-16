package com.example.dropcontroller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Regression guard for the communication log dialog on the About tab.
 * <p>
 * It used to crash with a {@code NullPointerException} when opened: the dialog content is inflated with a
 * {@code null} root, so its scroll view had no layout params before the dialog was shown. The dialog does not need
 * a connected device, so these tests open it and check that it is really showing and that the activity survives.
 */
@RunWith(AndroidJUnit4.class)
public class LogDialogsSmokeTest {

    @Test
    public void communicationLogDialog_isShownWithoutCrashing() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(MainActivity::showCommunicationLog);
            scenario.onActivity(activity -> {
                assertFalse(activity.isFinishing());
                assertTrue(activity.getLogDialogs().isCommunicationLogShowing());
            });
        }
    }

    @Test
    public void secondOpenRequest_doesNotStackAnotherDialog() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.showCommunicationLog();
                activity.showCommunicationLog();
                assertTrue(activity.getLogDialogs().isCommunicationLogShowing());
            });
        }
    }

    @Test
    public void release_dismissesTheDialog() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.showCommunicationLog();
                assertTrue(activity.getLogDialogs().isCommunicationLogShowing());

                activity.getLogDialogs().release();

                assertFalse(activity.getLogDialogs().isCommunicationLogShowing());
            });
        }
    }

    @Test
    public void newLogEntry_whileTheDialogIsOpen_doesNotCrash() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.showCommunicationLog();
                // Same path as a message from the device: file written, listener told, refresh scheduled
                ((DropControllerApp) activity.getApplication()).getBluetoothHelper().getProtocolFileLog()
                        .event("smoke test entry");
            });
            scenario.onActivity(activity -> {
                assertFalse(activity.isFinishing());
                assertTrue(activity.getLogDialogs().isCommunicationLogShowing());
            });
        }
    }
}
