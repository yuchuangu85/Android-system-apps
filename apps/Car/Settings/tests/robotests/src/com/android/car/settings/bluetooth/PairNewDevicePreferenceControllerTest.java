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

import static android.content.pm.PackageManager.FEATURE_BLUETOOTH;
import static android.os.UserManager.DISALLOW_BLUETOOTH;
import static android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_USER;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.bluetooth.BluetoothAdapter;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;
import com.android.car.settings.testutils.ShadowBluetoothPan;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.settingslib.bluetooth.BluetoothEventManager;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/** Unit test for {@link PairNewDevicePreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowBluetoothAdapter.class,
        ShadowBluetoothPan.class})
public class PairNewDevicePreferenceControllerTest {

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    @Mock
    private BluetoothEventManager mEventManager;
    private BluetoothEventManager mSaveRealEventManager;
    private LocalBluetoothManager mLocalBluetoothManager;
    private Context mContext;
    private Preference mPreference;
    private PreferenceControllerTestHelper<PairNewDevicePreferenceController> mControllerHelper;
    private PairNewDevicePreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        mContext = RuntimeEnvironment.application;
        mLocalBluetoothManager = LocalBluetoothManager.getInstance(mContext, /* onInitCallback= */
                null);
        mSaveRealEventManager = mLocalBluetoothManager.getEventManager();
        ReflectionHelpers.setField(mLocalBluetoothManager, "mEventManager", mEventManager);

        // Default to available.
        Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);

        mPreference = new Preference(mContext);
        mPreference.setIntent(new Intent());
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                PairNewDevicePreferenceController.class, mPreference);
        mController = mControllerHelper.getController();
        mControllerHelper.markState(Lifecycle.State.STARTED);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowBluetoothAdapter.reset();
        ReflectionHelpers.setField(mLocalBluetoothManager, "mEventManager", mSaveRealEventManager);
    }

    @Test
    public void checkInitialized_noFragmentOrIntent_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> new PreferenceControllerTestHelper<>(mContext,
                        PairNewDevicePreferenceController.class, new Preference(mContext)));
    }

    @Test
    public void getAvailabilityStatus_bluetoothNotAvailable_unsupportedOnDevice() {
        Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_disallowBluetoothUserRestriction_disabledForUser() {
        Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                DISALLOW_BLUETOOTH)).thenReturn(true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_USER);
    }

    @Test
    public void getAvailabilityStatus_disallowConfigBluetoothUserRestriction_disabledForUser() {
        Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                DISALLOW_CONFIG_BLUETOOTH)).thenReturn(true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_USER);
    }

    @Test
    public void getAvailabilityStatus_available() {
        Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        // No user restrictions.

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void refreshUi_bluetoothAdapterEnabled_setsEmptySummary() {
        BluetoothAdapter.getDefaultAdapter().enable();

        mController.refreshUi();

        assertThat(mPreference.getSummary().toString()).isEmpty();
    }

    @Test
    public void refreshUi_bluetoothAdapterDisabled_setsTurnOnToPairSummary() {
        BluetoothAdapter.getDefaultAdapter().disable();

        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.bluetooth_pair_new_device_summary));
    }

    @Test
    public void bluetoothAdapterStateChangedBroadcast_refreshesUi() {
        BluetoothAdapter.getDefaultAdapter().enable();
        mController.refreshUi();
        assertThat(mPreference.getSummary().toString()).isEmpty();

        BluetoothAdapter.getDefaultAdapter().disable();
        mContext.sendBroadcast(new Intent(BluetoothAdapter.ACTION_STATE_CHANGED));

        assertThat(mPreference.getSummary().toString()).isNotEmpty();
    }

    @Test
    public void preferenceClicked_enablesAdapter() {
        BluetoothAdapter.getDefaultAdapter().disable();

        mPreference.performClick();

        assertThat(BluetoothAdapter.getDefaultAdapter().isEnabled()).isTrue();
    }

    @Test
    public void preferenceClicked_notHandled() {
        assertThat(mPreference.getOnPreferenceClickListener().onPreferenceClick(
                mPreference)).isFalse();
    }
}
