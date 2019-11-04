/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.phone;

import android.net.Uri;
import android.text.TextUtils;

/**
 * TODO: Not much of this class is even used any more.  Need to unwind the RawGatewayInfo class as
 * it is used in part of the placeCall method used in OTASP.
 */
public class CallGatewayManager {
    public static final RawGatewayInfo EMPTY_INFO = new RawGatewayInfo(null, null, null);

    private CallGatewayManager() {
    }

    public static class RawGatewayInfo {
        public String packageName;
        public Uri gatewayUri;
        public String trueNumber;

        public RawGatewayInfo(String packageName, Uri gatewayUri,
                String trueNumber) {
            this.packageName = packageName;
            this.gatewayUri = gatewayUri;
            this.trueNumber = trueNumber;
        }

        public boolean isEmpty() {
            return TextUtils.isEmpty(packageName) || gatewayUri == null;
        }
    }
}
