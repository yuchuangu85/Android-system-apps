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
package com.android.customization.model.grid;

import static junit.framework.TestCase.fail;

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.annotation.Nullable;

import com.android.customization.model.CustomizationManager.Callback;
import com.android.customization.module.ThemesUserEventLogger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class GridOptionsManagerTest {

    @Mock LauncherGridOptionsProvider mProvider;
    @Mock ThemesUserEventLogger mThemesUserEventLogger;
    private GridOptionsManager mManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mManager = new GridOptionsManager(mProvider, mThemesUserEventLogger);
    }

    @Test
    public void testApply() {
        String gridName = "testName";
        GridOption grid = new GridOption("testTitle", gridName, false, 2, 2, null, 1, "");
        when(mProvider.applyGrid(gridName)).thenReturn(1);

        mManager.apply(grid, new Callback() {
            @Override
            public void onSuccess() {
                //Nothing to do here, the test passed
            }

            @Override
            public void onError(@Nullable Throwable throwable) {
                fail("onError was called when grid had been applied successfully");
            }
        });
    }

    @Test
    public void testFetch_backgroundThread() {
        mManager.fetchOptions(null, false);
        Robolectric.flushBackgroundThreadScheduler();
        verify(mProvider).fetch(anyBoolean());
    }
}
