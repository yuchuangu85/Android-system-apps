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

package com.android.car.settings.wifi;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.Lifecycle;
import androidx.preference.ListPreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowLocalBroadcastManager;
import com.android.settingslib.wifi.AccessPoint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowLocalBroadcastManager.class})
public class NetworkSecurityPreferenceControllerTest {

    private Context mContext;
    private ListPreference mListPreference;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mListPreference = new ListPreference(mContext);
        PreferenceControllerTestHelper<NetworkSecurityPreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        NetworkSecurityPreferenceController.class, mListPreference);
        controllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @After
    public void tearDown() {
        ShadowLocalBroadcastManager.reset();
    }

    @Test
    public void handlePreferenceChanged_unsecureNetwork_summaryUpdated() {
        String value = Integer.toString(AccessPoint.SECURITY_NONE);
        mListPreference.callChangeListener(value);

        assertThat(mListPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.wifi_security_none));
    }

    @Test
    public void handlePreferenceChanged_pskNetwork_summaryUpdated() {
        String value = Integer.toString(AccessPoint.SECURITY_PSK);
        mListPreference.callChangeListener(value);

        assertThat(mListPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.wifi_security_psk_generic));
    }

    @Test
    public void handlePreferenceChanged_broadcastIsSent() {
        String value = Integer.toString(AccessPoint.SECURITY_PSK);
        mListPreference.callChangeListener(value);

        List<Intent> intents = ShadowLocalBroadcastManager.getSentBroadcastIntents();
        assertThat(intents).hasSize(1);
        assertThat(intents.get(0).getAction()).isEqualTo(
                NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE);
        assertThat(intents.get(0).getIntExtra(NetworkSecurityPreferenceController.KEY_SECURITY_TYPE,
                AccessPoint.SECURITY_NONE)).isEqualTo(Integer.parseInt(value));
    }
}
