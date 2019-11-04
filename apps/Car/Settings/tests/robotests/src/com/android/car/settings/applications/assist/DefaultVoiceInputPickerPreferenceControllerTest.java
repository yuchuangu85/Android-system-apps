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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.provider.Settings;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowSecureSettings;
import com.android.car.settings.testutils.ShadowVoiceInteractionServiceInfo;
import com.android.settingslib.applications.DefaultAppInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowApplicationPackageManager;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowSecureSettings.class, ShadowVoiceInteractionServiceInfo.class,
        ShadowCarUserManagerHelper.class})
public class DefaultVoiceInputPickerPreferenceControllerTest {

    private static final String TEST_PACKAGE_NAME = "com.test.package";
    private static final String TEST_SERVICE = "TestService";
    private static final String TEST_OTHER_SERVICE = "TestOtherService";
    private static final String TEST_RECOGNIZER = "TestRecognizer";
    private static final int TEST_USER_ID = 10;

    private Context mContext;
    private PreferenceControllerTestHelper<DefaultVoiceInputPickerPreferenceController>
            mControllerHelper;
    private DefaultVoiceInputPickerPreferenceController mController;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);

        mContext = RuntimeEnvironment.application;

        // Set user.
        when(mCarUserManagerHelper.getCurrentProcessUserId()).thenReturn(TEST_USER_ID);
    }

    @After
    public void tearDown() {
        ShadowSecureSettings.reset();
        ShadowCarUserManagerHelper.reset();
        ShadowVoiceInteractionServiceInfo.reset();
    }

    @Test
    public void getCandidates_voiceInteractionService_hasOneElement() {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.packageName = TEST_PACKAGE_NAME;
        resolveInfo.serviceInfo.name = TEST_SERVICE;
        getShadowPackageManager().addResolveInfoForIntent(
                VoiceInputInfoProvider.VOICE_INTERACTION_SERVICE_TAG, resolveInfo);
        setupController();

        assertThat(mController.getCandidates()).hasSize(1);
    }

    @Test
    public void getCandidates_voiceRecognitionService_hasOneElement() {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.packageName = TEST_PACKAGE_NAME;
        resolveInfo.serviceInfo.name = TEST_RECOGNIZER;
        getShadowPackageManager().addResolveInfoForIntent(
                VoiceInputInfoProvider.VOICE_RECOGNITION_SERVICE_TAG, resolveInfo);
        setupController();

        assertThat(mController.getCandidates()).hasSize(1);
    }

    @Test
    public void getCandidates_oneIsSameAsAssistant_hasTwoElements() {
        ResolveInfo interactionInfo = new ResolveInfo();
        interactionInfo.serviceInfo = new ServiceInfo();
        interactionInfo.serviceInfo.packageName = TEST_PACKAGE_NAME;
        interactionInfo.serviceInfo.name = TEST_SERVICE;
        interactionInfo.serviceInfo.applicationInfo = new ApplicationInfo();
        interactionInfo.serviceInfo.applicationInfo.nonLocalizedLabel = "1";
        getShadowPackageManager().addResolveInfoForIntent(
                VoiceInputInfoProvider.VOICE_INTERACTION_SERVICE_TAG, interactionInfo);

        ResolveInfo interactionInfo2 = new ResolveInfo();
        interactionInfo2.serviceInfo = new ServiceInfo();
        interactionInfo2.serviceInfo.packageName = TEST_PACKAGE_NAME;
        interactionInfo2.serviceInfo.name = TEST_OTHER_SERVICE;
        interactionInfo2.serviceInfo.applicationInfo = new ApplicationInfo();
        interactionInfo2.serviceInfo.applicationInfo.nonLocalizedLabel = "2";
        getShadowPackageManager().addResolveInfoForIntent(
                VoiceInputInfoProvider.VOICE_INTERACTION_SERVICE_TAG, interactionInfo2);

        ComponentName voiceInteraction = new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE);
        setCurrentAssistant(voiceInteraction);
        setCurrentVoiceInteractionService(voiceInteraction);

        setupController();

        assertThat(mController.getCandidates()).hasSize(2);
    }

    @Test
    public void getCandidates_oneIsSameAsAssistant_sameOneIsEnabled() {
        ResolveInfo interactionInfo = new ResolveInfo();
        interactionInfo.serviceInfo = new ServiceInfo();
        interactionInfo.serviceInfo.packageName = TEST_PACKAGE_NAME;
        interactionInfo.serviceInfo.name = TEST_SERVICE;
        interactionInfo.serviceInfo.applicationInfo = new ApplicationInfo();
        interactionInfo.serviceInfo.applicationInfo.nonLocalizedLabel = "1";
        getShadowPackageManager().addResolveInfoForIntent(
                VoiceInputInfoProvider.VOICE_INTERACTION_SERVICE_TAG, interactionInfo);

        ResolveInfo interactionInfo2 = new ResolveInfo();
        interactionInfo2.serviceInfo = new ServiceInfo();
        interactionInfo2.serviceInfo.packageName = TEST_PACKAGE_NAME;
        interactionInfo2.serviceInfo.name = TEST_OTHER_SERVICE;
        interactionInfo2.serviceInfo.applicationInfo = new ApplicationInfo();
        interactionInfo2.serviceInfo.applicationInfo.nonLocalizedLabel = "2";
        getShadowPackageManager().addResolveInfoForIntent(
                VoiceInputInfoProvider.VOICE_INTERACTION_SERVICE_TAG, interactionInfo2);

        ComponentName voiceInteraction = new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE);
        setCurrentAssistant(voiceInteraction);
        setCurrentVoiceInteractionService(voiceInteraction);

        setupController();

        DefaultAppInfo defaultAppInfo = null;
        for (DefaultAppInfo info : mController.getCandidates()) {
            if (info.componentName.equals(new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE))) {
                defaultAppInfo = info;
            }
        }
        assertThat(defaultAppInfo).isNotNull();
        assertThat(defaultAppInfo.enabled).isTrue();
    }

    @Test
    public void getCandidates_oneIsSameAsAssistant_differentOneIsDisabled() {
        ResolveInfo interactionInfo = new ResolveInfo();
        interactionInfo.serviceInfo = new ServiceInfo();
        interactionInfo.serviceInfo.packageName = TEST_PACKAGE_NAME;
        interactionInfo.serviceInfo.name = TEST_SERVICE;
        interactionInfo.serviceInfo.applicationInfo = new ApplicationInfo();
        interactionInfo.serviceInfo.applicationInfo.nonLocalizedLabel = "1";
        getShadowPackageManager().addResolveInfoForIntent(
                VoiceInputInfoProvider.VOICE_INTERACTION_SERVICE_TAG, interactionInfo);

        ResolveInfo interactionInfo2 = new ResolveInfo();
        interactionInfo2.serviceInfo = new ServiceInfo();
        interactionInfo2.serviceInfo.packageName = TEST_PACKAGE_NAME;
        interactionInfo2.serviceInfo.name = TEST_OTHER_SERVICE;
        interactionInfo2.serviceInfo.applicationInfo = new ApplicationInfo();
        interactionInfo2.serviceInfo.applicationInfo.nonLocalizedLabel = "2";
        getShadowPackageManager().addResolveInfoForIntent(
                VoiceInputInfoProvider.VOICE_INTERACTION_SERVICE_TAG, interactionInfo2);

        ComponentName voiceInteraction = new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE);
        setCurrentAssistant(voiceInteraction);
        setCurrentVoiceInteractionService(voiceInteraction);

        setupController();

        DefaultAppInfo defaultAppInfo = null;
        for (DefaultAppInfo info : mController.getCandidates()) {
            if (info.componentName.equals(
                    new ComponentName(TEST_PACKAGE_NAME, TEST_OTHER_SERVICE))) {
                defaultAppInfo = info;
            }
        }
        assertThat(defaultAppInfo).isNotNull();
        assertThat(defaultAppInfo.enabled).isFalse();
    }

    @Test
    public void getCurrentDefaultKey_defaultIsNull_returnsNull() {
        setupController();

        assertThat(mController.getCurrentDefaultKey()).isNull();
    }

    @Test
    public void getCurrentDefaultKey_defaultExists_returnsComponentName() {
        setupController();

        ComponentName cn = new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE);
        setCurrentVoiceInteractionService(cn);

        assertThat(mController.getCurrentDefaultKey()).isEqualTo(cn.flattenToString());
    }

    @Test
    public void setCurrentDefault_typeVoiceInteractionInfo_setsServices() {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.packageName = TEST_PACKAGE_NAME;
        resolveInfo.serviceInfo.name = TEST_SERVICE;
        getShadowPackageManager().addResolveInfoForIntent(
                VoiceInputInfoProvider.VOICE_INTERACTION_SERVICE_TAG, resolveInfo);
        ShadowVoiceInteractionServiceInfo.setRecognitionService(resolveInfo.serviceInfo,
                TEST_RECOGNIZER);
        setupController();

        String key = new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE).flattenToString();
        String recognizer = new ComponentName(TEST_PACKAGE_NAME, TEST_RECOGNIZER).flattenToString();
        mController.setCurrentDefault(key);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.VOICE_INTERACTION_SERVICE)).isEqualTo(key);
        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.VOICE_RECOGNITION_SERVICE)).isEqualTo(recognizer);
    }

    @Test
    public void setCurrentDefault_typeVoiceRecognitionInfo_setsRecognitionService() {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.packageName = TEST_PACKAGE_NAME;
        resolveInfo.serviceInfo.name = TEST_RECOGNIZER;
        getShadowPackageManager().addResolveInfoForIntent(
                VoiceInputInfoProvider.VOICE_RECOGNITION_SERVICE_TAG, resolveInfo);
        setupController();

        String key = new ComponentName(TEST_PACKAGE_NAME, TEST_RECOGNIZER).flattenToString();
        mController.setCurrentDefault(key);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.VOICE_INTERACTION_SERVICE)).isEmpty();
        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.VOICE_RECOGNITION_SERVICE)).isEqualTo(key);
    }

    private void setupController() {
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                DefaultVoiceInputPickerPreferenceController.class,
                new LogicalPreferenceGroup(mContext));
        mController = mControllerHelper.getController();
    }

    private void setCurrentVoiceInteractionService(ComponentName service) {
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
}
