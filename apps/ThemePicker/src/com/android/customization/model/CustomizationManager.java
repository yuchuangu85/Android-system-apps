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
package com.android.customization.model;

import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Interface for a class that handles a "Customization" (eg, "Themes", "Clockfaces", etc)
 * @param <T> the type of {@link CustomizationOption} that this Manager class provides.
 */
public interface CustomizationManager<T extends CustomizationOption> {

    /**
     * Callback for applying a customization option.
     */
    interface Callback {
        /**
         * Called after an option was applied successfully.
         */
        void onSuccess();

        /**
         * Called if there was an error applying the customization
         * @param throwable Exception thrown if available.
         */
        void onError(@Nullable Throwable throwable);
    }

    /**
     * Listener interface for fetching CustomizationOptions
     */
    interface OptionsFetchedListener<T extends CustomizationOption> {
        /**
         * Called when the options have been retrieved.
         */
        void onOptionsLoaded(List<T> options);

        /**
         * Called if there was an error loading grid options
         */
        default void onError(@Nullable Throwable throwable) {
            if (throwable != null) {
                Log.e("OptionsFecthedListener", "Error loading options", throwable);
            }
        }
    }

    /**
     * Returns whether this customization is available in the system.
     */
    boolean isAvailable();

    /**
     * Applies the given option into the system.
     */
    void apply(T option, Callback callback);

    /**
     * Loads the available options for the type of Customization managed by this class, calling the
     * given callback when done.
     */
    void fetchOptions(OptionsFetchedListener<T> callback, boolean reload);
}
