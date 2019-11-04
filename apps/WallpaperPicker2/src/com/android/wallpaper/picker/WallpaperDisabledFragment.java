/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.picker;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.wallpaper.R;
import com.android.wallpaper.module.FormFactorChecker;
import com.android.wallpaper.module.FormFactorChecker.FormFactor;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.WallpaperPreferences;

import java.util.Date;

import androidx.annotation.IntDef;
import androidx.fragment.app.Fragment;

/**
 * Displays the UI indicating that setting wallpaper is disabled.
 */
public class WallpaperDisabledFragment extends Fragment {
    public static final int SUPPORTED_CAN_SET = 0;
    public static final int NOT_SUPPORTED_BLOCKED_BY_ADMIN = 1;
    public static final int NOT_SUPPORTED_BY_DEVICE = 2;
    private static final String ARG_WALLPAPER_SUPPORT_LEVEL = "wallpaper_support_level";

    public static WallpaperDisabledFragment newInstance(
            @WallpaperSupportLevel int wallpaperSupportLevel) {
        Bundle args = new Bundle();
        args.putInt(ARG_WALLPAPER_SUPPORT_LEVEL, wallpaperSupportLevel);

        WallpaperDisabledFragment fragment = new WallpaperDisabledFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        FormFactorChecker formFactorChecker =
                InjectorProvider.getInjector().getFormFactorChecker(getActivity());
        @FormFactor int formFactor = formFactorChecker.getFormFactor();

        View view;
        if (formFactor == FormFactorChecker.FORM_FACTOR_DESKTOP) {
            view = inflater.inflate(R.layout.fragment_disabled_by_admin_desktop, container, false);
        } else {  // FORM_FACTOR_MOBILE
            view = inflater.inflate(R.layout.fragment_disabled_by_admin, container, false);
        }

        @WallpaperSupportLevel int wallpaperSupportLevel = getArguments().getInt(
                ARG_WALLPAPER_SUPPORT_LEVEL);
        TextView messageView = (TextView) view.findViewById(R.id.wallpaper_disabled_message);
        if (wallpaperSupportLevel == NOT_SUPPORTED_BLOCKED_BY_ADMIN) {
            messageView.setText(R.string.wallpaper_disabled_by_administrator_message);
        } else if (wallpaperSupportLevel == NOT_SUPPORTED_BY_DEVICE) {
            messageView.setText(R.string.wallpaper_disabled_message);
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        WallpaperPreferences preferences = InjectorProvider.getInjector().getPreferences(getActivity());
        preferences.setLastAppActiveTimestamp(new Date().getTime());
    }

    /**
     * Whether or not setting wallpapers is supported on the current device and profile.
     */
    @IntDef({
            SUPPORTED_CAN_SET,
            NOT_SUPPORTED_BLOCKED_BY_ADMIN,
            NOT_SUPPORTED_BY_DEVICE
    })
    public @interface WallpaperSupportLevel {
    }
}
