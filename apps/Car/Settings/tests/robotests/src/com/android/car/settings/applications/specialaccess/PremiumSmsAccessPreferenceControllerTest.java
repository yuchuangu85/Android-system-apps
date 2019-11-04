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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Looper;
import android.os.RemoteException;

import androidx.lifecycle.Lifecycle;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowApplicationsState;
import com.android.car.settings.testutils.ShadowISms;
import com.android.internal.telephony.ISms;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.ApplicationsState.AppEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Unit test for {@link PremiumSmsAccessPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowApplicationsState.class, ShadowISms.class})
public class PremiumSmsAccessPreferenceControllerTest {

    @Mock
    private AppEntryListManager mAppEntryListManager;
    @Mock
    private ApplicationsState mApplicationsState;
    @Mock
    private ISms mISms;
    @Captor
    private ArgumentCaptor<AppEntryListManager.Callback> mCallbackCaptor;

    private PreferenceGroup mPreferenceGroup;
    private PreferenceControllerTestHelper<PremiumSmsAccessPreferenceController> mControllerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowApplicationsState.setInstance(mApplicationsState);
        when(mApplicationsState.getBackgroundLooper()).thenReturn(Looper.getMainLooper());
        ShadowISms.setISms(mISms);

        Context context = RuntimeEnvironment.application;
        mPreferenceGroup = new LogicalPreferenceGroup(context);
        mControllerHelper = new PreferenceControllerTestHelper<>(context,
                PremiumSmsAccessPreferenceController.class, mPreferenceGroup);
        mControllerHelper.getController().mAppEntryListManager = mAppEntryListManager;
        mControllerHelper.markState(Lifecycle.State.CREATED);
        verify(mAppEntryListManager).init(any(AppStatePremiumSmsBridge.class), any(),
                mCallbackCaptor.capture());
    }

    @After
    public void tearDown() {
        ShadowApplicationsState.reset();
        ShadowISms.reset();
    }

    @Test
    public void onStart_startsListManager() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        verify(mAppEntryListManager).start();
    }

    @Test
    public void onStop_stopsListManager() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        verify(mAppEntryListManager).stop();
    }

    @Test
    public void onDestroy_destroysListManager() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_DESTROY);

        verify(mAppEntryListManager).destroy();
    }

    @Test
    public void onAppEntryListChanged_addsPreferencesForEntries() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        List<AppEntry> entries = Arrays.asList(
                createAppEntry("test.package", /* uid= */ 1,
                        SmsUsageMonitor.PREMIUM_SMS_PERMISSION_ALWAYS_ALLOW),
                createAppEntry("another.test.package", /* uid= */ 2,
                        SmsUsageMonitor.PREMIUM_SMS_PERMISSION_NEVER_ALLOW));

        mCallbackCaptor.getValue().onAppEntryListChanged(entries);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);
        assertThat(((ListPreference) mPreferenceGroup.getPreference(0)).getValue()).isEqualTo(
                String.valueOf(SmsUsageMonitor.PREMIUM_SMS_PERMISSION_ALWAYS_ALLOW));
        assertThat(((ListPreference) mPreferenceGroup.getPreference(1)).getValue()).isEqualTo(
                String.valueOf(SmsUsageMonitor.PREMIUM_SMS_PERMISSION_NEVER_ALLOW));
    }

    @Test
    public void onPreferenceChange_setsPremiumSmsPermission() throws RemoteException {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        String packageName = "test.package";
        List<AppEntry> entries = Collections.singletonList(
                createAppEntry(packageName, /* uid= */ 1,
                        SmsUsageMonitor.PREMIUM_SMS_PERMISSION_NEVER_ALLOW));
        mCallbackCaptor.getValue().onAppEntryListChanged(entries);
        Preference appPref = mPreferenceGroup.getPreference(0);
        int updatedValue = SmsUsageMonitor.PREMIUM_SMS_PERMISSION_ASK_USER;

        appPref.getOnPreferenceChangeListener().onPreferenceChange(appPref,
                String.valueOf(updatedValue));

        verify(mISms).setPremiumSmsPermission(packageName, updatedValue);
    }

    @Test
    public void onPreferenceChange_updatesEntry() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        List<AppEntry> entries = Collections.singletonList(
                createAppEntry("test.package", /* uid= */ 1,
                        SmsUsageMonitor.PREMIUM_SMS_PERMISSION_NEVER_ALLOW));
        mCallbackCaptor.getValue().onAppEntryListChanged(entries);
        Preference appPref = mPreferenceGroup.getPreference(0);

        appPref.getOnPreferenceChangeListener().onPreferenceChange(appPref,
                String.valueOf(SmsUsageMonitor.PREMIUM_SMS_PERMISSION_ASK_USER));

        verify(mAppEntryListManager).forceUpdate(entries.get(0));
    }

    @Test
    public void appFilter_removesUnknownStates() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        ArgumentCaptor<AppEntryListManager.AppFilterProvider> filterCaptor =
                ArgumentCaptor.forClass(AppEntryListManager.AppFilterProvider.class);
        verify(mAppEntryListManager).init(any(), filterCaptor.capture(), any());
        ApplicationsState.AppFilter filter = filterCaptor.getValue().getAppFilter();
        AppEntry unknownStateApp = createAppEntry("test.package", /* uid= */ 1,
                SmsUsageMonitor.PREMIUM_SMS_PERMISSION_UNKNOWN);

        assertThat(filter.filterApp(unknownStateApp)).isFalse();
    }

    private AppEntry createAppEntry(String packageName, int uid, int smsState) {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = packageName;
        info.uid = uid;

        AppEntry appEntry = mock(AppEntry.class);
        appEntry.info = info;
        appEntry.label = packageName;
        appEntry.extraInfo = smsState;

        return appEntry;
    }
}
