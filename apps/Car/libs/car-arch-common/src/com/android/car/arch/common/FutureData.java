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

package com.android.car.arch.common;

/**
 * Class that holds data with a loading state.
 *
 * @param <T> the output data type
 */
public class FutureData<T> {

    private final boolean mIsLoading;
    private final T mData;

    public FutureData(boolean isLoading, T data) {
        mIsLoading = isLoading;
        mData = data;
    }

    /**
     * Gets if the data is in the process of being loaded
     */
    public boolean isLoading() {
        return mIsLoading;
    }

    /**
     * Gets the data, if done loading. If currently loading, returns null
     */
    public T getData() {
        return mData;
    }
}
