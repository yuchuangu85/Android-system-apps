/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.documentsui.picker;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.documentsui.MetricConsts;

public class PickResult implements android.os.Parcelable {
    private int mActionCount;
    private long mDuration;
    private int mFileCount;
    private boolean mIsSearching;
    private @MetricConsts.Root int mRoot;
    private @MetricConsts.Mime int mMimeType;
    private int mRepeatedPickTimes;

    // only used for single-select case to get the mRepeatedPickTimes and mMimeType
    private Uri mFileUri;
    private long mPickStartTime;

    /**
     * get total action count during picking.
     *
     * @return action count
     */
    public int getActionCount() {
        return mActionCount;
    }

    /**
     * increase action count.
     */
    public void increaseActionCount() {
        mActionCount++;
    }

    /**
     * get pick duration
     *
     * @return pick duration
     */
    public long getDuration() {
        return mDuration;
    }

    /**
     * increase pick duration.
     *
     * @param currentMillis current time millis
     */
    public void increaseDuration(long currentMillis) {
        mDuration += currentMillis - mPickStartTime;
        setPickStartTime(currentMillis);
    }

    /**
     * set the pick start time.
     *
     * @param millis
     */
    public void setPickStartTime(long millis) {
        mPickStartTime = millis;
    }

    /**
     * get number of files picked.
     *
     * @return file count
     */
    public int getFileCount() {
        return mFileCount;
    }

    /**
     * set number of files picked.
     *
     * @param count
     */
    public void setFileCount(int count) {
        mFileCount = count;
    }

    /**
     * check whether this pick is under searching.
     *
     * @return under searching or not
     */
    public boolean isSearching() {
        return mIsSearching;
    }

    /**
     * set whether this pick is under searching.
     *
     * @param isSearching
     */
    public void setIsSearching(boolean isSearching) {
        this.mIsSearching = isSearching;
    }

    /**
     * get the root where the file is picked.
     *
     * @return root
     */
    public int getRoot() {
        return mRoot;
    }

    /**
     * set the root where the file is picked.
     *
     * @param root
     */
    public void setRoot(@MetricConsts.Root int root) {
        this.mRoot = root;
    }

    /**
     * get the mime type of the pick file.
     *
     * @return mime type
     */
    public int getMimeType() {
        return mMimeType;
    }

    /**
     * set the mime type of the pick file.
     *
     * @param mimeType
     */
    public void setMimeType(@MetricConsts.Mime int mimeType) {
        this.mMimeType = mimeType;
    }

    /**
     * get number of time the selected file is picked repeatedly.
     *
     * @return repeatedly pick count
     */
    public int getRepeatedPickTimes() {
        return mRepeatedPickTimes;
    }

    /**
     * set number of time the selected file is picked repeatedly.
     *
     * @param times the repeatedly pick times
     */
    public void setRepeatedPickTimes(int times) {
        mRepeatedPickTimes = times;
    }

    /**
     * get the uri of the selected doc.
     *
     * @return file uri
     */
    public Uri getFileUri() {
        return mFileUri;
    }

    /**
     * set the uri of the selected doc.
     *
     * @param fileUri the selected doc uri
     */
    public void setFileUri(Uri fileUri) {
        this.mFileUri = fileUri;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mActionCount);
        out.writeLong(mDuration);
        out.writeInt(mFileCount);
        out.writeInt(mIsSearching ? 1 : 0);
        out.writeInt(mRoot);
        out.writeInt(mMimeType);
        out.writeInt(mRepeatedPickTimes);
    }

    public static final Parcelable.ClassLoaderCreator<PickResult>
            CREATOR = new Parcelable.ClassLoaderCreator<PickResult>() {
        @Override
        public PickResult createFromParcel(Parcel in) {
            return createFromParcel(in, null);
        }

        @Override
        public PickResult createFromParcel(Parcel in, ClassLoader loader) {
            final PickResult result = new PickResult();
            result.mActionCount = in.readInt();
            result.mDuration = in.readLong();
            result.mFileCount = in.readInt();
            result.mIsSearching = in.readInt() != 0;
            result.mRoot = in.readInt();
            result.mMimeType = in.readInt();
            result.mRepeatedPickTimes = in.readInt();
            return result;
        }

        @Override
        public PickResult[] newArray(int size) {
            return new PickResult[size];
        }
    };

    @Override
    public String toString() {
        return "PickResults{" +
                "actionCount=" + mActionCount +
                ", mDuration=" + mDuration +
                ", mFileCount=" + mFileCount +
                ", mIsSearching=" + mIsSearching +
                ", mRoot=" + mRoot +
                ", mMimeType=" + mMimeType +
                ", mRepeatedPickTimes=" + mRepeatedPickTimes +
                '}';
    }
}
