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

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.AsyncTask;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

/**
 * Interface representing an image asset.
 */
public abstract class Asset {

    /**
     * Creates and returns a placeholder Drawable instance sized exactly to the target ImageView and
     * filled completely with pixels of the provided placeholder color.
     */
    protected static Drawable getPlaceholderDrawable(
            Context context, ImageView imageView, int placeholderColor) {
        Point imageViewDimensions = getImageViewDimensions(imageView);
        Bitmap placeholderBitmap =
                Bitmap.createBitmap(imageViewDimensions.x, imageViewDimensions.y, Config.ARGB_8888);
        placeholderBitmap.eraseColor(placeholderColor);
        return new BitmapDrawable(context.getResources(), placeholderBitmap);
    }

    /**
     * Returns the visible height and width in pixels of the provided ImageView, or if it hasn't been
     * laid out yet, then gets the absolute value of the layout params.
     */
    private static Point getImageViewDimensions(ImageView imageView) {
        int width = imageView.getWidth() > 0
                ? imageView.getWidth()
                : Math.abs(imageView.getLayoutParams().width);
        int height = imageView.getHeight() > 0
                ? imageView.getHeight()
                : Math.abs(imageView.getLayoutParams().height);

        return new Point(width, height);
    }

    /**
     * Decodes a bitmap sized for the destination view's dimensions off the main UI thread.
     *
     * @param targetWidth  Width of target view in physical pixels.
     * @param targetHeight Height of target view in physical pixels.
     * @param receiver     Called with the decoded bitmap or null if there was an error decoding the
     *                     bitmap.
     */
    public abstract void decodeBitmap(int targetWidth, int targetHeight, BitmapReceiver receiver);

    /**
     * Decodes and downscales a bitmap region off the main UI thread.
     *
     * @param rect         Rect representing the crop region in terms of the original image's resolution.
     * @param targetWidth  Width of target view in physical pixels.
     * @param targetHeight Height of target view in physical pixels.
     * @param receiver     Called with the decoded bitmap region or null if there was an error decoding
     *                     the bitmap region.
     */
    public abstract void decodeBitmapRegion(Rect rect, int targetWidth, int targetHeight,
                                            BitmapReceiver receiver);

    /**
     * Calculates the raw dimensions of the asset at its original resolution off the main UI thread.
     * Avoids decoding the entire bitmap if possible to conserve memory.
     *
     * @param activity Activity in which this decoding request is made. Allows for early termination
     *                 of fetching image data and/or decoding to a bitmap. May be null, in which case the request
     *                 is made in the application context instead.
     * @param receiver Called with the decoded raw dimensions of the whole image or null if there was
     *                 an error decoding the dimensions.
     */
    public abstract void decodeRawDimensions(@Nullable Activity activity,
                                             DimensionsReceiver receiver);

    /**
     * Returns whether this asset has access to a separate, lower fidelity source of image data (that
     * may be able to be loaded more quickly to simulate progressive loading).
     */
    public boolean hasLowResDataSource() {
        return false;
    }

    /**
     * Loads the asset from the separate low resolution data source (if there is one) into the
     * provided ImageView with the placeholder color and bitmap transformation.
     *
     * @param transformation Bitmap transformation that can transform the thumbnail image
     *                       post-decoding.
     */
    public void loadLowResDrawable(Activity activity, ImageView imageView, int placeholderColor,
                                   BitmapTransformation transformation) {
        // No op
    }

    /**
     * Returns whether the asset supports rendering tile regions at varying pixel densities.
     */
    public abstract boolean supportsTiling();

    /**
     * Loads a Drawable for this asset into the provided ImageView. While waiting for the image to
     * load, first loads a ColorDrawable based on the provided placeholder color.
     *  @param context         Activity hosting the ImageView.
     * @param imageView        ImageView which is the target view of this asset.
     * @param placeholderColor Color of placeholder set to ImageView while waiting for image to load.
     */
    public void loadDrawable(final Context context, final ImageView imageView,
                             int placeholderColor) {
        // Transition from a placeholder ColorDrawable to the decoded bitmap when the ImageView in
        // question is empty.
        final boolean needsTransition = imageView.getDrawable() == null;
        final Drawable placeholderDrawable = new ColorDrawable(placeholderColor);
        if (needsTransition) {
            imageView.setImageDrawable(placeholderDrawable);
        }

        // Set requested height and width to the either the actual height and width of the view in
        // pixels, or if it hasn't been laid out yet, then to the absolute value of the layout params.
        int width = imageView.getWidth() > 0
                ? imageView.getWidth()
                : Math.abs(imageView.getLayoutParams().width);
        int height = imageView.getHeight() > 0
                ? imageView.getHeight()
                : Math.abs(imageView.getLayoutParams().height);

        decodeBitmap(width, height, new BitmapReceiver() {
            @Override
            public void onBitmapDecoded(Bitmap bitmap) {
                if (!needsTransition) {
                    imageView.setImageBitmap(bitmap);
                    return;
                }

                Resources resources = context.getResources();

                Drawable[] layers = new Drawable[2];
                layers[0] = placeholderDrawable;
                layers[1] = new BitmapDrawable(resources, bitmap);

                TransitionDrawable transitionDrawable = new TransitionDrawable(layers);
                transitionDrawable.setCrossFadeEnabled(true);

                imageView.setImageDrawable(transitionDrawable);
                transitionDrawable.startTransition(resources.getInteger(
                        android.R.integer.config_shortAnimTime));
            }
        });
    }

    /**
     * Loads a Drawable for this asset into the provided ImageView, providing a crossfade transition
     * with the given duration from the Drawable previously set on the ImageView.
     * @param context                 Activity hosting the ImageView.
     * @param imageView                ImageView which is the target view of this asset.
     * @param transitionDurationMillis Duration of the crossfade, in milliseconds.
     * @param drawableLoadedListener   Listener called once the transition has begun.
     * @param placeholderColor         Color of the placeholder if the provided ImageView is empty before the
     */
    public void loadDrawableWithTransition(
            final Context context,
            final ImageView imageView,
            final int transitionDurationMillis,
            @Nullable final DrawableLoadedListener drawableLoadedListener,
            int placeholderColor) {
        Point imageViewDimensions = getImageViewDimensions(imageView);

        // Transition from a placeholder ColorDrawable to the decoded bitmap when the ImageView in
        // question is empty.
        boolean needsPlaceholder = imageView.getDrawable() == null;
        if (needsPlaceholder) {
            imageView.setImageDrawable(getPlaceholderDrawable(context, imageView, placeholderColor));
        }

        decodeBitmap(imageViewDimensions.x, imageViewDimensions.y, new BitmapReceiver() {
            @Override
            public void onBitmapDecoded(Bitmap bitmap) {
                final Resources resources = context.getResources();

                new CenterCropBitmapTask(bitmap, imageView, new BitmapReceiver() {
                    @Override
                    public void onBitmapDecoded(@Nullable Bitmap newBitmap) {
                        Drawable[] layers = new Drawable[2];
                        Drawable existingDrawable = imageView.getDrawable();

                        if (existingDrawable instanceof TransitionDrawable) {
                            // Take only the second layer in the existing TransitionDrawable so we don't keep
                            // around a reference to older layers which are no longer shown (this way we avoid a
                            // memory leak).
                            TransitionDrawable existingTransitionDrawable =
                                    (TransitionDrawable) existingDrawable;
                            int id = existingTransitionDrawable.getId(1);
                            layers[0] = existingTransitionDrawable.findDrawableByLayerId(id);
                        } else {
                            layers[0] = existingDrawable;
                        }
                        layers[1] = new BitmapDrawable(resources, newBitmap);

                        TransitionDrawable transitionDrawable = new TransitionDrawable(layers);
                        transitionDrawable.setCrossFadeEnabled(true);

                        imageView.setImageDrawable(transitionDrawable);
                        transitionDrawable.startTransition(transitionDurationMillis);

                        if (drawableLoadedListener != null) {
                            drawableLoadedListener.onDrawableLoaded();
                        }
                    }
                }).execute();
            }
        });
    }

    /**
     * Interface for receiving decoded Bitmaps.
     */
    public interface BitmapReceiver {

        /**
         * Called with a decoded Bitmap object or null if there was an error decoding the bitmap.
         */
        void onBitmapDecoded(@Nullable Bitmap bitmap);
    }

    /**
     * Interface for receiving raw asset dimensions.
     */
    public interface DimensionsReceiver {

        /**
         * Called with raw dimensions of asset or null if the asset is unable to decode the raw
         * dimensions.
         *
         * @param dimensions Dimensions as a Point where width is represented by "x" and height by "y".
         */
        void onDimensionsDecoded(@Nullable Point dimensions);
    }

    /**
     * Interface for being notified when a drawable has been loaded.
     */
    public interface DrawableLoadedListener {
        void onDrawableLoaded();
    }

    /**
     * Custom AsyncTask which returns a copy of the given bitmap which is center cropped and scaled to
     * fit in the given ImageView.
     */
    protected static class CenterCropBitmapTask extends AsyncTask<Void, Void, Bitmap> {

        private Bitmap mBitmap;
        private BitmapReceiver mBitmapReceiver;

        private int mImageViewWidth;
        private int mImageViewHeight;

        public CenterCropBitmapTask(Bitmap bitmap, ImageView imageView,
                                    BitmapReceiver bitmapReceiver) {
            mBitmap = bitmap;
            mBitmapReceiver = bitmapReceiver;

            Point imageViewDimensions = getImageViewDimensions(imageView);

            mImageViewWidth = imageViewDimensions.x;
            mImageViewHeight = imageViewDimensions.y;
        }

        @Override
        protected Bitmap doInBackground(Void... unused) {
            int measuredWidth = mImageViewWidth;
            int measuredHeight = mImageViewHeight;

            int bitmapWidth = mBitmap.getWidth();
            int bitmapHeight = mBitmap.getHeight();

            float scale = Math.min(
                    (float) bitmapWidth / measuredWidth,
                    (float) bitmapHeight / measuredHeight);

            Bitmap scaledBitmap = Bitmap.createScaledBitmap(
                    mBitmap, Math.round(bitmapWidth / scale), Math.round(bitmapHeight / scale), true);

            int horizontalGutterPx = Math.max(0, (scaledBitmap.getWidth() - measuredWidth) / 2);
            int verticalGutterPx = Math.max(0, (scaledBitmap.getHeight() - measuredHeight) / 2);

            return Bitmap.createBitmap(
                    scaledBitmap,
                    horizontalGutterPx,
                    verticalGutterPx,
                    scaledBitmap.getWidth() - (2 * horizontalGutterPx),
                    scaledBitmap.getHeight() - (2 * verticalGutterPx));
        }

        @Override
        protected void onPostExecute(Bitmap newBitmap) {
            mBitmapReceiver.onBitmapDecoded(newBitmap);
        }
    }
}
