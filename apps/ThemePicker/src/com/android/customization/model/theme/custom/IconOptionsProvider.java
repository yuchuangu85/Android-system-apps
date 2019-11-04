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
import static com.android.customization.model.ResourceConstants.ICONS_FOR_PREVIEW;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_ANDROID;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_LAUNCHER;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_SETTINGS;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_SYSUI;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_THEMEPICKER;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.util.Log;

import com.android.customization.model.ResourceConstants;
import com.android.customization.model.theme.OverlayManagerCompat;
import com.android.customization.model.theme.custom.ThemeComponentOption.IconOption;
import com.android.wallpaper.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link ThemeComponentOptionProvider} that reads {@link IconOption}s from
 * icon overlays.
 */
public class IconOptionsProvider extends ThemeComponentOptionProvider<IconOption> {

    private static final String TAG = "IconOptionsProvider";

    private final List<String> mSysUiIconsOverlayPackages = new ArrayList<>();
    private final List<String> mSettingsIconsOverlayPackages = new ArrayList<>();
    private final List<String> mLauncherIconsOverlayPackages = new ArrayList<>();
    private final List<String> mThemePickerIconsOverlayPackages = new ArrayList<>();

    public IconOptionsProvider(Context context, OverlayManagerCompat manager) {
        super(context, manager, OVERLAY_CATEGORY_ICON_ANDROID);
        String[] targetPackages = ResourceConstants.getPackagesToOverlay(context);
        mSysUiIconsOverlayPackages.addAll(manager.getOverlayPackagesForCategory(
                OVERLAY_CATEGORY_ICON_SYSUI, UserHandle.myUserId(), targetPackages));
        mSettingsIconsOverlayPackages.addAll(manager.getOverlayPackagesForCategory(
                OVERLAY_CATEGORY_ICON_SETTINGS, UserHandle.myUserId(), targetPackages));
        mLauncherIconsOverlayPackages.addAll(manager.getOverlayPackagesForCategory(
                OVERLAY_CATEGORY_ICON_LAUNCHER, UserHandle.myUserId(), targetPackages));
        mThemePickerIconsOverlayPackages.addAll(manager.getOverlayPackagesForCategory(
                OVERLAY_CATEGORY_ICON_THEMEPICKER, UserHandle.myUserId(), targetPackages));
    }

    @Override
    protected void loadOptions() {
        addDefault();

        Map<String, IconOption> optionsByPrefix = new HashMap<>();
        for (String overlayPackage : mOverlayPackages) {
            IconOption option = addOrUpdateOption(optionsByPrefix, overlayPackage,
                    OVERLAY_CATEGORY_ICON_ANDROID);
            try{
                for (String iconName : ICONS_FOR_PREVIEW) {
                    option.addIcon(loadIconPreviewDrawable(iconName, overlayPackage));
                }
            } catch (NotFoundException | NameNotFoundException e) {
                Log.w(TAG, String.format("Couldn't load icon overlay details for %s, will skip it",
                        overlayPackage), e);
            }
        }

        for (String overlayPackage : mSysUiIconsOverlayPackages) {
            addOrUpdateOption(optionsByPrefix, overlayPackage, OVERLAY_CATEGORY_ICON_SYSUI);
        }

        for (String overlayPackage : mSettingsIconsOverlayPackages) {
            addOrUpdateOption(optionsByPrefix, overlayPackage, OVERLAY_CATEGORY_ICON_SETTINGS);
        }

        for (String overlayPackage : mLauncherIconsOverlayPackages) {
            addOrUpdateOption(optionsByPrefix, overlayPackage, OVERLAY_CATEGORY_ICON_LAUNCHER);
        }

        for (String overlayPackage : mThemePickerIconsOverlayPackages) {
            addOrUpdateOption(optionsByPrefix, overlayPackage, OVERLAY_CATEGORY_ICON_THEMEPICKER);
        }

        for (IconOption option : optionsByPrefix.values()) {
            if (option.isValid(mContext)) {
                mOptions.add(option);
                option.setLabel(mContext.getString(R.string.icon_component_label, mOptions.size()));
            }
        }
    }

    private IconOption addOrUpdateOption(Map<String, IconOption> optionsByPrefix,
            String overlayPackage, String category) {
        String prefix = overlayPackage.substring(0, overlayPackage.lastIndexOf("."));
        IconOption option;
        if (!optionsByPrefix.containsKey(prefix)) {
            option = new IconOption();
            optionsByPrefix.put(prefix, option);
        } else {
            option = optionsByPrefix.get(prefix);
        }
        option.addOverlayPackage(category, overlayPackage);
        return option;
    }

    private Drawable loadIconPreviewDrawable(String drawableName, String packageName)
            throws NameNotFoundException, NotFoundException {
        final Resources resources = ANDROID_PACKAGE.equals(packageName)
                ? Resources.getSystem()
                : mContext.getPackageManager().getResourcesForApplication(packageName);
        return resources.getDrawable(
                resources.getIdentifier(drawableName, "drawable", packageName), null);
    }

    private void addDefault() {
        IconOption option = new IconOption();
        option.setLabel(mContext.getString(R.string.default_theme_title));
        try {
            for (String iconName : ICONS_FOR_PREVIEW) {
                option.addIcon(loadIconPreviewDrawable(iconName, ANDROID_PACKAGE));
            }
        } catch (NameNotFoundException | NotFoundException e) {
            Log.w(TAG, "Didn't find SystemUi package icons, will skip option", e);
        }
        option.addOverlayPackage(OVERLAY_CATEGORY_ICON_ANDROID, null);
        option.addOverlayPackage(OVERLAY_CATEGORY_ICON_SYSUI, null);
        option.addOverlayPackage(OVERLAY_CATEGORY_ICON_SETTINGS, null);
        option.addOverlayPackage(OVERLAY_CATEGORY_ICON_LAUNCHER, null);
        option.addOverlayPackage(OVERLAY_CATEGORY_ICON_THEMEPICKER, null);
        mOptions.add(option);
    }

}
