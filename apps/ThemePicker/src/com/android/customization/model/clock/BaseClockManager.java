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
package com.android.customization.model.clock;

import com.android.customization.model.CustomizationManager;

/**
 * {@link CustomizationManager} for clock faces.
 */
public abstract class BaseClockManager implements CustomizationManager<Clockface> {

    private final ClockProvider mClockProvider;

    public BaseClockManager(ClockProvider provider) {
        mClockProvider = provider;
    }

    @Override
    public boolean isAvailable() {
        return mClockProvider.isAvailable();
    }

    @Override
    public void apply(Clockface option, Callback callback) {
        handleApply(option, callback);
    }

    @Override
    public void fetchOptions(OptionsFetchedListener<Clockface> callback, boolean reload) {
        mClockProvider.fetch(callback, false);
    }

    /** Returns the ID of the current clock face, which may be null for the default clock face. */
    String getCurrentClock() {
        return lookUpCurrentClock();
    }

    /**
     * Implement to apply the clock picked by the user for {@link BaseClockManager#apply}.
     *
     * @param option Clock option, containing ID of the clock, that the user picked.
     * @param callback Report success and failure.
     */
    protected abstract void handleApply(Clockface option, Callback callback);

    /**
     * Implement to look up the current clock face for {@link BaseClockManager#getCurrentClock()}.
     *
     * @return ID of current clock. Can be null for the default clock face.
     */
    protected abstract String lookUpCurrentClock();
}
