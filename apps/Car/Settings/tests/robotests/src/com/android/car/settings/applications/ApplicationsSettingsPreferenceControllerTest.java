/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.applications;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

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

/** Unit test for {@link ApplicationsSettingsPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class ApplicationsSettingsPreferenceControllerTest {

    private static final String SOURCE = "source";
    private static final int UID = 12;
    private static final String LABEL = "label";
    private static final String SIZE_STR = "12.34 MB";

    private Context mContext;
    private LogicalPreferenceGroup mLogicalPreferenceGroup;
    private PreferenceControllerTestHelper<ApplicationsSettingsPreferenceController>
            mPreferenceControllerHelper;
    private ApplicationsSettingsPreferenceController mController;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mLogicalPreferenceGroup = new LogicalPreferenceGroup(mContext);
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                ApplicationsSettingsPreferenceController.class, mLogicalPreferenceGroup);
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
        apps.add(appEntry);

        mController.onDataLoaded(apps);

        assertThat(mLogicalPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(mLogicalPreferenceGroup.getPreference(0).getTitle()).isEqualTo(LABEL);
        assertThat(mLogicalPreferenceGroup.getPreference(0).getSummary()).isEqualTo(SIZE_STR);
    }

    @Test
    public void preferenceClick_launchesDetailFragment() {
        ArrayList<ApplicationsState.AppEntry> apps = new ArrayList<>();
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.uid = UID;
        appInfo.sourceDir = SOURCE;

        ApplicationsState.AppEntry appEntry = new ApplicationsState.AppEntry(mContext, appInfo,
                1234L);
        appEntry.label = LABEL;
        appEntry.sizeStr = SIZE_STR;
        appEntry.icon = mContext.getDrawable(R.drawable.test_icon);
        apps.add(appEntry);

        mController.onDataLoaded(apps);

        Preference preference = mLogicalPreferenceGroup.getPreference(0);
        preference.performClick();

        verify(mPreferenceControllerHelper.getMockFragmentController()).launchFragment(
                any(ApplicationDetailsFragment.class));
    }

}
