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
package android.car.media;

import android.annotation.RequiresPermission;
import android.car.Car;
import android.car.CarManagerBase;
import android.content.ComponentName;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.HashMap;
import java.util.Map;

/**
 * API for updating and receiving updates to the primary media source in the car.
 * @hide
 */
public final class CarMediaManager implements CarManagerBase {

    private final ICarMedia mService;
    private Map<MediaSourceChangedListener, ICarMediaSourceListener> mCallbackMap = new HashMap();

    /**
     * Get an instance of the CarPowerManager.
     *
     * Should not be obtained directly by clients, use {@link Car#getCarManager(String)} instead.
     * @hide
     */
    public CarMediaManager(IBinder service) {
        mService = ICarMedia.Stub.asInterface(service);
    }

    /**
     * Listener for updates to the primary media source
     * @hide
     */
    public interface MediaSourceChangedListener {

        /**
         * Called when the primary media source is changed
         * @hide
         */
        void onMediaSourceChanged(ComponentName componentName);
    }

    /**
     * Gets the currently active media source, or null if none exists
     * Requires android.Manifest.permission.MEDIA_CONTENT_CONTROL permission
     * @hide
     */
    @RequiresPermission(value = android.Manifest.permission.MEDIA_CONTENT_CONTROL)
    public synchronized ComponentName getMediaSource() {
        try {
            return mService.getMediaSource();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the currently active media source
     * Requires android.Manifest.permission.MEDIA_CONTENT_CONTROL permission
     * @hide
     */
    @RequiresPermission(value = android.Manifest.permission.MEDIA_CONTENT_CONTROL)
    public synchronized void setMediaSource(ComponentName componentName) {
        try {
            mService.setMediaSource(componentName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Register a callback that receives updates to the active media source.
     * Requires android.Manifest.permission.MEDIA_CONTENT_CONTROL permission
     * @hide
     */
    @RequiresPermission(value = android.Manifest.permission.MEDIA_CONTENT_CONTROL)
    public synchronized void registerMediaSourceListener(MediaSourceChangedListener callback) {
        try {
            ICarMediaSourceListener binderCallback = new ICarMediaSourceListener.Stub() {
                @Override
                public void onMediaSourceChanged(ComponentName componentName) {
                    callback.onMediaSourceChanged(componentName);
                }
            };
            mCallbackMap.put(callback, binderCallback);
            mService.registerMediaSourceListener(binderCallback);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Unregister a callback that receives updates to the active media source.
     * Requires android.Manifest.permission.MEDIA_CONTENT_CONTROL permission
     * @hide
     */
    @RequiresPermission(value = android.Manifest.permission.MEDIA_CONTENT_CONTROL)
    public synchronized void unregisterMediaSourceListener(MediaSourceChangedListener callback) {
        try {
            ICarMediaSourceListener binderCallback = mCallbackMap.remove(callback);
            mService.unregisterMediaSourceListener(binderCallback);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** @hide */
    @Override
    public synchronized void onCarDisconnected() {
    }
}
