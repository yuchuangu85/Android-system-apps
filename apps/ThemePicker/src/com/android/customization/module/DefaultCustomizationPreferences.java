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

import com.android.wallpaper.module.DefaultWallpaperPreferences;

public class DefaultCustomizationPreferences extends DefaultWallpaperPreferences
        implements CustomizationPreferences {

    public DefaultCustomizationPreferences(Context context) {
        super(context);
    }


    @Override
    public String getSerializedCustomThemes() {
        return mSharedPrefs.getString(KEY_CUSTOM_THEME, null);
    }

    @Override
    public void storeCustomThemes(String serializedCustomThemes) {
        mSharedPrefs.edit().putString(KEY_CUSTOM_THEME, serializedCustomThemes).apply();
    }

    @Override
    public boolean getTabVisited(String id) {
        return mSharedPrefs.getBoolean(KEY_VISITED_PREFIX + id, false);
    }

    @Override
    public void setTabVisited(String id) {
        mSharedPrefs.edit().putBoolean(KEY_VISITED_PREFIX + id, true).apply();
    }
}
