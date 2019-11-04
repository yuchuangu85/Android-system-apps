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

package com.android.car.settings.testutils;

import android.os.storage.VolumeInfo;

import com.android.settingslib.deviceinfo.StorageManagerVolumeProvider;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

@Implements(StorageManagerVolumeProvider.class)
public class ShadowStorageManagerVolumeProvider {

    private static VolumeInfo sVolumeInfo;

    @Resetter
    public static void reset() {
        sVolumeInfo = null;
    }

    @Implementation
    protected VolumeInfo findEmulatedForPrivate(VolumeInfo privateVolume) {
        return sVolumeInfo;
    }

    public static void setVolumeInfo(VolumeInfo volumeInfo) {
        sVolumeInfo = volumeInfo;
    }
}
