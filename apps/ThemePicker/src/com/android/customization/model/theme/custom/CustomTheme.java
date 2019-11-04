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

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.customization.model.CustomizationManager;
import com.android.customization.model.theme.ThemeBundle;
import com.android.wallpaper.R;

import java.util.Map;
import java.util.UUID;

public class CustomTheme extends ThemeBundle {

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Used to uniquely identify a custom theme since names can change.
     */
    private final String mId;

    public CustomTheme(@NonNull String id, String title, Map<String, String> overlayPackages,
            @Nullable PreviewInfo previewInfo) {
        super(title, overlayPackages, false, null, null, previewInfo);
        mId = id;
    }

    public String getId() {
        return mId;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CustomTheme)) {
            return false;
        }
        CustomTheme other = (CustomTheme) obj;
        return mId.equals(other.mId);
    }

    @Override
    public int hashCode() {
        return mId.hashCode();
    }

    @Override
    public void bindThumbnailTile(View view) {
        if (isDefined()) {
            super.bindThumbnailTile(view);
        }
    }

    @Override
    public int getLayoutResId() {
        return isDefined() ? R.layout.theme_option : R.layout.custom_theme_option;
    }

    @Override
    public boolean shouldUseThemeWallpaper() {
        return false;
    }

    @Override
    public boolean isActive(CustomizationManager<ThemeBundle> manager) {
        return isDefined() && super.isActive(manager);
    }

    public boolean isDefined() {
        return getPreviewInfo() != null;
    }

    public static class Builder extends ThemeBundle.Builder {
        private String mId;

        @Override
        public CustomTheme build(Context context) {
            return new CustomTheme(mId, mTitle, mPackages, createPreviewInfo(context));
        }

        public Builder setId(String id) {
            mId = id;
            return this;
        }
    }
}
