/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.apps.common;


import android.annotation.NonNull;
import android.content.Context;
import android.content.res.Resources;
import android.os.SystemProperties;

/** Singleton class regrouping common library feature flags. */
public class CommonFlags {

    private static final String FLAG_IMPROPER_IMAGE_REFS_KEY =
            "com.android.car.apps.common.FlagNonLocalImages";

    @SuppressWarnings("StaticFieldLeak") // We store the application context, not an activity.
    private static CommonFlags sInstance;

    /** Returns the singleton. */
    public static CommonFlags getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new CommonFlags(context);
        }
        return sInstance;
    }

    private final Context mApplicationContext;
    private Boolean mFlagImproperImageRefs;

    private CommonFlags(@NonNull Context context) {
        mApplicationContext = context.getApplicationContext();
    }

    /**
     * Returns whether improper image references should be flagged (typically tinting the images
     * in red with {@link R.color.improper_image_refs_tint_color}. This special mode is intended for
     * third party app developers so they can notice quickly that they are sending improper image
     * references. Such references include :
     * <li>remote image instead of a local content uri</li>
     * <li>bitmap sent over the binder instead of a local content uri</li>
     * <li>bitmap icon instead of a vector drawable</li>
     * <p/>
     *
     * To activate, either overlay R.bool.flag_improper_image_references to true, or use adb:
     * <code>
     *     adb root
     *     adb shell setprop com.android.car.apps.common.FlagNonLocalImages 1
     *     adb shell am force-stop APP_PACKAGE # eg: APP_PACKAGE= com.android.car.media
     * </code>
     */
    public boolean shouldFlagImproperImageRefs() {
        if (mFlagImproperImageRefs == null) {
            Resources res = mApplicationContext.getResources();
            mFlagImproperImageRefs = res.getBoolean(R.bool.flag_improper_image_references)
                    || "1".equals(SystemProperties.get(FLAG_IMPROPER_IMAGE_REFS_KEY, "0"));
        }
        return mFlagImproperImageRefs;
    }
}
