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

import android.bluetooth.BluetoothProfile;

import androidx.room.Entity;

@Entity
class ProfilePrioritiesEntity {
    /* Bluetooth profile priorities*/
    public int a2dp_priority;
    public int a2dp_sink_priority;
    public int hfp_priority;
    public int hfp_client_priority;
    public int hid_host_priority;
    public int pan_priority;
    public int pbap_priority;
    public int pbap_client_priority;
    public int map_priority;
    public int sap_priority;
    public int hearing_aid_priority;
    public int map_client_priority;

    ProfilePrioritiesEntity() {
        a2dp_priority = BluetoothProfile.PRIORITY_UNDEFINED;
        a2dp_sink_priority = BluetoothProfile.PRIORITY_UNDEFINED;
        hfp_priority = BluetoothProfile.PRIORITY_UNDEFINED;
        hfp_client_priority = BluetoothProfile.PRIORITY_UNDEFINED;
        hid_host_priority = BluetoothProfile.PRIORITY_UNDEFINED;
        pan_priority = BluetoothProfile.PRIORITY_UNDEFINED;
        pbap_priority = BluetoothProfile.PRIORITY_UNDEFINED;
        pbap_client_priority = BluetoothProfile.PRIORITY_UNDEFINED;
        map_priority = BluetoothProfile.PRIORITY_UNDEFINED;
        sap_priority = BluetoothProfile.PRIORITY_UNDEFINED;
        hearing_aid_priority = BluetoothProfile.PRIORITY_UNDEFINED;
        map_client_priority = BluetoothProfile.PRIORITY_UNDEFINED;
    }
}
