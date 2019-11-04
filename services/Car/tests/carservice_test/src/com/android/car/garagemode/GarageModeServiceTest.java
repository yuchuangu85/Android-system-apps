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

package com.android.car.garagemode;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class GarageModeServiceTest {
    @Mock private Context mMockContext;
    @Mock private Controller mMockController;
    @Mock private ContentResolver mMockContentResolver;
    @Mock private PrintWriter mMockPrintWriter;
    @Captor private ArgumentCaptor<String> mCaptorString;

    private GarageModeService mService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);
        mService = new GarageModeService(mMockContext, mMockController);
    }

    @Test
    public void testDump_shouldSucceed() {
        when(mMockController.isGarageModeActive()).thenReturn(true);

        mService.dump(mMockPrintWriter);
        verify(mMockPrintWriter, atLeastOnce()).println(mCaptorString.capture());
        List<String> strings = mCaptorString.getAllValues();
        assertThat(strings.get(0)).isEqualTo("GarageModeInProgress true");
    }
}
