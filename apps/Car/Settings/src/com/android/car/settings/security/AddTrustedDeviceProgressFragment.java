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
package com.android.car.settings.security;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;

/**
 * Add trusted device fragment which displays the progress and show the companion app information
 * to user.
 */
public class AddTrustedDeviceProgressFragment extends SettingsFragment {
    private ProgressBar mProgressBar;

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.add_trusted_device_progress_fragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mProgressBar = requireActivity().findViewById(R.id.progress_bar);
        mProgressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onStart() {
        super.onStart();
        mProgressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onStop() {
        super.onStop();
        mProgressBar.setVisibility(View.GONE);
    }
}

