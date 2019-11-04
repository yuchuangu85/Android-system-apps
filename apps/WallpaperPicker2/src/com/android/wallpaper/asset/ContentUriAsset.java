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
package com.android.wallpaper.asset;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Represents an asset located via an Android content URI.
 */
public final class ContentUriAsset extends StreamableAsset {
    private static final String TAG = "ContentUriAsset";
    private static final String JPEG_MIME_TYPE = "image/jpeg";
    private static final String PNG_MIME_TYPE = "image/png";

    private final Context mContext;
    private final Uri mUri;
    private final RequestOptions mRequestOptions;

    private ExifInterfaceCompat mExifCompat;
    private int mExifOrientation;

    /**
     * @param context The application's context.
     * @param uri     Content URI locating the asset.
     * @param requestOptions {@link RequestOptions} to be applied when loading the asset.
     * @param uncached If true, {@link #loadDrawable(Context, ImageView, int)} and
     * {@link #loadDrawableWithTransition(Context, ImageView, int, DrawableLoadedListener, int)}
     * will not cache data, and fetch it each time.
     */
    public ContentUriAsset(Context context, Uri uri, RequestOptions requestOptions,
                           boolean uncached) {
        mExifOrientation = ExifInterfaceCompat.EXIF_ORIENTATION_UNKNOWN;
        mContext = context.getApplicationContext();
        mUri = uri;

        if (uncached) {
            mRequestOptions = requestOptions.apply(RequestOptions
                    .diskCacheStrategyOf(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true));
        } else {
            mRequestOptions = requestOptions;
        }
    }

    /**
     * @param context The application's context.
     * @param uri     Content URI locating the asset.
     * @param requestOptions {@link RequestOptions} to be applied when loading the asset.
     */
    public ContentUriAsset(Context context, Uri uri, RequestOptions requestOptions) {
        this(context, uri, requestOptions, /* uncached */ false);
    }

    /**
     * @param context The application's context.
     * @param uri     Content URI locating the asset.
     * @param uncached If true, {@link #loadDrawable(Context, ImageView, int)} and
     * {@link #loadDrawableWithTransition(Context, ImageView, int, DrawableLoadedListener, int)}
     * will not cache data, and fetch it each time.
     */
    public ContentUriAsset(Context context, Uri uri, boolean uncached) {
        this(context, uri, RequestOptions.centerCropTransform(), uncached);
    }

    /**
     * @param context The application's context.
     * @param uri     Content URI locating the asset.
     */
    public ContentUriAsset(Context context, Uri uri) {
            this(context, uri, /* uncached */ false);
    }



    @Override
    public void decodeBitmapRegion(final Rect rect, int targetWidth, int targetHeight,
                                   final BitmapReceiver receiver) {
        // BitmapRegionDecoder only supports images encoded in either JPEG or PNG, so if the content
        // URI asset is encoded with another format (for example, GIF), then fall back to cropping a
        // bitmap region from the full-sized bitmap.
        if (isJpeg() || isPng()) {
            super.decodeBitmapRegion(rect, targetWidth, targetHeight, receiver);
            return;
        }

        decodeRawDimensions(null /* activity */, new DimensionsReceiver() {
            @Override
            public void onDimensionsDecoded(@Nullable Point dimensions) {
                if (dimensions == null) {
                    Log.e(TAG, "There was an error decoding the asset's raw dimensions with " +
                            "content URI: " + mUri);
                    receiver.onBitmapDecoded(null);
                    return;
                }

                decodeBitmap(dimensions.x, dimensions.y, new BitmapReceiver() {
                    @Override
                    public void onBitmapDecoded(@Nullable Bitmap fullBitmap) {
                        if (fullBitmap == null) {
                            Log.e(TAG, "There was an error decoding the asset's full bitmap with " +
                                    "content URI: " + mUri);
                            receiver.onBitmapDecoded(null);
                            return;
                        }

                        BitmapCropTask task = new BitmapCropTask(fullBitmap, rect, receiver);
                        task.execute();
                    }
                });
            }
        });
    }

    /**
     * Returns whether this image is encoded in the JPEG file format.
     */
    public boolean isJpeg() {
        String mimeType = mContext.getContentResolver().getType(mUri);
        return mimeType != null && mimeType.equals(JPEG_MIME_TYPE);
    }

    /**
     * Returns whether this image is encoded in the PNG file format.
     */
    public boolean isPng() {
        String mimeType = mContext.getContentResolver().getType(mUri);
        return mimeType != null && mimeType.equals(PNG_MIME_TYPE);
    }

    /**
     * Reads the EXIF tag on the asset. Automatically trims leading and trailing whitespace.
     *
     * @return String attribute value for this tag ID, or null if ExifInterface failed to read tags
     * for this asset, if this tag was not found in the image's metadata, or if this tag was
     * empty (i.e., only whitespace).
     */
    public String readExifTag(String tagId) {
        ensureExifInterface();
        if (mExifCompat == null) {
            Log.w(TAG, "Unable to read EXIF tags for content URI asset");
            return null;
        }


        String attribute = mExifCompat.getAttribute(tagId);
        if (attribute == null || attribute.trim().isEmpty()) {
            return null;
        }

        return attribute.trim();
    }

    private void ensureExifInterface() {
        if (mExifCompat == null) {
            try (InputStream inputStream = openInputStream()) {
                if (inputStream != null) {
                    mExifCompat = new ExifInterfaceCompat(inputStream);
                }
            } catch (IOException e) {
                Log.w(TAG, "Couldn't read stream for " + mUri, e);
            }
        }

    }

    @Override
    protected InputStream openInputStream() {
        try {
            return mContext.getContentResolver().openInputStream(mUri);
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Image file not found", e);
            return null;
        }
    }

    @Override
    protected int getExifOrientation() {
        if (mExifOrientation != ExifInterfaceCompat.EXIF_ORIENTATION_UNKNOWN) {
            return mExifOrientation;
        }

        mExifOrientation = readExifOrientation();
        return mExifOrientation;
    }

    /**
     * Returns the EXIF rotation for the content URI asset. This method should only be called off
     * the main UI thread.
     */
    private int readExifOrientation() {
        ensureExifInterface();
        if (mExifCompat == null) {
            Log.w(TAG, "Unable to read EXIF rotation for content URI asset with content URI: "
                    + mUri);
            return ExifInterfaceCompat.EXIF_ORIENTATION_NORMAL;
        }

        return mExifCompat.getAttributeInt(ExifInterfaceCompat.TAG_ORIENTATION,
                ExifInterfaceCompat.EXIF_ORIENTATION_NORMAL);
    }

    @Override
    public void loadDrawable(Context context, ImageView imageView,
                             int placeholderColor) {
        Glide.with(context)
                .asDrawable()
                .load(mUri)
                .apply(mRequestOptions
                        .placeholder(new ColorDrawable(placeholderColor)))
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imageView);
    }

    @Override
    public void loadDrawableWithTransition(Context context, ImageView imageView,
            int transitionDurationMillis, @Nullable DrawableLoadedListener drawableLoadedListener,
            int placeholderColor) {
        Glide.with(context)
                .asDrawable()
                .load(mUri)
                .apply(mRequestOptions
                        .placeholder(new ColorDrawable(placeholderColor)))
                .transition(DrawableTransitionOptions.withCrossFade(transitionDurationMillis))
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(GlideException e, Object model,
                            Target<Drawable> target, boolean isFirstResource) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model,
                            Target<Drawable> target, DataSource dataSource,
                            boolean isFirstResource) {
                        if (drawableLoadedListener != null) {
                            drawableLoadedListener.onDrawableLoaded();
                        }
                        return false;
                    }
                })
                .into(imageView);
    }

    public Uri getUri() {
        return mUri;
    }

    /**
     * Custom AsyncTask which crops a bitmap region from a larger bitmap.
     */
    private static class BitmapCropTask extends AsyncTask<Void, Void, Bitmap> {

        private Bitmap mFromBitmap;
        private Rect mCropRect;
        private BitmapReceiver mReceiver;

        public BitmapCropTask(Bitmap fromBitmap, Rect cropRect, BitmapReceiver receiver) {
            mFromBitmap = fromBitmap;
            mCropRect = cropRect;
            mReceiver = receiver;
        }

        @Override
        protected Bitmap doInBackground(Void... unused) {
            if (mFromBitmap == null) {
                return null;
            }

            return Bitmap.createBitmap(
                    mFromBitmap, mCropRect.left, mCropRect.top, mCropRect.width(),
                    mCropRect.height());
        }

        @Override
        protected void onPostExecute(Bitmap bitmapRegion) {
            mReceiver.onBitmapDecoded(bitmapRegion);
        }
    }

}
