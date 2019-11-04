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

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.testutils.ShadowVoiceInteractionServiceInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowApplicationPackageManager;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowApplicationPackageManager.class, ShadowVoiceInteractionServiceInfo.class})
public class VoiceInputInfoProviderTest {

    private static final String TEST_PACKAGE = "test.package";
    private static final String TEST_CLASS = "Class1";
    private static final String TEST_RECOGNITION_SERVICE = "Recognition1";

    private Context mContext;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
    }

    @After
    public void tearDown() {
        ShadowVoiceInteractionServiceInfo.reset();
    }

    @Test
    public void getInteractionInfoList_hasElement() {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.packageName = TEST_PACKAGE;
        resolveInfo.serviceInfo.name = TEST_CLASS;
        ShadowVoiceInteractionServiceInfo.setRecognitionService(resolveInfo.serviceInfo,
                TEST_RECOGNITION_SERVICE);

        getShadowPackageManager().addResolveInfoForIntent(
                VoiceInputInfoProvider.VOICE_INTERACTION_SERVICE_TAG, resolveInfo);

        VoiceInputInfoProvider provider = new VoiceInputInfoProvider(mContext);
        assertThat(provider.getVoiceInteractionInfoList()).hasSize(1);
    }

    @Test
    public void getRecognitionInfoList_hasElement() {
        ResolveInfo otherInfo = new ResolveInfo();
        otherInfo.serviceInfo = new ServiceInfo();
        otherInfo.serviceInfo.packageName = TEST_PACKAGE;
        otherInfo.serviceInfo.name = TEST_RECOGNITION_SERVICE;

        getShadowPackageManager().addResolveInfoForIntent(
                VoiceInputInfoProvider.VOICE_RECOGNITION_SERVICE_TAG, otherInfo);

        VoiceInputInfoProvider provider = new VoiceInputInfoProvider(mContext);
        assertThat(provider.getVoiceRecognitionInfoList()).hasSize(1);
    }

    private ShadowApplicationPackageManager getShadowPackageManager() {
        return Shadow.extract(mContext.getPackageManager());
    }
}
