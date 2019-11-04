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

package com.android.car.settings.common;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.preference.Preference;

import java.util.HashSet;
import java.util.Set;

/**
 * Concrete {@link PreferenceController} with methods for verifying behavior in tests.
 */
public class FakePreferenceController extends PreferenceController<Preference> {

    @AvailabilityStatus
    private int mAvailabilityStatus;
    private int mCheckInitializedCallCount;
    private int mOnCreateInternalCallCount;
    private int mOnStartInternalCallCount;
    private int mOnResumeInternalCallCount;
    private int mOnPauseInternalCallCount;
    private int mOnStopInternalCallCount;
    private int mOnDestroyInternalCallCount;
    private int mUpdateStateCallCount;
    private Preference mUpdateStateArg;
    private int mHandlePreferenceChangedCallCount;
    private Preference mHandlePreferenceChangedPreferenceArg;
    private Object mHandlePreferenceChangedValueArg;
    private int mHandlePreferenceClickedCallCount;
    private Preference mHandlePreferenceClickedArg;
    private boolean mAllIgnoresUxRestrictions = false;
    private Set<String> mPreferencesIgnoringUxRestrictions = new HashSet<>();

    public FakePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mAvailabilityStatus = super.getAvailabilityStatus();
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void checkInitialized() {
        mCheckInitializedCallCount++;
    }

    int getCheckInitializedCallCount() {
        return mCheckInitializedCallCount;
    }

    @Override
    @AvailabilityStatus
    protected int getAvailabilityStatus() {
        return mAvailabilityStatus;
    }

    void setAvailabilityStatus(@AvailabilityStatus int availabilityStatus) {
        mAvailabilityStatus = availabilityStatus;
    }

    @Override
    protected void onCreateInternal() {
        mOnCreateInternalCallCount++;
    }

    int getOnCreateInternalCallCount() {
        return mOnCreateInternalCallCount;
    }

    @Override
    protected void onStartInternal() {
        mOnStartInternalCallCount++;
    }

    int getOnStartInternalCallCount() {
        return mOnStartInternalCallCount;
    }

    @Override
    protected void onResumeInternal() {
        mOnResumeInternalCallCount++;
    }

    int getOnResumeInternalCallCount() {
        return mOnResumeInternalCallCount;
    }

    @Override
    protected void onPauseInternal() {
        mOnPauseInternalCallCount++;
    }

    int getOnPauseInternalCallCount() {
        return mOnPauseInternalCallCount;
    }

    @Override
    protected void onStopInternal() {
        mOnStopInternalCallCount++;
    }

    int getOnStopInternalCallCount() {
        return mOnStopInternalCallCount;
    }

    @Override
    protected void onDestroyInternal() {
        mOnDestroyInternalCallCount++;
    }

    int getOnDestroyInternalCallCount() {
        return mOnDestroyInternalCallCount;
    }

    @Override
    protected void updateState(Preference preference) {
        mUpdateStateArg = preference;
        mUpdateStateCallCount++;
    }

    Preference getUpdateStateArg() {
        return mUpdateStateArg;
    }

    int getUpdateStateCallCount() {
        return mUpdateStateCallCount;
    }

    @Override
    protected boolean handlePreferenceChanged(Preference preference, Object newValue) {
        mHandlePreferenceChangedCallCount++;
        mHandlePreferenceChangedPreferenceArg = preference;
        mHandlePreferenceChangedValueArg = newValue;
        return super.handlePreferenceChanged(preference, newValue);
    }

    int getHandlePreferenceChangedCallCount() {
        return mHandlePreferenceChangedCallCount;
    }

    Preference getHandlePreferenceChangedPreferenceArg() {
        return mHandlePreferenceChangedPreferenceArg;
    }

    Object getHandlePreferenceChangedValueArg() {
        return mHandlePreferenceChangedValueArg;
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        mHandlePreferenceClickedCallCount++;
        mHandlePreferenceClickedArg = preference;
        return super.handlePreferenceClicked(preference);
    }

    int getHandlePreferenceClickedCallCount() {
        return mHandlePreferenceClickedCallCount;
    }

    Preference getHandlePreferenceClickedArg() {
        return mHandlePreferenceClickedArg;
    }

    @Override
    protected boolean isUxRestrictionsIgnored(boolean allIgnores, Set preferencesThatIgnore) {
        return super.isUxRestrictionsIgnored(mAllIgnoresUxRestrictions,
                mPreferencesIgnoringUxRestrictions);
    }

    protected void setUxRestrictionsIgnoredConfig(boolean allIgnore, Set preferencesThatIgnore) {
        mAllIgnoresUxRestrictions = allIgnore;
        mPreferencesIgnoringUxRestrictions = preferencesThatIgnore;
    }

}
