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
package com.android.car.audio;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.media.CarAudioManager;
import android.content.ContentResolver;
import android.content.Context;
import android.hardware.automotive.audiocontrol.V1_0.ContextNumber;
import android.media.AudioDevicePort;
import android.provider.Settings;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.internal.util.Preconditions;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A class encapsulates a volume group in car.
 *
 * Volume in a car is controlled by group. A group holds one or more car audio contexts.
 * Call {@link CarAudioManager#getVolumeGroupCount()} to get the count of {@link CarVolumeGroup}
 * supported in a car.
 */
/* package */ final class CarVolumeGroup {

    private final ContentResolver mContentResolver;
    private final int mZoneId;
    private final int mId;
    private final SparseIntArray mContextToBus = new SparseIntArray();
    private final SparseArray<CarAudioDeviceInfo> mBusToCarAudioDeviceInfo = new SparseArray<>();

    private int mDefaultGain = Integer.MIN_VALUE;
    private int mMaxGain = Integer.MIN_VALUE;
    private int mMinGain = Integer.MAX_VALUE;
    private int mStepSize = 0;
    private int mStoredGainIndex;
    private int mCurrentGainIndex = -1;

    /**
     * Constructs a {@link CarVolumeGroup} instance
     * @param context {@link Context} instance
     * @param zoneId Audio zone this volume group belongs to
     * @param id ID of this volume group
     */
    CarVolumeGroup(Context context, int zoneId, int id) {
        mContentResolver = context.getContentResolver();
        mZoneId = zoneId;
        mId = id;
        mStoredGainIndex = Settings.Global.getInt(mContentResolver,
                CarAudioService.getVolumeSettingsKeyForGroup(mZoneId, mId), -1);
    }

    /**
     * Constructs a {@link CarVolumeGroup} instance
     * @param context {@link Context} instance
     * @param zoneId Audio zone this volume group belongs to
     * @param id ID of this volume group
     * @param contexts Pre-populated array of car contexts, for legacy car_volume_groups.xml only
     * @deprecated In favor of {@link #CarVolumeGroup(Context, int, int)}
     */
    @Deprecated
    CarVolumeGroup(Context context, int zoneId, int id, @NonNull int[] contexts) {
        this(context, zoneId, id);
        // Deal with the pre-populated car audio contexts
        for (int audioContext : contexts) {
            mContextToBus.put(audioContext, -1);
        }
    }

    /**
     * @param busNumber Physical bus number for the audio device port
     * @return {@link CarAudioDeviceInfo} associated with a given bus number
     */
    CarAudioDeviceInfo getCarAudioDeviceInfoForBus(int busNumber) {
        return mBusToCarAudioDeviceInfo.get(busNumber);
    }

    /**
     * @return Array of context numbers in this {@link CarVolumeGroup}
     */
    int[] getContexts() {
        final int[] contextNumbers = new int[mContextToBus.size()];
        for (int i = 0; i < contextNumbers.length; i++) {
            contextNumbers[i] = mContextToBus.keyAt(i);
        }
        return contextNumbers;
    }

    /**
     * @param busNumber Physical bus number for the audio device port
     * @return Array of context numbers assigned to a given bus number
     */
    int[] getContextsForBus(int busNumber) {
        List<Integer> contextNumbers = new ArrayList<>();
        for (int i = 0; i < mContextToBus.size(); i++) {
            int value = mContextToBus.valueAt(i);
            if (value == busNumber) {
                contextNumbers.add(mContextToBus.keyAt(i));
            }
        }
        return contextNumbers.stream().mapToInt(i -> i).toArray();
    }

    /**
     * @return Array of bus numbers in this {@link CarVolumeGroup}
     */
    int[] getBusNumbers() {
        final int[] busNumbers = new int[mBusToCarAudioDeviceInfo.size()];
        for (int i = 0; i < busNumbers.length; i++) {
            busNumbers[i] = mBusToCarAudioDeviceInfo.keyAt(i);
        }
        return busNumbers;
    }

    /**
     * Binds the context number to physical bus number and audio device port information.
     * Because this may change the groups min/max values, thus invalidating an index computed from
     * a gain before this call, all calls to this function must happen at startup before any
     * set/getGainIndex calls.
     *
     * @param contextNumber Context number as defined in audio control HAL
     * @param busNumber Physical bus number for the audio device port
     * @param info {@link CarAudioDeviceInfo} instance relates to the physical bus
     */
    void bind(int contextNumber, int busNumber, CarAudioDeviceInfo info) {
        if (mBusToCarAudioDeviceInfo.size() == 0) {
            mStepSize = info.getAudioGain().stepValue();
        } else {
            Preconditions.checkArgument(
                    info.getAudioGain().stepValue() == mStepSize,
                    "Gain controls within one group must have same step value");
        }

        mContextToBus.put(contextNumber, busNumber);
        mBusToCarAudioDeviceInfo.put(busNumber, info);

        if (info.getDefaultGain() > mDefaultGain) {
            // We're arbitrarily selecting the highest bus default gain as the group's default.
            mDefaultGain = info.getDefaultGain();
        }
        if (info.getMaxGain() > mMaxGain) {
            mMaxGain = info.getMaxGain();
        }
        if (info.getMinGain() < mMinGain) {
            mMinGain = info.getMinGain();
        }
        if (mStoredGainIndex < getMinGainIndex() || mStoredGainIndex > getMaxGainIndex()) {
            // We expected to load a value from last boot, but if we didn't (perhaps this is the
            // first boot ever?), then use the highest "default" we've seen to initialize
            // ourselves.
            mCurrentGainIndex = getIndexForGain(mDefaultGain);
        } else {
            // Just use the gain index we stored last time the gain was set (presumably during our
            // last boot cycle).
            mCurrentGainIndex = mStoredGainIndex;
        }
    }

    private int getDefaultGainIndex() {
        return getIndexForGain(mDefaultGain);
    }

    int getMaxGainIndex() {
        return getIndexForGain(mMaxGain);
    }

    int getMinGainIndex() {
        return getIndexForGain(mMinGain);
    }

    int getCurrentGainIndex() {
        return mCurrentGainIndex;
    }

    /**
     * Sets the gain on this group, gain will be set on all buses within same bus.
     * @param gainIndex The gain index
     */
    void setCurrentGainIndex(int gainIndex) {
        int gainInMillibels = getGainForIndex(gainIndex);

        Preconditions.checkArgument(
                gainInMillibels >= mMinGain && gainInMillibels <= mMaxGain,
                "Gain out of range ("
                        + mMinGain + ":"
                        + mMaxGain + ") "
                        + gainInMillibels + "index "
                        + gainIndex);

        for (int i = 0; i < mBusToCarAudioDeviceInfo.size(); i++) {
            CarAudioDeviceInfo info = mBusToCarAudioDeviceInfo.valueAt(i);
            info.setCurrentGain(gainInMillibels);
        }

        mCurrentGainIndex = gainIndex;
        Settings.Global.putInt(mContentResolver,
                CarAudioService.getVolumeSettingsKeyForGroup(mZoneId, mId), gainIndex);
    }

    // Given a group level gain index, return the computed gain in millibells
    // TODO (randolphs) If we ever want to add index to gain curves other than lock-stepped
    // linear, this would be the place to do it.
    private int getGainForIndex(int gainIndex) {
        return mMinGain + gainIndex * mStepSize;
    }

    // TODO (randolphs) if we ever went to a non-linear index to gain curve mapping, we'd need to
    // revisit this as it assumes (at the least) that getGainForIndex is reversible.  Luckily,
    // this is an internal implementation details we could factor out if/when necessary.
    private int getIndexForGain(int gainInMillibel) {
        return (gainInMillibel - mMinGain) / mStepSize;
    }

    /**
     * Gets {@link AudioDevicePort} from a context number
     */
    @Nullable
    AudioDevicePort getAudioDevicePortForContext(int contextNumber) {
        final int busNumber = mContextToBus.get(contextNumber, -1);
        if (busNumber < 0 || mBusToCarAudioDeviceInfo.get(busNumber) == null) {
            return null;
        }
        return mBusToCarAudioDeviceInfo.get(busNumber).getAudioDevicePort();
    }

    @Override
    public String toString() {
        return "CarVolumeGroup id: " + mId
                + " currentGainIndex: " + mCurrentGainIndex
                + " contexts: " + Arrays.toString(getContexts())
                + " buses: " + Arrays.toString(getBusNumbers());
    }

    /** Writes to dumpsys output */
    void dump(String indent, PrintWriter writer) {
        writer.printf("%sCarVolumeGroup(%d)\n", indent, mId);
        writer.printf("%sGain values (min / max / default/ current): %d %d %d %d\n",
                indent, mMinGain, mMaxGain,
                mDefaultGain, getGainForIndex(mCurrentGainIndex));
        writer.printf("%sGain indexes (min / max / default / current): %d %d %d %d\n",
                indent, getMinGainIndex(), getMaxGainIndex(),
                getDefaultGainIndex(), mCurrentGainIndex);
        for (int i = 0; i < mContextToBus.size(); i++) {
            writer.printf("%sContext: %s -> Bus: %d\n", indent,
                    ContextNumber.toString(mContextToBus.keyAt(i)), mContextToBus.valueAt(i));
        }
        for (int i = 0; i < mBusToCarAudioDeviceInfo.size(); i++) {
            mBusToCarAudioDeviceInfo.valueAt(i).dump(indent, writer);
        }
        // Empty line for comfortable reading
        writer.println();
    }
}
