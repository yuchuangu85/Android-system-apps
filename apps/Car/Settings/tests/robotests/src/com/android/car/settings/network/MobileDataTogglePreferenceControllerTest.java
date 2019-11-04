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
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.lifecycle.Lifecycle;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.BaseTestActivity;
import com.android.car.settings.testutils.ShadowSubscriptionManager;
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
@Config(shadows = {ShadowTelephonyManager.class, ShadowSubscriptionManager.class})
public class MobileDataTogglePreferenceControllerTest {

    private static final int SUB_ID = 1;

    private Context mContext;
    private TwoStatePreference mPreference;
    private PreferenceControllerTestHelper<MobileDataTogglePreferenceController>
            mControllerHelper;
    private MobileDataTogglePreferenceController mController;
    private TelephonyManager mTelephonyManager;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                MobileDataTogglePreferenceController.class);
        mController = mControllerHelper.getController();
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mTelephonyManager.setDataEnabled(false);

        getShadowTelephonyManager().setTelephonyManagerForSubscriptionId(SUB_ID, mTelephonyManager);
        mController.setSubId(SUB_ID);

        mPreference = new SwitchPreference(mContext);
        mControllerHelper.setPreference(mPreference);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @After
    public void tearDown() {
        ShadowTelephonyManager.reset();
        ShadowSubscriptionManager.reset();
    }

    @Test
    public void onStart_singleSim_registersObserver() {
        ShadowTelephonyManager.setSimCount(1);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(getShadowContentResolver().getContentObservers(
                Settings.Global.getUriFor(Settings.Global.MOBILE_DATA))).isNotEmpty();
    }

    @Test
    public void onStart_multiSim_registersObserver() {
        ShadowTelephonyManager.setSimCount(2);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(getShadowContentResolver().getContentObservers(
                Settings.Global.getUriFor(Settings.Global.MOBILE_DATA + SUB_ID))).isNotEmpty();
    }

    @Test
    public void onStop_singleSim_unregistersObserver() {
        ShadowTelephonyManager.setSimCount(1);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        assertThat(getShadowContentResolver().getContentObservers(
                Settings.Global.getUriFor(Settings.Global.MOBILE_DATA))).isEmpty();
    }

    @Test
    public void refreshUi_isOpportunistic_isDisabled() {
        getShadowSubscriptionManager().setActiveSubscriptionInfos(
                createSubscriptionInfo(SUB_ID, /* isOpportunistic= */ true));
        mController.refreshUi();

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_isNotOpportunistic_isEnabled() {
        getShadowSubscriptionManager().setActiveSubscriptionInfos(
                createSubscriptionInfo(SUB_ID, /* isOpportunistic= */ false));
        mController.refreshUi();

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_dataEnabled_setChecked() {
        mTelephonyManager.setDataEnabled(true);
        mController.refreshUi();

        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void refreshUi_dataDisabled_setUnchecked() {
        mTelephonyManager.setDataEnabled(false);
        mController.refreshUi();

        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void handlePreferenceChanged_setFalse_isSingleSim_opensDialog() {
        ShadowTelephonyManager.setSimCount(1);
        mPreference.callChangeListener(false);

        verify(mControllerHelper.getMockFragmentController()).showDialog(
                any(ConfirmationDialogFragment.class),
                eq(MobileDataTogglePreferenceController.DISABLE_DIALOG_TAG));
    }

    @Test
    public void handlePreferenceChanged_setFalse_isMultiSim_disablesData() {
        mPreference.setChecked(true);
        mTelephonyManager.setDataEnabled(true);
        ShadowTelephonyManager.setSimCount(2);
        mPreference.callChangeListener(false);

        assertThat(mTelephonyManager.isDataEnabled()).isFalse();
    }

    @Test
    public void handlePreferenceChanged_setFalse_isMultiSim_setsUnchecked() {
        mPreference.setChecked(true);
        mTelephonyManager.setDataEnabled(true);
        ShadowTelephonyManager.setSimCount(2);
        mPreference.callChangeListener(false);

        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void handlePreferenceChanged_setTrue_isSingleSim_enablesData() {
        mPreference.setChecked(false);
        mTelephonyManager.setDataEnabled(false);
        ShadowTelephonyManager.setSimCount(1);
        mPreference.callChangeListener(true);

        assertThat(mTelephonyManager.isDataEnabled()).isTrue();
    }

    @Test
    public void handlePreferenceChanged_setTrue_isSingleSim_setsChecked() {
        mPreference.setChecked(false);
        mTelephonyManager.setDataEnabled(false);
        ShadowTelephonyManager.setSimCount(1);
        mPreference.callChangeListener(true);

        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void handlePreferenceChanged_setTrue_isMultiSim_noOtherSimActive_enablesData() {
        mPreference.setChecked(false);
        mTelephonyManager.setDataEnabled(false);
        ShadowTelephonyManager.setSimCount(2);
        ShadowSubscriptionManager.setDefaultDataSubscriptionId(SUB_ID);
        getShadowSubscriptionManager().setCurrentActiveSubscriptionId(SUB_ID);
        mPreference.callChangeListener(true);

        assertThat(mTelephonyManager.isDataEnabled()).isTrue();
    }

    @Test
    public void handlePreferenceChanged_setTrue_isMultiSim_noOtherSimActive_setsChecked() {
        mPreference.setChecked(false);
        mTelephonyManager.setDataEnabled(false);
        ShadowTelephonyManager.setSimCount(2);
        ShadowSubscriptionManager.setDefaultDataSubscriptionId(SUB_ID);
        getShadowSubscriptionManager().setCurrentActiveSubscriptionId(SUB_ID);
        mPreference.callChangeListener(true);

        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void handlePreferenceChanged_setTrue_isMultiSim_otherSimActive_opensDialog() {
        int otherSubId = SUB_ID + 1;
        mPreference.setChecked(false);
        mTelephonyManager.setDataEnabled(false);
        ShadowTelephonyManager.setSimCount(2);
        ShadowSubscriptionManager.setDefaultDataSubscriptionId(otherSubId);
        getShadowSubscriptionManager().setCurrentActiveSubscriptionId(otherSubId);
        mPreference.callChangeListener(true);

        verify(mControllerHelper.getMockFragmentController()).showDialog(
                any(ConfirmationDialogFragment.class),
                eq(MobileDataTogglePreferenceController.ENABLE_MULTISIM_DIALOG_TAG));
    }

    @Test
    public void disableSingleSimDialog_onConfirm_disablesData() {
        mPreference.setChecked(true);
        mTelephonyManager.setDataEnabled(true);
        ShadowTelephonyManager.setSimCount(1);
        mPreference.callChangeListener(false);

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(
                dialogCaptor.capture(),
                eq(MobileDataTogglePreferenceController.DISABLE_DIALOG_TAG));

        ConfirmationDialogFragment dialog = dialogCaptor.getValue();
        assertThat(dialogCaptor).isNotNull();
        AlertDialog alertDialog = showDialog(dialog);
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

        assertThat(mTelephonyManager.isDataEnabled()).isFalse();
    }

    @Test
    public void disableSingleSimDialog_onConfirm_setsUnchecked() {
        mPreference.setChecked(true);
        mTelephonyManager.setDataEnabled(true);
        ShadowTelephonyManager.setSimCount(1);
        mPreference.callChangeListener(false);

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(
                dialogCaptor.capture(),
                eq(MobileDataTogglePreferenceController.DISABLE_DIALOG_TAG));

        ConfirmationDialogFragment dialog = dialogCaptor.getValue();
        assertThat(dialogCaptor).isNotNull();
        AlertDialog alertDialog = showDialog(dialog);
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void enableMutliSimDialog_onConfirm_enablesData() {
        int otherSubId = SUB_ID + 1;
        mPreference.setChecked(false);
        mTelephonyManager.setDataEnabled(false);
        ShadowTelephonyManager.setSimCount(2);
        ShadowSubscriptionManager.setDefaultDataSubscriptionId(otherSubId);
        getShadowSubscriptionManager().setCurrentActiveSubscriptionId(otherSubId);
        mPreference.callChangeListener(true);

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(
                dialogCaptor.capture(),
                eq(MobileDataTogglePreferenceController.ENABLE_MULTISIM_DIALOG_TAG));

        ConfirmationDialogFragment dialog = dialogCaptor.getValue();
        assertThat(dialogCaptor).isNotNull();
        AlertDialog alertDialog = showDialog(dialog);
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

        assertThat(mTelephonyManager.isDataEnabled()).isTrue();
    }

    @Test
    public void enableMutliSimDialog_onConfirm_setsChecked() {
        int otherSubId = SUB_ID + 1;
        mPreference.setChecked(false);
        mTelephonyManager.setDataEnabled(false);
        ShadowTelephonyManager.setSimCount(2);
        ShadowSubscriptionManager.setDefaultDataSubscriptionId(otherSubId);
        getShadowSubscriptionManager().setCurrentActiveSubscriptionId(otherSubId);
        mPreference.callChangeListener(true);

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(
                dialogCaptor.capture(),
                eq(MobileDataTogglePreferenceController.ENABLE_MULTISIM_DIALOG_TAG));

        ConfirmationDialogFragment dialog = dialogCaptor.getValue();
        assertThat(dialogCaptor).isNotNull();
        AlertDialog alertDialog = showDialog(dialog);
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

        assertThat(mPreference.isChecked()).isTrue();
    }

    private ShadowTelephonyManager getShadowTelephonyManager() {
        return (ShadowTelephonyManager) Shadows.shadowOf(mTelephonyManager);
    }

    private ShadowContentResolver getShadowContentResolver() {
        return (ShadowContentResolver) Shadows.shadowOf(mContext.getContentResolver());
    }

    private ShadowSubscriptionManager getShadowSubscriptionManager() {
        return Shadow.extract(mContext.getSystemService(SubscriptionManager.class));
    }

    private SubscriptionInfo createSubscriptionInfo(int subId, boolean isOpportunistic) {
        SubscriptionInfo subInfo = new SubscriptionInfo(subId, /* iccId= */ "",
                /* simSlotIndex= */ 0, /* displayName= */ "", /* carrierName= */ "",
                /* nameSource= */ 0, /* iconTint= */ 0, /* number= */ "",
                /* roaming= */ 0, /* icon= */ null, /* mcc= */ "", /* mnc= */ "",
                /* countryIso= */ "", /* isEmbedded= */ false,
                /* accessRules= */ null, /* cardString= */ "", isOpportunistic,
                /* groupUUID= */ null, /* carrierId= */ 0, /* profileClass= */ 0);
        return subInfo;
    }

    private AlertDialog showDialog(ConfirmationDialogFragment dialog) {
        BaseTestActivity activity = Robolectric.setupActivity(BaseTestActivity.class);
        activity.showDialog(dialog, /* tag= */ null);
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }
}
