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
import static android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_USER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.bluetooth.BluetoothAdapter;
import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;
import com.android.car.settings.testutils.ShadowBluetoothPan;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

/** Unit test for {@link BluetoothDevicePreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowBluetoothAdapter.class,
        ShadowBluetoothPan.class})
public class BluetoothDevicePreferenceControllerTest {

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    @Mock
    private CachedBluetoothDevice mDevice;
    private Context mContext;
    private PreferenceControllerTestHelper<TestBluetoothDevicePreferenceController>
            mControllerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        mContext = RuntimeEnvironment.application;

        // Make sure controller is available.
        Shadows.shadowOf(mContext.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        BluetoothAdapter.getDefaultAdapter().enable();
        getShadowBluetoothAdapter().setState(BluetoothAdapter.STATE_ON);

        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                TestBluetoothDevicePreferenceController.class);
        mControllerHelper.getController().setCachedDevice(mDevice);
        mControllerHelper.setPreference(new Preference(mContext));
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowBluetoothAdapter.reset();
    }

    @Test
    public void setPreference_deviceNotSet_throwsIllegalStateException() {
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                TestBluetoothDevicePreferenceController.class);
        assertThrows(IllegalStateException.class,
                () -> mControllerHelper.setPreference(new Preference(mContext)));
    }

    @Test
    public void getAvailabilityStatus_disallowConfigBluetooth_disabledForUser() {
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                DISALLOW_CONFIG_BLUETOOTH)).thenReturn(true);

        assertThat(mControllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                DISABLED_FOR_USER);
    }

    @Test
    public void onStart_registersDeviceCallback() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        verify(mDevice).registerCallback(any(CachedBluetoothDevice.Callback.class));
    }

    @Test
    public void onStop_unregistersDeviceCallback() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        ArgumentCaptor<CachedBluetoothDevice.Callback> callbackCaptor = ArgumentCaptor.forClass(
                CachedBluetoothDevice.Callback.class);
        verify(mDevice).registerCallback(callbackCaptor.capture());

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        verify(mDevice).unregisterCallback(callbackCaptor.getValue());
    }

    @Test
    public void started_onDeviceAttributesChanged_refreshesUi() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        ArgumentCaptor<CachedBluetoothDevice.Callback> callbackCaptor = ArgumentCaptor.forClass(
                CachedBluetoothDevice.Callback.class);
        verify(mDevice).registerCallback(callbackCaptor.capture());
        // onCreate, onStart.
        assertThat(mControllerHelper.getController().getUpdateStateCallCount()).isEqualTo(2);

        callbackCaptor.getValue().onDeviceAttributesChanged();

        // onCreate, onStart, callback.
        assertThat(mControllerHelper.getController().getUpdateStateCallCount()).isEqualTo(3);
    }

    private ShadowBluetoothAdapter getShadowBluetoothAdapter() {
        return (ShadowBluetoothAdapter) Shadow.extract(BluetoothAdapter.getDefaultAdapter());
    }

    /** Concrete impl of {@link BluetoothDevicePreferenceController} for testing. */
    private static class TestBluetoothDevicePreferenceController extends
            BluetoothDevicePreferenceController<Preference> {

        private int mUpdateStateCallCount;

        TestBluetoothDevicePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected Class<Preference> getPreferenceType() {
            return Preference.class;
        }

        @Override
        protected void updateState(Preference preference) {
            mUpdateStateCallCount++;
        }

        int getUpdateStateCallCount() {
            return mUpdateStateCallCount;
        }
    }
}
