/*
 * Copyright 2013 The Android Open Source Project
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

package com.android.phone;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;

public class ImsUtil {
    private static final String LOG_TAG = ImsUtil.class.getSimpleName();
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private static boolean sImsPhoneSupported = false;

    private ImsUtil() {
    }

    static {
        PhoneGlobals app = PhoneGlobals.getInstance();
        sImsPhoneSupported = true;
    }

    /**
     * @return {@code true} if this device supports voice calls using the built-in SIP stack.
     */
    static boolean isImsPhoneSupported() {
        return sImsPhoneSupported;

    }

    /**
     * @return {@code true} if WFC is supported by the platform and has been enabled by the user.
     */
    public static boolean isWfcEnabled(Context context) {
        return isWfcEnabled(context, SubscriptionManager.getDefaultVoicePhoneId());
    }

    /**
     * @return {@code true} if WFC is supported per Slot and has been enabled by the user.
     */
    public static boolean isWfcEnabled(Context context, int phoneId) {
        ImsManager imsManager = ImsManager.getInstance(context, phoneId);
        boolean isEnabledByPlatform = imsManager.isWfcEnabledByPlatform();
        boolean isEnabledByUser = imsManager.isWfcEnabledByUser();
        if (DBG) Log.d(LOG_TAG, "isWfcEnabled :: isEnabledByPlatform=" + isEnabledByPlatform
                + " phoneId=" + phoneId);
        if (DBG) Log.d(LOG_TAG, "isWfcEnabled :: isEnabledByUser=" + isEnabledByUser
                + " phoneId=" + phoneId);
        return isEnabledByPlatform && isEnabledByUser;
    }

    /**
     * @return {@code true} if the device is configured to use "Wi-Fi only" mode. If WFC is not
     * enabled, this will return {@code false}.
     */
    public static boolean isWfcModeWifiOnly(Context context) {
        return isWfcModeWifiOnly(context, SubscriptionManager.getDefaultVoicePhoneId());
    }

    /**
     * @return {@code true} if the Slot is configured to use "Wi-Fi only" mode. If WFC is not
     * enabled, this will return {@code false}.
     */
    public static boolean isWfcModeWifiOnly(Context context, int phoneId) {
        ImsManager imsManager = ImsManager.getInstance(context, phoneId);
        boolean isWifiOnlyMode =
                imsManager.getWfcMode() == ImsConfig.WfcModeFeatureValueConstants.WIFI_ONLY;
        if (DBG) Log.d(LOG_TAG, "isWfcModeWifiOnly :: isWifiOnlyMode" + isWifiOnlyMode
                + " phoneId=" + phoneId);
        return isWfcEnabled(context, phoneId) && isWifiOnlyMode;
    }

    /**
     * When a call cannot be placed, determines if the use of WFC should be promoted, per the
     * carrier config.  Use of WFC is promoted to the user if the device is connected to a WIFI
     * network, WFC is disabled but provisioned, and the carrier config indicates that the
     * features should be promoted.
     *
     * @return {@code true} if use of WFC should be promoted, {@code false} otherwise.
     */
    public static boolean shouldPromoteWfc(Context context) {
        return shouldPromoteWfc(context, SubscriptionManager.getDefaultVoicePhoneId());
    }

    /**
     * When a call cannot be placed, determines if the use of WFC should be promoted, per the
     * carrier config of the slot.  Use of WFC is promoted to the user if the device is
     * connected to a WIFI network, WFC is disabled but provisioned, and the carrier config
     * indicates that the features should be promoted.
     *
     * @return {@code true} if use of WFC should be promoted, {@code false} otherwise.
     */
    public static boolean shouldPromoteWfc(Context context, int phoneId) {
        CarrierConfigManager cfgManager = (CarrierConfigManager) context
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (cfgManager == null || !cfgManager.getConfigForSubId(getSubId(phoneId))
                .getBoolean(CarrierConfigManager.KEY_CARRIER_PROMOTE_WFC_ON_CALL_FAIL_BOOL)) {
            return false;
        }

        if (!getDefaultImsManagerInstance(context).isWfcProvisionedOnDevice()) {
            return false;
        }

        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) {
                return ni.getType() == ConnectivityManager.TYPE_WIFI && !isWfcEnabled(context,
                        phoneId);
            }
        }
        return false;
    }

    private static ImsManager getDefaultImsManagerInstance(Context context) {
        return ImsManager.getInstance(context, SubscriptionManager.getDefaultVoicePhoneId());
    }

    private static int getSubId(int phoneId) {
        final int[] subIds = SubscriptionManager.getSubId(phoneId);
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (subIds != null && subIds.length >= 1) {
            subId = subIds[0];
        }
        return subId;
    }
}
