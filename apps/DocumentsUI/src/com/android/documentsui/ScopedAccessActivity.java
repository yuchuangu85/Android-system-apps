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

package com.android.documentsui;

import static com.android.documentsui.MetricConsts.SCOPED_DIRECTORY_ACCESS_DEPRECATED;
import static com.android.documentsui.ScopedAccessMetrics.logInvalidScopedAccessRequest;

import android.app.Activity;
import android.os.Bundle;
import android.os.storage.StorageVolume;

/**
 * Activity responsible for handling {@link StorageVolume#createAccessIntent(String)}.
 *
 * @deprecated This class handles the deprecated {@link StorageVolume#createAccessIntent(String)}.
 */
@Deprecated
public class ScopedAccessActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logInvalidScopedAccessRequest(SCOPED_DIRECTORY_ACCESS_DEPRECATED);
        setResult(RESULT_CANCELED);
        finish();
    }
}
