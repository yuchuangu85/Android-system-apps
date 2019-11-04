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
package com.android.customization.model.theme.custom;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.customization.model.CustomizationManager;
import com.android.customization.model.theme.ThemeBundle.PreviewInfo;
import com.android.customization.model.theme.ThemeManager;
import com.android.customization.model.theme.custom.CustomTheme.Builder;

import java.util.Map;

public class CustomThemeManager implements CustomizationManager<ThemeComponentOption> {

    private final CustomTheme mOriginalTheme;
    private final CustomTheme.Builder mBuilder;

    private CustomThemeManager(Map<String, String> overlayPackages,
            @Nullable CustomTheme originalTheme) {
        mBuilder = new Builder();
        overlayPackages.forEach(mBuilder::addOverlayPackage);
        mOriginalTheme = originalTheme;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void apply(ThemeComponentOption option, @Nullable Callback callback) {
        option.buildStep(mBuilder);
        if (callback != null) {
            callback.onSuccess();
        }
    }

    public Map<String, String> getOverlayPackages() {
        return mBuilder.getPackages();
    }

    public CustomTheme buildPartialCustomTheme(Context context, String id, String title) {
        return ((CustomTheme.Builder)mBuilder.setId(id).setTitle(title)).build(context);
    }

    @Override
    public void fetchOptions(OptionsFetchedListener<ThemeComponentOption> callback, boolean reload) {
        //Unused
    }

    public CustomTheme getOriginalTheme() {
        return mOriginalTheme;
    }

    public PreviewInfo buildCustomThemePreviewInfo(Context context) {
        return mBuilder.createPreviewInfo(context);
    }

    public static CustomThemeManager create(
            @Nullable CustomTheme customTheme, ThemeManager themeManager) {
        if (customTheme != null && customTheme.isDefined()) {
            return new CustomThemeManager(customTheme.getPackagesByCategory(), customTheme);
        }
        // Seed the first custom theme with the currently applied theme.
        return new CustomThemeManager(themeManager.getCurrentOverlays(), customTheme);
    }

}
