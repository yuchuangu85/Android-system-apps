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
package com.android.customization.module;

import android.content.Context;

import androidx.fragment.app.FragmentActivity;

import com.android.customization.model.theme.OverlayManagerCompat;
import com.android.customization.model.theme.ThemeBundleProvider;
import com.android.customization.model.theme.ThemeManager;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.WallpaperSetter;

public interface CustomizationInjector extends Injector {

    CustomizationPreferences getCustomizationPreferences(Context context);

    ThemeManager getThemeManager(ThemeBundleProvider provider, FragmentActivity activity,
            WallpaperSetter wallpaperSetter, OverlayManagerCompat overlayManagerCompat,
            ThemesUserEventLogger logger);
}
