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
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;

import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Image asset representing an APK resource.
 */
public class ResourceAsset extends StreamableAsset {
    protected final Resources mRes;
    protected final int mResId;
    private final RequestOptions mRequestOptions;

    protected Key mKey;

    /**
     * @param res   Resources containing the asset.
     * @param resId Resource ID referencing the asset.
     * @param requestOptions {@link RequestOptions} to be applied when loading the asset.
     */
    public ResourceAsset(Resources res, int resId, RequestOptions requestOptions) {
        mRes = res;
        mResId = resId;
        mRequestOptions = requestOptions;
    }

    /**
     * @param res   Resources containing the asset.
     * @param resId Resource ID referencing the asset.
     */
    public ResourceAsset(Resources res, int resId) {
        this(res, resId, RequestOptions.centerCropTransform());
    }

    @Override
    public void loadDrawable(Context context, ImageView imageView,
                             int placeholderColor) {
        Glide.with(context)
                .asDrawable()
                .load(ResourceAsset.this)
                .apply(mRequestOptions
                        .placeholder(new ColorDrawable(placeholderColor)))
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imageView);
    }

    @Override
    public int hashCode() {
        return getKey().hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof ResourceAsset) {
            ResourceAsset otherAsset = (ResourceAsset) object;
            return this.getKey().equals(otherAsset.getKey());
        }
        return false;
    }

    /**
     * Returns a Glide Key used to uniquely identify this asset as a data source in the cache.
     */
    public Key getKey() {
        if (mKey == null) {
            mKey = new PackageResourceKey(mRes, mResId);
        }
        return mKey;
    }

    /**
     * Returns the Resources instance for the resource represented by this asset.
     */
    Resources getResources() {
        return mRes;
    }

    /**
     * Returns the resource ID for the resource represented by this asset.
     */
    int getResId() {
        return mResId;
    }

    @Override
    protected InputStream openInputStream() {
        return mRes.openRawResource(mResId);
    }

    /**
     * Glide caching key for resources from any arbitrary package.
     */
    protected static class PackageResourceKey implements Key {
        protected String mPackageName;
        protected int mResId;

        public PackageResourceKey(Resources res, int resId) {
            mPackageName = res.getResourcePackageName(resId);
            mResId = resId;
        }

        @Override
        public String toString() {
            return getCacheKey();
        }

        @Override
        public int hashCode() {
            return getCacheKey().hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof PackageResourceKey) {
                PackageResourceKey otherKey = (PackageResourceKey) object;
                return getCacheKey().equals(otherKey.getCacheKey());
            }
            return false;
        }

        @Override
        public void updateDiskCacheKey(MessageDigest messageDigest) {
            messageDigest.update(getCacheKey().getBytes(CHARSET));
        }

        /**
         * Returns an inexpensively calculated {@link String} suitable for use as a disk cache key.
         */
        protected String getCacheKey() {
            return "PackageResourceKey{"
                    + "packageName=" + mPackageName
                    + ",resId=" + mResId
                    + '}';
        }
    }
}
