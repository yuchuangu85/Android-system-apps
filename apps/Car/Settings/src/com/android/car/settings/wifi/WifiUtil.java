/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.car.settings.wifi;

import android.annotation.DrawableRes;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.StringRes;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.settingslib.wifi.AccessPoint;

import java.util.regex.Pattern;

/**
 * A collections of util functions for WIFI.
 */
public class WifiUtil {

    private static final Logger LOG = new Logger(WifiUtil.class);

    /** Value that is returned when we fail to connect wifi. */
    public static final int INVALID_NET_ID = -1;
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9A-F]+$");

    @DrawableRes
    public static int getIconRes(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLING:
            case WifiManager.WIFI_STATE_DISABLED:
                return R.drawable.ic_settings_wifi_disabled;
            default:
                return R.drawable.ic_settings_wifi;
        }
    }

    public static boolean isWifiOn(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLING:
            case WifiManager.WIFI_STATE_DISABLED:
                return false;
            default:
                return true;
        }
    }

    /**
     * @return 0 if no proper description can be found.
     */
    @StringRes
    public static Integer getStateDesc(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLING:
                return R.string.wifi_starting;
            case WifiManager.WIFI_STATE_DISABLING:
                return R.string.wifi_stopping;
            case WifiManager.WIFI_STATE_DISABLED:
                return R.string.wifi_disabled;
            default:
                return 0;
        }
    }

    /**
     * Returns {@Code true} if wifi is available on this device.
     */
    public static boolean isWifiAvailable(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI);
    }

    /**
     * Gets a unique key for a {@link AccessPoint}.
     */
    public static String getKey(AccessPoint accessPoint) {
        return String.valueOf(accessPoint.hashCode());
    }

    /**
     * This method is a stripped and negated version of WifiConfigStore.canModifyNetwork.
     *
     * @param context Context of caller
     * @param config  The WiFi config.
     * @return {@code true} if Settings cannot modify the config due to lockDown.
     */
    public static boolean isNetworkLockedDown(Context context, WifiConfiguration config) {
        if (config == null) {
            return false;
        }

        final DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        final PackageManager pm = context.getPackageManager();

        // Check if device has DPM capability. If it has and dpm is still null, then we
        // treat this case with suspicion and bail out.
        if (pm.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN) && dpm == null) {
            return true;
        }

        boolean isConfigEligibleForLockdown = false;
        if (dpm != null) {
            final ComponentName deviceOwner = dpm.getDeviceOwnerComponentOnAnyUser();
            if (deviceOwner != null) {
                final int deviceOwnerUserId = dpm.getDeviceOwnerUserId();
                try {
                    final int deviceOwnerUid = pm.getPackageUidAsUser(deviceOwner.getPackageName(),
                            deviceOwnerUserId);
                    isConfigEligibleForLockdown = deviceOwnerUid == config.creatorUid;
                } catch (PackageManager.NameNotFoundException e) {
                    // don't care
                }
            }
        }
        if (!isConfigEligibleForLockdown) {
            return false;
        }

        final ContentResolver resolver = context.getContentResolver();
        final boolean isLockdownFeatureEnabled = Settings.Global.getInt(resolver,
                Settings.Global.WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN, 0) != 0;
        return isLockdownFeatureEnabled;
    }

    /**
     * Returns {@code true} if the provided NetworkCapabilities indicate a captive portal network.
     */
    public static boolean canSignIntoNetwork(NetworkCapabilities capabilities) {
        return (capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL));
    }

    /**
     * Returns netId. -1 if connection fails.
     */
    public static int connectToAccessPoint(Context context, String ssid, int security,
            String password, boolean hidden) {
        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = String.format("\"%s\"", ssid);
        wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        wifiConfig.hiddenSSID = hidden;
        switch (security) {
            case AccessPoint.SECURITY_NONE:
                wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                wifiConfig.allowedAuthAlgorithms.clear();
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                break;
            case AccessPoint.SECURITY_WEP:
                wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
                wifiConfig.wepKeys[0] = isHexString(password) ? password
                        : "\"" + password + "\"";
                wifiConfig.wepTxKeyIndex = 0;
                break;
            case AccessPoint.SECURITY_PSK:
            case AccessPoint.SECURITY_EAP:
                wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                wifiConfig.preSharedKey = String.format("\"%s\"", password);
                break;
            default:
                throw new IllegalArgumentException("invalid security type");
        }
        int netId = wifiManager.addNetwork(wifiConfig);
        // This only means wifiManager failed writing the new wifiConfig to the db. It doesn't mean
        // the network is invalid.
        if (netId == INVALID_NET_ID) {
            Toast.makeText(context, R.string.wifi_failed_connect_message,
                    Toast.LENGTH_SHORT).show();
        } else {
            wifiManager.enableNetwork(netId, true);
        }
        return netId;
    }

    /** Forget the network specified by {@code accessPoint}. */
    public static void forget(Context context, AccessPoint accessPoint) {
        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        if (!accessPoint.isSaved()) {
            if (accessPoint.getNetworkInfo() != null
                    && accessPoint.getNetworkInfo().getState() != NetworkInfo.State.DISCONNECTED) {
                // Network is active but has no network ID - must be ephemeral.
                wifiManager.disableEphemeralNetwork(
                        AccessPoint.convertToQuotedString(accessPoint.getSsidStr()));
            } else {
                // Should not happen, but a monkey seems to trigger it
                LOG.e("Failed to forget invalid network " + accessPoint.getConfig());
                return;
            }
        } else {
            wifiManager.forget(accessPoint.getConfig().networkId,
                    new ActionFailedListener(context, R.string.wifi_failed_forget_message));
        }
    }

    /** Returns {@code true} if the access point was disabled due to the wrong password. */
    public static boolean isAccessPointDisabledByWrongPassword(AccessPoint accessPoint) {
        WifiConfiguration config = accessPoint.getConfig();
        if (config == null) {
            return false;
        }
        WifiConfiguration.NetworkSelectionStatus networkStatus =
                config.getNetworkSelectionStatus();
        if (networkStatus == null || networkStatus.isNetworkEnabled()) {
            return false;
        }
        return networkStatus.getNetworkSelectionDisableReason()
                == WifiConfiguration.NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD;
    }

    private static boolean isHexString(String password) {
        return HEX_PATTERN.matcher(password).matches();
    }

    /**
     * A shared implementation of {@link WifiManager.ActionListener} which shows a failure message
     * in a toast.
     */
    public static class ActionFailedListener implements WifiManager.ActionListener {
        private final Context mContext;
        @StringRes
        private final int mFailureMessage;

        public ActionFailedListener(Context context, @StringRes int failureMessage) {
            mContext = context;
            mFailureMessage = failureMessage;
        }

        @Override
        public void onSuccess() {
        }

        @Override
        public void onFailure(int reason) {
            Toast.makeText(mContext, mFailureMessage, Toast.LENGTH_SHORT).show();
        }
    }
}
