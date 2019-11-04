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

package com.android.pump.app;

import android.os.Handler;
import android.os.StrictMode;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.android.pump.BuildConfig;
import com.android.pump.util.Clog;

@MainThread
public class PumpApplication extends GlobalsApplication implements Thread.UncaughtExceptionHandler {
    private static final String TAG = Clog.tag(PumpApplication.class);

    private final Thread.UncaughtExceptionHandler mSystemUncaughtExceptionHandler =
            Thread.getDefaultUncaughtExceptionHandler();

    public PumpApplication() {
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyFlashScreen()
                    .penaltyDialog()
                    .penaltyDeath()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }
    }

    @Override
    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        if (getMainLooper().getThread() != t) {
            Clog.e(TAG, "Uncaught exception in background thread " + t, e);
            new Handler(getMainLooper()).post(() ->
                    mSystemUncaughtExceptionHandler.uncaughtException(t, e));
        } else {
            mSystemUncaughtExceptionHandler.uncaughtException(t, e);
        }
    }
}
