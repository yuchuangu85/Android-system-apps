/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.traceur.uitest;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class TraceurAppTests {

    private static final String TRACEUR_PACKAGE = "com.android.traceur";
    private static final int TIMEOUT = 7000;   // milliseconds

    private UiDevice mDevice;

    @Before
    public void setUp() throws Exception {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        try {
            if (!mDevice.isScreenOn()) {
                mDevice.wakeUp();
            }

            // Press Menu to skip the lock screen.
            // In case we weren't on the lock screen, press Home to return to a clean launcher.
            mDevice.pressMenu();
            mDevice.pressHome();

            mDevice.setOrientationNatural();
        } catch (RemoteException e) {
            throw new RuntimeException("Failed to freeze device orientation.", e);
        }

        mDevice.waitForIdle();

        Context context = InstrumentationRegistry.getContext();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(TRACEUR_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);    // Clear out any previous instances
        context.startActivity(intent);

       // Wait for the app to appear
        mDevice.wait(Until.hasObject(By.pkg(TRACEUR_PACKAGE).depth(0)), TIMEOUT);
    }

    @After
    public void tearDown() throws Exception {
        mDevice.unfreezeRotation();
        // Finish Traceur activity.
        mDevice.pressBack();
        mDevice.pressHome();
    }

    @Presubmit
    @Test
    public void testElementsOnMainScreen() throws Exception {
        assertNotNull("Record trace switch not found.",
                mDevice.wait(Until.findObject(By.text("Record trace")),
                TIMEOUT));
        assertNotNull("Applications element not found.",
                mDevice.wait(Until.findObject(By.text("Trace debuggable applications")),
                TIMEOUT));
        assertNotNull("Categories element not found.",
                mDevice.wait(Until.findObject(By.text("Categories")),
                TIMEOUT));
        assertNotNull("Restore default categories element not found.",
                mDevice.wait(Until.findObject(By.text("Restore default categories")),
                TIMEOUT));
        assertNotNull("Per-CPU buffer size element not found.",
                mDevice.wait(Until.findObject(By.text("Per-CPU buffer size")),
                TIMEOUT));
        assertNotNull("Clear saved traces element not found.",
                mDevice.wait(Until.findObject(By.text("Clear saved traces")),
                TIMEOUT));
        assertNotNull("Long traces element not found.",
                mDevice.wait(Until.findObject(By.text("Long traces")),
                TIMEOUT));
        assertNotNull("Maximum long trace size element not found.",
                mDevice.wait(Until.findObject(By.text("Maximum long trace size")),
                TIMEOUT));
        assertNotNull("Maximum long trace duration element not found.",
                mDevice.wait(Until.findObject(By.text("Maximum long trace duration")),
                TIMEOUT));
        assertNotNull("Show Quick Settings tile switch not found.",
                mDevice.wait(Until.findObject(By.text("Show Quick Settings tile")),
                TIMEOUT));
    }

    /*
     * In this test:
     * Take a trace by toggling 'Record trace' in the UI
     * Tap the notification once the trace is saved, and verify the share dialog appears.
     */
    @Presubmit
    @Test
    public void testSuccessfulTracing() throws Exception {
        mDevice.wait(Until.findObject(By.text("Record trace")), TIMEOUT);

        mDevice.findObject(By.text("Record trace")).click();
        mDevice.wait(Until.hasObject(By.text("Trace is being recorded")), TIMEOUT);
        mDevice.findObject(By.text("Record trace")).click();

        // Wait for the popover notification to appear and then disappear,
        // so we can reliably click the notification in the notification shade.
        mDevice.wait(Until.hasObject(By.text("Tap to share your trace")), TIMEOUT);
        mDevice.wait(Until.gone(By.text("Tap to share your trace")), TIMEOUT);

        mDevice.openNotification();
        mDevice.wait(Until.hasObject(By.text("Tap to share your trace")), TIMEOUT);
        mDevice.findObject(By.text("Tap to share your trace")).click();

        mDevice.wait(Until.hasObject(By.text("Only share system traces with people and apps you trust.")), TIMEOUT);
        // The buttons on dialogs sometimes have their capitalization manipulated by themes.
        mDevice.findObject(By.text(Pattern.compile("share", Pattern.CASE_INSENSITIVE))).click();

        mDevice.wait(Until.hasObject(By.text("Share with")), TIMEOUT);
    }
}
