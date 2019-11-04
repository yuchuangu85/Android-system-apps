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

package com.android.car.settings.applications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.storage.VolumeInfo;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.settingslib.applications.ApplicationsState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;

/** Unit test for {@link ApplicationListItemManager}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class ApplicationListItemManagerTest {
    private static final String LABEL = "label";
    private static final String SIZE_STR = "12.34 MB";
    private static final String SOURCE = "source";
    private static final int UID = 12;

    private Context mContext;
    private ApplicationListItemManager mApplicationListItemManager;

    @Mock
    private VolumeInfo mVolumeInfo;
    @Mock
    private Lifecycle mLifecycle;
    @Mock
    private ApplicationsState mAppState;
    @Mock
    ApplicationsState.AppFilter mAppFilter;
    @Mock
    ApplicationListItemManager.AppListItemListener mAppListItemListener1;
    @Mock
    ApplicationListItemManager.AppListItemListener mAppListItemListener2;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mApplicationListItemManager = new ApplicationListItemManager(mVolumeInfo, mLifecycle,
                mAppState);
    }

    @Test
    public void startLoading_shouldStartNewSession() {
        mApplicationListItemManager.startLoading(mAppFilter, /* param= */ null);

        verify(mAppState).newSession(any(), eq(mLifecycle));
    }

    @Test
    public void onRebuildComplete_shouldNotifyRegisteredListener() {
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

        mApplicationListItemManager.registerListener(mAppListItemListener1);
        mApplicationListItemManager.registerListener(mAppListItemListener2);
        mApplicationListItemManager.onRebuildComplete(apps);

        verify(mAppListItemListener1).onDataLoaded(apps);
        verify(mAppListItemListener2).onDataLoaded(apps);
    }

    @Test
    public void onRebuildComplete_unRegisterOneListener_shouldNotifyRegisteredListener() {
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

        mApplicationListItemManager.registerListener(mAppListItemListener1);
        mApplicationListItemManager.registerListener(mAppListItemListener2);
        mApplicationListItemManager.unregisterlistener(mAppListItemListener2);
        mApplicationListItemManager.onRebuildComplete(apps);

        verify(mAppListItemListener1).onDataLoaded(apps);
        verify(mAppListItemListener2, times(0)).onDataLoaded(apps);
    }
}
