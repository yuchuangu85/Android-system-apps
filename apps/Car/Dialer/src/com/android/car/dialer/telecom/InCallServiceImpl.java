/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.car.dialer.telecom;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;

import com.android.car.dialer.log.L;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An implementation of {@link InCallService}. This service is bounded by android telecom and
 * {@link UiCallManager}. For incoming calls it will launch Dialer app.
 */
public class InCallServiceImpl extends InCallService {
    private static final String TAG = "CD.InCallService";

    /** An action which indicates a bind is from local component. */
    public static final String ACTION_LOCAL_BIND = "local_bind";

    private CopyOnWriteArrayList<Callback> mCallbacks = new CopyOnWriteArrayList<>();

    private InCallRouter mInCallRouter;

    /** Listens to active call list changes. Callbacks will be called on main thread. */
    public interface ActiveCallListChangedCallback {

        /**
         * Called when a new call is added.
         *
         * @return if the given call has been handled by this callback.
         */
        boolean onTelecomCallAdded(Call telecomCall);

        /**
         * Called when an existing call is removed.
         *
         * @return if the given call has been handled by this callback.
         */
        boolean onTelecomCallRemoved(Call telecomCall);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mInCallRouter = new InCallRouter(getApplicationContext());
        mInCallRouter.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mInCallRouter.stop();
        mInCallRouter = null;
    }

    @Override
    public void onCallAdded(Call telecomCall) {
        L.d(TAG, "onCallAdded: %s", telecomCall);

        for (Callback callback : mCallbacks) {
            callback.onTelecomCallAdded(telecomCall);
        }

        mInCallRouter.onCallAdded(telecomCall);
    }

    @Override
    public void onCallRemoved(Call telecomCall) {
        L.d(TAG, "onCallRemoved: %s", telecomCall);
        for (Callback callback : mCallbacks) {
            callback.onTelecomCallRemoved(telecomCall);
        }

        mInCallRouter.onCallRemoved(telecomCall);
    }

    @Override
    public IBinder onBind(Intent intent) {
        L.d(TAG, "onBind: %s", intent);
        return ACTION_LOCAL_BIND.equals(intent.getAction())
                ? new LocalBinder()
                : super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        L.d(TAG, "onUnbind, intent: %s", intent);
        if (ACTION_LOCAL_BIND.equals(intent.getAction())) {
            return false;
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        for (Callback callback : mCallbacks) {
            callback.onCallAudioStateChanged(audioState);
        }
    }

    public void registerCallback(Callback callback) {
        mCallbacks.add(callback);
    }

    public void unregisterCallback(Callback callback) {
        mCallbacks.remove(callback);
    }

    public void addActiveCallListChangedCallback(ActiveCallListChangedCallback callback) {
        mInCallRouter.registerActiveCallListChangedCallback(callback);
    }

    public void removeActiveCallListChangedCallback(ActiveCallListChangedCallback callback) {
        mInCallRouter.unregisterActiveCallHandler(callback);
    }

    @Deprecated
    interface Callback {
        void onTelecomCallAdded(Call telecomCall);

        void onTelecomCallRemoved(Call telecomCall);

        void onCallAudioStateChanged(CallAudioState audioState);
    }

    /**
     * Local binder only available for Car Dialer package.
     */
    public class LocalBinder extends Binder {

        /**
         * Returns a reference to {@link InCallServiceImpl}. Any process other than Dialer
         * process won't be able to get a reference.
         */
        public InCallServiceImpl getService() {
            if (getCallingPid() == Process.myPid()) {
                return InCallServiceImpl.this;
            }
            return null;
        }
    }
}
