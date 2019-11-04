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

package com.android.tv.common.experiments;

import android.support.annotation.VisibleForTesting;
import com.android.tv.common.BuildConfig;

import com.google.common.base.Supplier;

/** Experiments return values based on user, device and other criteria. */
public final class ExperimentFlag<T> {

    // NOTE: sAllowOverrides IS NEVER USED in the non AOSP version.
    private static boolean sAllowOverrides = false;

    @VisibleForTesting
    public static void initForTest() {
        /* Begin_AOSP_Comment_Out
        if (!BuildConfig.AOSP) {
            PhenotypeFlag.initForTest();
            return;
        }
        End_AOSP_Comment_Out */
        sAllowOverrides = true;
    }

    /** Returns a boolean experiment */
    public static ExperimentFlag<Boolean> createFlag(
// AOSP_Comment_Out             Supplier<Boolean> phenotypeFlag,
            boolean defaultValue) {
        return new ExperimentFlag<>(
// AOSP_Comment_Out                 phenotypeFlag,
                defaultValue);
    }

    private final T mDefaultValue;
// AOSP_Comment_Out     private final Supplier<T> mPhenotypeFlag;

// AOSP_Comment_Out     // NOTE: mOverrideValue IS NEVER USED in the non AOSP version.
    private T mOverrideValue = null;
    // mOverridden IS NEVER USED in the non AOSP version.
    private boolean mOverridden = false;

    private ExperimentFlag(
// AOSP_Comment_Out             Supplier<T> phenotypeFlag,
            // NOTE: defaultValue IS NEVER USED in the non AOSP version.
            T defaultValue) {
        mDefaultValue = defaultValue;
// AOSP_Comment_Out         mPhenotypeFlag = phenotypeFlag;
    }

    /** Returns value for this experiment */
    public T get() {
        /* Begin_AOSP_Comment_Out
        if (!BuildConfig.AOSP) {
            return mPhenotypeFlag.get();
        }
        End_AOSP_Comment_Out */
        return sAllowOverrides && mOverridden ? mOverrideValue : mDefaultValue;
    }

    @VisibleForTesting
    public void override(T t) {

        if (sAllowOverrides) {
            mOverridden = true;
            mOverrideValue = t;
        }
    }

    @VisibleForTesting
    public void resetOverride() {
        mOverridden = false;
    }

    /* Begin_AOSP_Comment_Out
    @VisibleForTesting
    T getAospDefaultValueForTesting() {
        return mDefaultValue;
    }
    End_AOSP_Comment_Out */
}
