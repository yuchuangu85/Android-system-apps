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

package com.android.car.settings.bluetooth;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Pair;

import androidx.preference.Preference;

import com.android.car.apps.common.util.Themes;
import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;

import java.util.StringJoiner;

/**
 * Displays the name, icon, and status (connected/disconnected, etc.) of a remote Bluetooth device.
 * When the associated preference is clicked, a dialog is shown to allow the user to update the
 * display name of the remote device.
 */
public class BluetoothDeviceNamePreferenceController extends
        BluetoothDevicePreferenceController<Preference> {

    public BluetoothDeviceNamePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void updateState(Preference preference) {
        CachedBluetoothDevice cachedDevice = getCachedDevice();
        Pair<Drawable, String> pair =
                com.android.settingslib.bluetooth.BluetoothUtils.getBtClassDrawableWithDescription(
                        getContext(),
                        cachedDevice);
        StringJoiner summaryJoiner = new StringJoiner(System.lineSeparator());
        summaryJoiner.setEmptyValue("");

        String summaryText = cachedDevice.getCarConnectionSummary();
        if (!TextUtils.isEmpty(summaryText)) {
            summaryJoiner.add(summaryText);
        }
        // If hearing aids are connected, two battery statuses should be shown.
        String pairDeviceSummary =
                getBluetoothManager().getCachedDeviceManager().getSubDeviceSummary(cachedDevice);
        if (!TextUtils.isEmpty(pairDeviceSummary)) {
            summaryJoiner.add(pairDeviceSummary);
        }
        preference.setTitle(cachedDevice.getName());
        preference.setIcon(pair.first);
        preference.getIcon().setTintList(
                Themes.getAttrColorStateList(getContext(), R.attr.iconColor));
        preference.setSummary(summaryJoiner.toString());
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        getFragmentController().showDialog(
                RemoteRenameDialogFragment.newInstance(getCachedDevice()),
                RemoteRenameDialogFragment.TAG);
        return true;
    }
}
