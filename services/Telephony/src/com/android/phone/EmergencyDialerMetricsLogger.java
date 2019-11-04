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

package com.android.phone;

import android.annotation.IntDef;
import android.app.KeyguardManager;
import android.content.Context;
import android.metrics.LogMaker;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * EmergencyCallMetricsLogger is a utility to collect metrics of emergency calls
 */
class EmergencyDialerMetricsLogger {
    private static final String LOG_TAG = "EmergencyDialerLogger";

    @IntDef({
            DialedFrom.TRADITIONAL_DIALPAD,
            DialedFrom.SHORTCUT,
            DialedFrom.FASTER_LAUNCHER_DIALPAD,
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface DialedFrom {
        int TRADITIONAL_DIALPAD = 0;
        int SHORTCUT = 1;
        int FASTER_LAUNCHER_DIALPAD = 2;
    }

    @IntDef({
            LaunchedFrom.UNDEFINED,
            LaunchedFrom.LOCK_SCREEN,
            LaunchedFrom.POWER_KEY_MENU,
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface LaunchedFrom {
        int UNDEFINED = 0;
        int LOCK_SCREEN = 1;
        int POWER_KEY_MENU = 2;
    }

    @IntDef({
            PhoneNumberType.HAS_SHORTCUT,
            PhoneNumberType.NO_SHORTCUT,
            PhoneNumberType.NOT_EMERGENCY_NUMBER,
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface PhoneNumberType {
        int HAS_SHORTCUT = 0;
        int NO_SHORTCUT = 1;
        int NOT_EMERGENCY_NUMBER = 2;
    }

    @IntDef({
            UiModeErrorCode.UNSPECIFIED_ERROR,
            UiModeErrorCode.SUCCESS,
            UiModeErrorCode.CONFIG_ENTRY_POINT,
            UiModeErrorCode.CONFIG_SIM_OPERATOR,
            UiModeErrorCode.UNSUPPORTED_COUNTRY,
            UiModeErrorCode.AIRPLANE_MODE,
            UiModeErrorCode.NO_PROMOTED_NUMBER,
            UiModeErrorCode.NO_CAPABLE_PHONE,
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface UiModeErrorCode {
        int UNSPECIFIED_ERROR = -1;
        int SUCCESS = 0;
        int CONFIG_ENTRY_POINT = 1;
        int CONFIG_SIM_OPERATOR = 2;
        int UNSUPPORTED_COUNTRY = 3;
        int AIRPLANE_MODE = 4;
        int NO_PROMOTED_NUMBER = 5;
        int NO_CAPABLE_PHONE = 6;
    }

    private class TelephonyInfo {
        private final String mNetworkCountryIso;
        private final String mNetworkOperator;

        TelephonyInfo(String networkCountryIso, String networkOperator) {
            mNetworkCountryIso = networkCountryIso;
            mNetworkOperator = networkOperator;
        }
    }

    private final MetricsLogger mMetricsLogger = new MetricsLogger();

    private final Context mAppContext;

    @LaunchedFrom
    private int mLaunchedFrom;
    @UiModeErrorCode
    private int mUiModeErrorCode;

    EmergencyDialerMetricsLogger(Context context) {
        mAppContext = context.getApplicationContext();
    }

    /**
     * Log when Emergency Dialer is launched.
     * - Where the user launch Emergency Dialer from.
     * - Whether shortcut view is enabled, and the reason why it's not enabled.
     *
     * @param entryType
     * @param uiModeErrorCode
     */
    public void logLaunchEmergencyDialer(int entryType,
            @UiModeErrorCode int uiModeErrorCode) {
        final @EmergencyDialerMetricsLogger.LaunchedFrom int launchedFrom;
        if (entryType == EmergencyDialer.ENTRY_TYPE_LOCKSCREEN_BUTTON) {
            launchedFrom = EmergencyDialerMetricsLogger.LaunchedFrom.LOCK_SCREEN;
        } else if (entryType == EmergencyDialer.ENTRY_TYPE_POWER_MENU) {
            launchedFrom = EmergencyDialerMetricsLogger.LaunchedFrom.POWER_KEY_MENU;
        } else {
            launchedFrom = EmergencyDialerMetricsLogger.LaunchedFrom.UNDEFINED;
        }

        mLaunchedFrom = launchedFrom;
        mUiModeErrorCode = uiModeErrorCode;
    }

    /**
     * Log when user tring to place an emergency call.
     * - Which UI (traditional dialpad, shortcut button, dialpad in shortcut view) the user place
     *   the call from.
     * - The number is promoted in shortcut view or not, or not even an emergency number?
     * - Whether the device is locked.
     * - Network country ISO and network operator.
     *
     * @param dialedFrom
     * @param phoneNumberType
     * @param phoneInfo
     */
    public void logPlaceCall(@DialedFrom int dialedFrom,
            @PhoneNumberType int phoneNumberType,
            @Nullable ShortcutViewUtils.PhoneInfo phoneInfo) {
        TelephonyInfo telephonyInfo = getTelephonyInfo(phoneNumberType, phoneInfo);
        final KeyguardManager keyguard = mAppContext.getSystemService(KeyguardManager.class);

        logBeforeMakeCall(dialedFrom, phoneNumberType, keyguard.isKeyguardLocked(),
                telephonyInfo.mNetworkCountryIso, telephonyInfo.mNetworkOperator);
    }

    private TelephonyInfo getTelephonyInfo(@PhoneNumberType int phoneNumberType,
            @Nullable ShortcutViewUtils.PhoneInfo phoneInfo) {
        final TelephonyManager telephonyManager = mAppContext.getSystemService(
                TelephonyManager.class);
        final TelephonyManager subTelephonyManager;
        final String networkCountryIso;
        final String networkOperator;
        if (phoneNumberType == PhoneNumberType.HAS_SHORTCUT && phoneInfo != null) {
            subTelephonyManager = telephonyManager.createForSubscriptionId(phoneInfo.getSubId());
            networkCountryIso = phoneInfo.getCountryIso();
        } else {
            // No specific phone to make this call. Take information of default network.
            subTelephonyManager = null;
            networkCountryIso = telephonyManager.getNetworkCountryIso();
        }
        if (subTelephonyManager != null) {
            networkOperator = subTelephonyManager.getNetworkOperator();
        } else {
            // This could be:
            // - No specific phone to make this call.
            // - Subscription changed! Maybe the device roamed to another network?
            // Take information of default network.
            networkOperator = telephonyManager.getNetworkOperator();
        }

        return new TelephonyInfo(networkCountryIso, networkOperator);
    }

    private void logBeforeMakeCall(@DialedFrom int dialedFrom,
            @PhoneNumberType int phoneNumberType,
            boolean isDeviceLocked,
            String networkCountryIso,
            String networkOperator) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "EmergencyDialer session: dialedFrom=" + dialFromToString(dialedFrom)
                    + ", launchedFrom=" + launchedFromToString(mLaunchedFrom)
                    + ", uimode=" + uiModeErrorCodeToString(mUiModeErrorCode)
                    + ", type=" + phoneNumberTypeToString(phoneNumberType)
                    + ", locked=" + isDeviceLocked
                    + ", country=" + networkCountryIso
                    + ", operator=" + networkOperator);
        }
        mMetricsLogger.write(new LogMaker(MetricsEvent.EMERGENCY_DIALER_MAKE_CALL_V2)
                .setType(MetricsEvent.TYPE_ACTION)
                .setSubtype(dialedFrom)
                .addTaggedData(MetricsEvent.FIELD_EMERGENCY_DIALER_LAUNCH_FROM, mLaunchedFrom)
                .addTaggedData(MetricsEvent.FIELD_EMERGENCY_DIALER_UI_MODE_ERROR_CODE,
                        mUiModeErrorCode)
                .addTaggedData(MetricsEvent.FIELD_EMERGENCY_DIALER_PHONE_NUMBER_TYPE,
                        phoneNumberType)
                .addTaggedData(MetricsEvent.FIELD_EMERGENCY_DIALER_IS_DEVICE_LOCKED,
                        isDeviceLocked ? 1 : 0)
                .addTaggedData(MetricsEvent.FIELD_EMERGENCY_DIALER_NETWORK_COUNTRY_ISO,
                        networkCountryIso)
                .addTaggedData(MetricsEvent.FIELD_EMERGENCY_DIALER_NETWORK_OPERATOR,
                        networkOperator)
                .addTaggedData(MetricsEvent.FIELD_EMERGENCY_DIALER_RADIO_VERSION,
                        Build.getRadioVersion())
        );
    }

    private String dialFromToString(@DialedFrom int dialedFrom) {
        switch (dialedFrom) {
            case DialedFrom.TRADITIONAL_DIALPAD:
                return "traditional";
            case DialedFrom.SHORTCUT:
                return "shortcut";
            case DialedFrom.FASTER_LAUNCHER_DIALPAD:
                return "dialpad";
            default:
                return "unknown_error";
        }
    }

    private String launchedFromToString(@LaunchedFrom int launchedFrom) {
        switch (launchedFrom) {
            case LaunchedFrom.UNDEFINED:
                return "undefined";
            case LaunchedFrom.LOCK_SCREEN:
                return "lockscreen";
            case LaunchedFrom.POWER_KEY_MENU:
                return "powermenu";
            default:
                return "unknown_error";
        }
    }

    private String phoneNumberTypeToString(@PhoneNumberType int phoneNumberType) {
        switch (phoneNumberType) {
            case PhoneNumberType.HAS_SHORTCUT:
                return "has_shortcut";
            case PhoneNumberType.NO_SHORTCUT:
                return "no_shortcut";
            case PhoneNumberType.NOT_EMERGENCY_NUMBER:
                return "not_emergency";
            default:
                return "unknown_error";
        }
    }

    private String uiModeErrorCodeToString(@UiModeErrorCode int uiModeErrorCode) {
        switch (uiModeErrorCode) {
            case UiModeErrorCode.UNSPECIFIED_ERROR:
                return "unspecified_error";
            case UiModeErrorCode.SUCCESS:
                return "success";
            case UiModeErrorCode.CONFIG_ENTRY_POINT:
                return "config_entry_point";
            case UiModeErrorCode.CONFIG_SIM_OPERATOR:
                return "config_sim_operator";
            case UiModeErrorCode.UNSUPPORTED_COUNTRY:
                return "unsupported_country";
            case UiModeErrorCode.AIRPLANE_MODE:
                return "airplane_mode";
            case UiModeErrorCode.NO_PROMOTED_NUMBER:
                return "no_promoted_number";
            case UiModeErrorCode.NO_CAPABLE_PHONE:
                return "no_capable_phone";
            default:
                return "unknown_error";
        }
    }
}
