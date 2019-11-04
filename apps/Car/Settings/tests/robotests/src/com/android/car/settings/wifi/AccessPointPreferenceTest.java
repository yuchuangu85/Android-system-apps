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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.AlertDialog;
import android.content.Context;
import android.net.wifi.WifiConfiguration;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;
import com.android.car.settings.testutils.FragmentController;
import com.android.settingslib.wifi.AccessPoint;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class AccessPointPreferenceTest {

    private static final String TEST_KEY = "test_key";
    private AccessPointPreference mPreference;

    @Mock
    private AccessPoint mAccessPoint;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = RuntimeEnvironment.application;
        FragmentController<TestSettingsFragment> fragmentController = FragmentController.of(
                new TestSettingsFragment());
        TestSettingsFragment fragment = fragmentController.get();
        fragmentController.setup();

        mPreference = new AccessPointPreference(context, mAccessPoint);
        mPreference.setKey(TEST_KEY);
        fragment.getPreferenceScreen().addPreference(mPreference);
    }

    @Test
    public void onClick_securityTypeNone_doesntOpenDialog() {
        when(mAccessPoint.getSecurity()).thenReturn(AccessPoint.SECURITY_NONE);
        mPreference.onClick();

        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertThat(dialog).isNull();
    }

    @Test
    public void onClick_hasSecurity_isSaved_correctPassword_doesntOpenDialog() {
        WifiConfiguration config = mock(WifiConfiguration.class);
        WifiConfiguration.NetworkSelectionStatus status = mock(
                WifiConfiguration.NetworkSelectionStatus.class);
        when(mAccessPoint.getSecurity()).thenReturn(AccessPoint.SECURITY_PSK);
        when(mAccessPoint.isSaved()).thenReturn(true);
        when(mAccessPoint.getConfig()).thenReturn(config);
        when(config.getNetworkSelectionStatus()).thenReturn(status);
        when(status.isNetworkEnabled()).thenReturn(true);
        mPreference.onClick();

        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertThat(dialog).isNull();

    }

    @Test
    public void onClick_hasSecurity_isSaved_incorrectPassword_opensDialog() {
        WifiConfiguration config = mock(WifiConfiguration.class);
        WifiConfiguration.NetworkSelectionStatus status = mock(
                WifiConfiguration.NetworkSelectionStatus.class);
        when(mAccessPoint.getSecurity()).thenReturn(AccessPoint.SECURITY_PSK);
        when(mAccessPoint.isSaved()).thenReturn(true);
        when(mAccessPoint.getConfig()).thenReturn(config);
        when(config.getNetworkSelectionStatus()).thenReturn(status);
        when(status.isNetworkEnabled()).thenReturn(false);
        when(status.getNetworkSelectionDisableReason()).thenReturn(
                WifiConfiguration.NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD);
        mPreference.onClick();

        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertThat(dialog).isNotNull();
    }

    @Test
    public void onClick_hasSecurity_isNotSaved_opensDialog() {
        when(mAccessPoint.getSecurity()).thenReturn(AccessPoint.SECURITY_PSK);
        when(mAccessPoint.isSaved()).thenReturn(false);
        mPreference.onClick();

        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertThat(dialog).isNotNull();
    }

    /** Concrete {@link SettingsFragment} for testing. */
    public static class TestSettingsFragment extends SettingsFragment {
        @Override
        protected int getPreferenceScreenResId() {
            return R.xml.settings_fragment;
        }
    }
}
