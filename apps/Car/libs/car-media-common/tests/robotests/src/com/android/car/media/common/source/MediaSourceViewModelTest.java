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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.RuntimeEnvironment.application;

import android.annotation.NonNull;
import android.app.Application;
import android.car.Car;
import android.car.media.CarMediaManager;
import android.content.ComponentName;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.Nullable;

import com.android.car.arch.common.testing.CaptureObserver;
import com.android.car.arch.common.testing.InstantTaskExecutorRule;
import com.android.car.arch.common.testing.TestLifecycleOwner;
import com.android.car.media.common.TestConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public class MediaSourceViewModelTest {

    private static final String BROWSER_CONTROLLER_PACKAGE_NAME = "browser";
    private static final String SESSION_MANAGER_CONTROLLER_PACKAGE_NAME = "mediaSessionManager";
    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule
    public final InstantTaskExecutorRule mTaskExecutorRule = new InstantTaskExecutorRule();
    @Rule
    public final TestLifecycleOwner mLifecycleOwner = new TestLifecycleOwner();

    @Mock
    public MediaBrowserCompat mMediaBrowser;
    @Mock
    public MediaControllerCompat mMediaControllerFromBrowser;
    @Mock
    public MediaControllerCompat mMediaControllerFromSessionManager;

    @Mock
    public Car mCar;
    @Mock
    public CarMediaManager mCarMediaManager;

    private MediaSourceViewModel mViewModel;

    private ComponentName mRequestedBrowseService;
    private MediaSource mMediaSource;

    @Before
    public void setUp() {
        when(mMediaControllerFromBrowser.getPackageName())
                .thenReturn(BROWSER_CONTROLLER_PACKAGE_NAME);
        when(mMediaControllerFromSessionManager.getPackageName())
                .thenReturn(SESSION_MANAGER_CONTROLLER_PACKAGE_NAME);

        mRequestedBrowseService = null;
        mMediaSource = null;
    }

    private void initializeViewModel() {
        mViewModel = new MediaSourceViewModel(application, new MediaSourceViewModel.InputFactory() {
            @Override
            public MediaBrowserConnector createMediaBrowserConnector(
                    @NonNull Application application,
                    @NonNull MediaBrowserConnector.Callback connectedBrowserCallback) {
                return new MediaBrowserConnector(application, connectedBrowserCallback) {
                    @Override
                    protected MediaBrowserCompat createMediaBrowser(ComponentName browseService,
                            MediaBrowserCompat.ConnectionCallback callback) {
                        mRequestedBrowseService = browseService;
                        return super.createMediaBrowser(browseService, callback);
                    }
                };
            }

            @Override
            public MediaControllerCompat getControllerForSession(
                    @Nullable MediaSessionCompat.Token token) {
                return mMediaControllerFromBrowser;
            }

            @Override
            public Car getCarApi() {
                return mCar;
            }

            @Override
            public CarMediaManager getCarMediaManager(Car carApi) {
                return mCarMediaManager;
            }

            @Override
            public MediaSource getMediaSource(ComponentName componentName) {
                return mMediaSource;
            }
        });
    }

    @Test
    public void testGetSelectedMediaSource_none() {
        initializeViewModel();
        CaptureObserver<MediaSource> observer = new CaptureObserver<>();

        mViewModel.getPrimaryMediaSource().observe(mLifecycleOwner, observer);

        assertThat(observer.getObservedValue()).isNull();
    }

    @Test
    public void testGetMediaController_connectedBrowser() {
        CaptureObserver<MediaControllerCompat> observer = new CaptureObserver<>();
        ComponentName testComponent = new ComponentName("test", "test");
        mMediaSource = mock(MediaSource.class);
        when(mMediaSource.getBrowseServiceComponentName()).thenReturn(testComponent);
        when(mMediaBrowser.isConnected()).thenReturn(true);
        initializeViewModel();

        mViewModel.getConnectedBrowserCallback().onConnectedBrowserChanged(mMediaBrowser);
        mViewModel.getMediaController().observe(mLifecycleOwner, observer);

        assertThat(observer.getObservedValue()).isSameAs(mMediaControllerFromBrowser);
        assertThat(mRequestedBrowseService).isEqualTo(testComponent);
    }

    @Test
    public void testGetMediaController_noActiveSession_notConnected() {
        CaptureObserver<MediaControllerCompat> observer = new CaptureObserver<>();
        ComponentName testComponent = new ComponentName("test", "test");
        mMediaSource = mock(MediaSource.class);
        when(mMediaSource.getBrowseServiceComponentName()).thenReturn(testComponent);
        when(mMediaBrowser.isConnected()).thenReturn(false);
        initializeViewModel();

        mViewModel.getMediaController().observe(mLifecycleOwner, observer);

        assertThat(observer.hasBeenNotified()).isTrue();
        assertThat(observer.getObservedValue()).isNull();
        assertThat(mRequestedBrowseService).isEqualTo(testComponent);
    }
}
