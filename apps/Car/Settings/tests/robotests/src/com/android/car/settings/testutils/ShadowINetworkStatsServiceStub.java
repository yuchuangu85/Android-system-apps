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

import android.net.INetworkStatsService;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

@Implements(value = INetworkStatsService.Stub.class)
public class ShadowINetworkStatsServiceStub {

    private static INetworkStatsService sINetworkStatsService;

    @Resetter
    public static void reset() {
        sINetworkStatsService = null;
    }

    @Implementation
    public static android.net.INetworkStatsService asInterface(android.os.IBinder obj) {
        return sINetworkStatsService;
    }

    public static void setINetworkStatsSession(INetworkStatsService iNetworkStatsService) {
        sINetworkStatsService = iNetworkStatsService;
    }
}
