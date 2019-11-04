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

package com.android.car.arch.common.switching;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

/**
 * Provides the implementation of {@link SwitchingLiveData}. This class uses an interface rather
 * than being exposed directly to ensure that its superclass {@link MediatorLiveData} is not
 * exposed. The use of MediatorLiveData is an implementation detail.
 */
class SwitchingLiveDataImpl<T> extends MediatorLiveData<T> implements SwitchingLiveData<T> {
    private LiveData<? extends T> mCurrentSource;

    @NonNull
    @Override
    public LiveData<T> asLiveData() {
        return this;
    }

    @Nullable
    @Override
    public LiveData<? extends T> getSource() {
        return mCurrentSource;
    }

    public void setSource(@Nullable LiveData<? extends T> source) {
        if (source == mCurrentSource) {
            return;
        }
        if (mCurrentSource != null) {
            removeSource(mCurrentSource);
        }
        mCurrentSource = source;
        if (source != null) {
            addSource(source, this::setValue);
        } else {
            setValue(null);
        }
    }
}
