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

package com.android.car.settings.datausage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.INetworkStatsService;
import android.net.INetworkStatsSession;
import android.net.NetworkPolicyManager;
import android.net.NetworkStats;
import android.os.Bundle;

import androidx.loader.app.LoaderManager;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.testutils.ShadowINetworkStatsServiceStub;
import com.android.car.settings.testutils.ShadowNetworkPolicyManager;

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

/** Unit test for {@link AppsNetworkStatsManager}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowINetworkStatsServiceStub.class, ShadowNetworkPolicyManager.class})
public class AppsNetworkStatsManagerTest {

    private Context mContext;
    private AppsNetworkStatsManager mAppsNetworkStatsManager;

    @Captor
    private ArgumentCaptor<LoaderManager.LoaderCallbacks<NetworkStats>>
            mCallbacksArgumentCaptor;

    @Mock
    private AppsNetworkStatsManager.Callback mCallback1;

    @Mock
    private AppsNetworkStatsManager.Callback mCallback2;

    @Mock
    private LoaderManager mLoaderManager;

    @Mock
    private INetworkStatsService mINetworkStatsService;

    @Mock
    private INetworkStatsSession mINetworkStatsSession;

    @Mock
    private NetworkPolicyManager mNetworkPolicyManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;

        when(mINetworkStatsService.openSession()).thenReturn(mINetworkStatsSession);
        ShadowINetworkStatsServiceStub.setINetworkStatsSession(mINetworkStatsService);

        when(mNetworkPolicyManager.getUidsWithPolicy(anyInt())).thenReturn(new int[0]);
        ShadowNetworkPolicyManager.setNetworkPolicyManager(mNetworkPolicyManager);

        mAppsNetworkStatsManager = new AppsNetworkStatsManager(mContext);
        mAppsNetworkStatsManager.startLoading(mLoaderManager, Bundle.EMPTY);

        verify(mLoaderManager).restartLoader(eq(1), eq(Bundle.EMPTY),
                mCallbacksArgumentCaptor.capture());
    }

    @After
    public void tearDown() {
        ShadowINetworkStatsServiceStub.reset();
        ShadowNetworkPolicyManager.reset();
    }

    @Test
    public void callback_onLoadFinished_listenerOnDataLoadedCalled() throws Exception {
        mAppsNetworkStatsManager.registerListener(mCallback1);
        mAppsNetworkStatsManager.registerListener(mCallback2);

        NetworkStats networkStats = new NetworkStats(0, 0);

        mCallbacksArgumentCaptor.getValue().onLoadFinished(null, networkStats);

        verify(mCallback1).onDataLoaded(eq(networkStats), any());
        verify(mCallback2).onDataLoaded(eq(networkStats), any());
    }

    @Test
    public void callback_unregisterListener_onlyOneListenerOnDataLoadedCalled() throws Exception {
        mAppsNetworkStatsManager.registerListener(mCallback1);
        mAppsNetworkStatsManager.registerListener(mCallback2);
        mAppsNetworkStatsManager.unregisterListener(mCallback2);

        NetworkStats networkStats = new NetworkStats(0, 0);

        mCallbacksArgumentCaptor.getValue().onLoadFinished(null, networkStats);

        verify(mCallback1).onDataLoaded(eq(networkStats), any());
        verify(mCallback2, never()).onDataLoaded(eq(networkStats), any());
    }

    @Test
    public void callback_notLoaded_listenerOnDataLoadedNotCalled() throws Exception {
        mAppsNetworkStatsManager.registerListener(mCallback1);
        mAppsNetworkStatsManager.registerListener(mCallback2);
        mAppsNetworkStatsManager.unregisterListener(mCallback2);

        NetworkStats networkStats = new NetworkStats(0, 0);

        verify(mCallback1, never()).onDataLoaded(eq(networkStats), any());
        verify(mCallback2, never()).onDataLoaded(eq(networkStats), any());
    }
}
