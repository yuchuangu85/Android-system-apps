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

package com.android.car.settings.wifi;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.Lifecycle;
import androidx.preference.EditTextPreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowLocalBroadcastManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowLocalBroadcastManager.class})
public class NetworkNamePreferenceControllerTest {

    private static final String TEST_SSID = "test_ssid";

    private Context mContext;
    private EditTextPreference mEditTextPreference;
    private NetworkNamePreferenceController mController;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mEditTextPreference = new EditTextPreference(mContext);
        PreferenceControllerTestHelper<NetworkNamePreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        NetworkNamePreferenceController.class, mEditTextPreference);
        mController = controllerHelper.getController();
        controllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @After
    public void tearDown() {
        ShadowLocalBroadcastManager.reset();
    }

    @Test
    public void refreshUi_defaultState_showsDefaultString() {
        mController.refreshUi();
        assertThat(mEditTextPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.default_network_name_summary));
    }

    @Test
    public void handlePreferenceChanged_newTextIsSet() {
        mEditTextPreference.setText("Old value");
        mEditTextPreference.callChangeListener("New value");
        assertThat(mEditTextPreference.getSummary()).isEqualTo("New value");
    }

    @Test
    public void handlePreferenceChanged_broadcastIsSent() {
        String value = "New value";
        mEditTextPreference.callChangeListener(value);

        List<Intent> intents = ShadowLocalBroadcastManager.getSentBroadcastIntents();
        assertThat(intents).hasSize(1);
        assertThat(intents.get(0).getAction()).isEqualTo(
                NetworkNamePreferenceController.ACTION_NAME_CHANGE);
        assertThat(intents.get(0).getStringExtra(
                NetworkNamePreferenceController.KEY_NETWORK_NAME)).isEqualTo(value);
    }
}
