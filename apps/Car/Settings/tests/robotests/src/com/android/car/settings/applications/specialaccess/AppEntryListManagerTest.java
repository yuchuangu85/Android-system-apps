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

package com.android.car.settings.applications.specialaccess;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Looper;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.testutils.ShadowApplicationsState;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.ApplicationsState.AppEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** Unit test for {@link AppEntryListManager}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowApplicationsState.class})
public class AppEntryListManagerTest {

    @Mock
    private ApplicationsState mApplicationsState;
    @Mock
    private ApplicationsState.Session mSession;
    @Mock
    private AppEntryListManager.ExtraInfoBridge mExtraInfoBridge;
    @Mock
    private AppEntryListManager.AppFilterProvider mFilterProvider;
    @Mock
    private AppEntryListManager.Callback mCallback;
    @Captor
    private ArgumentCaptor<ApplicationsState.Callbacks> mSessionCallbacksCaptor;
    @Captor
    private ArgumentCaptor<List<AppEntry>> mEntriesCaptor;

    private AppEntryListManager mAppEntryListManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowApplicationsState.setInstance(mApplicationsState);
        when(mApplicationsState.newSession(mSessionCallbacksCaptor.capture())).thenReturn(mSession);
        when(mApplicationsState.getBackgroundLooper()).thenReturn(Looper.getMainLooper());

        mAppEntryListManager = new AppEntryListManager(RuntimeEnvironment.application);
        mAppEntryListManager.init(mExtraInfoBridge, mFilterProvider, mCallback);
    }

    @After
    public void tearDown() {
        ShadowApplicationsState.reset();
    }

    @Test
    public void start_resumesSession() {
        mAppEntryListManager.start();

        verify(mSession).onResume();
    }

    @Test
    public void onPackageListChanged_loadsExtraInfo() {
        mSessionCallbacksCaptor.getValue().onPackageListChanged();

        verify(mExtraInfoBridge).loadExtraInfo(any());
    }

    @Test
    public void onLoadEntriesComplete_loadsExtraInfo() {
        mSessionCallbacksCaptor.getValue().onLoadEntriesCompleted();

        verify(mExtraInfoBridge).loadExtraInfo(any());
    }

    @Test
    public void stop_pausesSession() {
        mAppEntryListManager.stop();

        verify(mSession).onPause();
    }

    @Test
    public void destroy_destroysSession() {
        mAppEntryListManager.destroy();

        verify(mSession).onDestroy();
    }

    @Test
    public void forceUpdate_loadsExtraInfo() {
        ArrayList<AppEntry> entries = new ArrayList<>();
        entries.add(mock(AppEntry.class));
        when(mSession.getAllApps()).thenReturn(entries);

        mAppEntryListManager.forceUpdate();

        verify(mExtraInfoBridge).loadExtraInfo(entries);
    }

    @Test
    public void forceUpdate_forEntry_loadsExtraInfo() {
        AppEntry entry = mock(AppEntry.class);

        mAppEntryListManager.forceUpdate(entry);

        verify(mExtraInfoBridge).loadExtraInfo(mEntriesCaptor.capture());
        assertThat(mEntriesCaptor.getValue()).containsExactly(entry);
    }

    @Test
    public void loadingFinished_rebuildsSession() {
        ApplicationsState.AppFilter appFilter = mock(ApplicationsState.AppFilter.class);
        when(mFilterProvider.getAppFilter()).thenReturn(appFilter);

        mSessionCallbacksCaptor.getValue().onLoadEntriesCompleted();

        verify(mSession).rebuild(eq(appFilter),
                eq(ApplicationsState.ALPHA_COMPARATOR), /* foreground= */ eq(false));
    }

    @Test
    public void onRebuildComplete_callsCallback() {
        ArrayList<AppEntry> entries = new ArrayList<>();
        entries.add(mock(AppEntry.class));

        mSessionCallbacksCaptor.getValue().onRebuildComplete(entries);

        verify(mCallback).onAppEntryListChanged(entries);
    }
}
