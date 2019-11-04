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

package com.android.car.settings.security;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.car.Car;
import android.car.trust.CarTrustAgentEnrollmentManager;
import android.car.trust.TrustedDeviceInfo;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCar;
import com.android.car.settings.testutils.ShadowLockPatternUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link TrustedDeviceListPreferenceController}.
 */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCar.class, ShadowLockPatternUtils.class})
public class TrustedDeviceListPreferenceControllerTest {

    List<TrustedDeviceInfo> mUpdatedDevices = new ArrayList<>();
    List<TrustedDeviceInfo> mDevices = new ArrayList<>();
    private Context mContext;
    private PreferenceControllerTestHelper<TrustedDeviceListPreferenceController>
            mPreferenceControllerHelper;
    @Mock
    private CarTrustAgentEnrollmentManager mMockCarTrustAgentEnrollmentManager;
    private CarUserManagerHelper mCarUserManagerHelper;
    private PreferenceGroup mPreferenceGroup;
    private TrustedDeviceListPreferenceController mController;
    private TrustedDeviceInfo mTestDevice1 = new TrustedDeviceInfo(1, "", "");
    private TrustedDeviceInfo mTestDevice2 = new TrustedDeviceInfo(2, "", "");
    private TrustedDeviceInfo mTestDevice3 = new TrustedDeviceInfo(3, "", "");

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        ShadowCar.setCarManager(Car.CAR_TRUST_AGENT_ENROLLMENT_SERVICE,
                mMockCarTrustAgentEnrollmentManager);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                TrustedDeviceListPreferenceController.class, mPreferenceGroup);
        mController = mPreferenceControllerHelper.getController();
        mCarUserManagerHelper = new CarUserManagerHelper(mContext);
        mDevices.add(mTestDevice1);
        mDevices.add(mTestDevice2);
        mUpdatedDevices.add(mTestDevice1);
        mUpdatedDevices.add(mTestDevice2);
        mUpdatedDevices.add(mTestDevice3);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
    }

    @After
    public void tearDown() {
        ShadowCar.reset();
        ShadowLockPatternUtils.reset();
    }

    @Test
    public void onDeviceRemoved_refreshUi() {
        ShadowLockPatternUtils.setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        when(mMockCarTrustAgentEnrollmentManager.getEnrolledDeviceInfoForUser(
                mCarUserManagerHelper.getCurrentProcessUserId())).thenReturn(mUpdatedDevices);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(3);

        ArgumentCaptor<CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback> callBack =
                ArgumentCaptor.forClass(
                        CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback.class);
        verify(mMockCarTrustAgentEnrollmentManager).setEnrollmentCallback(callBack.capture());
        mUpdatedDevices.remove(0);
        when(mMockCarTrustAgentEnrollmentManager.getEnrolledDeviceInfoForUser(
                mCarUserManagerHelper.getCurrentProcessUserId())).thenReturn(mUpdatedDevices);
        callBack.getValue().onEscrowTokenRemoved(mUpdatedDevices.get(0).getHandle());

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);
    }

    @Test
    public void onDeviceAdded_refreshUi() {
        when(mMockCarTrustAgentEnrollmentManager.getEnrolledDeviceInfoForUser(
                mCarUserManagerHelper.getCurrentProcessUserId())).thenReturn(mDevices);
        ShadowLockPatternUtils.setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);

        ArgumentCaptor<CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback> callBack =
                ArgumentCaptor.forClass(
                        CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback.class);
        when(mMockCarTrustAgentEnrollmentManager.getEnrolledDeviceInfoForUser(
                mCarUserManagerHelper.getCurrentProcessUserId())).thenReturn(mUpdatedDevices);
        verify(mMockCarTrustAgentEnrollmentManager).setEnrollmentCallback(callBack.capture());

        callBack.getValue().onEscrowTokenActiveStateChanged(mUpdatedDevices.get(0).getHandle(),
                true);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(3);
    }

    @Test
    public void refreshUi_noDevices_hasPassword_hidesGroup() {
        ShadowLockPatternUtils.setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        when(mMockCarTrustAgentEnrollmentManager.getEnrolledDeviceInfoForUser(
                mCarUserManagerHelper.getCurrentProcessUserId())).thenReturn(
                Collections.emptyList());

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
        assertThat(mPreferenceGroup.isVisible()).isFalse();
    }

    @Test
    public void refreshUi_devices_hasPassword_showsGroup() {
        ShadowLockPatternUtils.setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        when(mMockCarTrustAgentEnrollmentManager.getEnrolledDeviceInfoForUser(
                mCarUserManagerHelper.getCurrentProcessUserId())).thenReturn(mUpdatedDevices);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(3);
        assertThat(mPreferenceGroup.isVisible()).isTrue();
    }

    @Test
    public void refreshUi_noPassword_showAuthenticationReminderPreference() {
        ShadowLockPatternUtils.setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);


        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreferenceGroup.getPreference(0).getSummary()).isEqualTo(
                mContext.getString(R.string.trusted_device_set_authentication_reminder));
    }

    @Test
    public void onPreferenceClicked_hasPassword_showDialog() {
        ShadowLockPatternUtils.setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        when(mMockCarTrustAgentEnrollmentManager.getEnrolledDeviceInfoForUser(
                mCarUserManagerHelper.getCurrentProcessUserId())).thenReturn(mDevices);
        mController.refreshUi();
        Preference p = mPreferenceGroup.getPreference(0);

        p.performClick();

        verify(mPreferenceControllerHelper.getMockFragmentController()).showDialog(
                any(ConfirmRemoveDeviceDialog.class), anyString());
    }

    @Test
    public void onRemoveDeviceDialogConfirmed_remoeEscrwoToken() {
        mController.mConfirmRemoveDeviceListener.onConfirmRemoveDevice(1);

        verify(mMockCarTrustAgentEnrollmentManager).removeEscrowToken(1,
                mCarUserManagerHelper.getCurrentProcessUserId());
    }
}
