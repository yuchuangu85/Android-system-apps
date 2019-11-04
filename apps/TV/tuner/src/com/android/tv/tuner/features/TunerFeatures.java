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
 * limitations under the License
 */

package com.android.tv.tuner.features;

import static com.android.tv.common.feature.FeatureUtils.OFF;

import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.common.feature.Feature;
import com.android.tv.common.feature.Model;
import com.android.tv.common.feature.PropertyFeature;
import com.android.tv.common.feature.Sdk;

/**
 * List of {@link Feature} for Tuner.
 *
 * <p>Only for use in Tuners.
 *
 * <p>Remove the {@code Feature} once it is launched.
 */
public class TunerFeatures extends CommonFeatures {

    /**
     * USE_SW_CODEC_FOR_SD
     *
     * <p>Prefer software based codec for SD channels.
     */
    public static final Feature USE_SW_CODEC_FOR_SD =
            PropertyFeature.create(
                    "use_sw_codec_for_sd",
                    false
                    );

    /**
     * Does the TvProvider on the installed device allow systems inserts to the programs table.
     *
     * <p>This is available in {@link Sdk#AT_LEAST_O} but vendors may choose to backport support to
     * the TvProvider.
     */
    public static final Feature TVPROVIDER_ALLOWS_COLUMN_CREATION = Sdk.AT_LEAST_O;

    /** Enable Dvb parsers and listeners. */
    public static final Feature ENABLE_FILE_DVB = OFF;

    private TunerFeatures() {}
}
