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

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.ProgressBar;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.FragmentController;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;
import com.android.car.settings.testutils.ShadowBluetoothPan;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Unit test for {@link BluetoothDevicePickerFragment}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowBluetoothAdapter.class, ShadowBluetoothPan.class})
public class BluetoothDevicePickerFragmentTest {

    private LocalBluetoothManager mLocalBluetoothManager;
    private FragmentController<BluetoothDevicePickerFragment> mFragmentController;
    private BluetoothDevicePickerFragment mFragment;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = RuntimeEnvironment.application;
        mLocalBluetoothManager = LocalBluetoothManager.getInstance(context, /* onInitCallback= */
                null);

        mFragment = new BluetoothDevicePickerFragment();
        mFragmentController = FragmentController.of(mFragment);
    }

    @After
    public void tearDown() {
        ShadowBluetoothAdapter.reset();
    }

    @Test
    public void onStart_setsBluetoothManagerForegroundActivity() {
        mFragmentController.create().start();

        assertThat(mLocalBluetoothManager.getForegroundActivity()).isEqualTo(
                mFragment.requireActivity());
    }

    @Test
    public void onStart_showsProgressBar() {
        mFragmentController.create();
        ProgressBar progressBar = findProgressBar(mFragment.requireActivity());
        progressBar.setVisibility(View.GONE);

        mFragmentController.start();

        assertThat(progressBar.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onStop_clearsBluetoothManagerForegroundActivity() {
        mFragmentController.create().start().resume().pause().stop();

        assertThat(mLocalBluetoothManager.getForegroundActivity()).isNull();
    }

    @Test
    public void onStop_hidesProgressBar() {
        mFragmentController.setup().onPause();
        ProgressBar progressBar = findProgressBar(mFragment.requireActivity());
        progressBar.setVisibility(View.VISIBLE);

        mFragmentController.stop();

        assertThat(progressBar.getVisibility()).isEqualTo(View.GONE);
    }

    private ProgressBar findProgressBar(Activity activity) {
        return activity.findViewById(R.id.progress_bar);
    }
}
