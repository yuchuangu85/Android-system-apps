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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;

import android.app.usage.StorageStats;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.settingslib.applications.StorageStatsSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

/** Unit test for {@link StorageSizeBasePreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class StorageSizeBasePreferenceControllerTest {

    private StorageAppDetailPreference mStorageAppDetailPreference;
    private TestStorageSizeBasePreferenceController mController;
    private PreferenceControllerTestHelper<TestStorageSizeBasePreferenceController>
            mPreferenceControllerHelper;

    @Mock
    private AppsStorageStatsManager mAppsStorageStatsManager;

    private static class TestStorageSizeBasePreferenceController extends
            StorageSizeBasePreferenceController {

        TestStorageSizeBasePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected long getSize() {
            return 1_000_000_000;
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = RuntimeEnvironment.application;
        mStorageAppDetailPreference = new StorageAppDetailPreference(context);
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(context,
                TestStorageSizeBasePreferenceController.class, mStorageAppDetailPreference);
        mController = mPreferenceControllerHelper.getController();
    }

    @Test
    public void refreshUi_defaultState_nothingIsSet() {
        assertThat(mStorageAppDetailPreference.getDetailText()).isNull();
        assertThat(mController.isCachedCleared()).isFalse();
        assertThat(mController.isDataCleared()).isFalse();
        assertThat(mController.getAppStorageStats()).isNull();
    }

    @Test
    public void setAppsStorageStatsManager_shouldRegisterController() {
        mController.setAppsStorageStatsManager(mAppsStorageStatsManager);
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);

        verify(mAppsStorageStatsManager).registerListener(mController);
    }

    @Test
    public void onDataLoaded_shouldUpdateCachedAndDataClearedState() {
        mController.onDataLoaded(null, true, true);

        assertThat(mController.isCachedCleared()).isTrue();
        assertThat(mController.isDataCleared()).isTrue();
    }

    @Test
    public void onDataLoaded_appStorageStatsNotSet_shouldNotUpdateDetailText() {
        mController.onDataLoaded(null, true, true);

        assertThat(mController.getAppStorageStats()).isNull();
        assertThat(mStorageAppDetailPreference.getDetailText()).isNull();
    }

    @Test
    public void onDataLoaded_appStorageStatsSet_shouldUpdateDetailText() {
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);

        StorageStats stats = new StorageStats();
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);
        mController.setAppStorageStats(storageStats);
        mController.onDataLoaded(null, true, true);

        assertThat(mController.getAppStorageStats()).isNotNull();
        assertThat(mStorageAppDetailPreference.getDetailText()).isEqualTo("1.00 GB");
    }
}
