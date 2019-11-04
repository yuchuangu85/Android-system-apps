/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.customization.model.clock;

import static junit.framework.TestCase.fail;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

import android.content.ContentResolver;
import android.provider.Settings.Secure;
import androidx.annotation.Nullable;

import com.android.customization.model.CustomizationManager.Callback;

import com.android.customization.module.ThemesUserEventLogger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class ClockManagerTest {

    private static final String CLOCK_ID = "id";

    @Mock ClockProvider mProvider;
    @Mock ThemesUserEventLogger mLogger;
    private ContentResolver mContentResolver;
    private ClockManager mManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContentResolver = RuntimeEnvironment.application.getContentResolver();
        mManager = new ClockManager(mContentResolver, mProvider, mLogger);
    }

    @Test
    public void testApply() {
        Clockface clock = new Clockface.Builder().setId(CLOCK_ID).build();

        mManager.apply(clock, new Callback() {
            @Override
            public void onSuccess() {
                //Nothing to do here, the test passed
            }

            @Override
            public void onError(@Nullable Throwable throwable) {
                fail("onError was called when grid had been applied successfully");
            }
        });

        // THEN the clock id is written to secure settings.
        assertEquals(CLOCK_ID, Secure.getString(mContentResolver, ClockManager.CLOCK_FACE_SETTING));
        // AND the event is logged
        verify(mLogger).logClockApplied(clock);
    }

    @Test
    public void testGetCurrentClock() {
        // GIVEN that secure settings contains a clock id
        Secure.putString(mContentResolver, ClockManager.CLOCK_FACE_SETTING, CLOCK_ID);
        // THEN the current clock is that id
        assertEquals(CLOCK_ID, mManager.getCurrentClock());
    }
}
