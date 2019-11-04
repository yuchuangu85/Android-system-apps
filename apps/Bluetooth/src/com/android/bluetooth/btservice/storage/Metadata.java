/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.bluetooth.btservice.storage;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;

import androidx.annotation.NonNull;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.List;

@Entity(tableName = "metadata")
class Metadata {
    @PrimaryKey
    @NonNull
    private String address;

    public boolean migrated;

    @Embedded
    public ProfilePrioritiesEntity profilePriorities;

    @Embedded
    @NonNull
    public CustomizedMetadataEntity publicMetadata;

    public int a2dpSupportsOptionalCodecs;
    public int a2dpOptionalCodecsEnabled;

    Metadata(String address) {
        this.address = address;
        migrated = false;
        profilePriorities = new ProfilePrioritiesEntity();
        publicMetadata = new CustomizedMetadataEntity();
        a2dpSupportsOptionalCodecs = BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN;
        a2dpOptionalCodecsEnabled = BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN;
    }

    String getAddress() {
        return address;
    }

    void setProfilePriority(int profile, int priority) {
        switch (profile) {
            case BluetoothProfile.A2DP:
                profilePriorities.a2dp_priority = priority;
                break;
            case BluetoothProfile.A2DP_SINK:
                profilePriorities.a2dp_sink_priority = priority;
                break;
            case BluetoothProfile.HEADSET:
                profilePriorities.hfp_priority = priority;
                break;
            case BluetoothProfile.HEADSET_CLIENT:
                profilePriorities.hfp_client_priority = priority;
                break;
            case BluetoothProfile.HID_HOST:
                profilePriorities.hid_host_priority = priority;
                break;
            case BluetoothProfile.PAN:
                profilePriorities.pan_priority = priority;
                break;
            case BluetoothProfile.PBAP:
                profilePriorities.pbap_priority = priority;
                break;
            case BluetoothProfile.PBAP_CLIENT:
                profilePriorities.pbap_client_priority = priority;
                break;
            case BluetoothProfile.MAP:
                profilePriorities.map_priority = priority;
                break;
            case BluetoothProfile.MAP_CLIENT:
                profilePriorities.map_client_priority = priority;
                break;
            case BluetoothProfile.SAP:
                profilePriorities.sap_priority = priority;
                break;
            case BluetoothProfile.HEARING_AID:
                profilePriorities.hearing_aid_priority = priority;
                break;
            default:
                throw new IllegalArgumentException("invalid profile " + profile);
        }
    }

    int getProfilePriority(int profile) {
        switch (profile) {
            case BluetoothProfile.A2DP:
                return profilePriorities.a2dp_priority;
            case BluetoothProfile.A2DP_SINK:
                return profilePriorities.a2dp_sink_priority;
            case BluetoothProfile.HEADSET:
                return profilePriorities.hfp_priority;
            case BluetoothProfile.HEADSET_CLIENT:
                return profilePriorities.hfp_client_priority;
            case BluetoothProfile.HID_HOST:
                return profilePriorities.hid_host_priority;
            case BluetoothProfile.PAN:
                return profilePriorities.pan_priority;
            case BluetoothProfile.PBAP:
                return profilePriorities.pbap_priority;
            case BluetoothProfile.PBAP_CLIENT:
                return profilePriorities.pbap_client_priority;
            case BluetoothProfile.MAP:
                return profilePriorities.map_priority;
            case BluetoothProfile.MAP_CLIENT:
                return profilePriorities.map_client_priority;
            case BluetoothProfile.SAP:
                return profilePriorities.sap_priority;
            case BluetoothProfile.HEARING_AID:
                return profilePriorities.hearing_aid_priority;
        }
        return BluetoothProfile.PRIORITY_UNDEFINED;
    }

    void setCustomizedMeta(int key, byte[] value) {
        switch (key) {
            case BluetoothDevice.METADATA_MANUFACTURER_NAME:
                publicMetadata.manufacturer_name = value;
                break;
            case BluetoothDevice.METADATA_MODEL_NAME:
                publicMetadata.model_name = value;
                break;
            case BluetoothDevice.METADATA_SOFTWARE_VERSION:
                publicMetadata.software_version = value;
                break;
            case BluetoothDevice.METADATA_HARDWARE_VERSION:
                publicMetadata.hardware_version = value;
                break;
            case BluetoothDevice.METADATA_COMPANION_APP:
                publicMetadata.companion_app = value;
                break;
            case BluetoothDevice.METADATA_MAIN_ICON:
                publicMetadata.main_icon = value;
                break;
            case BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET:
                publicMetadata.is_untethered_headset = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_ICON:
                publicMetadata.untethered_left_icon = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_ICON:
                publicMetadata.untethered_right_icon = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_CASE_ICON:
                publicMetadata.untethered_case_icon = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_BATTERY:
                publicMetadata.untethered_left_battery = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_BATTERY:
                publicMetadata.untethered_right_battery = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_CASE_BATTERY:
                publicMetadata.untethered_case_battery = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_CHARGING:
                publicMetadata.untethered_left_charging = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_CHARGING:
                publicMetadata.untethered_right_charging = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_CASE_CHARGING:
                publicMetadata.untethered_case_charging = value;
                break;
            case BluetoothDevice.METADATA_ENHANCED_SETTINGS_UI_URI:
                publicMetadata.enhanced_settings_ui_uri = value;
                break;
        }
    }

    byte[] getCustomizedMeta(int key) {
        byte[] value = null;
        switch (key) {
            case BluetoothDevice.METADATA_MANUFACTURER_NAME:
                value = publicMetadata.manufacturer_name;
                break;
            case BluetoothDevice.METADATA_MODEL_NAME:
                value = publicMetadata.model_name;
                break;
            case BluetoothDevice.METADATA_SOFTWARE_VERSION:
                value = publicMetadata.software_version;
                break;
            case BluetoothDevice.METADATA_HARDWARE_VERSION:
                value = publicMetadata.hardware_version;
                break;
            case BluetoothDevice.METADATA_COMPANION_APP:
                value = publicMetadata.companion_app;
                break;
            case BluetoothDevice.METADATA_MAIN_ICON:
                value = publicMetadata.main_icon;
                break;
            case BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET:
                value = publicMetadata.is_untethered_headset;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_ICON:
                value = publicMetadata.untethered_left_icon;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_ICON:
                value = publicMetadata.untethered_right_icon;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_CASE_ICON:
                value = publicMetadata.untethered_case_icon;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_BATTERY:
                value = publicMetadata.untethered_left_battery;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_BATTERY:
                value = publicMetadata.untethered_right_battery;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_CASE_BATTERY:
                value = publicMetadata.untethered_case_battery;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_CHARGING:
                value = publicMetadata.untethered_left_charging;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_CHARGING:
                value = publicMetadata.untethered_right_charging;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_CASE_CHARGING:
                value = publicMetadata.untethered_case_charging;
                break;
            case BluetoothDevice.METADATA_ENHANCED_SETTINGS_UI_URI:
                value = publicMetadata.enhanced_settings_ui_uri;
                break;
        }
        return value;
    }

    List<Integer> getChangedCustomizedMeta() {
        List<Integer> list = new ArrayList<>();
        if (publicMetadata.manufacturer_name != null) {
            list.add(BluetoothDevice.METADATA_MANUFACTURER_NAME);
        }
        if (publicMetadata.model_name != null) {
            list.add(BluetoothDevice.METADATA_MODEL_NAME);
        }
        if (publicMetadata.software_version != null) {
            list.add(BluetoothDevice.METADATA_SOFTWARE_VERSION);
        }
        if (publicMetadata.hardware_version != null) {
            list.add(BluetoothDevice.METADATA_HARDWARE_VERSION);
        }
        if (publicMetadata.companion_app != null) {
            list.add(BluetoothDevice.METADATA_COMPANION_APP);
        }
        if (publicMetadata.main_icon != null) {
            list.add(BluetoothDevice.METADATA_MAIN_ICON);
        }
        if (publicMetadata.is_untethered_headset != null) {
            list.add(BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET);
        }
        if (publicMetadata.untethered_left_icon != null) {
            list.add(BluetoothDevice.METADATA_UNTETHERED_LEFT_ICON);
        }
        if (publicMetadata.untethered_right_icon != null) {
            list.add(BluetoothDevice.METADATA_UNTETHERED_RIGHT_ICON);
        }
        if (publicMetadata.untethered_case_icon != null) {
            list.add(BluetoothDevice.METADATA_UNTETHERED_CASE_ICON);
        }
        if (publicMetadata.untethered_left_battery != null) {
            list.add(BluetoothDevice.METADATA_UNTETHERED_LEFT_BATTERY);
        }
        if (publicMetadata.untethered_right_battery != null) {
            list.add(BluetoothDevice.METADATA_UNTETHERED_RIGHT_BATTERY);
        }
        if (publicMetadata.untethered_case_battery != null) {
            list.add(BluetoothDevice.METADATA_UNTETHERED_CASE_BATTERY);
        }
        if (publicMetadata.untethered_left_charging != null) {
            list.add(BluetoothDevice.METADATA_UNTETHERED_LEFT_CHARGING);
        }
        if (publicMetadata.untethered_right_charging != null) {
            list.add(BluetoothDevice.METADATA_UNTETHERED_RIGHT_CHARGING);
        }
        if (publicMetadata.untethered_case_charging != null) {
            list.add(BluetoothDevice.METADATA_UNTETHERED_CASE_CHARGING);
        }
        if (publicMetadata.enhanced_settings_ui_uri != null) {
            list.add(BluetoothDevice.METADATA_ENHANCED_SETTINGS_UI_URI);
        }
        return list;
    }
}
