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
package com.android.customization.model;

import android.view.View;

import androidx.annotation.LayoutRes;

import com.android.wallpaper.R;


/**
 * Represents an option of customization (eg, a ThemeBundle, a Clock face, a Grid size)
 */
public interface CustomizationOption <T extends CustomizationOption> {

    /**
     * Optional name or label for this option
     */
    String getTitle();

    /**
     * Will be called to bind the thumbnail tile to be displayed in the picker.
     *
     * @param view the View to bind, corresponding to a view inside the layout specified in
     *        {@link #getLayoutResId()} with id {@link R.id#option_tile}
     */
    void bindThumbnailTile(View view);

    /**
     * Returns whether this option is the one currently set in the System.
     */
    boolean isActive(CustomizationManager<T> manager);

    /**
     * Return the id of the layout used to show this option in the UI. It must contain a view with
     * id {@link com.android.wallpaper.R.id#option_tile} that will be passed to
     * {@link #bindThumbnailTile(View)} on bind time, and optionally a TextView with id
     * {@link R.id#option_label} that will be populated with the value from {@link #getTitle()} if
     * present.
     */
    @LayoutRes int getLayoutResId();
}