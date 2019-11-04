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

import static com.android.customization.model.ResourceConstants.ACCENT_COLOR_DARK_NAME;
import static com.android.customization.model.ResourceConstants.ACCENT_COLOR_LIGHT_NAME;
import static com.android.customization.model.ResourceConstants.ANDROID_PACKAGE;
import static com.android.customization.model.ResourceConstants.ICONS_FOR_PREVIEW;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ANDROID_THEME;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_COLOR;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_ANDROID;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SHAPE;
import static com.android.customization.model.ResourceConstants.PATH_SIZE;
import static com.android.customization.model.ResourceConstants.SYSUI_PACKAGE;
import static com.android.customization.model.theme.custom.ThemeComponentOption.ColorOption.COLOR_TILES_ICON_IDS;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.PathShape;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.graphics.PathParser;

import com.android.customization.model.ResourceConstants;
import com.android.customization.model.theme.OverlayManagerCompat;
import com.android.customization.model.theme.custom.ThemeComponentOption.ColorOption;
import com.android.wallpaper.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link ThemeComponentOptionProvider} that reads {@link ColorOption}s from
 * icon overlays.
 */
public class ColorOptionsProvider extends ThemeComponentOptionProvider<ColorOption> {

    private static final String TAG = "ColorOptionsProvider";
    private final CustomThemeManager mCustomThemeManager;
    private final String mDefaultThemePackage;

    public ColorOptionsProvider(Context context, OverlayManagerCompat manager,
            CustomThemeManager customThemeManager) {
        super(context, manager, OVERLAY_CATEGORY_COLOR);
        mCustomThemeManager = customThemeManager;
        // System color is set with a static overlay for android.theme category, so let's try to
        // find that first, and if that's not present, we'll default to System resources.
        // (see #addDefault())
        List<String> themePackages = manager.getOverlayPackagesForCategory(
                OVERLAY_CATEGORY_ANDROID_THEME, UserHandle.myUserId(), ANDROID_PACKAGE);
        mDefaultThemePackage = themePackages.isEmpty() ? null : themePackages.get(0);
    }

    @Override
    protected void loadOptions() {
        List<Drawable> previewIcons = new ArrayList<>();
        String iconPackage =
                mCustomThemeManager.getOverlayPackages().get(OVERLAY_CATEGORY_ICON_ANDROID);
        if (TextUtils.isEmpty(iconPackage)) {
            iconPackage = ANDROID_PACKAGE;
        }
        for (String iconName : ICONS_FOR_PREVIEW) {
            try {
                previewIcons.add(loadIconPreviewDrawable(iconName, iconPackage));
            } catch (NameNotFoundException | NotFoundException e) {
                Log.w(TAG, String.format("Couldn't load icon in %s for color preview, will skip it",
                        iconPackage), e);
            }
        }
        String shapePackage = mCustomThemeManager.getOverlayPackages().get(OVERLAY_CATEGORY_SHAPE);
        if (TextUtils.isEmpty(shapePackage)) {
            shapePackage = ANDROID_PACKAGE;
        }
        Drawable shape = loadShape(shapePackage);
        addDefault(previewIcons, shape);
        for (String overlayPackage : mOverlayPackages) {
            try {
                Resources overlayRes = getOverlayResources(overlayPackage);
                int lightColor = overlayRes.getColor(
                        overlayRes.getIdentifier(ACCENT_COLOR_LIGHT_NAME, "color", overlayPackage),
                        null);
                int darkColor = overlayRes.getColor(
                        overlayRes.getIdentifier(ACCENT_COLOR_DARK_NAME, "color", overlayPackage),
                        null);
                PackageManager pm = mContext.getPackageManager();
                String label = pm.getApplicationInfo(overlayPackage, 0).loadLabel(pm).toString();
                ColorOption option = new ColorOption(overlayPackage, label, lightColor, darkColor);
                option.setPreviewIcons(previewIcons);
                option.setShapeDrawable(shape);
                mOptions.add(option);
            } catch (NameNotFoundException | NotFoundException e) {
                Log.w(TAG, String.format("Couldn't load color overlay %s, will skip it",
                        overlayPackage), e);
            }
        }
    }

    private void addDefault(List<Drawable> previewIcons, Drawable shape) {
        int lightColor, darkColor;
        Resources system = Resources.getSystem();
        try {
            Resources r = getOverlayResources(mDefaultThemePackage);
            lightColor = r.getColor(
                    r.getIdentifier(ACCENT_COLOR_LIGHT_NAME, "color", mDefaultThemePackage),
                    null);
            darkColor = r.getColor(
                    r.getIdentifier(ACCENT_COLOR_DARK_NAME, "color", mDefaultThemePackage),
                    null);
        } catch (NotFoundException | NameNotFoundException e) {
            Log.d(TAG, "Didn't find default color, will use system option", e);

            lightColor = system.getColor(
                    system.getIdentifier(ACCENT_COLOR_LIGHT_NAME, "color", ANDROID_PACKAGE), null);

            darkColor = system.getColor(
                    system.getIdentifier(ACCENT_COLOR_DARK_NAME, "color", ANDROID_PACKAGE), null);
        }
        ColorOption option = new ColorOption(null,
                mContext.getString(R.string.default_theme_title), lightColor, darkColor);
        option.setPreviewIcons(previewIcons);
        option.setShapeDrawable(shape);
        mOptions.add(option);
    }

    private Drawable loadIconPreviewDrawable(String drawableName, String packageName)
            throws NameNotFoundException, NotFoundException {

        Resources overlayRes = getOverlayResources(packageName);
        return overlayRes.getDrawable(
                overlayRes.getIdentifier(drawableName, "drawable", packageName), null);
    }

    private Drawable loadShape(String packageName) {
        String path = null;
        try {
            Resources r = getOverlayResources(packageName);

            path = ResourceConstants.getIconMask(r, packageName);
        } catch (NameNotFoundException e) {
            Log.d(TAG, String.format("Couldn't load shape icon for %s, skipping.", packageName), e);
        }
        ShapeDrawable shapeDrawable = null;
        if (!TextUtils.isEmpty(path)) {
            PathShape shape = new PathShape(PathParser.createPathFromPathData(path),
                    PATH_SIZE, PATH_SIZE);
            shapeDrawable = new ShapeDrawable(shape);
            shapeDrawable.setIntrinsicHeight((int) PATH_SIZE);
            shapeDrawable.setIntrinsicWidth((int) PATH_SIZE);
        }
        return shapeDrawable;
    }

}
