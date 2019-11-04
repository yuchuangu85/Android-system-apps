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

import static android.bluetooth.BluetoothAdapter.EXTRA_LOCAL_NAME;
import static android.bluetooth.BluetoothAdapter.STATE_ON;

import static com.google.common.truth.Truth.assertThat;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.EditText;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.testutils.BaseTestActivity;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowDialog;

/** Unit test for {@link LocalRenameDialogFragment}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowBluetoothAdapter.class})
public class LocalRenameDialogFragmentTest {

    private static final String NAME = "name";
    private static final String NAME_UPDATED = "name updated";

    private LocalRenameDialogFragment mFragment;

    @Before
    public void setUp() {
        mFragment = new LocalRenameDialogFragment();
        getShadowBluetoothAdapter().setState(STATE_ON);
        BluetoothAdapter.getDefaultAdapter().enable();
    }

    @After
    public void tearDown() {
        ShadowBluetoothAdapter.reset();
    }

    @Test
    public void getDeviceName_adapterEnabled_returnsLocalAdapterName() {
        BluetoothAdapter.getDefaultAdapter().setName(NAME);

        assertThat(mFragment.getDeviceName()).isEqualTo(NAME);
    }

    @Test
    public void getDeviceName_adapterDisabled_returnsNull() {
        BluetoothAdapter.getDefaultAdapter().setName(NAME);
        BluetoothAdapter.getDefaultAdapter().disable();

        assertThat(mFragment.getDeviceName()).isNull();
    }

    @Test
    public void localNameChangedBroadcast_updatesDeviceName() {
        BluetoothAdapter.getDefaultAdapter().setName(NAME);
        AlertDialog dialog = showDialog(mFragment);
        EditText editText = dialog.findViewById(android.R.id.edit);
        assertThat(editText.getText().toString()).isEqualTo(NAME);

        BluetoothAdapter.getDefaultAdapter().setName(NAME_UPDATED);
        Intent intent = new Intent(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        intent.putExtra(EXTRA_LOCAL_NAME, NAME_UPDATED);
        RuntimeEnvironment.application.sendBroadcast(intent);

        assertThat(editText.getText().toString()).isEqualTo(NAME_UPDATED);
        assertThat(dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled()).isFalse();
    }

    @Test
    public void setDeviceName_updatesLocalAdapterName() {
        BluetoothAdapter.getDefaultAdapter().setName(NAME);
        AlertDialog dialog = showDialog(mFragment);
        EditText editText = dialog.findViewById(android.R.id.edit);

        editText.setText(NAME_UPDATED);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

        assertThat(BluetoothAdapter.getDefaultAdapter().getName()).isEqualTo(NAME_UPDATED);
    }

    private AlertDialog showDialog(LocalRenameDialogFragment fragment) {
        BaseTestActivity activity = Robolectric.setupActivity(BaseTestActivity.class);
        activity.showDialog(fragment, /* tag= */ null);
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }

    private ShadowBluetoothAdapter getShadowBluetoothAdapter() {
        return (ShadowBluetoothAdapter) Shadow.extract(BluetoothAdapter.getDefaultAdapter());
    }
}
