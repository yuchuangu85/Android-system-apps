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
package com.android.car.settings.location;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowLocationManager;
import com.android.car.settings.testutils.ShadowSecureSettings;
import com.android.settingslib.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowSecureSettings.class, ShadowLocationManager.class})
public class RecentLocationRequestsEntryPreferenceControllerTest {

    private RecentLocationRequestsEntryPreferenceController mController;
    private Preference mPreference;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.application;
        mPreference = new Preference(context);
        PreferenceControllerTestHelper<RecentLocationRequestsEntryPreferenceController>
                controllerHelper = new PreferenceControllerTestHelper<>(context,
                RecentLocationRequestsEntryPreferenceController.class, mPreference);
        mController = controllerHelper.getController();
        controllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
    }

    @After
    public void tearDown() {
        ShadowSecureSettings.reset();
    }

    @Test
    public void refreshUi_locationOn_preferenceIsEnabled() {
        setLocationEnabled(true);
        mController.refreshUi();

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_locationOff_preferenceIsDisabled() {
        setLocationEnabled(false);
        mController.refreshUi();

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void locationModeChangedBroadcastSent_locationOff_preferenceIsDisabled() {
        setLocationEnabled(true);
        mController.refreshUi();
        setLocationEnabled(false);

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void locationModeChangedBroadcastSent_locationOn_preferenceIsEnabled() {
        setLocationEnabled(false);
        mController.refreshUi();
        setLocationEnabled(true);

        assertThat(mPreference.isEnabled()).isTrue();
    }

    private void setLocationEnabled(boolean enabled) {
        Utils.updateLocationEnabled(RuntimeEnvironment.application, enabled, UserHandle.myUserId(),
                Settings.Secure.LOCATION_CHANGER_SYSTEM_SETTINGS);
    }
}
