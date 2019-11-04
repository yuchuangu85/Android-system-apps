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

package com.android.car.dialer.livedata;

import android.bluetooth.BluetoothHeadsetClient;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.lifecycle.LiveData;

import com.android.car.dialer.log.L;
import com.android.car.dialer.telecom.UiCallManager;

/**
 * Provides the current connecting audio route.
 */
public class AudioRouteLiveData extends LiveData<Integer> {
    private static final String TAG = "CD.AudioRouteLiveData";

    private final Context mContext;
    private final IntentFilter mAudioRouteChangeFilter;

    private final BroadcastReceiver mAudioRouteChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateAudioRoute();
        }
    };

    public AudioRouteLiveData(Context context) {
        mContext = context;
        mAudioRouteChangeFilter =
                new IntentFilter(BluetoothHeadsetClient.ACTION_AUDIO_STATE_CHANGED);
    }

    @Override
    protected void onActive() {
        updateAudioRoute();
        mContext.registerReceiver(mAudioRouteChangeReceiver, mAudioRouteChangeFilter);
    }

    @Override
    protected void onInactive() {
        mContext.unregisterReceiver(mAudioRouteChangeReceiver);
    }

    private void updateAudioRoute() {
        int audioRoute = UiCallManager.get().getAudioRoute();
        if (getValue() == null || audioRoute != getValue()) {
            L.d(TAG, "updateAudioRoute to %s", audioRoute);
            setValue(audioRoute);
        }
    }
}
