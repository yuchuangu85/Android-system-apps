/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.system;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;
import static org.testng.Assert.assertThrows;

import android.content.ContentResolver;
import android.content.Context;
import android.net.INetworkPolicyManager.Default;
import android.net.NetworkPolicyManager;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;
import android.widget.Button;

import androidx.preference.PreferenceManager;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseTestActivity;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;
import com.android.car.settings.testutils.ShadowConnectivityManager;
import com.android.car.settings.testutils.ShadowNetworkPolicyManager;
import com.android.car.settings.testutils.ShadowRecoverySystem;
import com.android.car.settings.testutils.ShadowTelephonyManager;
import com.android.car.settings.testutils.ShadowWifiManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowContextImpl;

import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {
        ShadowConnectivityManager.class,
        ShadowWifiManager.class,
        ShadowBluetoothAdapter.class,
        ShadowTelephonyManager.class,
        ShadowNetworkPolicyManager.class,
        ShadowRecoverySystem.class
})
public class ResetNetworkConfirmFragmentTest {

    private Context mContext;
    private Button mResetButton;
    private ContentResolver mContentResolver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;

        final NetworkPolicyManager npm = new NetworkPolicyManager(mContext, new Default());
        ShadowContextImpl shadowContext =
                Shadow.extract(RuntimeEnvironment.application.getBaseContext());
        shadowContext.setSystemService(Context.NETWORK_POLICY_SERVICE, npm);

        mContentResolver = mContext.getContentResolver();

        setEuiccResetCheckbox(false);
        setNetworkSubscriptionId("");

        BaseTestActivity testActivity = Robolectric.setupActivity(BaseTestActivity.class);

        ResetNetworkConfirmFragment fragment = new ResetNetworkConfirmFragment();
        testActivity.launchFragment(fragment);

        mResetButton = testActivity.findViewById(R.id.action_button1);
    }

    @After
    public void tearDown() {
        ShadowConnectivityManager.reset();
        ShadowWifiManager.reset();
        ShadowBluetoothAdapter.reset();
        ShadowRecoverySystem.reset();
        ShadowTelephonyManager.reset();
        ShadowNetworkPolicyManager.reset();
        ShadowContentResolver.reset();
    }

    @Test
    public void testResetButtonClick_connectivityManagerReset() {
        mResetButton.performClick();
        assertThat(ShadowConnectivityManager.verifyFactoryResetCalled(/* numTimes */ 1)).isTrue();
    }

    @Test
    public void testResetButtonClick_wifiManagerReset() {
        mResetButton.performClick();
        assertThat(ShadowWifiManager.verifyFactoryResetCalled(/* numTimes */ 1)).isTrue();
    }

    @Test
    public void testResetButtonClick_bluetoothAdapterReset() {
        mResetButton.performClick();
        assertThat(ShadowBluetoothAdapter.verifyFactoryResetCalled(/* numTimes */ 1)).isTrue();
    }

    @Test
    public void testResetButtonClick_cleanSmsRawTable() {
        mResetButton.performClick();
        assertThat(getUriWithGivenPrefix(shadowOf(mContentResolver).getDeletedUris(),
                Telephony.Sms.CONTENT_URI)).isNotNull();
    }

    @Test
    public void testResetButtonClick_euiccResetEnabled_euiccReset() {
        setEuiccResetCheckbox(true);
        mResetButton.performClick();
        assertThat(ShadowRecoverySystem.verifyWipeEuiccDataCalled(/* numTimes */ 1)).isTrue();
    }

    @Test
    public void testResetButtonClick_euiccResetDisabled_euiccNotReset() {
        setEuiccResetCheckbox(false);
        mResetButton.performClick();
        assertThat(ShadowRecoverySystem.verifyWipeEuiccDataCalled(/* numTimes */ 1)).isFalse();
    }

    @Test
    public void testResetButtonClick_nonIntegerNetworkSubscriptionId_numberExceptionError() {
        setNetworkSubscriptionId("abc");
        assertThrows(NumberFormatException.class, () -> mResetButton.performClick());
    }

    @Test
    public void testResetButtonClick_emptyNetworkSubscriptionId_telephonyNotReset() {
        setNetworkSubscriptionId("");
        mResetButton.performClick();
        assertThat(ShadowTelephonyManager.verifyFactoryResetCalled(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID, /* numTimes */ 1)).isTrue();
    }

    @Test
    public void testResetButtonClick_validNetworkSubscriptionId_telephonyReset() {
        setNetworkSubscriptionId("123");
        mResetButton.performClick();
        assertThat(ShadowTelephonyManager.verifyFactoryResetCalled(123, /* numTimes */ 1)).isTrue();
    }

    @Test
    public void testResetButtonClick_emptyNetworkSubscriptionId_networkManagerNotReset() {
        setNetworkSubscriptionId("");
        mResetButton.performClick();
        assertThat(ShadowNetworkPolicyManager.verifyFactoryResetCalled(null, /* numTimes */
                1)).isTrue();
    }

    @Test
    public void testResetButtonClick_validNetworkSubscriptionId_networkManagerReset() {
        setNetworkSubscriptionId("123");
        mResetButton.performClick();
        assertThat(ShadowNetworkPolicyManager.verifyFactoryResetCalled(
                ShadowTelephonyManager.SUBSCRIBER_ID, /* numTimes */ 1)).isTrue();
    }

    @Test
    public void testResetButtonClick_emptyNetworkSubscriptionId_noRestoreDefaultApn() {
        setNetworkSubscriptionId("");
        mResetButton.performClick();
        Uri uri = getUriWithGivenPrefix(shadowOf(mContentResolver).getDeletedUris(),
                ResetNetworkConfirmFragment.RESTORE_CARRIERS_URI);
        assertThat(uri).isNotNull();
        assertThat(uri.toString().contains("subId/")).isFalse();
    }

    @Test
    public void testResetButtonClick_validNetworkSubscriptionId_restoreDefaultApn() {
        setNetworkSubscriptionId("123");
        mResetButton.performClick();
        Uri uri = getUriWithGivenPrefix(shadowOf(mContentResolver).getDeletedUris(),
                ResetNetworkConfirmFragment.RESTORE_CARRIERS_URI);
        assertThat(uri).isNotNull();
        assertThat(uri.toString().contains("subId/123")).isTrue();
    }

    private Uri getUriWithGivenPrefix(List<Uri> uris, String prefix) {
        for (Uri uri : uris) {
            if (uri.toString().startsWith(prefix)) return uri;
        }
        return null;
    }

    private Uri getUriWithGivenPrefix(List<Uri> uris, Uri prefix) {
        for (Uri uri : uris) {
            if (uri.isPathPrefixMatch(prefix)) return uri;
        }
        return null;
    }

    private void setEuiccResetCheckbox(boolean isChecked) {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().putBoolean(
                mContext.getString(R.string.pk_reset_esim), isChecked).commit();
    }

    private void setNetworkSubscriptionId(String id) {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().putString(
                mContext.getString(R.string.pk_reset_network_subscription), id).commit();
    }
}
