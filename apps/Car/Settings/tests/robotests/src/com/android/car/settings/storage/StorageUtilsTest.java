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

package com.android.car.settings.storage;

import static android.os.storage.VolumeInfo.STATE_MOUNTED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;

import com.android.car.settings.CarSettingsRobolectricTestRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit test for {@link StorageUtils}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class StorageUtilsTest {

    @Mock
    private StorageManager mStorageManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void maybeInitializeVolume_mounted_shouldReturnVolumeInfo() {
        VolumeInfo volumeInfo = new VolumeInfo("id", VolumeInfo.TYPE_PRIVATE, null, "id");
        volumeInfo.state = STATE_MOUNTED;

        when(mStorageManager.findVolumeById(VolumeInfo.ID_PRIVATE_INTERNAL)).thenReturn(volumeInfo);

        VolumeInfo expected = StorageUtils.maybeInitializeVolume(mStorageManager, null);

        assertThat(expected).isEqualTo(volumeInfo);
    }

    @Test
    public void maybeInitializeVolume_notMounted_shouldNotReturnVolumeInfo() {
        VolumeInfo volumeInfo = new VolumeInfo("id", VolumeInfo.TYPE_PRIVATE, null, "id");

        when(mStorageManager.findVolumeById(VolumeInfo.ID_PRIVATE_INTERNAL)).thenReturn(volumeInfo);

        VolumeInfo expected = StorageUtils.maybeInitializeVolume(mStorageManager, null);

        assertThat(expected).isNull();
    }

    @Test
    public void maybeInitializeVolume_differentType_shouldNotReturnVolumeInfo() {
        VolumeInfo volumeInfo = new VolumeInfo("id", VolumeInfo.TYPE_EMULATED, null, "id");

        when(mStorageManager.findVolumeById(VolumeInfo.ID_PRIVATE_INTERNAL)).thenReturn(volumeInfo);

        VolumeInfo expected = StorageUtils.maybeInitializeVolume(mStorageManager, null);

        assertThat(expected).isNull();
    }

    @Test
    public void maybeInitializeVolume_defaultId_notMounted_returnsNull() {
        VolumeInfo volumeInfo = new VolumeInfo("id", VolumeInfo.TYPE_EMULATED, null, "id");
        Bundle bundle = new Bundle();
        when(mStorageManager.findVolumeById(VolumeInfo.ID_PRIVATE_INTERNAL)).thenReturn(volumeInfo);

        VolumeInfo expected = StorageUtils.maybeInitializeVolume(mStorageManager, bundle);

        assertThat(expected).isNull();
    }

    @Test
    public void maybeInitializeVolume_getDefaultIdFromBundle_mounted_shouldReturnVolumeInfo() {
        VolumeInfo volumeInfo = new VolumeInfo("id", VolumeInfo.TYPE_PRIVATE, null, "id");
        volumeInfo.state = STATE_MOUNTED;
        Bundle bundle = new Bundle();
        when(mStorageManager.findVolumeById(VolumeInfo.ID_PRIVATE_INTERNAL)).thenReturn(volumeInfo);

        VolumeInfo expected = StorageUtils.maybeInitializeVolume(mStorageManager, bundle);

        assertThat(expected).isEqualTo(volumeInfo);
    }

    @Test
    public void maybeInitializeVolume_getIdFromBundle_shouldReturnVolumeInfo() {
        VolumeInfo volumeInfo = new VolumeInfo("id", VolumeInfo.TYPE_PRIVATE, null, "id");
        volumeInfo.state = STATE_MOUNTED;
        Bundle bundle = new Bundle();
        bundle.putString(VolumeInfo.EXTRA_VOLUME_ID, VolumeInfo.ID_EMULATED_INTERNAL);
        when(mStorageManager.findVolumeById(VolumeInfo.ID_EMULATED_INTERNAL)).thenReturn(
                volumeInfo);

        VolumeInfo expected = StorageUtils.maybeInitializeVolume(mStorageManager, bundle);

        assertThat(expected).isEqualTo(volumeInfo);
    }
}
