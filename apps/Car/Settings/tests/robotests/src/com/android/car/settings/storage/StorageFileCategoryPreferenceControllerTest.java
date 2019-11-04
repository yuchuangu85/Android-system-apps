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

import static android.os.storage.VolumeInfo.MOUNT_FLAG_PRIMARY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.os.storage.VolumeInfo;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.common.ProgressBarPreference;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowStorageManagerVolumeProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Unit test for {@link StorageFileCategoryPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowStorageManagerVolumeProvider.class, ShadowCarUserManagerHelper.class})
public class StorageFileCategoryPreferenceControllerTest {

    private static final int TEST_USER = 10;

    private Context mContext;
    private ProgressBarPreference mProgressBarPreference;
    private StorageFileCategoryPreferenceController mController;
    private PreferenceControllerTestHelper<StorageFileCategoryPreferenceController>
            mPreferenceControllerHelper;

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(RuntimeEnvironment.application);
        mProgressBarPreference = new ProgressBarPreference(mContext);
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                StorageFileCategoryPreferenceController.class, mProgressBarPreference);
        mController = mPreferenceControllerHelper.getController();
        when(mCarUserManagerHelper.getCurrentProcessUserId()).thenReturn(TEST_USER);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @After
    public void tearDown() {
        ShadowStorageManagerVolumeProvider.reset();
    }

    @Test
    public void handlePreferenceClicked_currentUser_startNewActivity() {
        VolumeInfo volumeInfo = new VolumeInfo("id", VolumeInfo.TYPE_EMULATED, null, "id");
        volumeInfo.mountFlags = MOUNT_FLAG_PRIMARY;
        ShadowStorageManagerVolumeProvider.setVolumeInfo(volumeInfo);

        mProgressBarPreference.performClick();
        ArgumentCaptor<Intent> argumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivityAsUser(argumentCaptor.capture(), nullable(UserHandle.class));

        Intent intent = argumentCaptor.getValue();
        Intent browseIntent = volumeInfo.buildBrowseIntent();
        assertThat(intent.getAction()).isEqualTo(browseIntent.getAction());
        assertThat(intent.getData()).isEqualTo(browseIntent.getData());
    }
}
