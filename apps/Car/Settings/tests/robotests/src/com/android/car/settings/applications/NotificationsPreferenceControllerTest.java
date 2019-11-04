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

package com.android.car.settings.applications;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.INotificationManager;
import android.app.NotificationChannel;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import androidx.lifecycle.Lifecycle;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class NotificationsPreferenceControllerTest {
    private static final String PKG_NAME = "package.name";
    private static final int UID = 1001010;
    private Context mContext;
    private NotificationsPreferenceController mController;
    private PreferenceControllerTestHelper<NotificationsPreferenceController>
            mPreferenceControllerHelper;
    private TwoStatePreference mTwoStatePreference;
    @Mock
    private INotificationManager mMockManager;
    @Mock
    private NotificationChannel mMockChannel;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = RuntimeEnvironment.application;
        mTwoStatePreference = new SwitchPreference(mContext);

        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                NotificationsPreferenceController.class, mTwoStatePreference);
        mController = mPreferenceControllerHelper.getController();
        mController.mNotificationManager = mMockManager;

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = PKG_NAME;

        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = PKG_NAME;
        packageInfo.applicationInfo = applicationInfo;
        packageInfo.applicationInfo.uid = UID;
        mController.setPackageInfo(packageInfo);
    }

    @Test
    public void onCreate_notificationEnabled_isChecked() throws Exception {
        when(mMockManager.areNotificationsEnabledForPackage(PKG_NAME, UID)).thenReturn(true);

        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        assertThat(mTwoStatePreference.isChecked()).isTrue();
    }

    @Test
    public void onCreate_notificationDisabled_isNotChecked() throws Exception {
        when(mMockManager.areNotificationsEnabledForPackage(PKG_NAME, UID)).thenReturn(false);

        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        assertThat(mTwoStatePreference.isChecked()).isFalse();
    }

    @Test
    public void callChangeListener_setEnable_enablingNotification() throws Exception {
        when(mMockManager.onlyHasDefaultChannel(PKG_NAME, UID)).thenReturn(false);

        mTwoStatePreference.callChangeListener(true);

        verify(mMockManager).setNotificationsEnabledForPackage(PKG_NAME, UID, true);
    }

    @Test
    public void callChangeListener_setDisable_disablingNotification() throws Exception {
        when(mMockManager.onlyHasDefaultChannel(PKG_NAME, UID)).thenReturn(false);

        mTwoStatePreference.callChangeListener(false);

        verify(mMockManager).setNotificationsEnabledForPackage(PKG_NAME, UID, false);
    }

    @Test
    public void callChangeListener_onlyHasDefaultChannel_updateChannel() throws Exception {
        when(mMockManager.onlyHasDefaultChannel(PKG_NAME, UID)).thenReturn(true);
        when(mMockManager
                .getNotificationChannelForPackage(
                        PKG_NAME, UID, NotificationChannel.DEFAULT_CHANNEL_ID, true))
                .thenReturn(mMockChannel);

        mTwoStatePreference.callChangeListener(true);

        verify(mMockManager).updateNotificationChannelForPackage(PKG_NAME, UID, mMockChannel);
    }
}
