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

import android.car.media.CarAudioManager;
import android.media.AudioDeviceInfo;
import android.util.Log;
import android.view.DisplayAddress;

import com.android.car.CarLog;
import com.android.internal.util.Preconditions;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A class encapsulates an audio zone in car.
 *
 * An audio zone can contain multiple {@link CarVolumeGroup}s, and each zone has its own
 * {@link CarAudioFocus} instance. Additionally, there may be dedicated hardware volume keys
 * attached to each zone.
 *
 * See also the unified car_audio_configuration.xml
 */
/* package */ class CarAudioZone {

    private final int mId;
    private final String mName;
    private final List<CarVolumeGroup> mVolumeGroups;
    private final List<DisplayAddress.Physical> mPhysicalDisplayAddresses;

    CarAudioZone(int id, String name) {
        mId = id;
        mName = name;
        mVolumeGroups = new ArrayList<>();
        mPhysicalDisplayAddresses = new ArrayList<>();
    }

    int getId() {
        return mId;
    }

    String getName() {
        return mName;
    }

    boolean isPrimaryZone() {
        return mId == CarAudioManager.PRIMARY_AUDIO_ZONE;
    }

    void addVolumeGroup(CarVolumeGroup volumeGroup) {
        mVolumeGroups.add(volumeGroup);
    }

    CarVolumeGroup getVolumeGroup(int groupId) {
        Preconditions.checkArgumentInRange(groupId, 0, mVolumeGroups.size() - 1,
                "groupId(" + groupId + ") is out of range");
        return mVolumeGroups.get(groupId);
    }

    /**
     * @return Snapshot of available {@link AudioDeviceInfo}s in List.
     */
    List<AudioDeviceInfo> getAudioDeviceInfos() {
        final List<AudioDeviceInfo> devices = new ArrayList<>();
        for (CarVolumeGroup group : mVolumeGroups) {
            for (int busNumber : group.getBusNumbers()) {
                devices.add(group.getCarAudioDeviceInfoForBus(busNumber).getAudioDeviceInfo());
            }
        }
        return devices;
    }

    int getVolumeGroupCount() {
        return mVolumeGroups.size();
    }

    /**
     * Associates a new display physical port with this audio zone. This can be used to
     * identify what zone an activity should produce sound in when launching on a particular display
     * @param physicalDisplayAddress port to associate with this zone
     */
    void addPhysicalDisplayAddress(DisplayAddress.Physical physicalDisplayAddress) {
        mPhysicalDisplayAddresses.add(physicalDisplayAddress);
    }

    /**
     * Gets list of ports for displays associated with this audio zone
     * @return list of Physical ports for displays associated with this audio zone
     */
    List<DisplayAddress.Physical> getPhysicalDisplayAddresses() {
        return mPhysicalDisplayAddresses;
    }

    /**
     * @return Snapshot of available {@link CarVolumeGroup}s in array.
     */
    CarVolumeGroup[] getVolumeGroups() {
        return mVolumeGroups.toArray(new CarVolumeGroup[0]);
    }

    /**
     * Constraints applied here:
     *
     * - One context should not appear in two groups
     * - All contexts are assigned
     * - One bus should not appear in two groups
     * - All gain controllers in the same group have same step value
     *
     * Note that it is fine that there are buses not appear in any group, those buses may be
     * reserved for other usages.
     * Step value validation is done in {@link CarVolumeGroup#bind(int, int, CarAudioDeviceInfo)}
     */
    boolean validateVolumeGroups() {
        Set<Integer> contextSet = new HashSet<>();
        Set<Integer> busNumberSet = new HashSet<>();
        for (CarVolumeGroup group : mVolumeGroups) {
            // One context should not appear in two groups
            for (int context : group.getContexts()) {
                if (contextSet.contains(context)) {
                    Log.e(CarLog.TAG_AUDIO, "Context appears in two groups: " + context);
                    return false;
                }
                contextSet.add(context);
            }

            // One bus should not appear in two groups
            for (int busNumber : group.getBusNumbers()) {
                if (busNumberSet.contains(busNumber)) {
                    Log.e(CarLog.TAG_AUDIO, "Bus appears in two groups: " + busNumber);
                    return false;
                }
                busNumberSet.add(busNumber);
            }
        }

        // All contexts are assigned
        if (contextSet.size() != CarAudioDynamicRouting.CONTEXT_NUMBERS.length) {
            Log.e(CarLog.TAG_AUDIO, "Some contexts are not assigned to group");
            Log.e(CarLog.TAG_AUDIO, "Assigned contexts "
                    + Arrays.toString(contextSet.toArray(new Integer[0])));
            Log.e(CarLog.TAG_AUDIO,
                    "All contexts " + Arrays.toString(CarAudioDynamicRouting.CONTEXT_NUMBERS));
            return false;
        }

        return true;
    }

    void synchronizeCurrentGainIndex() {
        for (CarVolumeGroup group : mVolumeGroups) {
            group.setCurrentGainIndex(group.getCurrentGainIndex());
        }
    }

    void dump(String indent, PrintWriter writer) {
        writer.printf("%sCarAudioZone(%s:%d) isPrimary? %b\n", indent, mName, mId, isPrimaryZone());
        for (DisplayAddress.Physical physical: mPhysicalDisplayAddresses) {
            long port = (long) physical.getPort();
            writer.printf("%sDisplayAddress.Physical(%d)\n", indent + "\t", port);
        }
        writer.println();

        for (CarVolumeGroup group : mVolumeGroups) {
            group.dump(indent + "\t", writer);
        }
        writer.println();
    }
}
