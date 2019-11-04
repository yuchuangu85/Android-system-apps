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

import static android.net.NetworkPolicy.LIMIT_DISABLED;

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
import com.android.car.settings.common.ConfirmationDialogFragment;
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
public class DataLimitPreferenceControllerTest {

    private static final long GIB_IN_BYTES = 1024 * 1024 * 1024;
    private static final long EPSILON = 100;

    private TwoStatePreference mEnablePreference;
    private Preference mLimitPreference;
    private DataLimitPreferenceController mController;
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
        PreferenceControllerTestHelper<DataLimitPreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(context,
                        DataLimitPreferenceController.class, preferenceGroup);
        mController = controllerHelper.getController();
        mFragmentController = controllerHelper.getMockFragmentController();

        mEnablePreference = new SwitchPreference(context);
        mEnablePreference.setKey(context.getString(R.string.pk_data_set_limit));
        preferenceGroup.addPreference(mEnablePreference);
        mLimitPreference = new Preference(context);
        mLimitPreference.setKey(context.getString(R.string.pk_data_limit));
        preferenceGroup.addPreference(mLimitPreference);

        mController.setNetworkPolicyEditor(mPolicyEditor);
        mController.setNetworkTemplate(mNetworkTemplate);
        controllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @Test
    public void refreshUi_limitDisabled_summaryEmpty() {
        when(mPolicyEditor.getPolicyLimitBytes(mNetworkTemplate)).thenReturn(LIMIT_DISABLED);
        mController.refreshUi();

        assertThat(mLimitPreference.getSummary()).isNull();
    }

    @Test
    public void refreshUi_limitDisabled_preferenceDisabled() {
        when(mPolicyEditor.getPolicyLimitBytes(mNetworkTemplate)).thenReturn(LIMIT_DISABLED);
        mController.refreshUi();

        assertThat(mLimitPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_limitDisabled_switchUnchecked() {
        when(mPolicyEditor.getPolicyLimitBytes(mNetworkTemplate)).thenReturn(LIMIT_DISABLED);
        mController.refreshUi();

        assertThat(mEnablePreference.isChecked()).isFalse();
    }

    @Test
    public void refreshUi_limitEnabled_summaryPopulated() {
        when(mPolicyEditor.getPolicyLimitBytes(mNetworkTemplate)).thenReturn(5 * GIB_IN_BYTES);
        mController.refreshUi();

        assertThat(mLimitPreference.getSummary().toString()).isNotEmpty();
    }

    @Test
    public void refreshUi_limitEnabled_preferenceEnabled() {
        when(mPolicyEditor.getPolicyLimitBytes(mNetworkTemplate)).thenReturn(5 * GIB_IN_BYTES);
        mController.refreshUi();

        assertThat(mLimitPreference.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_limitEnabled_switchChecked() {
        when(mPolicyEditor.getPolicyLimitBytes(mNetworkTemplate)).thenReturn(5 * GIB_IN_BYTES);
        mController.refreshUi();

        assertThat(mEnablePreference.isChecked()).isTrue();
    }

    @Test
    public void onPreferenceChanged_toggleFalse_limitBytesDisabled() {
        mEnablePreference.callChangeListener(false);
        verify(mPolicyEditor).setPolicyLimitBytes(mNetworkTemplate, LIMIT_DISABLED);
    }

    @Test
    public void onPreferenceChanged_toggleTrue_showsDialog() {
        mEnablePreference.callChangeListener(true);

        verify(mFragmentController).showDialog(any(ConfirmationDialogFragment.class),
                eq(ConfirmationDialogFragment.TAG));
    }

    @Test
    public void onDialogConfirm_noWarningThreshold_setsLimitTo5GB() {
        mController.onConfirm(null);

        verify(mPolicyEditor).setPolicyLimitBytes(mNetworkTemplate, 5 * GIB_IN_BYTES);
    }

    @Test
    public void onDialogConfirm_hasWarningThreshold_setsLimitToWithMultiplier() {
        when(mPolicyEditor.getPolicyWarningBytes(mNetworkTemplate)).thenReturn(5 * GIB_IN_BYTES);
        mController.onConfirm(null);

        ArgumentCaptor<Long> setLimit = ArgumentCaptor.forClass(Long.class);
        verify(mPolicyEditor).setPolicyLimitBytes(eq(mNetworkTemplate), setLimit.capture());

        long setValue = setLimit.getValue();
        // Due to precision errors, add and subtract a small epsilon.
        assertThat(setValue).isGreaterThan(
                (long) (5 * GIB_IN_BYTES * DataLimitPreferenceController.LIMIT_BYTES_MULTIPLIER)
                        - EPSILON);
        assertThat(setValue).isLessThan(
                (long) (5 * GIB_IN_BYTES * DataLimitPreferenceController.LIMIT_BYTES_MULTIPLIER)
                        + EPSILON);
    }

    @Test
    public void onPreferenceClicked_showsPickerDialog() {
        mLimitPreference.performClick();

        verify(mFragmentController).showDialog(any(UsageBytesThresholdPickerDialog.class),
                eq(UsageBytesThresholdPickerDialog.TAG));
    }
}
