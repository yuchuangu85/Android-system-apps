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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;
import com.android.car.settings.testutils.ShadowBluetoothPan;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

/** Unit test for {@link BluetoothNamePreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowBluetoothAdapter.class,
        ShadowBluetoothPan.class})
public class BluetoothNamePreferenceControllerTest {

    private static final String NAME = "name";
    private static final String NAME_UPDATED = "name updated";

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    private Preference mPreference;
    private PreferenceControllerTestHelper<BluetoothNamePreferenceController> mControllerHelper;
    private BluetoothNamePreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        Context context = RuntimeEnvironment.application;

        // Make sure controller is available.
        Shadows.shadowOf(context.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        BluetoothAdapter.getDefaultAdapter().enable();
        getShadowBluetoothAdapter().setState(BluetoothAdapter.STATE_ON);

        mPreference = new Preference(context);
        mControllerHelper = new PreferenceControllerTestHelper<>(context,
                BluetoothNamePreferenceController.class, mPreference);
        mController = mControllerHelper.getController();
        mControllerHelper.markState(Lifecycle.State.CREATED);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowBluetoothAdapter.reset();
    }

    @Test
    public void refreshUi_setsNameAsSummary() {
        BluetoothAdapter.getDefaultAdapter().setName(NAME);

        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(NAME);
    }

    @Test
    public void refreshUi_noUserRestrictions_setsSelectable() {
        mController.refreshUi();

        assertThat(mPreference.isSelectable()).isTrue();
    }

    @Test
    public void refreshUi_userHasConfigRestriction_setsNotSelectable() {
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                DISALLOW_CONFIG_BLUETOOTH)).thenReturn(true);

        mController.refreshUi();

        assertThat(mPreference.isSelectable()).isFalse();
    }

    @Test
    public void started_localNameChangedBroadcast_updatesSummary() {
        BluetoothAdapter.getDefaultAdapter().setName(NAME);
        mControllerHelper.markState(Lifecycle.State.STARTED);
        assertThat(mPreference.getSummary()).isEqualTo(NAME);

        BluetoothAdapter.getDefaultAdapter().setName(NAME_UPDATED);
        RuntimeEnvironment.application.sendBroadcast(
                new Intent(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED));

        assertThat(mPreference.getSummary()).isEqualTo(NAME_UPDATED);
    }

    @Test
    public void stopped_noUpdateOnLocalNameChangedBroadcast() {
        BluetoothAdapter.getDefaultAdapter().setName(NAME);
        mControllerHelper.markState(Lifecycle.State.STARTED);
        assertThat(mPreference.getSummary()).isEqualTo(NAME);

        mControllerHelper.markState(Lifecycle.State.CREATED);
        BluetoothAdapter.getDefaultAdapter().setName(NAME_UPDATED);
        RuntimeEnvironment.application.sendBroadcast(
                new Intent(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED));

        assertThat(mPreference.getSummary()).isEqualTo(NAME);
    }

    @Test
    public void preferenceClicked_launchesRenameDialog() {
        mControllerHelper.markState(Lifecycle.State.STARTED);

        mPreference.performClick();

        verify(mControllerHelper.getMockFragmentController()).showDialog(
                any(LocalRenameDialogFragment.class), eq(LocalRenameDialogFragment.TAG));
    }

    @Test
    public void preferenceClicked_handled() {
        mControllerHelper.markState(Lifecycle.State.STARTED);

        assertThat(
                mPreference.getOnPreferenceClickListener().onPreferenceClick(mPreference)).isTrue();
    }

    private ShadowBluetoothAdapter getShadowBluetoothAdapter() {
        return (ShadowBluetoothAdapter) Shadow.extract(BluetoothAdapter.getDefaultAdapter());
    }
}
