/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.settings.network;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.lifecycle.Lifecycle;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.BaseTestActivity;
import com.android.car.settings.testutils.ShadowCarrierConfigManager;
import com.android.car.settings.testutils.ShadowTelephonyManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowDialog;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowTelephonyManager.class, ShadowCarrierConfigManager.class})
public class RoamingPreferenceControllerTest {

    private static final int SUB_ID = 1;

    private Context mContext;
    private TwoStatePreference mPreference;
    private PreferenceControllerTestHelper<RoamingPreferenceController> mControllerHelper;
    private RoamingPreferenceController mController;
    private TelephonyManager mTelephonyManager;
    private CarrierConfigManager mCarrierConfigManager;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                RoamingPreferenceController.class);
        mController = mControllerHelper.getController();
        mCarrierConfigManager = mContext.getSystemService(CarrierConfigManager.class);
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        getShadowTelephonyManager().setTelephonyManagerForSubscriptionId(SUB_ID, mTelephonyManager);
        mController.setSubId(SUB_ID);

        mPreference = new SwitchPreference(mContext);
        mControllerHelper.setPreference(mPreference);
    }

    @After
    public void tearDown() {
        ShadowTelephonyManager.reset();
    }

    @Test
    public void onStart_registerObserver() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(getShadowContentResolver().getContentObservers(
                Settings.Global.getUriFor(Settings.Global.DATA_ROAMING))).isNotEmpty();
    }

    @Test
    public void onStart_registerObserver_additionalSubId() {
        ShadowTelephonyManager.setSimCount(2);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(getShadowContentResolver().getContentObservers(
                Settings.Global.getUriFor(Settings.Global.DATA_ROAMING + SUB_ID))).isNotEmpty();
    }

    @Test
    public void onStop_unregisterObserver() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        assertThat(getShadowContentResolver().getContentObservers(
                Settings.Global.getUriFor(Settings.Global.DATA_ROAMING))).isEmpty();
    }

    @Test
    public void refreshUi_invalidSubId_isDisabled() {
        getShadowTelephonyManager().setTelephonyManagerForSubscriptionId(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID, mTelephonyManager);
        mController.setSubId(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mController.refreshUi();

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_validSubId_isEnabled() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mController.refreshUi();

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_roamingEnabled_setChecked() {
        mTelephonyManager.setDataRoamingEnabled(true);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mController.refreshUi();

        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void refreshUi_roamingDisabled_setUnchecked() {
        mTelephonyManager.setDataRoamingEnabled(false);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mController.refreshUi();

        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void callChangeListener_toggleFalse_turnOffRoaming() {
        mPreference.setChecked(true);
        mTelephonyManager.setDataRoamingEnabled(true);

        mPreference.callChangeListener(false);

        assertThat(mTelephonyManager.isDataRoamingEnabled()).isFalse();
    }

    @Test
    public void callChangeListener_toggleTrue_needsDialog_showDialog() {
        mPreference.setChecked(false);
        mTelephonyManager.setDataRoamingEnabled(false);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_DISABLE_CHARGE_INDICATION_BOOL, false);
        getShadowCarrierConfigManager().setConfigForSubId(SUB_ID, bundle);

        mPreference.callChangeListener(true);

        verify(mControllerHelper.getMockFragmentController()).showDialog(
                any(ConfirmationDialogFragment.class), eq(ConfirmationDialogFragment.TAG));
    }

    @Test
    public void confirmDialog_turnOnRoaming() {
        mPreference.setChecked(false);
        mTelephonyManager.setDataRoamingEnabled(false);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_DISABLE_CHARGE_INDICATION_BOOL, false);
        getShadowCarrierConfigManager().setConfigForSubId(SUB_ID, bundle);

        mPreference.callChangeListener(true);

        // Capture the dialog that is shown on toggle.
        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(
                dialogCaptor.capture(), eq(ConfirmationDialogFragment.TAG));

        // Show the captured dialog on press the confirmation button.
        ConfirmationDialogFragment dialog = dialogCaptor.getValue();
        assertThat(dialogCaptor).isNotNull();
        AlertDialog alertDialog = showDialog(dialog);
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

        assertThat(mTelephonyManager.isDataRoamingEnabled()).isTrue();
    }

    @Test
    public void callChangeListener_toggleTrue_doesntNeedDialog_turnOnRoaming() {
        mPreference.setChecked(false);
        mTelephonyManager.setDataRoamingEnabled(false);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_DISABLE_CHARGE_INDICATION_BOOL, true);
        getShadowCarrierConfigManager().setConfigForSubId(SUB_ID, bundle);

        mPreference.callChangeListener(true);

        assertThat(mTelephonyManager.isDataRoamingEnabled()).isTrue();
    }

    private ShadowContentResolver getShadowContentResolver() {
        return (ShadowContentResolver) Shadows.shadowOf(mContext.getContentResolver());
    }

    private ShadowTelephonyManager getShadowTelephonyManager() {
        return (ShadowTelephonyManager) Shadows.shadowOf(mTelephonyManager);
    }

    private ShadowCarrierConfigManager getShadowCarrierConfigManager() {
        return (ShadowCarrierConfigManager) Shadow.extract(mCarrierConfigManager);
    }

    private AlertDialog showDialog(ConfirmationDialogFragment dialog) {
        BaseTestActivity activity = Robolectric.setupActivity(BaseTestActivity.class);
        activity.showDialog(dialog, /* tag= */ null);
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }
}
