/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.pump.util;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

@AnyThread
public final class Scheme {
    private Scheme() { }

    private final static String FILE = ContentResolver.SCHEME_FILE;
    private final static String HTTP = "http";
    private final static String HTTPS = "https";

    public static boolean isFile(@NonNull Uri uri) {
        return FILE.equals(uri.getScheme());
    }

    public static boolean isHttp(@NonNull Uri uri) {
        return HTTP.equals(uri.getScheme());
    }

    public static boolean isHttps(@NonNull Uri uri) {
        return HTTPS.equals(uri.getScheme());
    }
}
