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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.NetworkTemplate;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.FragmentController;
import com.android.settingslib.NetworkPolicyEditor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class CycleResetDayOfMonthPickerPreferenceControllerTest {

    private Preference mPreference;
    private PreferenceControllerTestHelper<CycleResetDayOfMonthPickerPreferenceController>
            mControllerHelper;
    @Mock
    private NetworkPolicyEditor mPolicyEditor;
    @Mock
    private NetworkTemplate mNetworkTemplate;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = RuntimeEnvironment.application;

        mPreference = new Preference(context);
        mControllerHelper = new PreferenceControllerTestHelper<>(context,
                CycleResetDayOfMonthPickerPreferenceController.class, mPreference);

        mControllerHelper.getController().setNetworkPolicyEditor(mPolicyEditor);
        mControllerHelper.getController().setNetworkTemplate(mNetworkTemplate);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @Test
    public void performClick_startsDialogWithStartingValue() {
        int startingValue = 15;
        when(mPolicyEditor.getPolicyCycleDay(mNetworkTemplate)).thenReturn(startingValue);
        mControllerHelper.getController().refreshUi();
        mPreference.performClick();

        ArgumentCaptor<UsageCycleResetDayOfMonthPickerDialog> dialogCaptor =
                ArgumentCaptor.forClass(UsageCycleResetDayOfMonthPickerDialog.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(
                dialogCaptor.capture(), anyString());

        UsageCycleResetDayOfMonthPickerDialog dialog = dialogCaptor.getValue();

        // Dialog was never started because FragmentController is mocked.
        FragmentController<Fragment> fragmentController = FragmentController.of(new Fragment());
        Fragment testFragment = fragmentController.setup();
        dialog.show(testFragment.getFragmentManager(), null);

        assertThat(dialog.getSelectedDayOfMonth()).isEqualTo(startingValue);
    }
}
