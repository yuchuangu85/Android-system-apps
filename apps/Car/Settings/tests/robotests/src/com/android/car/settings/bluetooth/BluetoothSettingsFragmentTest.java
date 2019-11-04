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

import static android.bluetooth.BluetoothAdapter.STATE_OFF;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF;
import static android.bluetooth.BluetoothAdapter.STATE_TURNING_ON;
import static android.os.UserManager.DISALLOW_BLUETOOTH;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.Intent;
import android.widget.Switch;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.FragmentController;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;
import com.android.car.settings.testutils.ShadowBluetoothPan;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

/** Unit test for {@link BluetoothSettingsFragment}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowBluetoothAdapter.class,
        ShadowBluetoothPan.class})
public class BluetoothSettingsFragmentTest {

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    private Context mContext;
    private LocalBluetoothManager mLocalBluetoothManager;
    private FragmentController<BluetoothSettingsFragment> mFragmentController;
    private BluetoothSettingsFragment mFragment;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);

        mContext = RuntimeEnvironment.application;
        mLocalBluetoothManager = LocalBluetoothManager.getInstance(mContext, /* onInitCallback= */
                null);
        mFragment = new BluetoothSettingsFragment();
        mFragmentController = FragmentController.of(mFragment);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowBluetoothAdapter.reset();
    }

    @Test
    public void onStart_setsBluetoothManagerForegroundActivity() {
        mFragmentController.create().start();

        assertThat(mLocalBluetoothManager.getForegroundActivity()).isEqualTo(
                mFragment.requireActivity());
    }

    @Test
    public void onStart_initializesSwitchState() {
        getShadowBluetoothAdapter().setState(STATE_ON);

        mFragmentController.create().start();

        assertThat(findSwitch(mFragment.requireActivity()).isChecked()).isTrue();
    }

    @Test
    public void onStop_clearsBluetoothManagerForegroundActivity() {
        mFragmentController.create().start().resume().pause().stop();

        assertThat(mLocalBluetoothManager.getForegroundActivity()).isNull();
    }

    @Test
    public void switchCheckedOn_enablesAdapter() {
        mFragmentController.setup();
        assertThat(BluetoothAdapter.getDefaultAdapter().isEnabled()).isFalse();

        findSwitch(mFragment.requireActivity()).performClick();

        assertThat(BluetoothAdapter.getDefaultAdapter().isEnabled()).isTrue();
    }

    @Test
    public void switchCheckedOff_disablesAdapter() {
        getShadowBluetoothAdapter().setState(STATE_ON);
        BluetoothAdapter.getDefaultAdapter().enable();
        mFragmentController.setup();
        assertThat(BluetoothAdapter.getDefaultAdapter().isEnabled()).isTrue();

        findSwitch(mFragment.requireActivity()).performClick();

        assertThat(BluetoothAdapter.getDefaultAdapter().isEnabled()).isFalse();
    }

    @Test
    public void stateChanged_turningOn_setsSwitchChecked() {
        mFragmentController.setup();

        sendStateChangedIntent(STATE_TURNING_ON);

        assertThat(findSwitch(mFragment.requireActivity()).isChecked()).isTrue();
    }

    @Test
    public void stateChanged_turningOn_setsSwitchDisabled() {
        mFragmentController.setup();

        sendStateChangedIntent(STATE_TURNING_ON);

        assertThat(findSwitch(mFragment.requireActivity()).isEnabled()).isFalse();
    }

    @Test
    public void stateChanged_on_setsSwitchChecked() {
        mFragmentController.setup();

        sendStateChangedIntent(STATE_ON);

        assertThat(findSwitch(mFragment.requireActivity()).isChecked()).isTrue();
    }

    @Test
    public void stateChanged_on_setsSwitchEnabled() {
        mFragmentController.setup();

        sendStateChangedIntent(STATE_ON);

        assertThat(findSwitch(mFragment.requireActivity()).isEnabled()).isTrue();
    }

    @Test
    public void stateChanged_on_userRestricted_setsSwitchDisabled() {
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                DISALLOW_BLUETOOTH)).thenReturn(true);
        mFragmentController.setup();

        sendStateChangedIntent(STATE_ON);

        assertThat(findSwitch(mFragment.requireActivity()).isEnabled()).isFalse();
    }

    @Test
    public void stateChanged_turningOff_setsSwitchUnchecked() {
        mFragmentController.setup();

        sendStateChangedIntent(STATE_TURNING_OFF);

        assertThat(findSwitch(mFragment.requireActivity()).isChecked()).isFalse();
    }

    @Test
    public void stateChanged_turningOff_setsSwitchDisabled() {
        mFragmentController.setup();

        sendStateChangedIntent(STATE_TURNING_OFF);

        assertThat(findSwitch(mFragment.requireActivity()).isEnabled()).isFalse();
    }

    @Test
    public void stateChanged_off_setsSwitchUnchecked() {
        mFragmentController.setup();

        sendStateChangedIntent(STATE_OFF);

        assertThat(findSwitch(mFragment.requireActivity()).isChecked()).isFalse();
    }

    @Test
    public void stateChanged_off_setsSwitchEnabled() {
        mFragmentController.setup();

        sendStateChangedIntent(STATE_OFF);

        assertThat(findSwitch(mFragment.requireActivity()).isEnabled()).isTrue();
    }

    @Test
    public void stateChanged_off_userRestricted_setsSwitchDisabled() {
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                DISALLOW_BLUETOOTH)).thenReturn(true);
        mFragmentController.setup();

        sendStateChangedIntent(STATE_OFF);

        assertThat(findSwitch(mFragment.requireActivity()).isEnabled()).isFalse();
    }

    @Test
    public void stateChanged_fragmentStopped_doesNothing() {
        mFragmentController.setup();
        mFragmentController.stop();

        sendStateChangedIntent(STATE_TURNING_ON);

        assertThat(findSwitch(mFragment.requireActivity()).isChecked()).isFalse();
    }

    private void sendStateChangedIntent(int state) {
        Intent intent = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);
        intent.putExtra(BluetoothAdapter.EXTRA_STATE, state);
        mContext.sendBroadcast(intent);
    }

    private Switch findSwitch(Activity activity) {
        return activity.findViewById(R.id.toggle_switch);
    }

    private ShadowBluetoothAdapter getShadowBluetoothAdapter() {
        return Shadow.extract(BluetoothAdapter.getDefaultAdapter());
    }
}
