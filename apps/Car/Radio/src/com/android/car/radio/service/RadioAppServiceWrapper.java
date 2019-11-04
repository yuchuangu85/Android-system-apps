/**
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

package com.android.car.radio.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.media.session.PlaybackState;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.car.radio.bands.ProgramType;
import com.android.car.radio.bands.RegionConfig;
import com.android.car.radio.platform.RadioTunerExt.TuneCallback;
import com.android.car.radio.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link IRadioAppService} wrapper to abstract out some nuances of interactions
 * with remote services.
 */
public class RadioAppServiceWrapper {
    private static final String TAG = "BcRadioApp.servicewr";

    /**
     * Binding has just been requested and we're connecting to the {@link RadioAppService} now.
     */
    public static final int STATE_CONNECTING = 1;

    /**
     * {@link RadioAppService} connection is up and running.
     */
    public static final int STATE_CONNECTED = 2;

    /**
     * This device has no broadcastradio hardware.
     */
    public static final int STATE_NOT_SUPPORTED = 3;

    /**
     * Some problem has occured (either RadioAppService crashed or there was HW problem).
     */
    public static final int STATE_ERROR = 4;

    /**
     * Application state.
     */
    @IntDef(value = {
        STATE_CONNECTING,
        STATE_CONNECTED,
        STATE_NOT_SUPPORTED,
        STATE_ERROR,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ConnectionState {}

    private Context mClientContext;
    @Nullable
    private final AtomicReference<IRadioAppService> mService = new AtomicReference<>();
    private final Object mLock = new Object();

    private final MutableLiveData<Integer> mConnectionState = new MutableLiveData<>();
    private final MutableLiveData<Integer> mPlaybackState = new MutableLiveData<>();
    private final MutableLiveData<ProgramInfo> mCurrentProgram = new MutableLiveData<>();
    private final MutableLiveData<List<ProgramInfo>> mProgramList = new MutableLiveData<>();

    {
        mConnectionState.postValue(STATE_CONNECTING);
        mPlaybackState.postValue(PlaybackState.STATE_NONE);
    }

    private static class TuneCallbackAdapter extends ITuneCallback.Stub {
        private final TuneCallback mCallback;

        private TuneCallbackAdapter(@Nullable TuneCallback cb) {
            mCallback = cb;
        }

        public void onFinished(boolean succeeded) {
            if (mCallback != null) mCallback.onFinished(succeeded);
        }
    }

    /**
     * Wraps remote service instance.
     *
     * You must call {@link #bind} once the context is available.
     */
    public RadioAppServiceWrapper() {}

    /**
     * Wraps existing (local) service instance.
     *
     * For use by the RadioAppService itself.
     */
    public RadioAppServiceWrapper(@NonNull IRadioAppService service) {
        Objects.requireNonNull(service);
        mService.set(service);
        initialize(service);
    }

    private void initialize(@NonNull IRadioAppService service) {
        try {
            service.addCallback(mCallback);
        } catch (RemoteException e) {
            throw new RuntimeException("Wrapper initialization failed", e);
        }
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            RadioAppServiceWrapper.this.onServiceConnected(binder,
                    Objects.requireNonNull(IRadioAppService.Stub.asInterface(binder)));
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            onServiceFailure();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            onServiceFailure();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            RadioAppServiceWrapper.this.onNullBinding();
        }
    };

    private final IRadioAppCallback mCallback = new IRadioAppCallback.Stub() {
        @Override
        public void onHardwareError() {
            onServiceFailure();
        }

        @Override
        public void onCurrentProgramChanged(ProgramInfo info) {
            mCurrentProgram.postValue(info);
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            mPlaybackState.postValue(state);
        }

        @Override
        public void onProgramListChanged(List<ProgramInfo> plist) {
            mProgramList.postValue(plist);
        }
    };

    /**
     * Binds to running {@link RadioAppService} instance or starts one if it doesn't exist.
     */
    public void bind(@NonNull Context context) {
        mClientContext = Objects.requireNonNull(context);

        Intent bindIntent = new Intent(RadioAppService.ACTION_APP_SERVICE, null,
                context, RadioAppService.class);
        if (!context.bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE)) {
            throw new RuntimeException("Failed to bind to RadioAppService");
        }
    }

    /**
     * Unbinds from remote radio service.
     */
    public void unbind() {
        if (mClientContext == null) {
            throw new IllegalStateException(
                    "This is not a remote service wrapper, you can't unbind it");
        }
        callService(service -> service.removeCallback(mCallback));
        mClientContext.unbindService(mServiceConnection);
    }

    private void onServiceConnected(IBinder binder, @NonNull IRadioAppService service) {
        Log.d(TAG, "RadioAppService connected");
        mService.set(service);
        initialize(service);
        mConnectionState.postValue(STATE_CONNECTED);
    }

    private void onServiceFailure() {
        if (mService.getAndSet(null) == null) return;
        Log.e(TAG, "RadioAppService failed " + (mClientContext == null ? "(local)" : "(remote)"));
        mConnectionState.postValue(STATE_ERROR);
    }

    private void onNullBinding() {
        Log.i(TAG, "RadioAppService is not accepting connections. "
                + "It means the radio hardware is not available");
        mClientContext.unbindService(mServiceConnection);
        mConnectionState.postValue(STATE_NOT_SUPPORTED);
    }

    private interface ServiceVoidOperation {
        void execute(@NonNull IRadioAppService service) throws RemoteException;
    }

    private interface ServiceOperation<V> {
        V execute(@NonNull IRadioAppService service) throws RemoteException;
    }

    private <V> V queryService(@NonNull ServiceOperation<V> op, V defaultResponse) {
        IRadioAppService service = mService.get();
        if (service == null) {
            throw new IllegalStateException("Service is not connected");
        }
        try {
            return op.execute(service);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote call failed", e);
            onServiceFailure();
            return defaultResponse;
        }
    }

    private void callService(@NonNull ServiceVoidOperation op) {
        IRadioAppService service = mService.get();
        if (service == null) {
            throw new IllegalStateException("Service is not connected");
        }
        try {
            op.execute(service);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote call failed", e);
            onServiceFailure();
        }
    }

    /**
     * Returns a {@link LiveData} stating if the {@link RadioAppService} connection state.
     *
     * @see {@link ConnectionState}.
     */
    @NonNull
    public LiveData<Integer> getConnectionState() {
        return mConnectionState;
    }

    /**
     * Returns a {@link LiveData} containing playback state.
     */
    @NonNull
    public LiveData<Integer> getPlaybackState() {
        return mPlaybackState;
    }

    /**
     * Returns a {@link LiveData} containing currently tuned program info.
     */
    @NonNull
    public LiveData<ProgramInfo> getCurrentProgram() {
        return mCurrentProgram;
    }

    /**
     * Returns a {@link LiveData} containing programs list found by the background tuner.
     *
     * @return Program list container, or {@code null} if program list is not supported
     */
    @NonNull
    public LiveData<List<ProgramInfo>> getProgramList() {
        return mProgramList;
    }

    /**
     * Tunes to a given program.
     */
    public void tune(@NonNull ProgramSelector sel) {
        tune(sel, null);
    }

    /**
     * Tunes to a given program with a callback.
     */
    public void tune(@NonNull ProgramSelector sel, @Nullable TuneCallback result) {
        callService(service -> service.tune(sel, new TuneCallbackAdapter(result)));
    }

    /**
     * Seeks forward/backwards.
     */
    public void seek(boolean forward) {
        seek(forward, null);
    }

    /**
     * Seeks forward/backwards with a callback.
     */
    public void seek(boolean forward, @Nullable TuneCallback result) {
        callService(service -> service.seek(forward, new TuneCallbackAdapter(result)));
    }

    /**
     * Steps forward/backwards
     */
    public void step(boolean forward) {
        step(forward, null);
    }

    /**
     * Steps forward/backwards with a callback.
     */
    public void step(boolean forward, @Nullable TuneCallback result) {
        callService(service -> service.step(forward, new TuneCallbackAdapter(result)));
    }

    /**
     * Mutes or resumes audio.
     *
     * @param muted {@code true} to mute, {@code false} to resume audio.
     */
    public void setMuted(boolean muted) {
        callService(service -> service.setMuted(muted));
    }

    /**
     * Tune to a default channel of a given program type (band).
     *
     * Usually, this means tuning to the recently listened program of a given band.
     *
     * @param band Program type to switch to
     */
    public void switchBand(@NonNull ProgramType band) {
        callService(service -> service.switchBand(Objects.requireNonNull(band)));
    }

    /**
     * States whether program list is supported on current device or not.
     *
     * @return {@code true} if the program list is supported, {@code false} otherwise.
     */
    public boolean isProgramListSupported() {
        return queryService(service -> service.isProgramListSupported(), false);
    }

    /**
     * Returns current region config (like frequency ranges for AM/FM).
     */
    @NonNull
    public RegionConfig getRegionConfig() {
        return Objects.requireNonNull(queryService(service -> service.getRegionConfig(), null));
    }
}
