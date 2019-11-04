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
package com.android.car.messenger;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

/**
 * No-op Activity that only exists in order to have an entry in the manifest with SMS specific
 * intent-filter.
 * <p>
 * We need the manifest entry so that PackageManager will grant this pre-installed app SMS related
 * permissions. See DefaultPermissionGrantPolicy.grantDefaultSystemHandlerPermissions().
 */
public class MessengerActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        finish();
    }

}
