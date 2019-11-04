/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.settings.applications.specialaccess;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.app.AutomaticZenRule;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.service.notification.NotificationListenerService;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowApplicationPackageManager;
import com.android.car.settings.testutils.ShadowNotificationManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

/** Unit test for {@link NotificationAccessPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowApplicationPackageManager.class, ShadowNotificationManager.class})
public class NotificationAccessPreferenceControllerTest {

    private Context mContext;
    private PreferenceGroup mPreferenceGroup;
    private PreferenceControllerTestHelper<NotificationAccessPreferenceController>
            mControllerHelper;
    private NotificationAccessPreferenceController mController;

    private ServiceInfo mListenerServiceInfo;
    private ComponentName mListenerComponent;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                NotificationAccessPreferenceController.class, mPreferenceGroup);
        mController = mControllerHelper.getController();

        mListenerServiceInfo = new ServiceInfo();
        mListenerServiceInfo.permission = Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE;
        mListenerServiceInfo.packageName = "com.android.test.package";
        mListenerServiceInfo.name = "SomeListenerService";
        mListenerServiceInfo.nonLocalizedLabel = "label";
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.uid = 123;
        mListenerServiceInfo.applicationInfo = applicationInfo;

        mListenerComponent = new ComponentName(mListenerServiceInfo.packageName,
                mListenerServiceInfo.name);
    }

    @After
    public void tearDown() {
        ShadowApplicationPackageManager.reset();
    }

    @Test
    public void onStart_loadsListenerServices() {
        addNotificationListenerService(mListenerServiceInfo);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void onStart_serviceAccessGranted_setsPreferenceChecked() {
        addNotificationListenerService(mListenerServiceInfo);
        mContext.getSystemService(NotificationManager.class).setNotificationListenerAccessGranted(
                mListenerComponent, /* granted= */ true);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        TwoStatePreference preference = (TwoStatePreference) mPreferenceGroup.getPreference(0);
        assertThat(preference.isChecked()).isTrue();
    }

    @Test
    public void onStart_serviceAccessNotGranted_setsPreferenceUnchecked() {
        addNotificationListenerService(mListenerServiceInfo);
        mContext.getSystemService(NotificationManager.class).setNotificationListenerAccessGranted(
                mListenerComponent, /* granted= */ false);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        TwoStatePreference preference = (TwoStatePreference) mPreferenceGroup.getPreference(0);
        assertThat(preference.isChecked()).isFalse();
    }

    @Test
    public void preferenceClicked_serviceAccessGranted_showsRevokeConfirmDialog() {
        addNotificationListenerService(mListenerServiceInfo);
        mContext.getSystemService(NotificationManager.class).setNotificationListenerAccessGranted(
                mListenerComponent, /* granted= */ true);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        TwoStatePreference preference = (TwoStatePreference) mPreferenceGroup.getPreference(0);

        preference.performClick();

        verify(mControllerHelper.getMockFragmentController()).showDialog(any(
                ConfirmationDialogFragment.class),
                eq(NotificationAccessPreferenceController.REVOKE_CONFIRM_DIALOG_TAG));
    }

    @Test
    public void preferenceClicked_serviceAccessNotGranted_showsGrantConfirmDialog() {
        addNotificationListenerService(mListenerServiceInfo);
        mContext.getSystemService(NotificationManager.class).setNotificationListenerAccessGranted(
                mListenerComponent, /* granted= */ false);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        TwoStatePreference preference = (TwoStatePreference) mPreferenceGroup.getPreference(0);

        preference.performClick();

        verify(mControllerHelper.getMockFragmentController()).showDialog(any(
                ConfirmationDialogFragment.class),
                eq(NotificationAccessPreferenceController.GRANT_CONFIRM_DIALOG_TAG));
    }

    @Test
    public void revokeConfirmed_revokesNotificationAccess() {
        addNotificationListenerService(mListenerServiceInfo);
        NotificationManager notificationManager = mContext.getSystemService(
                NotificationManager.class);
        notificationManager.setNotificationListenerAccessGranted(
                mListenerComponent, /* granted= */ true);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        TwoStatePreference preference = (TwoStatePreference) mPreferenceGroup.getPreference(0);
        preference.performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(dialogCaptor.capture(),
                eq(NotificationAccessPreferenceController.REVOKE_CONFIRM_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(/* dialog= */ null, DialogInterface.BUTTON_POSITIVE);

        assertThat(notificationManager.isNotificationListenerAccessGranted(
                mListenerComponent)).isFalse();
    }

    @Test
    public void revokeConfirmed_notificationPolicyAccessNotGranted_removesAutomaticZenRules() {
        addNotificationListenerService(mListenerServiceInfo);
        NotificationManager notificationManager = mContext.getSystemService(
                NotificationManager.class);
        notificationManager.setNotificationListenerAccessGranted(
                mListenerComponent, /* granted= */ true);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        TwoStatePreference preference = (TwoStatePreference) mPreferenceGroup.getPreference(0);
        preference.performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(dialogCaptor.capture(),
                eq(NotificationAccessPreferenceController.REVOKE_CONFIRM_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        // Add a rule to be removed on access revoke.
        notificationManager.addAutomaticZenRule(mock(AutomaticZenRule.class));
        assertThat(notificationManager.getAutomaticZenRules()).isNotEmpty();

        notificationManager.setNotificationPolicyAccessGranted(
                mListenerServiceInfo.packageName, /* granted= */ false);
        dialogFragment.onClick(/* dialog= */ null, DialogInterface.BUTTON_POSITIVE);

        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();

        assertThat(notificationManager.getAutomaticZenRules()).isEmpty();
    }

    @Test
    public void grantConfirmed_grantsNotificationAccess() {
        addNotificationListenerService(mListenerServiceInfo);
        NotificationManager notificationManager = mContext.getSystemService(
                NotificationManager.class);
        notificationManager.setNotificationListenerAccessGranted(
                mListenerComponent, /* granted= */ false);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        TwoStatePreference preference = (TwoStatePreference) mPreferenceGroup.getPreference(0);
        preference.performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(dialogCaptor.capture(),
                eq(NotificationAccessPreferenceController.GRANT_CONFIRM_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(/* dialog= */ null, DialogInterface.BUTTON_POSITIVE);

        assertThat(notificationManager.isNotificationListenerAccessGranted(
                mListenerComponent)).isTrue();
    }

    private void addNotificationListenerService(ServiceInfo serviceInfo) {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = serviceInfo;
        getShadowPackageManager().addResolveInfoForIntent(
                new Intent(NotificationListenerService.SERVICE_INTERFACE), resolveInfo);
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = serviceInfo.packageName;
        packageInfo.applicationInfo = serviceInfo.applicationInfo;
        getShadowPackageManager().addPackage(packageInfo);
    }

    private ShadowApplicationPackageManager getShadowPackageManager() {
        return Shadow.extract(mContext.getPackageManager());
    }
}
