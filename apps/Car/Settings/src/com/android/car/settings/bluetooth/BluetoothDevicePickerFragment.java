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

package com.android.car.settings.bluetooth;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

/**
 * Hosts {@link BluetoothDevicePickerPreferenceController} to display the list of Bluetooth
 * devices. The progress bar is shown while this fragment is visible to indicate discovery or
 * pairing progress.
 */
public class BluetoothDevicePickerFragment extends SettingsFragment {

    private LocalBluetoothManager mManager;
    private ProgressBar mProgressBar;

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.bluetooth_device_picker_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mManager = BluetoothUtils.getLocalBtManager(context);
        if (mManager == null) {
            goBack();
            return;
        }

        use(BluetoothDevicePickerPreferenceController.class,
                R.string.pk_bluetooth_device_picker).setLaunchIntent(requireActivity().getIntent());
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mProgressBar = requireActivity().findViewById(R.id.progress_bar);
    }

    @Override
    public void onStart() {
        super.onStart();
        mManager.setForegroundActivity(requireActivity());
        mProgressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onStop() {
        super.onStop();
        mManager.setForegroundActivity(null);
        mProgressBar.setVisibility(View.GONE);
    }
}
