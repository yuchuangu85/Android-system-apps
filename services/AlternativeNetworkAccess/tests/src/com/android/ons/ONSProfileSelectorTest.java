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

import static org.mockito.Mockito.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.telephony.AvailableNetworkInfo;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.IUpdateAvailableNetworksCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

public class ONSProfileSelectorTest extends ONSBaseTest {

    private MyONSProfileSelector mONSProfileSelector;
    private boolean testFailed;
    private boolean mCallbackInvoked;
    private int mDataSubId;
    private int mResult;
    @Mock
    ONSNetworkScanCtlr mONSNetworkScanCtlr;
    @Mock
    TelephonyManager mSubscriptionBoundTelephonyManager;
    private Looper mLooper;
    private static final String TAG = "ONSProfileSelectorTest";

    MyONSProfileSelector.ONSProfileSelectionCallback mONSProfileSelectionCallback =
        new MyONSProfileSelector.ONSProfileSelectionCallback() {
            public void onProfileSelectionDone() {
                mCallbackInvoked = true;
                setReady(true);
            }
        };

    public class MyONSProfileSelector extends ONSProfileSelector {

        public SubscriptionManager.OnOpportunisticSubscriptionsChangedListener mProfileChngLstnrCpy;
        public BroadcastReceiver mProfileSelectorBroadcastReceiverCpy;
        public ONSNetworkScanCtlr.NetworkAvailableCallBack mNetworkAvailableCallBackCpy;

        public MyONSProfileSelector(Context c,
            MyONSProfileSelector.ONSProfileSelectionCallback aNSProfileSelectionCallback) {
            super(c, aNSProfileSelectionCallback);
        }

        public void triggerProfileUpdate() {
            mHandler.sendEmptyMessage(1);
        }

        public void updateOppSubs() {
            updateOpportunisticSubscriptions();
        }

        protected void init(Context c,
            MyONSProfileSelector.ONSProfileSelectionCallback aNSProfileSelectionCallback) {
            super.init(c, aNSProfileSelectionCallback);
            this.mSubscriptionManager = ONSProfileSelectorTest.this.mSubscriptionManager;
            this.mSubscriptionBoundTelephonyManager =
                ONSProfileSelectorTest.this.mSubscriptionBoundTelephonyManager;
            mProfileChngLstnrCpy = mProfileChangeListener;
            mProfileSelectorBroadcastReceiverCpy = null;
            mNetworkAvailableCallBackCpy = mNetworkAvailableCallBack;
            mNetworkScanCtlr = mONSNetworkScanCtlr;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp("ONSTest");
        mLooper = null;
        MockitoAnnotations.initMocks(this);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        if (mLooper != null) {
            mLooper.quit();
            mLooper.getThread().join();
        }
    }


    @Test
    public void testStartProfileSelectionWithNoOpportunisticSub() {
        List<CellInfo> results2 = new ArrayList<CellInfo>();
        CellIdentityLte cellIdentityLte = new CellIdentityLte(310, 210, 1, 1, 1);
        CellInfoLte cellInfoLte = new CellInfoLte();
        cellInfoLte.setCellIdentity(cellIdentityLte);
        results2.add((CellInfo) cellInfoLte);
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
            new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);

        IUpdateAvailableNetworksCallback mCallback = new IUpdateAvailableNetworksCallback.Stub() {
            @Override
            public void onComplete(int result) {
                Log.d(TAG, "mResult end:" + result);
                mResult = result;
            }
        };

        mResult = -1;
        mReady = false;
        mCallbackInvoked = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                doReturn(true).when(mONSNetworkScanCtlr).startFastNetworkScan(anyObject());
                doReturn(new ArrayList<>()).when(mSubscriptionManager)
                    .getOpportunisticSubscriptions();
                mONSProfileSelector = new MyONSProfileSelector(mContext,
                    mONSProfileSelectionCallback);
                mONSProfileSelector.updateOppSubs();
                mONSProfileSelector.startProfileSelection(availableNetworkInfos, mCallback);
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();
        mReady = false;

        // Testing startProfileSelection without any oppotunistic data.
        // should not get any callback invocation.
        waitUntilReady(100);
        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_INVALID_ARGUMENTS, mResult);
        assertFalse(mCallbackInvoked);
    }


    @Test
    public void testStartProfileSelectionSuccess() {
        List<SubscriptionInfo> subscriptionInfoList = new ArrayList<SubscriptionInfo>();
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
            "123", 1, null, "310", "210", "", false, null, "1");
        SubscriptionInfo subscriptionInfo2 = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
            "123", 1, null, "310", "211", "", false, null, "1");
        subscriptionInfoList.add(subscriptionInfo);

        List<CellInfo> results2 = new ArrayList<CellInfo>();
        CellIdentityLte cellIdentityLte = new CellIdentityLte(310, 210, 1, 1, 1);
        CellInfoLte cellInfoLte = new CellInfoLte();
        cellInfoLte.setCellIdentity(cellIdentityLte);
        results2.add((CellInfo) cellInfoLte);
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
            new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);

        IUpdateAvailableNetworksCallback mCallback = new IUpdateAvailableNetworksCallback.Stub() {
            @Override
            public void onComplete(int result) {
                mResult = result;
            }
        };

        mResult = -1;
        mReady = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                doReturn(subscriptionInfoList).when(mSubscriptionManager)
                    .getOpportunisticSubscriptions();
                doReturn(true).when(mSubscriptionManager).isActiveSubId(anyInt());
                doReturn(true).when(mSubscriptionBoundTelephonyManager).enableModemForSlot(
                    anyInt(), anyBoolean());
                mONSProfileSelector = new MyONSProfileSelector(mContext,
                    new MyONSProfileSelector.ONSProfileSelectionCallback() {
                        public void onProfileSelectionDone() {
                            setReady(true);
                        }
                    });
                mONSProfileSelector.updateOppSubs();
                mONSProfileSelector.startProfileSelection(availableNetworkInfos, mCallback);
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();
        mReady = false;
        mDataSubId = -1;

        // Testing startProfileSelection with oppotunistic sub.
        // On success onProfileSelectionDone must get invoked.
        assertFalse(mReady);
        waitForMs(500);
        mONSProfileSelector.mNetworkAvailableCallBackCpy.onNetworkAvailability(results2);
        Intent callbackIntent = new Intent(MyONSProfileSelector.ACTION_SUB_SWITCH);
        callbackIntent.putExtra("sequenceId", 1);
        callbackIntent.putExtra("subId", 5);
        waitUntilReady();
        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS, mResult);
        assertTrue(mReady);
    }

    @Test
    public void testStartProfileSelectionWithActivePrimarySimOnESim() {
        List<SubscriptionInfo> opportunisticSubscriptionInfoList = new ArrayList<SubscriptionInfo>();
        List<SubscriptionInfo> activeSubscriptionInfoList = new ArrayList<SubscriptionInfo>();
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
            "123", 1, null, "310", "210", "", true, null, "1", true, null, 1839, 1);
        SubscriptionInfo subscriptionInfo2 = new SubscriptionInfo(6, "", 1, "TMO", "TMO", 1, 1,
            "123", 1, null, "310", "211", "", true, null, "1", false, null, 1839, 1);
        opportunisticSubscriptionInfoList.add(subscriptionInfo);
        activeSubscriptionInfoList.add(subscriptionInfo2);

        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(5, 2, mccMncs,
            new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);

        IUpdateAvailableNetworksCallback mCallback = new IUpdateAvailableNetworksCallback.Stub() {
            @Override
            public void onComplete(int result) {
                mResult = result;
            }
        };

        mResult = -1;
        mReady = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                doReturn(opportunisticSubscriptionInfoList).when(mSubscriptionManager)
                    .getOpportunisticSubscriptions();
                doReturn(false).when(mSubscriptionManager).isActiveSubId(anyInt());
                doReturn(activeSubscriptionInfoList).when(mSubscriptionManager)
                    .getActiveSubscriptionInfoList(anyBoolean());
                mONSProfileSelector = new MyONSProfileSelector(mContext,
                    new MyONSProfileSelector.ONSProfileSelectionCallback() {
                        public void onProfileSelectionDone() {
                            setReady(true);
                        }
                    });
                mONSProfileSelector.updateOppSubs();
                mONSProfileSelector.startProfileSelection(availableNetworkInfos, mCallback);
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();
        mReady = false;
        mDataSubId = -1;

        // Testing startProfileSelection with opportunistic sub.
        // On success onProfileSelectionDone must get invoked.
        assertFalse(mReady);
        waitForMs(100);
        Intent callbackIntent = new Intent(MyONSProfileSelector.ACTION_SUB_SWITCH);
        callbackIntent.putExtra("sequenceId", 1);
        callbackIntent.putExtra("subId", 5);
        waitUntilReady();
        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_INVALID_ARGUMENTS, mResult);
    }

    public static void waitForMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Log.d(TAG, "InterruptedException while waiting: " + e);
        }
    }

    @Test
    public void testselectProfileForDataWithNoOpportunsticSub() {
        mReady = false;
        doReturn(new ArrayList<>()).when(mSubscriptionManager).getOpportunisticSubscriptions();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mONSProfileSelector = new MyONSProfileSelector(mContext,
                    new MyONSProfileSelector.ONSProfileSelectionCallback() {
                        public void onProfileSelectionDone() {
                        }
                    });
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();

        // Testing selectProfileForData with no oppotunistic sub and the function should
        // return false.
        mONSProfileSelector.selectProfileForData(1, false, null);
    }

    @Test
    public void testselectProfileForDataWithInActiveSub() {
        List<SubscriptionInfo> subscriptionInfoList = new ArrayList<SubscriptionInfo>();
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
            "123", 1, null, "310", "210", "", false, null, "1");
        subscriptionInfoList.add(subscriptionInfo);
        mReady = false;
        doReturn(new ArrayList<>()).when(mSubscriptionManager).getOpportunisticSubscriptions();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mONSProfileSelector = new MyONSProfileSelector(mContext,
                    new MyONSProfileSelector.ONSProfileSelectionCallback() {
                        public void onProfileSelectionDone() {
                        }
                    });
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();

        // Testing selectProfileForData with in active sub and the function should return false.
        mONSProfileSelector.selectProfileForData(5, false, null);
    }

    @Test
    public void testselectProfileForDataWithInvalidSubId() {
        List<SubscriptionInfo> subscriptionInfoList = new ArrayList<SubscriptionInfo>();
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
            "123", 1, null, "310", "210", "", false, null, "1");
        subscriptionInfoList.add(subscriptionInfo);
        mReady = false;
        doReturn(subscriptionInfoList).when(mSubscriptionManager).getOpportunisticSubscriptions();
        doNothing().when(mSubscriptionManager).setPreferredDataSubscriptionId(
            anyInt(), anyBoolean(), any(), any());
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mONSProfileSelector = new MyONSProfileSelector(mContext,
                    new MyONSProfileSelector.ONSProfileSelectionCallback() {
                        public void onProfileSelectionDone() {
                        }
                    });
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();

        // Testing selectProfileForData with INVALID_SUBSCRIPTION_ID and the function should
        // return true.
        mONSProfileSelector.selectProfileForData(
            SubscriptionManager.INVALID_SUBSCRIPTION_ID, false, null);
    }

    @Test
    public void testselectProfileForDataWithValidSub() {
        List<SubscriptionInfo> subscriptionInfoList = new ArrayList<SubscriptionInfo>();
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
            "123", 1, null, "310", "210", "", false, null, "1");
        subscriptionInfoList.add(subscriptionInfo);
        mReady = false;
        doReturn(subscriptionInfoList).when(mSubscriptionManager)
            .getActiveSubscriptionInfoList();
        doNothing().when(mSubscriptionManager).setPreferredDataSubscriptionId(
            anyInt(), anyBoolean(), any(), any());
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                doReturn(subscriptionInfoList).when(mSubscriptionManager)
                    .getOpportunisticSubscriptions();
                mONSProfileSelector = new MyONSProfileSelector(mContext,
                    new MyONSProfileSelector.ONSProfileSelectionCallback() {
                        public void onProfileSelectionDone() {
                        }
                    });
                mONSProfileSelector.updateOppSubs();
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();

        // Testing selectProfileForData with valid opportunistic sub and the function should
        // return true.
        mONSProfileSelector.selectProfileForData(5, false, null);
    }

    @Test
    public void testStartProfileSelectionSuccessWithSameArgumentsAgain() {
        List<SubscriptionInfo> subscriptionInfoList = new ArrayList<SubscriptionInfo>();
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
            "123", 1, null, "310", "210", "", false, null, "1");
        SubscriptionInfo subscriptionInfo2 = new SubscriptionInfo(5, "", 1, "TMO", "TMO", 1, 1,
            "123", 1, null, "310", "211", "", false, null, "1");
        subscriptionInfoList.add(subscriptionInfo);

        List<CellInfo> results2 = new ArrayList<CellInfo>();
        CellIdentityLte cellIdentityLte = new CellIdentityLte(310, 210, 1, 1, 1);
        CellInfoLte cellInfoLte = new CellInfoLte();
        cellInfoLte.setCellIdentity(cellIdentityLte);
        results2.add((CellInfo) cellInfoLte);
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
            new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);

        IUpdateAvailableNetworksCallback mCallback = new IUpdateAvailableNetworksCallback.Stub() {
            @Override
            public void onComplete(int result) {
                mResult = result;
            }
        };

        mResult = -1;
        mReady = false;
        mONSProfileSelector = new MyONSProfileSelector(mContext,
            new MyONSProfileSelector.ONSProfileSelectionCallback() {
                public void onProfileSelectionDone() {
                    setReady(true);
                }
            });
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                doReturn(subscriptionInfoList).when(mSubscriptionManager)
                    .getOpportunisticSubscriptions();
                doReturn(true).when(mSubscriptionManager).isActiveSubId(anyInt());
                doReturn(true).when(mSubscriptionBoundTelephonyManager).enableModemForSlot(
                    anyInt(), anyBoolean());

                mONSProfileSelector.updateOppSubs();
                mONSProfileSelector.startProfileSelection(availableNetworkInfos, mCallback);
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();
        mReady = false;
        mDataSubId = -1;

        // Testing startProfileSelection with oppotunistic sub.
        // On success onProfileSelectionDone must get invoked.
        assertFalse(mReady);
        waitForMs(500);
        mONSProfileSelector.mNetworkAvailableCallBackCpy.onNetworkAvailability(results2);
        Intent callbackIntent = new Intent(MyONSProfileSelector.ACTION_SUB_SWITCH);
        callbackIntent.putExtra("sequenceId", 1);
        callbackIntent.putExtra("subId", 5);
        waitUntilReady();
        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS, mResult);
        assertTrue(mReady);

        mResult = -1;
        mReady = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                doReturn(subscriptionInfoList).when(mSubscriptionManager)
                    .getOpportunisticSubscriptions();
                doReturn(true).when(mSubscriptionManager).isActiveSubId(anyInt());
                doReturn(true).when(mSubscriptionBoundTelephonyManager).enableModemForSlot(
                    anyInt(), anyBoolean());
                mONSProfileSelector.updateOppSubs();
                mONSProfileSelector.startProfileSelection(availableNetworkInfos, mCallback);
                mLooper = Looper.myLooper();
                setReady(true);
                Looper.loop();
            }
        }).start();

        // Wait till initialization is complete.
        waitUntilReady();
        mReady = false;
        mDataSubId = -1;

        // Testing startProfileSelection with oppotunistic sub.
        // On success onProfileSelectionDone must get invoked.
        assertFalse(mReady);
        waitForMs(500);
        mONSProfileSelector.mNetworkAvailableCallBackCpy.onNetworkAvailability(results2);
        waitUntilReady();
        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS, mResult);
        assertTrue(mReady);
    }
}
