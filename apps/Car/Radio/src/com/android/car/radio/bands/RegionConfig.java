/**
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

package com.android.car.radio.bands;

import android.hardware.radio.RadioManager.AmBandDescriptor;
import android.hardware.radio.RadioManager.BandDescriptor;
import android.hardware.radio.RadioManager.FmBandDescriptor;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.radio.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Regional configuration for all radio technologies wrapped in a single object.
 */
public final class RegionConfig implements Parcelable {
    private static final String TAG = "BcRadioApp.region";

    private final List<ProgramType> mSupported;
    private final List<BandDescriptor> mAmConfig;
    private final List<BandDescriptor> mFmConfig;

    public RegionConfig(@Nullable List<BandDescriptor> amFmConfig) {
        mAmConfig = new ArrayList<>();
        mFmConfig = new ArrayList<>();
        if (amFmConfig == null) {
            mSupported = new ArrayList<>();
            return;
        }

        for (BandDescriptor band : amFmConfig) {
            if (band instanceof AmBandDescriptor) {
                mAmConfig.add(band);
            } else if (band instanceof FmBandDescriptor) {
                mFmConfig.add(band);
            } else {
                Log.w(TAG, "Unknown band type: " + band);
            }
        }

        Comparator<BandDescriptor> cmp = (BandDescriptor a, BandDescriptor b) ->
                a.getLowerLimit() - b.getLowerLimit();
        Collections.sort(mAmConfig, cmp);
        Collections.sort(mFmConfig, cmp);
        mSupported = createSupportedProgramTypes();
    }

    private RegionConfig(@NonNull Parcel in) {
        mAmConfig = in.createTypedArrayList(BandDescriptor.CREATOR);
        mFmConfig = in.createTypedArrayList(BandDescriptor.CREATOR);
        mSupported = createSupportedProgramTypes();
    }

    @NonNull
    private List<ProgramType> createSupportedProgramTypes() {
        List<ProgramType> supported = new ArrayList<>();
        if (!mAmConfig.isEmpty()) supported.add(ProgramType.AM);
        if (!mFmConfig.isEmpty()) supported.add(ProgramType.FM);
        return supported;
    }

    @NonNull
    public List<ProgramType> getSupportedProgramTypes() {
        return mSupported;
    }

    @NonNull
    public List<BandDescriptor> getAmConfig() {
        return mAmConfig;
    }

    @NonNull
    public List<BandDescriptor> getFmConfig() {
        return mFmConfig;
    }

    @Override
    public String toString() {
        return "RegionConfig{AM=" + mAmConfig + ", FM=" + mFmConfig + "}";
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAmConfig, mFmConfig);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RegionConfig)) return false;
        RegionConfig other = (RegionConfig) obj;
        if (!mAmConfig.equals(other.mAmConfig)) return false;
        if (!mFmConfig.equals(other.mFmConfig)) return false;
        return true;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mAmConfig);
        dest.writeTypedList(mFmConfig);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<RegionConfig> CREATOR =
            new Parcelable.Creator<RegionConfig>() {
        public RegionConfig createFromParcel(Parcel in) {
            return new RegionConfig(in);
        }

        public RegionConfig[] newArray(int size) {
            return new RegionConfig[size];
        }
    };
}
