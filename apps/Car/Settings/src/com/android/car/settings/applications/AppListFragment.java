/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.settings.applications;

import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;

/**
 * Fragment base class for use in cases where a list of applications is displayed with the option to
 * show/hide system apps in the list.
 */
public abstract class AppListFragment extends SettingsFragment {

    private static final String KEY_SHOW_SYSTEM = "showSystem";

    private boolean mShowSystem;

    @Override
    @XmlRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_button;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mShowSystem = savedInstanceState.getBoolean(KEY_SHOW_SYSTEM, false);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Button toggleShowSystem = requireActivity().findViewById(R.id.action_button1);
        setButtonText(toggleShowSystem);
        toggleShowSystem.setOnClickListener(v -> {
            mShowSystem = !mShowSystem;
            onToggleShowSystemApps(mShowSystem);
            setButtonText(toggleShowSystem);
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        onToggleShowSystemApps(mShowSystem);
    }

    /** Called when a user toggles the option to show system applications in the list. */
    protected abstract void onToggleShowSystemApps(boolean showSystem);

    /** Returns {@code true} if system applications should be shown in the list. */
    protected final boolean shouldShowSystemApps() {
        return mShowSystem;
    }

    private void setButtonText(Button button) {
        // Show text to reverse the current state.
        button.setText(mShowSystem ? R.string.hide_system : R.string.show_system);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_SHOW_SYSTEM, mShowSystem);
    }
}
