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
package com.android.customization.model.grid;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.customization.model.ResourceConstants;
import com.android.wallpaper.R;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstracts the logic to retrieve available grid options from the current Launcher.
 */
public class LauncherGridOptionsProvider {

    private static final String LIST_OPTIONS = "list_options";
    private static final String PREVIEW = "preview";
    private static final String DEFAULT_GRID = "default_grid";

    private static final String COL_NAME = "name";
    private static final String COL_ROWS = "rows";
    private static final String COL_COLS = "cols";
    private static final String COL_PREVIEW_COUNT = "preview_count";
    private static final String COL_IS_DEFAULT = "is_default";

    private final Context mContext;
    private final String mGridProviderAuthority;
    private final ProviderInfo mProviderInfo;
    private List<GridOption> mOptions;

    public LauncherGridOptionsProvider(Context context, String authorityMetadataKey) {
        mContext = context;
        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);

        ResolveInfo info = context.getPackageManager().resolveActivity(homeIntent,
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_META_DATA);
        if (info != null && info.activityInfo != null && info.activityInfo.metaData != null) {
            mGridProviderAuthority = info.activityInfo.metaData.getString(authorityMetadataKey);
        } else {
            mGridProviderAuthority = null;
        }
        // TODO: check permissions if needed
        mProviderInfo = TextUtils.isEmpty(mGridProviderAuthority) ? null
                : mContext.getPackageManager().resolveContentProvider(mGridProviderAuthority, 0);
    }

    boolean areGridsAvailable() {
        return mProviderInfo != null;
    }

    /**
     * Retrieve the available grids.
     * @param reload whether to reload grid options if they're cached.
     */
    @WorkerThread
    @Nullable
    List<GridOption> fetch(boolean reload) {
        if (!areGridsAvailable()) {
            return null;
        }
        if (mOptions != null && !reload) {
            return mOptions;
        }
        Uri optionsUri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(mProviderInfo.authority)
                .appendPath(LIST_OPTIONS)
                .build();
        ContentResolver resolver = mContext.getContentResolver();
        String iconPath = mContext.getResources().getString(Resources.getSystem().getIdentifier(
                ResourceConstants.CONFIG_ICON_MASK, "string", ResourceConstants.ANDROID_PACKAGE));
        try (Cursor c = resolver.query(optionsUri, null, null, null, null)) {
            mOptions = new ArrayList<>();
            while(c.moveToNext()) {
                String name = c.getString(c.getColumnIndex(COL_NAME));
                int rows = c.getInt(c.getColumnIndex(COL_ROWS));
                int cols = c.getInt(c.getColumnIndex(COL_COLS));
                int previewCount = c.getInt(c.getColumnIndex(COL_PREVIEW_COUNT));
                boolean isSet = Boolean.valueOf(c.getString(c.getColumnIndex(COL_IS_DEFAULT)));
                Uri preview = new Uri.Builder()
                        .scheme(ContentResolver.SCHEME_CONTENT)
                        .authority(mProviderInfo.authority)
                        .appendPath(PREVIEW)
                        .appendPath(name)
                        .build();
                String title = mContext.getString(R.string.grid_title_pattern, cols, rows);
                mOptions.add(new GridOption(title, name, isSet, rows, cols, preview, previewCount,
                        iconPath));
            }
            Glide.get(mContext).clearDiskCache();
        } catch (Exception e) {
            mOptions = null;
        }
        return mOptions;
    }

    int applyGrid(String name) {
        Uri updateDefaultUri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(mProviderInfo.authority)
                .appendPath(DEFAULT_GRID)
                .build();
        ContentValues values = new ContentValues();
        values.put("name", name);
        return mContext.getContentResolver().update(updateDefaultUri, values, null, null);
    }
}
