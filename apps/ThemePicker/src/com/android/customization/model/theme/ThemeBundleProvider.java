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
package com.android.customization.model.theme;

import androidx.annotation.Nullable;

import com.android.customization.model.CustomizationManager.OptionsFetchedListener;
import com.android.customization.model.theme.custom.CustomTheme;

import org.json.JSONException;

/**
 * Interface for a class that can retrieve Themes from the system.
 */
public interface ThemeBundleProvider {

    /**
     * Returns whether themes are available in the current setup.
     */
    boolean isAvailable();

    /**
     * Retrieve the available themes.
     * @param callback called when the themes have been retrieved (or immediately if cached)
     * @param reload whether to reload themes if they're cached.
     */
    void fetch(OptionsFetchedListener<ThemeBundle> callback, boolean reload);

    void storeCustomTheme(CustomTheme theme);

    void removeCustomTheme(CustomTheme theme);

    @Nullable CustomTheme.Builder parseCustomTheme(String serializedTheme) throws JSONException;

    ThemeBundle findEquivalent(ThemeBundle other);
}
