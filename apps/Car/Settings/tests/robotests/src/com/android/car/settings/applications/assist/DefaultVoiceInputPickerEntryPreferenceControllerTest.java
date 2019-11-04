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

package com.android.car.settings.applications.assist;

import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.provider.Settings;
import android.service.voice.VoiceInteractionServiceInfo;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.ButtonPreference;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowSecureSettings;
import com.android.car.settings.testutils.ShadowVoiceInteractionServiceInfo;
import com.android.settingslib.applications.DefaultAppInfo;

import com.google.common.collect.Iterables;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowApplicationPackageManager;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowSecureSettings.class, ShadowCarUserManagerHelper.class,
        ShadowVoiceInteractionServiceInfo.class})
public class DefaultVoiceInputPickerEntryPreferenceControllerTest {

    private static final String TEST_PACKAGE = "com.android.car.settings.testutils";
    private static final String TEST_ASSIST = "TestAssistService";
    private static final String TEST_VOICE = "TestVoiceService";
    private static final String TEST_SETTINGS_CLASS = "TestSettingsActivity";
    private static final int TEST_USER_ID = 10;

    private Context mContext;
    private DefaultVoiceInputPickerEntryPreferenceController mController;
    private PreferenceControllerTestHelper<DefaultVoiceInputPickerEntryPreferenceController>
            mControllerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        CarUserManagerHelper carUserManagerHelper = mock(CarUserManagerHelper.class);
        ShadowCarUserManagerHelper.setMockInstance(carUserManagerHelper);

        mContext = RuntimeEnvironment.application;
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                DefaultVoiceInputPickerEntryPreferenceController.class,
                new ButtonPreference(mContext));
        mController = mControllerHelper.getController();

        // Set user.
        when(carUserManagerHelper.getCurrentProcessUserId()).thenReturn(TEST_USER_ID);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowSecureSettings.reset();
        ShadowVoiceInteractionServiceInfo.reset();
    }

    @Test
    public void getAvailabilityStatus_sameComponents_returnsConditionallyUnavailable() {
        setCurrentAssistant(new ComponentName(TEST_PACKAGE, TEST_VOICE));
        setCurrentVoiceService(new ComponentName(TEST_PACKAGE, TEST_VOICE));

        assertThat(mController.getAvailabilityStatus()).isEqualTo(
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_bothNull_returnsConditionallyUnavailable() {
        assertThat(mController.getAvailabilityStatus()).isEqualTo(
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_differentComponents_returnsAvailable() {
        setCurrentAssistant(new ComponentName(TEST_PACKAGE, TEST_ASSIST));
        setCurrentVoiceService(new ComponentName(TEST_PACKAGE, TEST_VOICE));

        assertThat(mController.getAvailabilityStatus()).isEqualTo(
                PreferenceController.AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_assistNull_returnsAvailable() {
        setCurrentVoiceService(new ComponentName(TEST_PACKAGE, TEST_VOICE));
        assertThat(mController.getAvailabilityStatus()).isEqualTo(
                PreferenceController.AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_voiceInputNull_returnsAvailable() {
        setCurrentAssistant(new ComponentName(TEST_PACKAGE, TEST_ASSIST));
        assertThat(mController.getAvailabilityStatus()).isEqualTo(
                PreferenceController.AVAILABLE);
    }

    @Test
    public void onStart_registersObserver() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(getShadowContentResolver().getContentObservers(
                Settings.Secure.getUriFor(Settings.Secure.ASSISTANT))).isNotEmpty();
    }

    @Test
    public void onStop_unregistersObserver() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        assertThat(getShadowContentResolver().getContentObservers(
                Settings.Secure.getUriFor(Settings.Secure.ASSISTANT))).isEmpty();
    }

    @Test
    public void onChange_changeRegisteredSetting_callsRefreshUi() {
        setCurrentVoiceService(new ComponentName(TEST_PACKAGE, TEST_VOICE));
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(PreferenceController.AVAILABLE);

        setCurrentAssistant(new ComponentName(TEST_PACKAGE, TEST_VOICE));
        ContentObserver observer = Iterables.get(getShadowContentResolver().getContentObservers(
                Settings.Secure.getUriFor(Settings.Secure.ASSISTANT)), 0);
        observer.onChange(/* selfChange= */ false,
                Settings.Secure.getUriFor(Settings.Secure.ASSISTANT));

        assertThat(mController.getAvailabilityStatus()).isEqualTo(
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getCurrentDefaultAppInfo_providerHasCurrentService_returnsValidDefaultAppInfo() {
        // This is used so tht the VoiceInputInfoProvider returns a valid service.
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.packageName = TEST_PACKAGE;
        resolveInfo.serviceInfo.name = TEST_VOICE;
        getShadowPackageManager().addResolveInfoForIntent(
                VoiceInputInfoProvider.VOICE_INTERACTION_SERVICE_TAG, resolveInfo);

        // Create new controller to rerun the constructor with the new shadow package manager.
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                DefaultVoiceInputPickerEntryPreferenceController.class,
                new ButtonPreference(mContext));
        mController = mControllerHelper.getController();

        ComponentName voiceService = new ComponentName(TEST_PACKAGE, TEST_VOICE);
        setCurrentVoiceService(voiceService);

        assertThat(mController.getCurrentDefaultAppInfo()).isNotNull();
    }

    @Test
    public void getCurrentDefaultAppInfo_providerHasNoService_returnsNull() {
        assertThat(mController.getCurrentDefaultAppInfo()).isNull();
    }

    @Test
    public void getSettingIntent_nullInput_returnsNull() {
        assertThat(mController.getSettingIntent(null)).isEqualTo(null);
    }

    @Test
    public void getSettingIntent_inputIsWrongType_returnsNull() {
        DefaultAppInfo info = mock(DefaultAppInfo.class);
        assertThat(mController.getSettingIntent(info)).isEqualTo(null);
    }

    @Test
    public void getSettingIntent_validInput_returnsIntent() {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.packageName = TEST_PACKAGE;
        resolveInfo.serviceInfo.name = TEST_VOICE;

        ShadowVoiceInteractionServiceInfo.setSettingsActivity(resolveInfo.serviceInfo,
                TEST_SETTINGS_CLASS);
        VoiceInteractionServiceInfo interactionServiceInfo = new VoiceInteractionServiceInfo(
                mContext.getPackageManager(), resolveInfo.serviceInfo);
        VoiceInputInfoProvider.VoiceInputInfo info =
                new VoiceInputInfoProvider.VoiceInteractionInfo(mContext, interactionServiceInfo);

        DefaultVoiceInputServiceInfo serviceInfo = new DefaultVoiceInputServiceInfo(mContext,
                mContext.getPackageManager(), TEST_USER_ID, info, true);
        Intent settingIntent = mController.getSettingIntent(serviceInfo);

        assertThat(settingIntent.getAction()).isEqualTo(Intent.ACTION_MAIN);
        assertThat(settingIntent.getComponent()).isEqualTo(
                new ComponentName(TEST_PACKAGE, TEST_SETTINGS_CLASS));
    }

    private void setCurrentVoiceService(ComponentName service) {
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.VOICE_INTERACTION_SERVICE, service.flattenToString());
    }

    private void setCurrentAssistant(ComponentName assist) {
        Settings.Secure.putStringForUser(mContext.getContentResolver(), Settings.Secure.ASSISTANT,
                assist.flattenToString(), TEST_USER_ID);
    }

    private ShadowApplicationPackageManager getShadowPackageManager() {
        return Shadow.extract(mContext.getPackageManager());
    }

    private ShadowContentResolver getShadowContentResolver() {
        return (ShadowContentResolver) Shadows.shadowOf(mContext.getContentResolver());
    }
}
