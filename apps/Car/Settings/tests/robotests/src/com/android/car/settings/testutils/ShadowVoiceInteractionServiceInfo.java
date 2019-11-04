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

package com.android.car.settings.testutils;

import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.service.voice.VoiceInteractionServiceInfo;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.HashMap;
import java.util.Map;

@Implements(VoiceInteractionServiceInfo.class)
public class ShadowVoiceInteractionServiceInfo {
    private static Map<ServiceInfo, Boolean> sSupportsAssistMap = new HashMap<>();
    private static Map<ServiceInfo, String> sRecognitionServiceMap = new HashMap<>();
    private static Map<ServiceInfo, String> sSettingsActivityMap = new HashMap<>();

    private ServiceInfo mServiceInfo;

    public void __constructor__(PackageManager pm, ServiceInfo si) {
        mServiceInfo = si;
    }

    public static void setSupportsAssist(ServiceInfo si, boolean supports) {
        sSupportsAssistMap.put(si, supports);
    }

    public static void setRecognitionService(ServiceInfo si, String recognitionService) {
        sRecognitionServiceMap.put(si, recognitionService);
    }

    public static void setSettingsActivity(ServiceInfo si, String settingsActivity) {
        sSettingsActivityMap.put(si, settingsActivity);
    }

    @Implementation
    protected boolean getSupportsAssist() {
        return sSupportsAssistMap.get(mServiceInfo);
    }

    @Implementation
    protected String getRecognitionService() {
        return sRecognitionServiceMap.get(mServiceInfo);
    }

    @Implementation
    protected String getSettingsActivity() {
        return sSettingsActivityMap.get(mServiceInfo);
    }

    @Implementation
    protected ServiceInfo getServiceInfo() {
        return mServiceInfo;
    }

    @Resetter
    public static void reset() {
        sSupportsAssistMap.clear();
        sRecognitionServiceMap.clear();
        sSettingsActivityMap.clear();
    }
}
