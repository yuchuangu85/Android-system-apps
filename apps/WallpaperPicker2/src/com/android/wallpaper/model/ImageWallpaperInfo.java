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
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.ContentUriAsset;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Represents a wallpaper image from the system's image picker.
 */
public class ImageWallpaperInfo extends WallpaperInfo {
    public static final Parcelable.Creator<ImageWallpaperInfo> CREATOR =
            new Parcelable.Creator<ImageWallpaperInfo>() {
                @Override
                public ImageWallpaperInfo createFromParcel(Parcel in) {
                    return new ImageWallpaperInfo(in);
                }

                @Override
                public ImageWallpaperInfo[] newArray(int size) {
                    return new ImageWallpaperInfo[size];
                }
            };
    private static final String TAG = "ImageWallpaperInfo";
    // Desired EXIF tags in descending order of priority.
    private static final String[] EXIF_TAGS = {
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_MODEL,
    };
    private Uri mUri;
    private ContentUriAsset mAsset;
    private boolean mIsAssetUncached;

    public ImageWallpaperInfo(Uri uri) {
        mUri = uri;
    }

    public ImageWallpaperInfo(Uri uri, boolean uncachedAsset) {
        mUri = uri;
        mIsAssetUncached = uncachedAsset;
    }

    protected ImageWallpaperInfo(Parcel in) {
        mUri = Uri.parse(in.readString());
    }

    @Override
    public Uri getUri() {
        return mUri;
    }

    /**
     * Formats a localized date string based on the provided datetime string in EXIF datetime format.
     *
     * @param exifDateTime Datetime string in EXIF datetime format.
     * @return Localized date string, or the original datetime string if it could not be parsed.
     */
    private static String formatDate(String exifDateTime) {
        try {
            Date parsedDate = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss").parse(exifDateTime);
            return SimpleDateFormat.getDateInstance().format(parsedDate);
        } catch (ParseException e) {
            Log.w(TAG, "Unable to parse image datetime", e);
            return exifDateTime;
        }
    }

    private static List<String> getGenericAttributions(Context context) {
        return Arrays.asList(
                context.getResources().getString(R.string.my_photos_generic_wallpaper_title));
    }

    @Override
    public String getTitle(Context context) {
        return null;
    }

    @Override
    public List<String> getAttributions(Context context) {
        ContentUriAsset asset = (ContentUriAsset) getAsset(context);

        if (!asset.isJpeg()) {
            // Return generic attributions if image is not stored in the JPEG file format.
            return getGenericAttributions(context);
        }

        List<String> attributes = new ArrayList<>();

        for (String tag : EXIF_TAGS) {
            String attribute = asset.readExifTag(tag);

            if (attribute == null) {
                continue;
            }

            if (tag == ExifInterface.TAG_DATETIME_ORIGINAL) {
                attribute = formatDate(attribute);
            }

            attributes.add(attribute);
        }

        if (!attributes.isEmpty()) {
            return attributes;
        }

        // Return generic attributions if image did not contain any desired EXIF tags.
        return getGenericAttributions(context);
    }

    @Override
    public Asset getAsset(Context context) {
        if (mIsAssetUncached) {
            mAsset = new ContentUriAsset(
                    context,
                    mUri,
                    /* uncached */ true);
        } else {
            if (mAsset == null) {
                mAsset = new ContentUriAsset(context, mUri);
            }
        }

        return mAsset;
    }

    @Override
    public Asset getThumbAsset(Context context) {
        return getAsset(context);
    }

    @Override
    public String getCollectionId(Context context) {
        return context.getString(R.string.image_wallpaper_collection_id);
    }

    @Override
    public void showPreview(Activity srcActivity, InlinePreviewIntentFactory factory,
                            int requestCode) {
        srcActivity.startActivityForResult(factory.newIntent(srcActivity, this), requestCode);
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(mUri.toString());
    }
}
