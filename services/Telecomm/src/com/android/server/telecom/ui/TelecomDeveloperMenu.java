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
 * limitations under the License
 */

package com.android.server.telecom.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Switch;

import com.android.server.telecom.R;
import com.android.server.telecom.SystemSettingsUtil;

/**
 * Telecom Developer Settings Menu.
 */
public class TelecomDeveloperMenu extends Activity {

    private Switch mEnhancedCallingSwitch;
    private SystemSettingsUtil mSystemSettingsUtil;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSystemSettingsUtil = new SystemSettingsUtil();
        setContentView(R.layout.telecom_developer_menu);

        mEnhancedCallingSwitch = findViewById(R.id.switchEnhancedCallBlocking);
        mEnhancedCallingSwitch.setOnClickListener(l -> {
            handleEnhancedCallingToggle();
        });
        loadPreferences();
    }

    private void handleEnhancedCallingToggle() {
        mSystemSettingsUtil.setEnhancedCallBlockingEnabled(this,
                mEnhancedCallingSwitch.isChecked());
    }

    private void loadPreferences() {
        mEnhancedCallingSwitch.setChecked(mSystemSettingsUtil.isEnhancedCallBlockingEnabled(this));
    }
}