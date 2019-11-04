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
package com.android.customization.picker.theme;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.android.customization.model.theme.ThemeBundle.PreviewInfo;
import com.android.customization.picker.TimeTicker;
import com.android.customization.picker.theme.ThemePreviewPage.ThemeCoverPage;
import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.BitmapCachingAsset;
import com.android.wallpaper.module.CurrentWallpaperInfoFactory;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.picker.ToolbarFragment;

public class CustomThemeNameFragment extends CustomThemeStepFragment {

    public static CustomThemeNameFragment newInstance(CharSequence toolbarTitle, int position,
            int titleResId) {
        CustomThemeNameFragment fragment = new CustomThemeNameFragment();
        Bundle arguments = ToolbarFragment.createArguments(toolbarTitle);
        arguments.putInt(ARG_KEY_POSITION, position);
        arguments.putInt(ARG_KEY_TITLE_RES_ID, titleResId);
        fragment.setArguments(arguments);
        return fragment;
    }


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

    private Asset mWallpaperAsset;
    private ThemeCoverPage mCoverPage;
    private TimeTicker mTicker;
    private EditText mNameEditor;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CurrentWallpaperInfoFactory currentWallpaperFactory = InjectorProvider.getInjector()
                .getCurrentWallpaperFactory(getActivity().getApplicationContext());
        currentWallpaperFactory.createCurrentWallpaperInfos(
                (homeWallpaper, lockWallpaper, presentationMode) -> {
                    mWallpaperAsset = new BitmapCachingAsset(getContext(),
                            homeWallpaper.getThumbAsset(getContext()));
                    if (mCoverPage != null) {
                        mCoverPage.bindBody(true);
                    }
                }, false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        mTitle = view.findViewById(R.id.component_options_title);
        mTitle.setText(mTitleResId);
        mNameEditor = view.findViewById(R.id.custom_theme_name);
        mNameEditor.setText(mCustomThemeManager.getOriginalTheme().getTitle());
        bindCover(view.findViewById(R.id.component_preview_content));

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        mTicker = TimeTicker.registerNewReceiver(getContext(), this::updateTime);
        updateTime();
    }

    private void updateTime() {
        if (mCoverPage != null) {
            mCoverPage.updateTime();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getContext() != null) {
            getContext().unregisterReceiver(mTicker);
        }
    }

    private void bindCover(CardView card) {
        Context context = getContext();
        PreviewInfo previewInfo = mCustomThemeManager.buildCustomThemePreviewInfo(context);
        mCoverPage = new ThemeCoverPage(context, getThemeName(),
                previewInfo.resolveAccentColor(getResources()), previewInfo.icons,
                previewInfo.headlineFontFamily, previewInfo.bottomSheeetCornerRadius,
                previewInfo.shapeDrawable, previewInfo.shapeAppIcons, null,
                mColorButtonIds, mColorTileIds, mColorTileIconIds, mShapeIconIds,
                new WallpaperLayoutListener());
        mCoverPage.setCard(card);
        mCoverPage.bindPreviewContent();
        mNameEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                ((TextView)card.findViewById(R.id.theme_preview_card_header)).setText(charSequence);
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    private class WallpaperLayoutListener implements OnLayoutChangeListener {

        @Override
        public void onLayoutChange(View view, int left, int top, int right,
                int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            int targetWidth = right - left;
            int targetHeight = bottom - top;
            if (targetWidth > 0 && targetHeight > 0) {
                if (mWallpaperAsset != null) {
                    mWallpaperAsset.decodeBitmap(
                            targetWidth, targetHeight,
                            bitmap -> setWallpaperBitmap(view, bitmap));
                }
                view.removeOnLayoutChangeListener(this);
            }
        }

        private void setWallpaperBitmap(View view, Bitmap bitmap) {
            Resources res = view.getContext().getResources();
            Drawable background = new BitmapDrawable(res, bitmap);
            background.setAlpha(ThemeCoverPage.COVER_PAGE_WALLPAPER_ALPHA);

            view.findViewById(R.id.theme_preview_card_background).setBackground(background);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mCoverPage = null;
    }

    @Override
    protected int getFragmentLayoutResId() {
        return R.layout.fragment_custom_theme_name;
    }

    public String getThemeName() {
        return mNameEditor.getText().toString();
    }
}
