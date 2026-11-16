package com.example.dropcontroller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Regression guard for the two log dialogs on the About tab and in the overflow menu.
 * <p>
 * Both used to crash with a {@code NullPointerException} when opened: the dialog content is inflated with a
 * {@code null} root, so its scroll view had no layout params before the dialog was shown. The dialogs do not need
 * a connected device, so these tests open them and check that they are really showing and that the activity
 * survives.
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
                assertFalse(activity.getLogDialogs().isProtocolLogShowing());
            });
        }
    }

    @Test
    public void protocolLogDialog_isShownWithoutCrashing() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(MainActivity::showProtocolLog);
            scenario.onActivity(activity -> {
                assertFalse(activity.isFinishing());
                assertTrue(activity.getLogDialogs().isProtocolLogShowing());
                assertFalse(activity.getLogDialogs().isCommunicationLogShowing());
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
    public void release_dismissesBothDialogs() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.showCommunicationLog();
                activity.showProtocolLog();
                assertTrue(activity.getLogDialogs().isCommunicationLogShowing());
                assertTrue(activity.getLogDialogs().isProtocolLogShowing());

                activity.getLogDialogs().release();

                assertFalse(activity.getLogDialogs().isCommunicationLogShowing());
                assertFalse(activity.getLogDialogs().isProtocolLogShowing());
            });
        }
    }
}
