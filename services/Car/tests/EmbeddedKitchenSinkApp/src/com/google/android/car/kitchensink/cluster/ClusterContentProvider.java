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
package com.google.android.car.kitchensink.cluster;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Image Content Provider for the car instument cluster
 */
public class ClusterContentProvider extends ContentProvider {
    private static final String TAG = "ClusterContentProvider";
    private static final String AUTHORITY =
            "com.google.android.car.kitchensink.cluster.clustercontentprovider";

    private UriMatcher mUriMatcher;
    private static final int URI_IMAGE_CODE = 1;

    @Override
    public boolean onCreate() {
        mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mUriMatcher.addURI(AUTHORITY, "img/*", URI_IMAGE_CODE);

        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        switch (mUriMatcher.match(uri)) {
            case URI_IMAGE_CODE:
                // Tries to get the img file from internal cache
                String filename = new File(uri.getPath()).getName();
                File imageFile = new File(
                        getContext().getCacheDir() + File.separator + uri.getLastPathSegment());

                // If the file doesn't exist in internal cache,
                // copy the file from res.raw into internal cache
                if (!imageFile.exists()) {
                    InputStream inputStream = getContext().getResources().openRawResource(
                            getContext().getResources().getIdentifier(
                                    filename.substring(0, filename.lastIndexOf(".")),
                                    "raw",
                                    getContext().getPackageName()));

                    try {
                        Files.copy(inputStream, imageFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        Log.e(TAG, "could not copy file to internal cache: " + uri.getPath(), e);
                    }

                    imageFile = new File(
                            getContext().getCacheDir() + File.separator + uri.getLastPathSegment());
                }

                ParcelFileDescriptor image = ParcelFileDescriptor.open(imageFile,
                        ParcelFileDescriptor.MODE_READ_ONLY);

                return image;

            default:
                return null;
        }
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
