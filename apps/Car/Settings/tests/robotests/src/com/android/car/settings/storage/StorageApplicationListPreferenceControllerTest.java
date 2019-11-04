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

import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.settingslib.applications.ApplicationsState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;

/** Unit test for {@link StorageApplicationListPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class StorageApplicationListPreferenceControllerTest {

    private static final String SOURCE = "source";
    private static final int UID = 12;
    private static final String LABEL = "label";
    private static final String SIZE_STR = "12.34 MB";
    private static final String UPDATED_SIZE_STR = "15.34 MB";
    private static final String PACKAGE_NAME = "com.google.packageName";

    private Context mContext;
    private LogicalPreferenceGroup mLogicalPreferenceGroup;
    private StorageApplicationListPreferenceController mController;
    private PreferenceControllerTestHelper<StorageApplicationListPreferenceController>
            mPreferenceControllerHelper;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mLogicalPreferenceGroup = new LogicalPreferenceGroup(mContext);
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                StorageApplicationListPreferenceController.class, mLogicalPreferenceGroup);
        mController = mPreferenceControllerHelper.getController();
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);
    }

    @Test
    public void defaultInitialize_hasNoPreference() {
        assertThat(mLogicalPreferenceGroup.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void onDataLoaded_addPreference_hasOnePreference() {
        ArrayList<ApplicationsState.AppEntry> apps = new ArrayList<>();
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.uid = UID;
        appInfo.sourceDir = SOURCE;

        ApplicationsState.AppEntry appEntry = new ApplicationsState.AppEntry(mContext, appInfo,
                1234L);
        appEntry.label = LABEL;
        appEntry.sizeStr = SIZE_STR;
        appEntry.icon = mContext.getDrawable(R.drawable.test_icon);
        appEntry.info.packageName = PACKAGE_NAME;
        apps.add(appEntry);

        mController.onDataLoaded(apps);

        assertThat(mLogicalPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(mLogicalPreferenceGroup.getPreference(0).getTitle()).isEqualTo(LABEL);
        assertThat(mLogicalPreferenceGroup.getPreference(0).getSummary()).isEqualTo(SIZE_STR);
    }

    @Test
    public void onDataLoaded_updatePreference_hasOnePreferenceWithUpdatedValues() {
        ArrayList<ApplicationsState.AppEntry> apps = new ArrayList<>();
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.uid = UID;
        appInfo.sourceDir = SOURCE;

        ApplicationsState.AppEntry appEntry = new ApplicationsState.AppEntry(mContext, appInfo,
                1234L);
        appEntry.label = LABEL;
        appEntry.sizeStr = SIZE_STR;
        appEntry.icon = mContext.getDrawable(R.drawable.test_icon);
        appEntry.info.packageName = PACKAGE_NAME;
        apps.add(appEntry);

        mController.onDataLoaded(apps);

        apps.clear();
        appEntry = new ApplicationsState.AppEntry(mContext, appInfo, 1234L);
        appEntry.label = LABEL;
        appEntry.sizeStr = UPDATED_SIZE_STR;
        appEntry.icon = mContext.getDrawable(R.drawable.test_icon);
        appEntry.info.packageName = PACKAGE_NAME;
        apps.add(appEntry);

        mController.onDataLoaded(apps);

        assertThat(mLogicalPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(mLogicalPreferenceGroup.getPreference(0).getTitle()).isEqualTo(LABEL);
        assertThat(mLogicalPreferenceGroup.getPreference(0).getSummary()).isEqualTo(
                UPDATED_SIZE_STR);
    }
}
