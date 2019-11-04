/*
 * Copyright 2015 The Android Open Source Project
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

package com.android.tv.common;

/** Constants for common use in apps and tests. */
public final class CommonConstants {

    @Deprecated // TODO(b/121158110) refactor so this is not needed.
    public static final String BASE_PACKAGE =
// AOSP_Comment_Out             !BuildConfig.AOSP
// AOSP_Comment_Out                     ? "com.google.android.tv"
// AOSP_Comment_Out                     :
                    "com.android.tv";
    /** A constant for the key of the extra data for the app linking intent. */
    public static final String EXTRA_APP_LINK_CHANNEL_URI = "app_link_channel_uri";

    /**
     * Video is unavailable because the source is not physically connected, for example the HDMI
     * cable is not connected.
     */
    public static final int VIDEO_UNAVAILABLE_REASON_NOT_CONNECTED = 5;

    private CommonConstants() {}
}
