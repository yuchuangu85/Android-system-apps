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

package com.android.car.radio.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.radio.bands.ProgramType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A base class for widgets switching program types/bands (AM/FM/DAB etc).
 */
public abstract class BandSelector extends LinearLayout {
    private final Object mLock = new Object();

    @Nullable private Callback mCallback;

    @NonNull private List<ProgramType> mSupportedBands = new ArrayList<>();
    @Nullable private ProgramType mCurrentBand;

    /**
     * Widget's onClick event translated to band callback.
     */
    public interface Callback {
        /**
         * Called when user uses this button to switch the band.
         *
         * @param pt ProgramType to switch to
         */
        void onSwitchTo(@NonNull ProgramType pt);
    }

    public BandSelector(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Sets band selection callback.
     */
    public void setCallback(@Nullable Callback callback) {
        synchronized (mLock) {
            mCallback = callback;
        }
    }

    /**
     * Sets supported program types.
     */
    public void setSupportedProgramTypes(@NonNull List<ProgramType> supported) {
        synchronized (mLock) {
            mSupportedBands = Objects.requireNonNull(supported);
        }
    }

    protected void switchToNext() {
        synchronized (mLock) {
            switchTo(mSupportedBands.get(
                    (mSupportedBands.indexOf(mCurrentBand) + 1) % mSupportedBands.size()));
        }
    }

    protected void switchTo(@NonNull ProgramType ptype) {
        synchronized (mLock) {
            if (mCallback != null) mCallback.onSwitchTo(ptype);
        }
    }

    /**
     * Sets band button state.
     *
     * This method doesn't trigger callback.
     *
     * @param ptype Program type to set.
     */
    public void setType(@NonNull ProgramType ptype) {
        synchronized (mLock) {
            mCurrentBand = ptype;
        }
    }
}
