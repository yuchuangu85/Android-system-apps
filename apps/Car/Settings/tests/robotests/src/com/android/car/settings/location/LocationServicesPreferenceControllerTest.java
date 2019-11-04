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
package com.android.car.settings.location;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.location.SettingInjectorService;
import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.settingslib.location.SettingsInjector;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class LocationServicesPreferenceControllerTest {

    private static final int PROFILE_ID = UserHandle.USER_CURRENT;

    @Mock
    private SettingsInjector mSettingsInjector;
    private Context mContext;
    private PreferenceControllerTestHelper<LocationServicesPreferenceController> mControllerHelper;
    private LocationServicesPreferenceController mController;
    private PreferenceGroup mCategory;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mCategory = new PreferenceCategory(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                LocationServicesPreferenceController.class, mCategory);
        mController = mControllerHelper.getController();
        mController.setSettingsInjector(mSettingsInjector);
    }

    @Test
    public void onCreate_addsInjectedSettingsToPreferenceCategory() {
        Map<Integer, List<Preference>> samplePrefs = getSamplePreferences();
        doReturn(samplePrefs).when(mSettingsInjector)
                .getInjectedSettings(any(Context.class), anyInt());
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        assertThat(mCategory.getPreferenceCount()).isEqualTo(samplePrefs.get(PROFILE_ID).size());
    }

    @Test
    public void onStart_registersBroadcastReceiver() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mContext.sendBroadcast(new Intent(SettingInjectorService.ACTION_INJECTED_SETTING_CHANGED));
        verify(mSettingsInjector).reloadStatusMessages();
    }

    @Test
    public void onStop_unregistersBroadcastReceiver() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mContext.sendBroadcast(new Intent(SettingInjectorService.ACTION_INJECTED_SETTING_CHANGED));
        verify(mSettingsInjector).reloadStatusMessages();

        clearInvocations(mSettingsInjector);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);
        mContext.sendBroadcast(new Intent(SettingInjectorService.ACTION_INJECTED_SETTING_CHANGED));
        verify(mSettingsInjector, never()).reloadStatusMessages();
    }

    @Test
    public void preferenceCategory_isVisibleIfThereAreInjectedSettings() {
        doReturn(getSamplePreferences()).when(mSettingsInjector)
                .getInjectedSettings(any(Context.class), anyInt());
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mCategory.isVisible()).isTrue();
    }

    @Test
    public void preferenceCategory_isHiddenIfThereAreNoInjectedSettings() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mCategory.isVisible()).isFalse();
    }

    private Map<Integer, List<Preference>> getSamplePreferences() {
        Map<Integer, List<Preference>> preferences = new ArrayMap<>();
        preferences.put(PROFILE_ID,
                Arrays.asList(new Preference(mContext), new Preference(mContext),
                        new Preference(mContext)));
        return preferences;
    }
}
