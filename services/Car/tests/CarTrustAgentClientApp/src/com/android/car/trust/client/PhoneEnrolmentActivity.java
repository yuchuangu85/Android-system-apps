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
package com.android.car.trust.client;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

/**
 * Activity to allow the user to add an escrow token to a remote device. <p/>
 *
 * For this to work properly, the correct permissions must be set in the system config.  In AOSP,
 * this config is in frameworks/base/core/res/res/values/config.xml <p/>
 *
 * The config must set config_allowEscrowTokenForTrustAgent to true.  For the desired car
 * experience, the config should also set config_strongAuthRequiredOnBoot to false.
 */
public class PhoneEnrolmentActivity extends FragmentActivity {

    private static final int FINE_LOCATION_REQUEST_CODE = 42;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.phone_enrolment_activity);

        PhoneEnrolmentController enrolmentController = new PhoneEnrolmentController(this);
        enrolmentController.bind(findViewById(R.id.output), findViewById(R.id.enroll_scan),
                findViewById(R.id.enroll_button));

        PhoneUnlockController unlockController = new PhoneUnlockController(this);
        unlockController.bind(findViewById(R.id.output), findViewById(R.id.unlock_scan),
                findViewById(R.id.unlock_button));
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] { android.Manifest.permission.ACCESS_FINE_LOCATION },
                    FINE_LOCATION_REQUEST_CODE);
        }
    }
}
