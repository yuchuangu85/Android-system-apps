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

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkPolicyManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RecoverySystem;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.android.car.settings.R;
import com.android.car.settings.common.ErrorDialog;
import com.android.car.settings.common.SettingsFragment;

/**
 * Final warning presented to user to confirm restoring network settings to the factory default.
 * If a user confirms, all settings are reset for connectivity, Wi-Fi, and Bluetooth.
 */
public class ResetNetworkConfirmFragment extends SettingsFragment {

    // Copied from com.android.settings.network.ApnSettings.
    @VisibleForTesting
    static final String RESTORE_CARRIERS_URI = "content://telephony/carriers/restore";

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.reset_network_confirm_fragment;
    }

    @Override
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_button;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Button resetSettingsButton = requireActivity().findViewById(R.id.action_button1);
        resetSettingsButton.setText(
                requireContext().getString(R.string.reset_network_confirm_button_text));
        resetSettingsButton.setOnClickListener(v -> resetNetwork());
    }

    private void resetNetwork() {
        if (ActivityManager.isUserAMonkey()) {
            return;
        }

        Context context = requireActivity().getApplicationContext();

        ConnectivityManager connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            connectivityManager.factoryReset();
        }

        WifiManager wifiManager = (WifiManager)
                context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            wifiManager.factoryReset();
        }

        BluetoothManager btManager = (BluetoothManager)
                context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (btManager != null) {
            BluetoothAdapter btAdapter = btManager.getAdapter();
            if (btAdapter != null) {
                btAdapter.factoryReset();
            }
        }

        int networkSubscriptionId = getNetworkSubscriptionId();
        TelephonyManager telephonyManager = (TelephonyManager)
                context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            telephonyManager.factoryReset(networkSubscriptionId);
        }

        NetworkPolicyManager policyManager = (NetworkPolicyManager)
                context.getSystemService(Context.NETWORK_POLICY_SERVICE);
        if (policyManager != null) {
            String subscriberId = telephonyManager.getSubscriberId(networkSubscriptionId);
            policyManager.factoryReset(subscriberId);
        }

        restoreDefaultApn(context, networkSubscriptionId);

        // There has been issues when Sms raw table somehow stores orphan
        // fragments. They lead to garbled message when new fragments come
        // in and combined with those stale ones. In case this happens again,
        // user can reset all network settings which will clean up this table.
        cleanUpSmsRawTable(context);

        if (shouldResetEsim()) {
            new EraseEsimAsyncTask(getContext(), context.getPackageName(), this).execute();
        } else {
            showCompletionToast(getContext());
        }
    }

    private boolean shouldResetEsim() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                requireContext());
        return sharedPreferences.getBoolean(requireContext().getString(R.string.pk_reset_esim),
                false);
    }

    private int getNetworkSubscriptionId() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                requireContext());
        String stringId = sharedPreferences.getString(
                requireContext().getString(R.string.pk_reset_network_subscription), null);
        if (TextUtils.isEmpty(stringId)) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        return Integer.parseInt(stringId);
    }

    private void restoreDefaultApn(Context context, int subscriptionId) {
        Uri uri = Uri.parse(RESTORE_CARRIERS_URI);

        if (SubscriptionManager.isUsableSubIdValue(subscriptionId)) {
            uri = Uri.withAppendedPath(uri, "subId/" + subscriptionId);
        }

        ContentResolver resolver = context.getContentResolver();
        resolver.delete(uri, null, null);
    }

    private void cleanUpSmsRawTable(Context context) {
        ContentResolver resolver = context.getContentResolver();
        Uri uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "raw/permanentDelete");
        resolver.delete(uri, null, null);
    }

    private static void showCompletionToast(Context context) {
        Toast.makeText(context, R.string.reset_network_complete_toast,
                Toast.LENGTH_SHORT).show();
    }

    private static class EraseEsimAsyncTask extends AsyncTask<Void, Void, Boolean> {

        private final Context mContext;
        private final String mPackageName;
        private final Fragment mFragment;

        EraseEsimAsyncTask(Context context, String packageName, Fragment parent) {
            mContext = context;
            mPackageName = packageName;
            mFragment = parent;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            return RecoverySystem.wipeEuiccData(mContext, mPackageName);
        }

        @Override
        protected void onPostExecute(Boolean succeeded) {
            if (succeeded) {
                showCompletionToast(mContext);
            } else {
                ErrorDialog.show(mFragment, R.string.reset_esim_error_title);
            }
        }
    }
}
