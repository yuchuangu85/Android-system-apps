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

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;
import com.android.car.settings.testutils.FragmentController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class NetworkNameRestrictedPasswordEditTextPreferenceTest {

    private static final String KEY = "test_key";

    private Context mContext;
    private NetworkNameRestrictedPasswordEditTextPreference mPreference;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        FragmentController<TestSettingsFragment> fragmentController = FragmentController.of(
                new TestSettingsFragment());
        TestSettingsFragment fragment = fragmentController.get();
        fragmentController.setup();

        mPreference = new NetworkNameRestrictedPasswordEditTextPreference(mContext);
        mPreference.setKey(KEY);
        fragment.getPreferenceScreen().addPreference(mPreference);
    }

    @Test
    public void performClick_noName_toastShown() {
        mPreference.performClick();

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(
                mContext.getString(R.string.wifi_no_network_name));
    }

    @Test
    public void performClick_hasName_showsDialog() {
        mPreference.setNetworkName("test_name");
        mPreference.performClick();

        assertThat(ShadowDialog.getLatestDialog()).isNotNull();
    }

    /** Concrete {@link SettingsFragment} for testing. */
    public static class TestSettingsFragment extends SettingsFragment {
        @Override
        protected int getPreferenceScreenResId() {
            return R.xml.settings_fragment;
        }
    }
}
