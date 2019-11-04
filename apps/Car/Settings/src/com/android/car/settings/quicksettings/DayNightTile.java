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

package com.android.car.settings.quicksettings;

import android.annotation.DrawableRes;
import android.annotation.Nullable;
import android.app.UiModeManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.display.DisplaySettingsFragment;

/**
 * Toggles auto or night mode tile on quick setting page.
 */
public class DayNightTile implements QuickSettingGridAdapter.Tile {
    private final Context mContext;
    private final StateChangedListener mStateChangedListener;
    private final UiModeManager mUiModeManager;
    private final View.OnLongClickListener mLaunchDisplaySettings;

    @DrawableRes
    private int mIconRes = R.drawable.ic_settings_night_display;

    private final String mText;

    private State mState = State.ON;

    DayNightTile(
            Context context,
            StateChangedListener stateChangedListener,
            FragmentController fragmentController) {
        mStateChangedListener = stateChangedListener;
        mContext = context;
        mUiModeManager = (UiModeManager) mContext.getSystemService(Context.UI_MODE_SERVICE);
        if (mUiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES) {
            mState = State.ON;
        } else {
            mState = State.OFF;
        }
        mText = mContext.getString(R.string.night_mode_tile_label);
        mLaunchDisplaySettings = v -> {
            fragmentController.launchFragment(new DisplaySettingsFragment());
            return true;
        };
    }

    @Nullable
    public View.OnLongClickListener getOnLongClickListener() {
        return mLaunchDisplaySettings;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Drawable getIcon() {
        return mContext.getDrawable(mIconRes);
    }

    @Override
    @Nullable
    public String getText() {
        return mText;
    }

    @Override
    public State getState() {
        return mState;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void onClick(View v) {
        if (mUiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES) {
            mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_AUTO);
        } else {
            mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_YES);
        }
    }
}
