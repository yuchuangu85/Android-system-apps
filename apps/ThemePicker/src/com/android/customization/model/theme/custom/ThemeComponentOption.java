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

import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_COLOR;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_FONT;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_ANDROID;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_LAUNCHER;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_SETTINGS;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_SYSUI;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_THEMEPICKER;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SHAPE;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;

import com.android.customization.model.CustomizationManager;
import com.android.customization.model.CustomizationOption;
import com.android.customization.model.ResourceConstants;
import com.android.customization.model.theme.custom.CustomTheme.Builder;
import com.android.wallpaper.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an option of a component of a custom Theme (for example, a possible color, or font,
 * shape, etc).
 * Extending classes correspond to each component's options and provide the structure to bind
 * preview and thumbnails.
 * // TODO (santie): refactor the logic to bind preview cards to reuse between ThemeFragment and
 * // here
 */
public abstract class ThemeComponentOption implements CustomizationOption<ThemeComponentOption> {

    protected final Map<String, String> mOverlayPackageNames = new HashMap<>();

    protected void addOverlayPackage(String category, String packageName) {
        mOverlayPackageNames.put(category, packageName);
    }

    public Map<String, String> getOverlayPackages() {
        return mOverlayPackageNames;
    }

    @Override
    public String getTitle() {
        return null;
    }

    public abstract void bindPreview(ViewGroup container);

    public Builder buildStep(Builder builder) {
        getOverlayPackages().forEach(builder::addOverlayPackage);
        return builder;
    }

    public static class FontOption extends ThemeComponentOption {

        private final String mLabel;
        private final Typeface mHeadlineFont;
        private final Typeface mBodyFont;

        public FontOption(String packageName, String label, Typeface headlineFont,
                Typeface bodyFont) {
            addOverlayPackage(OVERLAY_CATEGORY_FONT, packageName);
            mLabel = label;
            mHeadlineFont = headlineFont;
            mBodyFont = bodyFont;
        }

        @Override
        public String getTitle() {
            return null;
        }

        @Override
        public void bindThumbnailTile(View view) {
            ((TextView) view.findViewById(R.id.thumbnail_text)).setTypeface(
                    mHeadlineFont);
            view.setContentDescription(mLabel);
        }

        @Override
        public boolean isActive(CustomizationManager<ThemeComponentOption> manager) {
            CustomThemeManager customThemeManager = (CustomThemeManager) manager;
            return Objects.equals(getOverlayPackages().get(OVERLAY_CATEGORY_FONT),
                    customThemeManager.getOverlayPackages().get(OVERLAY_CATEGORY_FONT));
        }

        @Override
        public int getLayoutResId() {
            return R.layout.theme_font_option;
        }

        @Override
        public void bindPreview(ViewGroup container) {
            bindPreviewHeader(container, R.string.preview_name_font, R.drawable.ic_font);

            ViewGroup cardBody = container.findViewById(R.id.theme_preview_card_body_container);
            if (cardBody.getChildCount() == 0) {
                LayoutInflater.from(container.getContext()).inflate(
                        R.layout.preview_card_font_content,
                        cardBody, true);
            }
            TextView title = container.findViewById(R.id.font_card_title);
            title.setTypeface(mHeadlineFont);
            TextView bodyText = container.findViewById(R.id.font_card_body);
            bodyText.setTypeface(mBodyFont);
            container.findViewById(R.id.font_card_divider).setBackgroundColor(
                    title.getCurrentTextColor());
        }

        @Override
        public Builder buildStep(Builder builder) {
            builder.setHeadlineFontFamily(mHeadlineFont).setBodyFontFamily(mBodyFont);
            return super.buildStep(builder);
        }
    }

    void bindPreviewHeader(ViewGroup container, @StringRes int headerTextResId,
            @DrawableRes int headerIcon) {
        TextView header = container.findViewById(R.id.theme_preview_card_header);
        header.setText(headerTextResId);

        Context context = container.getContext();
        Drawable icon = context.getResources().getDrawable(headerIcon, context.getTheme());
        int size = context.getResources().getDimensionPixelSize(R.dimen.card_header_icon_size);
        icon.setBounds(0, 0, size, size);

        header.setCompoundDrawables(null, icon, null, null);
        header.setCompoundDrawableTintList(ColorStateList.valueOf(
                header.getCurrentTextColor()));
    }

    public static class IconOption extends ThemeComponentOption {

        public static final int THUMBNAIL_ICON_POSITION = 0;
        private static int[] mIconIds = {
                R.id.preview_icon_0, R.id.preview_icon_1, R.id.preview_icon_2, R.id.preview_icon_3,
                R.id.preview_icon_4, R.id.preview_icon_5
        };

        private List<Drawable> mIcons = new ArrayList<>();
        private String mLabel;

        @Override
        public void bindThumbnailTile(View view) {
            Resources res = view.getContext().getResources();
            Drawable icon = mIcons.get(THUMBNAIL_ICON_POSITION)
                    .getConstantState().newDrawable().mutate();
            icon.setTint(res.getColor(R.color.icon_thumbnail_color, null));
            ((ImageView) view.findViewById(R.id.option_icon)).setImageDrawable(
                    icon);
            view.setContentDescription(mLabel);
        }

        @Override
        public boolean isActive(CustomizationManager<ThemeComponentOption> manager) {
            CustomThemeManager customThemeManager = (CustomThemeManager) manager;
            Map<String, String> themePackages = customThemeManager.getOverlayPackages();
            if (getOverlayPackages().isEmpty()) {
                return themePackages.get(OVERLAY_CATEGORY_ICON_SYSUI) == null &&
                        themePackages.get(OVERLAY_CATEGORY_ICON_SETTINGS) == null &&
                        themePackages.get(OVERLAY_CATEGORY_ICON_ANDROID) == null &&
                        themePackages.get(OVERLAY_CATEGORY_ICON_LAUNCHER) == null &&
                        themePackages.get(OVERLAY_CATEGORY_ICON_THEMEPICKER) == null;
            }
            for (Map.Entry<String, String> overlayEntry : getOverlayPackages().entrySet()) {
                if(!Objects.equals(overlayEntry.getValue(),
                        themePackages.get(overlayEntry.getKey()))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int getLayoutResId() {
            return R.layout.theme_icon_option;
        }

        @Override
        public void bindPreview(ViewGroup container) {
            bindPreviewHeader(container, R.string.preview_name_icon, R.drawable.ic_wifi_24px);

            ViewGroup cardBody = container.findViewById(R.id.theme_preview_card_body_container);
            if (cardBody.getChildCount() == 0) {
                LayoutInflater.from(container.getContext()).inflate(
                        R.layout.preview_card_icon_content, cardBody, true);
            }
            for (int i = 0; i < mIconIds.length && i < mIcons.size(); i++) {
                ((ImageView) container.findViewById(mIconIds[i])).setImageDrawable(
                        mIcons.get(i));
            }
        }

        public void addIcon(Drawable previewIcon) {
            mIcons.add(previewIcon);
        }

        /**
         * @return whether this icon option has overlays and previews for all the required packages
         */
        public boolean isValid(Context context) {
            return getOverlayPackages().keySet().size() ==
                    ResourceConstants.getPackagesToOverlay(context).length;
        }

        public void setLabel(String label) {
            mLabel = label;
        }

        @Override
        public Builder buildStep(Builder builder) {
            for (Drawable icon : mIcons) {
                builder.addIcon(icon);
            }
            return super.buildStep(builder);
        }
    }

    public static class ColorOption extends ThemeComponentOption {

        /**
         * Ids of views used to represent quick setting tiles in the color preview screen
         */
        private static int[] COLOR_TILE_IDS = {
                R.id.preview_color_qs_0_bg, R.id.preview_color_qs_1_bg, R.id.preview_color_qs_2_bg
        };

        /**
         * Ids of the views for the foreground of the icon, mapping to the corresponding index of
         * the actual icon drawable.
         */
        static int[][] COLOR_TILES_ICON_IDS = {
                new int[]{ R.id.preview_color_qs_0_icon, 0},
                new int[]{ R.id.preview_color_qs_1_icon, 1},
                new int[] { R.id.preview_color_qs_2_icon, 3}
        };

        /**
         * Ids of views used to represent control buttons in the color preview screen
         */
        private static int[] COLOR_BUTTON_IDS = {
                R.id.preview_check_selected, R.id.preview_radio_selected,
                R.id.preview_toggle_selected
        };

        @ColorInt private int mColorAccentLight;
        @ColorInt private int mColorAccentDark;
        /**
         * Icons shown as example of QuickSettings tiles in the color preview screen.
         */
        private List<Drawable> mIcons = new ArrayList<>();

        /**
         * Drawable with the currently selected shape to be used as background of the sample
         * QuickSetting icons in the color preview screen.
         */
        private Drawable mShapeDrawable;

        private String mLabel;

        ColorOption(String packageName, String label, @ColorInt int lightColor,
                @ColorInt int darkColor) {
            addOverlayPackage(OVERLAY_CATEGORY_COLOR, packageName);
            mLabel = label;
            mColorAccentLight = lightColor;
            mColorAccentDark = darkColor;
        }

        @Override
        public void bindThumbnailTile(View view) {
            @ColorInt int color = resolveColor(view.getResources());
            ((ImageView) view.findViewById(R.id.option_tile)).setImageTintList(
                    ColorStateList.valueOf(color));
            view.setContentDescription(mLabel);
        }

        @ColorInt
        private int resolveColor(Resources res) {
            Configuration configuration = res.getConfiguration();
            return (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES ? mColorAccentDark : mColorAccentLight;
        }

        @Override
        public boolean isActive(CustomizationManager<ThemeComponentOption> manager) {
            CustomThemeManager customThemeManager = (CustomThemeManager) manager;
            return Objects.equals(getOverlayPackages().get(OVERLAY_CATEGORY_COLOR),
                    customThemeManager.getOverlayPackages().get(OVERLAY_CATEGORY_COLOR));
        }

        @Override
        public int getLayoutResId() {
            return R.layout.theme_color_option;
        }

        @Override
        public void bindPreview(ViewGroup container) {
            bindPreviewHeader(container, R.string.preview_name_color, R.drawable.ic_colorize_24px);

            ViewGroup cardBody = container.findViewById(R.id.theme_preview_card_body_container);
            if (cardBody.getChildCount() == 0) {
                LayoutInflater.from(container.getContext()).inflate(
                        R.layout.preview_card_color_content, cardBody, true);
            }
            Resources res = container.getResources();
            @ColorInt int accentColor = resolveColor(res);
            @ColorInt int controlGreyColor = res.getColor(R.color.control_grey);
            ColorStateList tintList = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_selected},
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_enabled}
                    },
                    new int[] {
                            accentColor,
                            accentColor,
                            controlGreyColor
                    }
            );

            for (int i = 0; i < COLOR_BUTTON_IDS.length; i++) {
                CompoundButton button = container.findViewById(COLOR_BUTTON_IDS[i]);
                button.setButtonTintList(tintList);
            }

            Switch enabledSwitch = container.findViewById(R.id.preview_toggle_selected);
            enabledSwitch.setThumbTintList(tintList);
            enabledSwitch.setTrackTintList(tintList);

            ColorStateList seekbarTintList = ColorStateList.valueOf(accentColor);
            SeekBar seekbar = container.findViewById(R.id.preview_seekbar);
            seekbar.setThumbTintList(seekbarTintList);
            seekbar.setProgressTintList(seekbarTintList);
            seekbar.setProgressBackgroundTintList(seekbarTintList);
            // Disable seekbar
            seekbar.setOnTouchListener((view, motionEvent) -> true);
            if (!mIcons.isEmpty() && mShapeDrawable != null) {
                for (int i = 0; i < COLOR_TILE_IDS.length; i++) {
                    Drawable icon = mIcons.get(COLOR_TILES_ICON_IDS[i][1]).getConstantState()
                            .newDrawable();
                    //TODO: load and set the shape.
                    Drawable bgShape = mShapeDrawable.getConstantState().newDrawable();
                    bgShape.setTint(accentColor);

                    ImageView bg = container.findViewById(COLOR_TILE_IDS[i]);
                    bg.setImageDrawable(bgShape);
                    ImageView fg = container.findViewById(COLOR_TILES_ICON_IDS[i][0]);
                    fg.setImageDrawable(icon);
                }
            }
        }

        public void setPreviewIcons(List<Drawable> icons) {
            mIcons.addAll(icons);
        }

        public void setShapeDrawable(@Nullable Drawable shapeDrawable) {
            mShapeDrawable = shapeDrawable;
        }

        @Override
        public Builder buildStep(Builder builder) {
            builder.setColorAccentDark(mColorAccentDark).setColorAccentLight(mColorAccentLight);
            return super.buildStep(builder);
        }
    }

    public static class ShapeOption extends ThemeComponentOption {

        private final LayerDrawable mShape;
        private final List<Drawable> mAppIcons;
        private final String mLabel;
        private final Path mPath;
        private final int mCornerRadius;
        private int[] mShapeIconIds = {
                R.id.shape_preview_icon_0, R.id.shape_preview_icon_1, R.id.shape_preview_icon_2,
                R.id.shape_preview_icon_3, R.id.shape_preview_icon_4, R.id.shape_preview_icon_5
        };

        ShapeOption(String packageName, String label, Path path,
                @Dimension int cornerRadius, Drawable shapeDrawable,
                List<Drawable> appIcons) {
            addOverlayPackage(OVERLAY_CATEGORY_SHAPE, packageName);
            mLabel = label;
            mAppIcons = appIcons;
            mPath = path;
            mCornerRadius = cornerRadius;
            Drawable background = shapeDrawable.getConstantState().newDrawable();
            Drawable foreground = shapeDrawable.getConstantState().newDrawable();
            mShape = new LayerDrawable(new Drawable[]{background, foreground});
            mShape.setLayerGravity(0, Gravity.CENTER);
            mShape.setLayerGravity(1, Gravity.CENTER);
        }

        @Override
        public void bindThumbnailTile(View view) {
            ImageView thumb = view.findViewById(R.id.shape_thumbnail);
            Resources res = view.getResources();
            Theme theme = view.getContext().getTheme();
            int borderWidth = 2 * res.getDimensionPixelSize(R.dimen.option_border_width);

            Drawable background = mShape.getDrawable(0);
            background.setTintList(res.getColorStateList(R.color.option_border_color, theme));

            ShapeDrawable foreground = (ShapeDrawable) mShape.getDrawable(1);

            foreground.setIntrinsicHeight(background.getIntrinsicHeight() - borderWidth);
            foreground.setIntrinsicWidth(background.getIntrinsicWidth() - borderWidth);
            TypedArray ta = view.getContext().obtainStyledAttributes(
                    new int[]{android.R.attr.colorPrimary});
            int primaryColor = ta.getColor(0, 0);
            ta.recycle();
            int foregroundColor = res.getColor(R.color.shape_option_tile_foreground_color, theme);

            foreground.setTint(ColorUtils.blendARGB(primaryColor, foregroundColor, .05f));

            thumb.setImageDrawable(mShape);
            view.setContentDescription(mLabel);
        }

        @Override
        public boolean isActive(CustomizationManager<ThemeComponentOption> manager) {
            CustomThemeManager customThemeManager = (CustomThemeManager) manager;
            return Objects.equals(getOverlayPackages().get(OVERLAY_CATEGORY_SHAPE),
                    customThemeManager.getOverlayPackages().get(OVERLAY_CATEGORY_SHAPE));
        }

        @Override
        public int getLayoutResId() {
            return R.layout.theme_shape_option;
        }

        @Override
        public void bindPreview(ViewGroup container) {
            bindPreviewHeader(container, R.string.preview_name_shape, R.drawable.ic_shapes_24px);

            ViewGroup cardBody = container.findViewById(R.id.theme_preview_card_body_container);
            if (cardBody.getChildCount() == 0) {
                LayoutInflater.from(container.getContext()).inflate(
                        R.layout.preview_card_shape_content, cardBody, true);
            }
            for (int i = 0; i < mShapeIconIds.length && i < mAppIcons.size(); i++) {
                ImageView iconView = cardBody.findViewById(mShapeIconIds[i]);
                iconView.setBackground(mAppIcons.get(i));
            }
        }

        @Override
        public Builder buildStep(Builder builder) {
            builder.setShapePath(mPath).setBottomSheetCornerRadius(mCornerRadius);
            for (Drawable appIcon : mAppIcons) {
                builder.addShapePreviewIcon(appIcon);
            }
            return super.buildStep(builder);
        }
    }
}
