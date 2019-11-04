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

import android.app.Service;
import android.content.Intent;
import android.location.LocationManager;
import android.widget.Switch;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseTestActivity;
import com.android.car.settings.testutils.ShadowLocationManager;
import com.android.car.settings.testutils.ShadowSecureSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowSecureSettings.class, ShadowLocationManager.class})
public class LocationSettingsFragmentTest {
    private BaseTestActivity mActivity;
    private LocationManager mLocationManager;
    private Switch mLocationSwitch;

    @Before
    public void setUp() {
        mLocationManager = (LocationManager) RuntimeEnvironment.application
                .getSystemService(Service.LOCATION_SERVICE);

        mActivity = Robolectric.setupActivity(BaseTestActivity.class);
    }

    @After
    public void tearDown() {
        ShadowSecureSettings.reset();
    }

    @Test
    public void locationSwitch_toggle_shouldBroadcastLocationModeChangedIntent() {
        initFragment();
        mLocationSwitch.setChecked(!mLocationSwitch.isChecked());

        List<Intent> intentsFired = ShadowApplication.getInstance().getBroadcastIntents();
        assertThat(intentsFired).hasSize(1);
        Intent intentFired = intentsFired.get(0);
        assertThat(intentFired.getAction()).isEqualTo(LocationManager.MODE_CHANGED_ACTION);
    }

    @Test
    public void locationSwitch_checked_enablesLocation() {
        initFragment();
        mLocationSwitch.setChecked(true);

        assertThat(mLocationManager.isLocationEnabled()).isTrue();
    }

    @Test
    public void locationSwitch_unchecked_disablesLocation() {
        initFragment();
        mLocationSwitch.setChecked(false);

        assertThat(mLocationManager.isLocationEnabled()).isFalse();
    }

    private void initFragment() {
        mActivity.launchFragment(new LocationSettingsFragment());
        mLocationSwitch = (Switch) mActivity.findViewById(R.id.toggle_switch);
    }
}
