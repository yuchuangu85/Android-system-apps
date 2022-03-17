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

package com.android.settings.nfc;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class NfcDetectionPointControllerTest {

    private NfcDetectionPointController mController;

    @Before
    public void setUp() {
        mController = new NfcDetectionPointController(RuntimeEnvironment.application, "fakeKey");
    }

    @Test
    public void getAvailabilityStatus_withConfigIsTrue_returnAvailable() {
        mController.setConfig(true);
        assertThat(mController.getAvailabilityStatus())
            .isEqualTo(NfcDetectionPointController.AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_withConfigIsFalse_returnUnavailable() {
        mController.setConfig(false);
        assertThat(mController.getAvailabilityStatus())
            .isNotEqualTo(NfcDetectionPointController.AVAILABLE);
    }
}
