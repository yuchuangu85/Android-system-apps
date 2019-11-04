/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.module;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;

/**
 * Interface for an object which checks whether any activity on the device can handle ACTION_VIEW
 * (or "explore") intents for given payloads.
 */
public interface ExploreIntentChecker {

    /**
     * Fetches an ACTION_VIEW Intent object for the given Uri that can be handled by some activity on
     * the device or null if the Uri can not be viewed by any activity on the device.
     */
    void fetchValidActionViewIntent(Uri uri, IntentReceiver receiver);

    /**
     * Receiver of an intent.
     */
    interface IntentReceiver {
        void onIntentReceived(@Nullable Intent intent);
    }
}
