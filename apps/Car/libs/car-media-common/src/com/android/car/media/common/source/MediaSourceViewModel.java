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

import static com.android.car.apps.common.util.CarAppsDebugUtils.idHash;
import static com.android.car.arch.common.LiveDataFunctions.dataOf;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Application;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.media.CarMediaManager;
import android.content.ComponentName;
import android.media.session.MediaController;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Objects;

/**
 * Contains observable data needed for displaying playback and browse UI.
 * MediaSourceViewModel is a singleton tied to the application to provide a single source of truth.
 */
public class MediaSourceViewModel extends AndroidViewModel {
    private static final String TAG = "MediaSourceViewModel";

    private static MediaSourceViewModel sInstance;
    private final Car mCar;
    private CarMediaManager mCarMediaManager;

    // Primary media source.
    private final MutableLiveData<MediaSource> mPrimaryMediaSource = dataOf(null);
    // Connected browser for the primary media source.
    private final MutableLiveData<MediaBrowserCompat> mConnectedMediaBrowser = dataOf(null);
    // Media controller for the connected browser.
    private final MutableLiveData<MediaControllerCompat> mMediaController = dataOf(null);

    private final Handler mHandler;
    private final CarMediaManager.MediaSourceChangedListener mMediaSourceListener;

    /**
     * Factory for creating dependencies. Can be swapped out for testing.
     */
    @VisibleForTesting
    interface InputFactory {
        MediaBrowserConnector createMediaBrowserConnector(@NonNull Application application,
                @NonNull MediaBrowserConnector.Callback connectedBrowserCallback);

        MediaControllerCompat getControllerForSession(@Nullable MediaSessionCompat.Token session);

        Car getCarApi();

        CarMediaManager getCarMediaManager(Car carApi) throws CarNotConnectedException;

        MediaSource getMediaSource(ComponentName componentName);
    }

    /** Returns the MediaSourceViewModel singleton tied to the application. */
    public static MediaSourceViewModel get(@NonNull Application application) {
        if (sInstance == null) {
            sInstance = new MediaSourceViewModel(application);
        }
        return sInstance;
    }

    /**
     * Create a new instance of MediaSourceViewModel
     *
     * @see AndroidViewModel
     */
    private MediaSourceViewModel(@NonNull Application application) {
        this(application, new InputFactory() {
            @Override
            public MediaBrowserConnector createMediaBrowserConnector(
                    @NonNull Application application,
                    @NonNull MediaBrowserConnector.Callback connectedBrowserCallback) {
                return new MediaBrowserConnector(application, connectedBrowserCallback);
            }

            @Override
            public MediaControllerCompat getControllerForSession(
                    @Nullable MediaSessionCompat.Token token) {
                if (token == null) return null;
                try {
                    return new MediaControllerCompat(application, token);
                } catch (RemoteException e) {
                    Log.e(TAG, "Couldn't get MediaControllerCompat", e);
                    return null;
                }
            }

            @Override
            public Car getCarApi() {
                return Car.createCar(application);
            }

            @Override
            public CarMediaManager getCarMediaManager(Car carApi) throws CarNotConnectedException {
                return (CarMediaManager) carApi.getCarManager(Car.CAR_MEDIA_SERVICE);
            }

            @Override
            public MediaSource getMediaSource(ComponentName componentName) {
                return componentName == null ? null : MediaSource.create(application,
                        componentName);
            }
        });
    }

    private final InputFactory mInputFactory;
    private final MediaBrowserConnector mBrowserConnector;
    private final MediaBrowserConnector.Callback mConnectedBrowserCallback;

    @VisibleForTesting
    MediaSourceViewModel(@NonNull Application application, @NonNull InputFactory inputFactory) {
        super(application);

        mInputFactory = inputFactory;
        mCar = inputFactory.getCarApi();

        mConnectedBrowserCallback = browser -> {
            mConnectedMediaBrowser.setValue(browser);
            if (browser != null) {
                if (!browser.isConnected()) {
                    Log.e(TAG, "Browser is NOT connected !! "
                            + mPrimaryMediaSource.getValue().toString() + idHash(browser));
                    mMediaController.setValue(null);
                } else {
                    mMediaController.setValue(mInputFactory.getControllerForSession(
                            browser.getSessionToken()));
                }
            } else {
                mMediaController.setValue(null);
            }
        };
        mBrowserConnector = inputFactory.createMediaBrowserConnector(application,
                mConnectedBrowserCallback);

        mHandler = new Handler(application.getMainLooper());
        mMediaSourceListener = componentName -> mHandler.post(
                () -> updateModelState(mInputFactory.getMediaSource(componentName)));

        try {
            mCarMediaManager = mInputFactory.getCarMediaManager(mCar);
            mCarMediaManager.registerMediaSourceListener(mMediaSourceListener);
            updateModelState(mInputFactory.getMediaSource(mCarMediaManager.getMediaSource()));
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Car not connected", e);
        }
    }

    @VisibleForTesting
    MediaBrowserConnector.Callback getConnectedBrowserCallback() {
        return mConnectedBrowserCallback;
    }

    /**
     * Returns a LiveData that emits the MediaSource that is to be browsed or displayed.
     */
    public LiveData<MediaSource> getPrimaryMediaSource() {
        return mPrimaryMediaSource;
    }

    /**
     * Updates the primary media source.
     */
    public void setPrimaryMediaSource(@NonNull MediaSource mediaSource) {
        mCarMediaManager.setMediaSource(mediaSource.getBrowseServiceComponentName());
    }

    /**
     * Returns a LiveData that emits the currently connected MediaBrowser. Emits {@code null} if no
     * MediaSource is set, if the MediaSource does not support browsing, or if the MediaBrowser is
     * not connected.
     */
    public LiveData<MediaBrowserCompat> getConnectedMediaBrowser() {
        return mConnectedMediaBrowser;
    }

    /**
     * Returns a LiveData that emits a {@link MediaController} that allows controlling this media
     * source, or emits {@code null} if the media source doesn't support browsing or the browser is
     * not connected.
     */
    public LiveData<MediaControllerCompat> getMediaController() {
        return mMediaController;
    }

    private void updateModelState(MediaSource newMediaSource) {
        MediaSource oldMediaSource = mPrimaryMediaSource.getValue();

        if (Objects.equals(oldMediaSource, newMediaSource)) {
            return;
        }

        // Reset dependent values to avoid propagating inconsistencies.
        mMediaController.setValue(null);
        mConnectedMediaBrowser.setValue(null);
        mBrowserConnector.connectTo(null);

        // Broadcast the new source
        mPrimaryMediaSource.setValue(newMediaSource);

        // Recompute dependent values
        if (newMediaSource == null) {
            return;
        }

        ComponentName browseService = newMediaSource.getBrowseServiceComponentName();
        mBrowserConnector.connectTo(browseService);
    }
}
