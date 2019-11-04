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

package com.android.car.settings.applications.assist;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

import androidx.lifecycle.Lifecycle;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class TextContextPreferenceControllerTest {

    private static final int TEST_USER_ID = 10;
    private static final String TEST_PACKAGE_NAME = "com.test.package";
    private static final String TEST_SERVICE = "TestService";

    private Context mContext;
    private TwoStatePreference mTwoStatePreference;
    private PreferenceControllerTestHelper<TextContextPreferenceController>
            mControllerHelper;
    private TextContextPreferenceController mController;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        when(mCarUserManagerHelper.getCurrentProcessUserId()).thenReturn(TEST_USER_ID);

        mContext = RuntimeEnvironment.application;
        mTwoStatePreference = new SwitchPreference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                TextContextPreferenceController.class, mTwoStatePreference);
        mController = mControllerHelper.getController();

        String key = new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE).flattenToString();
        Settings.Secure.putStringForUser(mContext.getContentResolver(), Settings.Secure.ASSISTANT,
                key, TEST_USER_ID);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @Test
    public void refreshUi_contextEnabled_preferenceChecked() {
        mTwoStatePreference.setChecked(false);

        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ASSIST_STRUCTURE_ENABLED, 1);
        mController.refreshUi();

        assertThat(mTwoStatePreference.isChecked()).isTrue();
    }

    @Test
    public void refreshUi_contextDisabled_preferenceUnchecked() {
        mTwoStatePreference.setChecked(true);

        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ASSIST_STRUCTURE_ENABLED, 0);
        mController.refreshUi();

        assertThat(mTwoStatePreference.isChecked()).isFalse();
    }

    @Test
    public void callChangeListener_toggleTrue_contextEnabled() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ASSIST_STRUCTURE_ENABLED, 0);
        mTwoStatePreference.callChangeListener(true);

        assertThat(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.ASSIST_STRUCTURE_ENABLED, 0)).isEqualTo(1);
    }

    @Test
    public void callChangeListener_toggleFalse_contextDisabled() {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.ASSIST_STRUCTURE_ENABLED, 1);
        mTwoStatePreference.callChangeListener(false);

        assertThat(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.ASSIST_STRUCTURE_ENABLED, 1)).isEqualTo(0);
    }
}
