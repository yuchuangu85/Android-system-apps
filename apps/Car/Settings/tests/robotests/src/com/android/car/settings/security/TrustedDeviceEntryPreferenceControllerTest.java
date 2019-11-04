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

package com.android.car.settings.security;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.trust.CarTrustAgentEnrollmentManager;
import android.car.trust.TrustedDeviceInfo;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCar;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCar.class})
public class TrustedDeviceEntryPreferenceControllerTest {

    private Context mContext;
    private PreferenceControllerTestHelper<TrustedDeviceEntryPreferenceController>
            mPreferenceControllerHelper;
    private Preference mTrustedDevicePreference;
    @Mock
    private CarTrustAgentEnrollmentManager mMockCarTrustAgentEnrollmentManager;
    private TrustedDeviceEntryPreferenceController mController;
    private CarUserManagerHelper mCarUserManagerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mTrustedDevicePreference = new Preference(mContext);
        ShadowCar.setCarManager(Car.CAR_TRUST_AGENT_ENROLLMENT_SERVICE,
                mMockCarTrustAgentEnrollmentManager);
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                TrustedDeviceEntryPreferenceController.class, mTrustedDevicePreference);
        mController = mPreferenceControllerHelper.getController();
        mCarUserManagerHelper = new CarUserManagerHelper(mContext);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
    }

    @Test
    public void testHandlePreferenceClicked_listenerTriggered() {
        mTrustedDevicePreference.performClick();
        verify(mPreferenceControllerHelper.getMockFragmentController()).launchFragment(
                any(ChooseTrustedDeviceFragment.class));
    }

    @Test
    public void testUpdateState() throws CarNotConnectedException {
        List<TrustedDeviceInfo> devices = new ArrayList<>();
        devices.add(new TrustedDeviceInfo(1, "", ""));
        devices.add(new TrustedDeviceInfo(2, "", ""));
        when(mMockCarTrustAgentEnrollmentManager.getEnrolledDeviceInfoForUser(
                mCarUserManagerHelper.getCurrentProcessUserId()))
                .thenReturn(devices);
        mController.refreshUi();
        assertThat(mTrustedDevicePreference.getSummary()).isEqualTo("2 devices");
    }
}
