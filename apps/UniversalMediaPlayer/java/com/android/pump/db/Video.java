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

package com.android.pump.db;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@AnyThread
public abstract class Video {
    private final long mId;
    private final String mMimeType;

    Video(long id, @NonNull String mimeType) {
        mId = id;
        mMimeType = mimeType;
    }

    public long getId() {
        return mId;
    }

    public @NonNull String getMimeType() {
        return mMimeType;
    }

    @Override
    public final boolean equals(@Nullable Object obj) {
        return obj instanceof Video && mId == ((Video) obj).mId;
    }

    @Override
    public final int hashCode() {
        return (int) (mId ^ (mId >>> 32));
    }
}
