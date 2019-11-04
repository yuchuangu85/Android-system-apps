/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.car;

import android.content.Context;
import android.content.res.Resources;

import com.android.car.audio.CarAudioService;

import java.io.PrintWriter;

public class SystemStateControllerService implements CarServiceBase {
    private final CarAudioService mCarAudioService;
    private final ICarImpl mICarImpl;
    private final boolean mLockWhenMuting;

    public SystemStateControllerService(
            Context context, CarAudioService carAudioService, ICarImpl carImpl) {
        mCarAudioService = carAudioService;
        mICarImpl = carImpl;
        Resources res = context.getResources();
        mLockWhenMuting = res.getBoolean(R.bool.displayOffMuteLockAllAudio);
    }

    @Override
    public void init() {
    }

    @Override
    public void release() {
    }

    @Override
    public void dump(PrintWriter writer) {
    }
}
