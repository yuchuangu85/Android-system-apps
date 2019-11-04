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

import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@AnyThread
public class Artist {
    private final long mId;

    // TODO(b/123706949) Lock mutable fields to ensure consistent updates
    private String mName;
    private String mDescription;
    private Uri mHeadshotUri;
    private final List<Album> mAlbums = new ArrayList<>();
    private final List<Audio> mAudios = new ArrayList<>();
    private boolean mLoaded;

    Artist(long id) {
        mId = id;
    }

    public long getId() {
        return mId;
    }

    public @Nullable String getName() {
        return mName;
    }

    public @NonNull List<Album> getAlbums() {
        return Collections.unmodifiableList(mAlbums);
    }

    public @NonNull List<Audio> getAudios() {
        return Collections.unmodifiableList(mAudios);
    }

    public @Nullable Uri getHeadshotUri() {
        return mHeadshotUri;
    }

    public @Nullable String getDescription() {
        return mDescription;
    }

    public boolean setHeadshotUri(@NonNull Uri headshotUri) {
        if (headshotUri.equals(mHeadshotUri)) {
            return false;
        }
        mHeadshotUri = headshotUri;
        return true;
    }

    public boolean setDescription(@NonNull String description) {
        if (description.equals(mDescription)) {
            return false;
        }
        mDescription = description;
        return true;
    }

    boolean setName(@NonNull String name) {
        if (name.equals(mName)) {
            return false;
        }
        mName = name;
        return true;
    }

    boolean addAlbum(@NonNull Album album) {
        if (mAlbums.contains(album)) {
            return false;
        }
        return mAlbums.add(album);
    }

    boolean addAudio(@NonNull Audio audio) {
        if (mAudios.contains(audio)) {
            return false;
        }
        return mAudios.add(audio);
    }

    boolean isLoaded() {
        return mLoaded;
    }

    void setLoaded() {
        mLoaded = true;
    }

    @Override
    public final boolean equals(@Nullable Object obj) {
        return obj instanceof Artist && mId == ((Artist) obj).mId;
    }

    @Override
    public final int hashCode() {
        return (int) (mId ^ (mId >>> 32));
    }
}
