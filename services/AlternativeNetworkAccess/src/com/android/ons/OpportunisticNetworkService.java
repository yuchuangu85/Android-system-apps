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

package com.android.ons;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.AvailableNetworkInfo;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.IOns;
import com.android.internal.telephony.ISetOpportunisticDataCallback;
import com.android.internal.telephony.IUpdateAvailableNetworksCallback;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyPermissions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * OpportunisticNetworkService implements ions.
 * It scans network and matches the results with opportunistic subscriptions.
 * Use the same to provide user opportunistic data in areas with corresponding networks
 */
public class OpportunisticNetworkService extends Service {
    @VisibleForTesting protected Context mContext;
    private TelephonyManager mTelephonyManager;
    @VisibleForTesting protected SubscriptionManager mSubscriptionManager;

    private final Object mLock = new Object();
    @VisibleForTesting protected boolean mIsEnabled;
    private ONSProfileSelector mProfileSelector;
    private SharedPreferences mSharedPref;
    @VisibleForTesting protected HashMap<String, ONSConfigInput> mONSConfigInputHashMap;

    private static final String TAG = "ONS";
    private static final String PREF_NAME = TAG;
    private static final String PREF_ENABLED = "isEnabled";
    private static final String SERVICE_NAME = "ions";
    private static final String CARRIER_APP_CONFIG_NAME = "carrierApp";
    private static final String SYSTEM_APP_CONFIG_NAME = "systemApp";
    private static final boolean DBG = true;
    /* message to indicate sim state update */
    private static final int MSG_SIM_STATE_CHANGE = 1;

    /**
     * Profile selection callback. Will be called once Profile selector decides on
     * the opportunistic data profile.
     */
    private ONSProfileSelector.ONSProfileSelectionCallback  mProfileSelectionCallback =
            new ONSProfileSelector.ONSProfileSelectionCallback() {

                @Override
                public void onProfileSelectionDone() {
                    logDebug("profile selection done");
                }
            };

    /** Broadcast receiver to get SIM card state changed event */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mHandler.sendEmptyMessage(MSG_SIM_STATE_CHANGE);
        }
    };

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SIM_STATE_CHANGE:
                    synchronized (mLock) {
                        handleSimStateChange();
                    }
                    break;
                default:
                    log("invalid message");
                    break;
            }
        }
    };

    private static boolean enforceModifyPhoneStatePermission(Context context) {
        if (context.checkCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }

        return false;
    }

    @VisibleForTesting
    protected void handleSimStateChange() {
        logDebug("SIM state changed");
        ONSConfigInput carrierAppConfigInput = mONSConfigInputHashMap.get(CARRIER_APP_CONFIG_NAME);
        if (carrierAppConfigInput == null) {
            return;
        }
        List<SubscriptionInfo> subscriptionInfos =
            mSubscriptionManager.getActiveSubscriptionInfoList(false);
        for (SubscriptionInfo subscriptionInfo : subscriptionInfos) {
            if (subscriptionInfo.getSubscriptionId() == carrierAppConfigInput.getPrimarySub()) {
                return;
            }
        }

        logDebug("Carrier subscription is not available, removing entry");
        mONSConfigInputHashMap.put(CARRIER_APP_CONFIG_NAME, null);
        if (!mIsEnabled) {
            return;
        }
        if (mONSConfigInputHashMap.get(SYSTEM_APP_CONFIG_NAME) != null) {
            mProfileSelector.startProfileSelection(
                    mONSConfigInputHashMap.get(SYSTEM_APP_CONFIG_NAME).getAvailableNetworkInfos(),
                    mONSConfigInputHashMap.get(
                            SYSTEM_APP_CONFIG_NAME).getAvailableNetworkCallback());
        }
    }

    private boolean hasOpportunisticSubPrivilege(String callingPackage, int subId) {
        return mTelephonyManager.hasCarrierPrivileges(subId)
                || mSubscriptionManager.canManageSubscription(
                mProfileSelector.getOpprotunisticSubInfo(subId), callingPackage);
    }

    private final IOns.Stub mBinder = new IOns.Stub() {
        /**
         * Enable or disable Opportunistic Network service.
         *
         * This method should be called to enable or disable
         * OpportunisticNetwork service on the device.
         *
         * <p>
         * Requires Permission:
         *   {@link android.Manifest.permission#MODIFY_PHONE_STATE MODIFY_PHONE_STATE}
         * Or the calling app has carrier privileges. @see #hasCarrierPrivileges
         *
         * @param enable enable(True) or disable(False)
         * @param callingPackage caller's package name
         * @return returns true if successfully set.
         */
        @Override
        public boolean setEnable(boolean enable, String callingPackage) {
            TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                    mContext, mSubscriptionManager.getDefaultSubscriptionId(), "setEnable");
            log("setEnable: " + enable);

            final long identity = Binder.clearCallingIdentity();
            try {
                enableOpportunisticNetwork(enable);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }

            return true;
        }

        /**
         * is Opportunistic Network service enabled
         *
         * This method should be called to determine if the Opportunistic Network service
         * is enabled
         *
         * <p>
         * Requires Permission:
         *   {@link android.Manifest.permission#READ_PRIVILEGED_PHONE_STATE}
         * Or the calling app has carrier privileges. @see #hasCarrierPrivileges
         *
         * @param callingPackage caller's package name
         */
        @Override
        public boolean isEnabled(String callingPackage) {
            TelephonyPermissions
                    .enforeceCallingOrSelfReadPrivilegedPhoneStatePermissionOrCarrierPrivilege(
                            mContext, mSubscriptionManager.getDefaultSubscriptionId(), "isEnabled");
            return mIsEnabled;
        }

        /**
         * Set preferred opportunistic data.
         *
         * <p>Requires that the calling app has carrier privileges on both primary and
         * secondary subscriptions (see
         * {@link #hasCarrierPrivileges}), or has permission
         * {@link android.Manifest.permission#MODIFY_PHONE_STATE MODIFY_PHONE_STATE}.
         * @param subId which opportunistic subscription
         * {@link SubscriptionManager#getOpportunisticSubscriptions} is preferred for cellular data.
         * Pass {@link SubscriptionManager#DEFAULT_SUBSCRIPTION_ID} to unset the preference
         * @param needValidation whether validation is needed before switch happens.
         * @param callback callback upon request completion.
         * @param callingPackage caller's package name
         *
         */
        public void setPreferredDataSubscriptionId(int subId, boolean needValidation,
                ISetOpportunisticDataCallback callbackStub, String callingPackage) {
            logDebug("setPreferredDataSubscriptionId subId:" + subId + "callingPackage: " + callingPackage);
            if (!enforceModifyPhoneStatePermission(mContext)) {
                TelephonyPermissions.enforceCallingOrSelfCarrierPrivilege(
                        mSubscriptionManager.getDefaultSubscriptionId(), "setPreferredDataSubscriptionId");
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    TelephonyPermissions.enforceCallingOrSelfCarrierPrivilege(subId,
                            "setPreferredDataSubscriptionId");
                }
            } else {
                if (mONSConfigInputHashMap.get(CARRIER_APP_CONFIG_NAME) != null) {
                    sendSetOpptCallbackHelper(callbackStub,
                        TelephonyManager.SET_OPPORTUNISTIC_SUB_VALIDATION_FAILED);
                    return;
                }
            }


            final long identity = Binder.clearCallingIdentity();
            try {
                mProfileSelector.selectProfileForData(subId, needValidation, callbackStub);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * Get preferred default data sub Id
         *
         * <p>Requires that the calling app has carrier privileges
         * (see {@link #hasCarrierPrivileges}),or has either
         * {@link android.Manifest.permission#READ_PRIVILEGED_PHONE_STATE} or.
         * {@link android.Manifest.permission#READ_PHONE_STATE} permission.
         * @return subId preferred opportunistic subscription id or
         * {@link SubscriptionManager#DEFAULT_SUBSCRIPTION_ID} if there are no preferred
         * subscription id
         *
         */
        public int getPreferredDataSubscriptionId(String callingPackage) {
            TelephonyPermissions
                    .checkCallingOrSelfReadPhoneState(mContext,
                            mSubscriptionManager.getDefaultSubscriptionId(),
                            callingPackage, "getPreferredDataSubscriptionId");
            final long identity = Binder.clearCallingIdentity();
            try {
                return mProfileSelector.getPreferredDataSubscriptionId();
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        /**
         * Update availability of a list of networks in the current location.
         *
         * This api should be called if the caller is aware of the availability of a network
         * at the current location. This information will be used by OpportunisticNetwork service
         * to decide to attach to the network. If an empty list is passed,
         * it is assumed that no network is available.
         *  @param availableNetworks is a list of available network information.
         *  @param callback callback upon request completion.
         *  @param callingPackage caller's package name
         * <p>
         * <p>Requires that the calling app has carrier privileges on both primary and
         * secondary subscriptions (see
         * {@link #hasCarrierPrivileges}), or has permission
         * {@link android.Manifest.permission#MODIFY_PHONE_STATE MODIFY_PHONE_STATE}.
         *
         */
        public void updateAvailableNetworks(List<AvailableNetworkInfo> availableNetworks,
                IUpdateAvailableNetworksCallback callbackStub, String callingPackage) {
            logDebug("updateAvailableNetworks: " + availableNetworks);
            /* check if system app */
            if (enforceModifyPhoneStatePermission(mContext)) {
                handleSystemAppAvailableNetworks(
                        (ArrayList<AvailableNetworkInfo>) availableNetworks, callbackStub);
            } else {
                /* check if the app has primary carrier permission */
                TelephonyPermissions.enforceCallingOrSelfCarrierPrivilege(
                        mSubscriptionManager.getDefaultSubscriptionId(), "updateAvailableNetworks");
                handleCarrierAppAvailableNetworks(
                        (ArrayList<AvailableNetworkInfo>) availableNetworks, callbackStub,
                        callingPackage);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        initialize(getBaseContext());

        /* register the service */
        if (ServiceManager.getService(SERVICE_NAME) == null) {
            ServiceManager.addService(SERVICE_NAME, mBinder);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }

        String action = intent.getAction();
        if (action == null) {
            return START_STICKY;
        }

        switch (action) {
            case ONSProfileSelector.ACTION_SUB_SWITCH: {
                if (mProfileSelector != null) {
                    mProfileSelector.onSubSwitchComplete(intent);
                }
                break;
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        log("Destroyed Successfully...");

    }

    /**
     * initialize ONS and register as service.
     * Read persistent state to update enable state
     * Start sub components if already enabled.
     * @param context context instance
     */
    @VisibleForTesting
    protected void initialize(Context context) {
        mContext = context;
        mTelephonyManager = TelephonyManager.from(mContext);
        mProfileSelector = new ONSProfileSelector(mContext, mProfileSelectionCallback);
        mSharedPref = mContext.createDeviceProtectedStorageContext().getSharedPreferences(
                PREF_NAME, Context.MODE_PRIVATE);
        mSubscriptionManager = (SubscriptionManager) mContext.getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        mONSConfigInputHashMap = new HashMap<String, ONSConfigInput>();
        mContext.registerReceiver(mBroadcastReceiver,
            new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED));
        enableOpportunisticNetwork(getPersistentEnableState());
    }

    private void handleCarrierAppAvailableNetworks(
            ArrayList<AvailableNetworkInfo> availableNetworks,
            IUpdateAvailableNetworksCallback callbackStub, String callingPackage) {
        if ((availableNetworks != null) && (availableNetworks.size() > 0)) {
            /* carrier apps should report only subscription */
            if (availableNetworks.size() > 1) {
                log("Carrier app should not pass more than one subscription");
                sendUpdateNetworksCallbackHelper(callbackStub,
                        TelephonyManager.UPDATE_AVAILABLE_NETWORKS_INVALID_ARGUMENTS);
                return;
            }

            if (!mProfileSelector.hasOpprotunisticSub(availableNetworks)) {
                log("No opportunistic subscriptions received");
                sendUpdateNetworksCallbackHelper(callbackStub,
                        TelephonyManager.UPDATE_AVAILABLE_NETWORKS_INVALID_ARGUMENTS);
                return;
            }

            for (AvailableNetworkInfo availableNetworkInfo : availableNetworks) {
                if (Binder.withCleanCallingIdentity(
                            () -> mSubscriptionManager.isActiveSubId(
                                    availableNetworkInfo.getSubId()))) {
                    TelephonyPermissions.enforceCallingOrSelfCarrierPrivilege(
                        availableNetworkInfo.getSubId(), "updateAvailableNetworks");
                } else {
                    /* check if the app has opportunistic carrier permission */
                    if (!hasOpportunisticSubPrivilege(callingPackage,
                        availableNetworkInfo.getSubId())) {
                        log("No carrier privilege for opportunistic subscription");
                        sendUpdateNetworksCallbackHelper(callbackStub,
                            TelephonyManager.UPDATE_AVAILABLE_NETWORKS_NO_CARRIER_PRIVILEGE);
                        return;
                    }
                }
            }

            final long identity = Binder.clearCallingIdentity();
            try {
                ONSConfigInput onsConfigInput = new ONSConfigInput(availableNetworks, callbackStub);
                onsConfigInput.setPrimarySub(
                        mSubscriptionManager.getDefaultVoiceSubscriptionInfo().getSubscriptionId());
                onsConfigInput.setPreferredDataSub(availableNetworks.get(0).getSubId());
                mONSConfigInputHashMap.put(CARRIER_APP_CONFIG_NAME, onsConfigInput);

                if (mIsEnabled) {
                    /*  if carrier is reporting availability, then it takes higher priority. */
                    mProfileSelector.startProfileSelection(availableNetworks, callbackStub);
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        } else {
            final long identity = Binder.clearCallingIdentity();
            try {
                mONSConfigInputHashMap.put(CARRIER_APP_CONFIG_NAME, null);
                if (!mIsEnabled) {
                    sendUpdateNetworksCallbackHelper(callbackStub,
                        TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS);
                    return;
                }
                /* if carrier is reporting unavailability, then decide whether to start
                   system app request or not. */
                if (mONSConfigInputHashMap.get(SYSTEM_APP_CONFIG_NAME) != null) {
                    sendUpdateNetworksCallbackHelper(callbackStub,
                            TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS);
                    mProfileSelector.startProfileSelection(
                            mONSConfigInputHashMap.get(SYSTEM_APP_CONFIG_NAME)
                                    .getAvailableNetworkInfos(),
                            mONSConfigInputHashMap.get(
                                    SYSTEM_APP_CONFIG_NAME).getAvailableNetworkCallback());
                } else {
                    mProfileSelector.stopProfileSelection(callbackStub);
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
    }

    private void sendUpdateNetworksCallbackHelper(IUpdateAvailableNetworksCallback callback, int result) {
        if (callback == null) return;
        try {
            callback.onComplete(result);
        } catch (RemoteException exception) {
            log("RemoteException " + exception);
        }
    }

    private void sendSetOpptCallbackHelper(ISetOpportunisticDataCallback callback, int result) {
        if (callback == null) return;
        try {
            callback.onComplete(result);
        } catch (RemoteException exception) {
            log("RemoteException " + exception);
        }
    }

    private void handleSystemAppAvailableNetworks(
            ArrayList<AvailableNetworkInfo> availableNetworks,
            IUpdateAvailableNetworksCallback callbackStub) {
        final long identity = Binder.clearCallingIdentity();
        try {
            if ((availableNetworks != null) && (availableNetworks.size() > 0)) {
                /* all subscriptions should be opportunistic subscriptions */
                if (!mProfileSelector.hasOpprotunisticSub(availableNetworks)) {
                    log("No opportunistic subscriptions received");
                    sendUpdateNetworksCallbackHelper(callbackStub,
                            TelephonyManager.UPDATE_AVAILABLE_NETWORKS_INVALID_ARGUMENTS);
                    return;
                }
                mONSConfigInputHashMap.put(SYSTEM_APP_CONFIG_NAME,
                        new ONSConfigInput(availableNetworks, callbackStub));

                /* reporting availability. proceed if carrier app has not requested any */
                if (mIsEnabled && mONSConfigInputHashMap.get(CARRIER_APP_CONFIG_NAME) == null) {
                    mProfileSelector.startProfileSelection(availableNetworks, callbackStub);
                }
            } else {
                if (!mIsEnabled) {
                    mONSConfigInputHashMap.put(SYSTEM_APP_CONFIG_NAME, null);
                    sendUpdateNetworksCallbackHelper(callbackStub,
                        TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS);
                    return;
                }
                /* reporting unavailability */
                mONSConfigInputHashMap.put(SYSTEM_APP_CONFIG_NAME, null);
                if (mONSConfigInputHashMap.get(CARRIER_APP_CONFIG_NAME) == null) {
                    mProfileSelector.stopProfileSelection(callbackStub);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private boolean getPersistentEnableState() {
        return mSharedPref.getBoolean(PREF_ENABLED, true);
    }

    private void updateEnableState(boolean enable) {
        mIsEnabled = enable;
        mSharedPref.edit().putBoolean(PREF_ENABLED, mIsEnabled).apply();
    }

    /**
     * update the enable state
     * start profile selection if enabled.
     * @param enable enable(true) or disable(false)
     */
    private void enableOpportunisticNetwork(boolean enable) {
        synchronized (mLock) {
            if (mIsEnabled != enable) {
                updateEnableState(enable);
                if (!mIsEnabled) {
                    mProfileSelector.stopProfileSelection(null);
                } else {
                    if (mONSConfigInputHashMap.get(CARRIER_APP_CONFIG_NAME) != null &&
                        mONSConfigInputHashMap.get(CARRIER_APP_CONFIG_NAME)
                            .getAvailableNetworkInfos() != null) {
                        mProfileSelector.startProfileSelection(
                            mONSConfigInputHashMap.get(CARRIER_APP_CONFIG_NAME)
                                .getAvailableNetworkInfos(),
                            mONSConfigInputHashMap.get(
                                CARRIER_APP_CONFIG_NAME).getAvailableNetworkCallback());
                    } else if (mONSConfigInputHashMap.get(SYSTEM_APP_CONFIG_NAME) != null &&
                        mONSConfigInputHashMap.get(SYSTEM_APP_CONFIG_NAME)
                            .getAvailableNetworkInfos() != null) {
                        mProfileSelector.startProfileSelection(
                            mONSConfigInputHashMap.get(SYSTEM_APP_CONFIG_NAME)
                                .getAvailableNetworkInfos(),
                            mONSConfigInputHashMap.get(
                                SYSTEM_APP_CONFIG_NAME).getAvailableNetworkCallback());
                    }
                }
            }
        }
        logDebug("service is enable state " + mIsEnabled);
    }

    private void log(String msg) {
        Rlog.d(TAG, msg);
    }

    private void logDebug(String msg) {
        if (DBG) Rlog.d(TAG, msg);
    }
}
