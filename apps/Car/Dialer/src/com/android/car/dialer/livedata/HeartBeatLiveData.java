/*
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

package com.android.car.dialer.livedata;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

/**
 * Emits a true value in a fixed periodical pace. The first beat begins when this live data becomes
 * active.
 *
 * <p> Note that if this heart beat is shared, the time can be less than the given interval between
 * observation and first beat for the second observer.
 */
public class HeartBeatLiveData extends LiveData<Boolean> {
    private long mPulseRate;
    private Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    public HeartBeatLiveData(long rateInMillis) {
        mPulseRate = rateInMillis;
    }

    @Override
    protected void onActive() {
        super.onActive();
        mMainThreadHandler.post(mUpdateDurationRunnable);
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        mMainThreadHandler.removeCallbacks(mUpdateDurationRunnable);
    }

    private final Runnable mUpdateDurationRunnable = new Runnable() {
        @Override
        public void run() {
            setValue(true);
            mMainThreadHandler.postDelayed(this, mPulseRate);
        }
    };
}
