/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.model;

import android.app.Activity;
import android.content.Context;

import com.android.wallpaper.asset.Asset;

import java.util.List;

/**
 * Wallpaper category that includes a collection of wallpapers in addition to support for custom
 * photos picked through an Android photo file picker. Intended for use on desktop form factors.
 */
public class DesktopCustomCategory extends WallpaperCategory {

    public DesktopCustomCategory(String title, String collectionId,
                                 List<WallpaperInfo> wallpaperInfos, int priority) {
        super(title, collectionId, wallpaperInfos, priority);
    }

    @Override
    public Asset getThumbnail(Context context) {
        return null;
    }

    @Override
    public void show(Activity srcActivity, PickerIntentFactory factory, int requestCode) {
        // no op
    }

    @Override
    public boolean supportsCustomPhotos() {
        return true;
    }
}
