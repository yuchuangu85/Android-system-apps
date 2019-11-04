/*
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

package com.android.car.settings.common;

import android.content.Intent;

import androidx.annotation.Nullable;

/**
 * Handles activity results after a {@link FragmentController} fires {@link
 * FragmentController#startActivityForResult(Intent, int, ActivityResultCallback)}
 * or {@link FragmentController#startIntentSenderForResult}.
 */
public interface ActivityResultCallback {

    /**
     * Callback used when an activity started by
     * {@link FragmentController#startActivityForResult(Intent,
     * int, ActivityResultCallback)} or {@link FragmentController#startIntentSenderForResult}
     * receives a result.
     */
    void processActivityResult(int requestCode, int resultCode, @Nullable Intent data);
}
