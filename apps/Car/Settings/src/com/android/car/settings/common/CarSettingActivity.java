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

package com.android.car.settings.common;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.android.car.settings.R;

/**
 * Root activity used for most of the Settings app. This activity provides additional functionality
 * which handles intents.
 */
public class CarSettingActivity extends BaseCarSettingsActivity {

    private static final Logger LOG = new Logger(CarSettingActivity.class);

    private static final String KEY_HAS_NEW_INTENT =
            "com.android.car.settings.common.CarSettingActivity.KEY_HAS_NEW_INTENT";

    private boolean mHasNewIntent = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mHasNewIntent = savedInstanceState.getBoolean(KEY_HAS_NEW_INTENT, mHasNewIntent);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_HAS_NEW_INTENT, mHasNewIntent);
    }

    @Override
    public void onNewIntent(Intent intent) {
        LOG.d("onNewIntent" + intent);
        setIntent(intent);
        mHasNewIntent = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mHasNewIntent) {
            Fragment fragment = FragmentResolver.getFragmentForIntent(/* context= */ this,
                    getIntent());
            launchIfDifferent(fragment);
            mHasNewIntent = false;
        }
    }

    @Override
    public void launchFragment(Fragment fragment) {
        // Called before super to clear the back stack if necessary before launching the fragment
        // in question.
        if (fragment.getClass().getName().equals(
                getString(R.string.config_settings_hierarchy_root_fragment))
                && getSupportFragmentManager().getBackStackEntryCount() > 1) {
            getSupportFragmentManager().popBackStackImmediate(/* name= */ null,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        super.launchFragment(fragment);
    }

    /**
     * Gets the fragment to show onCreate. This will only be launched if it is different from the
     * current fragment shown.
     */
    @Override
    @Nullable
    protected Fragment getInitialFragment() {
        if (getCurrentFragment() != null) {
            return getCurrentFragment();
        }
        return Fragment.instantiate(this,
                getString(R.string.config_settings_hierarchy_root_fragment));
    }
}
