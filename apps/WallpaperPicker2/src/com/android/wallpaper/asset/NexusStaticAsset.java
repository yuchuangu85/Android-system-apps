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

import android.content.res.Resources;

import com.bumptech.glide.load.Key;

/**
 * Image asset representing a Nexus stub APK resource.
 */
public final class NexusStaticAsset extends ResourceAsset {
    private final String mResName;

    /**
     * @param res   Resources containing the asset.
     * @param resId Resource ID referencing the asset.
     */
    public NexusStaticAsset(Resources res, int resId, String resName) {
        super(res, resId);
        mResName = resName;
    }

    @Override
    public Key getKey() {
        if (mKey == null) {
            mKey = new PackageResourceKey(mRes, mResId, mResName);
        }
        return mKey;
    }

    /**
     * Glide caching key for resources from Nexus stub APK.
     */
    private static class PackageResourceKey extends ResourceAsset.PackageResourceKey {
        private String mResName;

        public PackageResourceKey(Resources res, int resId, String resName) {
            super(res, resId);
            mResName = resName;
        }

        @Override
        protected String getCacheKey() {
            return "PackageResourceKey{"
                    + "packageName=" + mPackageName
                    + ",resId=" + mResId
                    + ",resName=" + mResName
                    + '}';
        }
    }
}
