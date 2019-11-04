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

package com.android.tv.features;

import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import com.android.tv.common.feature.Feature;
import com.google.android.tv.partner.support.PartnerCustomizations;

/** Features backed by {@link PartnerCustomizations}. */
@SuppressWarnings("AndroidApiChecker") // TODO(b/32513850) remove when error prone is updated
public final class PartnerFeatures {

    public static final Feature TVPROVIDER_ALLOWS_SYSTEM_INSERTS_TO_PROGRAM_TABLE =
            new PartnerFeature(
                    PartnerCustomizations.TVPROVIDER_ALLOWS_SYSTEM_INSERTS_TO_PROGRAM_TABLE);

    public static final Feature TURN_OFF_EMBEDDED_TUNER =
            new PartnerFeature(PartnerCustomizations.TURN_OFF_EMBEDDED_TUNER);

    public static final Feature TVPROVIDER_ALLOWS_COLUMN_CREATION =
            new PartnerFeature(PartnerCustomizations.TVPROVIDER_ALLOWS_COLUMN_CREATION);

    private static class PartnerFeature implements Feature {

        private final String property;

        public PartnerFeature(String property) {
            this.property = property;
        }

        @Override
        public boolean isEnabled(Context context) {
            if (VERSION.SDK_INT >= VERSION_CODES.N) {
                PartnerCustomizations partnerCustomizations = new PartnerCustomizations(context);
                return partnerCustomizations.getBooleanResource(context, property).orElse(false);
            }
            return false;
        }
    }

    private PartnerFeatures() {}
}
