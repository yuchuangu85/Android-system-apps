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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.widget.EditText;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.testutils.BaseTestActivity;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;
import com.android.car.settings.testutils.ShadowBluetoothPan;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;

/** Unit test for {@link RemoteRenameDialogFragment}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowBluetoothAdapter.class, ShadowBluetoothPan.class})
public class RemoteRenameDialogFragmentTest {

    private static final String NAME = "name";
    private static final String NAME_UPDATED = "name updated";

    @Mock
    private CachedBluetoothDevice mCachedDevice;
    @Mock
    private CachedBluetoothDeviceManager mCachedDeviceManager;
    private CachedBluetoothDeviceManager mSaveRealCachedDeviceManager;
    private LocalBluetoothManager mLocalBluetoothManager;
    private RemoteRenameDialogFragment mFragment;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mLocalBluetoothManager = LocalBluetoothManager.getInstance(
                RuntimeEnvironment.application, /* onInitCallback= */ null);
        mSaveRealCachedDeviceManager = mLocalBluetoothManager.getCachedDeviceManager();
        ReflectionHelpers.setField(mLocalBluetoothManager, "mCachedDeviceManager",
                mCachedDeviceManager);

        String address = "00:11:22:33:AA:BB";
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        when(mCachedDeviceManager.findDevice(device)).thenReturn(mCachedDevice);
        when(mCachedDevice.getAddress()).thenReturn(address);

        mFragment = RemoteRenameDialogFragment.newInstance(mCachedDevice);
    }

    @After
    public void tearDown() {
        ShadowBluetoothAdapter.reset();
        ReflectionHelpers.setField(mLocalBluetoothManager, "mCachedDeviceManager",
                mSaveRealCachedDeviceManager);
    }

    @Test
    public void getDeviceName_returnsCachedDeviceName() {
        when(mCachedDevice.getName()).thenReturn(NAME);
        showDialog(mFragment); // Attach the fragment.

        assertThat(mFragment.getDeviceName()).isEqualTo(NAME);
    }

    @Test
    public void setDeviceName_updatesCachedDeviceName() {
        when(mCachedDevice.getName()).thenReturn(NAME);
        AlertDialog dialog = showDialog(mFragment);
        EditText editText = dialog.findViewById(android.R.id.edit);

        editText.setText(NAME_UPDATED);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

        verify(mCachedDevice).setName(NAME_UPDATED);
    }

    private AlertDialog showDialog(RemoteRenameDialogFragment fragment) {
        BaseTestActivity activity = Robolectric.setupActivity(BaseTestActivity.class);
        activity.showDialog(fragment, /* tag= */ null);
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }
}
