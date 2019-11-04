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

/**
 * Interface for a LiveData that emits the value of a given source. The LiveData can change sources
 * at any time, and the value emitted will represent only the most recent source. Older sources will
 * be forgotten.
 *
 * @param <T> The type this SwitchingLiveData will emit.
 */
public interface SwitchingLiveData<T> {

    /**
     * Returns this SwitchingLiveData as a LiveData. This method is needed due to limitations in
     * Java syntax.
     */
    @NonNull
    LiveData<T> asLiveData();

    /**
     * Returns the current source as set by {@link #setSource(LiveData)}
     */
    @Nullable
    LiveData<? extends T> getSource();

    /**
     * Sets which LiveData acts as the source for this SwitchingLiveData. If {@code null}, this
     * SwitchingLiveData will emit {@code null}.
     */
    void setSource(@Nullable LiveData<? extends T> source);

    /** Returns a new instance of SwitchingLiveData */
    static <T> SwitchingLiveData<T> newInstance() {
        return new SwitchingLiveDataImpl<>();
    }
}
