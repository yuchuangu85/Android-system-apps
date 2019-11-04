/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.common.feature;

import android.content.Context;
import com.android.tv.common.BuildConfig;
import com.android.tv.common.util.CommonUtils;
import java.util.Arrays;

/** Static utilities for features. */
public class FeatureUtils {

    /**
     * Returns a feature that is enabled if any of {@code features} is enabled.
     *
     * @param features the features to or
     */
    public static Feature or(final Feature... features) {
        return new Feature() {
            @Override
            public boolean isEnabled(Context context) {
                for (Feature f : features) {
                    if (f.isEnabled(context)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String toString() {
                return "or(" + Arrays.asList(features) + ")";
            }
        };
    }

    /**
     * Returns a feature that is enabled if all of {@code features} is enabled.
     *
     * @param features the features to and
     */
    public static Feature and(final Feature... features) {
        return new Feature() {
            @Override
            public boolean isEnabled(Context context) {
                for (Feature f : features) {
                    if (!f.isEnabled(context)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public String toString() {
                return "and(" + Arrays.asList(features) + ")";
            }
        };
    }
    /**
     * A feature available in AOSP.
     *
     * @param googleFeature the feature used in non AOSP builds
     * @param aospFeature the feature used in AOSP builds
     */
    public static Feature aospFeature(
// AOSP_Comment_Out             final Feature googleFeature,
            final Feature aospFeature) {
        /* Begin_AOSP_Comment_Out
        if (!BuildConfig.AOSP) {
            return googleFeature;
        } else {
            End_AOSP_Comment_Out */
            return aospFeature;
// AOSP_Comment_Out         }
    }

    /**
     * Returns a feature that is opposite of the given {@code feature}.
     *
     * @param feature the feature to invert
     */
    public static Feature not(final Feature feature) {
        return new Feature() {
            @Override
            public boolean isEnabled(Context context) {
                return !feature.isEnabled(context);
            }

            @Override
            public String toString() {
                return "not(" + feature + ")";
            }
        };
    }

    /** A feature that is always enabled. */
    public static final Feature ON =
            new Feature() {
                @Override
                public boolean isEnabled(Context context) {
                    return true;
                }

                @Override
                public String toString() {
                    return "on";
                }
            };

    /** A feature that is always disabled. */
    public static final Feature OFF =
            new Feature() {
                @Override
                public boolean isEnabled(Context context) {
                    return false;
                }

                @Override
                public String toString() {
                    return "off";
                }
            };

    /** True if running in robolectric. */
    public static final Feature ROBOLECTRIC =
            new Feature() {
                @Override
                public boolean isEnabled(Context context) {
                    return CommonUtils.isRoboTest();
                }

                @Override
                public String toString() {
                    return "isRobolecteric";
                }
            };

    private FeatureUtils() {}
}
