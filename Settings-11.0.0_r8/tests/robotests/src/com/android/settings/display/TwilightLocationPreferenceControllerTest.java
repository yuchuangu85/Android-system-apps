/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.settings.display;

import static com.android.settings.core.BasePreferenceController.AVAILABLE_UNSEARCHABLE;
import static com.android.settings.core.BasePreferenceController.CONDITIONALLY_UNAVAILABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.location.LocationManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class TwilightLocationPreferenceControllerTest {
    private static final String TEST_KEY = "test_key";

    private Context mContext;
    private TwilightLocationPreferenceController mController;

    @Mock
    private LocationManager mLocationManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(RuntimeEnvironment.application);
        when(mContext.getSystemService(LocationManager.class)).thenReturn(mLocationManager);
        mController = new TwilightLocationPreferenceController(mContext, TEST_KEY);
    }

    @Test
    public void getAvailabilityStatus_locationEnabled_shouldBeCONDITIONALLY_UNAVAILABLE() {
        when(mLocationManager.isLocationEnabled()).thenReturn(true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_locationDisabled_shouldBeAVAILABLE_UNSEARCHABLE() {
        when(mLocationManager.isLocationEnabled()).thenReturn(false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE_UNSEARCHABLE);
    }
}
