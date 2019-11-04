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
package com.android.car.settings.location;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.settingslib.location.RecentLocationApps;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class RecentLocationRequestsPreferenceControllerTest {

    @Mock
    private RecentLocationApps mRecentLocationApps;

    private RecentLocationRequestsPreferenceController mController;
    private PreferenceScreen mScreen;
    private Context mContext;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mScreen = new PreferenceManager(mContext).createPreferenceScreen(mContext);
        PreferenceControllerTestHelper<RecentLocationRequestsPreferenceController>
                controllerHelper = new PreferenceControllerTestHelper<>(mContext,
                RecentLocationRequestsPreferenceController.class, mScreen);
        mController = controllerHelper.getController();
        mController.setRecentLocationApps(mRecentLocationApps);
        controllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @Test
    public void refreshUi_noRecentRequests_messageDisplayed() {
        when(mRecentLocationApps.getAppListSorted(true)).thenReturn(Collections.emptyList());
        mController.refreshUi();

        assertThat(mScreen.getPreference(0).getTitle()).isEqualTo(
                mContext.getString(R.string.location_settings_recent_requests_empty_message));
    }

    @Test
    public void refreshUi_someRecentRequests_preferencesAddedToScreen() {
        List<RecentLocationApps.Request> list = Arrays.asList(
                mock(RecentLocationApps.Request.class),
                mock(RecentLocationApps.Request.class),
                mock(RecentLocationApps.Request.class));
        when(mRecentLocationApps.getAppListSorted(true)).thenReturn(list);
        mController.refreshUi();

        assertThat(mScreen.getPreferenceCount()).isEqualTo(list.size());
    }

    @Test
    public void refreshUi_newRecentRequests_listIsUpdated() {
        List<RecentLocationApps.Request> list1 = Arrays.asList(
                mock(RecentLocationApps.Request.class),
                mock(RecentLocationApps.Request.class),
                mock(RecentLocationApps.Request.class));
        when(mRecentLocationApps.getAppListSorted(true)).thenReturn(list1);

        List<RecentLocationApps.Request> list2 = new ArrayList<>(list1);
        list2.add(mock(RecentLocationApps.Request.class));

        mController.refreshUi();
        assertThat(mScreen.getPreferenceCount()).isEqualTo(list1.size());

        when(mRecentLocationApps.getAppListSorted(true)).thenReturn(list2);
        mController.refreshUi();

        assertThat(mScreen.getPreferenceCount()).isEqualTo(list2.size());
    }
}
