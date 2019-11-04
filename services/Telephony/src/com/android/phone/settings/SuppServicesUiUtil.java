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

package com.android.phone.settings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;

import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.phone.CarrierXmlParser;
import com.android.phone.GsmUmtsAdditionalCallOptions;
import com.android.phone.GsmUmtsCallOptions;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneUtils;
import com.android.phone.R;

import java.util.HashMap;

/**
 * Utility class to help supplementary service functions and UI.
 */
public class SuppServicesUiUtil {
    static final String LOG_TAG = "SuppServicesUiUtil";

    private static final String CLIR_ACTIVATE = "#31#";
    private static final String CLIR_DEACTIVATE = "*31#";

    /**
     * show dialog for supplementary services over ut precaution.
     *
     * @param context       The context.
     * @param phone         The Phone object.
     * @param preferenceKey The preference's key.
     */
    public static Dialog showBlockingSuppServicesDialog(Context context, Phone phone,
            String preferenceKey) {
        if (context == null || phone == null) {
            return null;
        }

        String message = makeMessage(context, preferenceKey, phone);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        DialogInterface.OnClickListener networkSettingsClickListener =
                new Dialog.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        ComponentName mobileNetworkSettingsComponent = new ComponentName(
                                context.getString(R.string.mobile_network_settings_package),
                                context.getString(R.string.mobile_network_settings_class));
                        intent.setComponent(mobileNetworkSettingsComponent);
                        context.startActivity(intent);
                    }
                };
        return builder.setMessage(message)
                .setNeutralButton(context.getResources().getString(
                        R.string.settings_label),
                        networkSettingsClickListener)
                .setPositiveButton(context.getResources().getString(
                        R.string.supp_service_over_ut_precautions_dialog_dismiss), null)
                .create();
    }

    private static String makeMessage(Context context, String preferenceKey, Phone phone) {
        String message = "";
        int simSlot = (phone.getPhoneId() == 0) ? 1 : 2;
        String suppServiceName = getSuppServiceName(context, preferenceKey);

        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        boolean isRoaming = telephonyManager.isNetworkRoaming(phone.getSubId());
        boolean isMultiSim = (telephonyManager.getSimCount() > 1);

        if (!isMultiSim) {
            if (isRoaming) {
                message = context.getResources().getString(
                        R.string.supp_service_over_ut_precautions_roaming, suppServiceName);
            } else {
                message = context.getResources().getString(
                        R.string.supp_service_over_ut_precautions, suppServiceName);
            }
        } else {
            if (isRoaming) {
                message = context.getResources().getString(
                        R.string.supp_service_over_ut_precautions_roaming_dual_sim, suppServiceName,
                        simSlot);
            } else {
                message = context.getResources().getString(
                        R.string.supp_service_over_ut_precautions_dual_sim, suppServiceName,
                        simSlot);
            }
        }
        return message;
    }

    private static String getSuppServiceName(Context context, String preferenceKey) {
        String suppServiceName = "";
        if (preferenceKey.equals(GsmUmtsCallOptions.CALL_FORWARDING_KEY)) {
            suppServiceName = context.getResources().getString(R.string.labelCF);
        } else if (preferenceKey.equals(GsmUmtsCallOptions.CALL_BARRING_KEY)) {
            suppServiceName = context.getResources().getString(R.string.labelCallBarring);
        } else if (preferenceKey.equals(GsmUmtsAdditionalCallOptions.BUTTON_CLIR_KEY)) {
            suppServiceName = context.getResources().getString(R.string.labelCallerId);
        } else if (preferenceKey.equals(GsmUmtsAdditionalCallOptions.BUTTON_CW_KEY)) {
            suppServiceName = context.getResources().getString(R.string.labelCW);
        }
        return suppServiceName;
    }

    /**
     * Check SS over Ut precautions in condition which is
     * "mobile data button is off" or "Roaming button is off during roaming".
     *
     * @param context The context.
     * @param phone   The Phone object.
     * @return "mobile data button is off" or "Roaming button is off during roaming", return true.
     */
    public static boolean isSsOverUtPrecautions(Context context, Phone phone) {
        if (phone == null || context == null) {
            return false;
        }
        return isMobileDataOff(context, phone) || isDataRoamingOffUnderRoaming(context, phone);
    }

    private static boolean isMobileDataOff(Context context, Phone phone) {
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return !telephonyManager.getDataEnabled(phone.getSubId());
    }

    private static boolean isDataRoamingOffUnderRoaming(Context context, Phone phone) {
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.isNetworkRoaming(phone.getSubId())
                && !phone.getDataRoamingEnabled();
    }

    /**
     * To handle caller id's ussd response message which sets caller id activate or deactivate,
     * and then sync caller id's ussd value to ss value if this command successful.
     *
     * @param context context to get strings.
     * @param mmiCode MMI result.
     * @return Text from response message is displayed on dialog .
     * @hide
     */
    public static CharSequence handleCallerIdUssdResponse(PhoneGlobals app, Context context,
            Phone phone, MmiCode mmiCode) {
        if (TextUtils.isEmpty(mmiCode.getDialString())) {
            return mmiCode.getMessage();
        }

        TelephonyManager telephonyManager = new TelephonyManager(context, phone.getSubId());
        int carrierId = telephonyManager.getSimCarrierId();
        if (carrierId == TelephonyManager.UNKNOWN_CARRIER_ID) {
            return mmiCode.getMessage();
        }

        CarrierXmlParser carrierXmlParser = new CarrierXmlParser(context, carrierId);
        CarrierXmlParser.SsEntry.SSAction ssAction = carrierXmlParser.getCallerIdUssdCommandAction(
                mmiCode.getDialString());
        Log.d(LOG_TAG, "handleCallerIdUssdResponse: ssAction =" + ssAction);

        if (ssAction == CarrierXmlParser.SsEntry.SSAction.UNKNOWN) {
            return mmiCode.getMessage();
        }

        HashMap<String, String> analysisResult = carrierXmlParser.getFeature(
                CarrierXmlParser.FEATURE_CALLER_ID)
                .getResponseSet(ssAction,
                        mmiCode.getMessage().toString());
        Log.d(LOG_TAG, "handleCallerIdUssdResponse: analysisResult =" + analysisResult);
        if (analysisResult.get(CarrierXmlParser.TAG_RESPONSE_STATUS).equals(
                CarrierXmlParser.TAG_COMMAND_RESULT_DEFINITION_OK)) {

            new Thread(new Runnable() {
                @Override
                public void run() {
                    TelephonyManager.UssdResponseCallback ussdCallback =
                            new TelephonyManager.UssdResponseCallback() {
                                @Override
                                public void onReceiveUssdResponse(
                                        final TelephonyManager telephonyManager,
                                        String request, CharSequence response) {
                                    Log.d(LOG_TAG, "handleCallerIdUssdResponse: response ="
                                            + response.toString());
                                    PhoneUtils.createUssdDialog(app, context, response.toString(),
                                            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                                }

                                @Override
                                public void onReceiveUssdResponseFailed(
                                        final TelephonyManager telephonyManager,
                                        String request, int failureCode) {
                                    Log.d(LOG_TAG, "handleCallerIdUssdResponse: failureCode ="
                                            + failureCode);
                                    PhoneUtils.createUssdDialog(app, context,
                                            context.getText(R.string.response_error),
                                            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                                }
                            };

                    String clir = "";
                    if (ssAction == CarrierXmlParser.SsEntry.SSAction.UPDATE_ACTIVATE) {
                        clir = CLIR_ACTIVATE;
                    } else {
                        clir = CLIR_DEACTIVATE;
                    }
                    TelephonyManager telephonyManager =
                            (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                    telephonyManager.sendUssdRequest(clir, ussdCallback, null);
                }
            }).start();

            return "";
        } else {
            return context.getText(
                    com.android.internal.R.string.mmiError);
        }
    }
}
