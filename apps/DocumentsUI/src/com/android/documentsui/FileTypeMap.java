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

package com.android.documentsui;

import android.content.ContentResolver;
import android.content.Context;

import com.android.documentsui.base.Lookup;

/**
 * A map from mime type to user friendly type string.
 */
public class FileTypeMap implements Lookup<String, String> {
    private final ContentResolver mResolver;

    FileTypeMap(Context context) {
        mResolver = context.getContentResolver();
    }

    @Override
    public String lookup(String mimeType) {
        if (mimeType == null) return null;
        return String.valueOf(mResolver.getTypeInfo(mimeType).getLabel());
    }
}
