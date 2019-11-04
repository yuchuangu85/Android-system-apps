/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.customization.picker.theme;

import android.app.Activity;
import android.app.WallpaperColors;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.RecyclerView;

import com.android.customization.model.CustomizationManager.Callback;
import com.android.customization.model.CustomizationManager.OptionsFetchedListener;
import com.android.customization.model.theme.ThemeBundle;
import com.android.customization.model.theme.ThemeBundle.PreviewInfo;
import com.android.customization.model.theme.ThemeManager;
import com.android.customization.model.theme.custom.CustomTheme;
import com.android.customization.module.ThemesUserEventLogger;
import com.android.customization.picker.BasePreviewAdapter;
import com.android.customization.picker.TimeTicker;
import com.android.customization.picker.theme.ThemePreviewPage.ThemeCoverPage;
import com.android.customization.picker.theme.ThemePreviewPage.TimeContainer;
import com.android.customization.widget.OptionSelectorController;
import com.android.customization.widget.PreviewPager;
import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.CurrentWallpaperInfoFactory;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.picker.ToolbarFragment;

import java.util.List;

/**
 * Fragment that contains the main UI for selecting and applying a ThemeBundle.
 */
public class ThemeFragment extends ToolbarFragment {

    private static final String TAG = "ThemeFragment";
    private static final String KEY_SELECTED_THEME = "ThemeFragment.SelectedThemeBundle";

    /**
     * Interface to be implemented by an Activity hosting a {@link ThemeFragment}
     */
    public interface ThemeFragmentHost {
        ThemeManager getThemeManager();
    }
    public static ThemeFragment newInstance(CharSequence title) {
        ThemeFragment fragment = new ThemeFragment();
        fragment.setArguments(ToolbarFragment.createArguments(title));
        return fragment;
    }

    private RecyclerView mOptionsContainer;
    private CheckBox mUseMyWallpaperButton;
    private OptionSelectorController<ThemeBundle> mOptionsController;
    private ThemeManager mThemeManager;
    private ThemesUserEventLogger mEventLogger;
    private ThemeBundle mSelectedTheme;
    private ThemePreviewAdapter mAdapter;
    private PreviewPager mPreviewPager;
    private ContentLoadingProgressBar mLoading;
    private View mContent;
    private View mError;
    private boolean mUseMyWallpaper;
    private WallpaperInfo mCurrentHomeWallpaper;
    private CurrentWallpaperInfoFactory mCurrentWallpaperFactory;
    private TimeTicker mTicker;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mThemeManager = ((ThemeFragmentHost) context).getThemeManager();
        mEventLogger = (ThemesUserEventLogger)
                InjectorProvider.getInjector().getUserEventLogger(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.fragment_theme_picker, container, /* attachToRoot */ false);
        setUpToolbar(view);

        mContent = view.findViewById(R.id.content_section);
        mLoading = view.findViewById(R.id.loading_indicator);
        mError = view.findViewById(R.id.error_section);
        mCurrentWallpaperFactory = InjectorProvider.getInjector()
                .getCurrentWallpaperFactory(getActivity().getApplicationContext());
        mPreviewPager = view.findViewById(R.id.theme_preview_pager);
        mOptionsContainer = view.findViewById(R.id.options_container);
        view.findViewById(R.id.apply_button).setOnClickListener(v -> {
            applyTheme();
        });
        mUseMyWallpaperButton = view.findViewById(R.id.use_my_wallpaper);
        mUseMyWallpaperButton.setOnCheckedChangeListener(this::onUseMyWallpaperCheckChanged);
        setUpOptions(savedInstanceState);

        return view;
    }

    private void applyTheme() {
        mThemeManager.apply(mSelectedTheme, new Callback() {
            @Override
            public void onSuccess() {
                Toast.makeText(getContext(), R.string.applied_theme_msg,
                        Toast.LENGTH_LONG).show();
                getActivity().finish();
                getActivity().overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            }

            @Override
            public void onError(@Nullable Throwable throwable) {
                Log.w(TAG, "Error applying theme", throwable);
                Toast.makeText(getContext(), R.string.apply_theme_error_msg,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        mTicker = TimeTicker.registerNewReceiver(getContext(), this::updateTime);
        reloadWallpaper();
        updateTime();
    }

    private void updateTime() {
        if (mAdapter != null) {
            mAdapter.updateTime();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getContext() != null) {
            getContext().unregisterReceiver(mTicker);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSelectedTheme != null && !mSelectedTheme.isActive(mThemeManager)) {
            outState.putString(KEY_SELECTED_THEME, mSelectedTheme.getSerializedPackages());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CustomThemeActivity.REQUEST_CODE_CUSTOM_THEME) {
            if (resultCode == CustomThemeActivity.RESULT_THEME_DELETED) {
                mSelectedTheme = null;
                reloadOptions();
            } else if (resultCode == CustomThemeActivity.RESULT_THEME_APPLIED) {
                getActivity().finish();
            } else {
                if (mSelectedTheme != null) {
                    mOptionsController.setSelectedOption(mSelectedTheme);
                } else {
                    reloadOptions();
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void onUseMyWallpaperCheckChanged(CompoundButton checkbox, boolean checked) {
        mUseMyWallpaper = checked;
        reloadWallpaper();
    }

    private void reloadWallpaper() {
        mCurrentWallpaperFactory.createCurrentWallpaperInfos(
                (homeWallpaper, lockWallpaper, presentationMode) -> {
                    mCurrentHomeWallpaper = homeWallpaper;
                    if (mSelectedTheme != null) {
                        if (mUseMyWallpaper || (mSelectedTheme instanceof CustomTheme)) {
                            mSelectedTheme.setOverrideThemeWallpaper(homeWallpaper);
                        } else {
                            mSelectedTheme.setOverrideThemeWallpaper(null);
                        }
                        if (mAdapter != null) {
                            mAdapter.rebindWallpaperIfAvailable();
                        }
                    }
        }, false);
    }

    private void createAdapter(List<ThemeBundle> options) {
        mAdapter = new ThemePreviewAdapter(getActivity(), mSelectedTheme,
                mSelectedTheme instanceof CustomTheme ? this::onEditClicked : null,
                new PreloadWallpapersLayoutListener(options));
        mPreviewPager.setAdapter(mAdapter);
    }

    private void onEditClicked(View view) {
        if (mSelectedTheme instanceof CustomTheme) {
            navigateToCustomTheme((CustomTheme) mSelectedTheme);
        }
    }

    private void updateButtonsVisibility() {
        mUseMyWallpaperButton.setVisibility(mSelectedTheme instanceof CustomTheme
                ? View.INVISIBLE : View.VISIBLE);
    }

    private void hideError() {
        mContent.setVisibility(View.VISIBLE);
        mError.setVisibility(View.GONE);
    }

    private void showError() {
        mLoading.hide();
        mContent.setVisibility(View.GONE);
        mError.setVisibility(View.VISIBLE);
    }

    private void setUpOptions(@Nullable Bundle savedInstanceState) {
        hideError();
        mLoading.show();
        mThemeManager.fetchOptions(new OptionsFetchedListener<ThemeBundle>() {
            @Override
            public void onOptionsLoaded(List<ThemeBundle> options) {
                mOptionsController = new OptionSelectorController<>(mOptionsContainer, options);
                mOptionsController.addListener(selected -> {
                    mLoading.hide();
                    if (selected instanceof CustomTheme && !((CustomTheme) selected).isDefined()) {
                        navigateToCustomTheme((CustomTheme) selected);
                    } else {
                        mSelectedTheme = (ThemeBundle) selected;
                        if (mUseMyWallpaper || mSelectedTheme instanceof CustomTheme) {
                            mSelectedTheme.setOverrideThemeWallpaper(mCurrentHomeWallpaper);
                        } else {
                            mSelectedTheme.setOverrideThemeWallpaper(null);
                        }
                        mEventLogger.logThemeSelected(mSelectedTheme,
                                selected instanceof CustomTheme);
                        createAdapter(options);
                        updateButtonsVisibility();
                    }
                });
                mOptionsController.initOptions(mThemeManager);
                String previouslySelected = savedInstanceState != null
                        ? savedInstanceState.getString(KEY_SELECTED_THEME) : null;
                for (ThemeBundle theme : options) {
                    if (previouslySelected != null
                            && previouslySelected.equals(theme.getSerializedPackages())) {
                        mSelectedTheme = theme;
                    } else if (theme.isActive(mThemeManager)) {
                        mSelectedTheme = theme;
                        break;
                    }
                }
                if (mSelectedTheme == null) {
                    // Select the default theme if there is no matching custom enabled theme
                    // TODO(b/124796742): default to custom if there is no matching theme bundle
                    mSelectedTheme = options.get(0);
                } else {
                    // Only show show checkmark if we found a matching theme
                    mOptionsController.setAppliedOption(mSelectedTheme);
                }
                mOptionsController.setSelectedOption(mSelectedTheme);
            }
            @Override
            public void onError(@Nullable Throwable throwable) {
                if (throwable != null) {
                    Log.e(TAG, "Error loading theme bundles", throwable);
                }
                showError();
            }
        }, false);
    }

    private void reloadOptions() {
        mThemeManager.fetchOptions(options -> {
            mOptionsController.resetOptions(options);
            for (ThemeBundle theme : options) {
                if (theme.isActive(mThemeManager)) {
                    mSelectedTheme = theme;
                    break;
                }
            }
            if (mSelectedTheme == null) {
                // Select the default theme if there is no matching custom enabled theme
                // TODO(b/124796742): default to custom if there is no matching theme bundle
                mSelectedTheme = options.get(0);
            } else {
                // Only show show checkmark if we found a matching theme
                mOptionsController.setAppliedOption(mSelectedTheme);
            }
            mOptionsController.setSelectedOption(mSelectedTheme);
        }, true);
    }

    private void navigateToCustomTheme(CustomTheme themeToEdit) {
        Intent intent = new Intent(getActivity(), CustomThemeActivity.class);
        intent.putExtra(CustomThemeActivity.EXTRA_THEME_TITLE, themeToEdit.getTitle());
        intent.putExtra(CustomThemeActivity.EXTRA_THEME_ID, themeToEdit.getId());
        intent.putExtra(CustomThemeActivity.EXTRA_THEME_PACKAGES,
                themeToEdit.getSerializedPackages());
        startActivityForResult(intent, CustomThemeActivity.REQUEST_CODE_CUSTOM_THEME);
    }

    /**
     * Adapter class for mPreviewPager.
     * This is a ViewPager as it allows for a nice pagination effect (ie, pages snap on swipe,
     * we don't want to just scroll)
     */
    private static class ThemePreviewAdapter extends BasePreviewAdapter<ThemePreviewPage> {

        private int[] mIconIds = {
                R.id.preview_icon_0, R.id.preview_icon_1, R.id.preview_icon_2, R.id.preview_icon_3,
                R.id.preview_icon_4, R.id.preview_icon_5
        };
        private int[] mColorButtonIds = {
            R.id.preview_check_selected, R.id.preview_radio_selected, R.id.preview_toggle_selected
        };
        private int[] mColorTileIds = {
            R.id.preview_color_qs_0_bg, R.id.preview_color_qs_1_bg, R.id.preview_color_qs_2_bg
        };
        private int[][] mColorTileIconIds = {
                new int[]{ R.id.preview_color_qs_0_icon, 0},
                new int[]{ R.id.preview_color_qs_1_icon, 1},
                new int[] { R.id.preview_color_qs_2_icon, 3}
        };

        private int[] mShapeIconIds = {
                R.id.shape_preview_icon_0, R.id.shape_preview_icon_1, R.id.shape_preview_icon_2,
                R.id.shape_preview_icon_3, R.id.shape_preview_icon_4, R.id.shape_preview_icon_5
        };

        ThemePreviewAdapter(Activity activity, ThemeBundle theme,
                @Nullable OnClickListener editClickListener,
                @Nullable OnLayoutChangeListener coverCardLayoutListener) {
            super(activity, R.layout.theme_preview_card);
            final Resources res = activity.getResources();
            final PreviewInfo previewInfo = theme.getPreviewInfo();

            Drawable coverScrim = theme instanceof CustomTheme
                    ? res.getDrawable(R.drawable.theme_cover_scrim, activity.getTheme())
                    : null;

            WallpaperPreviewLayoutListener wallpaperListener = new WallpaperPreviewLayoutListener(
                    theme, previewInfo, coverScrim, true);

            addPage(new ThemeCoverPage(activity, theme.getTitle(),
                    previewInfo.resolveAccentColor(res), previewInfo.icons,
                    previewInfo.headlineFontFamily, previewInfo.bottomSheeetCornerRadius,
                    previewInfo.shapeDrawable, previewInfo.shapeAppIcons, editClickListener,
                    mColorButtonIds, mColorTileIds, mColorTileIconIds, mShapeIconIds,
                    wallpaperListener, coverCardLayoutListener));
            addPage(new ThemePreviewPage(activity, R.string.preview_name_font, R.drawable.ic_font,
                    R.layout.preview_card_font_content,
                    previewInfo.resolveAccentColor(res)) {
                @Override
                protected void bindBody(boolean forceRebind) {
                    TextView title = card.findViewById(R.id.font_card_title);
                    title.setTypeface(previewInfo.headlineFontFamily);
                    TextView body = card.findViewById(R.id.font_card_body);
                    body.setTypeface(previewInfo.bodyFontFamily);
                    card.findViewById(R.id.font_card_divider).setBackgroundColor(accentColor);
                }
            });
            if (previewInfo.icons.size() >= mIconIds.length) {
                addPage(new ThemePreviewPage(activity, R.string.preview_name_icon,
                        R.drawable.ic_wifi_24px, R.layout.preview_card_icon_content,
                        previewInfo.resolveAccentColor(res)) {
                    @Override
                    protected void bindBody(boolean forceRebind) {
                        for (int i = 0; i < mIconIds.length && i < previewInfo.icons.size(); i++) {
                            ((ImageView) card.findViewById(mIconIds[i]))
                                    .setImageDrawable(previewInfo.icons.get(i)
                                            .getConstantState().newDrawable().mutate());
                        }
                    }
                });
            }
            if (previewInfo.colorAccentDark != -1 && previewInfo.colorAccentLight != -1) {
                addPage(new ThemePreviewPage(activity, R.string.preview_name_color,
                        R.drawable.ic_colorize_24px, R.layout.preview_card_color_content,
                        previewInfo.resolveAccentColor(res)) {
                    @Override
                    protected void bindBody(boolean forceRebind) {
                        int controlGreyColor = res.getColor(R.color.control_grey);
                        ColorStateList tintList = new ColorStateList(
                                new int[][]{
                                    new int[]{android.R.attr.state_selected},
                                    new int[]{android.R.attr.state_checked},
                                    new int[]{-android.R.attr.state_enabled},
                                },
                                new int[] {
                                    accentColor,
                                    accentColor,
                                    controlGreyColor
                                }
                            );

                        for (int i = 0; i < mColorButtonIds.length; i++) {
                            CompoundButton button = card.findViewById(mColorButtonIds[i]);
                            button.setButtonTintList(tintList);
                        }

                        Switch enabledSwitch = card.findViewById(R.id.preview_toggle_selected);
                        enabledSwitch.setThumbTintList(tintList);
                        enabledSwitch.setTrackTintList(tintList);

                        ColorStateList seekbarTintList = ColorStateList.valueOf(accentColor);
                        SeekBar seekbar = card.findViewById(R.id.preview_seekbar);
                        seekbar.setThumbTintList(seekbarTintList);
                        seekbar.setProgressTintList(seekbarTintList);
                        seekbar.setProgressBackgroundTintList(seekbarTintList);
                        // Disable seekbar
                        seekbar.setOnTouchListener((view, motionEvent) -> true);

                        for (int i = 0; i < mColorTileIds.length && i < previewInfo.icons.size();
                                i++) {
                            Drawable icon = previewInfo.icons.get(mColorTileIconIds[i][1])
                                    .getConstantState().newDrawable().mutate();
                            Drawable bgShape =
                                    previewInfo.shapeDrawable.getConstantState().newDrawable();
                            bgShape.setTint(accentColor);

                            ImageView bg = card.findViewById(mColorTileIds[i]);
                            bg.setImageDrawable(bgShape);
                            ImageView fg = card.findViewById(mColorTileIconIds[i][0]);
                            fg.setImageDrawable(icon);
                        }
                    }
                });
            }
            if (!previewInfo.shapeAppIcons.isEmpty()) {
                addPage(new ThemePreviewPage(activity, R.string.preview_name_shape,
                        R.drawable.ic_shapes_24px, R.layout.preview_card_shape_content,
                        previewInfo.resolveAccentColor(res)) {
                    @Override
                    protected void bindBody(boolean forceRebind) {
                        for (int i = 0; i < mShapeIconIds.length
                                && i < previewInfo.shapeAppIcons.size(); i++) {
                            ImageView iconView = card.findViewById(mShapeIconIds[i]);
                            iconView.setBackground(
                                    previewInfo.shapeAppIcons.get(i));
                        }
                    }
                });
            }
            if (previewInfo.wallpaperAsset != null) {
                addPage(new ThemePreviewPage(activity, R.string.preview_name_wallpaper,
                        R.drawable.ic_nav_wallpaper, R.layout.preview_card_wallpaper_content,
                        previewInfo.resolveAccentColor(res)) {

                    private final WallpaperPreviewLayoutListener mListener =
                            new WallpaperPreviewLayoutListener(theme, previewInfo, null, false);

                    @Override
                    protected boolean containsWallpaper() {
                        return true;
                    }

                    @Override
                    protected void bindBody(boolean forceRebind) {
                        if (card == null) {
                            return;
                        }
                        card.addOnLayoutChangeListener(mListener);
                        if (forceRebind) {
                            card.requestLayout();
                        }
                    }
                });
            }
        }

        public void rebindWallpaperIfAvailable() {
            for (ThemePreviewPage page : mPages) {
                if (page.containsWallpaper()) {
                    page.bindBody(true);
                }
            }
        }

        public void updateTime() {
            for (ThemePreviewPage page : mPages) {
                if (page instanceof TimeContainer) {
                    ((TimeContainer)page).updateTime();
                }
            }
        }

        private static class WallpaperPreviewLayoutListener implements OnLayoutChangeListener {
            private final ThemeBundle mTheme;
            private final PreviewInfo mPreviewInfo;
            private final Drawable mScrim;
            private final boolean mIsTranslucent;

            public WallpaperPreviewLayoutListener(ThemeBundle theme, PreviewInfo previewInfo,
                    Drawable scrim, boolean translucent) {
                mTheme = theme;
                mPreviewInfo = previewInfo;
                mScrim = scrim;
                mIsTranslucent = translucent;
            }

            @Override
            public void onLayoutChange(View view, int left, int top, int right,
                    int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int targetWidth = right - left;
                int targetHeight = bottom - top;
                if (targetWidth > 0 && targetHeight > 0) {
                    Asset wallpaperPreviewAsset = mTheme.getWallpaperPreviewAsset(
                            view.getContext());
                    if (wallpaperPreviewAsset != null) {
                        wallpaperPreviewAsset.decodeBitmap(
                                targetWidth, targetHeight,
                                bitmap -> setWallpaperBitmap(view, bitmap));
                    }
                    view.removeOnLayoutChangeListener(this);
                }
            }

            private void setWallpaperBitmap(View view, Bitmap bitmap) {
                Resources res = view.getContext().getResources();
                Drawable background = new BitmapDrawable(res, bitmap);
                if (mIsTranslucent) {
                    background.setAlpha(ThemeCoverPage.COVER_PAGE_WALLPAPER_ALPHA);
                }
                if (mScrim != null) {
                    background = new LayerDrawable(new Drawable[]{background, mScrim});
                }
                view.findViewById(R.id.theme_preview_card_background).setBackground(background);
                if (mScrim == null && !mIsTranslucent) {
                    boolean shouldRecycle = false;
                    if (bitmap.getConfig() == Config.HARDWARE) {
                        bitmap = bitmap.copy(Config.ARGB_8888, false);
                        shouldRecycle = true;
                    }
                    int colorsHint = WallpaperColors.fromBitmap(bitmap).getColorHints();
                    if (shouldRecycle) {
                        bitmap.recycle();
                    }
                    TextView header = view.findViewById(R.id.theme_preview_card_header);
                    if ((colorsHint & WallpaperColors.HINT_SUPPORTS_DARK_TEXT) == 0) {
                        int colorLight = res.getColor(R.color.text_color_light, null);
                        header.setTextColor(colorLight);
                        header.setCompoundDrawableTintList(ColorStateList.valueOf(colorLight));
                    } else {
                        header.setTextColor(res.getColor(R.color.text_color_dark, null));
                        header.setCompoundDrawableTintList(ColorStateList.valueOf(
                                mPreviewInfo.colorAccentLight));
                    }
                }
            }
        }
    }

    /**
     * Runs only once after the card size is known, and requests decoding wallpaper bitmaps
     * for all the options, to warm-up the bitmap cache.
     */
    private static class PreloadWallpapersLayoutListener implements OnLayoutChangeListener {
        private static boolean alreadyRunOnce;
        private final List<ThemeBundle> mOptions;

        public PreloadWallpapersLayoutListener(List<ThemeBundle> options) {
            mOptions = options;
        }

        @Override
        public void onLayoutChange(View view, int left, int top, int right,
                int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if (alreadyRunOnce) {
                view.removeOnLayoutChangeListener(this);
                return;
            }
            int targetWidth = right - left;
            int targetHeight = bottom - top;
            if (targetWidth > 0 && targetHeight > 0) {
                for (ThemeBundle theme : mOptions) {
                    if (theme instanceof CustomTheme && !((CustomTheme) theme).isDefined()) {
                        continue;
                    }
                    Asset wallpaperAsset = theme.getWallpaperPreviewAsset(view.getContext());
                    if (wallpaperAsset != null) {
                        wallpaperAsset.decodeBitmap(targetWidth, targetHeight, bitmap -> {});
                    }
                }
                view.removeOnLayoutChangeListener(this);
                alreadyRunOnce = true;
            }
        }
    }
}
