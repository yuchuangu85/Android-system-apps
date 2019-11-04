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

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.AccessNetworkConstants;
import android.telephony.AvailableNetworkInfo;
import android.telephony.CarrierConfigManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.NetworkScan;
import android.telephony.NetworkScanRequest;
import android.telephony.RadioAccessSpecifier;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyScanManager;
import android.util.ArraySet;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Network Scan controller class which will scan for the specific bands as requested and
 * provide results to caller when ready.
 */
public class ONSNetworkScanCtlr {
    private static final String LOG_TAG = "ONSNetworkScanCtlr";
    private static final boolean DBG = true;
    private static final int SEARCH_PERIODICITY_SLOW = (int) TimeUnit.MINUTES.toSeconds(5);
    private static final int SEARCH_PERIODICITY_FAST = (int) TimeUnit.MINUTES.toSeconds(1);
    private static final int MAX_SEARCH_TIME = (int) TimeUnit.MINUTES.toSeconds(1);
    private static final int SCAN_RESTART_TIME = (int) TimeUnit.MINUTES.toMillis(1);
    private final Object mLock = new Object();

    /* message  to handle scan responses from modem */
    private static final int MSG_SCAN_RESULTS_AVAILABLE = 1;
    private static final int MSG_SCAN_COMPLETE = 2;
    private static final int MSG_SCAN_ERROR = 3;

    /* scan object to keep track of current scan request */
    private NetworkScan mCurrentScan;
    private boolean mIsScanActive;
    private NetworkScanRequest mCurrentScanRequest;
    private List<String> mMccMncs;
    private TelephonyManager mTelephonyManager;
    private CarrierConfigManager configManager;
    private int mRsrpEntryThreshold;
    @VisibleForTesting
    protected NetworkAvailableCallBack mNetworkAvailableCallBack;
    HandlerThread mThread;
    private Handler mHandler;

    @VisibleForTesting
    public TelephonyScanManager.NetworkScanCallback mNetworkScanCallback =
            new TelephonyScanManager.NetworkScanCallback() {

        @Override
        public void onResults(List<CellInfo> results) {
            logDebug("Total results :" + results.size());
            for (CellInfo cellInfo : results) {
                logDebug("cell info: " + cellInfo);
            }

            Message message = Message.obtain(mHandler, MSG_SCAN_RESULTS_AVAILABLE, results);
            message.sendToTarget();
        }

        @Override
        public void onComplete() {
            logDebug("Scan completed!");
            Message message = Message.obtain(mHandler, MSG_SCAN_COMPLETE, NetworkScan.SUCCESS);
            mHandler.sendMessageDelayed(message, SCAN_RESTART_TIME);
        }

        @Override
        public void onError(@NetworkScan.ScanErrorCode int error) {
            logDebug("Scan error " + error);
            Message message = Message.obtain(mHandler, MSG_SCAN_ERROR, error);
            message.sendToTarget();
        }
    };

    /**
     * call back for network availability
     */
    public interface NetworkAvailableCallBack {

        /**
         * Returns the scan results to the user, this callback will be called multiple times.
         */
        void onNetworkAvailability(List<CellInfo> results);

        /**
         * on error
         * @param error
         */
        void onError(int error);
    }

    private int getIntCarrierConfig(String key) {
        PersistableBundle b = null;
        if (configManager != null) {
            // If an invalid subId is used, this bundle will contain default values.
            b = configManager.getConfig();
        }
        if (b != null) {
            return b.getInt(key);
        } else {
            // Return static default defined in CarrierConfigManager.
            return CarrierConfigManager.getDefaultConfig().getInt(key);
        }
    }

    /**
     * analyze scan results
     * @param results contains all available cells matching the scan request at current location.
     */
    public void analyzeScanResults(List<CellInfo> results) {
        /* Inform registrants about availability of network */
        if (!mIsScanActive || results == null) {
          return;
        }
        List<CellInfo> filteredResults = new ArrayList<CellInfo>();
        synchronized (mLock) {
            for (CellInfo cellInfo : results) {
                if (mMccMncs.contains(getMccMnc(cellInfo))) {
                    if (cellInfo instanceof CellInfoLte) {
                        int rsrp = ((CellInfoLte) cellInfo).getCellSignalStrength().getRsrp();
                        logDebug("cell info rsrp: " + rsrp);
                        if (rsrp >= mRsrpEntryThreshold) {
                            filteredResults.add(cellInfo);
                        }
                    }
                }
            }
        }
        if ((filteredResults.size() >= 1) && (mNetworkAvailableCallBack != null)) {
            /* Todo: change to aggregate results on success. */
            mNetworkAvailableCallBack.onNetworkAvailability(filteredResults);
        }
    }

    private void invalidateScanOnError(int error) {
        logDebug("scan invalidated on error");
        if (mNetworkAvailableCallBack != null) {
            mNetworkAvailableCallBack.onError(error);
        }

        synchronized (mLock) {
            mIsScanActive = false;
            mCurrentScan = null;
        }
    }

    public ONSNetworkScanCtlr(Context c, TelephonyManager telephonyManager,
            NetworkAvailableCallBack networkAvailableCallBack) {
        init(c, telephonyManager, networkAvailableCallBack);
    }

    /**
     * initialize Network Scan controller
     * @param c context
     * @param telephonyManager Telephony manager instance
     * @param networkAvailableCallBack callback to be called when network selection is done
     */
    public void init(Context context, TelephonyManager telephonyManager,
            NetworkAvailableCallBack networkAvailableCallBack) {
        log("init called");
        mThread = new HandlerThread(LOG_TAG);
        mThread.start();
        mHandler =  new Handler(mThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_SCAN_RESULTS_AVAILABLE:
                        logDebug("Msg received for scan results");
                        /* Todo: need to aggregate the results */
                        analyzeScanResults((List<CellInfo>) msg.obj);
                        break;
                    case MSG_SCAN_COMPLETE:
                        logDebug("Msg received for scan complete");
                        restartScan();
                        break;
                    case MSG_SCAN_ERROR:
                        logDebug("Msg received for scan error");
                        invalidateScanOnError((int) msg.obj);
                        break;
                    default:
                        log("invalid message");
                        break;
                }
            }
        };
        mTelephonyManager = telephonyManager;
        mNetworkAvailableCallBack = networkAvailableCallBack;
        configManager = (CarrierConfigManager) context.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
    }

    /* get mcc mnc from cell info if the cell is for LTE */
    private String getMccMnc(CellInfo cellInfo) {
        if (cellInfo instanceof CellInfoLte) {
            return ((CellInfoLte) cellInfo).getCellIdentity().getMccString()
                    + ((CellInfoLte) cellInfo).getCellIdentity().getMncString();
        }

        return null;
    }

    private NetworkScanRequest createNetworkScanRequest(ArrayList<AvailableNetworkInfo> availableNetworks,
            int periodicity) {
        RadioAccessSpecifier[] ras = new RadioAccessSpecifier[1];
        ArrayList<String> mccMncs = new ArrayList<String>();
        Set<Integer> bandSet = new ArraySet<>();

        /* by default add band 48 */
        bandSet.add(AccessNetworkConstants.EutranBand.BAND_48);
        /* retrieve mcc mncs and bands for available networks */
        for (AvailableNetworkInfo availableNetwork : availableNetworks) {
            mccMncs.addAll(availableNetwork.getMccMncs());
            bandSet.addAll(availableNetwork.getBands());
        }

        int[] bands = bandSet.stream().mapToInt(band->band).toArray();
        /* create network scan request */
        ras[0] = new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.EUTRAN, bands,
                null);
        NetworkScanRequest networkScanRequest = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_PERIODIC, ras, periodicity, MAX_SEARCH_TIME, false,
                NetworkScanRequest.MAX_INCREMENTAL_PERIODICITY_SEC, mccMncs);
        synchronized (mLock) {
            mMccMncs = mccMncs;
        }
        return networkScanRequest;
    }

    /**
     * start less interval network scan
     * @param availableNetworks list of subscriptions for which the scanning needs to be started.
     * @return true if successfully accepted request.
     */
    public boolean startFastNetworkScan(ArrayList<AvailableNetworkInfo> availableNetworks) {
        NetworkScanRequest networkScanRequest = createNetworkScanRequest(availableNetworks,
                SEARCH_PERIODICITY_FAST);
        return startNetworkScan(networkScanRequest);
    }


    private boolean startNetworkScan(NetworkScanRequest networkScanRequest) {
        NetworkScan networkScan;
        synchronized (mLock) {
            /* if the request is same as existing one, then make sure to not proceed */
            if (mIsScanActive && mCurrentScanRequest.equals(networkScanRequest)) {
                return true;
            }

            /* Need to stop current scan if we already have one */
            stopNetworkScan();

            /* user lower threshold to enable modem stack */
            mRsrpEntryThreshold =
                getIntCarrierConfig(
                    CarrierConfigManager.KEY_OPPORTUNISTIC_NETWORK_EXIT_THRESHOLD_RSRP_INT);

            /* start new scan */
            networkScan = mTelephonyManager.requestNetworkScan(networkScanRequest,
                    mNetworkScanCallback);

            mCurrentScan = networkScan;
            mIsScanActive = true;
            mCurrentScanRequest = networkScanRequest;
        }

        logDebug("startNetworkScan " + networkScanRequest);
        return true;
    }

    private void restartScan() {
        NetworkScan networkScan;
        logDebug("restartScan");
        synchronized (mLock) {
            if (mCurrentScanRequest != null) {
                networkScan = mTelephonyManager.requestNetworkScan(mCurrentScanRequest,
                        mNetworkScanCallback);
                mIsScanActive = true;
            }
        }
    }

    /**
     * stop network scan
     */
    public void stopNetworkScan() {
        logDebug("stopNetworkScan");
        synchronized (mLock) {
            if (mIsScanActive && mCurrentScan != null) {
                try {
                    mCurrentScan.stopScan();
                } catch (IllegalArgumentException iae) {
                    logDebug("Scan failed with exception " + iae);
                }
                mIsScanActive = false;
                mCurrentScan = null;
                mCurrentScanRequest = null;
            }
        }
    }

    private static void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    private static void logDebug(String msg) {
        if (DBG) {
            Rlog.d(LOG_TAG, msg);
        }
    }
}
