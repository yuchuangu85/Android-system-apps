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
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SHAPE;
import static com.android.customization.model.ResourceConstants.PATH_SIZE;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.PathShape;
import android.icu.text.SimpleDateFormat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.PathParser;

import com.android.customization.model.CustomizationManager;
import com.android.customization.model.CustomizationOption;
import com.android.customization.widget.DynamicAdaptiveIconDrawable;
import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.BitmapCachingAsset;
import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.model.WallpaperInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Represents a Theme component available in the system as a "persona" bundle.
 * Note that in this context a Theme is not related to Android's Styles, but it's rather an
 * abstraction representing a series of overlays to be applied to the system.
 */
public class ThemeBundle implements CustomizationOption<ThemeBundle> {

    private static final String TAG = "ThemeBundle";
    private final static String EMPTY_JSON = "{}";
    private final static String TIMESTAMP_FIELD = "_applied_timestamp";

    private final String mTitle;
    private final PreviewInfo mPreviewInfo;
    private final boolean mIsDefault;
    protected final Map<String, String> mPackagesByCategory;
    @Nullable private final WallpaperInfo mWallpaperInfo;
    @Nullable private final String mWallpaperOptions;
    private WallpaperInfo mOverrideWallpaper;
    private Asset mOverrideWallpaperAsset;
    private CharSequence mContentDescription;

    protected ThemeBundle(String title, Map<String, String> overlayPackages,
            boolean isDefault, @Nullable WallpaperInfo wallpaperInfo,
            @Nullable String wallpaperOptions, PreviewInfo previewInfo) {
        mTitle = title;
        mIsDefault = isDefault;
        mPreviewInfo = previewInfo;
        mWallpaperInfo = wallpaperInfo;
        mWallpaperOptions = wallpaperOptions;
        mPackagesByCategory = Collections.unmodifiableMap(overlayPackages);
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public void bindThumbnailTile(View view) {
        Resources res = view.getContext().getResources();

        ((TextView) view.findViewById(R.id.theme_option_font)).setTypeface(
                mPreviewInfo.headlineFontFamily);
        if (mPreviewInfo.shapeDrawable != null) {
            ((ShapeDrawable) mPreviewInfo.shapeDrawable).getPaint().setColor(
                    mPreviewInfo.resolveAccentColor(res));
            ((ImageView) view.findViewById(R.id.theme_option_shape)).setImageDrawable(
                    mPreviewInfo.shapeDrawable);
        }
        if (!mPreviewInfo.icons.isEmpty()) {
            Drawable icon = mPreviewInfo.icons.get(0).getConstantState().newDrawable().mutate();
            icon.setTint(res.getColor(R.color.icon_thumbnail_color, null));
            ((ImageView) view.findViewById(R.id.theme_option_icon)).setImageDrawable(
                    icon);
        }
        view.setContentDescription(getContentDescription(view.getContext()));
    }

    @Override
    public boolean isActive(CustomizationManager<ThemeBundle> manager) {
        ThemeManager themeManager = (ThemeManager) manager;

        if (mIsDefault) {
            String serializedOverlays = themeManager.getStoredOverlays();
            return TextUtils.isEmpty(serializedOverlays) || EMPTY_JSON.equals(serializedOverlays);
        } else {
            Map<String, String> currentOverlays = themeManager.getCurrentOverlays();
            return mPackagesByCategory.equals(currentOverlays);
        }
    }

    @Override
    public int getLayoutResId() {
        return R.layout.theme_option;
    }

    /**
     * This is similar to #equals() but it only compares this theme's packages with the other, that
     * is, it will return true if applying this theme has the same effect of applying the given one.
     */
    public boolean isEquivalent(ThemeBundle other) {
        if (other == null) {
            return false;
        }
        if (mIsDefault) {
            return other.isDefault() || TextUtils.isEmpty(other.getSerializedPackages())
                    || EMPTY_JSON.equals(other.getSerializedPackages());
        }
        // Map#equals ensures keys and values are compared.
        return mPackagesByCategory.equals(other.mPackagesByCategory);
    }

    public PreviewInfo getPreviewInfo() {
        return mPreviewInfo;
    }

    public void setOverrideThemeWallpaper(WallpaperInfo homeWallpaper) {
        mOverrideWallpaper = homeWallpaper;
        mOverrideWallpaperAsset = null;
    }

    public boolean shouldUseThemeWallpaper() {
        return mOverrideWallpaper == null && mWallpaperInfo != null;
    }

    public Asset getWallpaperPreviewAsset(Context context) {
        return mOverrideWallpaper != null ?
                getOverrideWallpaperAsset(context) :
                getPreviewInfo().wallpaperAsset;
    }

    private Asset getOverrideWallpaperAsset(Context context) {
        if (mOverrideWallpaperAsset == null) {
            mOverrideWallpaperAsset = new BitmapCachingAsset(context,
                    mOverrideWallpaper.getThumbAsset(context));
        }
        return mOverrideWallpaperAsset;
    }

    public WallpaperInfo getWallpaperInfo() {
        return mWallpaperInfo;
    }

    @Nullable
    public String getWallpaperOptions() {
        return mWallpaperOptions;
    }

    boolean isDefault() {
        return mIsDefault;
    }

    public Map<String, String> getPackagesByCategory() {
        return mPackagesByCategory;
    }

    public String getSerializedPackages() {
        return getJsonPackages(false).toString();
    }

    public String getSerializedPackagesWithTimestamp() {
        return getJsonPackages(true).toString();
    }

    JSONObject getJsonPackages(boolean insertTimestamp) {
        if (isDefault()) {
            return new JSONObject();
        }
        JSONObject json = new JSONObject(mPackagesByCategory);
        // Remove items with null values to avoid deserialization issues.
        removeNullValues(json);
        if (insertTimestamp) {
            try {
                json.put(TIMESTAMP_FIELD, System.currentTimeMillis());
            } catch (JSONException e) {
                Log.e(TAG, "Couldn't add timestamp to serialized themebundle");
            }
        }
        return json;
    }

    private void removeNullValues(JSONObject json) {
        Iterator<String> keys = json.keys();
        Set<String> keysToRemove = new HashSet<>();
        while(keys.hasNext()) {
            String key = keys.next();
            if (json.isNull(key)) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            json.remove(key);
        }
    }

    protected CharSequence getContentDescription(Context context) {
        if (mContentDescription == null) {
            CharSequence defaultName = context.getString(R.string.default_theme_title);
            if (isDefault()) {
                mContentDescription = defaultName;
            } else {
                PackageManager pm = context.getPackageManager();
                CharSequence fontName = getOverlayName(pm, OVERLAY_CATEGORY_FONT);
                CharSequence iconName = getOverlayName(pm, OVERLAY_CATEGORY_ICON_ANDROID);
                CharSequence shapeName = getOverlayName(pm, OVERLAY_CATEGORY_SHAPE);
                CharSequence colorName = getOverlayName(pm, OVERLAY_CATEGORY_COLOR);
                mContentDescription = context.getString(R.string.theme_description,
                        TextUtils.isEmpty(fontName) ? defaultName : fontName,
                        TextUtils.isEmpty(iconName) ? defaultName : iconName,
                        TextUtils.isEmpty(shapeName) ? defaultName : shapeName,
                        TextUtils.isEmpty(colorName) ? defaultName : colorName);
            }
        }
        return mContentDescription;
    }

    private CharSequence getOverlayName(PackageManager pm, String overlayCategoryFont) {
        try {
            return pm.getApplicationInfo(
                    mPackagesByCategory.get(overlayCategoryFont), 0).loadLabel(pm);
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    public static class PreviewInfo {
        public final Typeface bodyFontFamily;
        public final Typeface headlineFontFamily;
        @ColorInt public final int colorAccentLight;
        @ColorInt public final int colorAccentDark;
        public final List<Drawable> icons;
        public final Drawable shapeDrawable;
        @Nullable public final Asset wallpaperAsset;
        public final List<Drawable> shapeAppIcons;
        @Dimension public final int bottomSheeetCornerRadius;

        private PreviewInfo(Context context, Typeface bodyFontFamily, Typeface headlineFontFamily,
                int colorAccentLight, int colorAccentDark, List<Drawable> icons,
                Drawable shapeDrawable, @Dimension int cornerRadius,
                @Nullable Asset wallpaperAsset, List<Drawable> shapeAppIcons) {
            this.bodyFontFamily = bodyFontFamily;
            this.headlineFontFamily = headlineFontFamily;
            this.colorAccentLight = colorAccentLight;
            this.colorAccentDark = colorAccentDark;
            this.icons = icons;
            this.shapeDrawable = shapeDrawable;
            this.bottomSheeetCornerRadius = cornerRadius;
            this.wallpaperAsset = wallpaperAsset == null
                    ? null : new BitmapCachingAsset(context, wallpaperAsset);
            this.shapeAppIcons = shapeAppIcons;
        }

        /**
         * Returns the accent color to be applied corresponding with the current configuration's
         * UI mode.
         * @return one of {@link #colorAccentDark} or {@link #colorAccentLight}
         */
        @ColorInt
        public int resolveAccentColor(Resources res) {
            return (res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES ? colorAccentDark : colorAccentLight;
        }
    }

    public static class Builder {
        protected String mTitle;
        private Typeface mBodyFontFamily;
        private Typeface mHeadlineFontFamily;
        @ColorInt private int mColorAccentLight = -1;
        @ColorInt private int mColorAccentDark = -1;
        private List<Drawable> mIcons = new ArrayList<>();
        private String mPathString;
        private Path mShapePath;
        private boolean mIsDefault;
        @Dimension private int mCornerRadius;
        private Asset mWallpaperAsset;
        private WallpaperInfo mWallpaperInfo;
        private String mWallpaperOptions;
        protected Map<String, String> mPackages = new HashMap<>();
        private List<Drawable> mAppIcons = new ArrayList<>();

        public ThemeBundle build(Context context) {
            return new ThemeBundle(mTitle, mPackages, mIsDefault, mWallpaperInfo, mWallpaperOptions,
                    createPreviewInfo(context));
        }

        public PreviewInfo createPreviewInfo(Context context) {
            ShapeDrawable shapeDrawable = null;
            List<Drawable> shapeIcons = new ArrayList<>();
            Path path = mShapePath;
            if (!TextUtils.isEmpty(mPathString)) {
                path = PathParser.createPathFromPathData(mPathString);
            }
            if (path != null) {
                PathShape shape = new PathShape(path, PATH_SIZE, PATH_SIZE);
                shapeDrawable = new ShapeDrawable(shape);
                shapeDrawable.setIntrinsicHeight((int) PATH_SIZE);
                shapeDrawable.setIntrinsicWidth((int) PATH_SIZE);
                for (Drawable icon : mAppIcons) {
                    if (icon instanceof AdaptiveIconDrawable) {
                        AdaptiveIconDrawable adaptiveIcon = (AdaptiveIconDrawable) icon;
                        shapeIcons.add(new DynamicAdaptiveIconDrawable(adaptiveIcon.getBackground(),
                                adaptiveIcon.getForeground(), path));
                    } else if (icon instanceof DynamicAdaptiveIconDrawable) {
                        shapeIcons.add(icon);
                    }
                    // TODO: add iconloader library's legacy treatment helper methods for
                    //  non-adaptive icons
                }
            }
            return new PreviewInfo(context, mBodyFontFamily, mHeadlineFontFamily, mColorAccentLight,
                    mColorAccentDark, mIcons, shapeDrawable, mCornerRadius,
                    mWallpaperAsset, shapeIcons);
        }

        public Map<String, String> getPackages() {
            return Collections.unmodifiableMap(mPackages);
        }

        public String getTitle() {
            return mTitle;
        }

        public Builder setTitle(String title) {
            mTitle = title;
            return this;
        }

        public Builder setBodyFontFamily(@Nullable Typeface bodyFontFamily) {
            mBodyFontFamily = bodyFontFamily;
            return this;
        }

        public Builder setHeadlineFontFamily(@Nullable Typeface headlineFontFamily) {
            mHeadlineFontFamily = headlineFontFamily;
            return this;
        }

        public Builder setColorAccentLight(@ColorInt int colorAccentLight) {
            mColorAccentLight = colorAccentLight;
            return this;
        }

        public Builder setColorAccentDark(@ColorInt int colorAccentDark) {
            mColorAccentDark = colorAccentDark;
            return this;
        }

        public Builder addIcon(Drawable icon) {
            mIcons.add(icon);
            return this;
        }

        public Builder addOverlayPackage(String category, String packageName) {
            mPackages.put(category, packageName);
            return this;
        }

        public Builder setShapePath(String path) {
            mPathString = path;
            return this;
        }

        public Builder setShapePath(Path path) {
            mShapePath = path;
            return this;
        }

        public Builder setWallpaperInfo(String wallpaperPackageName, String wallpaperResName,
                String themeId, @DrawableRes int wallpaperResId, @StringRes int titleResId,
                @StringRes int attributionResId, @StringRes int actionUrlResId) {
            mWallpaperInfo = new ThemeBundledWallpaperInfo(wallpaperPackageName, wallpaperResName,
                    themeId, wallpaperResId, titleResId, attributionResId, actionUrlResId);
            return this;
        }

        public Builder setLiveWallpaperInfo(LiveWallpaperInfo info) {
            mWallpaperInfo = info;
            return this;
        }


        public Builder setWallpaperAsset(Asset wallpaperAsset) {
            mWallpaperAsset = wallpaperAsset;
            return this;
        }

        public Builder setWallpaperOptions(String wallpaperOptions) {
            mWallpaperOptions = wallpaperOptions;
            return this;
        }

        public Builder asDefault() {
            mIsDefault = true;
            return this;
        }

        public Builder addShapePreviewIcon(Drawable appIcon) {
            mAppIcons.add(appIcon);
            return this;
        }

        public Builder setBottomSheetCornerRadius(@Dimension int radius) {
            mCornerRadius = radius;
            return this;
        }
    }
}
