/*
 * Copyright (C) 2019 The Android Open Source Project.
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

package com.android.car.themeplayground;

import android.app.UiModeManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;

/**
 * Activity that shows different widgets on configuration changes.
 */
public class WidgetsSamples extends AbstractSampleActivity {

    private UiModeManager mUiModeManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.onActivityCreateSetTheme(this);
        setContentView(R.layout.widget_samples);
        Button triggerConfigChanges = findViewById(R.id.trigger_config_change);
        mUiModeManager = (UiModeManager) this.getSystemService(Context.UI_MODE_SERVICE);
        setupBackgroundColorControls(R.id.widgetLayout);
        triggerConfigChanges.setOnClickListener(v -> {
            if (mUiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES) {
                mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_NO);
            } else {
                mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_YES);
            }
        });
    }
}
