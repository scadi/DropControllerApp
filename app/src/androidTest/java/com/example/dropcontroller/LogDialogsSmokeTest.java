package com.example.dropcontroller;

import static org.junit.Assert.assertFalse;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Regression guard for the two log dialogs on the About tab and in the overflow menu.
 * <p>
 * Both used to crash with a {@code NullPointerException} when opened: the dialog content is inflated with a
 * {@code null} root, so its scroll view had no layout params before the dialog was shown. The dialogs do not need
 * a connected device, so these tests only open them and check that the activity survives.
 */
@RunWith(AndroidJUnit4.class)
public class LogDialogsSmokeTest {

    @Test
    public void communicationLogDialog_opensWithoutCrashing() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(MainActivity::showCommunicationLog);
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }

    @Test
    public void protocolLogDialog_opensWithoutCrashing() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(MainActivity::showProtocolLog);
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }
}
