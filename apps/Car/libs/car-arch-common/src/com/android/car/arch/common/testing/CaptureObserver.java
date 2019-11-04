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

package com.android.car.arch.common.testing;


import android.annotation.Nullable;

import androidx.annotation.RestrictTo;
import androidx.lifecycle.Observer;


/**
 * An Observer that retains its most recently observed value
 *
 * @param <T> the type to be observed
 */
@RestrictTo(RestrictTo.Scope.TESTS)
public class CaptureObserver<T> implements Observer<T> {

    private boolean mNotified = false;
    private T mValue;

    /**
     * Returns {@code true} iff {@link #onChanged(T)} has been called with any value (including
     * {@code
     * null}).
     */
    public boolean hasBeenNotified() {
        return mNotified;
    }

    /**
     * Returns the most recently observed value (may be {@code null}). Returns {@code null} if no
     * value has been observed.
     *
     * @see #hasBeenNotified()
     */
    @Nullable
    public T getObservedValue() {
        return mValue;
    }

    @Override
    public void onChanged(@Nullable T t) {
        mNotified = true;
        mValue = t;
    }

    /**
     * Resets this CaptureObserver to its inital state. {@link #hasBeenNotified()} will return
     * {@code
     * false} and {@link #getObservedValue()} will return {@code null}.
     */
    public void reset() {
        mValue = null;
        mNotified = false;
    }
}
