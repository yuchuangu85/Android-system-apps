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

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.annotation.Nullable;

import com.android.customization.model.CustomizationManager.Callback;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BaseClockManagerTest {

    private static final String CURRENT_CLOCK = "current_clock";

    @Mock ClockProvider mProvider;
    private TestClockManager mManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mManager = new TestClockManager(mProvider);
    }

    @Test
    public void testIsAvailable() {
        // GIVEN that the ClockProvider is available
        when(mProvider.isAvailable()).thenReturn(true);
        // THEN the BaseClockManager is true
        assertTrue(mManager.isAvailable());
    }

    @Test
    public void testApply() {
        final String id = "id";
        Clockface clock = new Clockface.Builder().setId(id).build();

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

        assertEquals(id, mManager.getClockId());
    }

    @Test
    public void testFetch() {
        mManager.fetchOptions(null, false);
        verify(mProvider).fetch(eq(null), anyBoolean());
    }

    /**
     * Testable BaseClockManager that provides basic implementations of abstract methods.
     */
    private static final class TestClockManager extends BaseClockManager {

        private String mClockId;

        TestClockManager(ClockProvider provider) {
            super(provider);
        }

        String getClockId() {
            return mClockId;
        }

        @Override
        protected void handleApply(Clockface option, Callback callback) {
            mClockId = option.getId();
            callback.onSuccess();
        }

        @Override
        protected String lookUpCurrentClock() {
            return CURRENT_CLOCK;
        }
    }
}
