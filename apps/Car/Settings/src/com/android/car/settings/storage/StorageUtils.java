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

package com.android.car.settings.storage;

import android.annotation.Nullable;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;

/** Utility functions for use in storage settings. */
public class StorageUtils {

    private StorageUtils() { }

    /**
     * Tries to initialize a volume with the given bundle. If it is a valid, private, and readable
     * {@link VolumeInfo}, it is returned. If it is not valid, {@code null} is returned.
     */
    @Nullable
    public static VolumeInfo maybeInitializeVolume(StorageManager sm, @Nullable Bundle bundle) {
        String volumeId = VolumeInfo.ID_PRIVATE_INTERNAL;
        if (bundle != null) {
            volumeId = bundle.getString(VolumeInfo.EXTRA_VOLUME_ID,
                    VolumeInfo.ID_PRIVATE_INTERNAL);
        }

        VolumeInfo volume = sm.findVolumeById(volumeId);
        return isVolumeValid(volume) ? volume : null;
    }

    private static boolean isVolumeValid(VolumeInfo volume) {
        return (volume != null) && (volume.getType() == VolumeInfo.TYPE_PRIVATE)
                && volume.isMountedReadable();
    }
}
