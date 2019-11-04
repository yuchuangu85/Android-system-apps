/*
 * Copyright (c) 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.media.testmediaapp;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;

public class TmaAssetProvider extends ContentProvider {

    private static final String TAG = "TmaAssetProvider";

    private static final String PACKAGE_NAME = "com.android.car.media.testmediaapp";

    private static final String ASSET_URI_PREFIX =
            ContentResolver.SCHEME_CONTENT + "://" + PACKAGE_NAME + ".assets/";

    private static final String RESOURCE_URI_PREFIX =
            ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + PACKAGE_NAME + "/";


    public static String buildUriString(String localArt) {
        String prefix = localArt.startsWith("drawable") ? RESOURCE_URI_PREFIX : ASSET_URI_PREFIX;
        return prefix + localArt;
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        Log.i(TAG, "TmaAssetProvider#openAssetFile " + uri);

        String file_path = uri.getPath();
        if (TextUtils.isEmpty(file_path)) throw new FileNotFoundException();
        try {
            if (file_path.startsWith("/")) {
                file_path = file_path.substring(1);
            }
            return getContext().getAssets().openFd(file_path);
        } catch (IOException e) {
            Log.e(TAG, "openAssetFile failed: " + e);
            return null;
        }
    }

    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
