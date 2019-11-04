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

package com.android.car.settings.datausage;

import static android.net.NetworkPolicy.WARNING_DISABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.NetworkTemplate;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.settingslib.NetworkPolicyEditor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class DataWarningPreferenceControllerTest {

    private static final long BYTES_IN_GIGABYTE = 1024 * 1024 * 1024;

    private TwoStatePreference mEnablePreference;
    private Preference mWarningPreference;
    private DataWarningPreferenceController mController;
    private FragmentController mFragmentController;
    @Mock
    private NetworkPolicyEditor mPolicyEditor;
    @Mock
    private NetworkTemplate mNetworkTemplate;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = RuntimeEnvironment.application;

        PreferenceGroup preferenceGroup = new LogicalPreferenceGroup(context);
        PreferenceControllerTestHelper<DataWarningPreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(context,
                        DataWarningPreferenceController.class, preferenceGroup);
        mController = controllerHelper.getController();
        mFragmentController = controllerHelper.getMockFragmentController();

        mEnablePreference = new SwitchPreference(context);
        mEnablePreference.setKey(context.getString(R.string.pk_data_set_warning));
        preferenceGroup.addPreference(mEnablePreference);
        mWarningPreference = new Preference(context);
        mWarningPreference.setKey(context.getString(R.string.pk_data_warning));
        preferenceGroup.addPreference(mWarningPreference);

        mController.setNetworkPolicyEditor(mPolicyEditor);
        mController.setNetworkTemplate(mNetworkTemplate);
        controllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @Test
    public void refreshUi_warningDisabled_summaryEmpty() {
        when(mPolicyEditor.getPolicyWarningBytes(mNetworkTemplate)).thenReturn(WARNING_DISABLED);
        mController.refreshUi();

        assertThat(mWarningPreference.getSummary()).isNull();
    }

    @Test
    public void refreshUi_warningDisabled_preferenceDisabled() {
        when(mPolicyEditor.getPolicyWarningBytes(mNetworkTemplate)).thenReturn(WARNING_DISABLED);
        mController.refreshUi();

        assertThat(mWarningPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_warningDisabled_switchUnchecked() {
        when(mPolicyEditor.getPolicyWarningBytes(mNetworkTemplate)).thenReturn(WARNING_DISABLED);
        mController.refreshUi();

        assertThat(mEnablePreference.isChecked()).isFalse();
    }

    @Test
    public void refreshUi_warningEnabled_summaryPopulated() {
        when(mPolicyEditor.getPolicyWarningBytes(mNetworkTemplate)).thenReturn(
                3 * BYTES_IN_GIGABYTE);
        mController.refreshUi();

        assertThat(mWarningPreference.getSummary().toString()).isNotEmpty();
    }

    @Test
    public void refreshUi_warningEnabled_preferenceEnabled() {
        when(mPolicyEditor.getPolicyWarningBytes(mNetworkTemplate)).thenReturn(
                3 * BYTES_IN_GIGABYTE);
        mController.refreshUi();

        assertThat(mWarningPreference.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_warningEnabled_switchChecked() {
        when(mPolicyEditor.getPolicyWarningBytes(mNetworkTemplate)).thenReturn(
                3 * BYTES_IN_GIGABYTE);
        mController.refreshUi();

        assertThat(mEnablePreference.isChecked()).isTrue();
    }

    @Test
    public void onPreferenceChanged_toggleFalse_warningBytesDisabled() {
        mEnablePreference.callChangeListener(false);
        verify(mPolicyEditor).setPolicyWarningBytes(mNetworkTemplate, WARNING_DISABLED);
    }

    @Test
    public void onPreferenceChanged_toggleTrue_warningBytesNotDisabled() {
        mEnablePreference.callChangeListener(true);

        ArgumentCaptor<Long> setWarning = ArgumentCaptor.forClass(Long.class);
        verify(mPolicyEditor).setPolicyWarningBytes(eq(mNetworkTemplate), setWarning.capture());
        assertThat(setWarning.getValue()).isNotEqualTo(WARNING_DISABLED);
    }

    @Test
    public void onPreferenceClicked_showsPickerDialog() {
        mWarningPreference.performClick();

        verify(mFragmentController).showDialog(any(UsageBytesThresholdPickerDialog.class),
                eq(UsageBytesThresholdPickerDialog.TAG));
    }
}
