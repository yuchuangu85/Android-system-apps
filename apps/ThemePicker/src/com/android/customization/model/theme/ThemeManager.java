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

import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_COLOR;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_FONT;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_ANDROID;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_LAUNCHER;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_SETTINGS;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_SYSUI;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_THEMEPICKER;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SHAPE;
import android.graphics.Point;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.android.customization.model.CustomizationManager;
import com.android.customization.model.ResourceConstants;
import com.android.customization.model.theme.custom.CustomTheme;
import com.android.customization.module.ThemesUserEventLogger;
import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.module.WallpaperPersister.SetWallpaperCallback;
import com.android.wallpaper.module.WallpaperSetter;
import com.android.wallpaper.util.WallpaperCropUtils;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ThemeManager implements CustomizationManager<ThemeBundle> {

    private static final Set<String> THEME_CATEGORIES = new HashSet<>();
    static {
        THEME_CATEGORIES.add(OVERLAY_CATEGORY_COLOR);
        THEME_CATEGORIES.add(OVERLAY_CATEGORY_FONT);
        THEME_CATEGORIES.add(OVERLAY_CATEGORY_SHAPE);
        THEME_CATEGORIES.add(OVERLAY_CATEGORY_ICON_ANDROID);
        THEME_CATEGORIES.add(OVERLAY_CATEGORY_ICON_SETTINGS);
        THEME_CATEGORIES.add(OVERLAY_CATEGORY_ICON_SYSUI);
        THEME_CATEGORIES.add(OVERLAY_CATEGORY_ICON_LAUNCHER);
        THEME_CATEGORIES.add(OVERLAY_CATEGORY_ICON_THEMEPICKER);
    };


    private final ThemeBundleProvider mProvider;
    private final OverlayManagerCompat mOverlayManagerCompat;

    private final WallpaperSetter mWallpaperSetter;
    protected final FragmentActivity mActivity;
    private final ThemesUserEventLogger mEventLogger;

    private Map<String, String> mCurrentOverlays;

    public ThemeManager(ThemeBundleProvider provider, FragmentActivity activity,
            WallpaperSetter wallpaperSetter, OverlayManagerCompat overlayManagerCompat,
            ThemesUserEventLogger logger) {
        mProvider = provider;
        mActivity = activity;
        mOverlayManagerCompat = overlayManagerCompat;
        mWallpaperSetter = wallpaperSetter;
        mEventLogger = logger;
    }

    @Override
    public boolean isAvailable() {
        return mOverlayManagerCompat.isAvailable() && mProvider.isAvailable();
    }

    @Override
    public void apply(ThemeBundle theme, Callback callback) {
        // Set wallpaper
        if (theme.shouldUseThemeWallpaper()) {
            mWallpaperSetter.requestDestination(mActivity, mActivity.getSupportFragmentManager(),
                    R.string.set_theme_wallpaper_dialog_message,
                    destination -> applyWallpaper(
                            theme,
                            destination,
                            createSetWallpaperCallback(theme, callback)),
                    theme.getWallpaperInfo() instanceof LiveWallpaperInfo);

        } else {
            applyOverlays(theme, callback);
        }
    }

    private SetWallpaperCallback createSetWallpaperCallback(ThemeBundle theme, Callback callback) {
        return new SetWallpaperCallback() {
            @Override
            public void onSuccess() {
                applyWallpaperOptions(theme);
                applyOverlays(theme, callback);
            }

            @Override
            public void onError(@Nullable Throwable throwable) {
                callback.onError(throwable);
            }
        };
    }

    protected void applyWallpaperOptions(ThemeBundle theme) {
        //Do nothing.
    }

    private void applyWallpaper(ThemeBundle theme, int destination,
            SetWallpaperCallback callback) {
        Point defaultCropSurfaceSize = WallpaperCropUtils.getDefaultCropSurfaceSize(
                mActivity.getResources(),
                mActivity.getWindowManager().getDefaultDisplay());
        Asset wallpaperAsset = theme.getWallpaperInfo().getAsset(mActivity);
        if (wallpaperAsset != null) {
            wallpaperAsset.decodeRawDimensions(mActivity,
                    dimensions -> {
                        float scale = 1f;
                        // Calculate scale to fit the screen height
                        if (dimensions != null && dimensions.y > 0) {
                            scale = (float) defaultCropSurfaceSize.y / dimensions.y;
                        }
                        mWallpaperSetter.setCurrentWallpaper(mActivity,
                                theme.getWallpaperInfo(),
                                wallpaperAsset,
                                destination,
                                scale, null, callback);
                    });
        } else {
            mWallpaperSetter.setCurrentWallpaper(mActivity,
                    theme.getWallpaperInfo(),
                    null,
                    destination,
                    1f, null, callback);
        }
    }

    private void applyOverlays(ThemeBundle theme, Callback callback) {
        boolean allApplied = Settings.Secure.putString(mActivity.getContentResolver(),
                ResourceConstants.THEME_SETTING, theme.getSerializedPackagesWithTimestamp());
        if (theme instanceof CustomTheme) {
            storeCustomTheme((CustomTheme) theme);
        }
        mCurrentOverlays = null;
        if (allApplied) {
            mEventLogger.logThemeApplied(theme, theme instanceof CustomTheme);
            callback.onSuccess();
        } else {
            callback.onError(null);
        }
    }

    private void storeCustomTheme(CustomTheme theme) {
        mProvider.storeCustomTheme(theme);
    }

    @Override
    public void fetchOptions(OptionsFetchedListener<ThemeBundle> callback, boolean reload) {
        mProvider.fetch(callback, reload);
    }

    public Map<String, String> getCurrentOverlays() {
        if (mCurrentOverlays == null) {
            mCurrentOverlays = mOverlayManagerCompat.getEnabledOverlaysForTargets(
                    ResourceConstants.getPackagesToOverlay(mActivity));
            mCurrentOverlays.entrySet().removeIf(
                    categoryAndPackage -> !THEME_CATEGORIES.contains(categoryAndPackage.getKey()));
        }
        return mCurrentOverlays;
    }

    public String getStoredOverlays() {
        return Settings.Secure.getString(mActivity.getContentResolver(),
                ResourceConstants.THEME_SETTING);
    }

    public void removeCustomTheme(CustomTheme theme) {
        mProvider.removeCustomTheme(theme);
    }

    /**
     * @return an existing ThemeBundle that matches the same packages as the given one, if one
     * exists, or {@code null} otherwise.
     */
    @Nullable
    public ThemeBundle findThemeByPackages(ThemeBundle other) {
        return mProvider.findEquivalent(other);
    }

    /**
     * Store empty theme if no theme has been set yet. This will prevent Settings from showing the
     * suggestion to select a theme
     */
    public void storeEmptyTheme() {
        String themeSetting = Settings.Secure.getString(mActivity.getContentResolver(),
                ResourceConstants.THEME_SETTING);
        if (TextUtils.isEmpty(themeSetting)) {
            Settings.Secure.putString(mActivity.getContentResolver(),
                    ResourceConstants.THEME_SETTING, new JSONObject().toString());
        }
    }
}
