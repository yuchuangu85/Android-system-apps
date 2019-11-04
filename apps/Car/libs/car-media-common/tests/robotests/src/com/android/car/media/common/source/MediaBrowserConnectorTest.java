/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.car.media.common.source;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.RuntimeEnvironment.application;

import android.annotation.NonNull;
import android.content.ComponentName;
import android.support.v4.media.MediaBrowserCompat;

import com.android.car.arch.common.testing.InstantTaskExecutorRule;
import com.android.car.arch.common.testing.TestLifecycleOwner;
import com.android.car.media.common.TestConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public class MediaBrowserConnectorTest {

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule
    public final InstantTaskExecutorRule mTaskExecutorRule = new InstantTaskExecutorRule();
    @Rule
    public final TestLifecycleOwner mLifecycleOwner = new TestLifecycleOwner();

    @Mock
    public MediaBrowserCompat mMediaBrowser1;
    @Mock
    public MediaBrowserCompat mMediaBrowser2;

    private final ComponentName mBrowseService1 = new ComponentName("mediaService1", "className1");
    private final ComponentName mBrowseService2 = new ComponentName("mediaService2", "className2");

    private final Map<ComponentName, MediaBrowserCompat> mBrowsers = new HashMap<>(2);

    private MediaBrowserConnector mBrowserConnector;
    private MediaBrowserCompat.ConnectionCallback mConnectionCallback;

    @Mock
    public MediaBrowserConnector.Callback mConnectedBrowserCallback;
    @Captor
    private ArgumentCaptor<MediaBrowserCompat> mConnectedBrowserCaptor;

    @Before
    public void setUp() {
        mBrowsers.put(mBrowseService1, mMediaBrowser1);
        mBrowsers.put(mBrowseService2, mMediaBrowser2);
        when(mMediaBrowser1.isConnected()).thenReturn(false);
        when(mMediaBrowser2.isConnected()).thenReturn(false);

        doNothing().when(mConnectedBrowserCallback).onConnectedBrowserChanged(
                mConnectedBrowserCaptor.capture());

        mBrowserConnector = new MediaBrowserConnector(application, mConnectedBrowserCallback) {
            @Override
            protected MediaBrowserCompat createMediaBrowser(@NonNull ComponentName browseService,
                    @NonNull MediaBrowserCompat.ConnectionCallback callback) {
                mConnectionCallback = callback;
                return mBrowsers.get(browseService);
            }
        };
    }

    @Test
    public void testExceptionOnConnectDoesNotCrash() {
        setConnectionAction(() -> {
            throw new IllegalStateException("expected");
        });

        mBrowserConnector.connectTo(mBrowseService1);
        verify(mMediaBrowser1).connect();
    }

    @Test
    public void testConnectionCallback_onConnected() {
        setConnectionAction(() -> mConnectionCallback.onConnected());

        mBrowserConnector.connectTo(mBrowseService1);

        assertThat(mConnectedBrowserCaptor.getValue()).isEqualTo(mMediaBrowser1);
    }

    @Test
    public void testConnectionCallback_onConnectionFailed() {
        setConnectionAction(() -> mConnectionCallback.onConnectionFailed());

        mBrowserConnector.connectTo(mBrowseService1);

        assertThat(mConnectedBrowserCaptor.getValue()).isNull();
    }

    @Test
    public void testConnectionCallback_onConnectionSuspended() {
        setConnectionAction(() -> {
            mConnectionCallback.onConnected();
            mConnectionCallback.onConnectionSuspended();
        });

        mBrowserConnector.connectTo(mBrowseService1);


        List<MediaBrowserCompat> browsers = mConnectedBrowserCaptor.getAllValues();
        assertThat(browsers.get(0)).isEqualTo(mMediaBrowser1);
        assertThat(browsers.get(1)).isNull();
    }

    @Test
    public void testConnectionCallback_onConnectedIgnoredWhenLate() {
        mBrowserConnector.connectTo(mBrowseService1);
        MediaBrowserCompat.ConnectionCallback cb1 = mConnectionCallback;

        mBrowserConnector.connectTo(mBrowseService2);
        MediaBrowserCompat.ConnectionCallback cb2 = mConnectionCallback;

        cb2.onConnected();
        cb1.onConnected();
        assertThat(mConnectedBrowserCaptor.getValue()).isEqualTo(mMediaBrowser2);
    }

    private void setConnectionAction(@NonNull Runnable action) {
        doAnswer(invocation -> {
            action.run();
            return null;
        }).when(mMediaBrowser1).connect();
    }


}
