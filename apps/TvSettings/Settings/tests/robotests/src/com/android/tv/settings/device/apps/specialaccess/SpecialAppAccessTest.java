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

package com.android.tv.settings.device.apps.specialaccess;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.robolectric.shadow.api.Shadow.extract;

import android.app.ActivityManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowActivityManager;

@RunWith(RobolectricTestRunner.class)
public class SpecialAppAccessTest {

    @Spy
    private SpecialAppAccess mSpecialAppAccess;

    private ShadowActivityManager mActivityManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mActivityManager = extract(RuntimeEnvironment.application
                .getSystemService(ActivityManager.class));
        doReturn(RuntimeEnvironment.application).when(mSpecialAppAccess).getContext();
        mSpecialAppAccess.onAttach(RuntimeEnvironment.application);
    }

    @Test
    public void testFeaturesDisabledOnLowRam() {
        mActivityManager.setIsLowRamDevice(true);

        mSpecialAppAccess.updatePreferenceStates();

        verify(mSpecialAppAccess, times(1)).removePreference(SpecialAppAccess.KEY_FEATURE_PIP);
        verify(mSpecialAppAccess, times(1)).removePreference(
                SpecialAppAccess.KEY_FEATURE_NOTIFICATION_ACCESS);
    }

    @Test
    public void testFeaturesNotDisabledNonLowRam() {
        mActivityManager.setIsLowRamDevice(false);

        mSpecialAppAccess.updatePreferenceStates();

        verify(mSpecialAppAccess, never()).removePreference(SpecialAppAccess.KEY_FEATURE_PIP);
        verify(mSpecialAppAccess, never()).removePreference(
                SpecialAppAccess.KEY_FEATURE_NOTIFICATION_ACCESS);
    }
}
