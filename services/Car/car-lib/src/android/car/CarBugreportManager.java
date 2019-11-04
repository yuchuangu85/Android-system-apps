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

package android.car;

import android.annotation.FloatRange;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import com.android.internal.util.Preconditions;

import libcore.io.IoUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

/**
 * Car specific bugreport manager. Only available for userdebug and eng builds.
 *
 * @hide
 */
public final class CarBugreportManager implements CarManagerBase {

    private final ICarBugreportService mService;
    private Handler mHandler;

    /**
     * Callback from carbugreport manager. Callback methods are always called on the main thread.
     */
    public abstract static class CarBugreportManagerCallback {

        @Retention(RetentionPolicy.SOURCE)
        @IntDef(prefix = {"CAR_BUGREPORT_ERROR_"}, value = {
                CAR_BUGREPORT_DUMPSTATE_FAILED,
                CAR_BUGREPORT_IN_PROGRESS,
                CAR_BUGREPORT_DUMPSTATE_CONNECTION_FAILED,
                CAR_BUGREPORT_SERVICE_NOT_AVAILABLE
        })

        public @interface CarBugreportErrorCode {
        }

        /** Dumpstate failed to generate bugreport. */
        public static final int CAR_BUGREPORT_DUMPSTATE_FAILED = 1;

        /**
         * Another bugreport is in progress.
         */
        public static final int CAR_BUGREPORT_IN_PROGRESS = 2;

        /** Cannot connect to dumpstate */
        public static final int CAR_BUGREPORT_DUMPSTATE_CONNECTION_FAILED = 3;

        /** Car bugreport service is not available (true for user builds) */
        public static final int CAR_BUGREPORT_SERVICE_NOT_AVAILABLE = 4;

        /**
         * Called when bugreport progress changes.
         *
         * <p>It's never called after {@link #onError} or {@link #onFinished}.
         *
         * @param progress - a number in [0.0, 100.0].
         */
        public void onProgress(@FloatRange(from = 0f, to = 100f) float progress) {
        }

        /**
         * Called on an error condition with one of the error codes listed above.
         *
         * @param errorCode the error code that defines failure reason.
         */
        public void onError(@CarBugreportErrorCode int errorCode) {
        }

        /**
         * Called when taking bugreport finishes successfully.
         */
        public void onFinished() {
        }
    }

    /**
     * Internal wrapper class to service.
     */
    private static final class CarBugreportManagerCallbackWrapper extends
            ICarBugreportCallback.Stub {

        private final WeakReference<CarBugreportManagerCallback> mWeakCallback;
        private final WeakReference<Handler> mWeakHandler;

        /**
         * Create a new callback wrapper.
         *
         * @param callback the callback passed from app
         * @param handler  the handler to execute callbacks on
         */
        CarBugreportManagerCallbackWrapper(CarBugreportManagerCallback callback,
                Handler handler) {
            mWeakCallback = new WeakReference<>(callback);
            mWeakHandler = new WeakReference<>(handler);
        }

        @Override
        public void onProgress(@FloatRange(from = 0f, to = 100f) float progress) {
            CarBugreportManagerCallback callback = mWeakCallback.get();
            Handler handler = mWeakHandler.get();
            if (handler != null && callback != null) {
                handler.post(() -> callback.onProgress(progress));
            }
        }

        @Override
        public void onError(@CarBugreportManagerCallback.CarBugreportErrorCode int errorCode) {
            CarBugreportManagerCallback callback = mWeakCallback.get();
            Handler handler = mWeakHandler.get();
            if (handler != null && callback != null) {
                handler.post(() -> callback.onError(errorCode));
            }
        }

        @Override
        public void onFinished() {
            CarBugreportManagerCallback callback = mWeakCallback.get();
            Handler handler = mWeakHandler.get();
            if (handler != null && callback != null) {
                handler.post(callback::onFinished);
            }
        }
    }

    /**
     * Get an instance of the CarBugreportManager
     *
     * Should not be obtained directly by clients, use {@link Car#getCarManager(String)} instead.
     */
    public CarBugreportManager(IBinder service, Context context) {
        mService = ICarBugreportService.Stub.asInterface(service);
        mHandler = new Handler(context.getMainLooper());
    }

    /**
     * Request a bug report. A zipped (i.e. legacy) bugreport is generated in the background
     * using dumpstate. This API also generates extra files that does not exist in the legacy
     * bugreport and makes them available through a extra output file. Currently the extra
     * output contains the screenshots for all the physical displays.
     *
     * <p>The file descriptor is closed when bugreport is written or if an exception happens.
     *
     * <p>This method is enabled only for one bug reporting app. It can be configured using
     * {@code config_car_bugreport_application} string that is defined in
     * {@code packages/services/Car/service/res/values/config.xml}. To learn more please
     * see {@code packages/services/Car/tests/BugReportApp/README.md}.
     *
     * @param output the zipped bugreport file
     * @param extraOutput a zip file that contains extra files generated for automotive.
     * @param callback  the callback for reporting dump status
     */
    @RequiresPermission(android.Manifest.permission.DUMP)
    public void requestBugreport(
            @NonNull ParcelFileDescriptor output,
            @NonNull ParcelFileDescriptor extraOutput,
            @NonNull CarBugreportManagerCallback callback) {
        Preconditions.checkNotNull(output);
        Preconditions.checkNotNull(extraOutput);
        Preconditions.checkNotNull(callback);
        try {
            CarBugreportManagerCallbackWrapper wrapper =
                    new CarBugreportManagerCallbackWrapper(callback, mHandler);
            mService.requestBugreport(output, extraOutput, wrapper);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        } finally {
            IoUtils.closeQuietly(output);
            IoUtils.closeQuietly(extraOutput);
        }
    }

    @Override
    public void onCarDisconnected() {
    }
}
