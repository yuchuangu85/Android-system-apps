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

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import com.android.customization.model.theme.custom.CustomThemeManager;
import com.android.customization.model.theme.custom.ThemeComponentOption;
import com.android.customization.model.theme.custom.ThemeComponentOptionProvider;
import com.android.customization.widget.OptionSelectorController;
import com.android.wallpaper.R;
import com.android.wallpaper.picker.ToolbarFragment;

public class CustomThemeComponentFragment extends CustomThemeStepFragment {
    private static final String ARG_USE_GRID_LAYOUT = "CustomThemeComponentFragment.use_grid";;

    public static CustomThemeComponentFragment newInstance(CharSequence toolbarTitle, int position,
            int titleResId) {
        return newInstance(toolbarTitle, position, titleResId, false);
    }

    public static CustomThemeComponentFragment newInstance(CharSequence toolbarTitle, int position,
            int titleResId, boolean allowGridLayout) {
        CustomThemeComponentFragment fragment = new CustomThemeComponentFragment();
        Bundle arguments = ToolbarFragment.createArguments(toolbarTitle);
        arguments.putInt(ARG_KEY_POSITION, position);
        arguments.putInt(ARG_KEY_TITLE_RES_ID, titleResId);
        arguments.putBoolean(ARG_USE_GRID_LAYOUT, allowGridLayout);
        fragment.setArguments(arguments);
        return fragment;
    }

    private ThemeComponentOptionProvider<? extends ThemeComponentOption> mProvider;
    private boolean mUseGridLayout;

    private RecyclerView mOptionsContainer;
    private OptionSelectorController<ThemeComponentOption> mOptionsController;
    private ThemeComponentOption mSelectedOption;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUseGridLayout = getArguments().getBoolean(ARG_USE_GRID_LAYOUT);
        mProvider = mHost.getComponentOptionProvider(mPosition);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        mOptionsContainer = view.findViewById(R.id.options_container);
        mPreviewContainer = view.findViewById(R.id.component_preview_content);
        mTitle = view.findViewById(R.id.component_options_title);
        mTitle.setText(mTitleResId);
        setUpOptions();

        return view;
    }

    @Override
    protected int getFragmentLayoutResId() {
        return R.layout.fragment_custom_theme_component;
    }

    public ThemeComponentOption getSelectedOption() {
        return mSelectedOption;
    }

    private void bindPreview() {
        mSelectedOption.bindPreview(mPreviewContainer);
    }

    private void setUpOptions() {
        mProvider.fetch(options -> {
            mOptionsController = new OptionSelectorController(
                    mOptionsContainer, options, mUseGridLayout, false);

            mOptionsController.addListener(selected -> {
                mSelectedOption = (ThemeComponentOption) selected;
                bindPreview();
            });
            mOptionsController.initOptions(mCustomThemeManager);

            for (ThemeComponentOption option : options) {
                if (option.isActive(mCustomThemeManager)) {
                    mSelectedOption = option;
                    break;
                }
            }
            if (mSelectedOption == null) {
                mSelectedOption = options.get(0);
            }
            mOptionsController.setSelectedOption(mSelectedOption);
        }, false);
    }
}
