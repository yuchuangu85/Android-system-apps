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

import static com.android.customization.model.ResourceConstants.ANDROID_PACKAGE;
import static com.android.customization.model.ResourceConstants.CONFIG_BODY_FONT_FAMILY;
import static com.android.customization.model.ResourceConstants.CONFIG_HEADLINE_FONT_FAMILY;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_FONT;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Typeface;
import android.util.Log;

import com.android.customization.model.ResourceConstants;
import com.android.customization.model.theme.OverlayManagerCompat;
import com.android.customization.model.theme.custom.ThemeComponentOption.FontOption;
import com.android.wallpaper.R;

/**
 * Implementation of {@link ThemeComponentOptionProvider} that reads {@link FontOption}s from
 * font overlays.
 */
public class FontOptionsProvider extends ThemeComponentOptionProvider<FontOption> {

    private static final String TAG = "FontOptionsProvider";

    public FontOptionsProvider(Context context, OverlayManagerCompat manager) {
        super(context, manager, OVERLAY_CATEGORY_FONT);
    }

    @Override
    protected void loadOptions() {
        addDefault();
        for (String overlayPackage : mOverlayPackages) {
            try {
                Resources overlayRes = getOverlayResources(overlayPackage);
                Typeface headlineFont = Typeface.create(
                        getFontFamily(overlayPackage, overlayRes, CONFIG_HEADLINE_FONT_FAMILY),
                        Typeface.NORMAL);
                Typeface bodyFont = Typeface.create(
                        getFontFamily(overlayPackage, overlayRes, CONFIG_BODY_FONT_FAMILY),
                        Typeface.NORMAL);
                PackageManager pm = mContext.getPackageManager();
                String label = pm.getApplicationInfo(overlayPackage, 0).loadLabel(pm).toString();
                mOptions.add(new FontOption(overlayPackage, label, headlineFont, bodyFont));
            } catch (NameNotFoundException | NotFoundException e) {
                Log.w(TAG, String.format("Couldn't load font overlay %s, will skip it",
                        overlayPackage), e);
            }
        }
    }

    private void addDefault() {
        Resources system = Resources.getSystem();
        Typeface headlineFont = Typeface.create(system.getString(system.getIdentifier(
                ResourceConstants.CONFIG_HEADLINE_FONT_FAMILY,"string", ANDROID_PACKAGE)),
                Typeface.NORMAL);
        Typeface bodyFont = Typeface.create(system.getString(system.getIdentifier(
                ResourceConstants.CONFIG_BODY_FONT_FAMILY,
                "string", ANDROID_PACKAGE)),
                Typeface.NORMAL);
        mOptions.add(new FontOption(null, mContext.getString(R.string.default_theme_title),
                headlineFont, bodyFont));
    }

    private String getFontFamily(String overlayPackage, Resources overlayRes, String configName) {
        return overlayRes.getString(overlayRes.getIdentifier(configName, "string", overlayPackage));
    }
}
