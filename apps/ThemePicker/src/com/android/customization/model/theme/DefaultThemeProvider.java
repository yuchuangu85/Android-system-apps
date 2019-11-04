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

import static android.content.res.Resources.ID_NULL;

import static com.android.customization.model.ResourceConstants.ANDROID_PACKAGE;
import static com.android.customization.model.ResourceConstants.ICONS_FOR_PREVIEW;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_COLOR;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_FONT;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_ANDROID;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_LAUNCHER;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_SETTINGS;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_SYSUI;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_THEMEPICKER;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SHAPE;
import static com.android.customization.model.ResourceConstants.SYSUI_PACKAGE;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources.NotFoundException;
import android.service.wallpaper.WallpaperService;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.customization.model.CustomizationManager.OptionsFetchedListener;
import com.android.customization.model.ResourcesApkProvider;
import com.android.customization.model.theme.ThemeBundle.Builder;
import com.android.customization.model.theme.custom.CustomTheme;
import com.android.customization.module.CustomizationPreferences;
import com.android.wallpaper.R;
import com.android.wallpaper.asset.ResourceAsset;
import com.android.wallpaper.model.LiveWallpaperInfo;

import com.bumptech.glide.request.RequestOptions;
import com.google.android.apps.wallpaper.asset.ThemeBundleThumbAsset;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link ThemeBundleProvider} that reads Themes' overlays from a stub APK.
 */
public class DefaultThemeProvider extends ResourcesApkProvider implements ThemeBundleProvider {

    private static final String TAG = "DefaultThemeProvider";

    private static final String THEMES_ARRAY = "themes";
    private static final String TITLE_PREFIX = "theme_title_";
    private static final String FONT_PREFIX = "theme_overlay_font_";
    private static final String COLOR_PREFIX = "theme_overlay_color_";
    private static final String SHAPE_PREFIX = "theme_overlay_shape_";
    private static final String ICON_ANDROID_PREFIX = "theme_overlay_icon_android_";
    private static final String ICON_LAUNCHER_PREFIX = "theme_overlay_icon_launcher_";
    private static final String ICON_THEMEPICKER_PREFIX = "theme_overlay_icon_themepicker_";
    private static final String ICON_SETTINGS_PREFIX = "theme_overlay_icon_settings_";
    private static final String ICON_SYSUI_PREFIX = "theme_overlay_icon_sysui_";
    private static final String WALLPAPER_PREFIX = "theme_wallpaper_";
    private static final String WALLPAPER_TITLE_PREFIX = "theme_wallpaper_title_";
    private static final String WALLPAPER_ATTRIBUTION_PREFIX = "theme_wallpaper_attribution_";
    private static final String WALLPAPER_THUMB_PREFIX = "theme_wallpaper_thumbnail_";
    private static final String WALLPAPER_ACTION_PREFIX = "theme_wallpaper_action_";
    private static final String WALLPAPER_OPTIONS_PREFIX = "theme_wallpaper_options_";

    private static final String DEFAULT_THEME_NAME= "default";
    private static final String THEME_TITLE_FIELD = "_theme_title";
    private static final String THEME_ID_FIELD = "_theme_id";

    // Maximum number of themes allowed (including default, pre-bundled and custom)
    private static final int MAX_TOTAL_THEMES = 10;

    private final OverlayThemeExtractor mOverlayProvider;
    private List<ThemeBundle> mThemes;
    private final CustomizationPreferences mCustomizationPreferences;

    public DefaultThemeProvider(Context context, CustomizationPreferences customizationPrefs) {
        super(context, context.getString(R.string.themes_stub_package));
        mOverlayProvider = new OverlayThemeExtractor(context);
        mCustomizationPreferences = customizationPrefs;
    }

    @Override
    public void fetch(OptionsFetchedListener<ThemeBundle> callback, boolean reload) {
        if (mThemes == null || reload) {
            mThemes = new ArrayList<>();
            loadAll();
        }

        if(callback != null) {
            callback.onOptionsLoaded(mThemes);
        }
    }

    @Override
    public boolean isAvailable() {
        return mOverlayProvider.isAvailable() && super.isAvailable();
    }

    private void loadAll() {
        addDefaultTheme();

        String[] themeNames = getItemsFromStub(THEMES_ARRAY);

        for (String themeName : themeNames) {
            // Default theme needs special treatment (see #addDefaultTheme())
            if (DEFAULT_THEME_NAME.equals(themeName)) {
                continue;
            }
            ThemeBundle.Builder builder = new Builder();
            try {
                builder.setTitle(mStubApkResources.getString(
                        mStubApkResources.getIdentifier(TITLE_PREFIX + themeName,
                                "string", mStubPackageName)));

                String shapeOverlayPackage = getOverlayPackage(SHAPE_PREFIX, themeName);
                mOverlayProvider.addShapeOverlay(builder, shapeOverlayPackage);

                String fontOverlayPackage = getOverlayPackage(FONT_PREFIX, themeName);
                mOverlayProvider.addFontOverlay(builder, fontOverlayPackage);

                String colorOverlayPackage = getOverlayPackage(COLOR_PREFIX, themeName);
                mOverlayProvider.addColorOverlay(builder, colorOverlayPackage);

                String iconAndroidOverlayPackage = getOverlayPackage(ICON_ANDROID_PREFIX,
                        themeName);

                mOverlayProvider.addAndroidIconOverlay(builder, iconAndroidOverlayPackage);

                String iconSysUiOverlayPackage = getOverlayPackage(ICON_SYSUI_PREFIX, themeName);

                mOverlayProvider.addSysUiIconOverlay(builder, iconSysUiOverlayPackage);

                String iconLauncherOverlayPackage = getOverlayPackage(ICON_LAUNCHER_PREFIX,
                        themeName);
                mOverlayProvider.addNoPreviewIconOverlay(builder, iconLauncherOverlayPackage);

                String iconThemePickerOverlayPackage = getOverlayPackage(ICON_THEMEPICKER_PREFIX,
                        themeName);
                mOverlayProvider.addNoPreviewIconOverlay(builder,
                        iconThemePickerOverlayPackage);

                String iconSettingsOverlayPackage = getOverlayPackage(ICON_SETTINGS_PREFIX,
                        themeName);

                mOverlayProvider.addNoPreviewIconOverlay(builder, iconSettingsOverlayPackage);

                addWallpaper(themeName, builder);

                mThemes.add(builder.build(mContext));
            } catch (NameNotFoundException | NotFoundException e) {
                Log.w(TAG, String.format("Couldn't load part of theme %s, will skip it", themeName),
                        e);
            }
        }

        addCustomThemes();
    }

    private void addWallpaper(String themeName, Builder builder) {
        try {
            String wallpaperResName = WALLPAPER_PREFIX + themeName;
            int wallpaperResId = mStubApkResources.getIdentifier(wallpaperResName,
                    "drawable", mStubPackageName);
            // Check in case the theme has a separate thumbnail for the wallpaper
            String wallpaperThumbnailResName = WALLPAPER_THUMB_PREFIX + themeName;
            int wallpaperThumbnailResId = mStubApkResources.getIdentifier(wallpaperThumbnailResName,
                    "drawable", mStubPackageName);
            if (wallpaperResId != ID_NULL) {
                builder.setWallpaperInfo(mStubPackageName, wallpaperResName,
                        themeName, wallpaperResId,
                        mStubApkResources.getIdentifier(WALLPAPER_TITLE_PREFIX + themeName,
                                "string", mStubPackageName),
                        mStubApkResources.getIdentifier(
                                WALLPAPER_ATTRIBUTION_PREFIX + themeName, "string",
                                mStubPackageName),
                        mStubApkResources.getIdentifier(WALLPAPER_ACTION_PREFIX + themeName,
                                "string", mStubPackageName))
                        .setWallpaperAsset(wallpaperThumbnailResId != ID_NULL ?
                                getThumbAsset(WALLPAPER_THUMB_PREFIX, themeName)
                                : getDrawableResourceAsset(WALLPAPER_PREFIX, themeName));
            } else {
                // Try to see if it's a live wallpaper reference
                wallpaperResId = mStubApkResources.getIdentifier(wallpaperResName,
                        "string", mStubPackageName);
                if (wallpaperResId != ID_NULL) {
                    String wpComponent = mStubApkResources.getString(wallpaperResId);

                    int wallpaperOptionsResId = mStubApkResources.getIdentifier(
                            WALLPAPER_OPTIONS_PREFIX + themeName, "string", mStubPackageName);
                    String wallpaperOptions = wallpaperOptionsResId != ID_NULL
                            ? mStubApkResources.getString(wallpaperOptionsResId) : null;

                    String[] componentParts = wpComponent.split("/");
                    Intent liveWpIntent =  new Intent(WallpaperService.SERVICE_INTERFACE);
                    liveWpIntent.setComponent(
                            new ComponentName(componentParts[0], componentParts[1]));

                    Context appContext = mContext.getApplicationContext();
                    PackageManager pm = appContext.getPackageManager();
                    ResolveInfo resolveInfo =
                            pm.resolveService(liveWpIntent, PackageManager.GET_META_DATA);
                    if (resolveInfo != null) {
                        android.app.WallpaperInfo wallpaperInfo;
                        try {
                            wallpaperInfo = new android.app.WallpaperInfo(appContext, resolveInfo);
                            LiveWallpaperInfo liveInfo = new LiveWallpaperInfo(wallpaperInfo);
                            builder.setLiveWallpaperInfo(liveInfo).setWallpaperAsset(
                                    wallpaperThumbnailResId != ID_NULL ?
                                            getThumbAsset(WALLPAPER_THUMB_PREFIX, themeName)
                                        : liveInfo.getThumbAsset(mContext))
                                    .setWallpaperOptions(wallpaperOptions);
                        } catch (XmlPullParserException | IOException e) {
                            Log.w(TAG, "Skipping wallpaper " + resolveInfo.serviceInfo, e);
                        }
                    }
                }
            }
        } catch (NotFoundException e) {
            // Nothing to do here, if there's no wallpaper we'll just omit wallpaper
        }
    }

    /**
     * Default theme requires different treatment: if there are overlay packages specified in the
     * stub apk, we'll use those, otherwise we'll get the System default values. But we cannot skip
     * the default theme.
     */
    private void addDefaultTheme() {
        ThemeBundle.Builder builder = new Builder().asDefault();

        int titleId = mStubApkResources.getIdentifier(TITLE_PREFIX + DEFAULT_THEME_NAME,
                "string", mStubPackageName);
        if (titleId > 0) {
            builder.setTitle(mStubApkResources.getString(titleId));
        } else {
            builder.setTitle(mContext.getString(R.string.default_theme_title));
        }

        String colorOverlayPackage = getOverlayPackage(COLOR_PREFIX, DEFAULT_THEME_NAME);

        try {
            mOverlayProvider.addColorOverlay(builder, colorOverlayPackage);
        } catch (NameNotFoundException | NotFoundException e) {
            Log.d(TAG, "Didn't find color overlay for default theme, will use system default");
            mOverlayProvider.addSystemDefaultColor(builder);
        }

        String fontOverlayPackage = getOverlayPackage(FONT_PREFIX, DEFAULT_THEME_NAME);

        try {
            mOverlayProvider.addFontOverlay(builder, fontOverlayPackage);
        } catch (NameNotFoundException | NotFoundException e) {
            Log.d(TAG, "Didn't find font overlay for default theme, will use system default");
            mOverlayProvider.addSystemDefaultFont(builder);
        }

        try {
            String shapeOverlayPackage = getOverlayPackage(SHAPE_PREFIX, DEFAULT_THEME_NAME);
            mOverlayProvider.addShapeOverlay(builder ,shapeOverlayPackage, false);
        } catch (NameNotFoundException | NotFoundException e) {
            Log.d(TAG, "Didn't find shape overlay for default theme, will use system default");
            mOverlayProvider.addSystemDefaultShape(builder);
        }
        for (String packageName : mOverlayProvider.getShapePreviewIconPackages()) {
            try {
                builder.addShapePreviewIcon(
                        mContext.getPackageManager().getApplicationIcon(packageName));
            } catch (NameNotFoundException e) {
                Log.d(TAG, "Couldn't find app " + packageName + ", won't use it for icon shape"
                        + "preview");
            }
        }

        try {
            String iconAndroidOverlayPackage = getOverlayPackage(ICON_ANDROID_PREFIX,
                    DEFAULT_THEME_NAME);
            mOverlayProvider.addAndroidIconOverlay(builder, iconAndroidOverlayPackage);
        } catch (NameNotFoundException | NotFoundException e) {
            Log.d(TAG, "Didn't find Android icons overlay for default theme, using system default");
            mOverlayProvider.addSystemDefaultIcons(builder, ANDROID_PACKAGE, ICONS_FOR_PREVIEW);
        }

        try {
            String iconSysUiOverlayPackage = getOverlayPackage(ICON_SYSUI_PREFIX,
                    DEFAULT_THEME_NAME);
            mOverlayProvider.addSysUiIconOverlay(builder, iconSysUiOverlayPackage);
        } catch (NameNotFoundException | NotFoundException e) {
            Log.d(TAG,
                    "Didn't find SystemUi icons overlay for default theme, using system default");
            mOverlayProvider.addSystemDefaultIcons(builder, SYSUI_PACKAGE, ICONS_FOR_PREVIEW);
        }

        addWallpaper(DEFAULT_THEME_NAME, builder);

        mThemes.add(builder.build(mContext));
    }

    @Override
    public void storeCustomTheme(CustomTheme theme) {
        if (mThemes == null) {
            fetch(options -> {
                addCustomThemeAndStore(theme);
            }, false);
        } else {
            addCustomThemeAndStore(theme);
        }
    }

    private void addCustomThemeAndStore(CustomTheme theme) {
        if (!mThemes.contains(theme)) {
            mThemes.add(theme);
        } else {
            mThemes.replaceAll(t -> theme.equals(t) ? theme : t);
        }
        JSONArray themesArray = new JSONArray();
        mThemes.stream()
                .filter(themeBundle -> themeBundle instanceof CustomTheme
                        && !themeBundle.getPackagesByCategory().isEmpty())
                .forEachOrdered(themeBundle -> addThemeBundleToArray(themesArray, themeBundle));
        mCustomizationPreferences.storeCustomThemes(themesArray.toString());
    }

    private void addThemeBundleToArray(JSONArray themesArray, ThemeBundle themeBundle) {
        JSONObject jsonPackages = themeBundle.getJsonPackages(false);
        try {
            jsonPackages.put(THEME_TITLE_FIELD, themeBundle.getTitle());
            if (themeBundle instanceof CustomTheme) {
                jsonPackages.put(THEME_ID_FIELD, ((CustomTheme)themeBundle).getId());
            }
        } catch (JSONException e) {
            Log.w("Exception saving theme's title", e);
        }
        themesArray.put(jsonPackages);
    }

    @Override
    public void removeCustomTheme(CustomTheme theme) {
        JSONArray themesArray = new JSONArray();
        mThemes.stream()
                .filter(themeBundle -> themeBundle instanceof CustomTheme
                        && ((CustomTheme) themeBundle).isDefined())
                .forEachOrdered(customTheme -> {
                    if (!customTheme.equals(theme)) {
                        addThemeBundleToArray(themesArray, customTheme);
                    }
                });
        mCustomizationPreferences.storeCustomThemes(themesArray.toString());
    }

    private void addCustomThemes() {
        String serializedThemes = mCustomizationPreferences.getSerializedCustomThemes();
        int customThemesCount = 0;
        if (!TextUtils.isEmpty(serializedThemes)) {
            try {
                JSONArray customThemes = new JSONArray(serializedThemes);
                for (int i = 0; i < customThemes.length(); i++) {
                    JSONObject jsonTheme = customThemes.getJSONObject(i);
                    ThemeBundle.Builder builder = convertJsonToBuilder(jsonTheme);
                    if (builder != null) {
                        if (TextUtils.isEmpty(builder.getTitle())) {
                            builder.setTitle(mContext.getString(R.string.custom_theme_title,
                                    customThemesCount + 1));
                        }
                        mThemes.add(builder.build(mContext));
                    } else {
                        Log.w(TAG, "Couldn't read stored custom theme, resetting");
                        mThemes.add(new CustomTheme(CustomTheme.newId(),
                                mContext.getString(R.string.custom_theme_title,
                                customThemesCount + 1), new HashMap<>(), null));
                    }
                    customThemesCount++;
                }
            } catch (JSONException e) {
                Log.w(TAG, "Couldn't read stored custom theme, resetting", e);
                mThemes.add(new CustomTheme(CustomTheme.newId(), mContext.getString(
                        R.string.custom_theme_title, customThemesCount + 1),
                        new HashMap<>(), null));
            }
        }

        if (mThemes.size() < MAX_TOTAL_THEMES) {
            // Add an empty one at the end.
            mThemes.add(new CustomTheme(CustomTheme.newId(), mContext.getString(
                    R.string.custom_theme_title, customThemesCount + 1), new HashMap<>(), null));
        }

    }

    @Override
    public CustomTheme.Builder parseCustomTheme(String serializedTheme) throws JSONException {
        JSONObject theme = new JSONObject(serializedTheme);
        return convertJsonToBuilder(theme);
    }

    @Nullable
    private CustomTheme.Builder convertJsonToBuilder(JSONObject theme) throws JSONException {
        try {
            Map<String, String> customPackages = new HashMap<>();
            Iterator<String> keysIterator = theme.keys();

            while (keysIterator.hasNext()) {
                String category = keysIterator.next();
                customPackages.put(category, theme.getString(category));
            }
            CustomTheme.Builder builder = new CustomTheme.Builder();
            mOverlayProvider.addShapeOverlay(builder,
                    customPackages.get(OVERLAY_CATEGORY_SHAPE));
            mOverlayProvider.addFontOverlay(builder,
                    customPackages.get(OVERLAY_CATEGORY_FONT));
            mOverlayProvider.addColorOverlay(builder,
                    customPackages.get(OVERLAY_CATEGORY_COLOR));
            mOverlayProvider.addAndroidIconOverlay(builder,
                    customPackages.get(OVERLAY_CATEGORY_ICON_ANDROID));
            mOverlayProvider.addSysUiIconOverlay(builder,
                    customPackages.get(OVERLAY_CATEGORY_ICON_SYSUI));
            mOverlayProvider.addNoPreviewIconOverlay(builder,
                    customPackages.get(OVERLAY_CATEGORY_ICON_SETTINGS));
            mOverlayProvider.addNoPreviewIconOverlay(builder,
                    customPackages.get(OVERLAY_CATEGORY_ICON_LAUNCHER));
            mOverlayProvider.addNoPreviewIconOverlay(builder,
                    customPackages.get(OVERLAY_CATEGORY_ICON_THEMEPICKER));
            if (theme.has(THEME_TITLE_FIELD)) {
                builder.setTitle(theme.getString(THEME_TITLE_FIELD));
            }
            if (theme.has(THEME_ID_FIELD)) {
                builder.setId(theme.getString(THEME_ID_FIELD));
            }
            return builder;
        } catch (NameNotFoundException | NotFoundException e) {
            Log.i(TAG, "Couldn't parse serialized custom theme", e);
            return null;
        }
    }


    @Override
    public ThemeBundle findEquivalent(ThemeBundle other) {
        if (mThemes == null) {
            return null;
        }
        for (ThemeBundle theme : mThemes) {
            if (theme.isEquivalent(other)) {
                return theme;
            }
        }
        return null;
    }

    private String getOverlayPackage(String prefix, String themeName) {
        return getItemStringFromStub(prefix, themeName);
    }

    private ResourceAsset getDrawableResourceAsset(String prefix, String themeName) {
        int drawableResId = mStubApkResources.getIdentifier(prefix + themeName,
                "drawable", mStubPackageName);
        return drawableResId == 0 ? null : new ResourceAsset(mStubApkResources, drawableResId,
                RequestOptions.fitCenterTransform());
    }

    private ThemeBundleThumbAsset getThumbAsset(String prefix, String themeName) {
        int drawableResId = mStubApkResources.getIdentifier(prefix + themeName,
                "drawable", mStubPackageName);
        return drawableResId == 0 ? null : new ThemeBundleThumbAsset(mStubApkResources,
                drawableResId);
    }
}
