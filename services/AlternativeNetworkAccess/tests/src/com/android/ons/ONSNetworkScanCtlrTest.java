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
 * limitations under the License
 */
package com.android.ons;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

import android.os.Looper;
import android.telephony.AvailableNetworkInfo;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.NetworkScan;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.Array;
import java.util.ArrayList;
import java.util.List;

public class ONSNetworkScanCtlrTest extends ONSBaseTest {
    private ONSNetworkScanCtlr mONSNetworkScanCtlr;
    private NetworkScan mNetworkScan;
    private List<CellInfo> mResults;
    private int mError;
    private boolean mCallbackInvoked;
    private Looper mLooper;

    @Before
    public void setUp() throws Exception {
        super.setUp("ONSTest");
        mLooper = null;
        mNetworkScan = new NetworkScan(1, 1);
        doReturn(mNetworkScan).when(mMockTelephonyManager).requestNetworkScan(anyObject(), anyObject());
    }

    @After
    public void tearDown() throws Exception {
        if (mLooper != null) {
            mLooper.quit();
            mLooper.getThread().join();
        }
        super.tearDown();
    }

    @Test
    public void testStartFastNetworkScan() {
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);
        List<CellInfo> expectedResults = new ArrayList<CellInfo>();
        CellIdentityLte cellIdentityLte = new CellIdentityLte(310, 210, 1, 1, 1);
        CellInfoLte cellInfoLte = new CellInfoLte();
        cellInfoLte.setCellIdentity(cellIdentityLte);
        expectedResults.add((CellInfo)cellInfoLte);
        mReady = false;

        // initializing ONSNetworkScanCtlr
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mONSNetworkScanCtlr = new ONSNetworkScanCtlr(mContext, mMockTelephonyManager,
                        new ONSNetworkScanCtlr.NetworkAvailableCallBack() {
                        @Override
                        public void onNetworkAvailability(List<CellInfo> results) {
                            mResults = results;
                            setReady(true);
                        }

                        public void onError(int error) {
                            setReady(true);
                        }
                    });

                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();
        mReady = false;

        // Testing startFastNetworkScan, onNetworkAvailability should be called with expectedResults
        mONSNetworkScanCtlr.startFastNetworkScan(availableNetworkInfos);
        mONSNetworkScanCtlr.mNetworkScanCallback.onResults(expectedResults);
        waitUntilReady(100);
        assertEquals(expectedResults, mResults);
    }

    @Test
    public void testStartFastNetworkScanFail() {
        List<CellInfo> expectedResults = new ArrayList<CellInfo>();
        CellIdentityLte cellIdentityLte = new CellIdentityLte(310, 210, 1, 1, 1);
        CellInfoLte cellInfoLte = new CellInfoLte();
        cellInfoLte.setCellIdentity(cellIdentityLte);
        expectedResults.add((CellInfo)cellInfoLte);
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);
        mReady = false;
        mError = NetworkScan.SUCCESS;

        // initializing ONSNetworkScanCtlr
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mONSNetworkScanCtlr = new ONSNetworkScanCtlr(mContext, mMockTelephonyManager,
                        new ONSNetworkScanCtlr.NetworkAvailableCallBack() {
                        @Override
                        public void onNetworkAvailability(List<CellInfo> results) {
                            setReady(true);
                        }

                        @Override
                        public void onError(int error) {
                            mError = error;
                            setReady(true);
                        }
                    });

                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();

            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();
        mReady = false;

        // Testing startFastNetworkScan, onError should be called with ERROR_INVALID_SCAN
        mONSNetworkScanCtlr.startFastNetworkScan(availableNetworkInfos);
        mONSNetworkScanCtlr.mNetworkScanCallback.onError(NetworkScan.ERROR_INVALID_SCAN);
        waitUntilReady(100);
        assertEquals(NetworkScan.ERROR_INVALID_SCAN, mError);
    }

    @Test
    public void testStartFastNetworkScanWithMultipleNetworks() {
        List<CellInfo> expectedResults = new ArrayList<CellInfo>();
        CellIdentityLte cellIdentityLte = new CellIdentityLte(310, 210, 1, 1, 1);
        CellInfoLte cellInfoLte = new CellInfoLte();
        cellInfoLte.setCellIdentity(cellIdentityLte);
        expectedResults.add((CellInfo)cellInfoLte);
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        mccMncs.add("310211");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
            new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);
        mReady = false;

        // initializing ONSNetworkScanCtlr
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mONSNetworkScanCtlr = new ONSNetworkScanCtlr(mContext, mMockTelephonyManager,
                        new ONSNetworkScanCtlr.NetworkAvailableCallBack() {
                        @Override
                        public void onNetworkAvailability(List<CellInfo> results) {
                            mResults = results;
                            setReady(true);
                        }

                        public void onError(int error) {
                            setReady(true);
                        }
                    });

                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();

            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();
        mReady = false;

        // Testing startSlowNetworkScan, onNetworkAvailability should be called with expectedResults
        mONSNetworkScanCtlr.startFastNetworkScan(availableNetworkInfos);
        mONSNetworkScanCtlr.mNetworkScanCallback.onResults(expectedResults);
        waitUntilReady(100);
        assertEquals(expectedResults, mResults);
    }

    @Test
    public void testStopNetworkScan() {
        List<CellInfo> expectedResults = new ArrayList<CellInfo>();
        CellIdentityLte cellIdentityLte = new CellIdentityLte(310, 210, 1, 1, 1);
        CellInfoLte cellInfoLte = new CellInfoLte();
        cellInfoLte.setCellIdentity(cellIdentityLte);
        expectedResults.add((CellInfo)cellInfoLte);
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);
        mCallbackInvoked = false;
        mReady = false;

        // initializing ONSNetworkScanCtlr
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mONSNetworkScanCtlr = new ONSNetworkScanCtlr(mContext, mMockTelephonyManager,
                        new ONSNetworkScanCtlr.NetworkAvailableCallBack() {
                        @Override
                        public void onNetworkAvailability(List<CellInfo> results) {
                            mCallbackInvoked = true;
                            setReady(true);
                        }

                        public void onError(int error) {
                            mCallbackInvoked = true;
                        }
                    });

                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();
        mReady = false;

        // Testing stopNetworkScan, should not get any callback invocation after stopNetworkScan.
        mONSNetworkScanCtlr.startFastNetworkScan(availableNetworkInfos);
        mONSNetworkScanCtlr.stopNetworkScan();
        mONSNetworkScanCtlr.mNetworkScanCallback.onResults(expectedResults);
        waitUntilReady(100);
        assertFalse(mCallbackInvoked);
    }
}
